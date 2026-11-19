package dev.dyfeed

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import dev.dyfeed.security.AppLockManager
import dev.dyfeed.work.MaintenanceWorker
import okhttp3.OkHttpClient
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FeedApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var lockManager: AppLockManager
    @Inject lateinit var settings: dev.dyfeed.data.settings.SettingsRepository
    @Inject lateinit var offline: dev.dyfeed.data.repo.OfflineStore
    @Inject lateinit var updater: dev.dyfeed.update.Updater
    @Inject lateinit var tasteEngine: dev.dyfeed.data.repo.TasteEngineStore
    @Inject lateinit var douyinWeb: dev.dyfeed.source.douyin.DouyinWeb

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Volatile private var dataSaver = false
    @Volatile private var bestQuality = false

    /** 480p with Data saver on, or while the battery is low (battery saver); 1080p otherwise. */
    private fun applyQuality() {
        val height = if (dataSaver || dev.dyfeed.player.Battery.saving) 480 else if (bestQuality) 2160 else 1080
        dev.dyfeed.player.PlayerPool.maxHeight = height
    }

    private var readyScheduled: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        dev.dyfeed.player.ResumePoints.init(this)
        dev.dyfeed.player.ComicPages.init(this)
        dev.dyfeed.source.net.GeoBlock.init(this)
        dev.dyfeed.source.douyin.CreatorAvatars.init(this)
        CrashLog.install(this)
        lockManager.attach()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            settings.flow.collect {
                dev.dyfeed.ui.theme.Palette.set(it.accent)
                dev.dyfeed.player.SmartVolume.enabled = it.smartVolume
                dev.dyfeed.domain.privacy.Incognito.always = it.privateMode
                dev.dyfeed.ui.lock.LockLook.calculator.value = it.calculatorLock
                dev.dyfeed.ui.feed.VolumeKeys.turnPages = it.volumeKeysTurnPages
                dev.dyfeed.source.net.EncryptedDns.always = it.encryptedDns
                dev.dyfeed.source.net.AppProxy.configure(proxyConfig(it))
                dataSaver = it.dataSaver
                dev.dyfeed.data.repo.OfflineStore.dataSaver = it.dataSaver
                bestQuality = it.bestQuality
                dev.dyfeed.player.Battery.saverOn = it.batterySaver
                if (it.readyAhead != readyScheduled) { readyScheduled = it.readyAhead; dev.dyfeed.work.ReadyWorker.schedule(this@FeedApp, it.readyAhead) }
                applyQuality()
            }
        }
        dev.dyfeed.player.Battery.init(this) { applyQuality() }
        // Updates from GitHub: look as the app comes up; a downloaded one that can go in without a
        // prompt is installed once you've left the app, so it never closes in front of you.
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                updater.auto()
                // Open Douyin's page out of sight now, so it's ready by the time the feed asks
                // (it takes ~10-15 s to load and set up its signer).
                kotlinx.coroutines.MainScope().launch { runCatching { douyinWeb.warmUp() } }
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                tasteEngine.flush()
                dev.dyfeed.data.repo.DataMeter.month()
                updater.install(quiet = true)
            }
        })
        dev.dyfeed.data.repo.DataMeter.init(this)
        MaintenanceWorker.schedule(this)
        dev.dyfeed.work.UpdateWorker.schedule(this)
        offline.start()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttpClient)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            add(VideoFrameDecoder.Factory())
            add(MaxSizeInterceptor)
        }
        .crossfade(true)
        .build()
}

/** Settings → Blocked where you are, as the network layer wants it. */
internal fun proxyConfig(s: dev.dyfeed.data.settings.AppSettings): dev.dyfeed.source.net.AppProxy.Config = when (s.proxyType) {
    "http" -> dev.dyfeed.source.net.AppProxy.Config(java.net.Proxy.Type.HTTP, s.proxyHost.trim(), s.proxyPort, s.proxyUser, s.proxyPassword, s.proxyEverything)
    "socks" -> dev.dyfeed.source.net.AppProxy.Config(java.net.Proxy.Type.SOCKS, s.proxyHost.trim(), s.proxyPort, s.proxyUser, s.proxyPassword, s.proxyEverything)
    "orbot" -> dev.dyfeed.source.net.AppProxy.Config(java.net.Proxy.Type.SOCKS, "127.0.0.1", 9050, everything = s.proxyEverything)
    else -> dev.dyfeed.source.net.AppProxy.Config()
}

/**
 * Every image, however it's requested (feed, thumbnails, warming the cache), is asked for at most
 * 1280 × 4096 pixels. Decoding may come out up to twice that each way (it samples by powers of two),
 * so the largest possible bitmap stays near 80 MB, under the ~100 MB Android will draw. A huge
 * original asked for at 2048 × 8192 came out at 140 MB and crashed the feed.
 */
private object MaxSizeInterceptor : coil.intercept.Interceptor {
    private const val MAX_W = 1280
    private const val MAX_H = 4096

    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val s = chain.size
        fun cap(d: coil.size.Dimension, max: Int): coil.size.Dimension = when (d) {
            is coil.size.Dimension.Pixels -> coil.size.Dimension(minOf(d.px, max))
            else -> coil.size.Dimension(max)
        }
        val capped = coil.size.Size(cap(s.width, MAX_W), cap(s.height, MAX_H))
        return if (capped == s) chain.proceed(chain.request) else chain.withSize(capped).proceed(chain.request)
    }
}
