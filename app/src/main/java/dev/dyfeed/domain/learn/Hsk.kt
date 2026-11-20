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
