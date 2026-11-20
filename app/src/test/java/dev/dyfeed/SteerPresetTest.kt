package dev.dyfeed

import dev.dyfeed.domain.reco.Steer
import org.junit.Assert.assertEquals
import org.junit.Test

class SteerPresetTest {
    @Test
    fun `the one-tap memes steer reads as four mores`() {
        val list = Steer.parse("more 搞笑, more 沙雕, more 梗图, more 段子", Steer.resolver(emptyMap()))
        // 沙雕 counts as 搞笑 (synonyms), so they merge into one.
        assertEquals(setOf(Steer.Kind.MORE), list.map { it.kind }.toSet())
        assertEquals(list.size, list.map { it.tag }.distinct().size)
        assert(list.size >= 3) { list.toString() }
    }
}
