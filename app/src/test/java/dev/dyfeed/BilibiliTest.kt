package dev.dyfeed

import dev.dyfeed.source.bilibili.BilibiliParser
import dev.dyfeed.source.bilibili.Wbi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliTest {
    @Test
    fun `wbi signature matches the reference`() {
        // Reference computed with the probe's Python (the same algorithm that got answers from Bilibili).
        val key = Wbi.mixin("7cd084941338484aae1ad9425b84077c" + "4932caff0ff746eab6f01bf08b70ac45")
        assertEquals("ea1db124af3c7062474693fa704f4ff8", key)
        val signed = Wbi.sign(mapOf("search_type" to "video", "keyword" to "美食", "page" to "1"), key, 1_700_000_000)
        assertEquals("2feae5e03d3c829b6dc5013a2fd798c0", signed["w_rid"])
    }

    @Test
    fun `a real search result becomes a post`() {
        val o = Json.parseToJsonElement("{\"type\": \"video\", \"id\": 117653599427291, \"author\": \"小B的中间世界\", \"mid\": 244100385, \"typename\": \"美食测评\", \"arcurl\": \"http://www.bilibili.com/video/av117653599427291\", \"aid\": 117653599427291, \"bvid\": \"BV17hyaz7E4j\", \"title\": \"重现1660年狂野的奢华的神秘的带皮烤兔--香疯了！！！\", \"description\": \"这期略失体面了先生们，如果是在温暖的火炉旁慢烤慢吃，那一定奢华又美味！\", \"pic\": \"//i1.hdslb.com/bfs/archive/ccf9b6cb1ff58b4e96068fc6a99c74fb20e932f5.jpg\", \"play\": 94009, \"like\": 10720, \"tag\": \"风味美食图鉴,海岛求生,末日废土,美食制作,户外美食,美食测评,古法,复刻,大海,荒野求生\", \"duration\": \"11:33\", \"pubdate\": 1795252800}") as JsonObject
        val p = BilibiliParser.parse(o)!!
        assertEquals("BV17hyaz7E4j", p.id)
        assertEquals("小B的中间世界", p.creator)
        assertEquals(11 * 60 + 33, p.durationSec)
        assertTrue(p.thumbnailUrl!!.startsWith("https://"))
        assertTrue("美食制作" in p.tags)
        assertEquals("https://www.bilibili.com/video/BV17hyaz7E4j", p.pageUrl)
    }
}
