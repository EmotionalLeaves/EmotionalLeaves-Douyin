package dev.dyfeed.source.douyin

import dev.dyfeed.domain.lang.Zh
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
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
    private val LENIENT = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

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
        // The module feed: cards[].aweme, an object or the same object as a JSON string.
        (o["cards"] as? JsonArray)?.let { cards ->
            return cards.mapNotNull { c ->
                when (val a = (c as? JsonObject)?.get("aweme")) {
                    is JsonObject -> a
                    is JsonPrimitive -> a.contentOrNull?.let { str -> runCatching { LENIENT.parseToJsonElement(str) as? JsonObject }.getOrNull() }
                    else -> null
                }
            }
        }
        // Anything else (a page's embedded RENDER_DATA): look everywhere for things shaped like a post.
        return deepPosts(o)
    }

    /** Objects anywhere in [e] that look like a post: an id and a video or pictures. */
    fun deepPosts(e: JsonElement, depth: Int = 0, out: MutableList<JsonObject> = ArrayList()): List<JsonObject> {
        if (depth > 14 || out.size >= 200) return out
        when (e) {
            is JsonObject -> {
                val id = (e["aweme_id"] ?: e["awemeId"]) as? JsonPrimitive
                if (id != null && (e["video"] is JsonObject || e["images"] is JsonArray)) { out += e; return out }
                e.values.forEach { deepPosts(it, depth + 1, out) }
            }
            is JsonArray -> e.forEach { deepPosts(it, depth + 1, out) }
            else -> {}
        }
        return out
    }

    /** The hot list's topics: (word, its topic page id if given), in order. */
    fun trendingTopics(root: JsonElement?): List<Pair<String, String?>> {
        val data = (root as? JsonObject)?.get("data") as? JsonObject ?: return emptyList()
        return listOf("word_list", "trending_list").flatMap { k ->
            (data[k] as? JsonArray)?.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val word = o.str("word")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                word to (o.str("sentence_id") ?: o.str("sentenceId"))?.takeIf { it.isNotBlank() && it != "0" }
            }.orEmpty()
        }
    }

    /** The hot list's heat per word (hot_value), where given. */
    fun trendingHeat(root: JsonElement?): Map<String, Long> {
        val data = (root as? JsonObject)?.get("data") as? JsonObject ?: return emptyMap()
        return listOf("word_list", "trending_list").flatMap { k ->
            (data[k] as? JsonArray)?.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val word = o.str("word")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val heat = (o.str("hot_value") ?: o.str("hotValue"))?.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                word to heat
            }.orEmpty()
        }.toMap()
    }

    /** A comment-list answer's comments: (who, what they said, likes), blanks skipped. */
    fun comments(root: JsonElement?): List<Triple<String, String, Int>> {
        val list = (root as? JsonObject)?.get("comments") as? JsonArray ?: return emptyList()
        return list.mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            val text = o.str("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val who = (o["user"] as? JsonObject)?.str("nickname")?.takeIf { it.isNotBlank() } ?: "?"
            Triple(who, text, o.str("digg_count")?.toIntOrNull() ?: 0)
        }
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
        // Two spellings of the same post: the web API's (aweme_id, play_addr.url_list) and the site's
        // own camelCase one (awemeId, playAddr[].src), which the module feed's cards may carry.
        val id = a.str("aweme_id") ?: a.str("awemeId") ?: return null
        if (a.str("is_ads") == "true" || a["cell_room"].isPresent() || a["live_reason"].isPresent()) return null
        val author = (a["author"] ?: a["authorInfo"]) as? JsonObject
        val creator = author?.str("nickname")?.trim()?.takeIf { it.isNotEmpty() }
        val secUid = author?.str("sec_uid") ?: author?.str("secUid")
        val avatar = listOf("avatar_thumb", "avatarThumb", "avatar_168x168", "avatar_medium").firstNotNullOfOrNull { k ->
            when (val v = author?.get(k)) {
                is JsonObject -> ((v["url_list"] ?: v["urlList"]) as? JsonArray).urls().let(::pickImage)
                is JsonPrimitive -> v.contentOrNull?.let(::https)
                else -> null
            }
        }
        // The caption without its #hashtags and @mentions: those show as tappable tags already.
        val desc = a.str("desc")?.let(::caption)
        val tags = buildList {
            ((a["text_extra"] ?: a["textExtra"]) as? JsonArray)?.forEach { t -> (t as? JsonObject)?.let { it.str("hashtag_name") ?: it.str("hashtagName") }?.let { add(it) } }
            ((a["video_tag"] ?: a["videoTag"]) as? JsonArray)?.forEach { t -> (t as? JsonObject)?.let { it.str("tag_name") ?: it.str("tagName") }?.let { add(it) } }
        }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val created = (a.str("create_time") ?: a.str("createTime"))?.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
        val likes = ((a["statistics"] ?: a["stats"]) as? JsonObject)?.let { it.str("digg_count") ?: it.str("diggCount") }
        val commentCount = ((a["statistics"] ?: a["stats"]) as? JsonObject)?.let { it.str("comment_count") ?: it.str("commentCount") }
        val video = a["video"] as? JsonObject

        // Hashtag ids ("美食=1575..."), so a search for a tag can open that tag's own video list.
        val tagIds = ((a["text_extra"] ?: a["textExtra"]) as? JsonArray)?.mapNotNull { t ->
            val o = t as? JsonObject ?: return@mapNotNull null
            val name = (o.str("hashtag_name") ?: o.str("hashtagName"))?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val cid = (o.str("hashtag_id") ?: o.str("hashtagId") ?: o.str("cid"))?.takeIf { it.length > 3 && it.all(Char::isDigit) } ?: return@mapNotNull null
            "$name=$cid"
        }.orEmpty()
        // The sound: "song – artist" (Douyin's own "original sound" posts read 原声 – creator).
        val music = (a["music"] as? JsonObject)?.let { m ->
            val title = m.str("title")?.trim()?.takeIf { it.isNotEmpty() }
            val by = (m.str("author") ?: m.str("owner_nickname") ?: m.str("ownerNickname"))?.trim()?.takeIf { it.isNotEmpty() }
            listOfNotNull(title, by).distinct().joinToString(" – ").takeIf { it.isNotEmpty() }
        }
        val common = buildMap {
            music?.let { put("music", it) }
            secUid?.let { put("secUid", it) }
            avatar?.let { put("avatar", it) }
            if (tagIds.isNotEmpty()) put("tagIds", tagIds.joinToString(";"))
            likes?.let { put("score", it) }
            commentCount?.let { put("comments", it) }
            put("at", fetchedAt.toString())
            // Douyin's own "made with AI" label, in whichever form the answer carries it.
            if (aiLabelled(a)) put(dev.dyfeed.domain.filter.AiArt.AI_LABEL, "1")
            // The text Douyin itself read off the video's frames (its subtitles, mostly): for Learn.
            ((a["seo_info"] ?: a["seoInfo"]) as? JsonObject)?.let { seo -> seo.str("ocr_content") ?: seo.str("ocrContent") }
                ?.trim()?.takeIf { Zh.has(it) }?.let { put(SCREEN_TEXT, it.take(MAX_SCREEN_TEXT)) }
        }

        // Photo post: every picture, in order.
        val images = (a["images"] as? JsonArray)?.mapNotNull { img ->
            val o = img as? JsonObject ?: return@mapNotNull null
            ((o["url_list"] ?: o["urlList"]) as? JsonArray).urls().let(::pickImage) ?: o.str("url")?.let(::https)
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
        val addresses = videoAddresses(video, maxHeight)
        val media = addresses.firstOrNull() ?: return null
        val cover = listOf("cover", "origin_cover", "originCover", "dynamic_cover", "dynamicCover").firstNotNullOfOrNull { k ->
            when (val c = video[k]) {
                is JsonObject -> ((c["url_list"] ?: c["urlList"]) as? JsonArray).urls().let(::pickImage)
                is JsonPrimitive -> c.contentOrNull?.takeIf { it.isNotBlank() }?.let(::https)
                else -> null
            }
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
            extras = common + mapOf("media" to media, "mime" to "video/mp4") +
                (addresses.drop(1).take(MAX_ALTERNATES).takeIf { it.isNotEmpty() }?.let { mapOf(ALTERNATES to it.joinToString(" ")) } ?: emptyMap()),
        )
    }

    /**
     * The best stream that fits the screen: from `bit_rate[]` the tallest H.264 rendition no taller
     * than [maxHeight] (H.265 only if nothing else), else `play_addr`. Share-page answers carry the
     * watermarked "playwm" address, which plays clean as "play".
     */
    fun pickVideo(video: JsonObject, maxHeight: Int): String? = videoAddresses(video, maxHeight).firstOrNull()

    /**
     * Every address the post's video can be fetched from, best first: the chosen rendition on each
     * of Douyin's mirror servers, then the other renditions, then `play_addr`. When one server
     * refuses (403/404), the player moves on to the next instead of skipping the video.
     */
    fun videoAddresses(video: JsonObject, maxHeight: Int): List<String> {
        data class R(val urls: List<String>, val height: Int, val h265: Boolean, val bitrate: Long)
        val renditions = ((video["bit_rate"] ?: video["bitRateList"]) as? JsonArray)?.mapNotNull { b ->
            val bo = b as? JsonObject ?: return@mapNotNull null
            val addr = bo["play_addr"] ?: bo["playAddr"]
            val urls = addrUrls(addr).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val ao = addr as? JsonObject
            val w = (ao?.str("width") ?: bo.str("width"))?.toIntOrNull() ?: 0
            val h = (ao?.str("height") ?: bo.str("height"))?.toIntOrNull() ?: 0
            R(
                urls = urls,
                // "1080p" means the short side, for portrait and landscape alike.
                height = if (w > 0 && h > 0) minOf(w, h) else maxOf(w, h),
                h265 = listOf("is_h265", "isH265", "is_bytevc1", "isBytevc1").any { bo.str(it) == "1" || bo.str(it) == "true" } ||
                    ((bo.str("gear_name") ?: bo.str("gearName"))?.contains("265") == true),
                bitrate = (bo.str("bit_rate") ?: bo.str("bitRate"))?.toLongOrNull() ?: 0,
            )
        }.orEmpty()
        val ranked = renditions
            .sortedWith(compareBy<R> { it.h265 }.thenByDescending { if (it.height in 1..maxHeight) it.height else -it.height }.thenByDescending { it.bitrate })
        val plain = listOf("play_addr", "playAddr", "play_addr_h264", "playAddrH264").flatMap { addrUrls(video[it]) }
        return (ranked.filterNot { it.h265 }.flatMap { it.urls } + plain + ranked.filter { it.h265 }.flatMap { it.urls })
            .map { it.replace("/playwm/", "/play/").replace("http://", "https://") }
            .filter { it.startsWith("https://") }
            .distinct()
    }

    /**
     * Whether Douyin marks the post as made with AI: `is_aigc_media`, an `aigc_info` object saying
     * so (created_by_ai, a non-zero AIGC label type), or a top-level is_ai / created_by_ai flag.
     * Only those exact keys: Douyin has others with "aigc" in the name that mean something else
     * (`item_aigc_follow_shot` is 1 on every post: "you can film an AI follow-along of this"),
     * and matching on the name alone hid every post as AI in 1.7.41–1.7.46.
     */
    internal fun aiLabelled(a: JsonObject): Boolean {
        fun truthy(e: JsonElement?): Boolean = when (e) {
            is JsonPrimitive -> e.contentOrNull?.let { it == "true" || (it.toIntOrNull() ?: 0) > 0 } == true
            else -> false
        }
        if (AI_FLAGS.any { truthy(a[it]) }) return true
        val info = a["aigc_info"] as? JsonObject ?: return false
        return AI_INFO_FLAGS.any { truthy(info[it]) }
    }

    private val AI_FLAGS = listOf("is_aigc_media", "is_ai", "created_by_ai", "is_aigc", "aigc_label_type")
    private val AI_INFO_FLAGS = listOf("created_by_ai", "aigc_label_type", "is_aigc", "aigc_type", "label_type")

    /** Extra key: which Douyin answer a post came from, when it's worth telling apart (e.g. "related"). */
    const val VIA = "via"

    /** Extra key: what the video says on screen (its subtitles), as Douyin read it. */
    const val SCREEN_TEXT = "ocr"
    private const val MAX_SCREEN_TEXT = 600

    /** Extra key: other addresses for the same video, space-separated, tried in order if the first fails. */
    const val ALTERNATES = "media_alt"
    private const val MAX_ALTERNATES = 6

    /** A play address in either spelling: {url_list:[…]} / {urlList:[…]}, or [{src:…}, …]. */
    private fun addrUrls(addr: JsonElement?): List<String> = when (addr) {
        is JsonObject -> ((addr["url_list"] ?: addr["urlList"]) as? JsonArray).urls()
        is JsonArray -> addr.mapNotNull { (it as? JsonObject)?.str("src")?.let(::https) }
        else -> emptyList()
    }

    /**
     * When a media address stops working, if it says (milliseconds): CDN addresses carry their
     * expiry as a Unix time in a query parameter such as x-expires, expire or Expires. Douyin's
     * own video servers (…douyinvod.com/<signature>/<expiry in hex>/video/…) put it in the path.
     */
    fun expiresAt(url: String): Long? {
        VOD_PATH.find(url.substringBefore('?'))?.groupValues?.get(1)?.toLongOrNull(16)
            ?.takeIf { it in 1_500_000_000L..4_000_000_000L }?.let { return it * 1000 }
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        return query.split('&').firstNotNullOfOrNull { kv ->
            val k = kv.substringBefore('=').lowercase()
            if (k in EXPIRY_KEYS) kv.substringAfter('=', "").toLongOrNull()?.takeIf { it in 1_500_000_000L..4_000_000_000L }?.times(1000) else null
        }
    }

    private val VOD_PATH = Regex("""^https?://[^/]+/[0-9a-f]{16,64}/([0-9a-f]{8})/""")
    private val EXPIRY_KEYS = setOf("x-expires", "expires", "expire", "x-oss-expires", "deadline")

    /** "//host/path" (the site's own addresses) → "https://host/path". */
    private fun https(u: String): String? = when {
        u.startsWith("//") -> "https:$u"
        u.startsWith("http") -> u
        else -> null
    }

    private val HASHTAG = Regex("""[#＃][^\s#＃@]+""")
    private val MENTION = Regex("""@[^\s#＃@]+""")

    /**
     * Whether [item] answers a search for [q]: every word of it appears in the caption, the tags or
     * the creator's name (Chinese needs no spaces: "火锅" matches "重庆火锅太好吃了").
     */
    fun matches(item: MediaItem, q: String): Boolean {
        val words = q.lowercase().split(Regex("""\s+""")).map { it.removePrefix("#") }.filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val hay = buildString {
            append(item.title.orEmpty().lowercase()); append(' ')
            item.tags.forEach { append(it.lowercase()); append(' ') }
            append(item.creator.orEmpty().lowercase())
        }
        return words.all { it in hay }
    }

    /**
     * How well [item] fits the search [q], 0–1: each word of it found in the caption, tags or
     * creator counts 1; a Chinese word that isn't there whole counts for the share of its
     * two-character pieces that are ("有趣的猫" against "猫咪太有趣了"), small words like 的 aside.
     */
    fun relevance(item: MediaItem, q: String): Double {
        val words = q.lowercase().split(Regex("""\s+""")).map { it.removePrefix("#") }.filter { it.isNotBlank() }
        if (words.isEmpty()) return 0.0
        val hay = buildString {
            append(item.title.orEmpty().lowercase()); append(' ')
            item.tags.forEach { append(it.lowercase()); append(' ') }
            append(item.creator.orEmpty().lowercase())
        }
        return words.sumOf { w ->
            when {
                w in hay -> 1.0
                w.any { dev.dyfeed.domain.lang.Zh.has(it.toString()) } -> {
                    val core = w.filterNot { it in FILLER }
                    if (core.length <= 1) (if (core.isNotEmpty() && core in hay) 1.0 else 0.0)
                    else {
                        // Half for its two-character pieces, half for its characters (word order varies).
                        val pieces = core.windowed(2)
                        val chars = core.toSet()
                        0.5 * pieces.count { it in hay } / pieces.size + 0.5 * chars.count { it in hay } / chars.size
                    }
                }
                else -> 0.0
            }
        } / words.size
    }

    private const val FILLER = "的了是在和与吗呢吧啊很太也都就"

    /** "重庆火锅太好吃了 #美食 #重庆 @小王" → "重庆火锅太好吃了"; null if nothing else is left. */
    fun caption(desc: String): String? =
        desc.replace(HASHTAG, " ").replace(MENTION, " ").replace(Regex("""\s+"""), " ").trim().takeIf { it.isNotEmpty() }

    /** Prefer a JPEG/PNG/WebP address over HEIC, which some phones can't decode. */
    private fun pickImage(urls: List<String>): String? =
        urls.firstOrNull { u -> listOf(".jpeg", ".jpg", ".png", ".webp").any { u.substringBefore('?').endsWith(it) } } ?: urls.firstOrNull()

    private fun JsonArray?.urls(): List<String> =
        this?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::https) }.orEmpty()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    /** Set to something: not missing, null, "", 0, false, {} or []. Many posts carry empty `live_reason` / `cell_room`. */
    private fun JsonElement?.isPresent(): Boolean = when (this) {
        null, is kotlinx.serialization.json.JsonNull -> false
        is JsonPrimitive -> content.isNotBlank() && content != "0" && content != "false"
        is JsonObject -> isNotEmpty()
        is JsonArray -> isNotEmpty()
    }

    /** Why [parse] turned a post down, for the health probe; null if it didn't. */
    fun whyNot(a: JsonObject, maxHeight: Int): String? {
        if (parse(a, maxHeight) != null) return null
        if (a.str("aweme_id") == null && a.str("awemeId") == null) return "no id"
        if (a.str("is_ads") == "true") return "ad"
        if (a["cell_room"].isPresent()) return "live room"
        if (a["live_reason"].isPresent()) return "live reason=" + a["live_reason"].toString().take(30)
        val v = a["video"] as? JsonObject ?: return "no video (keys: " + a.keys.take(30).joinToString(",") + ")"
        return "no playable address (video keys: " + v.keys.joinToString(",") +
            "; play_addr=" + (v["play_addr"] ?: v["playAddr"]).toString().take(160) + ")"
    }
}
