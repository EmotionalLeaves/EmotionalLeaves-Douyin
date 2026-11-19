package dev.dyfeed.ui.learn

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** On-phone reading of Chinese text in a picture (ML Kit, through Play services). */
internal object Reader {
    val client by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    /** The text lines in [bitmap], positions as fractions of [fullW]×[fullH] (the crop starts at [top]). */
    suspend fun lines(bitmap: Bitmap, top: Int, fullW: Int, fullH: Int): List<LiveSubs.Line> = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                cont.resume(
                    text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
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
    var viewH by remember { mutableStateOf(0) }
    LaunchedEffect(player, playerView, active, on) {
        chinese = null; english = null
        if (!on || !active || player == null || playerView == null || android.os.Build.VERSION.SDK_INT < 24) return@LaunchedEffect
        var misses = 0
        var lastSeenAt = 0L
        while (true) {
            delay(if (misses > MISSES_BEFORE_SLOWING) SLOW_MS else EVERY_MS)
            if (!player.isPlaying) continue
            val surface = playerView.videoSurfaceView as? android.view.SurfaceView ?: continue
            val size = player.videoSize
            if (size.width <= 0 || size.height <= 0) continue
            val scale = minOf(1f, MAX_WIDTH.toFloat() / size.width)
            val w = (size.width * scale).toInt()
            val h = (size.height * scale).toInt()
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val copied = suspendCancellableCoroutine { cont ->
                runCatching {
                    android.view.PixelCopy.request(surface, frame, { r -> cont.resume(r == android.view.PixelCopy.SUCCESS) }, android.os.Handler(android.os.Looper.getMainLooper()))
                }.onFailure { cont.resume(false) }
            }
            if (!copied) { frame.recycle(); continue }
            // Subtitles live in the lower half: read only that.
            val top = (h * 0.42f).toInt()
            val crop = Bitmap.createBitmap(frame, 0, top, w, (h * 0.96f).toInt() - top)
            val lines = Reader.lines(crop, top, w, h)
            crop.recycle(); frame.recycle()
            val line = LiveSubs.pick(lines)
            val now = System.currentTimeMillis()
            if (line == null) {
                misses++
                // Keep the last line up a moment after it leaves the picture.
                if (chinese != null && now - lastSeenAt > LINGER_MS) { chinese = null; english = null }
                continue
            }
            misses = 0
            lastSeenAt = now
            lines.filter { line.endsWith(it.text.replace(" ", "")) }.maxByOrNull { it.centerY }?.let { lineY = it.centerY - it.height }
            if (LiveSubs.same(chinese, line)) continue
            chinese = line
            english = dev.dyfeed.domain.lang.ChineseToEnglish.translate(line).getOrNull()
        }
    }
    val en = english ?: return
    val zh = chinese ?: return
    // Just above the subtitle it translates (the video fills the height in the feed, near enough).
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        val y = (maxHeight * lineY - 64.dp).coerceAtLeast(80.dp)
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
            Text(en, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            val pinyinOn by LearnPrefs.pinyin.collectAsState()
            if (pinyinOn) pinyinLine(zh)?.let {
                Text(it, color = Color(0xFFFFD58A), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val EVERY_MS = 700L
private const val SLOW_MS = 2_500L
private const val MISSES_BEFORE_SLOWING = 10
private const val LINGER_MS = 1_800L
private const val MAX_WIDTH = 720
