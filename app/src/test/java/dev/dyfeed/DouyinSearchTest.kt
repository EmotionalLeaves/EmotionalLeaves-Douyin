package dev.dyfeed

import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.source.douyin.DouyinParser
import dev.dyfeed.source.douyin.GiveUpGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinSearchTest {
    private fun post(title: String?, tags: List<String> = emptyList(), creator: String? = null) = MediaItem(
        id = "1", sourceId = "douyin", type = MediaType.VIDEO, title = title, creator = creator, tags = tags,
        durationSec = null, thumbnailUrl = null, pageUrl = "", publishedAt = null, extras = emptyMap(),
    )

    @Test
    fun `loaded posts match every word, in caption, tags or creator`() {
        val p = post("重庆火锅太好吃了", tags = listOf("美食"), creator = "小王 Eats")
        assertTrue(DouyinParser.matches(p, "火锅"))
        assertTrue(DouyinParser.matches(p, "#美食"))
        assertTrue(DouyinParser.matches(p, "火锅 美食"))
        assertTrue(DouyinParser.matches(p, "eats"))
        assertFalse(DouyinParser.matches(p, "火锅 猫"))
        assertFalse(DouyinParser.matches(p, "   "))
    }

    @Test
    fun `a way of searching that keeps finding nothing is dropped, one that ever worked is kept`() = runBlocking {
        val dead = GiveUpGate(2)
        repeat(2) { dead.attempt { emptyList<Int>() } }
        assertFalse(dead.open)
        var asked = false
        assertEquals(emptyList<Int>(), dead.attempt { asked = true; listOf(1) })
        assertFalse(asked)

        dead.reset()
        assertTrue(dead.open)

        val flaky = GiveUpGate(2)
        flaky.attempt { listOf(1) }
        repeat(5) { flaky.attempt { emptyList<Int>() } }
        assertTrue(flaky.open)
    }

    @Test
    fun `a gate closed from an earlier session stays shut until reset`() {
        val g = dev.dyfeed.source.douyin.GiveUpGate(2)
        g.close()
        org.junit.Assert.assertFalse(g.open)
        g.reset()
        org.junit.Assert.assertTrue(g.open)
    }
}
