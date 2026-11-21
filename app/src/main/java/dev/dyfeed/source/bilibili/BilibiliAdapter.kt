package dev.dyfeed.source.bilibili

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bilibili (哔哩哔哩, B站), as a visitor: its recommended feed, search, and playback, all of which
 * answer without an account from the US (checked from a US server: feed, search, ranking and
 * 720p files all fine). Requests carry a visitor id (buvid) and, where Bilibili asks for it, the
 * "WBI" signature its own web page adds (an MD5 over the sorted query plus a daily key).
 */
@Singleton
class BilibiliAdapter @Inject constructor(private val http: OkHttpClient) : SourceAdapter {
    override val id = SOURCE_ID
    override val displayName = "哔哩哔哩 Bilibili"
    override val requiresAuth = false

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lock = Mutex()
    @Volatile private var cookie: String? = null
    @Volatile private var mixin: Pair<Long, String>? = null

    override suspend fun authenticate(ctx: AuthContext): AuthResult = AuthResult.NotRequired

    override suspend fun fetchFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> {
        val idx = cursor?.toIntOrNull() ?: 1
        // Its home page's own recommendations (they follow what this visitor id watches), and
        // every third pull what's popular right now, for variety.
        val items = if (idx % 3 == 0) {
            val root = api("/x/web-interface/popular", mapOf("ps" to "20", "pn" to (idx / 3).toString()), signed = false)
            BilibiliParser.list(root.data()["list"])
        } else {
            val root = api(
                "/x/web-interface/wbi/index/top/feed/rcmd",
                mapOf("fresh_type" to "4", "ps" to "12", "fresh_idx" to idx.toString(), "fresh_idx_1h" to idx.toString(), "brush" to idx.toString(), "feed_version" to "V8", "homepage_ver" to "1"),
                signed = true,
            )
            BilibiliParser.list(root.data()["item"])
        }
        return Page(items, (idx + 1).toString().takeIf { idx < MAX_PULLS })
    }

    override suspend fun search(query: String, cursor: String?): Page<MediaItem> {
        val typed = query.trim()
        if (typed.isEmpty()) return Page(emptyList(), null)
        // English typed: Bilibili is searched in Chinese.
        val q = if (dev.dyfeed.domain.lang.Zh.isLatinQuery(typed))
            dev.dyfeed.domain.lang.EnglishToChinese.translate(typed).getOrNull()?.trim()?.takeIf { dev.dyfeed.domain.lang.Zh.has(it) } ?: typed
        else typed
        val page = cursor?.toIntOrNull() ?: 1
        val root = api("/x/web-interface/wbi/search/type", mapOf("search_type" to "video", "keyword" to q, "page" to page.toString()), signed = true)
        val d = root.data()
        val items = BilibiliParser.list(d["result"])
        val pages = (d["numPages"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
        return Page(items, (page + 1).toString().takeIf { items.isNotEmpty() && page < minOf(pages, MAX_SEARCH_PAGES) })
    }

    override suspend fun resolveStream(item: MediaItem): StreamInfo {
        val bvid = item.id
        val cid = item.extras["cid"] ?: run {
            val view = api("/x/web-interface/view", mapOf("bvid" to bvid), signed = false)
            (view.data()["cid"] as? JsonPrimitive)?.contentOrNull
        } ?: throw SourceException(id, "This Bilibili video is gone")
        // One progressive MP4 (720p at most for visitors): the player plays it like any file.
        val root = api(
            "/x/player/wbi/playurl",
            mapOf("bvid" to bvid, "cid" to cid, "qn" to "64", "fnval" to "1", "fnver" to "0", "fourk" to "0", "platform" to "html5", "high_quality" to "1"),
            signed = true,
        )
        val durl = (root.data()["durl"] as? JsonArray)?.firstOrNull() as? JsonObject
        val url = (durl?.get("url") as? JsonPrimitive)?.contentOrNull ?: throw SourceException(id, "Bilibili didn't give this video's file")
        // Its mirrors: the first one given was sometimes too slow to show a frame in 8 s on the
        // test phone. Ask them all for one byte and play from the first to answer.
        val backups = (durl["backup_url"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val best = if (backups.isEmpty()) url else firstAnswering((listOf(url) + backups).distinct().take(4)) ?: url
        return StreamInfo.Direct(best, "video/mp4")
    }

    private suspend fun firstAnswering(urls: List<String>): String? = kotlinx.coroutines.coroutineScope {
        val winners = kotlinx.coroutines.channels.Channel<String>(urls.size)
        val jobs = urls.map { u ->
            launch(Dispatchers.IO) {
                val ok = runCatching {
                    http.newCall(Request.Builder().url(u).header("Range", "bytes=0-0").header("User-Agent", UA).header("Referer", "https://www.bilibili.com/").build()).execute().use { it.code in 200..299 }
                }.getOrDefault(false)
                if (ok) winners.trySend(u)
            }
        }
        val first = kotlinx.coroutines.withTimeoutOrNull(RACE_MS) { winners.receive() }
        jobs.forEach { it.cancel() }
        first
    }

    override fun reportUrl(item: MediaItem): String? = item.pageUrl

    /** Videos Bilibili lists beside [bvid] ("related"). */
    suspend fun related(bvid: String): List<MediaItem> =
        BilibiliParser.list(api("/x/web-interface/archive/related", mapOf("bvid" to bvid), signed = false).let { (it as? JsonObject)?.get("data") })

    // ── requests ──

    private fun JsonElement.data(): JsonObject = (this as? JsonObject)?.get("data") as? JsonObject ?: JsonObject(emptyMap())

    private suspend fun api(path: String, params: Map<String, String>, signed: Boolean): JsonElement = withContext(Dispatchers.IO) {
        val c = visitor()
        val query = if (signed) Wbi.sign(params, mixinKey(), System.currentTimeMillis() / 1000) else params
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("api.bilibili.com").encodedPath(path)
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url)
            .header("User-Agent", UA).header("Referer", "https://www.bilibili.com/").header("Origin", "https://www.bilibili.com")
            .apply { if (c != null) header("Cookie", c) }
            .build()
        val body = runCatching { http.newCall(req).execute().use { r ->
            if (r.code == 412 || r.code == 429) throw SourceException(id, "Bilibili is limiting requests, trying again soon", httpCode = r.code)
            if (!r.isSuccessful) throw SourceException(id, "Bilibili answered ${r.code}", httpCode = r.code)
            r.body?.string().orEmpty()
        } }.getOrElse { e -> if (e is SourceException) throw e else throw SourceException(id, "Bilibili didn't answer. Check your connection.", cause = e) }
        val root = runCatching { json.parseToJsonElement(body) }.getOrElse { throw SourceException(id, "Bilibili answered with something unreadable") }
        val code = ((root as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        if (code == -352 || code == -412) { mixin = null; throw SourceException(id, "Bilibili wants a moment (code $code)", httpCode = 429) }
        if (code != 0) throw SourceException(id, "Bilibili said: " + ((root as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "code $code" })
        root
    }

    /** A visitor id (buvid3/4), as its web page gets one: once per run. */
    private suspend fun visitor(): String? = cookie ?: lock.withLock {
        cookie ?: runCatching {
            val r = http.newCall(Request.Builder().url("https://api.bilibili.com/x/frontend/finger/spi").header("User-Agent", UA).build()).execute()
            val d = r.use { json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject["data"]?.jsonObject }
            val b3 = (d?.get("b_3") as? JsonPrimitive)?.contentOrNull
            val b4 = (d?.get("b_4") as? JsonPrimitive)?.contentOrNull
            if (b3 == null) null else "buvid3=$b3; buvid4=$b4; b_nut=${System.currentTimeMillis() / 1000}"
        }.getOrNull()?.also { cookie = it }
    }

    /** Today's WBI key, from the "nav" answer (given to visitors too). Kept for six hours. */
    private suspend fun mixinKey(): String {
        mixin?.let { (at, k) -> if (System.currentTimeMillis() - at < KEY_TTL_MS) return k }
        val req = Request.Builder().url("https://api.bilibili.com/x/web-interface/nav")
            .header("User-Agent", UA).header("Referer", "https://www.bilibili.com/")
            .apply { visitor()?.let { header("Cookie", it) } }.build()
        val img = runCatching {
            http.newCall(req).execute().use { r ->
                val w = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["data"]?.jsonObject?.get("wbi_img")?.jsonObject
                fun stem(k: String) = (w?.get(k) as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/')?.substringBefore('.')
                (stem("img_url") ?: "") + (stem("sub_url") ?: "")
            }
        }.getOrNull()
        if (img.isNullOrEmpty()) throw SourceException(id, "Bilibili didn't answer. Check your connection.")
        return Wbi.mixin(img).also { mixin = System.currentTimeMillis() to it }
    }

    companion object {
        const val SOURCE_ID = "bilibili"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val KEY_TTL_MS = 6 * 60 * 60_000L
        private const val MAX_PULLS = 300
        private const val MAX_SEARCH_PAGES = 5
        private const val RACE_MS = 2_500L
    }
}

/** Bilibili's "WBI" request signature, as its web page computes it. */
object Wbi {
    private val TABLE = intArrayOf(46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52)

    /** The 32-character key from the two picture names in "nav". */
    fun mixin(raw: String): String = TABLE.filter { it < raw.length }.map { raw[it] }.joinToString("").take(32)

    /** [params] plus wts and w_rid. */
    fun sign(params: Map<String, String>, key: String, wts: Long): Map<String, String> {
        val p = (params + ("wts" to wts.toString())).mapValues { (_, v) -> v.filterNot { it in "!'()*" } }.toSortedMap()
        val query = p.entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        val md5 = java.security.MessageDigest.getInstance("MD5").digest((query + key).toByteArray()).joinToString("") { "%02x".format(it) }
        return p + ("w_rid" to md5)
    }

    /** As JavaScript's encodeURIComponent (and Python's urlencode, which the probe used): spaces as %20. */
    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%7E", "~")
}

/** Bilibili answers → posts. */
object BilibiliParser {
    fun list(e: JsonElement?): List<MediaItem> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parse) }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    /** One video (from the feed, popular, related or search); null for ads, live rooms and the like. */
    fun parse(o: JsonObject): MediaItem? {
        val goto = o.str("goto")
        if (goto != null && goto != "av") return null
        if (o.obj("business_info") != null) return null // an advert
        val bvid = o.str("bvid") ?: return null
        val title = o.str("title")?.let(::plain) ?: return null
        val owner = o.obj("owner")
        val author = owner?.str("name") ?: o.str("author")
        val mid = owner?.str("mid") ?: o.str("mid")
        val pic = (o.str("pic") ?: o.str("cover"))?.let(::https)
        val duration = o.str("duration")?.let(::seconds)
        val stat = o.obj("stat")
        val views = stat?.str("view") ?: o.str("play")
        val likes = stat?.str("like") ?: o.str("like")
        val pub = (o.str("pubdate") ?: o.str("ctime"))?.toLongOrNull()?.let(Instant::ofEpochSecond)
        // Search results carry its tags as one comma-separated string.
        val tags = o.str("tag")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty().take(8)
        val extras = buildMap {
            o.str("cid")?.let { put("cid", it) }
            (o.str("aid") ?: o.str("id"))?.let { put("aid", it) }
            mid?.let { put("mid", it) }
            owner?.str("face")?.let { put("avatar", https(it)) }
            views?.let { put("views", it) }
            likes?.let { put("score", it) }
            o.str("description")?.let(::plain)?.let { put("desc", it.take(300)) }
        }
        return MediaItem(
            id = bvid,
            sourceId = BilibiliAdapter.SOURCE_ID,
            type = MediaType.VIDEO,
            title = title,
            creator = author,
            tags = tags,
            durationSec = duration,
            thumbnailUrl = pic,
            pageUrl = "https://www.bilibili.com/video/$bvid",
            publishedAt = pub,
            extras = extras,
        )
    }

    /** Search wraps the matched words in <em class="keyword">; HTML entities too. */
    fun plain(s: String): String = s.replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").trim()

    fun https(u: String): String = when {
        u.startsWith("//") -> "https:$u"
        u.startsWith("http://") -> "https://" + u.removePrefix("http://")
        else -> u
    }

    /** "1211" (seconds) or "3:25" / "1:02:03". */
    fun seconds(s: String): Int? = if (':' in s) s.split(':').map { it.toIntOrNull() ?: return null }.fold(0) { acc, n -> acc * 60 + n } else s.toIntOrNull()
}
