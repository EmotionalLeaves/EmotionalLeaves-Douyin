package dev.pfeed.source.douyin

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

    /**
     * GET [path] (e.g. `/aweme/v1/web/tab/feed/`) on www.douyin.com with [params] plus the usual
     * web-client parameters, signed by the page. Status -1 means the request itself failed.
     */
    suspend fun get(path: String, params: Map<String, String>, timeoutMs: Long = 15_000): Reply {
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

    fun signedIn(): Boolean = CookieManager.getInstance().getCookie("https://www.douyin.com")
        ?.split(';')?.any { it.trim().startsWith("sessionid=") && it.substringAfter('=').isNotBlank() } == true

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

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createPage(done: CompletableDeferred<Unit>) {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        val w = WebView(context)
        cookies.setAcceptThirdPartyCookies(w, true)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = DESKTOP_UA
            mediaPlaybackRequiresUserGesture = true
            blockNetworkImage = true
            loadsImagesAutomatically = false
        }
        w.addJavascriptInterface(Bridge(), "DYB")
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {}

            override fun onPageFinished(view: WebView, url: String?) {
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
        web = w
        w.loadUrl(HOME)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun reply(id: String, status: Int, body: String) {
            pending[id]?.complete(Reply(status, body))
        }
    }

    companion object {
        const val HOME = "https://www.douyin.com/?recommend=1"

        /** Douyin serves its web client (and signer) only to desktop browsers. */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val PAGE_TTL_MS = 30 * 60_000L
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
                var wid = (c.match(/(?:^|; )(?:webid|s_v_web_id)=([^;]*)/) || [])[1];
                if (wid) p.webid = wid;
                for (var k in extra) p[k] = extra[k];
                var qs = Object.keys(p).map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(p[k]); }).join('&');
                var x = new XMLHttpRequest();
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
