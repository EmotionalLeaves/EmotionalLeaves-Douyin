# Improvement loop

Rounds of "find the most useful fix or upgrade → build → test → push". Each scheduled round reads
this file first, adds one entry at the bottom, and keeps the rules below.

## Rules for every round

1. **Never push anything that doesn't build and pass:** `./gradlew --no-daemon testDebugUnitTest compileDebugAndroidTestKotlin`
   must succeed locally first. Gate the push on it (`&& git push`), never run them as separate steps.
2. One focused change per round. Keep the app's look and wording consistent (English UI, Chinese content).
3. A user-visible change bumps `versionName` in `app/build.gradle.kts` and adds a section at the top of
   `app/src/main/assets/whats_new.md`; GitHub then publishes a release the app updates itself from.
4. After pushing, wait for the GitHub run (about 25 min). If it fails, the next priority is making it
   green again. Failure reasons and the emulator's Douyin probe appear as annotations:
   `GET /repos/EmotionalLeaves/EmotionalLeaves-Douyin/check-runs/<job id>/annotations`.
5. Don't weaken or delete tests to get green; fix the cause. A test may tolerate something Douyin
   itself refuses (for example search needing sign-in) only if it logs it.
6. No new analytics, accounts or data leaving the phone beyond what Douyin and the listed services see.

## What Douyin allows a signed-out visitor (from the emulator probe)

| Thing | Answer |
|---|---|
| `/aweme/v1/web/tab/feed/` | 403 |
| `/aweme/v2/web/module/feed/` | 404 |
| `/aweme/v1/web/module/feed/` | 200, but video addresses come back encrypted (unusable) |
| Feed answers Douyin's own page loads for itself (hook in `DouyinWeb.HOOK_JS`) | ✅ the feed's real source |
| `/aweme/v1/web/search/item/`, `general/search/single/` | 2483 "请先登录" (sign in first) |
| Douyin's search page | shows a captcha page (验证码中间页) |
| `/aweme/v1/web/aweme/related/` | ✅ 10 per post: main source of signed-out search |
| `/aweme/v1/web/hot/search/list/` | ✅ trending words (shown in Search) |
| `/aweme/v1/web/challenge/aweme/` | times out |
| Bing / DuckDuckGo `site:douyin.com/video` | no links from datacenter IPs |

## Ideas not done yet

- Grow signed-out search: related videos for trending words.
- Douyin comments for signed-out visitors (check with the probe whether `/comment/list/` answers).
- Cache search results so going back doesn't search again.

## Log

- 2026-11-18 → 19 (chat session): cloned from Feed; Douyin source via hidden WebView; adult sources and
  age gate removed; package `dev.dyfeed`; spacing fix (one place per creator); simplified Settings and
  hold menu; Douyin row at top of Settings; translate captions/comments (ML Kit); camelCase and
  `cards[]` post formats; signed-out search (related, trending, loaded posts); trending list in Search.
  Releases 1.0.0 → 1.2.0, all green.
- 2026-11-19 (chat, round 14): feed moves the hidden page along (↓ key + scroll) before reloading it;
  search uses related videos of 5 matching posts (was 3); probe reports whether moving the page brings
  a batch (`nudgeNewAnswers`). 1.2.1.
