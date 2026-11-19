package dev.dyfeed

import dev.dyfeed.domain.learn.Card
import dev.dyfeed.domain.learn.Grade
import dev.dyfeed.domain.learn.Pinyin
import dev.dyfeed.domain.learn.Srs
import dev.dyfeed.domain.learn.ZhDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnTest {
    private val dict = ZhDictionary.parse(
        sequenceOf(
            "你\t\tni3\tyou",
            "好\t\thao3\tgood; well",
            "你好\t\tni3 hao3\thello; hi",
            "重庆\t重慶\tChong2 qing4\tChongqing",
            "火锅\t火鍋\thuo3 guo1\thotpot",
            "太\t\ttai4\ttoo (much)",
            "好吃\t\thao3 chi1\ttasty; delicious",
            "了\t\tle5\t(completed action marker)",
            "绿\t綠\tlu:4\tgreen",
            "我\t\two3\tI; me",
            "喜欢\t喜歡\txi3 huan5\tto like",
            "吃\t\tchi1\tto eat",
            "吃重\t\tchi1 zhong4\tarduous",
            "庆\t慶\tqing4\tto celebrate",
        ),
    )

    @Test
    fun `pinyin gets its tone marks where readers expect them`() {
        assertEquals("nǐ hǎo", Pinyin.marks("ni3 hao3"))
        assertEquals("Chóng qìng", Pinyin.marks("Chong2 qing4"))
        assertEquals("lǜ", Pinyin.marks("lu:4"))
        assertEquals("duō", Pinyin.marks("duo1"))
        assertEquals("zǒu", Pinyin.marks("zou3"))
        assertEquals("guì", Pinyin.marks("gui4"))
        assertEquals("le", Pinyin.marks("le5"))
        assertEquals("xiè xie", Pinyin.marks("xie4 xie5"))
        assertEquals(listOf(3, 3), Pinyin.tones("ni3 hao3"))
    }

    @Test
    fun `a caption is split into the longest words the dictionary knows`() {
        val words = dict.segment("重庆火锅太好吃了！#美食")
        assertEquals(listOf("重庆", "火锅", "太", "好吃", "了", "！#", "美", "食"), words.map { it.text })
        assertEquals("hotpot", words[1].entries.single().english)
        assertTrue(words[5].entries.isEmpty())
        assertEquals("hello; hi", dict.segment("你好")[0].entries.single().english)
        // Not 吃重 / 庆: the reading with the better words wins.
        assertEquals(listOf("我", "喜欢", "吃", "重庆", "火锅"), dict.segment("我喜欢吃重庆火锅").map { it.text })
    }

    @Test
    fun `traditional characters find the same word`() {
        assertEquals("火锅", dict.lookup("火鍋").single().simplified)
    }

    @Test
    fun `words are found by english, by pinyin with or without tones, and by characters`() {
        assertEquals("火锅", dict.search("hotpot").first().simplified)
        assertEquals("你好", dict.search("nihao").first().simplified)
        assertEquals("你好", dict.search("ni3 hao3").first().simplified)
        assertEquals("好", dict.search("好").first().simplified)
        assertEquals("好吃", dict.search("tasty").first().simplified)
    }

    @Test
    fun `reviews space out with each good answer and start over when forgotten`() {
        val day = 24 * 60 * 60_000L
        var c = Card("火锅", "huo3 guo1", "hotpot", addedAt = 0, due = 0)
        c = Srs.review(c, Grade.GOOD, 0); assertEquals(1 * day, c.due)
        c = Srs.review(c, Grade.GOOD, c.due); assertEquals(1 * day + 3 * day, c.due)
        val third = Srs.review(c, Grade.GOOD, c.due)
        assertEquals(7.5, third.intervalDays, 0.01)
        val forgot = Srs.review(third, Grade.AGAIN, third.due)
        assertEquals(0, forgot.reps)
        assertEquals(third.due + 10 * 60_000L, forgot.due)
        assertTrue(forgot.ease < third.ease)
        assertEquals(listOf("a"), Srs.due(listOf(c.copy(word = "a", due = 5), c.copy(word = "b", due = 50)), 10).map { it.word })
    }

    @Test
    fun `the bundled dictionary knows everyday words and splits real sentences`() {
        val f = listOf("src/main/assets/learn/zh_en.tsv.gz", "app/src/main/assets/learn/zh_en.tsv.gz").map { java.io.File(it) }.first { it.exists() }
        val real = java.util.zip.GZIPInputStream(f.inputStream()).bufferedReader().useLines { ZhDictionary.parse(it) }
        assertTrue(real.lookup("你好").any { "hello" in it.english })
        assertEquals(listOf("我", "喜欢", "吃", "重庆", "火锅"), real.segment("我喜欢吃重庆火锅").map { it.text })
        assertEquals("xǐ huan", real.lookup("喜欢").first().pinyinMarks)
    }

    @Test
    fun `words you keep meeting come first, without the ones you saved or lone characters`() {
        val captions = listOf("重庆火锅太好吃了", "火锅好吃", "我喜欢吃火锅", "你好重庆", "好吃")
        val top = dev.dyfeed.domain.learn.Frequent.words(dict, captions, known = setOf("重庆"))
        assertEquals(listOf("好吃" to 3, "火锅" to 3), top.map { it.first.text to it.second })
    }

    @Test
    fun `a streak counts days in a row up to today or yesterday`() {
        assertEquals(3, Srs.streak(setOf(10L, 9L, 8L, 5L), 10))
        assertEquals(3, Srs.streak(setOf(9L, 8L, 7L), 10)) // not yet today: still alive
        assertEquals(0, Srs.streak(setOf(7L), 10))
    }
}
