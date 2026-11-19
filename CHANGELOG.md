# Changelog

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
