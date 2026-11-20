package dev.dyfeed.domain.learn

import dev.dyfeed.domain.lang.Zh

/**
 * Finding the spoken line among the text a video shows: subtitles sit low in the picture, centred,
 * in Chinese, one or two lines of similar height. Watermarks (抖音号, @names), corner logos and
 * on-screen titles high up are left out.
 */
object LiveSubs {
    /** One line of text read off a frame: where it is, as fractions of the frame (0–1). */
    data class Line(val text: String, val centerX: Float, val centerY: Float, val height: Float)

    /** The subtitle in [lines], or null when nothing looks like one. */
    fun pick(lines: List<Line>): String? {
        val candidates = lines.filter { l ->
            val t = l.text.trim()
            Zh.has(t) && t.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } >= 2 &&
                l.centerY in 0.45f..0.95f && l.centerX in 0.25f..0.75f &&
                WATERMARK.none { it in t } && !t.startsWith("@")
        }
        if (candidates.isEmpty()) return null
        // The lowest line, and the one just above it if it's the same size (a two-line subtitle).
        val sorted = candidates.sortedBy { it.centerY }
        val last = sorted.last()
        val above = sorted.dropLast(1).lastOrNull()?.takeIf { a ->
            last.centerY - a.centerY < last.height * 2.2f && kotlin.math.abs(a.height - last.height) < last.height * 0.35f
        }
        return listOfNotNull(above, last).joinToString("") { it.text.trim() }.replace(" ", "")
    }

    /** Whether [b] is the same subtitle as [a], allowing for a character read differently. */
    fun same(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a == b
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        if (shorter == 0) return false
        val common = a.toSet().intersect(b.toSet()).size
        return common >= maxOf(a.toSet().size, b.toSet().size) * 0.8 && kotlin.math.abs(a.length - b.length) <= 2
    }

    /**
     * A cheap fingerprint of where subtitles go (rows [top] to [bottom] of [frame]): which cells of
     * a 48×12 grid hold bright text-like pixels. Two the same means the subtitle hasn't changed, so
     * the text reader needn't run again.
     */
    fun signature(frame: android.graphics.Bitmap, top: Int, bottom: Int): LongArray {
        val cols = 48; val rows = 12
        val out = LongArray((cols * rows + 63) / 64)
        val w = frame.width
        val h = (bottom - top).coerceAtLeast(1)
        val px = IntArray(w)
        for (r in 0 until rows) {
            val y = top + (r * h + h / 2) / rows
            frame.getPixels(px, 0, w, 0, y.coerceIn(0, frame.height - 1), w, 1)
            for (c in 0 until cols) {
                val x0 = c * w / cols; val x1 = (c + 1) * w / cols
                var bright = 0
                for (x in x0 until x1) { val p = px[x]; if (luma(p) > 200) bright++ }
                if (bright * 6 >= (x1 - x0).coerceAtLeast(1)) { val bit = r * cols + c; out[bit / 64] = out[bit / 64] or (1L shl (bit % 64)) }
            }
        }
        return out
    }

    /** Fingerprints that differ in at most a few cells: the same subtitle (video noise aside). */
    fun sameSignature(a: LongArray, b: LongArray): Boolean = a.indices.sumOf { java.lang.Long.bitCount(a[it] xor b[it]) } <= SIGNATURE_SLACK

    private fun luma(p: Int): Int = ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10
    private const val SIGNATURE_SLACK = 6

    private val WATERMARK = listOf("抖音号", "抖音", "DouYin", "TikTok", "关注", "点赞")
}

/**
 * What's been said in each video lately (lines read off its subtitles, with when and their English),
 * kept in memory for the last [MAX_VIDEOS] videos: the ⋯ menu's transcript.
 */
object Transcripts {
    data class Line(val atMs: Long, val zh: String, val en: String?)

    private const val MAX_VIDEOS = 30
    private val byVideo = object : LinkedHashMap<String, MutableList<Line>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableList<Line>>?) = size > MAX_VIDEOS
    }

    @Synchronized fun add(key: String, atMs: Long, zh: String, en: String?) {
        if (dev.dyfeed.domain.privacy.Incognito.active) return
        val list = byVideo.getOrPut(key) { ArrayList() }
        val same = list.indexOfFirst { it.zh == zh }
        if (same >= 0) { if (en != null && list[same].en == null) list[same] = list[same].copy(en = en); return }
        list += Line(atMs, zh, en)
        list.sortBy { it.atMs }
    }

    @Synchronized fun of(key: String): List<Line> = byVideo[key]?.toList().orEmpty()
}

/** "Jump to" asked from the transcript: the page playing that video seeks there. */
object SeekRequest {
    val request = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Long>?>(null)
}

/** Saying a line back: how much of it came through, character by character (0–100). */
object Shadowing {
    /** The share of [target]'s Chinese characters, in order, found in [said] (longest common subsequence). */
    fun score(target: String, said: String): Int {
        val a = target.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        val b = said.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        if (a.isEmpty()) return 0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1 else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
        return dp[a.length][b.length] * 100 / a.length
    }

    /** The best of the recogniser's guesses. */
    fun best(target: String, guesses: List<String>): Pair<String, Int>? = guesses.map { it to score(target, it) }.maxByOrNull { it.second }
}
