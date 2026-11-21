package dev.dyfeed

import dev.dyfeed.source.kuaishou.KuaishouParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KuaishouTest {
    @Test
    fun `a real featured-feed entry becomes a post`() {
        val p = KuaishouParser.parse(Json.parseToJsonElement("{\"type\": 1, \"author\": {\"id\": \"3xgskysithwf8ry\", \"name\": \"苏晓晖的世界\", \"headerUrl\": \"https://p22.a.yximgs.com/uhead/AB/2023/06/01/19/BMjAyMzA2MDExOTI1NDlfMjE4Mjg5NDIwOV8yX2hkNjM3Xzk1OQ==_s.jpg\", \"__typename\": \"Author\"}, \"photo\": {\"id\": \"3xvequ9wcknq4xw\", \"duration\": 126480, \"caption\": \"乌克兰媒体为何要披露美“和平计划”？ #老铁看热点 #知识创作回归季 \", \"likeCount\": \"3891\", \"viewCount\": \"162002\", \"photoUrl\": \"https://v1.kwaicdn.com/ksc2/ZbP0jgOTxXvRGdkNQXdDbRemsPwe4360-dGeJ6_5tUa9OSy-bYqF_G5bY4APdEpLlMgNURhwqyKRxrDnmXiYFawZCtCbv-MDoScqvJkf18QswaWssWlUwkwD_yazE1ZpQWmxcVaECzBbTu-mepOLhkI0UZS3WMF2nornLYkpaAL7KWax8bslG6yiKLJbBXPc.mp4?pkey=AAUZ5xfJssVXBzLzSNXZLjxofW5U2v9ubaAVCXN_TSUIRS6zcum8ZYQBtqJK50NxzQLesISsXiJRqFZvO5KqUh2Z59tGT0WNqWi9wrDnvumytirU86KyTMYRp2PNtFwmA9w&tag=1-1795303091-unknown-0-d6paa3ht9g-65af7bac5256ad7f&clientCacheKey=3xvequ9wcknq4xw_b.mp4&di=449a2547&bp=14944&kwai-not-alloc=35&tt=b&ss=vps\", \"coverUrl\": \"https://p5.a.yximgs.com/upic/2026/09/09/17/BMjAyNjA5MDkxNzI4NTdfMjE4Mjg5NDIwOV8yMDgzODU1MTM4OTJfMF8z_Be077322a5bd5ab6f091896ca0ce8d086.jpg?tag=1-1795303091-xpcwebbrilliant-0-ktkfw2se4m-381e42368948d0ee&clientCacheKey=3xvequ9wcknq4xw.jpg&di=449a2547&bp=14944\", \"timestamp\": 1788946658883, \"__typename\": \"PhotoEntity\"}, \"tags\": [{\"type\": 1, \"name\": \"知识创作回归季\", \"__typename\": \"Tag\"}, {\"type\": 1, \"name\": \"老铁看热点\", \"__typename\": \"Tag\"}], \"__typename\": \"Feed\"}") as JsonObject)!!
        assertTrue(p.extras["media"]!!.startsWith("https://"))
        assertTrue(p.title!!.isNotBlank() && '#' !in p.title!!)
        assertTrue(p.tags.isNotEmpty())
        assertTrue((p.durationSec ?: 0) > 0)
    }

    @Test
    fun `counts in wan`() {
        assertEquals(58_000L, KuaishouParser.count("5.8万"))
        assertEquals(631_484L, KuaishouParser.count("631484"))
    }
}
