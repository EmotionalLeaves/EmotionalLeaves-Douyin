package dev.dyfeed.domain.learn

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One meaning of a word, as CC-CEDICT gives it. */
data class Entry(val simplified: String, val traditional: String, val pinyin: String, val english: String) {
    /** "nǐ hǎo" rather than "ni3 hao3". */
    val pinyinMarks: String get() = Pinyin.marks(pinyin)
}

/** A piece of a sentence: a dictionary word (with its meanings), or a character/run it doesn't know. */
data class Token(val text: String, val entries: List<Entry>) {
    val known: Boolean get() = entries.isNotEmpty()
}

/**
 * Chinese → English word list (CC-CEDICT, 120,000 words, CC BY-SA 4.0, bundled), and the splitting
 * of a sentence into its words: longest dictionary word first, as learners' readers do.
 */
class ZhDictionary(private val words: Map<String, List<Entry>>) {
    private val longest = words.keys.maxOfOrNull { it.length }?.coerceAtMost(MAX_WORD) ?: 1

    fun lookup(word: String): List<Entry> = words[word].orEmpty()

    /**
     * [text] as words, left to right; punctuation, Latin text and numbers come back as unknown pieces.
     * Longest words first, read both forwards and backwards, keeping the reading with fewer pieces
     * (then fewer lone characters; then the backward one, which is right more often in Chinese):
     * 吃重庆火锅 is 吃 / 重庆 / 火锅, not 吃重 / 庆 / 火锅.
     */
    fun segment(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            if (!isHan(text[i])) {
                val j = (i until text.length).firstOrNull { isHan(text[it]) } ?: text.length
                out += Token(text.substring(i, j), emptyList())
                i = j
                continue
            }
            val j = (i until text.length).firstOrNull { !isHan(text[it]) } ?: text.length
            val run = text.substring(i, j)
            val forward = forward(run)
            val backward = backward(run)
            fun singles(ws: List<String>) = ws.count { it.length == 1 }
            val best = when {
                forward.size != backward.size -> if (forward.size < backward.size) forward else backward
                singles(forward) < singles(backward) -> forward
                else -> backward
            }
            best.forEach { w -> out += Token(w, lookup(w)) }
            i = j
        }
        return out
    }

    private fun forward(run: String): List<String> {
        val ws = ArrayList<String>()
        var i = 0
        while (i < run.length) {
            val len = (minOf(longest, run.length - i) downTo 1).firstOrNull { words.containsKey(run.substring(i, i + it)) } ?: 1
            ws += run.substring(i, i + len)
            i += len
        }
        return ws
    }

    private fun backward(run: String): List<String> {
        val ws = ArrayList<String>()
        var end = run.length
        while (end > 0) {
            val len = (minOf(longest, end) downTo 1).firstOrNull { words.containsKey(run.substring(end - it, end)) } ?: 1
            ws += run.substring(end - len, end)
            end -= len
        }
        return ws.asReversed()
    }

    /** Words whose English, pinyin (with or without tones) or characters contain [q], best first. */
    fun search(q: String, limit: Int = 40): List<Entry> {
        val query = q.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        if (query.any(::isHan)) {
            lookup(query).takeIf { it.isNotEmpty() }?.let { exact -> return (exact + words.filterKeys { it != query && it.contains(query) }.values.flatten().sortedBy { it.simplified.length }).take(limit) }
            return words.filterKeys { it.contains(query) }.values.flatten().sortedBy { it.simplified.length }.take(limit)
        }
        val bare = query.replace(" ", "")
        val all = words.values.asSequence().flatten()
        val byPinyin = all.filter { e -> val p = e.pinyin.lowercase(); p.replace(" ", "") == bare || p.filter { it.isLetter() } == bare.filter { it.isLetter() } }.take(limit).toList()
        val word = Regex("""(^|[^a-z])${Regex.escape(query)}($|[^a-z])""")
        val byEnglish = all.filter { word.containsMatchIn(it.english.lowercase()) }
            .sortedWith(compareBy({ !it.english.lowercase().startsWith(query) }, { it.english.length }))
            .take(limit).toList()
        return (byPinyin + byEnglish).distinct().take(limit)
    }

    companion object {
        private const val MAX_WORD = 8
        fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

        /** Lines "simplified \t traditional (empty if the same) \t pinyin \t meanings". */
        fun parse(lines: Sequence<String>): ZhDictionary {
            val map = HashMap<String, MutableList<Entry>>(140_000)
            lines.forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@forEach
                val e = Entry(parts[0], parts[1].ifEmpty { parts[0] }, parts[2], parts[3])
                map.getOrPut(e.simplified) { ArrayList(1) } += e
                if (e.traditional != e.simplified) map.getOrPut(e.traditional) { ArrayList(1) } += e
            }
            return ZhDictionary(map)
        }
    }
}

/** The bundled dictionary, read once (about a second) the first time it's needed. */
object Dict {
    private val lock = Mutex()
    @Volatile private var loaded: ZhDictionary? = null

    /** Already in memory: lookups can be made at once. */
    val ready: ZhDictionary? get() = loaded

    suspend fun get(context: Context): ZhDictionary = loaded ?: lock.withLock {
        loaded ?: withContext(Dispatchers.IO) {
            context.assets.open("learn/zh_en.tsv.gz").use { raw ->
                java.util.zip.GZIPInputStream(raw).bufferedReader().useLines { ZhDictionary.parse(it) }
            }
        }.also { loaded = it }
    }
}

/** The words that keep coming up in what you watch: worth learning first. */
object Frequent {
    /**
     * Words of two or more characters met in at least [minTimes] of [captions] (each caption counts
     * once), most met first, leaving out [known] words. Single characters are mostly particles
     * (的, 了, 是) and are left out.
     */
    fun words(dict: ZhDictionary, captions: List<String>, known: Set<String>, minTimes: Int = 2, limit: Int = 24): List<Pair<Token, Int>> {
        val counts = HashMap<String, Int>()
        val tokens = HashMap<String, Token>()
        captions.forEach { c ->
            dict.segment(c).filter { it.known && it.text.length >= 2 && it.text !in known }.distinctBy { it.text }.forEach { t ->
                counts[t.text] = (counts[t.text] ?: 0) + 1
                tokens.putIfAbsent(t.text, t)
            }
        }
        return counts.entries.filter { it.value >= minTimes }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }.thenBy { it.key })
            .take(limit).map { tokens.getValue(it.key) to it.value }
    }
}
