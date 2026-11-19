package dev.dyfeed.source.douyin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * Douyin's own site, in full, so you can sign in (QR code from the Douyin app, or phone number) or
 * pass the "verify you're human" slider it sometimes shows. Cookies are shared with the hidden page
 * the feed uses ([DouyinWeb]), so whatever you do here carries over. Tap Done when finished.
 */
class DouyinLoginActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = DouyinWeb.DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(32, 16, 16, 16)
            addView(
                TextView(context).apply {
                    text = "Sign in or pass Douyin's check, then tap Done"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (DouyinWeb.isSignedIn()) {
                // Signed in already: offer the way out too (clears Douyin's cookies in this app).
                addView(Button(context).apply {
                    text = "Sign out"
                    setOnClickListener {
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                            visibility = android.view.View.GONE
                            web.loadUrl(DouyinWeb.HOME)
                        }
                    }
                })
            }
            addView(Button(context).apply { text = "Done"; setOnClickListener { finish() } })
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(FrameLayout(context).apply { addView(web) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(s.left, s.top, s.right, s.bottom)
            insets
        }
        setContentView(root)
        web.loadUrl(savedInstanceState?.getString(KEY_URL) ?: DouyinWeb.HOME)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.url?.let { outState.putString(KEY_URL, it) }
    }

    @Deprecated("Back goes back in the page first")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        web.destroy()
        if (isFinishing) closed?.complete(Unit)
        super.onDestroy()
    }

    companion object {
        private const val KEY_URL = "url"
        @Volatile private var closed: CompletableDeferred<Unit>? = null

        /** Opens the page; the result completes when it's closed. */
        fun open(activity: Activity): CompletableDeferred<Unit> {
            closed?.complete(Unit)
            val d = CompletableDeferred<Unit>()
            closed = d
            activity.startActivity(Intent(activity, DouyinLoginActivity::class.java))
            return d
        }
    }
}
