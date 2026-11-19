package dev.dyfeed

import dev.dyfeed.domain.lang.Zh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhTest {
    @Test
    fun `chinese is spotted anywhere in the text`() {
        assertTrue(Zh.has("重庆火锅"))
        assertTrue(Zh.has("best 火锅 ever"))
        assertFalse(Zh.has("hot pot"))
        assertFalse(Zh.has(null))
    }

    @Test
    fun `english searches are put into chinese, others are left alone`() {
        assertTrue(Zh.isLatinQuery("hot pot"))
        assertTrue(Zh.isLatinQuery("cats"))
        assertFalse(Zh.isLatinQuery("火锅"))
        assertFalse(Zh.isLatinQuery("5"))
        assertFalse(Zh.isLatinQuery("猫 cats"))
        assertFalse(Zh.isLatinQuery("кошки")) // not Latin script
    }
}
