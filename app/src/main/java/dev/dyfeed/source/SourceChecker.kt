package dev.dyfeed.source

import dev.dyfeed.domain.model.FeedMode
import dev.dyfeed.domain.model.FeedPrefs
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.domain.model.StreamInfo
import dev.dyfeed.source.net.HttpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Check sources": asks one source for posts and tries to open one of their files, from this phone
 * and connection, and says in plain words what happened. Sites behave differently per network and
 * country, so this is the ground truth for "why isn't X showing up".
 */
@Singleton
class SourceChecker @Inject constructor(private val http: HttpJson) {

    data class Result(val ok: Boolean, val detail: String)

    suspend fun check(a: SourceAdapter): Result {
        // Douyin: posts already in hand (with live addresses) answer "do files load" in seconds;
        // one small signed request (today's trending list) answers "does Douyin still answer".
        // Fetching a fresh batch from cold took up to two minutes.
        if (a is dev.dyfeed.source.douyin.DouyinAdapter) quickDouyin(a)?.let { return it }
        val listed: List<MediaItem> = try {
            withTimeoutOrNull(LIST_TIMEOUT_MS) {
                a.fetchFeed(null, FeedPrefs(FeedMode.LATEST)).items.ifEmpty { a.search(PROBE_QUERY, null).items }
            } ?: return Result(false, "Didn't answer within ${LIST_TIMEOUT_MS / 1000} s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result(false, e.message?.take(140) ?: e.javaClass.simpleName)
        }
        if (listed.isEmpty()) {
            // Sources that only show what you add yourself are fine when empty.
            return if (a.id in USER_FILLED) Result(true, "Nothing added to it yet") else Result(false, "Answered, but with no posts")
        }

        // Try up to three posts' files: one dead link shouldn't condemn a source.
        var tried = 0
        for (item in listed.take(3)) {
            val url = try {
                (withTimeoutOrNull(FILE_TIMEOUT_MS) { a.resolveStream(item) } as? StreamInfo.Direct)?.url
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            if (!url.startsWith("http")) return Result(true, "${listed.size} posts (on this device)")
            tried++
            if (withTimeoutOrNull(FILE_TIMEOUT_MS) { http.reachable(url) } == true) {
                return Result(true, "Working: ${listed.size} posts, files load")
            }
        }
        return if (tried == 0) Result(false, "Lists ${listed.size} posts, but none had a file to open")
        else Result(false, "Lists ${listed.size} posts, but their files aren't loading (the site's file servers, or blocked on this network)")
    }

    private suspend fun quickDouyin(a: dev.dyfeed.source.douyin.DouyinAdapter): Result? = kotlinx.coroutines.coroutineScope {
        val t0 = System.currentTimeMillis()
        val ready = withContext(kotlinx.coroutines.Dispatchers.IO) { a.readyPosts(5) }
        if (ready.isEmpty()) { lastQuick = "no posts with live addresses in hand"; return@coroutineScope null }
        val answers = async { withTimeoutOrNull(QUICK_SIGNED_MS) { runCatching { a.trending() }.getOrDefault(emptyList()) } }
        // Each post's mirrors too: some of Douyin's servers turn away some networks (the player
        // moves on to the next one, and so does this).
        val urls = ready.flatMap { item ->
            listOfNotNull(item.extras["media"]) + item.extras[dev.dyfeed.source.douyin.DouyinParser.ALTERNATES].orEmpty().split(' ').filter { it.isNotBlank() }.take(2)
        }.distinct().take(12)
        val loads = urls.map { url -> async { withTimeoutOrNull(QUICK_FILE_MS) { http.reachable(url) } == true } }
        val fileOk = loads.awaitAll().any { it }
        val douyinOk = !answers.await().isNullOrEmpty()
        lastQuick = "${ready.size} ready, files ${if (fileOk) "load" else "didn't load"}, Douyin ${if (douyinOk) "answered" else "didn't answer"}, ${System.currentTimeMillis() - t0} ms"
        when {
            fileOk && douyinOk -> Result(true, "Working: ${a.knownCount()} posts, files load")
            fileOk -> Result(true, "Videos load; Douyin is slow to answer right now (the feed may take a moment)")
            else -> null // not sure: do the full check
        }
    }

    /** What the quick Douyin check found last time (for the health test). */
    @Volatile var lastQuick: String = "not run"
        private set

    companion object {
        private const val PROBE_QUERY = "美食"
        /** One small signed request (trending) on a warm page answers in a second or two. */
        private const val QUICK_SIGNED_MS = 8_000L
        /** A live video address answers its first bytes in well under this; any one of three will do. */
        private const val QUICK_FILE_MS = 6_000L
        private val USER_FILLED = setOf("rss", "local", "direct", "json", "api")
        // Douyin from cold: open its page, then wait for the batch it loads (~35 s at worst).
        private const val LIST_TIMEOUT_MS = 50_000L
        private const val FILE_TIMEOUT_MS = 10_000L
    }
}
