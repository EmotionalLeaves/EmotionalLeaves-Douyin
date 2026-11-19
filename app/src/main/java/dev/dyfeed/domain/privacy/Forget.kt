package dev.dyfeed.domain.privacy

/**
 * Things kept outside the database that "Clear history" must also clear (the Douyin search index).
 * Each registers how to forget itself; [all] runs them.
 */
object Forget {
    private val hooks = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun register(hook: () -> Unit) { hooks += hook }

    fun all() = hooks.forEach { runCatching { it() } }
}
