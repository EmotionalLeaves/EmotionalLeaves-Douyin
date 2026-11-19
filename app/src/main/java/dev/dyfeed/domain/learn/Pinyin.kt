package dev.dyfeed.domain.learn

/**
 * Pinyin as the dictionary writes it ("ni3 hao3", "lu:4") → as people read it ("nǐ hǎo", "lǜ").
 * The tone mark goes on a or e if there is one, on the o of "ou", else on the last vowel.
 */
object Pinyin {
    private val MARKS = mapOf(
        'a' to "āáǎàa", 'e' to "ēéěèe", 'i' to "īíǐìi", 'o' to "ōóǒòo", 'u' to "ūúǔùu", 'ü' to "ǖǘǚǜü",
        'A' to "ĀÁǍÀA", 'E' to "ĒÉĚÈE", 'O' to "ŌÓǑÒO",
    )

    /** Every syllable of [numbered] with its tone mark; anything that isn't a syllable is kept as is. */
    fun marks(numbered: String): String = numbered.split(' ').joinToString(" ") { syllable(it) }

    /** The tone of each syllable (1–4, 5 for the neutral tone), for colouring. */
    fun tones(numbered: String): List<Int> = numbered.split(' ').map { it.lastOrNull()?.digitToIntOrNull()?.takeIf { t -> t in 1..5 } ?: 5 }

    private fun syllable(s: String): String {
        val tone = s.lastOrNull()?.digitToIntOrNull()?.takeIf { it in 1..5 } ?: return s.replace("u:", "ü").replace("U:", "Ü")
        val base = s.dropLast(1).replace("u:", "ü").replace("v", "ü").replace("U:", "Ü")
        if (tone == 5) return base
        val lower = base.lowercase()
        val at = when {
            'a' in lower -> lower.indexOf('a')
            'e' in lower -> lower.indexOf('e')
            "ou" in lower -> lower.indexOf('o')
            else -> lower.indexOfLast { it in "aeiouü" }
        }
        if (at < 0) return base
        val c = base[at]
        val marked = MARKS[c]?.get(tone - 1) ?: return base
        return base.substring(0, at) + marked + base.substring(at + 1)
    }
}
