package dev.dyfeed

import dev.dyfeed.source.douyin.DouyinFeedTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTemplateTest {
    @Test
    fun `drops the one-off signature, bumps refresh_index, keeps the rest`() {
        val u = "https://www.douyin.com/aweme/v2/web/module/feed/?device_platform=webapp&aid=6383&module_id=3003101&count=20&refresh_index=1&pull_type=0&msToken=abc&a_bogus=xyz"
        val out = DouyinFeedTemplate.prepare(u, 2)
        assertFalse(out.contains("a_bogus"))
        assertFalse(out.contains("msToken"))
        assertTrue(out.contains("refresh_index=3"))
        assertTrue(out.contains("module_id=3003101"))
        assertTrue(out.startsWith("https://www.douyin.com/aweme/v2/web/module/feed/?device_platform=webapp"))
    }

    @Test
    fun `an address without a query is left alone`() {
        assertEquals("https://www.douyin.com/x/", DouyinFeedTemplate.prepare("https://www.douyin.com/x/", 1))
    }
}
