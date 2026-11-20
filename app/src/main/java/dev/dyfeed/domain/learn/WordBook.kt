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
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var file: java.io.File? = null
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    private val _days = MutableStateFlow<Set<Long>>(emptySet())
    /** The days (epoch days, local) you reviewed at least one word. */
    val days: StateFlow<Set<Long>> = _days
    @Volatile private var daysFile: java.io.File? = null
    val cards: StateFlow<List<Card>> = _cards

    fun init(context: Context) {
        val f = java.io.File(context.filesDir, "learn_words.json")
        file = f
        _cards.value = runCatching { json.decodeFromString<List<Card>>(f.readText()) }.getOrDefault(emptyList())
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

    fun remove(word: String) = save(_cards.value.filterNot { it.word == word })

    fun answer(card: Card, grade: Grade, now: Long = System.currentTimeMillis()) {
        save(_cards.value.map { if (it.word == card.word) Srs.review(it, grade, now) else it })
        val today = today(now)
        if (today !in _days.value) {
            _days.value = (_days.value + today).sortedDescending().take(400).toSet()
            daysFile?.let { f -> runCatching { f.writeText(_days.value.joinToString("\n")) } }
        }
    }

    /** Today, as a local epoch day. */
    fun today(now: Long = System.currentTimeMillis()): Long =
        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun save(next: List<Card>) {
        _cards.value = next
        val f = file ?: return
        runCatching { f.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Card.serializer()), next)) }
    }
}

/** A listening question: hear [answer] said, pick its meaning among [choices] (one of them is right). */
data class Question(val answer: Card, val choices: List<Card>)

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
