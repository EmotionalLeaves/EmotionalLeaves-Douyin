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
        // Stricter (the user wants none): "AI" alone, the AI tools' names, AI singers and presenters.
        assertTrue(AiArt.isAi(post("用AI做了一个视频，太真实了")))
        assertTrue(AiArt.isAi(post("新作品", tags = listOf("即梦"))))
        assertTrue(AiArt.isAi(post("可灵生成的猫猫跳舞")))
        assertTrue(AiArt.isAi(post("虚拟主播今天唱歌")))
        assertTrue(AiArt.isAi(post("第一集", tags = listOf("漫剧"))))
        assertTrue(AiArt.isAi(post("新作品").copy(creator = "呀呀漫剧")))
        assertTrue(AiArt.isAi(post("小猫咪", extras = emptyMap()).copy(creator = "AI小助手")))
        // But not words that merely contain the letters.
        assertFalse(AiArt.isAi(post("Thai food 泰国美食 paint the wall")))
        assertFalse(AiArt.isAi(post("重庆火锅太好吃了 #美食 #探店")))
    }

    @Test
    fun `douyin's own ai label is read from the answer`() {
        val labelled = Json.parseToJsonElement("""{"aweme_id":"1","aigc_info":{"aigc_label_type":1,"created_by_ai":true}}""") as JsonObject
        val plain = Json.parseToJsonElement("""{"aweme_id":"2","aigc_info":{"aigc_label_type":0,"created_by_ai":false}}""") as JsonObject
        assertTrue(DouyinParser.aiLabelled(labelled))
        assertFalse(DouyinParser.aiLabelled(plain))
    }

    @Test
    fun `other aigc-named fields on every post don't count as an ai label`() {
        // As on the test phone: every post carries these, none made with AI.
        val ordinary = Json.parseToJsonElement("""{"aweme_id":"3","is_aigc_media":false,"item_aigc_follow_shot":1,"aigc_chat_follow_shoot_property":null,"aigc_follow_shot_left_top_label":null}""") as JsonObject
        assertFalse(DouyinParser.aiLabelled(ordinary))
        val flagged = Json.parseToJsonElement("""{"aweme_id":"4","is_aigc_media":true,"item_aigc_follow_shot":1}""") as JsonObject
        assertTrue(DouyinParser.aiLabelled(flagged))
    }
}
