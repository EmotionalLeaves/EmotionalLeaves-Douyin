package dev.dyfeed

import dev.dyfeed.data.repo.PoolCache
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class PoolCacheTest {
    private fun post(id: String, media: String) = MediaItem(id = id, sourceId = "douyin", type = MediaType.VIDEO, title = null, creator = null, tags = emptyList(), durationSec = null, thumbnailUrl = null, pageUrl = "", publishedAt = null, extras = mapOf("media" to media))

    @Test
    fun `posts whose video address has expired are not brought back at launch`() {
        val now = 0x691e2a80L * 1000
        val expired = post("a", "https://v3-web.douyinvod.com/0123456789abcdef0123456789abcdef/691e2a70/video/x/")
        val fresh = post("b", "https://v3-web.douyinvod.com/0123456789abcdef0123456789abcdef/691e5000/video/x/")
        val unknown = post("c", "https://v3-web.douyinvod.com/x.mp4")
        assertEquals(listOf("b", "c"), PoolCache.stillPlayable(listOf(expired, fresh, unknown), now).map { it.id })
    }
}
