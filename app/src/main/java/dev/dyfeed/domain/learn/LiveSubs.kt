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

    private val WATERMARK = listOf("抖音号", "抖音", "DouYin", "TikTok", "关注", "点赞")
}
