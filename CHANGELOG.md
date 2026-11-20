# Changelog

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
