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
           "author":{"nickname":"小王吃遍中国","sec_uid":"MS4wLjABAAAA_x"},
           "statistics":{"digg_count":123456},
           "text_extra":[{"hashtag_name":"美食"},{"hashtag_name":"重庆"}],
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
        assertEquals("MS4wLjABAAAA_x", v.extras["secUid"])
        assertEquals("42", v.extras["at"])
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
    fun `post ids in shared links`() {
        assertEquals("7300000000000000001", DouyinAdapter.awemeIdIn("https://www.douyin.com/video/7300000000000000001"))
        assertEquals("7300000000000000001", DouyinAdapter.awemeIdIn("https://www.douyin.com/?modal_id=7300000000000000001"))
        assertEquals("7300000000000000001", DouyinAdapter.awemeIdIn("https://www.iesdouyin.com/share/video/7300000000000000001/?region=CN"))
        assertEquals("7300000000000000003", DouyinAdapter.awemeIdIn("https://www.douyin.com/note/7300000000000000003"))
        assertNull(DouyinAdapter.awemeIdIn("https://www.douyin.com/user/MS4wLjABAAAA"))
        assertTrue(DouyinAdapter.isDouyinHost("v.douyin.com"))
    }
}
