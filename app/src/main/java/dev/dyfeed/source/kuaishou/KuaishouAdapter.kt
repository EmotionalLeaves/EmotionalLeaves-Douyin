package dev.dyfeed.source.kuaishou

import dev.dyfeed.domain.model.AuthContext
import dev.dyfeed.domain.model.AuthResult
import dev.dyfeed.domain.model.FeedPrefs
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.domain.model.Page
import dev.dyfeed.domain.model.StreamInfo
import dev.dyfeed.source.SourceAdapter
import dev.dyfeed.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kuaishou (快手), as a visitor: its web site's featured feed ("精选"), which answers without an
 * account from the US (checked from a US server: 20 posts a pull, files play). Its search and
 * creator pages ask a visitor to solve a captcha, so those aren't offered.
 */
@Singleton
class KuaishouAdapter @Inject constructor(private val http: OkHttpClient) : SourceAdapter {
    override val id = SOURCE_ID
    override val displayName = "快手 Kuaishou"
    override val requiresAuth = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Mutex()
    @Volatile private var cookie: String? = null

    override suspend fun authenticate(ctx: AuthContext): AuthResult = AuthResult.NotRequired

    override suspend fun fetchFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("operationName", "brilliantTypeDataQuery")
            put("variables", buildJsonObject { put("hotChannelId", "00"); put("page", "brilliant"); put("pcursor", cursor.orEmpty()) })
            put("query", FEED_QUERY)
        }.toString()
        val req = Request.Builder().url("https://www.kuaishou.com/graphql")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", UA).header("Referer", "https://www.kuaishou.com/brilliant").header("Origin", "https://www.kuaishou.com")
            .apply { visitor()?.let { header("Cookie", it) } }
            .build()
        val text = runCatching { http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw SourceException(id, "Kuaishou answered ${r.code}", httpCode = r.code)
            r.body?.string().orEmpty()
        } }.getOrElse { e -> if (e is SourceException) throw e else throw SourceException(id, "Kuaishou didn't answer. Check your connection.", cause = e) }
        val data = ((runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("data") as? JsonObject)?.get("brilliantTypeData") as? JsonObject
            ?: throw SourceException(id, "Kuaishou answered with something unreadable")
        val items = KuaishouParser.feeds(data["feeds"])
        if (items.isEmpty()) throw SourceException(id, "Kuaishou had nothing just now")
        // Its cursor stays the same from pull to pull; each pull is a fresh batch anyway.
        val next = (data["pcursor"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "no_more" } ?: "1"
        Page(items, next)
    }

    /** Kuaishou asks visitors to solve a captcha to search: nothing here (the feed still is). */
    override suspend fun search(query: String, cursor: String?): Page<MediaItem> = Page(emptyList(), null)

    override suspend fun resolveStream(item: MediaItem): StreamInfo =
        StreamInfo.Direct(item.extras["media"] ?: throw SourceException(id, "This Kuaishou video has no file"), "video/mp4")

    override fun reportUrl(item: MediaItem): String? = item.pageUrl

    /** The visitor cookies (did, clientid…) its web page sets, once per run. */
    private suspend fun visitor(): String? = cookie ?: lock.withLock {
        cookie ?: runCatching {
            http.newCall(Request.Builder().url("https://www.kuaishou.com/new-reco").header("User-Agent", UA).build()).execute().use { r ->
                r.headers("Set-Cookie").map { it.substringBefore(';') }.filter { '=' in it }.joinToString("; ").ifEmpty { null }
            }
        }.getOrNull()?.also { cookie = it }
    }

    companion object {
        const val SOURCE_ID = "kuaishou"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val FEED_QUERY = "fragment photoContent on PhotoEntity { id duration caption likeCount viewCount photoUrl coverUrl timestamp __typename } " +
            "fragment feedContent on Feed { type author { id name headerUrl __typename } photo { ...photoContent __typename } tags { type name __typename } __typename } " +
            "query brilliantTypeDataQuery(\$pcursor: String, \$hotChannelId: String, \$page: String) { brilliantTypeData(pcursor: \$pcursor, hotChannelId: \$hotChannelId, page: \$page) { result pcursor feeds { ...feedContent __typename } __typename } }"
    }
}

/** Kuaishou feed entries → posts. */
object KuaishouParser {
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private val HASHTAG = Regex("""#\S+""")

    fun feeds(e: JsonElement?): List<MediaItem> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }

    fun parse(feed: JsonObject): MediaItem? {
        val photo = feed["photo"] as? JsonObject ?: return null
        val author = feed["author"] as? JsonObject
        val id = photo.str("id") ?: return null
        val media = photo.str("photoUrl") ?: return null
        val caption = photo.str("caption").orEmpty()
        val tags = ((feed["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") } +
            HASHTAG.findAll(caption).map { it.value.removePrefix("#") }).distinct().take(8)
        val title = caption.replace(HASHTAG, " ").replace(Regex("""\s+"""), " ").trim().ifEmpty { null }
        val extras = buildMap {
            put("media", media)
            author?.str("id")?.let { put("uid", it) }
            author?.str("headerUrl")?.let { put("avatar", it) }
            photo.str("likeCount")?.let(::count)?.let { put("score", it.toString()) }
            photo.str("viewCount")?.let(::count)?.let { put("views", it.toString()) }
            put("at", System.currentTimeMillis().toString())
        }
        return MediaItem(
            id = id,
            sourceId = KuaishouAdapter.SOURCE_ID,
            type = MediaType.VIDEO,
            title = title,
            creator = author?.str("name"),
            tags = tags,
            durationSec = photo.str("duration")?.toLongOrNull()?.let { (it / 1000).toInt() },
            thumbnailUrl = photo.str("coverUrl"),
            pageUrl = "https://www.kuaishou.com/short-video/$id",
            publishedAt = photo.str("timestamp")?.toLongOrNull()?.let(Instant::ofEpochMilli),
            extras = extras,
        )
    }

    /** "5.8万" → 58000; "631484" → 631484. */
    fun count(s: String): Long? {
        val t = s.trim()
        return when {
            t.endsWith("万") -> t.removeSuffix("万").toDoubleOrNull()?.let { (it * 10_000).toLong() }
            t.endsWith("亿") -> t.removeSuffix("亿").toDoubleOrNull()?.let { (it * 100_000_000).toLong() }
            else -> t.toLongOrNull()
        }
    }
}
