package dev.dyfeed.ui.learn

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dev.dyfeed.domain.learn.LiveSubs
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** On-phone reading of Chinese text in a picture (ML Kit, its model inside the app: works at once, offline, without Play services). */
internal object Reader {
    val client by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    /** The text lines in [bitmap], positions as fractions of [fullW]×[fullH] (the crop starts at [top]). */
    suspend fun lines(bitmap: Bitmap, top: Int, fullW: Int, fullH: Int): List<LiveSubs.Line> = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                cont.resume(
                    text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                        // Lines it isn't sure of would translate into nonsense: leave them out.
                        if (l.confidence > 0f && l.confidence < MIN_CONFIDENCE) return@mapNotNull null
                        val b = l.boundingBox ?: return@mapNotNull null
                        LiveSubs.Line(l.text, b.exactCenterX() / fullW, (b.exactCenterY() + top) / fullH, b.height().toFloat() / fullH)
                    },
                )
            }
            .addOnFailureListener { cont.resume(emptyList()) }
    }
}

/**
 * What's being said, in English, as it's said: the subtitles burned into the video are read off the
 * picture every moment (on the phone), each new line translated and shown just above it. Tap it to
 * take the Chinese line apart, word by word. Learn → Translate what's said turns it off.
 */
@Composable
fun BoxScope.LiveSubtitles(player: ExoPlayer?, playerView: PlayerView?, active: Boolean) {
    val on by LearnPrefs.liveSubs.collectAsState()
    var chinese by remember { mutableStateOf<String?>(null) }
    var english by remember { mutableStateOf<String?>(null) }
    var lineY by remember { mutableStateOf(0.7f) }
    /** Where the line on screen began (ms into the video), to hear it again. */
    var lineStart by remember { mutableStateOf(-1L) }
    /** Paused at the end of a line (Pause after each line): say so, and a tap goes on. */
    var pausedForLine by remember { mutableStateOf(false) }
    LaunchedEffect(player, pausedForLine) {
        while (pausedForLine) { delay(200); if (player?.isPlaying == true) pausedForLine = false }
    }
    var viewH by remember { mutableStateOf(0) }
    LaunchedEffect(player, playerView, active, on) {
        chinese = null; english = null
        if (!on || !active || player == null || playerView == null || android.os.Build.VERSION.SDK_INT < 24) return@LaunchedEffect
        // Lines met in this video, with when they were on screen: clips loop, so from the second
        // time round each line shows exactly on time, already translated, without reading a frame.
        val cues = ArrayList<Cue>()
        var current: Cue? = null
        var frame: Bitmap? = null
        var lastSig: LongArray? = null
        var misses = 0
        try {
            while (true) {
                delay(if (misses > MISSES_BEFORE_SLOWING) SLOW_MS else EVERY_MS)
                if (!player.isPlaying) { if (pausedForLine) lastSig = null; continue }
                val pos = player.currentPosition
                // Known already (a loop): show it from memory.
                val known = cues.firstOrNull { it.end > it.start && pos in it.start..it.end }
                if (known != null) {
                    if (chinese != known.zh) { chinese = known.zh; english = known.en; lineY = known.y; lineStart = known.start }
                    continue
                }
                val surface = playerView.videoSurfaceView as? android.view.SurfaceView ?: continue
                val size = player.videoSize
                if (size.width <= 0 || size.height <= 0) continue
                val scale = minOf(1f, MAX_WIDTH.toFloat() / size.width)
                val w = (size.width * scale).toInt()
                val h = (size.height * scale).toInt()
                // One small frame, reused (no new picture memory every check).
                val f = frame?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { frame?.recycle(); frame = it }
                val copied = suspendCancellableCoroutine { cont ->
                    runCatching {
                        android.view.PixelCopy.request(surface, f, { r -> cont.resume(r == android.view.PixelCopy.SUCCESS) }, android.os.Handler(android.os.Looper.getMainLooper()))
                    }.onFailure { cont.resume(false) }
                }
                if (!copied) continue
                val top = (h * 0.42f).toInt()
                val bottom = (h * 0.96f).toInt()
                // Nothing changed where subtitles go: the same line (or none) is still up.
                val sig = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { LiveSubs.signature(f, top, bottom) }
                val prev = lastSig
                if (prev != null && LiveSubs.sameSignature(prev, sig)) {
                    current?.let { it.end = pos }
                    continue
                }
                lastSig = sig
                val (lines, line) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val crop = Bitmap.createBitmap(f, 0, top, w, bottom - top)
                    val read = Reader.lines(crop, top, w, h)
                    crop.recycle()
                    read to LiveSubs.pick(read)
                }
                if (line == null) {
                    misses++
                    // Learn → Pause after each line: stop here so the English can be read.
                    // Learn → Pause after each line: stop here, its English left up to be read.
                    if (current != null && LearnPrefs.autoPause.value) {
                        player.pause(); pausedForLine = true
                        current?.let { it.end = pos }
                        current = null
                        continue
                    }
                    // The line has gone: take its English down now, not later.
                    current?.let { it.end = pos }
                    current = null
                    chinese = null; english = null
                    continue
                }
                misses = 0
                val y = lines.filter { line.endsWith(it.text.replace(" ", "")) }.maxByOrNull { it.centerY }?.let { it.centerY - it.height } ?: lineY
                if (LiveSubs.same(current?.zh, line)) { current?.let { it.end = pos }; continue }
                current?.let { it.end = pos }
                // One line straight into the next: pause on the change, showing the line just ended.
                if (current != null && LearnPrefs.autoPause.value) {
                    player.pause(); pausedForLine = true
                    current = null
                    continue
                }
                dev.dyfeed.domain.learn.Heard.add(line)
                // The new line at once (the old English never sits under a new line), English as soon as it's in.
                chinese = line; lineY = y; lineStart = pos
                english = dev.dyfeed.domain.lang.ChineseToEnglish.cached(line)
                val cue = Cue(pos, pos, line, english, y)
                current = cue
                cues += cue
                if (english == null) {
                    val en = dev.dyfeed.domain.lang.ChineseToEnglish.translate(line).getOrNull()
                    cue.en = en
                    if (chinese == line) english = en
                }
            }
        } finally {
            frame?.recycle()
        }
    }
    val zh = chinese ?: return
    val en = english ?: return
    // Just above the subtitle it translates (the video fills the height in the feed, near enough).
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        // Where the line is on screen: the video is fitted, so a wide one sits in a band mid-screen.
        val size = player?.videoSize
        val aspect = if (size != null && size.width > 0 && size.height > 0) size.width.toFloat() / size.height else 9f / 16f
        val shownH = minOf(maxHeight, maxWidth / aspect)
        val top = (maxHeight - shownH) / 2
        val y = (top + shownH * lineY - 64.dp).coerceAtLeast(80.dp)
        Column(
            Modifier.align(Alignment.TopCenter)
                .offset(y = y)
                .padding(horizontal = 28.dp)
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { LearnWords.request.value = zh }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(en, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                // Hear this line again: back to where it began.
                if (lineStart >= 0) androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Replay,
                    contentDescription = "Hear this line again",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 8.dp).size(22.dp).clip(RoundedCornerShape(11.dp))
                        .clickable { player?.seekTo((lineStart - REPLAY_LEAD_MS).coerceAtLeast(0L)) },
                )
            }
            if (pausedForLine) Text(
                "Paused after the line · tap here to go on",
                color = Color(0xFFFFD58A),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp).clickable { pausedForLine = false; player?.play() },
            )
            val pinyinOn by LearnPrefs.pinyin.collectAsState()
            if (pinyinOn) pinyinLine(zh)?.let {
                Text(it, color = Color(0xFFFFD58A), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** How sure the reader must be of a line to use it (0–1; ML Kit gives 0 when it can't say). */
private const val MIN_CONFIDENCE = 0.45f

/** A little before the line began, so its first syllable isn't cut. */
private const val REPLAY_LEAD_MS = 300L

/** A line seen in this video, and from when to when (ms into it). */
private class Cue(val start: Long, var end: Long, val zh: String, var en: String?, val y: Float)

/** Four checks a second: a new line shows within about a quarter of a second of appearing. */
private const val EVERY_MS = 250L
/** Videos with no subtitles get looked at less often, to spare the battery. */
private const val SLOW_MS = 2_000L
private const val MISSES_BEFORE_SLOWING = 12
/** Wide enough for subtitle text to be read reliably, small enough to copy four times a second. */
private const val MAX_WIDTH = 480
