package dev.dyfeed.ui.learn

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Reads Chinese aloud with the phone's own voice (Android text-to-speech, Mandarin). Started the
 * first time something is spoken; says why not when the phone has no Chinese voice.
 */
object Speak {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ok = false
    private val _problem = MutableStateFlow<String?>(null)
    /** Why nothing was said (no Chinese voice on the phone, …), or null. */
    val problem: StateFlow<String?> = _problem
    private var pending: String? = null

    /** Slower than normal while learning: 0.85×. */
    var rate = 0.85f

    fun say(context: Context, text: String) {
        val t = tts
        if (t == null) {
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                val engine = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) { _problem.value = "The phone's text-to-speech didn't start"; return@TextToSpeech }
                val r = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ok) { _problem.value = "No Chinese voice on this phone: Settings → Text-to-speech → install Chinese (Mandarin)"; return@TextToSpeech }
                engine.setSpeechRate(rate)
                pending?.let { engine.speak(it, TextToSpeech.QUEUE_FLUSH, null, "learn") }
                pending = null
            }
            return
        }
        if (!ok) return
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learn")
    }
}

/** Learning settings kept on the phone. */
object LearnPrefs {
    private const val FILE = "learn"
    private val _pinyin = MutableStateFlow(false)
    /** Pinyin above every Chinese caption in the feed. */
    val pinyin: StateFlow<Boolean> = _pinyin
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).also { _pinyin.value = it.getBoolean("pinyin", false) }
    }

    fun setPinyin(on: Boolean) { _pinyin.value = on; prefs?.edit()?.putBoolean("pinyin", on)?.apply() }
}

/** "Learn the words" asked for this text (a caption, a comment): the app shows its words. */
object LearnWords {
    val request = MutableStateFlow<String?>(null)
}
