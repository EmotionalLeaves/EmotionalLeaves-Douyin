package dev.dyfeed.domain.learn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HSK levels (the Chinese proficiency test's word lists, HSK 3.0: 1–6, and 7 for its 7–9 band) and
 * how common each word is, from the Complete HSK Vocabulary (MIT, drkameleon), bundled.
 */
class HskTable(private val rows: Map<String, Row>) {
    data class Row(val level: Int?, val oldLevel: Int?, val rank: Int?)

    fun level(word: String): Int? = rows[word]?.level
    fun rank(word: String): Int? = rows[word]?.rank

    /** The next [n] words of [level] not in [have], commonest first. */
    fun next(level: Int, have: Set<String>, n: Int): List<String> =
        rows.entries.asSequence().filter { it.value.level == level && it.key !in have }
            .sortedBy { it.value.rank ?: Int.MAX_VALUE }.take(n).map { it.key }.toList()

    fun count(level: Int): Int = rows.values.count { it.level == level }

    companion object {
        /** Lines "word \t new level \t old level \t frequency rank" (empty when unknown). */
        fun parse(lines: Sequence<String>): HskTable = HskTable(
            lines.mapNotNull { l ->
                val p = l.split('\t')
                if (p.size < 4 || p[0].isBlank()) null else p[0] to Row(p[1].toIntOrNull(), p[2].toIntOrNull(), p[3].toIntOrNull())
            }.toMap(),
        )
    }
}

object Hsk {
    @Volatile private var table: HskTable? = null
    val ready: HskTable? get() = table

    suspend fun get(context: Context): HskTable = table ?: withContext(Dispatchers.IO) {
        runCatching { context.assets.open("learn/hsk.tsv").bufferedReader().useLines { HskTable.parse(it) } }.getOrDefault(HskTable(emptyMap()))
    }.also { table = it }
}

/** Tone practice: single beginner characters with one clear tone (1–4) to hear and name. */
object TonePractice {
    data class Round(val char: String, val syllable: String, val tone: Int)

    /** [n] rounds from HSK 1–2 single characters with a toned reading, mixed so each tone comes up. */
    fun rounds(dict: ZhDictionary, hsk: HskTable, n: Int = 10, random: kotlin.random.Random = kotlin.random.Random.Default): List<Round> {
        val pool = (1..2).flatMap { lv -> hsk.next(lv, emptySet(), 2000) }.filter { it.length == 1 }.mapNotNull { c ->
            val e = dict.lookup(c).firstOrNull() ?: return@mapNotNull null
            val syl = e.pinyin.lowercase()
            val tone = syl.lastOrNull()?.digitToIntOrNull()?.takeIf { it in 1..4 } ?: return@mapNotNull null
            if (' ' in syl) null else Round(c, syl.dropLast(1), tone)
        }
        val byTone = pool.groupBy { it.tone }
        return (0 until n).mapNotNull { i -> byTone[(i % 4) + 1]?.randomOrNull(random) }.shuffled(random)
    }
}
