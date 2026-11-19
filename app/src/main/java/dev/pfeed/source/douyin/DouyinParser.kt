package dev.pfeed.source.douyin

import dev.pfeed.domain.model.MediaItem
import dev.pfeed.domain.model.MediaType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/**
 * Turns Douyin's "aweme" objects (one post each, as the web API and the share pages return them)
 * into feed items. Pure JSON in, items out, so it can be unit-tested without a phone.
 *
 * An aweme is either a video (`video.play_addr` / `video.bit_rate`) or a photo post
 * (`images[]`, aweme_type 68), which becomes a gallery you page through sideways.
 * Ads, live rooms and posts with nothing playable are skipped.
 */
object DouyinParser {
    const val SOURCE_ID = "douyin"

    /** Every aweme in a feed / user-posts answer (`aweme_list`) or a search answer (`data[].aweme_info`). */
    fun awemes(root: JsonElement?): List<JsonObject> {
        val o = root as? JsonObject ?: return emptyList()
        (o["aweme_list"] as? JsonArray)?.let { list -> return list.mapNotNull { it as? JsonObject } }
        (o["data"] as? JsonArray)?.let { list ->
            return list.mapNotNull { e ->
                val eo = e as? JsonObject ?: return@mapNotNull null
                (eo["aweme_info"] as? JsonObject)
                    ?: ((eo["aweme_mix_info"] as? JsonObject)?.get("mix_items") as? JsonArray)?.firstOrNull() as? JsonObject
            }
        }
        (o["aweme_detail"] as? JsonObject)?.let { return listOf(it) }
        return emptyList()
    }

    fun parseAll(root: JsonElement?, maxHeight: Int, fetchedAt: Long = System.currentTimeMillis()): List<MediaItem> =
        awemes(root).mapNotNull { parse(it, maxHeight, fetchedAt) }.distinctBy { it.id }

    /** Douyin's own status: 0 is fine; anything else carries a message (`status_msg`). */
    fun statusCode(root: JsonElement?): Int? = ((root as? JsonObject)?.get("status_code") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    fun hasMore(root: JsonElement?): Boolean {
        val v = ((root as? JsonObject)?.get("has_more") as? JsonPrimitive)?.contentOrNull ?: return false
        return v == "1" || v.equals("true", ignoreCase = true)
    }

    fun long(root: JsonElement?, key: String): Long? = ((root as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    fun parse(a: JsonObject, maxHeight: Int, fetchedAt: Long = System.currentTimeMillis()): MediaItem? {
        val id = a.str("aweme_id") ?: return null
        if (a.str("is_ads") == "true" || a["cell_room"].isPresent() || a["live_reason"].isPresent()) return null
        val author = a["author"] as? JsonObject
        val creator = author?.str("nickname")?.trim()?.takeIf { it.isNotEmpty() }
        val secUid = author?.str("sec_uid")
        // The caption without its #hashtags and @mentions: those show as tappable tags already.
        val desc = a.str("desc")?.let(::caption)
        val tags = buildList {
            (a["text_extra"] as? JsonArray)?.forEach { t -> (t as? JsonObject)?.str("hashtag_name")?.let { add(it) } }
            (a["video_tag"] as? JsonArray)?.forEach { t -> (t as? JsonObject)?.str("tag_name")?.let { add(it) } }
        }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val created = a.str("create_time")?.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
        val likes = (a["statistics"] as? JsonObject)?.str("digg_count")
        val video = a["video"] as? JsonObject

        val common = buildMap {
            secUid?.let { put("secUid", it) }
            likes?.let { put("score", it) }
            put("at", fetchedAt.toString())
        }

        // Photo post: every picture, in order.
        val images = (a["images"] as? JsonArray)?.mapNotNull { img ->
            ((img as? JsonObject)?.get("url_list") as? JsonArray).urls().let(::pickImage)
        }.orEmpty()
        if (images.isNotEmpty()) {
            return MediaItem(
                id = id,
                sourceId = SOURCE_ID,
                type = MediaType.IMAGE,
                title = desc,
                creator = creator,
                tags = tags,
                durationSec = null,
                thumbnailUrl = images.first(),
                pageUrl = "https://www.douyin.com/note/$id",
                publishedAt = created,
                extras = common + ("media" to images.first()),
                pages = if (images.size > 1) images else emptyList(),
            )
        }

        video ?: return null
        val media = pickVideo(video, maxHeight) ?: return null
        val cover = listOf("cover", "origin_cover", "dynamic_cover").firstNotNullOfOrNull { k ->
            ((video[k] as? JsonObject)?.get("url_list") as? JsonArray).urls().let(::pickImage)
        }
        val durationMs = video.str("duration")?.toLongOrNull() ?: a.str("duration")?.toLongOrNull()
        return MediaItem(
            id = id,
            sourceId = SOURCE_ID,
            type = MediaType.VIDEO,
            title = desc,
            creator = creator,
            tags = tags,
            durationSec = durationMs?.let { (it / 1000).toInt() }?.takeIf { it > 0 },
            thumbnailUrl = cover,
            pageUrl = "https://www.douyin.com/video/$id",
            publishedAt = created,
            extras = common + mapOf("media" to media, "mime" to "video/mp4"),
        )
    }

    /**
     * The best stream that fits the screen: from `bit_rate[]` the tallest H.264 rendition no taller
     * than [maxHeight] (H.265 only if nothing else), else `play_addr`. Share-page answers carry the
     * watermarked "playwm" address, which plays clean as "play".
     */
    fun pickVideo(video: JsonObject, maxHeight: Int): String? {
        data class R(val url: String, val height: Int, val h265: Boolean, val bitrate: Long)
        val renditions = (video["bit_rate"] as? JsonArray)?.mapNotNull { b ->
            val bo = b as? JsonObject ?: return@mapNotNull null
            val addr = bo["play_addr"] as? JsonObject ?: return@mapNotNull null
            val url = (addr["url_list"] as? JsonArray).urls().firstOrNull() ?: return@mapNotNull null
            val w = addr.str("width")?.toIntOrNull() ?: 0
            val h = addr.str("height")?.toIntOrNull() ?: 0
            R(
                url = url,
                // "1080p" means the short side, for portrait and landscape alike.
                height = if (w > 0 && h > 0) minOf(w, h) else maxOf(w, h),
                h265 = bo.str("is_h265") == "1" || bo.str("is_bytevc1") == "1" || (bo.str("gear_name")?.contains("265") == true),
                bitrate = bo.str("bit_rate")?.toLongOrNull() ?: 0,
            )
        }.orEmpty()
        val pick = renditions
            .sortedWith(compareBy<R> { it.h265 }.thenByDescending { if (it.height in 1..maxHeight) it.height else -it.height }.thenByDescending { it.bitrate })
            .firstOrNull()
        val url = pick?.url
            ?: ((video["play_addr"] as? JsonObject)?.get("url_list") as? JsonArray).urls().firstOrNull()
            ?: ((video["play_addr_h264"] as? JsonObject)?.get("url_list") as? JsonArray).urls().firstOrNull()
            ?: return null
        return url.replace("/playwm/", "/play/").replace("http://", "https://")
    }

    private val HASHTAG = Regex("""[#＃][^\s#＃@]+""")
    private val MENTION = Regex("""@[^\s#＃@]+""")

    /** "重庆火锅太好吃了 #美食 #重庆 @小王" → "重庆火锅太好吃了"; null if nothing else is left. */
    fun caption(desc: String): String? =
        desc.replace(HASHTAG, " ").replace(MENTION, " ").replace(Regex("""\s+"""), " ").trim().takeIf { it.isNotEmpty() }

    /** Prefer a JPEG/PNG/WebP address over HEIC, which some phones can't decode. */
    private fun pickImage(urls: List<String>): String? =
        urls.firstOrNull { u -> listOf(".jpeg", ".jpg", ".png", ".webp").any { u.substringBefore('?').endsWith(it) } } ?: urls.firstOrNull()

    private fun JsonArray?.urls(): List<String> =
        this?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { u -> u.startsWith("http") } }.orEmpty()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.isPresent(): Boolean = this != null && this !is kotlinx.serialization.json.JsonNull
}
