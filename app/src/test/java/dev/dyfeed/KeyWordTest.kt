package dev.dyfeed

import dev.dyfeed.domain.learn.HskTable
import dev.dyfeed.domain.learn.KeyWord
import dev.dyfeed.domain.learn.ZhDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyWordTest {
    private val dict = ZhDictionary.parse(sequenceOf(
        "我\t\two3\tI", "喜欢\t\txi3 huan5\tto like", "吃\t\tchi1\tto eat", "重庆\t\tChong2 qing4\tChongqing", "火锅\t\thuo3 guo1\thot pot",
    ))
    private val hsk = HskTable.parse(sequenceOf("喜欢\t1\t1\t100", "火锅\t5\t\t3000"))

    @Test
    fun `picks the most useful unsaved word, never a name`() {
        assertEquals("喜欢", KeyWord.pick(dict, hsk, "我喜欢吃重庆火锅", emptySet())?.text)
        assertEquals("火锅", KeyWord.pick(dict, hsk, "我喜欢吃重庆火锅", setOf("喜欢"))?.text)
        assertNull(KeyWord.pick(dict, hsk, "我喜欢吃重庆火锅", setOf("喜欢", "火锅")))
    }
}
