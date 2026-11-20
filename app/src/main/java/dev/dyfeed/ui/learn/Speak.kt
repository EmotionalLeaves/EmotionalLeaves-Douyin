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

    /** Slower than normal while learning (Settings → Learn → Slower voice). */
    val rate: Float get() = if (LearnPrefs.slowVoice.value) 0.75f else 1.0f

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

    private val _boost = MutableStateFlow(false)
    /** For You leans toward videos whose captions use your saved words. */
    val boost: StateFlow<Boolean> = _boost
    fun setBoost(on: Boolean) { _boost.value = on; dev.dyfeed.domain.learn.LearnBoost.on = on; prefs?.edit()?.putBoolean("boost", on)?.apply() }

    private val _live = MutableStateFlow(true)
    /** Subtitles burned into videos, read off the picture and shown in English as they're said. */
    val liveSubs: StateFlow<Boolean> = _live
    fun setLiveSubs(on: Boolean) { _live.value = on; prefs?.edit()?.putBoolean("live", on)?.apply() }

    private val _slow = MutableStateFlow(true)
    /** Read aloud a little slower than normal speech. */
    val slowVoice: StateFlow<Boolean> = _slow

    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).also {
            _pinyin.value = it.getBoolean("pinyin", false)
            _slow.value = it.getBoolean("slow", true)
            _live.value = it.getBoolean("live", true)
            _boost.value = it.getBoolean("boost", false)
            dev.dyfeed.domain.learn.LearnBoost.on = _boost.value
        }
    }

    fun setPinyin(on: Boolean) { _pinyin.value = on; prefs?.edit()?.putBoolean("pinyin", on)?.apply() }
    fun setSlowVoice(on: Boolean) { _slow.value = on; prefs?.edit()?.putBoolean("slow", on)?.apply() }
}

/** "Learn the words" asked for this text (a caption, a comment): the app shows its words. */
object LearnWords {
    val request = MutableStateFlow<String?>(null)
}
