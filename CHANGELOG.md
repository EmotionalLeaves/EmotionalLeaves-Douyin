# Changelog

## 1.7.67 — 2026-11-21
- Pre-translate the next posts' captions (cached → shown at once); live captions poll every 150 ms, back off to 500 ms after ~10 s without text (was 2 s after 3 s); warm OCR and translator models.

## 1.7.66 — 2026-11-21
- Close the hidden pages 5 min (was 1) after the app leaves the screen.

## 1.7.65 — 2026-11-21
- Health: time a Douyin cover load through Coil; loop log.

## 1.7.64 — 2026-11-21
- Douyin search: detached ways, first page at first results + 1.2 s grace (6 s cap), the rest as a 'late' page; SearchRepository streams each page.

## 1.7.63 — 2026-11-21
- Race mirrors for every Douyin video (v3-web-prime 403s minutes-old addresses).

## 1.7.62 — 2026-11-21
- Race the mirrors of addresses older than 20 min (1-byte range GETs, 2.5 s cap) in resolveStream.

## 1.7.61 — 2026-11-21
- Captured page answers held as UTF-8 bytes (heap dump: 4 × ~6 MB Strings).

## 1.7.60 — 2026-11-21
- ZhDictionary: UTF-8 blob + sorted keys + line refs, entries parsed on lookup (was ~120k Entry objects, ~80 MB per heap dump).

## 1.7.59 — 2026-11-21
- Shorter Douyin card subtitle and Learn section title.

## 1.7.58 — 2026-11-21
- Record status/size/head of replayed feed requests in the timeline.

## 1.7.57 — 2026-11-21
- Top up the launch pool to 24 with unseen, unexpired posts from the Douyin index.

## 1.7.56 — 2026-11-21
- Timeline marks for the first video's requests, bytes and address switches.

## 1.7.55 — 2026-11-21
- Break the first-load loop before waiting when a few candidates are in hand; first-video marks; search timings in the health test.

## 1.7.54 — 2026-11-21
- Mark web view creation time; cap StartTimes marks at 400.

## 1.7.53 — 2026-11-21
- Clear screen reachable from the hold menu (CleanScreen had no way in); CI times a relaunch (WARMSTART).

## 1.7.52 — 2026-11-21
- Record the page's module/feed request (method/url/body/type); replay it in-page (signed, captured) once ready and before any reload; refresh_index bumped; give up after 3 misses. Undo window hosting.

## 1.7.51 — 2026-11-21
- Host the feed WebView in the activity window (2 px, alpha 0.01, behind content, touch-proof) so Chromium doesn't treat it as a hidden tab.

## 1.7.50 — 2026-11-20
- Page readiness check ran via view.postDelayed on a never-attached WebView (never ran): use the main looper; no onPause.

## 1.7.49 — 2026-11-20
- Skip mcs/mon zijieapi, ibytedapm and CSS in the hidden page; MAX_CAPTURED 4.

## 1.7.48 — 2026-11-20
- First screen goes out at 4 candidates; log hosts fetched before the page's first answer; loop log.

## 1.7.47 — 2026-11-20
- AI label only from is_aigc_media/aigc_info flags (item_aigc_follow_shot=1 everywhere hid all); silent-server check uses real transferred bytes; Hide English toggle with larger targets.

## 1.7.46 — 2026-11-20
- Fresh-page wait only until the page's first answer; 6 s follow-up wait for its second; timeline marks for the page's first answers.

## 1.7.45 — 2026-11-20
- Harvest opens a missing page instead of reloading it; fromPage takes a second answer when the first had nothing playable; loading screen teaches a word; page lifecycle timeline marks.

## 1.7.44 — 2026-11-20
- Drop the related-of-history first-screen race (related addresses silent on the emulator); tag related posts via=related in failure reasons.

## 1.7.43 — 2026-11-20
- Single-flight harvest shared by callers; first pull races related-of-history against the page batch; FRESH_PAGE_MS 40 s.

## 1.7.42 — 2026-11-20
- Signed out: recommended() always harvests the page (API skipped); VM retries every 2 s while nothing is shown; startup marks for each step.

## 1.7.41 — 2026-11-20
- First For You load holds interest searches; harvest waits for a fresh page's own batch (no nudge/reload within 25 s); no reload on bad answers while loading; second-batch wait 0.8 s; startup timeline + cold start with pm clear in CI; bottom bar never fades/tucks; Chinese AI terms + Douyin AIGC label; hideAiArt on by default (migration 2).

## 1.7.40 — 2026-11-20
- Transcript save-all via WordBook.addSentence.

## 1.7.39 — 2026-11-20
- quickDouyin tries up to 5 ready posts with 2 mirrors each (12 URLs, parallel); health logs app start to first video.

## 1.7.38 — 2026-11-20
- directFeedGate closed at start when it proved useless within 24 h (signed out); GiveUpGate.close().

## 1.7.37 — 2026-11-20
- TonePractice rounds from HSK 1–2 single characters with tones 1–4; trainer screen.

## 1.7.36 — 2026-11-20
- WordBook.reviewedToday (persisted per day); review card progress; Check sources quick path measured at 0.56 s.

## 1.7.35 — 2026-11-20
- Smoke test checks DouyinLoginActivity registration via PackageManager instead of launching it mid-run (that cut 1.7.34's run short).

## 1.7.34 — 2026-11-20
- DouyinLoginActivity declared in the manifest (ActivityNotFoundException found by the monkey run); smoke test opens it; quick check timeouts 8 s / 6 s.

## 1.7.33 — 2026-11-20
- directFeedGate skips callFeed after two unplayable answers (reset on sign-in change); SourceChecker.quickDouyin (ready posts + trending + reachable, in parallel); health test times Check sources.

## 1.7.32 — 2026-11-20
- Word list filter chips; More-like-this smoke test seeded from 美食.

## 1.7.31 — 2026-11-20
- Quick-entry row: equal-width cells, 46 dp circles.

## 1.7.30 — 2026-11-20
- Caption difficulty label from easyShare (≥0.8 Easy, ≥0.55 Medium).

## 1.7.29 — 2026-11-20
- Dictionary rebuilt keeping CL: classifiers (5th column, up to 3), shown in WordRow.

## 1.7.28 — 2026-11-20
- Quiz alternates listening (Chinese audio → English) and reading (English → characters).

## 1.7.27 — 2026-11-20
- Dynamic shortcuts (ShortcutManagerCompat) with an extra routed through OpenTarget; Review opens Learn straight into the review.

## 1.7.26 — 2026-11-20
- LearnBoost.easy: ±0.3 by the share of HSK≤3 words in the caption (hashtags out, ≥3 words); Dict+HSK loaded at start when on; HSK in word list and review.

## 1.7.25 — 2026-11-20
- Bundled HSK 3.0 levels + frequency ranks (Complete HSK Vocabulary, MIT, 181 KB); WordRow badge; Learn HSK 1–3 starter (+10 by frequency).

## 1.7.24 — 2026-11-20
- Shadowing.score (LCS of Han characters) with Say it in Words; Learn switches moved to the bottom.

## 1.7.23 — 2026-11-20
- ZhDictionary.parse ranks entries: preferred everyday readings for common polyphones first, proper names/surnames/variants last (stable).

## 1.7.22 — 2026-11-20
- Due-count badge on the Learn tab.

## 1.7.21 — 2026-11-20
- LiveSubtitles hide-for-this-video.

## 1.7.20 — 2026-11-20
- LearnPrefs.subSize (bodyLarge / titleLarge / headlineSmall).

## 1.7.19 — 2026-11-20
- Transcripts (in-memory, 30 videos) + SeekRequest; Learn heard lines; lint fix (collectAsState for Heard in WordRow); release gate runs lint.

## 1.7.18 — 2026-11-20
- Search quick-entry row; Heard example lines under words.

## 1.7.17 — 2026-11-20
- pinyinColored (AnnotatedString per syllable) in feed, subtitles, comments; Words save-all.

## 1.7.16 — 2026-11-20
- Review: zh-CN RecognizerIntent check against the card; Search mic long-press for zh-CN.

## 1.7.15 — 2026-11-20
- Steer presets in the For You hold menu.

## 1.7.14 — 2026-11-20
- Live subtitles skip lines with ML Kit confidence under 0.45 (when it gives one); Learn topic cards with thumbnails from the remembered index.

## 1.7.13 — 2026-11-20
- LearnPrefs.autoPause: LiveSubtitles pauses the player when a line ends or changes, keeping the ended line's English up.

## 1.7.12 — 2026-11-20
- LearnCard on the Me page; bilingual screen and section titles.

## 1.7.11 — 2026-11-20
- Replay button seeks to the cue start minus 300 ms; PlaybackSpeed adds 0.5x.

## 1.7.10 — 2026-11-20
- DouyinWeb.reload keeps the WebView (loadUrl again; destroy only in the background); ready signal via provider; WordBook writes on a background executor.

## 1.7.9 — 2026-11-20
- wakeTimers uses the existing page (throwaway WebView only if none); Heard writes on a background executor; caption English max 3 lines; CI prints the ANR main-thread trace.

## 1.7.8 — 2026-11-19
- Preloader.connect (1-byte open per host, once a minute) for the next videos; SILENT_TIMEOUT_MS 3.5 s when nothing is buffered; StartTimes health metric (instant starts count as 0).

## 1.7.7 — 2026-11-19
- Live subtitles: 250 ms checks, reused 480-px frame, signature skip, cue memory for loops, no linger; DouyinWeb pauses all WebView timers 6 s after the last request (woken per request; kept awake while the check page or an embed is on screen); preload after a 1.2 s head start (3 ahead on Wi-Fi); translator warmed at start; scrub bar polls at 100 ms.

## 1.7.6 — 2026-11-19
- autoTranslate default on, migrated once (learnDefaultsVersion); review example highlights the word; README learning section.

## 1.7.5 — 2026-11-19
- Heard (live subtitle lines, 300 kept, Forget hook, incognito-aware) feeds Frequent; pinyin in comments; top tabs: Chinese name beside the English.

## 1.7.4 — 2026-11-19
- Per-character glosses, tap-to-hear ruby, Chinese-used hint on English searches, live-subtitle y mapped through the fitted video band.

## 1.7.3 — 2026-11-19
- Quiz (least-known first, 3 distractors), LearnBoost (+0.25 base score for captions with saved words, off by default), exportWords (TSV), ExportBundle.words with merge on import.

## 1.7.2 — 2026-11-19
- Bundled ML Kit Chinese text recognition (the Play-services module read nothing on first use in the emulator run).
- Sideload/release APK: arm64-v8a + x86_64 only (93 → 79 MB).
- Preload 4 videos ahead on unmetered networks; Search topic rows from the remembered index first.

## 1.7.1 — 2026-11-19
- Fix: AGP unpacks .gz assets, so the dictionary is learn/zh_en.tsv in the APK; Dict reads either and never throws.
- Sentence cards, seo_info.ocr_content as "What's said in it", review streak.

## 1.7.0 — 2026-11-19
- Learn tab: CC-CEDICT dictionary (bundled, 3.6 MB), bidirectional maximum-match word splitting, pinyin tone marks, SM-2-style review (WordBook), learner topics, frequent words from history, Android TTS.
- Live subtitles: ML Kit Chinese text recognition on the playing frame (lower half, every 0.7 s, slower when none found), LiveSubs.pick picks the subtitle line, translated and shown above it.
- Sign-in prompts removed; the Douyin button is "Verify I'm human".
- Stalled clips (no frame in 8 s) also move to the next mirror.

## 1.6.0 — 2026-11-19
- Show Chinese in English across tags, sounds, comments, trending and search captions (`rememberEnglish`); English queries translated to Chinese before searching (`EnglishToChinese`, `Zh.isLatinQuery`).
- Ported Feed 5.15 (Moments) and 5.17 (landscape controls, with moments as jump chips).
- Bilingual tab labels; launch cache drops expired douyinvod addresses.

## 1.5.2 — 2026-11-19
- Playback tries each of Douyin's mirror servers (and a fresh address) before skipping a video; fixes the 403s that failed 1.5.1's emulator test.
- Expiry read from douyinvod.com paths.
- Related (Feed 5.8/5.14): Douyin's related list feeds For You after a video you enjoyed, and boosts "More like this".
- Ported Feed 5.3/5.4 search features; Douyin no longer capped at 20 posts per load.

## 1.0.0 — 2026-11-18
- First version: cloned from Feed, with every adult source removed and Douyin (抖音) as the source.
- Douyin's recommended feed, search, creator pages, comments and shared links (v.douyin.com/…).
- Douyin requests are signed by Douyin's own page in a hidden WebView; shared links fall back to the public share page.
- No age gate. Installs alongside Feed (app id `dev.dyfeed`) and updates from this repo's releases.
