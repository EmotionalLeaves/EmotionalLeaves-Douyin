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
}

/** Your saved words, kept on the phone (learn_words.json in the app's own storage). */
object WordBook {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var file: java.io.File? = null
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards

    fun init(context: Context) {
        val f = java.io.File(context.filesDir, "learn_words.json")
        file = f
        _cards.value = runCatching { json.decodeFromString<List<Card>>(f.readText()) }.getOrDefault(emptyList())
    }

    fun has(word: String): Boolean = _cards.value.any { it.word == word }

    fun add(entry: Entry, example: String?, now: Long = System.currentTimeMillis()) {
        if (has(entry.simplified)) return
        save(_cards.value + Card(entry.simplified, entry.pinyin, entry.english, example?.take(200), addedAt = now, due = now))
    }

    fun remove(word: String) = save(_cards.value.filterNot { it.word == word })

    fun answer(card: Card, grade: Grade, now: Long = System.currentTimeMillis()) =
        save(_cards.value.map { if (it.word == card.word) Srs.review(it, grade, now) else it })

    private fun save(next: List<Card>) {
        _cards.value = next
        val f = file ?: return
        runCatching { f.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Card.serializer()), next)) }
    }
}
