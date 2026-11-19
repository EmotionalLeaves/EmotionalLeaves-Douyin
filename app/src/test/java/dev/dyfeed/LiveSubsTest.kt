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
}
