package dev.dyfeed

import dev.dyfeed.domain.filter.AiArt
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.source.douyin.DouyinParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDouyinTest {
    private fun post(title: String, tags: List<String> = emptyList(), extras: Map<String, String> = emptyMap()) =
        MediaItem(id = title, sourceId = "douyin", type = MediaType.VIDEO, title = title, creator = null, tags = tags, durationSec = null, thumbnailUrl = null, pageUrl = "", publishedAt = null, extras = extras)

    @Test
    fun `douyin's ai words and tags are caught, ordinary posts aren't`() {
        assertTrue(AiArt.isAi(post("一键生成！AI绘画太神奇了")))
        assertTrue(AiArt.isAi(post("这个数字人主播你能看出来吗")))
        assertTrue(AiArt.isAi(post("新作品", tags = listOf("AIGC"))))
        assertTrue(AiArt.isAi(post("小猫", extras = mapOf(AiArt.AI_LABEL to "1"))))
        assertFalse(AiArt.isAi(post("重庆火锅太好吃了 #美食")))
        assertFalse(AiArt.isAi(post("爱情故事 爱你一万年"))) // 爱 isn't "ai"
    }

    @Test
    fun `douyin's own ai label is read from the answer`() {
        val labelled = Json.parseToJsonElement("""{"aweme_id":"1","aigc_info":{"aigc_label_type":1,"created_by_ai":true}}""") as JsonObject
        val plain = Json.parseToJsonElement("""{"aweme_id":"2","aigc_info":{"aigc_label_type":0,"created_by_ai":false}}""") as JsonObject
        assertTrue(DouyinParser.aiLabelled(labelled))
        assertFalse(DouyinParser.aiLabelled(plain))
    }
}
