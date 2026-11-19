package dev.pfeed

import dev.pfeed.domain.model.MediaItem
import dev.pfeed.domain.model.MediaType
import dev.pfeed.domain.reco.RankWeights
import dev.pfeed.domain.reco.Ranker
import dev.pfeed.domain.reco.Spread
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/** With Douyin the only source, the spacing rules must still let a full page through. */
class SingleSourceFeedTest {
    private fun dy(i: Int, creator: String = "creator$i") = MediaItem(
        id = "73000000000000$i", sourceId = "douyin", type = MediaType.VIDEO, title = "视频 $i", creator = creator,
        tags = listOf(if (i % 2 == 0) "美食" else "搞笑"), durationSec = 15, thumbnailUrl = null,
        pageUrl = "https://www.douyin.com/video/73000000000000$i", publishedAt = Instant.now(), extras = mapOf("media" to "m"),
    )

    @Test
    fun `a page of Douyin posts is not held back as one long run`() {
        val items = (1..40).map { dy(it) }
        val ordered = Spread.order(items, recent = emptyList(), limit = 20, allowForcedRun = false)
        assertTrue("only ${ordered.size} of 20", ordered.size >= 20)
        val ranked = Ranker(RankWeights(exploration = 0.0), Random(1))
            .rank(items, mapOf("美食" to 3.0), emptyMap(), Instant.now(), limit = 20, allowForcedRun = false)
        assertTrue("For You let only ${ranked.size} through", ranked.size >= 20)
    }

    @Test
    fun `one creator still can't flood the feed`() {
        val items = (1..10).map { dy(it, creator = "same") } + (11..30).map { dy(it) }
        val ordered = Spread.order(items, recent = emptyList(), limit = 12, allowForcedRun = false)
        assertTrue(ordered.count { it.creator == "same" } <= Spread.MAX_PER_CREATOR)
    }
}
