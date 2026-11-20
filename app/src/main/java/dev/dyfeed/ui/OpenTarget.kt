package dev.dyfeed.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Where a home-screen shortcut asked to go: "learn", "review" or "search". */
object OpenTarget {
    const val EXTRA = "dev.dyfeed.open"
    val request = MutableStateFlow<String?>(null)

    /** Hold the app icon: Review words, Learn, Search. Set at start (the app id differs per build, so not in XML). */
    fun publish(context: Context) = runCatching {
        fun shortcut(id: String, short: String, long: String) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, dev.dyfeed.R.mipmap.ic_launcher))
            .setIntent(Intent(context, dev.dyfeed.MainActivity::class.java).setAction(Intent.ACTION_MAIN).putExtra(EXTRA, id))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            listOf(
                shortcut("review", "复习 Review", "Review your words"),
                shortcut("learn", "学习 Learn", "Learn Chinese"),
                shortcut("search", "发现 Search", "Search Douyin"),
            ),
        )
    }
}
