package dev.dyfeed.domain.lang

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Chinese text: anything written in Han characters. */
object Zh {
    fun has(text: String?): Boolean = text != null && text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    /** Latin words and no Chinese ("hot pot", "cats"): worth putting into Chinese before searching Douyin. */
    fun isLatinQuery(q: String): Boolean = !has(q) && q.count { it.isLetter() } >= 2 && q.all { it.code < 0x250 }
}

/**
 * One direction of on-phone translation (Google ML Kit). The language model (about 30 MB, shared
 * by both directions) downloads the first time it's used; after that translation works offline.
 */
open class PhoneTranslator(private val from: String, private val to: String) {
    private val translator: Translator by lazy {
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
    }
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_CACHED
    }

    /** True once the model is on the phone (so a tap translates at once). */
    @Volatile var ready = false
        private set

    /** Already translated this session: at once, without waiting. */
    fun cached(text: String): String? = synchronized(cache) { cache[text] }

    /** The translation of [text], or the failure (no connection for the first download, …). */
    suspend fun translate(text: String): Result<String> = runCatching {
        cached(text)?.let { return@runCatching it }
        if (!ready) {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
            ready = true
        }
        translator.translate(text).awaitTask().also { synchronized(cache) { cache[text] = it } }
    }

    /** Several at once, in order; any that fail come back as null. */
    suspend fun translateAll(texts: List<String>): List<String?> = texts.map { translate(it).getOrNull() }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object { const val MAX_CACHED = 2000 }
}

/** Chinese on screen → English. */
object ChineseToEnglish : PhoneTranslator(TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH) {
    private val _on = MutableStateFlow(false)

    /** Settings → Show Chinese in English: captions, tags, trending, comments and results. */
    val on: StateFlow<Boolean> = _on
    var auto: Boolean
        get() = _on.value
        set(v) { _on.value = v }
}

/** What you type in English → Chinese, so a search for "hot pot" finds 火锅. */
object EnglishToChinese : PhoneTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.CHINESE)
