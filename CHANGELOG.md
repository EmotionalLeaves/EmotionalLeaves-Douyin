# Changelog

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
