package dev.dyfeed.source.douyin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.dyfeed.data.repo.SeenItems
import dev.dyfeed.domain.model.AuthContext
import dev.dyfeed.domain.model.AuthResult
import dev.dyfeed.domain.model.FeedMode
import dev.dyfeed.domain.model.FeedPrefs
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.MediaType
import dev.dyfeed.domain.model.Page
import dev.dyfeed.domain.model.StreamInfo
import dev.dyfeed.player.PlayerPool
import dev.dyfeed.source.SourceAdapter
import dev.dyfeed.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        resetIfSignInChanged(force = true)
        return if (web.signedIn()) AuthResult.Success
        else AuthResult.Failure("Not signed in. The feed still works; search may need it.")
    }

    // ── feed ──

    override suspend fun fetchFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> =
        if (prefs.mode == FeedMode.FOLLOWING) followingFeed(cursor, prefs) else recommended(cursor)

    /** Cursor = how many times the feed has been pulled this session ("refresh_index"). */
    private suspend fun recommended(cursor: String?): Page<MediaItem> {
        val index = cursor?.toIntOrNull() ?: 1
        val next = (index + 1).toString().takeIf { index < MAX_FEED_PULLS }
        // What Douyin's own page loaded for itself: the backup when the app's own request is
        // turned away, or answers in a form with no readable posts.
        suspend fun fromPage(): List<MediaItem> = web.harvest().flatMap { body ->
            runCatching { items(json.parseToJsonElement(body)) }.getOrDefault(emptyList())
        }.distinctBy { it.id }.filterNot { seen.isSeen(it.key) }
        val root = try { callFeed(index) } catch (e: SourceException) {
            if (e.httpCode == 429) throw e
            val harvested = fromPage()
            if (harvested.isEmpty()) throw e
            return Page(harvested, next)
        }
        val parsed = items(root)
        if (parsed.isEmpty()) return Page(fromPage(), next)
        return Page(parsed.filterNot { seen.isSeen(it.key) }, next)
    }

    /**
     * Douyin's web client has moved its recommended feed between endpoints over time (the old
     * `tab/feed` now answers 403). Try the current one first, then the older ones; remember the
     * one that answered so the next pull goes straight there.
     */
    @Volatile private var feedPath: String? = null

    private suspend fun callFeed(index: Int): JsonElement {
        val params = mapOf(
            "module_id" to "3003101",
            "count" to "15",
            "refresh_index" to index.toString(),
            "pull_type" to if (index == 1) "0" else "2",
            "video_type_select" to "1",
            "filterGids" to "",
            "presented_ids" to "",
            "refer_id" to "",
            "refer_type" to "10",
            "awemePcRecRawData" to """{"is_xigua_user":0,"is_client":false}""",
            "aweme_pc_rec_raw_data" to """{"is_client":false,"ff_danmaku_status":1,"danmaku_switch_status":1}""",
            "is_main_tab" to "true",
            "tag_id" to "",
        )
        val paths = listOfNotNull(feedPath) + FEED_PATHS.filter { it != feedPath }
        var last: SourceException? = null
        for (path in paths) {
            try {
                val root = call(path, params)
                if (DouyinParser.awemes(root).isEmpty() && path != paths.last()) continue
                feedPath = path
                return root
            } catch (e: SourceException) {
                if (e.httpCode == 429) throw e
                last = e
            }
        }
        throw last ?: SourceException(id, "Douyin's feed came back empty")
    }

    /** Followed creators' newest posts, newest first. Cursor = page number across them. */
    private suspend fun followingFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> {
        val ids = prefs.followedCreators.mapNotNull { secUidFor(it) }.distinct()
        if (ids.isEmpty()) return Page(emptyList(), null)
        if (cursor != null) return Page(emptyList(), null)
        val all = coroutineScope {
            // Signed out, each creator may need their page opened (~20 s, one at a time): only the
            // first few get that; the rest show what the app already has of them.
            ids.take(MAX_FOLLOWED).mapIndexed { i, sec ->
                async { runCatching { userPosts(sec, 0L, allowPage = i < MAX_FOLLOWED_PAGES).items }.getOrDefault(emptyList()) }
            }.awaitAll()
        }.flatten()
        return Page(all.sortedByDescending { it.publishedAt }, null)
    }

    private suspend fun userPosts(secUid: String, maxCursor: Long, allowPage: Boolean = true): Page<MediaItem> =
        try {
            directUserPosts(secUid, maxCursor)
        } catch (e: SourceException) {
            if (maxCursor > 0) throw e
            // Signed out the post list answers 403: take what the creator's own page loads, plus
            // their posts the app already has.
            val fromPage = if (!allowPage) emptyList() else creatorPageGate.attempt {
                runCatching { web.creatorPage(secUid) }.getOrDefault(emptyList()).flatMap { body ->
                    runCatching { items(json.parseToJsonElement(body)) }.getOrDefault(emptyList())
                }
            }
            ensureIndex()
            val known = synchronized(loaded) { loaded.filter { it.extras["secUid"] == secUid } }
            // Douyin's related videos for their posts often include more of theirs.
            val viaRelated = if (!allowPage) emptyList() else runCatching { relatedPosts(known.take(3)) }.getOrDefault(emptyList())
                .filter { it.extras["secUid"] == secUid }
            val all = (fromPage + known + viaRelated).distinctBy { it.id }.sortedByDescending { it.publishedAt }
            if (all.isEmpty()) throw e
            Page(all, null)
        }

    private suspend fun directUserPosts(secUid: String, maxCursor: Long): Page<MediaItem> {
        val root = call(
            "/aweme/v1/web/aweme/post/",
            mapOf("sec_user_id" to secUid, "max_cursor" to maxCursor.toString(), "count" to "18", "locate_query" to "false", "show_live_replay_strategy" to "1", "need_time_list" to "1", "time_list_query" to "0", "whale_cut_token" to "", "cut_version" to "1", "publish_video_strategy_type" to "2"),
        )
        val next = DouyinParser.long(root, "max_cursor")?.takeIf { DouyinParser.hasMore(root) && it > 0 }
        return Page(items(root), next?.let { "u:$it" })
    }

    // ── search ──

    override suspend fun search(query: String, cursor: String?): Page<MediaItem> {
        resetIfSignInChanged()
        val q = query.trim()
        if (q.isEmpty()) return Page(emptyList(), null)
        // A creator page: their own posts.
        secUidFor(q)?.let { sec ->
            val max = cursor?.removePrefix("u:")?.toLongOrNull() ?: 0L
            if (cursor == null || cursor.startsWith("u:")) return userPosts(sec, max)
        }
        // Later pages: only the signed search API pages; the other two ways give one page.
        if (cursor == "end") return Page(emptyList(), null)
        val offset = cursor?.toIntOrNull() ?: 0
        // Signed out, the search API always says "sign in first": don't spend the time asking.
        val direct = if (web.signedIn() || offset > 0) runCatching { directSearch(q, offset) }
        else Result.failure(SourceException(id, "Douyin search needs you to sign in: Settings → 抖音 Douyin", isAuthError = true))
        direct.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { return it }
        if (offset > 0) return Page(emptyList(), null)
        // Signed out, Douyin's search API asks you to sign in. Its own search page still shows a
        // visitor results, so open that and take what it loads; and add matching posts the feed
        // has already brought in, so a search is never empty.
        val fromFeed = loadedMatching(q)
        val found = coroutineScope {
            val tag = async { runCatching { hashtagPosts(q) }.getOrDefault(emptyList()) }
            val related = async { runCatching { relatedPosts(fromFeed.take(5)) }.getOrDefault(emptyList()) }
            val trending = async { runCatching { trendingPosts(q) }.getOrDefault(emptyList()) }
            val trendPage = async { runCatching { trendPosts(q) }.getOrDefault(emptyList()) }
            val page = async {
                pageSearchGate.attempt {
                    runCatching { web.pageSearch(q) }.getOrDefault(emptyList()).flatMap { body ->
                        runCatching { items(json.parseToJsonElement(body)) }.getOrDefault(emptyList())
                    }
                }
            }
            val engine = async { webSearchGate.attempt { runCatching { webSearch(q) }.getOrDefault(emptyList()) } }
            // Best evidence first: the tag's own list and Douyin's own results, then the rest.
            tag.await() + trendPage.await() + page.await() + fromFeed + related.await() + trending.await() + engine.await()
        }
        // Posts that contain every word of the search first; related ones after (order kept within each).
        val all = found.distinctBy { it.id }.sortedBy { !DouyinParser.matches(it, q) }
        if (all.isEmpty()) direct.exceptionOrNull()?.let { throw it }
        return Page(all, null)
    }

    private suspend fun directSearch(q: String, offset: Int): Page<MediaItem> {
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

    private val pageSearchGate = GiveUpGate(GIVE_UP_AFTER)
    private val webSearchGate = GiveUpGate(GIVE_UP_AFTER)
    // Pages Douyin shows a signed-out visitor without their posts (creator, trending topic, single
    // post): each costs ~10 s, so after two empty tries they're skipped for the session.
    private val creatorPageGate = GiveUpGate(GIVE_UP_AFTER)
    private val trendPageGate = GiveUpGate(GIVE_UP_AFTER)
    private val postPageGate = GiveUpGate(GIVE_UP_AFTER)
    @Volatile private var lastSignedIn: Boolean? = null

    /** Signing in or out (from any screen) re-opens the look-ups that were skipped. */
    private fun resetIfSignInChanged(force: Boolean = false) {
        val now = web.signedIn()
        if (force || (lastSignedIn != null && lastSignedIn != now)) {
            listOf(pageSearchGate, webSearchGate, creatorPageGate, trendPageGate, postPageGate).forEach { it.reset() }
        }
        lastSignedIn = now
    }

    /** A hashtag's own video list, for a search that is (or contains) a hashtag seen in the feed. */
    private suspend fun hashtagPosts(q: String): List<MediaItem> {
        val key = q.trim().lowercase().removePrefix("#")
        val cid = hashtagIds[key]
            ?: hashtagIds.entries.firstOrNull { (name, _) -> name.contains(key) || key.contains(name) }?.value
            ?: return emptyList()
        return challengePosts(cid)
    }

    private suspend fun challengePosts(cid: String): List<MediaItem> =
        items(call("/aweme/v1/web/challenge/aweme/", mapOf("ch_id" to cid, "cursor" to "0", "count" to "20", "sort_type" to "1", "query_type" to "0")))

    /** Douyin's "related videos" for a few posts that match: more of the same thing. */
    private suspend fun relatedPosts(seeds: List<MediaItem>): List<MediaItem> = coroutineScope {
        seeds.map { seed ->
            async {
                runCatching {
                    items(call("/aweme/v1/web/aweme/related/", mapOf("aweme_id" to seed.id, "count" to "20", "filterGids" to "", "awemePcRecRawData" to """{"is_client":false}""", "sub_channel_id" to "3")))
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    /** Trending topics matching [q]: each topic's word goes through the tag and related paths. */
    private suspend fun trendingPosts(q: String): List<MediaItem> {
        val root = call("/aweme/v1/web/hot/search/list/", mapOf("detail_list" to "1"))
        val words = trendingWords(root)
        val key = q.trim().lowercase()
        val hits = words.filter { it.lowercase().contains(key) || key.contains(it.lowercase()) }.take(2)
        return hits.flatMap { w -> runCatching { hashtagPosts(w) }.getOrDefault(emptyList()) + loadedMatching(w) }
    }

    @Volatile private var trendingCache: Pair<Long, List<String>>? = null
    /** Trending word → its topic page id (sentence_id), from the hot list. */
    private val trendIds = ConcurrentHashMap<String, String>()

    /** Douyin's trending topics right now (its hot list), newest fetch at most 20 minutes old. */
    suspend fun trending(): List<String> {
        trendingCache?.let { (at, list) -> if (System.currentTimeMillis() - at < 20 * 60_000L && list.isNotEmpty()) return list }
        val root = call("/aweme/v1/web/hot/search/list/", mapOf("detail_list" to "1"))
        DouyinParser.trendingTopics(root).forEach { (word, sid) -> if (sid != null) trendIds[word] = sid }
        DouyinParser.trendingHeat(root).forEach { (word, heat) -> trendHeat[word] = heat }
        val words = trendingWords(root).distinct()
        trendingCache = System.currentTimeMillis() to words
        return words
    }

    /** Trending words with their heat (热度), as the hot list gives it; empty heat if not given. */
    private val trendHeat = ConcurrentHashMap<String, Long>()

    fun heatOf(word: String): Long? = trendHeat[word]

    /** A trending topic's posts, from its own page, when [q] is (or is inside) a trending word. */
    private suspend fun trendPosts(q: String): List<MediaItem> {
        if (trendIds.isEmpty()) runCatching { trending() }
        val key = q.trim()
        val sid = trendIds[key] ?: trendIds.entries.firstOrNull { (w, _) -> w.contains(key) || key.contains(w) }?.value ?: return emptyList()
        return trendPageGate.attempt {
            runCatching { web.trendPage(sid) }.getOrDefault(emptyList()).flatMap { body ->
                runCatching { items(json.parseToJsonElement(body)) }.getOrDefault(emptyList())
            }
        }
    }

    /** The words of Douyin's trending list (data.word_list[].word, or trending_list). */
    private fun trendingWords(root: JsonElement): List<String> {
        val data = (root as? JsonObject)?.get("data") as? JsonObject ?: return emptyList()
        return listOf("word_list", "trending_list").flatMap { k ->
            (data[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { ((it as? JsonObject)?.get("word") as? JsonPrimitive)?.contentOrNull }.orEmpty()
        }
    }

    /**
     * Douyin videos for [q] found the way anyone finds them: a public web search limited to
     * douyin.com (Bing, then DuckDuckGo), each post then read by id like a shared link.
     * Needs no Douyin sign-in; finds fewer and older posts than Douyin's own search.
     */
    private suspend fun webSearch(q: String): List<MediaItem> {
        val ids = engineIds(q)
        if (ids.isEmpty()) return emptyList()
        return coroutineScope {
            ids.map { awemeId -> async { kotlinx.coroutines.withTimeoutOrNull(15_000) { runCatching { fresh(awemeId) }.getOrNull() } } }
                .awaitAll().filterNotNull()
        }
    }

    /** Douyin post ids in public web-search results for [q]. */
    internal suspend fun engineIds(q: String): List<String> = withContext(Dispatchers.IO) {
        val enc = { t: String -> java.net.URLEncoder.encode(t, "UTF-8") }
        val pages = listOf(
            "https://www.bing.com/search?q=" + enc("site:douyin.com/video $q") + "&count=50&setlang=zh-Hans",
            "https://html.duckduckgo.com/html/?q=" + enc("site:douyin.com/video $q"),
        )
        val ids = LinkedHashSet<String>()
        for (u in pages) {
            val html = runCatching {
                http.newCall(
                    Request.Builder().url(u)
                        .header("User-Agent", DouyinWeb.DESKTOP_UA)
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                        .build(),
                ).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull() ?: continue
            idsInHtml(html).forEach { ids += it }
            if (ids.size >= MAX_WEB_RESULTS) break
        }
        ids.take(MAX_WEB_RESULTS)
    }

    /** Posts brought in this session (feed, creators, links) whose caption, tags or creator contain every word of [q]. */
    fun loadedMatching(q: String): List<MediaItem> {
        if (q.isBlank()) return emptyList()
        ensureIndex()
        return synchronized(loaded) { loaded.toList() }.asReversed().filter { DouyinParser.matches(it, q) }
    }

    // ── playback ──

    override suspend fun resolveStream(item: MediaItem): StreamInfo {
        // As it starts playing, fetch what Douyin shows beside it (in the background), so that if
        // you enjoy it, For You and "More like this" go on from there.
        if (item.type == MediaType.VIDEO && dev.dyfeed.domain.reco.Related.of(item.key).isEmpty()) indexScope.launch {
            runCatching { relatedPosts(listOf(item)) }.getOrNull()
                ?.filter { it.key != item.key }?.takeIf { it.size >= 3 }
                ?.let { dev.dyfeed.domain.reco.Related.put(item.key, it) }
        }
        val at = item.extras["at"]?.toLongOrNull() ?: 0L
        val cached = item.extras["media"]
        // Trust the address until the expiry it carries (if it says), else for URL_TTL_MS.
        val stillGood = cached != null && (DouyinParser.expiresAt(cached)?.let { it - System.currentTimeMillis() > 60_000 }
            ?: (System.currentTimeMillis() - at < URL_TTL_MS))
        val media = if (stillGood) cached else null
        val url = media ?: fresh(item.id)?.extras?.get("media") ?: item.extras["media"]
            ?: throw SourceException(id, "This Douyin post is gone")
        return StreamInfo.Direct(url, if (item.type == MediaType.VIDEO) "video/mp4" else null)
    }

    override fun reportUrl(item: MediaItem): String? = item.pageUrl

    /**
     * Where to try next when [failed] wouldn't play (a mirror server said 403/404): the post's other
     * addresses in order, then a fresh read of the post. Null when there's nothing left to try.
     */
    suspend fun nextAddress(item: MediaItem, failed: String, tried: Set<String>): String? {
        val all = listOfNotNull(item.extras["media"]) + item.extras[DouyinParser.ALTERNATES].orEmpty().split(' ').filter { it.isNotBlank() }
        all.firstOrNull { it != failed && it !in tried }?.let { return it }
        if (item.type != MediaType.VIDEO) return null
        val again = runCatching { fresh(item.id) }.getOrNull() ?: return null
        return (listOfNotNull(again.extras["media"]) + again.extras[DouyinParser.ALTERNATES].orEmpty().split(' '))
            .firstOrNull { it.isNotBlank() && it != failed && it !in tried }
    }

    /** One post by id, with addresses fetched now: the signed API first, else the public share page. */
    suspend fun fresh(awemeId: String): MediaItem? =
        runCatching {
            val root = call("/aweme/v1/web/aweme/detail/", mapOf("aweme_id" to awemeId))
            items(root).firstOrNull { it.id == awemeId }
        }.getOrNull()
            ?: sharePage(awemeId)
            // The post's own page, read in the second hidden page (its embedded data carries it).
            ?: postPageGate.attempt {
                runCatching { web.postPage(awemeId) }.getOrDefault(emptyList())
                    .flatMap { body -> runCatching { items(json.parseToJsonElement(body)) }.getOrDefault(emptyList()) }
                    .filter { it.id == awemeId }
            }.firstOrNull()

    /** A link shared to the app: v.douyin.com short links, douyin.com/video|note/<id>, ?modal_id=<id>, iesdouyin share pages. */
    suspend fun fromLink(url: HttpUrl): MediaItem? {
        val target = if (url.host == "v.douyin.com" || url.host == "v.iesdouyin.com") expand(url) ?: return null else url
        val awemeId = awemeIdIn(target) ?: return null
        return fresh(awemeId)
    }

    data class Comment(val author: String, val body: String, val score: Int)

    /** The post's top comments, read-only (the first page). */
    suspend fun comments(item: MediaItem): List<Comment> = commentsPage(item, 0).first

    /** One page of comments from [cursor]; the second value is where the next page starts, if there is one. */
    suspend fun commentsPage(item: MediaItem, cursor: Long): Pair<List<Comment>, Long?> {
        val root = call("/aweme/v1/web/comment/list/", mapOf("aweme_id" to item.id, "cursor" to cursor.toString(), "count" to "20", "item_type" to "0"))
        val comments = DouyinParser.comments(root).map { (who, text, likes) -> Comment(who, text, likes) }
        val next = DouyinParser.long(root, "cursor")?.takeIf { DouyinParser.hasMore(root) && it > cursor && comments.isNotEmpty() }
        return comments to next
    }

    /**
     * One line on how Douyin is answering right now, for the health test and bug reports:
     * the signed feed request's status, size and first bytes, then the backup path.
     */
    suspend fun probe(): String {
        val perPath = FEED_PATHS.map { path ->
            val r = web.get(path, mapOf("module_id" to "3003101", "count" to "10", "refresh_index" to "1", "pull_type" to "0", "video_type_select" to "1"))
            val root = runCatching { json.parseToJsonElement(r.body) }.getOrNull()
            val n = root?.let { runCatching { items(it).size }.getOrNull() }
            "${path.removePrefix("/aweme/").removeSuffix("/")}=${r.status}/${r.body.length}B/items=$n" +
                (if (root != null && n == 0) " rejected=" + DouyinParser.awemes(root).take(3).joinToString(" | ") { a ->
                    DouyinParser.whyNot(a, PlayerPool.maxHeight) ?: "ok"
                }.take(900) else "")
        }
        // Does moving the open page along bring a new batch? (Uses nothing up: only counts.)
        val nudged = runCatching { web.nudge(8_000) }.getOrDefault(-1)
        return "signedIn=${web.signedIn()} ${perPath.joinToString(" ")} pageAnswersWaiting=${web.waitingAnswers()} nudgeNewAnswers=$nudged nudgesWorkedSoFar=${web.nudgesWorked}"
    }

    /** How each way of searching does right now, for the health test. */
    suspend fun probeSearch(q: String): String {
        val direct = runCatching { directSearch(q, 0).items.size }.fold({ "$it" }, { "error(${it.message?.take(40)})" })
        val answers = runCatching { web.pageSearch(q) }.getOrDefault(emptyList())
        val pageItems = answers.sumOf { b -> runCatching { items(json.parseToJsonElement(b)).size }.getOrDefault(0) }
        val ids = runCatching { engineIds(q) }.getOrDefault(emptyList())
        val first = ids.firstOrNull()
        val viaApi = first?.let { id -> runCatching { items(call("/aweme/v1/web/aweme/detail/", mapOf("aweme_id" to id))).size }.fold({ "$it" }, { "error(${it.message?.take(30)})" }) }
        val viaShare = first?.let { id -> runCatching { sharePage(id) != null }.getOrDefault(false) }
        // Candidate endpoints that may answer a visitor; each: HTTP status / Douyin status / posts.
        val candidates = listOf(
            "/aweme/v1/web/general/search/single/" to mapOf("keyword" to q, "search_channel" to "aweme_general", "search_source" to "normal_search", "query_correct_type" to "1", "is_filter_search" to "0", "offset" to "0", "count" to "10"),
            "/aweme/v1/web/search/challenge/" to mapOf("keyword" to q, "offset" to "0", "count" to "10", "search_source" to "normal_search"),
            "/aweme/v1/web/hot/search/list/" to mapOf("detail_list" to "1"),
        ).map { (path, params) ->
            val r = web.get(path, params)
            val root = runCatching { json.parseToJsonElement(r.body) }.getOrNull()
            val sc = root?.let { DouyinParser.statusCode(it) }
            val n = root?.let { runCatching { items(it).size }.getOrNull() }
            path.removePrefix("/aweme/v1/web/").removeSuffix("/") + "=${r.status}/sc$sc/${r.body.length}B/posts=$n" +
                (if (sc != null && sc != 0) "(" + ((root as? JsonObject)?.get("status_msg") as? JsonPrimitive)?.contentOrNull?.take(20) + ")" else "")
        }.joinToString(" ")
        val anyTag = hashtagIds.entries.firstOrNull()
        val tagProbe = anyTag?.let { (name, cid) -> runCatching { "$name:" + challengePosts(cid).size }.getOrElse { "$name:error(${it.message?.take(30)})" } } ?: "no tag ids yet"
        val relProbe = synchronized(loaded) { loaded.lastOrNull() }?.let { seed ->
            runCatching { relatedPosts(listOf(seed)).size.toString() }.getOrElse { "error(${it.message?.take(30)})" }
        } ?: "no posts yet"
        val trendProbe = runCatching { trendingWords(call("/aweme/v1/web/hot/search/list/", mapOf("detail_list" to "1"))).take(5).joinToString("/") }.getOrElse { "error(${it.message?.take(30)})" }
        val pageInfo = web.searchPageInfo()
        return "tags=${hashtagIds.size} challenge=$tagProbe related=$relProbe trending=$trendProbe page=[$pageInfo] " +
            "direct=$direct page=${answers.size} answers/$pageItems posts loaded=${loadedMatching(q).size} " +
            "web=${ids.size} ids detailApi=$viaApi sharePage=$viaShare | $candidates | searchPageCalls=" +
            web.searchTrace().takeLast(12).joinToString("; ")
    }

    /** What the second hidden page last did (where it landed, the Douyin calls it made), for the health probe. */
    suspend fun secondPageReport(): String =
        "page=[" + web.searchPageInfo() + "] calls=" + web.searchTrace().takeLast(10).joinToString("; ")

    /** A short sketch of a JSON answer's layout: keys, array sizes, the first element of each array. */
    private fun shape(e: JsonElement, depth: Int = 0): String = when {
        depth > 2 -> "…"
        e is JsonObject -> e.entries.take(14).joinToString(",", "{", "}") { (k, v) -> k + ":" + shape(v, depth + 1) }
        e is kotlinx.serialization.json.JsonArray -> "[${e.size}]" + (e.firstOrNull()?.let { shape(it, depth + 1) } ?: "")
        e is JsonPrimitive -> if (e.isString) "s${e.content.length}" else e.content.take(6)
        else -> "null"
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

    /** Hashtag name → Douyin's id for it, learned from posts as they arrive. */
    private val hashtagIds = ConcurrentHashMap<String, String>()

    /** The last [MAX_LOADED] posts parsed this session, for search without signing in. */
    private val loaded = ArrayDeque<MediaItem>()

    /**
     * The posts behind signed-out search survive restarts: the last [MAX_LOADED] are kept in the
     * app's private storage (not in Private mode), so a search can match what you saw yesterday.
     */
    private val indexFile by lazy { java.io.File(context.filesDir, "douyin_index.json") }
    private val indexScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    @Volatile private var indexLoaded = false
    private var unsaved = 0

    init {
        dev.dyfeed.domain.privacy.Forget.register {
            synchronized(loaded) { loaded.clear(); unsaved = 0 }
            runCatching { indexFile.delete() }
        }
    }

    private fun ensureIndex() {
        if (indexLoaded) return
        synchronized(loaded) {
            if (indexLoaded) return
            indexLoaded = true
            val saved = runCatching {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MediaItem.serializer()), indexFile.readText())
            }.getOrDefault(emptyList())
            val have = loaded.mapTo(HashSet()) { it.id }
            saved.filter { it.id !in have }.asReversed().forEach { loaded.addFirst(it) }
            while (loaded.size > MAX_LOADED) loaded.removeFirst()
        }
    }

    private fun saveIndexSoon(added: Int) {
        if (dev.dyfeed.domain.privacy.Incognito.active) return
        unsaved += added
        if (unsaved < SAVE_EVERY) return
        unsaved = 0
        val snapshot = synchronized(loaded) { loaded.toList() }
        indexScope.launch {
            runCatching {
                val tmp = java.io.File(indexFile.path + ".tmp")
                tmp.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MediaItem.serializer()), snapshot))
                tmp.renameTo(indexFile)
            }
        }
    }

    private fun items(root: JsonElement): List<MediaItem> =
        DouyinParser.parseAll(root, PlayerPool.maxHeight).also { ensureIndex(); saveIndexSoon(it.size) }.onEach { item ->
            synchronized(loaded) {
                loaded.removeAll { it.id == item.id }
                loaded.addLast(item)
                while (loaded.size > MAX_LOADED) loaded.removeFirst()
            }
            item.extras["tagIds"]?.split(';')?.forEach { pair ->
                val name = pair.substringBefore('=')
                val cid = pair.substringAfter('=', "")
                if (name.isNotEmpty() && cid.isNotEmpty()) hashtagIds[name] = cid
            }
            item.creator?.let { name -> item.extras["avatar"]?.let { rememberAvatar(name, it) } }
            val sec = item.extras["secUid"] ?: return@onEach
            item.creator?.let { remember(it, sec) }
        }

    private fun key(name: String) = name.lowercase().filterNot { it.isWhitespace() || it == '_' }

    private fun remember(name: String, secUid: String) {
        val k = key(name)
        if (creators.put(k, secUid) != secUid) prefs.edit { putString(k, secUid) }
    }

    private fun rememberAvatar(name: String, url: String) = CreatorAvatars.remember(name, url)

    /** A creator's profile picture, if one of their posts has come by (kept across restarts). */
    fun avatarFor(name: String): String? = CreatorAvatars.get(name)

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
        private const val MAX_LOADED = 3_000
        private const val SAVE_EVERY = 25
        private const val MAX_WEB_RESULTS = 16
        private const val GIVE_UP_AFTER = 2

        /** douyin.com/video/<id> (or /note/), plain or URL-encoded inside a search engine's redirect link. */
        private val VIDEO_LINK = Regex("""douyin\.com(?:/|%2F)(?:video|note)(?:/|%2F)(\d{15,21})""", RegexOption.IGNORE_CASE)

        fun idsInHtml(html: String): List<String> = VIDEO_LINK.findAll(html).map { it.groupValues[1] }.distinct().toList()
        private val FEED_PATHS = listOf("/aweme/v2/web/module/feed/", "/aweme/v1/web/module/feed/", "/aweme/v1/web/tab/feed/")
        private const val MAX_FOLLOWED = 30
        private const val MAX_FOLLOWED_PAGES = 4
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

/**
 * Gives up on a way of searching that keeps finding nothing: after [giveUpAfter] empty tries in a
 * row (and never a hit) it isn't asked again this session. Douyin's search page shows a visitor a
 * captcha and search engines don't list Douyin for some networks; waiting ~20 s on each for every
 * search (and every topic row) made search slow for nothing.
 */
internal class GiveUpGate(private val giveUpAfter: Int) {
    @Volatile private var misses = 0
    @Volatile private var everWorked = false

    val open: Boolean get() = everWorked || misses < giveUpAfter

    /** Try again from scratch (after signing in, Douyin may answer what it didn't before). */
    fun reset() { misses = 0; everWorked = false }

    suspend fun <T> attempt(block: suspend () -> List<T>): List<T> {
        if (!open) return emptyList()
        val r = block()
        if (r.isEmpty()) misses++ else { everWorked = true; misses = 0 }
        return r
    }
}
