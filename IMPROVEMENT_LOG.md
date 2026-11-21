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
| `/aweme/v1/web/comment/list/` | ✅ 20 comments signed out |
| `/aweme/v1/web/aweme/post/` (a creator's posts) | 403 to the app; even Douyin's own creator page gets 200 with an empty body signed out, and its RENDER_DATA (~210 KB) has no posts. Signed out, creator pages can only show related videos / posts already seen |
| `/aweme/v1/web/aweme/detail/`, share page, douyin.com/video/<id> page | nothing signed out: expired addresses can't be refreshed (hence: trust URL expiry, download saved clips at once) |
| douyin.com/hot/<sentence_id> (trending topic page) | no posts for a visitor |
| `/aweme/v1/web/challenge/aweme/` | times out |
| Bing / DuckDuckGo `site:douyin.com/video` | no links from datacenter IPs |

## Ideas not done yet

- Grow signed-out search: related videos for trending words.
- Load more feed per page visit: simulated keys don't work; try real scrolling of the feed container or a second page.
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
- 2026-11-19 (chat, round 15): probe showed Douyin ignores simulated ↓ keys (nudgeNewAnswers=0), so
  nudging stops after two misses; comments show Douyin's reason instead of "No comments"; probe logs
  signed-out comments. 1.2.2.
- 2026-11-19 (chat, round 16): hidden page alternates douyin.com home and /jingxuan on each fresh load. 1.2.3.
- 2026-11-19 (chat, round 17): search skips the page search and web search after two empty tries in a
  session (they cost ~20 s each and never answered in tests). Probe found creator post lists answer 403
  signed out; creator pages and Following now use the creator's own page. 1.3.0.
- 2026-11-19 (chat, rounds 18–27, batch → 1.4.0): creator-page probe trace; fresh page's second batch;
  warm-up at app start; Following caps page fallback at 4; like counts (万/亿) on the heart; "See
  translation" under captions; search matching + GiveUpGate as tested pieces; probe re-fetch by id;
  creator avatars; second hidden page closes after 2 idle minutes.
- 2026-11-19 (chat, rounds 28–40, batch → 1.4.1): exact matches first in search; wording sweep and
  Comics mood removed; dead subreddit/board settings code removed; Share; English hints on tag chips;
  creator pages read RENDER_DATA (deep post search, tested); search index of 3,000 posts kept across
  restarts (cleared by Clear history, not saved incognito); Sign out; refresh by id via the post's own
  page; followed creators' pictures; Check sources gives Douyin 50 s; More comments.
- 2026-11-19 (chat, rounds 41–54, batches → 1.4.2, 1.5.0): signed-out hint in search; comment parsing
  tested; trending topic pages (no posts signed out) and heat; avatars everywhere (CreatorAvatars);
  sound line; creator posts via related videos + signed-out note; URL expiry trusted; saved clips
  download at once; hidden pages close in the background; like-button screen-reader text; More
  comments; Translate captions automatically (setting).
- 2026-11-19 (chat, rounds 55–60 → 1.5.1): creator/trend/post-page look-ups behind give-up gates
  (reset on any sign-in change); Settings wording on signed-out limits; lint 0 errors; Library wording.
- 2026-11-19 (chat, rounds 61–72 → 1.5.2, 1.6.0): 1.5.1's emulator test failed: most clips got 403
  from Douyin's video servers. Now every mirror address is kept and the player tries the next (then a
  fresh read) before skipping; expiry read from douyinvod.com paths; the launch cache drops expired
  posts. Ported from Feed: 5.3/5.4 (taste chips, voice search, For-you sort), 5.8/5.14 (Related: what
  Douyin shows beside a video you enjoyed feeds For You and More like this), 5.15 (Save this moment),
  5.17 (landscape controls; moments as jump chips). "Show Chinese in English" everywhere (tags,
  sounds, trending, comments, search captions) and English searches put into Chinese on the phone.
  Tabs in both languages (首页/发现/收藏/设置).
- 2026-11-19 (chat, rounds 73–90 → 1.7.0): verified 1.6.0 green (feed 40, search 59, comments 16);
  a re-run stalled clips (no frame) from the US runner, so stalls now move to the next mirror too.
  User: accounts are impossible outside China, so every sign-in prompt is gone. Then, as asked once
  verified, the Learn section: dictionary, words under captions and comments, pinyin, voice, word list
  and review, learner topics, frequent words; and live English for subtitles burned into videos.
- 2026-11-19 (chat, rounds 91–110 → 1.7.1, 1.7.2, 1.7.3): 1.7.0's emulator run caught the
  dictionary missing from the APK (the build unpacks .gz assets) — fixed in 1.7.1, plus the half-open
  words sheet. The Play-services text reader read nothing on first use, so the model is now inside
  the app; the APK went 64-bit only (93 → 79 MB). Emulator: live subtitles read 我们今天去吃火锅 exactly,
  translation both ways, English search 57 posts, monkey 0 crashes / 0 ANRs. Added: listening quiz,
  share word list, words in backups, "More videos with your words", topic rows from remembered posts,
  4 videos preloaded on Wi-Fi, English searches show the Chinese used, subtitle placement for wide videos.
- 2026-11-19/20 (chat, rounds 111–130 → 1.7.4–1.7.8): user: English subtitles late, app slow.
  Live subtitles: 250 ms checks, reused 480-px frame, fingerprint skip, no linger, timed cues for
  loops. Hidden Douyin pages pause all WebView timers 6 s after the last request (feed then gave 69
  posts vs 30–40). Probe facts: Douyin gives visitors no caption files (caption_download_addr 0/19).
  Start time measured (StartTimes; instant starts count 0): first swipes median ~1 s, 90% ~3.8 s;
  fast swiping ~2.2 s / 8.4 s, the tail being the 8 s stall wait → silent servers now 3.5 s, and
  connections to the next videos' servers opened ahead. Also: comments button with count, Chinese
  tab names, trending in Learn, pinyin in comments, heard lines in frequent words, English on screen
  by default, listening quiz, word export, words in backups, word boost.
- 2026-11-20 (chat, rounds 131–160 → 1.7.9–1.7.20): stutter fixes (no throwaway web views; hidden page
  reused, not rebuilt) — 99th-percentile frame 1.2 s → 0.15 s, no ANRs; starts mostly 0 ms at a
  normal pace. Learning: replay a line, 0.5×, pause after each line, confidence filter, transcript per
  video (⋯), lines heard, example lines under words, save-all, tone-coloured pinyin, Say it
  (zh-CN recogniser) and Mandarin voice search, subtitle size. UI: Me card, bilingual titles, WeChat-
  style quick entries on 发现, one-tap Funny & memes / Learning Chinese steers. 1.7.18's build failed
  on lint (StateFlow.value in composition) — the release gate now runs lint too.
- 2026-11-20 (chat, rounds 161–175 → 1.7.21–1.7.26): hide subtitles per video, due-count badge on
  学习, everyday pinyin readings first (说 shuō… surnames last), shadowing score (LCS of characters),
  tidier Learn, HSK levels + frequency ranks (Complete HSK Vocabulary, MIT, cloned via the git proxy)
  with "Learn the basics" (+10 by frequency), and "Easier Chinese first" (±0.3 by HSK≤3 share).
- 2026-11-20 (chat, rounds 176–190 → 1.7.27–1.7.32): home-screen shortcuts, two-way quiz, measure
  words (dictionary rebuilt keeping CL:), caption difficulty label, quick-entry fit on small phones,
  word-list filters; the More-like-this emulator check had been seeded with a Feed leftover query and
  never ran — now seeded from 美食.
- 2026-11-20 (chat, rounds 191–205 → 1.7.33–1.7.39): user: Check sources took ~2 min. Feed API
  (1.4 MB, unplayable for visitors) skipped after two tries and remembered for a day; Check sources
  quick path (posts in hand + mirrors + one trending call, parallel): 2 min → 2.4 s measured; For You
  60 posts 31 s → 21 s. Monkey found DouyinLoginActivity never declared in the manifest (Verify I'm
  human crashed) — fixed, registration checked each run. Daily goal, tone trainer.

## Rounds 206–215 (1.7.41–1.7.48): the two-minute start
- Timeline marks (`timeline +Nms …`) in PFHEALTH, from app start: page opening/started/finished, each page answer, harvest calls, first batch, first video.
- Found: concurrent harvests reloading each other's page; a harvest before the page existed "reloading" it; the fresh-page wait applied after the first batch had come (40 s stalls); the first answer often had nothing playable; an `item_aigc_follow_shot=1` field on every post read as an AI label (filter hid nearly everything); the silent-server check firing on slow but working downloads.
- Tried and dropped: related-videos-of-history as the first screen (1.7.43): their addresses sent no bytes from any mirror on the emulator.
- Result: cold install → first video at +16.4 s on the emulator (was 89–535 s); 0 silent-server skips; For You 64 posts in 44 s, Latest 60 in 71 s.
- 1.7.49–1.7.57: CSS/telemetry skipped in the hidden page; the readiness check ran via view.postDelayed on a never-attached WebView, so it never ran (every signed call waited out 14 s) — main looper now; the page's own module/feed POST is noted and replayed in-page (signed, captured), kept across launches; hosting the WebView in the window (1.7.51) didn't help and was undone; first screen at 4 candidates; launch pool topped up to 24 from unseen unexpired index posts. CI now times a relaunch (WARMSTART). Relaunch: 12 posts at 1.1 s, first video 2.65 s. Fresh install: ~16 s (page's own first batch). Latest 60 posts 16 s (was 73), For You 21 s.
- 1.7.58–1.7.64: replay of a fresh page's own request doesn't answer (its signer isn't set up until ~14 s) — the floor on a fresh install. Heap dumps after the monkey run (as root, hprof-conv, ~/tools/hprof_hist.py with the R8 mapping): the dictionary as 120k Entry objects was ~80 MB → compact blob/sorted keys (~18 MB); waiting page answers as UTF-8 bytes; peak Java heap 119 → 57 MB. Mirrors raced with 1-byte range GETs for every Douyin video (v3-web-prime 403s minutes-old addresses). Search: first page at first results + 1.2 s, slow ways as a late page, streamed per page: 18.4 s → 3.4 s (English 15 → 3.9 s).
