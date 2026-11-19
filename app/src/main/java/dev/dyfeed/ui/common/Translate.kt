package dev.dyfeed.ui.common

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Chinese → English, on the phone (Google ML Kit). The language model (about 30 MB) downloads the
 * first time it's used; after that translation works offline. Nothing is loaded until then.
 */
object ChineseToEnglish {
    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build(),
        )
    }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** True once the model is on the phone (so a tap translates at once). */
    @Volatile var ready = false
        private set

    /** English for [text], or the failure (no connection for the first download, …). */
    suspend fun translate(text: String): Result<String> = runCatching {
        cache[text]?.let { return@runCatching it }
        if (!ready) {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
            ready = true
        }
        translator.translate(text).awaitTask().also { cache[text] = it }
    }

    /** Several at once, in order; any that fail come back as null. */
    suspend fun translateAll(texts: List<String>): List<String?> = texts.map { translate(it).getOrNull() }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
