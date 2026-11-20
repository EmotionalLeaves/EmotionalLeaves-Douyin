package dev.dyfeed.source.douyin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Douyin's web API only answers requests that carry a signature (`a_bogus`, `msToken`) computed by
 * the obfuscated script on douyin.com, and the signature changes whenever Douyin ships a new script.
 * Rather than copy that script (and break every time it changes), the app keeps one invisible
 * WebView open on douyin.com and sends its API requests *from inside the page*: Douyin's own script
 * wraps XMLHttpRequest there and signs every call itself, with the page's own cookies.
 *
 * The page's pictures and videos are never downloaded (they're answered with nothing), so the hidden
 * page costs little data; the app plays the videos itself from the addresses in the answers.
 *
 * Cookies are shared with [DouyinLoginActivity], so signing in (or clearing a "verify you're human"
 * check) there carries over here.
 */
@Singleton
class DouyinWeb @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    class Reply(val status: Int, val body: String)

    private var web: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var loadedAt = 0L
    private val loadMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Reply>>()
    private val seq = AtomicLong()
    @Volatile private var badAnswers = 0
    /** Feed answers the page fetched for itself (see [HOOK_JS]), newest last. */
    private val captured = java.util.concurrent.ConcurrentLinkedDeque<String>()
    @Volatile private var capturedSignal = CompletableDeferred<Unit>()

    /**
     * The backup path: recommended-feed answers Douyin's own page loaded by itself (signed by
     * itself), when the app's own requests are being turned away. Takes what's waiting; if
     * nothing is, opens a fresh page (which loads a fresh batch) and waits up to [waitMs] for it.
     */
    private var searchWeb: WebView? = null
    private val searchCaptured = java.util.concurrent.ConcurrentLinkedDeque<String>()
    @Volatile private var searchSignal = CompletableDeferred<Unit>()
    private val searchMutex = Mutex()
    /** The last Douyin API calls the search page made ("path status size"), for the health probe. */
    private val searchTrace = java.util.concurrent.ConcurrentLinkedDeque<String>()

    fun searchTrace(): List<String> = searchTrace.toList()

    /** Where the hidden search page ended up and what it shows, for the health probe. */
    suspend fun searchPageInfo(): String = withContext(Dispatchers.Main) {
        val w = searchWeb ?: return@withContext "no search page"
        val done = CompletableDeferred<String>()
        w.evaluateJavascript(
            "(function(){var r=document.getElementById('RENDER_DATA');var t=r?decodeURIComponent(r.textContent):'';" +
                "function n(s){return t.split(s).length-1}" +
                "return location.href.slice(0,80)+' | '+document.title.slice(0,40)+' | render='+t.length+' awemeId='+n('awemeId')+' aweme_id='+n('aweme_id')+' playAddr='+n('playAddr')+' | login='+(!!document.querySelector('[class*=login],[id*=login]'))})()",
        ) { done.complete(it ?: "") }
        withTimeoutOrNull(3_000) { done.await() } ?: "no answer"
    }

    /**
     * Search without signing in: open Douyin's own search page for [keyword] in a second hidden
     * page and take the results it loads for itself (its requests carry everything a visitor's
     * browser sends). Returns the raw answers; empty if Douyin showed it nothing.
     */
    suspend fun pageSearch(keyword: String, waitMs: Long = 20_000): List<String> =
        secondPage("https://www.douyin.com/search/" + android.net.Uri.encode(keyword) + "?type=video", waitMs) { url, body ->
            "search" in url && body.contains("aweme_info")
        }

    /**
     * A creator's posts without signing in: Douyin answers the post-list API with 403 for a visitor,
     * but the creator's own page still loads their posts for itself, so open it and take those.
     */
    suspend fun creatorPage(secUid: String, waitMs: Long = 10_000): List<String> =
        secondPage("https://www.douyin.com/user/" + android.net.Uri.encode(secUid), waitMs, withRenderData = true) { url, body ->
            "/aweme/post" in url && body.contains("aweme_list")
        }

    private val idleScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    @Volatile private var closeSecond: kotlinx.coroutines.Job? = null

    /** A trending topic's own page (douyin.com/hot/<sentence id>): the posts it carries. */
    suspend fun trendPage(sentenceId: String, waitMs: Long = 8_000): List<String> =
        secondPage("https://www.douyin.com/hot/" + android.net.Uri.encode(sentenceId), waitMs, withRenderData = true) { url, body ->
            ("/hot/search" in url || "/aweme/" in url) && body.contains("aweme")
        }

    /** One post's own page (douyin.com/video/<id>): its embedded data, and the detail it may fetch. */
    suspend fun postPage(awemeId: String, waitMs: Long = 8_000): List<String> =
        secondPage("https://www.douyin.com/video/" + android.net.Uri.encode(awemeId), waitMs, withRenderData = true) { url, body ->
            "/aweme/detail" in url && body.contains("aweme_detail")
        }

    /** The second page's embedded data (RENDER_DATA, URL-decoded JSON), if it has any. */
    private suspend fun renderData(): String? = withContext(Dispatchers.Main) {
        val w = searchWeb ?: return@withContext null
        val done = CompletableDeferred<String?>()
        w.evaluateJavascript(
            "(function(){try{var r=document.getElementById('RENDER_DATA');return r?decodeURIComponent(r.textContent):''}catch(e){return ''}})()",
        ) { v -> done.complete(v) }
        val raw = withTimeoutOrNull(5_000) { done.await() } ?: return@withContext null
        // evaluateJavascript hands back the string as a JSON string literal.
        runCatching { (kotlinx.serialization.json.Json.parseToJsonElement(raw) as JsonPrimitive).content }
            .getOrNull()?.takeIf { it.length > 100 }
    }

    /** What the second hidden page accepts as an answer worth keeping (url, body). */
    @Volatile private var secondAccept: (String, String) -> Boolean = { _, _ -> false }

    /** Opens [url] in the second hidden page and returns the answers it fetches that [accept] keeps. */
    private suspend fun secondPage(
        url: String,
        waitMs: Long,
        withRenderData: Boolean = false,
        accept: (String, String) -> Boolean,
    ): List<String> = awake { secondPageNow(url, waitMs, withRenderData, accept) }

    private suspend fun secondPageNow(
        url: String,
        waitMs: Long,
        withRenderData: Boolean,
        accept: (String, String) -> Boolean,
    ): List<String> = searchMutex.withLock {
        closeSecond?.cancel()
        searchCaptured.clear()
        searchSignal = CompletableDeferred()
        secondAccept = accept
        withContext(Dispatchers.Main) {
            val w = searchWeb ?: WebView(context).also { configure(it, Bridge(search = true), onReady = null); searchWeb = it }
            w.loadUrl(url)
        }
        withTimeoutOrNull(waitMs) { searchSignal.await() }
        // Douyin often answers in two parts (the first screen, then the rest): give it a moment.
        delay(1_500)
        val out = ArrayList<String>()
        while (true) out += searchCaptured.pollFirst() ?: break
        // Some pages (a creator's) arrive with their posts already inside, in RENDER_DATA, and
        // never ask for them: take that too.
        if (withRenderData) renderData()?.let { out += it }
        // A whole browser page in memory: close it once it's gone unused for a while.
        closeSecond = idleScope.launch {
            delay(SECOND_PAGE_IDLE_MS)
            withContext(Dispatchers.Main) { searchWeb?.destroy(); searchWeb = null }
        }
        out
    }

    /** Loads the page now (if it isn't already), so the first feed request doesn't wait for it. */
    suspend fun warmUp() = awake { ensurePage() }

    // ── pausing when idle ──
    // Douyin's site keeps its scripts busy all the time (timers, logging, polling) even in a hidden
    // page, taking CPU from the video you're watching. Between requests the app's web pages are
    // paused (all their timers and scripts), and woken for the next request.
    private val users = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var pauseJob: kotlinx.coroutines.Job? = null

    private suspend fun <T> awake(block: suspend () -> T): T {
        users.incrementAndGet()
        pauseJob?.cancel()
        if (timersPaused) withContext(Dispatchers.Main) { wakeTimers(context, web ?: searchWeb) }
        try {
            return block()
        } finally {
            if (users.decrementAndGet() == 0) pauseJob = idleScope.launch {
                delay(IDLE_PAUSE_MS)
                if (users.get() == 0) withContext(Dispatchers.Main) {
                    val any = web ?: searchWeb
                    if (any != null && users.get() == 0 && visiblePages == 0) { any.pauseTimers(); timersPaused = true }
                }
            }
        }
    }

    /**
     * Closes both hidden pages (the app has been in the background a while): Douyin's scripts
     * would otherwise keep running and draining the battery. The next request opens a fresh one.
     */
    suspend fun sleep() {
        reload()
        closeSecond?.cancel()
        withContext(Dispatchers.Main) { searchWeb?.destroy(); searchWeb = null }
    }

    /** How many page-fetched feed answers are waiting (without using them up). */
    fun waitingAnswers(): Int = captured.size

    suspend fun harvest(waitMs: Long = 15_000): List<String> = awake { harvestNow(waitMs) }

    private suspend fun harvestNow(waitMs: Long): List<String> {
        // First the cheap way: move the open page along (as pressing ↓ does on douyin.com), which
        // makes it fetch its next batch. Only if that brings nothing, start a fresh page.
        // Stops trying once it has failed twice without ever working (the emulator showed Douyin
        // ignoring the simulated keys): then each batch would just wait 7 s for nothing.
        val worthNudging = nudgesWorked > 0 || nudgesFailed < 2
        if (captured.isEmpty() && web != null && worthNudging) {
            if (nudge(NUDGE_WAIT_MS) > 0) nudgesWorked++ else nudgesFailed++
        }
        if (captured.isEmpty()) {
            capturedSignal = CompletableDeferred()
            reload()
            ensurePage()
            if (withTimeoutOrNull(waitMs) { capturedSignal.await() } != null) {
                // A fresh page often fetches a second batch right after the first: take it too.
                delay(SECOND_BATCH_WAIT_MS)
            }
        }
        val out = ArrayList<String>()
        while (true) out += captured.pollFirst() ?: break
        return out
    }

    /** How often moving the page along brought a new batch, or didn't (for the health probe). */
    @Volatile var nudgesWorked = 0
        private set
    @Volatile private var nudgesFailed = 0

    /**
     * Presses ↓ on the open page a few times (and scrolls it), the way someone watching
     * douyin.com moves to the next videos, then waits up to [waitMs] for the batch it fetches.
     * Returns how many new feed answers arrived.
     */
    suspend fun nudge(waitMs: Long): Int = awake { nudgeNow(waitMs) }

    private suspend fun nudgeNow(waitMs: Long): Int {
        val before = captured.size
        capturedSignal = CompletableDeferred()
        withContext(Dispatchers.Main) { web?.evaluateJavascript(NUDGE_JS, null) }
        withTimeoutOrNull(waitMs) { capturedSignal.await() }
        delay(500)
        return (captured.size - before).coerceAtLeast(0)
    }

    /**
     * GET [path] (e.g. `/aweme/v1/web/tab/feed/`) on www.douyin.com with [params] plus the usual
     * web-client parameters, signed by the page. Status -1 means the request itself failed.
     */
    suspend fun get(path: String, params: Map<String, String>, timeoutMs: Long = 15_000): Reply = awake { getNow(path, params, timeoutMs) }

    private suspend fun getNow(path: String, params: Map<String, String>, timeoutMs: Long): Reply {
        ensurePage()
        val id = seq.incrementAndGet().toString()
        val done = CompletableDeferred<Reply>()
        pending[id] = done
        val args = buildJsonObject { params.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        val js = "($CALL_JS)(${JsonPrimitive(id)}, ${JsonPrimitive(path)}, $args)"
        try {
            withContext(Dispatchers.Main) { web?.evaluateJavascript(js, null) }
            val reply = withTimeoutOrNull(timeoutMs) { done.await() } ?: Reply(-1, "")
            // A page whose signer has gone stale answers with nothing; start it over after a few.
            if (reply.status != 200 || reply.body.isBlank()) {
                if (++badAnswers >= 3) { badAnswers = 0; reload() }
            } else badAnswers = 0
            return reply
        } finally {
            pending.remove(id)
        }
    }

    /** Throws away the page (after signing in, or when it stops answering); the next call loads a fresh one. */
    suspend fun reload() = loadMutex.withLock {
        withContext(Dispatchers.Main) {
            web?.destroy()
            web = null
        }
        ready = null
    }

    fun signedIn(): Boolean = isSignedIn()

    private suspend fun ensurePage() {
        val r = loadMutex.withLock {
            val stale = System.currentTimeMillis() - loadedAt > PAGE_TTL_MS
            if (stale && ready != null) {
                withContext(Dispatchers.Main) { web?.destroy(); web = null }
                ready = null
            }
            ready ?: CompletableDeferred<Unit>().also { d ->
                ready = d
                loadedAt = System.currentTimeMillis()
                withContext(Dispatchers.Main) { createPage(d) }
            }
        }
        // Even if the page never says it's finished, try after the wait: often the signer is there.
        withTimeoutOrNull(READY_TIMEOUT_MS) { r.await() }
    }

    private fun createPage(done: CompletableDeferred<Unit>) {
        val w = WebView(context)
        configure(w, Bridge(search = false), onReady = done)
        web = w
        // Alternate between the home feed and 精选 (featured): each loads its own batch, so taking
        // turns brings more posts, and more varied ones, per fresh page.
        w.loadUrl(FEED_PAGES[(pageLoads++) % FEED_PAGES.size])
    }

    @Volatile private var pageLoads = 0

    /**
     * Settings shared by both hidden pages: a desktop browser, no pictures or video downloaded,
     * and the hook that notes the answers the page fetches for itself. [onReady] completes once
     * Douyin's signer script is in place (only the main page waits for it).
     */
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configure(w: WebView, bridge: Bridge, onReady: CompletableDeferred<Unit>?) {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(w, true)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = DESKTOP_UA
            mediaPlaybackRequiresUserGesture = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
        }
        w.addJavascriptInterface(bridge, "DYB")
        // Before any of the page's own scripts: note the answers it fetches for itself.
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching { androidx.webkit.WebViewCompat.addDocumentStartJavaScript(w, HOOK_JS, setOf("https://www.douyin.com")) }
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {}

            override fun onPageFinished(view: WebView, url: String?) {
                val done = onReady ?: return
                if (done.isCompleted) return
                // Wait for Douyin's signer script to install itself, then a moment more.
                view.postDelayed(object : Runnable {
                    var tries = 0
                    override fun run() {
                        view.evaluateJavascript(SIGNER_PRESENT_JS) { v ->
                            if (v == "true" || ++tries >= 20) {
                                view.postDelayed({ done.complete(Unit) }, 800)
                                view.onPause() // stops animations; scripts keep running
                            } else view.postDelayed(this, 500)
                        }
                    }
                }, 500)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                val host = u.host.orEmpty()
                val path = u.path.orEmpty()
                val heavy = MEDIA_HOSTS.any { host.contains(it) } ||
                    MEDIA_EXT.any { path.endsWith(it, ignoreCase = true) } ||
                    request.requestHeaders["Accept"]?.startsWith("image/") == true
                return if (heavy) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
            }
        }
    }

    private inner class Bridge(private val search: Boolean) {
        @JavascriptInterface
        fun reply(id: String, status: Int, body: String) {
            pending[id]?.complete(Reply(status, body))
        }

        @JavascriptInterface
        fun saw(url: String, status: Int, size: Int) {
            if (!search) return
            searchTrace.addLast(url.substringBefore('?').removePrefix("https://www.douyin.com") + " $status ${size}B")
            while (searchTrace.size > 30) searchTrace.pollFirst()
        }

        @JavascriptInterface
        fun captured(url: String, body: String) {
            if (body.isBlank()) return
            val isSearch = "search" in url
            if (search) {
                if (!secondAccept(url, body)) return
                searchCaptured.addLast(body)
                while (searchCaptured.size > MAX_CAPTURED) searchCaptured.pollFirst()
                searchSignal.complete(Unit)
            } else {
                if (isSearch || "/aweme/post" in url || !body.contains("aweme")) return
                captured.addLast(body)
                while (captured.size > MAX_CAPTURED) captured.pollFirst()
                capturedSignal.complete(Unit)
            }
        }
    }

    companion object {
        const val HOME = "https://www.douyin.com/?recommend=1"
        private val FEED_PAGES = listOf(HOME, "https://www.douyin.com/jingxuan")

        /** True when douyin.com's sign-in cookie is set (signed in on the Douyin page in this app). */
        fun isSignedIn(): Boolean = runCatching {
            CookieManager.getInstance().getCookie("https://www.douyin.com")
                ?.split(';')?.any { it.trim().startsWith("sessionid=") && it.substringAfter('=').isNotBlank() } == true
        }.getOrDefault(false)

        /** Douyin serves its web client (and signer) only to desktop browsers. */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val PAGE_TTL_MS = 30 * 60_000L
        private const val MAX_CAPTURED = 6
        private const val NUDGE_WAIT_MS = 7_000L
        private const val SECOND_BATCH_WAIT_MS = 2_000L
        private const val SECOND_PAGE_IDLE_MS = 120_000L
        /** Unused this long, the pages are paused (a feed pull or search wakes them at once). */
        private const val IDLE_PAUSE_MS = 6_000L

        /** Web pages on screen (Douyin's check, an embedded player): nothing is paused while any is. */
        @Volatile var visiblePages = 0

        /** Whether the app's web pages are paused (timers are paused for all of the app's web views at once). */
        @Volatile var timersPaused = false
            private set

        /**
         * Wakes every web view in the app. Any instance does it, so [existing] is used when there is
         * one; only if there's none is a throwaway one made (making a web view costs the main
         * thread a lot, so not on every request). Main thread.
         */
        fun wakeTimers(context: Context, existing: WebView? = null) {
            if (!timersPaused) return
            if (existing != null) existing.resumeTimers()
            else WebView(context.applicationContext).apply { resumeTimers(); destroy() }
            timersPaused = false
        }

        /** ↓ ten times, a third of a second apart, on the page and whatever has focus, plus a scroll. */
        private val NUDGE_JS = """
            (function () {
              var n = 0;
              function press() {
                try {
                  var opts = { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, which: 40, bubbles: true, cancelable: true };
                  var targets = [document, document.body, document.activeElement];
                  for (var i = 0; i < targets.length; i++) if (targets[i]) {
                    targets[i].dispatchEvent(new KeyboardEvent('keydown', opts));
                    targets[i].dispatchEvent(new KeyboardEvent('keyup', opts));
                  }
                  window.scrollBy(0, window.innerHeight || 800);
                  var sc = document.querySelector('[data-e2e="feed-active-video"]');
                  if (sc && sc.parentElement) sc.parentElement.scrollTop += 2000;
                } catch (e) {}
                if (++n < 10) setTimeout(press, 330);
              }
              press();
              return true;
            })()
        """.trimIndent()

        /**
         * Wraps XMLHttpRequest and fetch so feed answers the page asks for itself are passed to the
         * app. The app's own requests are marked (`__dyMine`) and skipped, since they're answered directly.
         */
        private val HOOK_JS = """
            (function () {
              if (window.__dyHooked) return; window.__dyHooked = true;
              var FEED = /\/aweme\/v[12]\/web\/(tab\/feed|module\/feed|aweme\/post|search\/item|search\/single|general\/search\/single|general\/search\/stream)\//;
              function pass(u, t) { try { if (t && t.length > 50) DYB.captured(String(u), t); } catch (e) {} }
              var open = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function (m, u) {
                try { this.__dyUrl = String(u); } catch (e) {}
                return open.apply(this, arguments);
              };
              var send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.send = function () {
                var x = this;
                if (!x.__dyMine && /\/aweme\//.test(x.__dyUrl || '')) {
                  x.addEventListener('loadend', function () {
                    try { DYB.saw(String(x.__dyUrl), x.status, (x.responseText || '').length); } catch (e) {}
                  });
                }
                if (!x.__dyMine && FEED.test(x.__dyUrl || '')) {
                  x.addEventListener('load', function () { if (x.status === 200) pass(x.__dyUrl, x.responseText); });
                }
                return send.apply(this, arguments);
              };
              if (window.fetch) {
                var f = window.fetch;
                window.fetch = function (input) {
                  var url = typeof input === 'string' ? input : (input && input.url) || '';
                  var p = f.apply(this, arguments);
                  if (/\/aweme\//.test(url)) p.then(function (r) {
                    if (!r) return;
                    r.clone().text().then(function (t) {
                      try { DYB.saw(String(url), r.status, t.length); } catch (e) {}
                      if (r.ok && FEED.test(url)) pass(url, t);
                    });
                  }).catch(function () {});
                  return p;
                };
              }
            })();
        """.trimIndent()
        private const val READY_TIMEOUT_MS = 14_000L

        private val MEDIA_HOSTS = listOf("douyinvod.com", "douyinpic.com", "douyincdn.com", "zjcdn.com", "douyinliving.com")
        private val MEDIA_EXT = listOf(".mp4", ".m3u8", ".flv", ".jpg", ".jpeg", ".png", ".webp", ".heic", ".gif", ".woff", ".woff2", ".mp3")

        private const val SIGNER_PRESENT_JS =
            "(function(){try{return !!(window.bdms||window.byted_acrawler||window._SdkGlueInit||window.__ac_nonce||document.querySelector('script[src*=\"bdms\"],script[src*=\"sdk-glue\"]'))}catch(e){return false}})()"

        /**
         * Sends one GET from inside the page, with the parameters the web client always sends. They
         * describe a desktop Chrome on Windows, to match the page's user agent.
         */
        private val CALL_JS = """
            function(id, path, extra) {
              try {
                var n = navigator, s = window.screen || {};
                var m = (n.userAgent || '').match(/Chrome\/([\d.]+)/);
                var cv = m ? m[1] : '131.0.0.0';
                var p = {
                  device_platform: 'webapp', aid: '6383', channel: 'channel_pc_web', update_version_code: '170400',
                  pc_client_type: '1', pc_libra_divert: 'Windows', version_code: '190500', version_name: '19.5.0',
                  cookie_enabled: 'true', screen_width: '1920', screen_height: '1080',
                  browser_language: 'zh-CN', browser_platform: 'Win32', browser_name: 'Chrome', browser_version: cv,
                  browser_online: 'true', engine_name: 'Blink', engine_version: cv, os_name: 'Windows', os_version: '10',
                  cpu_core_num: String(n.hardwareConcurrency || 8), device_memory: String(n.deviceMemory || 8),
                  platform: 'PC', downlink: '10', effective_type: '4g', round_trip_time: '50'
                };
                var c = document.cookie || '';
                var wid = (c.match(/(?:^|; )webid=([^;]*)/) || [])[1];
                if (wid) p.webid = wid;
                // The "verify" fingerprint cookie goes in verifyFp / fp, as the web client sends it.
                var fp = (c.match(/(?:^|; )s_v_web_id=([^;]*)/) || [])[1];
                if (fp) { p.verifyFp = fp; p.fp = fp; }
                for (var k in extra) p[k] = extra[k];
                var qs = Object.keys(p).map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(p[k]); }).join('&');
                var x = new XMLHttpRequest();
                x.__dyMine = true;
                x.open('GET', path + '?' + qs, true);
                x.withCredentials = true;
                x.timeout = 14000;
                x.onload = function () { DYB.reply(id, x.status, x.responseText || ''); };
                x.onerror = function () { DYB.reply(id, -1, ''); };
                x.ontimeout = function () { DYB.reply(id, -1, ''); };
                x.send();
              } catch (e) { DYB.reply(id, -2, String(e)); }
            }
        """.trimIndent()
    }
}
