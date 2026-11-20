package dev.dyfeed

import dev.dyfeed.domain.learn.LiveSubs
import dev.dyfeed.domain.learn.LiveSubs.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSubsTest {
    @Test
    fun `the spoken line is the centred chinese low in the picture`() {
        val lines = listOf(
            Line("今日份快乐", 0.5f, 0.12f, 0.04f), // a title up top
            Line("抖音号：12345", 0.8f, 0.9f, 0.02f), // the watermark
            Line("我们今天去吃火锅", 0.5f, 0.78f, 0.035f),
            Line("OK", 0.5f, 0.6f, 0.03f),
        )
        assertEquals("我们今天去吃火锅", LiveSubs.pick(lines))
    }

    @Test
    fun `a two-line subtitle is read as one`() {
        val lines = listOf(Line("你知道吗 这家店", 0.5f, 0.74f, 0.035f), Line("开了三十年了", 0.5f, 0.79f, 0.035f))
        assertEquals("你知道吗这家店开了三十年了", LiveSubs.pick(lines))
    }

    @Test
    fun `nothing that looks like a subtitle gives nothing`() {
        assertNull(LiveSubs.pick(listOf(Line("@小王", 0.5f, 0.8f, 0.03f), Line("hello", 0.5f, 0.8f, 0.03f))))
    }

    @Test
    fun `a line read slightly differently is still the same line`() {
        assertTrue(LiveSubs.same("我们今天去吃火锅", "我们今天去吃火锅"))
        assertTrue(LiveSubs.same("我们今天去吃火锅", "我们今天去吃火锅！"))
        assertFalse(LiveSubs.same("我们今天去吃火锅", "明天见"))
    }

    @Test
    fun `a transcript keeps each line once, in time order, filling in its english`() {
        val t = dev.dyfeed.domain.learn.Transcripts
        t.add("v1", 5_000, "第二句", null)
        t.add("v1", 1_000, "第一句", "first line")
        t.add("v1", 9_000, "第二句", "second line") // the same line again, later in a loop
        assertEquals(listOf("第一句" to "first line", "第二句" to "second line"), t.of("v1").map { it.zh to it.en })
        assertEquals(listOf(1_000L, 5_000L), t.of("v1").map { it.atMs })
    }

    @Test
    fun `saying a line back is scored by how much of it came through`() {
        val s = dev.dyfeed.domain.learn.Shadowing
        assertEquals(100, s.score("我们今天去吃火锅", "我们今天去吃火锅。"))
        assertEquals(75, s.score("我们今天去吃火锅", "我们今天吃锅"))
        assertEquals(0, s.score("我们今天去吃火锅", "hello"))
        assertEquals("我们去吃火锅" to 75, s.best("我们今天去吃火锅", listOf("我们去吃火锅", "我们")))
    }
}
