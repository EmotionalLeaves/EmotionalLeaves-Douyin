package dev.dyfeed.ui.feed

import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.domain.model.StreamInfo
import dev.dyfeed.source.parse.MediaTypes
import dev.dyfeed.domain.reco.Features

/**
 * How an item's metadata is shown in the feed overlay. Sources hand back raw, technical values
 * ("booru", "/u/name", "12345678@onlyfans", "highres", "rating:explicit"); this turns them into what
 * a person wants to read, without changing the underlying data the app learns from.
 */
object Display {

    /** Page [index] of a gallery as a stand-alone image item (so the normal image page can show it). */
    fun galleryPage(item: MediaItem, index: Int): MediaItem = item.copy(
        id = "${item.id}#$index",
        type = MediaType.IMAGE,
        extras = (item.extras - "full" - "resolve") + ("media" to item.pages[index]),
        pages = emptyList(),
    )

    /**
     * The type to render an item as once its file is known. Listings often only know a thumbnail:
     * a post listed as an image whose file is really an MP4/WebM must play as a video.
     */
    fun effectiveType(item: MediaItem, stream: StreamInfo?): MediaType {
        // A video whose page didn't expose a stream plays in the site's own embed player.
        if (stream is StreamInfo.Embed) return MediaType.EMBED
        val direct = stream as? StreamInfo.Direct ?: return item.type
        val actual = MediaTypes.classify(direct.url, direct.mimeType) ?: return item.type
        return when {
            // Listed as a still, file is a clip → play it.
            (item.type == MediaType.IMAGE || item.type == MediaType.GIF) && actual == MediaType.VIDEO -> MediaType.VIDEO
            // Listed as a clip (boards tag GIFs "animated" too), file is a GIF → show it as a GIF;
            // the video player can't play one and the post was being dropped as broken.
            item.type == MediaType.VIDEO && actual == MediaType.GIF -> MediaType.GIF
            item.type == MediaType.IMAGE && actual == MediaType.GIF -> MediaType.GIF
            else -> item.type
        }
    }

    /** A video post whose only file is a still image (the clip couldn't be found): nothing to play. */
    fun isStillForVideo(item: MediaItem, stream: StreamInfo?): Boolean {
        val s = stream as? StreamInfo.Direct ?: return false
        if (item.type != MediaType.VIDEO) return false
        val actual = MediaTypes.classify(s.url, s.mimeType)
        return actual == MediaType.IMAGE
    }

    /** How long ago it was posted ("5m", "3h", "2d", "6w"), when the source said; null for guessed or old dates. */
    fun age(item: MediaItem, now: java.time.Instant = java.time.Instant.now()): String? {
        val at = item.publishedAt ?: return null
        if (item.extras[MediaItem.APPROX_DATE] != null) return null
        val s = java.time.Duration.between(at, now).seconds
        return when {
            s < 0 -> null
            s < 3600 -> "${(s / 60).coerceAtLeast(1)}m"
            s < 86_400 -> "${s / 3600}h"
            s < 14 * 86_400 -> "${s / 86_400}d"
            s < 365 * 86_400 -> "${s / (7 * 86_400)}w"
            else -> null
        }
    }

    /** Short, recognisable name of where an item came from: "抖音", a folder, a site… */
    fun source(item: MediaItem, fallback: String): String {
        return when (item.sourceId) {
            "douyin" -> "抖音"
            else -> fallback
        }
    }

    /** The creator as a readable handle, or null when there's nothing meaningful to show. */
    fun creator(item: MediaItem): String? {
        var c = item.creator?.trim().orEmpty()
        c = c.removePrefix("/u/").removePrefix("u/").removePrefix("@")
        if (c.isBlank()) return null
        // Long numeric account ids (Patreon/Fanbox) mean nothing to a reader.
        if (c.length > 5 && c.all(Char::isDigit)) return null
        return c
    }

    /**
     * Up to [max] tags worth tapping: drops ratings, file formats, booru bookkeeping tags and
     * internal markers, and the subreddit name already shown as the source. Returns
     * (label, tag-to-open) pairs.
     */
    fun tags(item: MediaItem, max: Int = 5): List<Pair<String, String>> {
        val skipFirst = item.sourceId == "reddit"
        return item.tags.asSequence()
            .drop(if (skipFirst) 1 else 0)
            .map { it.trim() }
            .filter { it.length in 2..28 || (it.length == 1 && it[0].code >= 0x2E80) }
            .filterNot { it.startsWith("creator:") }
            .map { t -> if (t.contains(':') && t.substringBefore(':').lowercase() in NAME_PREFIXES) t.substringAfter(':') else t }
            .filterNot { it.contains(':') && it.substringBefore(':').lowercase() in META_PREFIXES }
            .filterNot { Features.isGeneric(it) }
            .filterNot { Features.normalizeTag(it) in BOOKKEEPING }
            .filterNot { it.all(Char::isDigit) }
            .distinctBy { Features.normalizeTag(it) }
            .take(max)
            .map { raw -> raw.replace('_', ' ') to raw }
            .toList()
    }

    /** A count the way Douyin writes it: up to 9,999 as is, then 万 (10 thousand) and 亿 (100 million). */
    fun count(n: Long): String = when {
        n < 10_000 -> "%,d".format(n)
        n < 100_000_000 -> trim(n / 10_000.0) + "万"
        else -> trim(n / 100_000_000.0) + "亿"
    }

    private fun trim(x: Double): String = if (x >= 100) "%.0f".format(x) else "%.1f".format(x).removeSuffix(".0")

    private val META_PREFIXES = setOf("rating", "score", "order", "sort", "source", "md5", "id", "width", "height", "status", "language", "artist", "group")

    /** Named-thing prefixes (nhentai, boards): shown and searched without the prefix. */
    private val NAME_PREFIXES = setOf("parody", "character", "series", "copyright")

    /** Booru housekeeping tags: about the upload, not the content. */
    private val BOOKKEEPING = setOf(
        "1girl", "1boy", "2girls", "solo", "solo_focus", "commentary", "commentary_request", "english_commentary",
        "translated", "translation_request", "bad_id", "bad_pixiv_id", "bad_twitter_id", "md5_mismatch",
        "duplicate", "artist_name", "signature", "watermark", "web_address", "patreon_username", "twitter_username",
        "simple_background", "white_background", "transparent_background", "looking_at_viewer", "absurd_res", "hi_res",
        "high_res", "conditional_dnp", "third-party_edit", "edit", "censored", "uncensored", "mosaic_censoring",
        "artist_request", "character_request", "copyright_request", "tagme_(artist)", "ai_generated", "ai-generated",
        "has_audio", "sound_warning", "long_playtime", "short_playtime", "2d", "digital_media_(artwork)",
    )
}
