package dev.dyfeed.source.douyin

import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap

/**
 * Creators' profile pictures, learned from their posts and kept across restarts, so every round
 * avatar in the app (feed, creator pages, Following, Library) can show the real face.
 */
object CreatorAvatars {
    private const val PREFIX = "avatar:"
    private val memory = ConcurrentHashMap<String, String>()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("douyin_creators", Context.MODE_PRIVATE)
    }

    private fun key(name: String) = PREFIX + name.removePrefix("@").substringBefore('@').lowercase().filterNot { it.isWhitespace() || it == '_' }

    fun remember(name: String, url: String) {
        val k = key(name)
        if (memory.put(k, url) != url) prefs?.edit { putString(k, url) }
    }

    fun get(name: String): String? {
        val k = key(name)
        return memory[k] ?: prefs?.getString(k, null)?.also { memory[k] = it }
    }
}
