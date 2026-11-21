package dev.dyfeed

import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.source.douyin.DouyinParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFitTest {
    private fun post(title: String, tags: List<String> = emptyList()) =
        MediaItem(id = title, sourceId = "douyin", type = MediaType.VIDEO, title = title, creator = null, tags = tags, durationSec = null, thumbnailUrl = null, pageUrl = "", publishedAt = null)

    @Test
    fun `a chinese search fits posts that say it in other words`() {
        assertEquals(1.0, DouyinParser.relevance(post("今天吃重庆火锅"), "火锅"), 1e-9)
        assertTrue(DouyinParser.relevance(post("猫咪太有趣了"), "有趣的猫") >= 0.6)
        assertTrue(DouyinParser.relevance(post("今天去爬山"), "有趣的猫") < 0.34)
        assertEquals(1.0, DouyinParser.relevance(post("新视频", tags = listOf("美食")), "美食"), 1e-9)
    }
}
