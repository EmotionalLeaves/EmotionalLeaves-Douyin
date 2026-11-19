package dev.pfeed.data.settings

import dev.pfeed.domain.reco.RankWeights
import kotlinx.serialization.Serializable

@Serializable
data class SourceConfig(val id: String, val enabled: Boolean)

@Serializable
enum class Disguise { NONE, NOTES, CALCULATOR, BROWSER }

@Serializable
data class MastodonFeed(val host: String, val tag: String? = null) {
    val label: String get() = if (tag.isNullOrBlank()) host else "$host #$tag"
}

@Serializable
data class BooruFeed(val host: String, val tags: String? = null) {
    val label: String get() = if (tags.isNullOrBlank()) host else "$host · $tags"
}

/**
 * User-defined mapping for the Generic JSON adapter. [urlTemplate] may contain {cursor} and {query}.
 * Paths are dotted (see JsonPath), relative to each item after [wrapPath].
 */
@Serializable
data class JsonFeed(
    val name: String,
    val urlTemplate: String,
    val itemsPath: String,
    val mediaPath: String = "url",
    val searchTemplate: String? = null,
    val wrapPath: String = "",
    val idPath: String = "id",
    val titlePath: String = "title",
    val creatorPath: String = "",
    val thumbnailPath: String = "",
    val tagsPath: String = "",
    val pagePath: String = "",
    val typeHint: String = "auto",
)

/** Everything user-configurable except secrets (PIN, DB key, OAuth tokens), which never leave the device. */
@Serializable
data class AppSettings(
    /** Order = priority (first wins when deduplicating). Missing adapters are appended with their defaults. */
    val sources: List<SourceConfig> = emptyList(),
    val rssFeeds: List<String> = emptyList(),
    /** Direct media/page URLs the user pasted. */
    val directUrls: List<String> = emptyList(),
    /** Subreddit names (no "r/" prefix). Pre-seeded with popular NSFW subs. */
    val redditSubs: List<String> = emptyList(),
    val mastodonFeeds: List<MastodonFeed> = emptyList(),
    val booruFeeds: List<BooruFeed> = emptyList(),
    /** coomer.st / kemono hosts (OnlyFans/Fansly/Patreon aggregators, shared API). */
    val kemonoHosts: List<String> = emptyList(),
    val jsonFeeds: List<JsonFeed> = emptyList(),
    /** Storage Access Framework tree URIs. Device-specific, so not imported from backups. */
    val localFolders: List<String> = emptyList(),
    val cacheSizeMb: Int = 1024,
    val autoplayOnMobileData: Boolean = true,
    /** Grids play the video nearest the middle silently in its tile, moving on every few seconds. */
    val livePreviews: Boolean = true,
    /** The app's colour (a [dev.pfeed.ui.theme.Palette] name). */
    val accent: String = "ROSE",
    /** Check GitHub for new versions and download them on Wi-Fi. */
    val autoUpdate: Boolean = true,
    /** Face the phone down or shake it to hide the app (like the three-finger touch). */
    val flipToHide: Boolean = false,
    /** Long videos (5 min+) you've not watched before start 25 s in, past the studio intro. */
    val skipIntros: Boolean = false,
    /** Even out loudness between videos (and within them). */
    val smartVolume: Boolean = true,
    /** The lock screen looks (and works) like a calculator; the PIN then = gets in. */
    val calculatorLock: Boolean = false,
    /** When the battery is low: 480p and still grids, until it's charging. */
    val batterySaver: Boolean = true,
    /** Every few hours on Wi-Fi, For You is fetched and ranked while the app is closed, so it opens instantly. */
    val readyAhead: Boolean = true,
    /** Volume down = next post, up = the one before, while a feed is on screen. */
    val volumeKeysTurnPages: Boolean = false,
    /** Sound plays only through headphones; with only the speaker, the feed stays silent. */
    val headphonesOnly: Boolean = false,
    /** A gentle "time for a break?" after this many minutes of watching in one go (0 = never). */
    val breakReminderMin: Int = 0,
    /** Highest quality, up to 4K where a site has it (more data; Data saver still wins). */
    val bestQuality: Boolean = false,
    /** Leave the app out of the recent-apps list entirely. */
    val hideFromRecents: Boolean = false,
    /** Where a panic (three fingers, flip, shake) leaves to: "home", "browser", "calculator" or "messages". */
    val panicTo: String = "home",
    val defaultMute: Boolean = false,
    val historyRetentionDays: Int = 30,
    /** Look every site up over encrypted DNS (1.1.1.1), not just ones the network blocks. */
    val encryptedDns: Boolean = false,
    /** Private mode: nothing is added to watch history or search history, and watching teaches For You nothing. */
    val privateMode: Boolean = false,
    /** Data saver: videos at most 480p (fewer megabytes on mobile data). */
    val dataSaver: Boolean = false,
    /** Blur grid previews (Library, Search) until opened, for using the app around other people. */
    val blurPreviews: Boolean = false,
    /** Saved posts are downloaded (on Wi-Fi) into private storage so they play offline. */
    val keepSavedOffline: Boolean = true,
    /** Kinds of post kept out of the main feeds ("video", "image", "gif", "comic"); search and tag pages still show them. */
    val hiddenFeedTypes: Set<String> = emptySet(),
    /** Which videos the main feeds show by length: "any", "short" (clips under 3 min) or "long" (3 min and up). */
    val videoLength: String = "any",
    /** Leave out AI-generated art (Stable Diffusion, NovelAI…) everywhere: feeds, search, tag pages. */
    val hideAiArt: Boolean = false,
    /** Leaving the app while a video plays keeps it going in a small floating window. Off by default. */
    val pictureInPicture: Boolean = false,
    /** Your proxy for sites blocked where you are: "off", "http", "socks" or "orbot" (Tor via Orbot, 127.0.0.1:9050). */
    val proxyType: String = "off",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyUser: String = "",
    val proxyPassword: String = "",
    /** Send everything through the proxy, not just blocked sites. */
    val proxyEverything: Boolean = false,
    val lockTimeoutSec: Int = 30,
    val halfLifeDays: Double = 14.0,
    val weights: RankWeights = RankWeights(),
    val disguise: Disguise = Disguise.NONE,
    /** Version of the taste model the stored affinities were learned with; see SignalRepository. */
    val tasteModelVersion: Int = 0,
    /** Version of the built-in content cleanup applied to saved sources; see SettingsRepository. */
    val contentFilterVersion: Int = 0,
)

/** Leftovers from the app this was cloned from; nothing is listed any more. */
object MastodonExclusions {
    fun isExcluded(host: String): Boolean = false
}

object DefaultSources {
    val redditSubs: List<String> = emptyList()
    val mastodonFeeds: List<MastodonFeed> = emptyList()
    val booruFeeds: List<BooruFeed> = emptyList()
    val kemonoHosts: List<String> = emptyList()
    fun isSfwOnlyHost(host: String): Boolean = false
    fun isRetiredHost(host: String): Boolean = false
}

