package dev.pfeed.source

import dev.pfeed.domain.model.MediaItem
import dev.pfeed.domain.model.MediaType
import dev.pfeed.source.parse.MediaTypes
import dev.pfeed.source.douyin.DouyinAdapter
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a link shared to the app (from a browser, a chat, anywhere) into a post it can play:
 * a Douyin video or photo post, a direct video or picture file, or, for anything else, the page
 * itself in the in-app player.
 */
@Singleton
class LinkOpener @Inject constructor(
    private val douyin: DouyinAdapter,
) {
    /** The first web link in [text] (shared text often has a title around the URL). */
    fun findUrl(text: String?): HttpUrl? =
        text?.let { URL.find(it)?.value?.trimEnd('.', ',', ')', ']', '"', '\'', '，', '。') }?.toHttpUrlOrNull()

    suspend fun open(url: HttpUrl): MediaItem? {
        val host = url.host.removePrefix("www.").removePrefix("m.")
        val s = url.toString()
        // Douyin: v.douyin.com short links and douyin.com/video|note/<id>, played from the post itself.
        if (DouyinAdapter.isDouyinHost(url.host)) {
            runCatching { douyin.fromLink(url) }.getOrNull()?.let { return it }
        }
        // A file: plays as it is.
        MediaTypes.classify(s)?.let { type ->
            return MediaItem(
                id = sha1Hex(s),
                sourceId = DIRECT,
                type = type,
                title = url.pathSegments.lastOrNull()?.substringBeforeLast('.')?.ifBlank { null },
                creator = host,
                tags = emptyList(),
                durationSec = null,
                thumbnailUrl = if (type == MediaType.IMAGE || type == MediaType.GIF) s else null,
                pageUrl = s,
                publishedAt = null,
                extras = mapOf("media" to s),
            )
        }
        // Anything else: the page in the in-app player.
        return MediaItem(
            id = sha1Hex(s),
            sourceId = DIRECT,
            type = MediaType.EMBED,
            title = titleFromSlug(url) ?: host,
            creator = host,
            tags = emptyList(),
            durationSec = null,
            thumbnailUrl = null,
            pageUrl = s,
            publishedAt = null,
            extras = mapOf("player" to s),
        )
    }

    companion object {
        private const val DIRECT = "direct"
        private val URL = Regex("""https?://[^\s<>"]+""")

        /** The site's own id for a video page, by the usual URL shapes. */
        fun videoId(url: HttpUrl): String {
            url.queryParameter("viewkey")?.let { return it } // pornhub family
            val segs = url.pathSegments.filter { it.isNotBlank() }
            segs.firstOrNull { it.startsWith("video-") }?.let { return it.removePrefix("video-") } // eporner
            segs.firstOrNull { it.startsWith("video") && it.drop(5).all(Char::isDigit) && it.length > 5 }?.let { return it.drop(5) }
            // A numeric segment (/videos/123456/slug), else the id glued to a slug's end (title-xhAb12).
            segs.firstOrNull { it.all(Char::isDigit) }?.let { return it }
            val last = segs.lastOrNull().orEmpty()
            Regex("""-([A-Za-z0-9]{5,})$""").find(last)?.let { if (it.groupValues[1].any(Char::isDigit)) return it.groupValues[1] }
            return last.ifBlank { sha1Hex(url.toString()).take(12) }
        }

        private fun titleFromSlug(url: HttpUrl): String? =
            url.pathSegments.filter { it.isNotBlank() }
                .maxByOrNull { seg -> seg.count { it == '-' || it == '_' } }
                ?.takeIf { seg -> seg.count { it == '-' || it == '_' } >= 2 }
                ?.replace('-', ' ')?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() }
    }
}
