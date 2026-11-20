package dev.dyfeed.domain.learn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A word you saved to learn (生词本), with where you met it and when to see it again.
 * Spaced repetition, SM-2 style: each good answer pushes the next review further out.
 */
@Serializable
data class Card(
    val word: String,
    val pinyin: String,
    val english: String,
    /** The caption you met it in, if any: context for the review. */
    val example: String? = null,
    val addedAt: Long,
    val due: Long,
    val intervalDays: Double = 0.0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
)

enum class Grade { AGAIN, HARD, GOOD, EASY }

object Srs {
    private const val DAY = 24 * 60 * 60_000L
    private const val MIN_EASE = 1.3

    /** [card] after you answered [grade] at [now]: when it comes back, and how hard it now counts as. */
    fun review(card: Card, grade: Grade, now: Long): Card {
        if (grade == Grade.AGAIN) {
            // Forgotten: back in ten minutes, and it starts over.
            return card.copy(due = now + 10 * 60_000L, intervalDays = 0.0, reps = 0, lapses = card.lapses + 1, ease = (card.ease - 0.2).coerceAtLeast(MIN_EASE))
        }
        val ease = (card.ease + when (grade) { Grade.HARD -> -0.15; Grade.GOOD -> 0.0; Grade.EASY -> 0.15; Grade.AGAIN -> 0.0 }).coerceAtLeast(MIN_EASE)
        val interval = when (card.reps) {
            0 -> when (grade) { Grade.HARD -> 0.5; Grade.EASY -> 3.0; else -> 1.0 }
            1 -> when (grade) { Grade.HARD -> 2.0; Grade.EASY -> 6.0; else -> 3.0 }
            else -> card.intervalDays * when (grade) { Grade.HARD -> 1.2; Grade.EASY -> ease * 1.3; else -> ease }
        }
        return card.copy(due = now + (interval * DAY).toLong(), intervalDays = interval, ease = ease, reps = card.reps + 1)
    }

    fun due(cards: List<Card>, now: Long): List<Card> = cards.filter { it.due <= now }.sortedBy { it.due }

    /** Days in a row (ending today or yesterday) with at least one review, from the days you reviewed (epoch days). */
    fun streak(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}

/** Your saved words, kept on the phone (learn_words.json in the app's own storage). */
object WordBook {
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var file: java.io.File? = null
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    private val _days = MutableStateFlow<Set<Long>>(emptySet())
    /** Reviews answered today (resets each day): for the daily goal. */
    private val _today = MutableStateFlow(0)
    val reviewedToday: StateFlow<Int> = _today
    @Volatile private var todayDay = -1L
    @Volatile private var countFile: java.io.File? = null
    /** The days (epoch days, local) you reviewed at least one word. */
    val days: StateFlow<Set<Long>> = _days
    @Volatile private var daysFile: java.io.File? = null
    val cards: StateFlow<List<Card>> = _cards

    fun init(context: Context) {
        val f = java.io.File(context.filesDir, "learn_words.json")
        file = f
        _cards.value = runCatching { json.decodeFromString<List<Card>>(f.readText()) }.getOrDefault(emptyList())
        LearnBoost.words = _cards.value.map { it.word }.filter { it.length in 2..6 }.toSet()
        val c = java.io.File(context.filesDir, "learn_today.txt")
        countFile = c
        runCatching { c.readText().split(' ') }.getOrNull()?.let { p ->
            if (p.getOrNull(0)?.toLongOrNull() == today()) { todayDay = today(); _today.value = p.getOrNull(1)?.toIntOrNull() ?: 0 }
        }
        val d = java.io.File(context.filesDir, "learn_days.txt")
        daysFile = d
        _days.value = runCatching { d.readLines().mapNotNull { it.trim().toLongOrNull() }.toSet() }.getOrDefault(emptySet())
    }

    fun has(word: String): Boolean = _cards.value.any { it.word == word }

    fun add(entry: Entry, example: String?, now: Long = System.currentTimeMillis()) {
        if (has(entry.simplified)) return
        save(_cards.value + Card(entry.simplified, entry.pinyin, entry.english, example?.take(200), addedAt = now, due = now))
    }

    /** A whole line (a caption, a subtitle) to learn as one card: its pinyin from its words, its English given. */
    fun addSentence(text: String, tokens: List<Token>, english: String, now: Long = System.currentTimeMillis()) {
        val line = text.trim().take(MAX_SENTENCE)
        if (line.isEmpty() || has(line)) return
        val pinyin = tokens.joinToString(" ") { t -> t.entries.firstOrNull()?.pinyin ?: t.text.trim() }.replace(Regex("""\s+"""), " ").trim()
        save(_cards.value + Card(line, pinyin, english, example = null, addedAt = now, due = now))
    }

    private const val MAX_SENTENCE = 80

    /** Words from a backup, added to yours (where both have a word, the one reviewed more wins). */
    fun merge(incoming: List<Card>) {
        if (incoming.isEmpty()) return
        val byWord = _cards.value.associateBy { it.word }.toMutableMap()
        incoming.forEach { c -> val mine = byWord[c.word]; if (mine == null || c.reps > mine.reps) byWord[c.word] = c }
        save(byWord.values.toList())
    }

    fun remove(word: String) = save(_cards.value.filterNot { it.word == word })

    fun answer(card: Card, grade: Grade, now: Long = System.currentTimeMillis()) {
        save(_cards.value.map { if (it.word == card.word) Srs.review(it, grade, now) else it })
        val today = today(now)
        if (todayDay != today) { todayDay = today; _today.value = 0 }
        _today.value++
        val count = _today.value
        countFile?.let { f -> writer.execute { runCatching { f.writeText("$today $count") } } }
        if (today !in _days.value) {
            _days.value = (_days.value + today).sortedDescending().take(400).toSet()
            val days = _days.value
            daysFile?.let { f -> writer.execute { runCatching { f.writeText(days.joinToString("\n")) } } }
        }
    }

    /** Today, as a local epoch day. */
    fun today(now: Long = System.currentTimeMillis()): Long =
        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun save(next: List<Card>) {
        _cards.value = next
        LearnBoost.words = next.map { it.word }.filter { it.length in 2..6 }.toSet()
        val f = file ?: return
        // In the background, in order (saves often follow taps on the main thread).
        writer.execute { runCatching { f.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Card.serializer()), next)) } }
    }
}

/** A listening question: hear [answer] said, pick its meaning among [choices] (one of them is right). */
data class Question(val answer: Card, val choices: List<Card>)

/** Your words as tab-separated lines (word, pinyin, meaning, where you met it): Anki and spreadsheets import it. */
fun exportWords(cards: List<Card>): String = cards.sortedBy { it.addedAt }.joinToString("\n") { c ->
    listOf(c.word, Pinyin.marks(c.pinyin), c.english, c.example.orEmpty()).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
}

object Quiz {
    /**
     * Up to [count] questions from your words, the ones you know least first, each with three other
     * words' meanings as the wrong choices. Needs at least four words.
     */
    fun questions(cards: List<Card>, count: Int = 10, random: kotlin.random.Random = kotlin.random.Random.Default): List<Question> {
        val pool = cards.distinctBy { it.english }
        if (pool.size < 4) return emptyList()
        val order = pool.sortedWith(compareBy<Card>({ it.reps }, { it.intervalDays })).take(count)
        return order.map { target ->
            val wrong = pool.filter { it.word != target.word }.shuffled(random).take(3)
            Question(target, (wrong + target).shuffled(random))
        }
    }
}

/**
 * Learn → "More videos with your words": For You leans toward posts whose captions use words
 * you're learning, so you meet them again in real speech.
 */
object LearnBoost {
    @Volatile var on = false
    @Volatile var words: Set<String> = emptySet()
    /** Learn → Easier Chinese first: captions made mostly of beginner words rank higher. */
    @Volatile var easy = false
    private const val BONUS = 0.25
    /** At most this much either way: easy captions up, hard ones down, taste still leading. */
    private const val EASY_SPAN = 0.3

    fun bonus(item: dev.dyfeed.domain.model.MediaItem): Double {
        val title = item.title ?: return 0.0
        var b = 0.0
        val w = words
        if (on && w.isNotEmpty() && w.any { it in title }) b += BONUS
        if (easy) {
            val d = Dict.ready
            val h = Hsk.ready
            if (d != null && h != null) easyShare(d, h, title)?.let { b += EASY_SPAN * (2 * it - 1) }
        }
        return b
    }

    /**
     * The share of a caption's Chinese words that are beginner words (HSK 1–3), or null when it has
     * too few words to say (hashtags and names left out as far as possible).
     */
    fun easyShare(dict: ZhDictionary, hsk: HskTable, text: String): Double? {
        val words = dict.segment(text.replace(Regex("""[#＃@][^\s#＃@]+"""), " ")).filter { it.known && ZhDictionary.isHan(it.text[0]) }
        if (words.size < 3) return null
        return words.count { (hsk.level(it.text) ?: 9) <= 3 }.toDouble() / words.size
    }
}

/** Subtitle lines heard in videos lately (read live), kept on the phone for "Words you keep hearing". */
object Heard {
    private const val MAX = 300
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    @Volatile private var file: java.io.File? = null

    fun init(context: Context) {
        val f = java.io.File(context.filesDir, "learn_heard.txt")
        file = f
        _lines.value = runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        // Watch history: Clear history forgets what was heard too.
        dev.dyfeed.domain.privacy.Forget.register { clear() }
    }

    fun add(line: String) {
        if (dev.dyfeed.domain.privacy.Incognito.active) return
        val l = line.trim().replace('\n', ' ')
        if (l.isEmpty() || _lines.value.lastOrNull() == l) return
        _lines.value = (_lines.value + l).takeLast(MAX)
        // Off the main thread: this runs as subtitles come up, during playback.
        val snapshot = _lines.value
        file?.let { f -> writer.execute { runCatching { f.writeText(snapshot.joinToString("\n")) } } }
    }

    fun clear() { _lines.value = emptyList(); file?.delete() }
}
