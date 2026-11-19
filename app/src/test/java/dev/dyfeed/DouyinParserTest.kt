package dev.dyfeed

import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.source.douyin.DouyinAdapter
import dev.dyfeed.source.douyin.DouyinParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinParserTest {
    private val feed = Json.parseToJsonElement(
        """
        {"status_code":0,"has_more":1,"aweme_list":[
          {"aweme_id":"7300000000000000001","desc":"重庆火锅太好吃了 #美食 #重庆","create_time":1700000000,
           "author":{"nickname":"小王吃遍中国","sec_uid":"MS4wLjABAAAA_x","avatar_thumb":{"url_list":["https://p3.douyinpic.com/a.jpeg"]}},
           "statistics":{"digg_count":123456},"music":{"title":"火锅之歌","author":"小王"},
           "text_extra":[{"hashtag_name":"美食","hashtag_id":"1575791821492238"},{"hashtag_name":"重庆"}],
           "video":{"duration":15300,
             "cover":{"url_list":["https://p3-pc-sign.douyinpic.com/c.heic","https://p3-pc-sign.douyinpic.com/c.jpeg?x=1"]},
             "play_addr":{"url_list":["https://v3-web.douyinvod.com/fallback.mp4"]},
             "bit_rate":[
               {"gear_name":"normal_1080_0","bit_rate":3000000,"is_h265":0,"play_addr":{"width":1080,"height":1920,"url_list":["https://v3-web.douyinvod.com/1080.mp4"]}},
               {"gear_name":"normal_720_0","bit_rate":1500000,"is_h265":0,"play_addr":{"width":720,"height":1280,"url_list":["https://v3-web.douyinvod.com/720.mp4"]}},
               {"gear_name":"adapt_1080_265","bit_rate":2000000,"is_h265":1,"play_addr":{"width":1080,"height":1920,"url_list":["https://v3-web.douyinvod.com/265.mp4"]}}
             ]}},
          {"aweme_id":"7300000000000000002","is_ads":true,"video":{"play_addr":{"url_list":["https://ad/x.mp4"]}}},
          {"aweme_id":"7300000000000000003","desc":"今天的穿搭","aweme_type":68,
           "author":{"nickname":"穿搭小姐姐"},
           "images":[{"url_list":["https://p3/1.webp"]},{"url_list":["https://p3/2.webp"]}]},
          {"aweme_id":"7300000000000000004","cell_room":{"rawdata":"{}"}}
        ]}
        """.trimIndent(),
    )

    @Test
    fun `videos, photo posts, ads and live rooms`() {
        val items = DouyinParser.parseAll(feed, maxHeight = 1080, fetchedAt = 42)
        assertEquals(listOf("7300000000000000001", "7300000000000000003"), items.map { it.id })

        val v = items[0]
        assertEquals(MediaType.VIDEO, v.type)
        assertEquals("https://v3-web.douyinvod.com/1080.mp4", v.extras["media"])
        assertEquals("小王吃遍中国", v.creator)
        assertEquals("重庆火锅太好吃了", v.title)
        assertEquals(listOf("美食", "重庆"), v.tags)
        assertEquals(15, v.durationSec)
        assertEquals("123456", v.extras["score"])
        assertEquals("火锅之歌 – 小王", v.extras["music"])
        assertEquals("MS4wLjABAAAA_x", v.extras["secUid"])
        assertEquals("https://p3.douyinpic.com/a.jpeg", v.extras["avatar"])
        assertEquals("42", v.extras["at"])
        assertEquals("美食=1575791821492238", v.extras["tagIds"])
        assertTrue(v.thumbnailUrl!!.contains("c.jpeg"))
        assertEquals("https://www.douyin.com/video/7300000000000000001", v.pageUrl)

        val photos = items[1]
        assertEquals(MediaType.IMAGE, photos.type)
        assertEquals(listOf("https://p3/1.webp", "https://p3/2.webp"), photos.pages)
        assertTrue(photos.isGallery)
    }

    @Test
    fun `captions lose their hashtags and mentions`() {
        assertEquals("今天的穿搭", DouyinParser.caption("今天的穿搭 #ootd #穿搭 @小美"))
        assertNull(DouyinParser.caption("#美食 #重庆"))
        assertEquals("a b", DouyinParser.caption("a  ＃全角   b"))
    }

    @Test
    fun `data saver picks the smaller H264 rendition`() {
        val v = DouyinParser.parseAll(feed, maxHeight = 720).first()
        assertEquals("https://v3-web.douyinvod.com/720.mp4", v.extras["media"])
    }

    @Test
    fun `search answers wrap each post`() {
        val search = Json.parseToJsonElement(
            """{"status_code":0,"has_more":1,"cursor":16,"data":[{"type":1,"aweme_info":{"aweme_id":"7311111111111111111",
               "video":{"play_addr":{"url_list":["http://v.douyinvod.com/playwm/x.mp4"]}}}}]}""",
        )
        val items = DouyinParser.parseAll(search, 1080)
        assertEquals(1, items.size)
        // Share-page addresses: watermark-free and https.
        assertEquals("https://v.douyinvod.com/play/x.mp4", items[0].extras["media"])
        assertTrue(DouyinParser.hasMore(search))
        assertEquals(16L, DouyinParser.long(search, "cursor"))
    }

    @Test
    fun `module feed cards carry each post, sometimes as a JSON string`() {
        val inner = """{"aweme_id":"7322222222222222222","desc":"猫 #萌宠","video":{"play_addr":{"url_list":["https://v.douyinvod.com/a.mp4"]}}}"""
        val root = Json.parseToJsonElement(
            """{"status_code":0,"has_more":true,"cards":[{"type":1,"aweme":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(inner))}},
               {"type":1,"aweme":{"aweme_id":"7333333333333333333","video":{"play_addr":{"url_list":["https://v.douyinvod.com/b.mp4"]}}}},
               {"type":2,"live":{}}]}""",
        )
        val items = DouyinParser.parseAll(root, 1080)
        assertEquals(listOf("7322222222222222222", "7333333333333333333"), items.map { it.id })
        assertEquals("猫", items[0].title)
        assertTrue(DouyinParser.hasMore(root))
    }

    @Test
    fun `the site's camelCase spelling of a post`() {
        val a = Json.parseToJsonElement(
            """{"awemeId":"7344444444444444444","desc":"成都 #旅行","createTime":1700000000,
               "authorInfo":{"nickname":"旅行者","secUid":"MS4w_y"},"stats":{"diggCount":5000},
               "textExtra":[{"hashtagName":"旅行"}],
               "video":{"duration":20000,"cover":"//p3.douyinpic.com/c.jpeg",
                 "playAddr":[{"src":"//v3-web.douyinvod.com/p.mp4"}],
                 "bitRateList":[{"bitRate":2000000,"isH265":0,"width":1080,"height":1920,"playAddr":[{"src":"//v3-web.douyinvod.com/1080.mp4"}]}]}}""",
        ) as kotlinx.serialization.json.JsonObject
        val v = DouyinParser.parse(a, 1080)!!
        assertEquals("7344444444444444444", v.id)
        assertEquals("https://v3-web.douyinvod.com/1080.mp4", v.extras["media"])
        assertEquals("https://p3.douyinpic.com/c.jpeg", v.thumbnailUrl)
        assertEquals("旅行者", v.creator)
        assertEquals("MS4w_y", v.extras["secUid"])
        assertEquals(listOf("旅行"), v.tags)
        assertEquals("5000", v.extras["score"])
        assertEquals(20, v.durationSec)
    }

    @Test
    fun `empty live fields don't make a post a live room`() {
        val a = Json.parseToJsonElement(
            """{"aweme_id":"7355555555555555555","live_reason":"","cell_room":null,"is_ads":false,
               "video":{"play_addr":{"url_list":["https://v.douyinvod.com/c.mp4"]}}}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("7355555555555555555", DouyinParser.parse(a, 1080)?.id)
        assertNull(DouyinParser.whyNot(a, 1080))
    }

    @Test
    fun `video ids in web search results, plain and inside redirect links`() {
        val html = """<a href="https://www.douyin.com/video/7366666666666666666">x</a>
            <a href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.douyin.com%2Fvideo%2F7377777777777777777&rut=1">y</a>
            <a href="https://www.douyin.com/user/MS4w">no</a> douyin.com/video/7366666666666666666"""
        assertEquals(listOf("7366666666666666666", "7377777777777777777"), DouyinAdapter.idsInHtml(html))
    }

    @Test
    fun `posts inside a page's embedded data, however deep`() {
        val root = Json.parseToJsonElement(
            """{"app":{"user":{}},"42":{"uid":"x","post":{"cursor":0,"data":[
                {"awemeId":"7388888888888888888","desc":"今天 #穿搭","video":{"playAddr":[{"src":"//v3-web.douyinvod.com/x.mp4"}]}},
                {"awemeId":"7399999999999999999","desc":"图集","images":[{"urlList":["https://p3.douyinpic.com/1.jpeg"]}]}
              ]}}}""",
        )
        val items = DouyinParser.parseAll(root, 1080)
        assertEquals(listOf("7388888888888888888", "7399999999999999999"), items.map { it.id })
        assertEquals("https://v3-web.douyinvod.com/x.mp4", items[0].extras["media"])
    }

    @Test
    fun `comments with their authors and likes, blanks skipped`() {
        val root = Json.parseToJsonElement(
            """{"status_code":0,"cursor":20,"has_more":1,"comments":[
               {"text":"太香了","digg_count":1520,"user":{"nickname":"吃货一号"}},
               {"text":"   ","user":{"nickname":"x"}},
               {"text":"在哪里？","user":{}}]}""",
        )
        assertEquals(listOf(Triple("吃货一号", "太香了", 1520), Triple("?", "在哪里？", 0)), DouyinParser.comments(root))
        assertEquals(20L, DouyinParser.long(root, "cursor"))
    }

    @Test
    fun `trending topics with their page ids`() {
        val root = Json.parseToJsonElement(
            """{"status_code":0,"data":{"word_list":[{"word":"中美元首会晤","sentence_id":"2305123","hot_value":11000000},
               {"word":"累了就出去玩","sentence_id":0}],"trending_list":[{"word":"新歌","sentence_id":"77"}]}}""",
        )
        assertEquals(listOf("中美元首会晤" to "2305123", "累了就出去玩" to null, "新歌" to "77"), DouyinParser.trendingTopics(root))
        assertEquals(mapOf("中美元首会晤" to 11_000_000L), DouyinParser.trendingHeat(root))
    }

    @Test
    fun `media addresses say when they expire`() {
        assertEquals(1_763_600_000_000L, DouyinParser.expiresAt("https://v3-web.douyinvod.com/a/b.mp4?a=6383&x-expires=1763600000&br=1"))
        assertEquals(1_763_600_000_000L, DouyinParser.expiresAt("https://x.zjcdn.com/v.mp4?Expires=1763600000"))
        assertNull(DouyinParser.expiresAt("https://v3-web.douyinvod.com/a/b.mp4?a=6383"))
        assertNull(DouyinParser.expiresAt("https://v.douyinvod.com/x.mp4?expire=12"))
    }

    @Test
    fun `post ids in shared links`() {
        assertEquals("7300000000000000001", DouyinAdapter.awemeIdIn("https://www.douyin.com/video/7300000000000000001"))
        assertEquals("7300000000000000001", DouyinAdapter.awemeIdIn("https://www.douyin.com/?modal_id=7300000000000000001"))
        assertEquals("7300000000000000001", DouyinAdapter.awemeIdIn("https://www.iesdouyin.com/share/video/7300000000000000001/?region=CN"))
        assertEquals("7300000000000000003", DouyinAdapter.awemeIdIn("https://www.douyin.com/note/7300000000000000003"))
        assertNull(DouyinAdapter.awemeIdIn("https://www.douyin.com/user/MS4wLjABAAAA"))
        assertTrue(DouyinAdapter.isDouyinHost("v.douyin.com"))
    }
}
