package dev.dyfeed.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dyfeed.BuildConfig
import dev.dyfeed.data.db.AppDatabase
import dev.dyfeed.data.db.AffinityDao
import dev.dyfeed.data.db.BlockDao
import dev.dyfeed.data.db.CredentialDao
import dev.dyfeed.data.db.FollowDao
import dev.dyfeed.data.db.ItemDao
import dev.dyfeed.data.db.LibraryDao
import dev.dyfeed.data.db.SignalDao
import dev.dyfeed.security.SecretStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.math.max

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        // OkHttp's default is 5 concurrent requests per host. Video streaming, video preloads and
        // API calls to the same host (RedGIFs, Reddit, a board's CDN) all share this client, so the
        // default made preloads and feed requests queue behind playback.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 96; maxRequestsPerHost = 16 })
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        // Looks names up again over encrypted DNS when the network blocks or sinkholes them.
        .dns(dev.dyfeed.source.net.EncryptedDns)
        // Your proxy, for sites that block your state or country (direct when none is set).
        .proxySelector(dev.dyfeed.source.net.AppProxy)
        .proxyAuthenticator { _, response ->
            val c = dev.dyfeed.source.net.AppProxy.config
            if (c.user.isBlank() || response.request.header("Proxy-Authorization") != null) null
            else response.request.newBuilder().header("Proxy-Authorization", okhttp3.Credentials.basic(c.user, c.password)).build()
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // Sites that only answer browser-looking requests (none listed at the moment).
        .addInterceptor { chain ->
            val request = chain.request()
            val site = ArchiveHeaders.siteFor(request.url.host)
            if (site == null) chain.proceed(request)
            else chain.proceed(
                request.newBuilder()
                    .header("User-Agent", ArchiveHeaders.BROWSER_UA)
                    .apply {
                        if (request.header("Referer") == null) header("Referer", ArchiveHeaders.refererFor(site))
                        // Sites that put an "are you 18?" page in front of everything until this is set.
                        ArchiveHeaders.cookiesFor(site)?.let { if (request.header("Cookie") == null) header("Cookie", it) }
                    }
                    .build(),
            )
        }
        // Default User-Agent for everything else
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent") != null) chain.proceed(request)
            else if (HotlinkReferer.siteFor(request.url.host) == "www.douyin.com")
                chain.proceed(request.newBuilder().header("User-Agent", dev.dyfeed.source.douyin.DouyinWeb.DESKTOP_UA).build())
            else chain.proceed(request.newBuilder().header("User-Agent", "PrivateFeed/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        // gelbooru.com and realbooru.com only serve their images and videos to requests that name
        // the site as the referring page (hotlink protection); without it the file URL returns an
        // HTML page, so nothing from those boards ever displayed. The app does get these links from
        // the site's own pages, so say so. Covers thumbnails (Coil), playback (ExoPlayer), preloads.
        .addInterceptor { chain ->
            val request = chain.request()
            val site = HotlinkReferer.siteFor(request.url.host)
            if (site == null || request.header("Referer") != null) chain.proceed(request)
            else chain.proceed(request.newBuilder().header("Referer", "https://$site/").build())
        }
        // Notice sites that refuse this connection because of where it is (state age-verification
        // laws, country blocks): they're routed through your proxy, if you've set one, and named in Settings.
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            dev.dyfeed.source.net.GeoBlock.inspect(request.url.host, response, dev.dyfeed.source.net.AppProxy.routes(request.url.host))
            response
        }
        // 429 / 503: one quick retry, and only when the server asks for a short wait. Longer
        // back-off is handled per site by the sources themselves; sleeping here for up to 20 s held
        // a network thread (and whatever was waiting on it, including video) for nothing.
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
            if ((response.code == 429 || response.code == 503) && retryAfterSec != null && retryAfterSec <= 2) {
                response.close()
                Thread.sleep(max(retryAfterSec * 1_000L, 500L))
                chain.proceed(chain.request())
            } else {
                response
            }
        }
        .build()
        .also { client ->
            val evict = { client.connectionPool.evictAll() }
            dev.dyfeed.source.net.AppProxy.onChange = evict
            dev.dyfeed.source.net.GeoBlock.onNewBlock = { if (dev.dyfeed.source.net.AppProxy.config.active) evict() }
        }

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, secrets: SecretStore): AppDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, AppDatabase::class.java, "pfeed.db")
            .openHelperFactory(SupportOpenHelperFactory(secrets.databasePassphrase()))
            .build()
    }

    @Provides fun itemDao(db: AppDatabase): ItemDao = db.itemDao()
    @Provides fun libraryDao(db: AppDatabase): LibraryDao = db.libraryDao()
    @Provides fun signalDao(db: AppDatabase): SignalDao = db.signalDao()
    @Provides fun affinityDao(db: AppDatabase): AffinityDao = db.affinityDao()
    @Provides fun followDao(db: AppDatabase): FollowDao = db.followDao()
    @Provides fun blockDao(db: AppDatabase): BlockDao = db.blockDao()
    @Provides fun credentialDao(db: AppDatabase): CredentialDao = db.credentialDao()
}

/** Boards whose file hosts require the board itself as the referring page. */
object HotlinkReferer {
    private val SITES = emptyList<String>()

    /** Douyin's video and picture servers expect the site as the referring page. */
    private val DOUYIN_MEDIA = listOf("douyinvod.com", "douyinpic.com", "douyincdn.com", "zjcdn.com", "douyin.com")

    fun siteFor(host: String): String? =
        if (DOUYIN_MEDIA.any { host == it || host.endsWith(".$it") }) "www.douyin.com"
        else SITES.firstOrNull { host == it || host.endsWith(".$it") }
}

/**
 * Sites that only serve their pages, API and files to browser-looking requests, plus their CDN
 * domains. Empty for now; kept so a site can be added in one line.
 */
object ArchiveHeaders {
    /** Site → the domain its files/CDN are served from, when different. */
    private val CDN = emptyMap<String, String>()
    private val SITES = emptyList<String>()
    private val COOKIES = emptyMap<String, String>()

    fun cookiesFor(site: String): String? = COOKIES[site]

    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** The site to name as referrer for [host] (covers subdomains and the sites' CDN domains), or null. */
    /** Sites whose file servers only accept their www address as the referring page (checked live). */
    private val WWW_REFERER = emptySet<String>()

    fun refererFor(site: String): String = if (site in WWW_REFERER) "https://www.$site/" else "https://$site/"

    fun siteFor(host: String): String? {
        SITES.firstOrNull { host == it || host.endsWith(".$it") }?.let { return it }
        return CDN.entries.firstOrNull { (cdn, _) -> host == cdn || host.endsWith(".$cdn") }?.value
    }
}
