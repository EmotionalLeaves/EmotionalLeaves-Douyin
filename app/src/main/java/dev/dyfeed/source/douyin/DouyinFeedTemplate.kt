package dev.dyfeed.source.douyin

/**
 * The page's own feed address, made ready to ask again: without the one-off signature and token
 * (the page's signer adds fresh ones as the request goes out), and with a new refresh_index so
 * Douyin hands out the next posts rather than the same ones.
 */
object DouyinFeedTemplate {
    private val DROP = setOf("a_bogus", "X-Bogus", "msToken", "_signature")

    fun prepare(url: String, round: Int): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val base = url.substring(0, q)
        val parts = url.substring(q + 1).split('&').filter { it.isNotEmpty() }
            .filterNot { it.substringBefore('=') in DROP }
            .map { p ->
                val k = p.substringBefore('=')
                val v = p.substringAfter('=', "")
                when (k) {
                    "refresh_index" -> "$k=${(v.toIntOrNull() ?: 1) + round}"
                    else -> p
                }
            }
        return if (parts.isEmpty()) base else base + "?" + parts.joinToString("&")
    }
}
