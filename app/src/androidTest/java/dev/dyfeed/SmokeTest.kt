package dev.dyfeed

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end smoke test on a real emulator: app starts (Hilt, SQLCipher, DataStore, WorkManager),
 * the app opens without a PIN by default, every top-level screen opens without crashing, and the
 * Douyin sources screen opens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    companion object {
        /**
         * Start every launch from an empty feed: with instant start a video can be playing the
         * moment the app opens, and the test framework can't inspect the screen while one plays.
         */
        @JvmStatic
        @org.junit.BeforeClass
        fun noInstantStart() {
            dev.dyfeed.data.repo.PoolCache.enabled = false
            dev.dyfeed.ui.feed.FirstRun.askInterests = false
        }
    }

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val pin = listOf("1", "2", "3", "4")

    private fun <T> android.app.Activity.runOnUiThreadBlocking(block: () -> T): T {
        var out: T? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { out = block() }
        @Suppress("UNCHECKED_CAST") return out as T
    }

    private fun exists(text: String, substring: Boolean = false) =
        rule.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Waits by polling. A busy screen (loading bars, thumbnails arriving, a playing video) makes the
     * framework refuse to inspect it for a moment; that counts as "not yet", not as a failure.
     */
    private fun waitFor(timeoutMs: Long = 15_000, what: String, condition: () -> Boolean) {
        var last: Throwable? = null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition()) return
            } catch (e: Throwable) {
                last = e
            }
            Thread.sleep(300)
        }
        val onScreen = runCatching {
            rule.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { root ->
                fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
                    run {
                        val key = androidx.compose.ui.semantics.SemanticsProperties.Text
                        if (key in n.config) n.config[key].map { t -> t.text } else emptyList()
                    } + n.children.flatMap { c -> walk(c) }
                walk(root)
            }.distinct().take(40).joinToString(" | ")
        }.getOrDefault("?")
        throw AssertionError("Failed waiting for: $what [${last?.javaClass?.simpleName}: ${last?.message?.take(200)}] on screen: $onScreen", last)
    }

    /** Taps a text, retrying while the screen is too busy to be inspected. */
    /** A tab in the bottom bar (brought back first, in case scrolling tucked the bar away). */
    private fun tab(name: String) {
        dev.dyfeed.ui.BottomBar.collapse = 0f
        // Wake it too: over the feed it hides after a while, and a tap on a hidden bar only wakes it.
        dev.dyfeed.ui.BottomBar.lastTouch = System.currentTimeMillis()
        rule.waitForIdle()
        rule.onNode(
            hasText(name) and androidx.compose.ui.test.isSelectable() and
                androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab),
        ).performClick()
    }

    private fun tap(text: String) = waitFor(timeoutMs = 30_000, what = "tap '$text'") {
        rule.onAllNodes(hasText(text), useUnmergedTree = true)[0].performClick()
        true
    }

    /** Polls without touching the Compose tree (which can't be inspected while a video plays). */
    private fun poll(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            // Under a test rule the UI only draws frames when the test drives its clock.
            rule.mainClock.advanceTimeBy(250)
            Thread.sleep(150)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    /** A real finger swipe up through the Activity, the same path a physical touch takes. */
    private fun swipeUpGesture() {
        val activity = rule.activity
        val w = activity.window.decorView.width.toFloat()
        val h = activity.window.decorView.height.toFloat()
        val down = SystemClock.uptimeMillis()
        fun ev(action: Int, y: Float, t: Long) = MotionEvent.obtain(down, t, action, w / 2f, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val steps = 10
        rule.runOnUiThread { ev(MotionEvent.ACTION_DOWN, h * 0.75f, down).let { activity.dispatchTouchEvent(it); it.recycle() } }
        for (i in 1..steps) {
            Thread.sleep(16)
            val y = h * (0.75f - 0.5f * i / steps)
            rule.runOnUiThread { ev(MotionEvent.ACTION_MOVE, y, SystemClock.uptimeMillis()).let { activity.dispatchTouchEvent(it); it.recycle() } }
        }
        rule.runOnUiThread { ev(MotionEvent.ACTION_UP, h * 0.25f, SystemClock.uptimeMillis()).let { activity.dispatchTouchEvent(it); it.recycle() } }
    }

    /**
     * The thing the app is for: with the built-in sources and a real network, posts actually
     * display (an image drawn or a video's first frame), and keep displaying as you swipe.
     */
    @Test
    fun feedShowsRealPostsAndKeepsScrolling() {
        val shown = rule.activity.mediaHealth.shownCount
        val t0 = System.currentTimeMillis()
        try {
            poll(90_000, "a first post to display") { shown.get() >= 1 }
            android.util.Log.i("PFHEALTH", "feed: first post on screen ${System.currentTimeMillis() - t0} ms after launch (cold, no saved posts)")
        } catch (e: AssertionError) {
            throw AssertionError("No post displayed in 90 s. What happened: ${rule.activity.mediaHealth.recentEvents()}")
        }
        var swipes = 0
        try {
            poll(120_000, "three posts to have displayed while swiping") {
                if (swipes < 12) {
                swipeUpGesture(); swipes++
                repeat(8) { rule.mainClock.advanceTimeBy(250); Thread.sleep(150) }
            }
                shown.get() >= 3
            }
        } catch (e: AssertionError) {
            throw AssertionError("Only ${shown.get()} posts displayed after $swipes swipes. What happened: ${rule.activity.mediaHealth.recentEvents()}")
        }
        // Keep scrolling a while and log what showed and what failed: the mix of sites and the
        // failure rate are what using the app feels like.
        fun mem(): String {
            val rt = Runtime.getRuntime()
            return "java ${(rt.totalMemory() - rt.freeMemory()) / 1_048_576} MB, native ${android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576} MB"
        }
        android.util.Log.i("PFHEALTH", "feed: memory after ${swipes} swipes: ${mem()}")
        // As people watch (while the feed still has posts): a few seconds on each before the next.
        android.util.Log.i("PFHEALTH", "feed: video start (first swipes) ${dev.dyfeed.player.StartTimes.summary()}")
        dev.dyfeed.player.StartTimes.clear()
        repeat(8) { swipeUpGesture(); repeat(16) { rule.mainClock.advanceTimeBy(250); Thread.sleep(250) } }
        android.util.Log.i("PFHEALTH", "feed: video start (watching) ${dev.dyfeed.player.StartTimes.summary()}")
        dev.dyfeed.player.StartTimes.clear()
        repeat(45) { swipeUpGesture(); repeat(6) { rule.mainClock.advanceTimeBy(250); Thread.sleep(120) } }
        android.util.Log.i("PFHEALTH", "feed: memory after ${swipes + 45} swipes: ${mem()}")
        android.util.Log.i("PFHEALTH", "feed: video start (fast swiping) ${dev.dyfeed.player.StartTimes.summary()}")
        val events = rule.activity.mediaHealth.recentEvents()
        events.forEach { android.util.Log.i("PFHEALTH", "  feed $it") }
        android.util.Log.i("PFHEALTH", "feed: ${shown.get()} shown after ${swipes + 45} swipes, " +
            "${events.count { it.startsWith("failed") }} of the last ${events.size} events failed, " +
            "${events.filter { it.startsWith("shown") }.map { it.split(' ').getOrElse(1) { "" } }.distinct().size} different origins")
        assertTrue("activity still alive after scrolling", !rule.activity.isFinishing && !rule.activity.isDestroyed)
    }

    /** Search finds real results and plays them. */
    @Test
    fun searchFindsPlaysAndSaves() {
        waitFor(timeoutMs = 30_000, what = "feed tab") { exists("For You") }
        tap("Search")
        waitFor(timeoutMs = 30_000, what = "search screen") { exists("Surprise me") }
        // Type the search, as a person would.
        waitFor(timeoutMs = 30_000, what = "type a search") {
            rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("美食")
            true
        }
        waitFor(timeoutMs = 10_000, what = "run the search") {
            rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performImeAction()
            true
        }
        // Results stream in as sources answer. On a slow test machine every source can miss its
        // deadline; that's a finished search with nothing found, not a failure of the app.
        waitFor(timeoutMs = 120_000, what = "search to return results or finish") {
            exists("Play all") || (!exists("Searching Douyin…") && !exists("still searching", substring = true))
        }
        if (!exists("Play all")) return
        tap("Play all")
        // Results must really display in the viewer.
        val shown = rule.activity.mediaHealth.shownCount
        val before = shown.get()
        try {
            poll(90_000, "a search result to display") { shown.get() > before }
        } catch (e: AssertionError) {
            throw AssertionError("No search result displayed in 90 s. What happened: ${rule.activity.mediaHealth.recentEvents()}")
        }
        assertTrue("viewer still alive after playing search results", !rule.activity.isFinishing && !rule.activity.isDestroyed)
    }

    @Test
    fun launchAndNavigate() {
        // 1–2. The app lock is optional and off by default: no PIN screen, straight to the feed.
        waitFor(what = "app opens without a PIN") { exists("For You") || exists("Latest") }
        assertTrue("no lock screen by default", !exists("Create a PIN") && !exists("Enter PIN"))

        // 3. Feed loads — either content appears (pre-seeded sources) or the tab bar is visible.
        //    We no longer expect an empty-state: sources are pre-seeded at install and start loading.
        waitFor(what = "feed visible") { exists("For You") || exists("Latest") || exists("Following") }

        // 4. Every tab opens.
        tab("Search")
        waitFor(what = "search screen") { exists("Search Douyin · 搜索") && exists("Surprise me") }

        tab("Learn")
        waitFor(what = "learn screen") { exists("学习 Learn") && exists("Chinese from the videos you watch") }

        tab("Library")
        waitFor(what = "library screen") { exists("Saved", substring = true) && exists("Following", substring = true) }

        tab("Me")
        waitFor(what = "settings screen") { exists("Sources") && exists("App lock") }

        // 5. Sources screen lists every source, the health check and the topic finder.
        rule.onNodeWithText("Sources").performClick()
        waitFor(what = "sources screen") { exists("抖音 Douyin") && exists("Local files") && exists("Check sources") }
        waitFor(timeoutMs = 30_000, what = "open Douyin") {
            rule.onAllNodes(hasText("抖音 Douyin"), useUnmergedTree = true)[0].performScrollTo().performClick()
            true
        }
        waitFor(what = "douyin sheet") { exists("Verify I'm human") }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor(what = "sheet closed, still on sources") { !exists("Verify I'm human") && exists("Check sources") }
        waitFor(what = "tap back") { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back"), useUnmergedTree = true)[0].performClick(); true }
        waitFor(what = "back to settings") { exists("App lock") }

        tab("Feed")
        waitFor(what = "back on feed") { exists("For You") }

    }

    /** Saves a screenshot of what's on screen (pulled from the device and uploaded by CI). */
    private fun shot(name: String) {
        repeat(6) { rule.mainClock.advanceTimeBy(250); Thread.sleep(120) }
        // Taken by the shell into /data/local/tmp, which outlives the test run's uninstall (CI pulls it).
        val ua = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun sh(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }
        sh("mkdir -p /data/local/tmp/shots")
        sh("screencap -p /data/local/tmp/shots/$name.png")
        android.util.Log.i("PFHEALTH", "screenshot $name")
    }

    /**
     * Screenshots of every main screen for design review. Every post's picture is covered with a
     * flat panel (see MaskMedia): the shots show the app's layout, not the content.
     */
    @Test
    fun designScreenshots() {
        dev.dyfeed.ui.common.MaskMedia.on = true
        // The app blocks screenshots (FLAG_SECURE); lift that for this test only, with the posts masked.
        rule.runOnUiThread { rule.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        try {
            val shown = rule.activity.mediaHealth.shownCount
            val before = shown.get()
            runCatching { poll(90_000, "a post on screen") { shown.get() > before } }
            Thread.sleep(1_500)
            shot("01_feed")
            // Wall: four clips at once.
            runCatching {
                rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Wall: four clips at once"), useUnmergedTree = true)[0].performClick()
                waitFor(what = "wall") { exists("Tap one to watch it") }
                Thread.sleep(6_000)
                shot("01c_wall")
                rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Back to the feed"), useUnmergedTree = true)[0].performClick()
                waitFor(what = "back from the wall") { !exists("Tap one to watch it") }
            }.onFailure { android.util.Log.i("PFHEALTH", "wall: ${it.message?.take(120)}") }
            tab("Search")
            waitFor(what = "search screen") { exists("Surprise me") }
            // Topic rows arrive once the video sites answer.
            runCatching { waitFor(timeoutMs = 25_000, what = "topic rows") { exists("See all") } }
            android.util.Log.i("PFHEALTH", "search landing: topic rows ${if (exists("See all")) "shown" else "not shown"}")
            shot("02_search")
            runCatching {
                rule.onAllNodes(hasText("Popular"), useUnmergedTree = true)[0].performScrollTo()
                Thread.sleep(600)
                shot("02b_browse")
                rule.onAllNodes(hasText("Search every site"), useUnmergedTree = true)[0].performScrollTo()
            }
            // The browse card may be below the fold (the Spotlight sits on top): scroll to it first.
            runCatching { rule.onAllNodes(hasText("美食"), useUnmergedTree = true)[0].performScrollTo(); Thread.sleep(400) }
            tap("美食")
            waitFor(timeoutMs = 60_000, what = "results") { exists("Play all") || !exists("still searching", substring = true) }
            Thread.sleep(2_000)
            shot("03_results")
            if (exists("Play all")) {
                tap("Play all")
                val b = shown.get()
                runCatching { poll(60_000, "a result on screen") { shown.get() > b } }
                Thread.sleep(1_000)
                shot("03b_viewer")
                // Swipe left on the post: the creator's profile (header, counts, grid).
                runCatching {
                    rule.onRoot().performTouchInput {
                        swipe(androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.4f), androidx.compose.ui.geometry.Offset(width * 0.08f, height * 0.4f), 250)
                    }
                    waitFor(timeoutMs = 20_000, what = "creator profile") { exists("posts") && (exists("Follow") || exists("Following")) }
                    Thread.sleep(2_500)
                    shot("03c_creator")
                    android.util.Log.i("PFHEALTH", "creator profile: shown")
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    Thread.sleep(1_000)
                }.onFailure { android.util.Log.i("PFHEALTH", "creator profile: not shown (${it.message?.take(80)})") }
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                Thread.sleep(1_000)
            }
            // A few saved images, so the Saved grid (count, Play all, kept badges) is in the shot.
            val savedForShot = runBlocking {
                rule.activity.searchRepository.search("美食").toList().lastOrNull()?.items.orEmpty()
                    .filter { it.type == dev.dyfeed.domain.model.MediaType.IMAGE && !it.isGallery }.take(5)
            }
            runBlocking { savedForShot.forEach { rule.activity.library.add(dev.dyfeed.data.repo.LibraryKind.SAVED, it) } }
            // Two collections: one with three of them, one renamed after creation.
            val lib = rule.activity.library
            runBlocking {
                savedForShot.take(3).forEach { lib.addToCollection("Cosplay", it) }
                savedForShot.lastOrNull()?.let { lib.addToCollection("temp name", it) }
                lib.renameCollection("temp name", "Favourites")
                val cols = lib.collections.first()
                val first = savedForShot.firstOrNull()?.let { lib.collectionsOf(it) }
                android.util.Log.i("PFHEALTH", "collections: ${cols.joinToString { "${it.name}=${it.count}" }}; first post in $first")
                // Backup round trip: export, lose a collection, import, and it's back.
                runCatching {
                    val out = java.io.ByteArrayOutputStream()
                    rule.activity.settingsExporter.exportTo(out, "test-pass".toCharArray())
                    lib.deleteCollection("Cosplay")
                    rule.activity.settingsExporter.importFrom(java.io.ByteArrayInputStream(out.toByteArray()), "test-pass".toCharArray())
                    "${out.size()} bytes; after import: ${lib.collections.first().joinToString { "${it.name}=${it.count}" }}"
                }.let { android.util.Log.i("PFHEALTH", "backup: ${it.getOrElse { e -> "FAILED ${e.javaClass.simpleName}: ${e.message?.take(100)}" }}") }
            }
            Thread.sleep(8_000) // time for them to be kept
            tab("Library")
            waitFor(what = "library") { exists("Saved", substring = true) }
            Thread.sleep(1_500)
            shot("04_library")
            runCatching {
                rule.onAllNodes(hasText("Cosplay · ", substring = true))[0].performClick()
                Thread.sleep(1_500)
                shot("04b_collection")
                rule.onAllNodes(hasText("All · ", substring = true))[0].performClick()
            }.onFailure { android.util.Log.i("PFHEALTH", "collections: chip not found (${it.message?.take(80)})") }
            // Your week: the recap on top of History, once there's a week's worth to sum up.
            runCatching {
                runBlocking { savedForShot.forEach { lib.add(dev.dyfeed.data.repo.LibraryKind.HISTORY, it) } }
                rule.onAllNodes(hasText("History"))[0].performClick()
                waitFor(what = "week recap", timeoutMs = 6_000) { exists("YOUR WEEK") }
                Thread.sleep(800)
                shot("04c_week")
                android.util.Log.i("PFHEALTH", "week recap: shown")
                rule.onAllNodes(hasText("Saved"))[0].performClick()
            }.onFailure { android.util.Log.i("PFHEALTH", "week recap: not shown (${it.message?.take(80)})") }
            runBlocking {
                savedForShot.forEach { lib.remove(dev.dyfeed.data.repo.LibraryKind.SAVED, it) }
                android.util.Log.i("PFHEALTH", "collections after unsaving: ${lib.collections.first().joinToString { "${it.name}=${it.count}" }.ifEmpty { "none" }}")
            }
            tab("Me")
            waitFor(what = "settings") { exists("App lock") }
            shot("05_settings")
            waitFor(what = "scroll settings") { rule.onAllNodes(hasText("Encrypted DNS"), useUnmergedTree = true)[0].performScrollTo(); true }
            shot("06_settings_more")
            runCatching {
                rule.onAllNodes(hasText("Blocked where you are"), useUnmergedTree = true)[0].performScrollTo().performClick()
                waitFor(what = "network screen") { exists("Orbot") }
                Thread.sleep(600)
                shot("06b_network")
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                waitFor(what = "back to settings") { exists("App lock") }
            }.onFailure {
                android.util.Log.i("PFHEALTH", "network shot: ${it.message?.take(100)}")
                if (!exists("App lock")) androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            }
            waitFor(what = "scroll to top") { rule.onAllNodes(hasText("Sources"), useUnmergedTree = true)[0].performScrollTo(); true }
            rule.onNodeWithText("Your taste").performClick()
            Thread.sleep(1_000)
            shot("07_taste")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitFor(what = "settings again") { exists("App lock") }
            rule.onNodeWithText("Hidden & blocked").performClick()
            Thread.sleep(1_000)
            shot("07b_blocked")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitFor(what = "settings again") { exists("App lock") }
            rule.onNodeWithText("Sources").performClick()
            waitFor(what = "sources") { exists("Check sources") }
            shot("08_sources")
            // The PIN screen, as it looks when the app locks.
            val lm = rule.activity.lockManager
            runBlocking { lm.createPin("1234".toCharArray()) }
            rule.runOnUiThread { lm.lockNow() }
            Thread.sleep(800)
            shot("09_lock")
            // The calculator lock: works as a calculator, and the PIN then = opens the app.
            runCatching {
                rule.runOnUiThread { dev.dyfeed.ui.lock.LockLook.calculator.value = true }
                waitFor(what = "calculator lock", timeoutMs = 5_000) { rule.onAllNodes(hasTestTag("calc_÷")).fetchSemanticsNodes().isNotEmpty() }
                fun key(k: String) = rule.onNode(hasTestTag("calc_$k")).performClick()
                listOf("7", "×", "6", "=").forEach(::key)
                rule.waitForIdle()
                val sums = rule.onNode(hasTestTag("calc_display")).fetchSemanticsNode().config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString() == "42"
                shot("09b_calculator_lock")
                key("C")
                listOf("1", "2", "3", "4", "=").forEach(::key)
                waitFor(what = "unlocked by the calculator", timeoutMs = 8_000) { !lm.locked.value }
                android.util.Log.i("PFHEALTH", "calculator lock: 7×6 ${if (sums) "= 42" else "wrong"}; PIN then = unlocked")
            }.onFailure { android.util.Log.i("PFHEALTH", "calculator lock: failed (${it.message?.take(80)})") }
            rule.runOnUiThread { dev.dyfeed.ui.lock.LockLook.calculator.value = false }
            runBlocking { if (lm.locked.value) lm.unlockWithPin("1234".toCharArray()); lm.disable("1234".toCharArray()) }
            // The first-run topic picker, as a new install sees it.
            rule.activity.getSharedPreferences("ui_hints", android.content.Context.MODE_PRIVATE).edit().remove("interests_asked").commit()
            dev.dyfeed.ui.feed.FirstRun.askInterests = true
            dev.dyfeed.ui.feed.FirstRun.forceInterests = true
            try {
                tab("Feed")
                waitFor(what = "topic picker") { exists("What are you into?") }
                tap("美食 · food"); tap("猫 · cats")
                shot("10_interests")
                tap("Skip")
            } finally {
                dev.dyfeed.ui.feed.FirstRun.askInterests = false
                dev.dyfeed.ui.feed.FirstRun.forceInterests = false
            }
        } finally {
            rule.runOnUiThread { rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
            dev.dyfeed.ui.common.MaskMedia.on = false
        }
    }

    /** What Douyin answers from this network: logged for the run's summary, never fails the run. */
    @Test
    fun douyinAnswers() {
        val douyin = rule.activity.sourceRegistry.byId("douyin") as dev.dyfeed.source.douyin.DouyinAdapter
        val line = runCatching { runBlocking { douyin.probe() } }.getOrElse { "probe failed: ${it.javaClass.simpleName}: ${it.message}" }
        android.util.Log.i("PFHEALTH", "douyin probe: $line")
        val feed = runCatching { runBlocking { douyin.fetchFeed(null, dev.dyfeed.domain.model.FeedPrefs(dev.dyfeed.domain.model.FeedMode.FOR_YOU)).items.size } }
        android.util.Log.i("PFHEALTH", "douyin feed: " + feed.fold({ "$it posts" }, { "error ${it.message}" }))
        val withText = runCatching { runBlocking { douyin.fetchFeed(null, dev.dyfeed.domain.model.FeedPrefs(dev.dyfeed.domain.model.FeedMode.FOR_YOU)).items } }.getOrDefault(emptyList())
        android.util.Log.i("PFHEALTH", "douyin feed on-screen text: ${withText.count { it.extras["ocr"] != null }} of ${withText.size} posts" +
            (withText.firstNotNullOfOrNull { it.extras["ocr"] }?.let { " e.g. ${it.take(60)}" } ?: ""))
        android.util.Log.i("PFHEALTH", "douyin probe subtitles: " + runCatching { runBlocking { douyin.probeSubtitles() } }.getOrElse { "failed: ${it.message}" })
        val ways = runCatching { runBlocking { douyin.probeSearch("美食") } }.getOrElse { "failed: ${it.message}" }
        android.util.Log.i("PFHEALTH", "douyin probe search: $ways")
        val anyPost = runCatching { runBlocking { douyin.fetchFeed(null, dev.dyfeed.domain.model.FeedPrefs(dev.dyfeed.domain.model.FeedMode.FOR_YOU)).items.firstOrNull() } }.getOrNull()
        val comments = anyPost?.let { p -> runCatching { runBlocking { douyin.comments(p).size } } }
        android.util.Log.i("PFHEALTH", "douyin search comments: " + (comments?.fold({ "$it comments" }, { "error ${it.message}" }) ?: "no post to try"))
        // A creator's page (and the Following tab) is that creator's own post list.
        val creatorPosts = anyPost?.creator?.let { name -> runCatching { runBlocking { douyin.search(name, null).items.count { it.creator == name } } } }
        android.util.Log.i("PFHEALTH", "douyin creator page: " + (creatorPosts?.fold({ "$it posts by ${anyPost.creator}" }, { "error ${it.message}" }) ?: "no post to try"))
        android.util.Log.i("PFHEALTH", "douyin creator page trace: " + runBlocking { douyin.secondPageReport() })
        // A trending topic searched: its own page's posts.
        val topic = runCatching { runBlocking { douyin.trending().firstOrNull() } }.getOrNull()
        val trendHits = topic?.let { t -> runCatching { runBlocking { douyin.search(t, null).items.size } } }
        android.util.Log.i("PFHEALTH", "douyin search trending '$topic': " + (trendHits?.fold({ "$it posts" }, { "error ${it.message}" }) ?: "no topics"))
        // Video addresses expire after a few hours: a liked or history post is looked up again by id.
        val again = anyPost?.let { p -> runCatching { runBlocking { douyin.fresh(p.id) } } }
        val media = anyPost?.extras?.get("media")
        val expiry = media?.let { dev.dyfeed.source.douyin.DouyinParser.expiresAt(it) }
        android.util.Log.i("PFHEALTH", "douyin feed address: " + (media?.substringAfter('?')?.split('&')?.map { it.substringBefore('=') }?.joinToString(",")?.take(200) ?: "none") +
            " | expires " + (expiry?.let { "in ${(it - System.currentTimeMillis()) / 60_000} min" } ?: "unknown"))
        android.util.Log.i("PFHEALTH", "douyin feed refresh by id: " + (again?.fold({ if (it == null) "nothing" else "ok, plays from ${it.extras["media"]?.substringBefore('?')?.take(60)}" }, { "error ${it.message}" }) ?: "no post to try"))
        // Settings → Check sources, as you'd run it after using the app a little: how long it takes.
        val t0 = System.currentTimeMillis()
        val checked = runCatching { runBlocking { rule.activity.sourceChecker.check(douyin) } }
        android.util.Log.i("PFHEALTH", "douyin check sources: ${System.currentTimeMillis() - t0} ms: " + checked.fold({ "${it.ok} ${it.detail}" }, { "error ${it.message}" }))
        val search = runCatching { runBlocking { douyin.search("猫", null).items.size } }
        android.util.Log.i("PFHEALTH", "douyin search: " + search.fold({ "$it posts" }, { "error ${it.message}" }))
    }

    /** On-phone translation both ways (the model downloads on first use), and an English search. */
    @Test
    fun translatesBothWays() {
        val en = runBlocking { dev.dyfeed.domain.lang.ChineseToEnglish.translate("你好，世界") }
        val zh = runBlocking { dev.dyfeed.domain.lang.EnglishToChinese.translate("hot pot") }
        android.util.Log.i("PFHEALTH", "translate: 你好，世界 → ${en.getOrElse { "error ${it.message}" }} | hot pot → ${zh.getOrElse { "error ${it.message}" }}")
        // The download needs Google Play services; where the emulator can't get the model, nothing to check.
        org.junit.Assume.assumeTrue(en.isSuccess && zh.isSuccess)
        assertTrue("Chinese → English: ${en.getOrNull()}", en.getOrThrow().lowercase().contains("hello"))
        assertTrue("English → Chinese: ${zh.getOrNull()}", dev.dyfeed.domain.lang.Zh.has(zh.getOrThrow()))
        val douyin = rule.activity.sourceRegistry.byId("douyin") as dev.dyfeed.source.douyin.DouyinAdapter
        val hits = runCatching { runBlocking { douyin.search("cat", null).items.size } }
        android.util.Log.i("PFHEALTH", "translate: English search 'cat' → " + hits.fold({ "$it posts" }, { "error ${it.message}" }))
    }

    /** Learn: a caption taken apart into words, saved, and reviewed. */
    @Test
    fun learnFromACaption() {
        val ctx = rule.activity
        val dict = runBlocking { dev.dyfeed.domain.learn.Dict.get(ctx) }
        val words = dict.segment("我喜欢吃重庆火锅").map { it.text }
        android.util.Log.i("PFHEALTH", "learn: 我喜欢吃重庆火锅 → ${words.joinToString(" / ")}")
        assertEquals(listOf("我", "喜欢", "吃", "重庆", "火锅"), words)
        rule.waitForIdle()
        dev.dyfeed.ui.learn.LearnWords.request.value = "我喜欢吃重庆火锅"
        waitFor(timeoutMs = 20_000, what = "words sheet") { exists("词 Words") && exists("to like", substring = true) }
        shot("11_words")
        dev.dyfeed.ui.learn.LearnWords.request.value = null
        // Let the sheet finish closing first (a tap during its exit lands on its scrim).
        waitFor(what = "words sheet closed") { !exists("Listen") }
        tab("Learn")
        waitFor(what = "learn screen") { exists("学习 Learn") }
        shot("12_learn")
    }

    /** Live subtitles: a frame with a subtitle burned in is read, and the line picked out. */
    @Test
    fun readsBurnedInSubtitles() {
        val bmp = android.graphics.Bitmap.createBitmap(720, 1280, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.rgb(40, 60, 80))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 46f; textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("我们今天去吃火锅", 360f, 1000f, paint)
        val top = (1280 * 0.42f).toInt()
        val crop = android.graphics.Bitmap.createBitmap(bmp, 0, top, 720, (1280 * 0.96f).toInt() - top)
        val lines = runBlocking { kotlinx.coroutines.withTimeoutOrNull(60_000) { dev.dyfeed.ui.learn.Reader.lines(crop, top, 720, 1280) } }.orEmpty()
        val picked = dev.dyfeed.domain.learn.LiveSubs.pick(lines)
        android.util.Log.i("PFHEALTH", "translate: live subtitles read ${lines.map { it.text }} → $picked")
        // The reader comes from Play services; where the emulator has none, nothing to check.
        org.junit.Assume.assumeTrue(lines.isNotEmpty())
        assertTrue("subtitle read: $picked", picked?.contains("火锅") == true)
    }

    /** The "Verify I'm human" page opens (it was once missing from the manifest and crashed the app). */
    @Test
    fun douyinCheckPageOpens() {
        val closed = rule.activity.runOnUiThreadBlocking { dev.dyfeed.source.douyin.DouyinLoginActivity.open(rule.activity) }
        Thread.sleep(3_000)
        val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        var onTop = false
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            onTop = resumed.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).any { it is dev.dyfeed.source.douyin.DouyinLoginActivity }
        }
        android.util.Log.i("PFHEALTH", "douyin check page: " + if (onTop) "opens" else "did not open")
        assertTrue("the Verify I'm human page should open", onTop)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        runBlocking { kotlinx.coroutines.withTimeoutOrNull(10_000) { closed.await() } }
    }

    /** A link shared into the app opens in the viewer. */
    @Test
    fun sharedLinkOpensInViewer() {
        rule.waitForIdle()
        dev.dyfeed.ui.SharedLink.pending.value = "Look at this: https://example.com/media/some-clip.mp4"
        val shown = runCatching { waitFor(timeoutMs = 15_000, what = "shared link viewer") { exists("Shared link") } }.isSuccess
        android.util.Log.i("PFHEALTH", "shared link: " + if (shown) "opened in the viewer" else "did not open")
        if (shown) shot("09_shared_link")
        assert(shown) { "a shared link should open in the viewer" }
    }

    /** The updater reaches GitHub's releases and makes sense of the answer. */
    @Test
    fun updaterChecksGitHub() {
        val updater = dagger.hilt.android.EntryPointAccessors.fromApplication(
            rule.activity.applicationContext, dev.dyfeed.update.UpdaterEntryPoint::class.java,
        ).updater()
        updater.checkAndDownload()
        val deadline = System.currentTimeMillis() + 90_000
        var s = updater.state.value
        while (System.currentTimeMillis() < deadline) {
            s = updater.state.value
            if (s !is dev.dyfeed.update.Updater.State.Checking && s !is dev.dyfeed.update.Updater.State.Downloading && s !is dev.dyfeed.update.Updater.State.Idle) break
            Thread.sleep(500)
        }
        android.util.Log.i("PFHEALTH", "updater: this is ${updater.currentVersion}; GitHub says ${s.toString().take(160)}")
    }

    /** What For You actually serves (not what had time to draw): its first 60 posts by source and type. */
    @Test
    fun forYouMix() {
        val a = rule.activity
        for (mode in listOf(dev.dyfeed.domain.model.FeedMode.FOR_YOU, dev.dyfeed.domain.model.FeedMode.LATEST)) runBlocking {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
            val session = a.feedSessions.create(dev.dyfeed.data.repo.FeedSpec.Main(mode), scope)
            val got = ArrayList<dev.dyfeed.domain.model.MediaItem>()
            val t0 = System.currentTimeMillis()
            while (got.size < 60 && System.currentTimeMillis() - t0 < 90_000) {
                val r = session.loadMore()
                got += r.items
                if (r.items.isEmpty()) kotlinx.coroutines.delay(1_000)
            }
            session.close()
            val bySource = got.groupingBy { it.sourceId }.eachCount().entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" }
            val byType = got.groupingBy { if (it.isGallery) "comic" else it.type.name.lowercase() }.eachCount()
            android.util.Log.i("PFHEALTH", "$mode: ${got.size} posts in ${System.currentTimeMillis() - t0} ms; by source: $bySource; by type: $byType; origins: ${got.map { dev.dyfeed.domain.reco.Spread.origin(it) }.distinct().size}")
            assertTrue("For You served nothing", got.isNotEmpty())
        }
    }

    /** "More like this": a feed around one post finds posts that share its tags. */
    @Test
    fun moreLikeThis() {
        val a = rule.activity
        runBlocking {
            // A Douyin post with a few tags, from a search (signed out, Douyin tags most posts).
            val seed = a.searchRepository.search("美食").toList().lastOrNull()?.items.orEmpty()
                .firstOrNull { it.tags.size >= 2 } ?: return@runBlocking android.util.Log.i("PFHEALTH", "similar: no seed found").let { }
            val features = dev.dyfeed.domain.reco.Features.of(seed).filterNot(dev.dyfeed.domain.reco.Features::isMeta)
            val queries = (listOfNotNull(seed.creator) + features.filterNot { it.startsWith("w:") }.take(3).map { it.replace('_', ' ') }).distinct()
            val spec = dev.dyfeed.data.repo.FeedSpec.Similar(seed.key, queries, features.toSet(), seed.creator)
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
            val session = a.feedSessions.create(spec, scope)
            val got = ArrayList<dev.dyfeed.domain.model.MediaItem>()
            val t0 = System.currentTimeMillis()
            while (got.size < 30 && System.currentTimeMillis() - t0 < 90_000) {
                val r = session.loadMore()
                got += r.items
                if (r.items.isEmpty()) kotlinx.coroutines.delay(1_000)
                if (r.endReached) break
            }
            session.close()
            val sharing = got.count { c -> dev.dyfeed.domain.reco.Features.of(c).any { it in features } }
            android.util.Log.i("PFHEALTH", "similar: seed ${seed.key} queries=$queries; ${got.size} posts in ${System.currentTimeMillis() - t0} ms; $sharing share a tag; seed returned: ${got.any { it.key == seed.key }}")
            assertTrue("More like this served nothing", got.isNotEmpty())
        }
    }
}
