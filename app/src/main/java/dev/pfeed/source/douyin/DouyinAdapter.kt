package dev.pfeed.source.douyin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pfeed.data.repo.SeenItems
import dev.pfeed.domain.model.AuthContext
import dev.pfeed.domain.model.AuthResult
import dev.pfeed.domain.model.FeedMode
import dev.pfeed.domain.model.FeedPrefs
import dev.pfeed.domain.model.MediaItem
import dev.pfeed.domain.model.MediaType
import dev.pfeed.domain.model.Page
import dev.pfeed.domain.model.StreamInfo
import dev.pfeed.player.PlayerPool
import dev.pfeed.source.SourceAdapter
import dev.pfeed.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 抖音 (Douyin, China's TikTok): its own "recommended" feed, search, creators' pages and shared
 * links, straight from douyin.com's web API (signed by [DouyinWeb]).
 *
 * - **For You / Latest**: Douyin's recommended feed (`/aweme/v1/web/tab/feed/`), re-ranked on the
 *   phone by the app's own taste engine like any other source.
 * - **Following**: the newest posts of creators you follow here (`/aweme/v1/web/aweme/post/`).
 * - **Search** (`/aweme/v1/web/search/item/`): Douyin may ask you to sign in first; the feed works
 *   without it. A creator page searches the creator's name, which opens their own posts.
 * - **Shared links** (v.douyin.com/…, douyin.com/video/…): read from the public share page, which
 *   needs no signature, so they keep working even when the signed API doesn't.
 *
 * Video addresses expire after a few hours, so a post kept longer is looked up again before playing.
 */
@Singleton
class DouyinAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val web: DouyinWeb,
    private val http: OkHttpClient,
    private val seen: SeenItems,
) : SourceAdapter {
    override val id = DouyinParser.SOURCE_ID
    override val displayName = "抖音 Douyin"
    override val requiresAuth = false
    override val offersSignIn = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val prefs by lazy { context.getSharedPreferences("douyin_creators", Context.MODE_PRIVATE) }
    private val creators = ConcurrentHashMap<String, String>()

    override suspend fun authenticate(ctx: AuthContext): AuthResult {
        val closed = DouyinLoginActivity.open(ctx.activity)
        closed.await()
        web.reload()
        return if (web.signedIn()) AuthResult.Success
        else AuthResult.Failure("Not signed in. The feed still works; search may need it.")
    }

    // ── feed ──

    override suspend fun fetchFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> =
        if (prefs.mode == FeedMode.FOLLOWING) followingFeed(cursor, prefs) else recommended(cursor)

    /** Cursor = how many times the feed has been pulled this session ("refresh_index"). */
    private suspend fun recommended(cursor: String?): Page<MediaItem> {
        val index = cursor?.toIntOrNull() ?: 1
        val root = try { callFeed(index) } catch (e: SourceException) {
            if (e.httpCode == 429) throw e
            // The app's own request was turned away: use what Douyin's page loaded for itself.
            val harvested = web.harvest().flatMap { body ->
                runCatching { items(json.parseToJsonElement(body)) }.getOrDefault(emptyList())
            }.distinctBy { it.id }.filterNot { seen.isSeen(it.key) }
            if (harvested.isEmpty()) throw e
            return Page(harvested, (index + 1).toString())
        }
        val items = items(root).filterNot { seen.isSeen(it.key) }
        return Page(items, (index + 1).toString().takeIf { index < MAX_FEED_PULLS })
    }

    private suspend fun callFeed(index: Int): JsonElement =
        call(
            "/aweme/v1/web/tab/feed/",
            mapOf(
                "count" to "15",
                "refresh_index" to index.toString(),
                "video_type_select" to "1",
                "aweme_pc_rec_raw_data" to """{"is_client":false,"ff_danmaku_status":1,"danmaku_switch_status":1}""",
                "globalwid" to "",
                "pull_type" to if (index == 1) "0" else "2",
                "min_window" to "0",
                "free_right" to "0",
                "ug_source" to "",
                "creative_id" to "",
                "is_main_tab" to "true",
                "module_id" to "3003101",
            ),
        )

    /** Followed creators' newest posts, newest first. Cursor = page number across them. */
    private suspend fun followingFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> {
        val ids = prefs.followedCreators.mapNotNull { secUidFor(it) }.distinct()
        if (ids.isEmpty()) return Page(emptyList(), null)
        if (cursor != null) return Page(emptyList(), null)
        val all = coroutineScope {
            ids.take(MAX_FOLLOWED).map { sec -> async { runCatching { userPosts(sec, 0L).items }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten()
        return Page(all.sortedByDescending { it.publishedAt }, null)
    }

    private suspend fun userPosts(secUid: String, maxCursor: Long): Page<MediaItem> {
        val root = call(
            "/aweme/v1/web/aweme/post/",
            mapOf("sec_user_id" to secUid, "max_cursor" to maxCursor.toString(), "count" to "18", "locate_query" to "false", "show_live_replay_strategy" to "1", "need_time_list" to "1", "time_list_query" to "0", "whale_cut_token" to "", "cut_version" to "1", "publish_video_strategy_type" to "2"),
        )
        val next = DouyinParser.long(root, "max_cursor")?.takeIf { DouyinParser.hasMore(root) && it > 0 }
        return Page(items(root), next?.let { "u:$it" })
    }

    // ── search ──

    override suspend fun search(query: String, cursor: String?): Page<MediaItem> {
        val q = query.trim()
        if (q.isEmpty()) return Page(emptyList(), null)
        // A creator page: their own posts.
        secUidFor(q)?.let { sec ->
            val max = cursor?.removePrefix("u:")?.toLongOrNull() ?: 0L
            if (cursor == null || cursor.startsWith("u:")) return userPosts(sec, max)
        }
        val offset = cursor?.toIntOrNull() ?: 0
        val root = call(
            "/aweme/v1/web/search/item/",
            mapOf(
                "search_channel" to "aweme_video_web", "enable_history" to "1", "keyword" to q,
                "search_source" to "normal_search", "query_correct_type" to "1", "is_filter_search" to "0",
                "from_group_id" to "", "offset" to offset.toString(), "count" to "16", "need_filter_settings" to "1",
                "list_type" to "single",
            ),
            searching = true,
        )
        val items = items(root)
        val next = (DouyinParser.long(root, "cursor") ?: (offset + 16).toLong()).toString()
        return Page(items, next.takeIf { DouyinParser.hasMore(root) && items.isNotEmpty() })
    }

    // ── playback ──

    override suspend fun resolveStream(item: MediaItem): StreamInfo {
        val at = item.extras["at"]?.toLongOrNull() ?: 0L
        val media = if (System.currentTimeMillis() - at < URL_TTL_MS) item.extras["media"] else null
        val url = media ?: fresh(item.id)?.extras?.get("media") ?: item.extras["media"]
            ?: throw SourceException(id, "This Douyin post is gone")
        return StreamInfo.Direct(url, if (item.type == MediaType.VIDEO) "video/mp4" else null)
    }

    override fun reportUrl(item: MediaItem): String? = item.pageUrl

    /** One post by id, with addresses fetched now: the signed API first, else the public share page. */
    suspend fun fresh(awemeId: String): MediaItem? =
        runCatching {
            val root = call("/aweme/v1/web/aweme/detail/", mapOf("aweme_id" to awemeId))
            items(root).firstOrNull()
        }.getOrNull() ?: sharePage(awemeId)

    /** A link shared to the app: v.douyin.com short links, douyin.com/video|note/<id>, ?modal_id=<id>, iesdouyin share pages. */
    suspend fun fromLink(url: HttpUrl): MediaItem? {
        val target = if (url.host == "v.douyin.com" || url.host == "v.iesdouyin.com") expand(url) ?: return null else url
        val awemeId = awemeIdIn(target) ?: return null
        return fresh(awemeId)
    }

    data class Comment(val author: String, val body: String, val score: Int)

    /** The post's top comments, read-only. */
    suspend fun comments(item: MediaItem): List<Comment> {
        val root = call("/aweme/v1/web/comment/list/", mapOf("aweme_id" to item.id, "cursor" to "0", "count" to "20", "item_type" to "0"))
        val list = (root as? JsonObject)?.get("comments") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return list.mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            val text = (o["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val who = ((o["user"] as? JsonObject)?.get("nickname") as? JsonPrimitive)?.contentOrNull ?: "?"
            val likes = (o["digg_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            Comment(who, text, likes)
        }
    }

    // ── internals ──

    private suspend fun call(path: String, params: Map<String, String>, searching: Boolean = false): JsonElement {
        val reply = web.get(path, params)
        if (reply.status == -1) throw SourceException(id, "Douyin didn't answer. Check your connection.")
        if (reply.status == 429) throw SourceException(id, "Douyin is limiting requests, trying again soon", httpCode = 429)
        if (reply.status !in 200..299) throw SourceException(id, "Douyin answered ${reply.status}", httpCode = reply.status)
        if (reply.body.isBlank()) throw SourceException(
            id,
            if (searching && !web.signedIn()) "Douyin search needs you to sign in: Settings → 抖音 Douyin"
            else "Douyin turned the request away. Open Settings → 抖音 Douyin and pass its check",
            isAuthError = searching,
        )
        val root = runCatching { json.parseToJsonElement(reply.body) }.getOrElse {
            throw SourceException(id, "Douyin sent something unreadable (probably a check page). Open Settings → 抖音 Douyin to clear it")
        }
        val status = DouyinParser.statusCode(root)
        if (status != null && status != 0) {
            val msg = ((root as? JsonObject)?.get("status_msg") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            throw SourceException(id, "Douyin: ${msg ?: "error $status"}", isAuthError = status == 8 || status == 2483)
        }
        return root
    }

    private fun items(root: JsonElement): List<MediaItem> =
        DouyinParser.parseAll(root, PlayerPool.maxHeight).onEach { item ->
            val sec = item.extras["secUid"] ?: return@onEach
            item.creator?.let { remember(it, sec) }
        }

    private fun key(name: String) = name.lowercase().filterNot { it.isWhitespace() || it == '_' }

    private fun remember(name: String, secUid: String) {
        val k = key(name)
        if (creators.put(k, secUid) != secUid) prefs.edit { putString(k, secUid) }
    }

    private fun secUidFor(name: String): String? {
        val k = key(name)
        return creators[k] ?: prefs.getString(k, null)?.also { creators[k] = it }
    }

    /** Follows a short link's redirect to the real post address (without fetching the post). */
    private suspend fun expand(url: HttpUrl): HttpUrl? = withContext(Dispatchers.IO) {
        val client = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", MOBILE_UA).build()).execute().use { r ->
                r.header("Location")?.let { url.resolve(it) }
            }
        }.getOrNull()
    }

    /** The public share page embeds the post as JSON (`window._ROUTER_DATA`); no signature needed. */
    private suspend fun sharePage(awemeId: String): MediaItem? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.iesdouyin.com/share/video/$awemeId/")
            .header("User-Agent", MOBILE_UA)
            .header("Referer", "https://www.douyin.com/")
            .build()
        val html = runCatching { http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
            ?: return@withContext null
        val data = ROUTER_DATA.find(html)?.groupValues?.get(1) ?: return@withContext null
        val root = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return@withContext null
        val item = findItemList(root)?.firstOrNull() as? JsonObject ?: return@withContext null
        DouyinParser.parse(item, PlayerPool.maxHeight)?.also { parsed ->
            parsed.extras["secUid"]?.let { sec -> parsed.creator?.let { remember(it, sec) } }
        }
    }

    private fun findItemList(e: JsonElement?): kotlinx.serialization.json.JsonArray? {
        val o = e as? JsonObject ?: return null
        (o["item_list"] as? kotlinx.serialization.json.JsonArray)?.let { return it }
        for (v in o.values) findItemList(v)?.let { return it }
        return null
    }

    companion object {
        private const val MAX_FEED_PULLS = 500
        private const val MAX_FOLLOWED = 30
        /** Douyin's video addresses stop working after a few hours. */
        private const val URL_TTL_MS = 90 * 60_000L

        private const val MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

        private val ROUTER_DATA = Regex("""window\._ROUTER_DATA\s*=\s*(\{.*?\})\s*</script>""", RegexOption.DOT_MATCHES_ALL)

        fun isDouyinHost(host: String): Boolean =
            host == "douyin.com" || host.endsWith(".douyin.com") || host == "iesdouyin.com" || host.endsWith(".iesdouyin.com")

        /** The post id in a Douyin address: /video/<id>, /note/<id>, /share/video/<id>, ?modal_id= or ?vid=. */
        fun awemeIdIn(url: HttpUrl): String? {
            listOf("modal_id", "vid", "aweme_id").firstNotNullOfOrNull { url.queryParameter(it) }
                ?.takeIf { it.all(Char::isDigit) && it.length >= 8 }?.let { return it }
            val segs = url.pathSegments.filter { it.isNotBlank() }
            val i = segs.indexOfFirst { it == "video" || it == "note" || it == "slides" }
            return segs.getOrNull(i + 1)?.takeIf { i >= 0 && it.all(Char::isDigit) && it.length >= 8 }
        }

        fun awemeIdIn(url: String): String? = url.toHttpUrlOrNull()?.let(::awemeIdIn)
    }
}

