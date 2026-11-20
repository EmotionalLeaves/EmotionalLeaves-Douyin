package dev.dyfeed.player

/**
 * How long videos take to start: from coming on screen (play asked) to their first frame, the
 * last [KEEP]. For the health test, to tell whether a change made starting faster or slower.
 */
object StartTimes {
    /** When the app process started (set in the Application), and when the first video drew its first frame. */
    @Volatile var appStartedAt = 0L
    @Volatile var firstVideoAt = 0L

    /** From the app starting to the first video on screen, or null if none has shown yet. */
    fun firstVideoMs(): Long? = if (firstVideoAt > 0 && appStartedAt > 0) firstVideoAt - appStartedAt else null

    private const val KEEP = 100
    private val times = ArrayDeque<Long>()

    @Synchronized fun add(ms: Long) { if (ms in 0..60_000) { times.addLast(ms); while (times.size > KEEP) times.removeFirst() } }

    @Synchronized fun clear() = times.clear()

    @Synchronized fun summary(): String {
        if (times.isEmpty()) return "none yet"
        val sorted = times.sorted()
        return "median ${sorted[sorted.size / 2]} ms, 90% under ${sorted[(sorted.size * 9 / 10).coerceAtMost(sorted.lastIndex)]} ms, over ${sorted.size}"
    }
}
