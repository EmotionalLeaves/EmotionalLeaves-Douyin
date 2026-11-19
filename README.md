# 抖音 Feed

A private, sideloaded Android app that plays **Douyin (抖音, China's TikTok)** in one vertical swipe feed, with recommendations re-learned on the phone. Cloned from Feed, with every adult source taken out. No accounts needed, no backend, no analytics.

- **Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room + SQLCipher, OkHttp/kotlinx.serialization, Media3 ExoPlayer, Coil, WorkManager, Android WebView.
- **Min SDK 26 / target SDK 36.** App id `dev.dyfeed` (installs next to Feed).

## How Douyin is read

Douyin's web API (`www.douyin.com/aweme/v1/web/…`) only answers requests signed by the obfuscated script on douyin.com (`a_bogus`, `msToken`). Instead of copying that script, which changes often, the app keeps one invisible WebView open on douyin.com and sends its requests from inside the page, where Douyin's own script signs them (`source/douyin/DouyinWeb.kt`). The page's own pictures and videos are never downloaded; the app plays the videos itself from the addresses in the answers.

| What | Endpoint |
|---|---|
| For You / Latest | `/aweme/v1/web/tab/feed/` (Douyin's recommended feed, re-ranked here) |
| Search | `/aweme/v1/web/search/item/` (Douyin may want you signed in) |
| Creator page, Following | `/aweme/v1/web/aweme/post/` |
| Comments | `/aweme/v1/web/comment/list/` |
| Shared links | `v.douyin.com/…` → `iesdouyin.com/share/video/<id>/` (public page, no signature) |

If the feed stops loading, Douyin probably wants a "verify you're human" check: **Settings → 抖音 Douyin** (top of Settings) opens the real site, and anything you do there (the check, signing in) carries over. Video addresses expire after a few hours, so older saved posts are looked up again before playing.

This depends on Douyin's website, which can change without notice. When it does, `DouyinWeb.kt` (the request) and `DouyinParser.kt` (the answer) are the two places to look.

## Build

Requirements: JDK 17+, the Android SDK (platform 36 + build-tools), and internet access for the first Gradle sync.

```bash
cp local.properties.example local.properties   # set sdk.dir
./gradlew testDebugUnitTest
./gradlew assembleRelease                       # → app/build/outputs/apk/release/app-release.apk
```

### Building on GitHub Actions

`.github/workflows/build.yml` runs on every push to `main` (or by hand from **Actions → Run workflow**): unit tests, the APKs, lint, emulator tests, and a GitHub release for each new version. The APK is under the run's **Artifacts** and on the **Releases** page. Optional signing secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Sideload

Download the APK from the latest GitHub release on your phone, open it, and allow installs from your browser or file manager if Android asks. After that the app updates itself from this repo's releases (Settings → Updates).

## Using it

- **Feed:** For You (learned from what you watch), Following (creators you follow here) and Latest. Swipe up for the next post; tap to pause, double-tap to like, hold for the menu (Comments, Not interested, hide a tag or creator, open on Douyin…). Swipe left on a post for everything by its creator.
- **Photo posts** turn sideways picture by picture.
- **Search:** type in Chinese for best results (美食, 重庆, 猫…); the topic chips do it for you. Combos work: `美食 + 重庆 -火锅`.
- **Share to the app:** share a Douyin link from anywhere and it plays here.
- **Library:** Saved, Liked, History and Following; saved posts download for offline.

## Architecture

```
dev.pfeed
├── domain/            pure Kotlin, unit-tested
│   ├── model/         MediaItem, Page, StreamInfo, FeedPrefs…
│   ├── filter/        Blocklist
│   └── reco/          AffinityModel + TasteModel, Engagement, Features, Ranker, Spread, Niche, Deduplicator
├── data/
│   ├── db/            Room entities/DAOs, encrypted by SQLCipher
│   ├── settings/      DataStore-backed AppSettings
│   └── repo/          FeedSession (fetch → filter → dedup → rank → space), Search, Library, Signals, OfflineStore
├── source/            SourceAdapter, SourceRegistry
│   ├── parse/         pure, unit-tested parsers (MediaTypes, JsonPath, GenericJson)
│   ├── net/           HttpJson, CachingAdapter (fetch-many + in-memory paging base)
│   ├── douyin/ local/ direct/   the adapters (rss/ generic/ api/ are kept but not registered)
│   └── auth/          OAuth PKCE (Custom Tabs)
├── player/            MediaCache (LRU + offline), PlayerPool, Preloader, ResumePoints
├── security/          AppLockManager, SecretStore (PIN + Keystore-wrapped DB key), LauncherDisguise
├── work/              MaintenanceWorker (history purge, compaction)
└── ui/                Compose screens: feed, search, library, settings, lock
```

## Adding a SourceAdapter

1. Create a class in `source/<name>/` that implements `SourceAdapter`:

   ```kotlin
   @Singleton
   class MyAdapter @Inject constructor(private val http: OkHttpClient) : SourceAdapter {
       override val id = "my"                   // stable, unique, used in item keys
       override val displayName = "My Source"
       override val requiresAuth = false
       override suspend fun authenticate(ctx: AuthContext) = AuthResult.NotRequired
       override suspend fun fetchFeed(cursor: String?, prefs: FeedPrefs): Page<MediaItem> = TODO()
       override suspend fun search(query: String, cursor: String?): Page<MediaItem> = TODO()
       override suspend fun resolveStream(item: MediaItem): StreamInfo = TODO()
       override fun reportUrl(item: MediaItem): String? = item.pageUrl
   }
   ```

2. Register it in `di/SourceModule.kt` with a `@Binds @IntoSet` line.
3. Rules:
   - Throw `SourceException(id, "message", isAuthError = …)` for anything the user should see. The feed shows the message and keeps going with the other sources.
   - `cursor == null` means "refresh from the top". Return `nextCursor = null` at the end.
   - Return `StreamInfo.Embed(url)` only for an **official** embed player. It renders in the sandboxed WebView.
   - For OAuth, use `OAuthManager.authorize(ctx.activity, OAuthConfig(...))`, which runs PKCE in a Custom Tab with redirect `dyfeed://oauth/callback`. Store tokens with `CredentialDao`, which lives in the encrypted DB.
   - A source that works without signing in but gets more with it sets `offersSignIn = true` (Douyin does).

## Privacy

| Feature | Where |
|---|---|
| Optional app lock: PIN (PBKDF2-SHA256, salted; lockout after 5 wrong tries) then biometrics | Settings → Privacy |
| Encrypted database (SQLCipher, key wrapped by the Android Keystore) | `SecretStore.databasePassphrase` |
| Private mode (always incognito): no history, seen marks or learning | Settings → Privacy |
| Weekly encrypted backups to a folder you choose | Settings → Backup |
| Everything For You learns stays on the phone; no accounts, analytics or notifications | — |

Douyin itself sees what any visitor to douyin.com sees: the hidden page and your sign-in (if any) are a normal browser session with Douyin.

## Tests

- `app/src/test`: the Douyin parser (feed, search, photo posts, shared links), blocklist, dedup, spacing rules, the taste model and a simulation of the For You loop (converges on a taste, finds a rare one, follows a change of taste, judges comics by reading progress).
- `app/src/androidTest`: on a real emulator in CI, with the real network: posts display and keep scrolling, search, saved posts are kept offline, every source's health, the panic gesture, and screenshots of every screen (post pictures masked).
