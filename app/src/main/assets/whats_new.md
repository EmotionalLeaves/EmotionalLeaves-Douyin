## 1.7.64 — 2026-11-21
Search results sooner

- Faster search: results appear as soon as the quicker sources come back (a few seconds), and the slower ones (Douyin's own search page, the web) add theirs underneath when they arrive. Before, nothing showed until the slowest had finished, 15 to 18 seconds on the test phone.

## 1.7.63 — 2026-11-21
Every video from the fastest mirror

- Every Douyin video now starts from whichever of its mirror servers answers first. In 1.7.62 this only covered posts fetched over 20 minutes earlier. The test phone showed a server refusing addresses only minutes old, so it now applies to every video. The quick check also opens the connection the video then plays over, so it costs almost nothing.

## 1.7.62 — 2026-11-21
Fastest mirror first

- The first video after reopening the app starts sooner. For posts fetched a while ago (more than 20 minutes), the app now asks all of the video's mirror servers at once and plays from the first one that answers. Before, it tried them one by one, and the test phone waited 3.8 seconds for a refusal before trying the next.
- Includes 1.7.61: the app's peak memory in the stress test dropped from 119 MB to 57 MB.

## 1.7.61 — 2026-11-21
Leaner again

- Uses less memory again. Douyin's feed answers waiting to be shown (about 3 MB of text each) are now kept in half the space. The memory snapshot found four of them waiting, using 24 MB.
- Includes 1.7.60: the dictionary takes about 18 MB instead of 80 MB. In the test phone's stress test, the app's peak memory dropped from 119 MB to 73 MB.

## 1.7.60 — 2026-11-21
A lighter dictionary

- Uses much less memory. A memory snapshot from the test phone showed the Chinese dictionary (120,000 words) taking about 80 MB, more than everything else in the app together. It's now stored compactly at about 18 MB, with the same words, readings and search. Less memory means fewer pauses while scrolling and less chance of Android closing the app in the background.

## 1.7.59 — 2026-11-21
Tidier text

- Tidier text: the Douyin card in Me now just says "No account needed" (the old line was cut off), and Learn's "Often heard · 常见词" heading fits on one line.

## 1.7.58 — 2026-11-21
Replay answers

- Behind the scenes: when the app asks Douyin for posts the page's way and gets nothing back, health checks now record what Douyin answered. This is to get a fresh install's first posts sooner, too.

## 1.7.57 — 2026-11-21
More ready at launch

- Opening the app again: the first video plays at about 3.5 seconds on the test phone (posts appear at 1.3 s).
- More to watch straight away: if few posts were left over from last time, the app adds others Douyin sent earlier that you haven't seen and whose videos still load. You get about 24 posts right away, which covers the time Douyin's page takes to load (around 15 s).

## 1.7.56 — 2026-11-21
Tracing the first video

- Behind the scenes: health checks now trace each network step of the first video. When you reopen the app, the posts appear at 2.4 s but the first video took until 10 s, and this shows where that time goes.

## 1.7.55 — 2026-11-21
Leftovers first

- Opening the app again shows posts sooner. Posts left over from last time now go on screen straight away (about 1 second), instead of waiting for the first round of new ones (4.4 seconds on the test phone).
- Health checks now time each step of the first video's start, and how long searches take.

## 1.7.54 — 2026-11-21
Start-up check

- Behind the scenes: health checks now time how long the phone's browser engine takes to start, which blocks the screen while it happens. The start-up log is also capped so it can't grow during long sessions.

## 1.7.53 — 2026-11-21
Clear screen

- 清屏 Clear screen: hold a post and choose Clear screen to see just the video. Touch the screen and "Show everything" appears for a couple of seconds to bring the rest back. The bottom bar stays.
- Includes 1.7.52: the app notes how Douyin's page asks for its feed and asks the same way itself. On the test phone, Latest now loads its first 60 posts in 10–17 seconds (it was 73), and later launches no longer wait for the page to ask by itself.
- Health checks now also time a second launch, the kind you'd normally have.

## 1.7.52 — 2026-11-21
Ask the page's way

- Pulls should be much faster. The app now notes how Douyin's own page asks for its feed, keeps that across launches, and asks the same way from inside the page as soon as the page can sign requests. Before, it waited about 10 seconds for the page to ask by itself, and restarted the page for every new batch (about 13 seconds each).
- Undoes 1.7.51's change: keeping the hidden page in the app's window didn't bring the first batch any sooner, and the page took longer to load.

## 1.7.51 — 2026-11-21
A page that isn't throttled

- Douyin's hidden page now sits inside the app's window while you're using it: 2 pixels, invisible, behind everything, and it never takes a tap. A page in no window counts as a background tab, so the browser slows its timers right down. On the test phone the page finished loading at 2.7 s, but its first batch only came at 13.8 s.
- Includes 1.7.50: no more 14-second waits before searches, comments and creator pages. For You and Latest now load their first 60 posts in 27 s and 16 s on the test phone (they took 38 s and 73 s).

## 1.7.50 — 2026-11-20
No more 14-second waits

- Searches, comments and creator pages answer up to 14 seconds sooner. Douyin's hidden page was never marked "ready", so the first request after each page load sat out a 14-second timeout before going ahead. It now goes as soon as Douyin's request signer is in place.
- Includes 1.7.49: the hidden page skips Douyin's usage logging, performance monitoring and styling, and uses less memory.

## 1.7.49 — 2026-11-20
Lighter Douyin page

- Douyin's hidden page loads lighter. It no longer downloads Douyin's usage logging, performance monitoring or page styling. None of that is needed to fetch posts, and on the test phone it made up about 75 of the requests made before the first batch.
- Uses less memory: fewer of Douyin's large feed answers (1.5–3 MB each) are kept waiting.
- Includes 1.7.48: the first screen shows as soon as a few posts are in.

## 1.7.48 — 2026-11-20
First screen sooner

- The first screen shows up as soon as a few posts are in, instead of waiting for a full batch of twelve. That's about 3 seconds sooner on the test phone. The rest load in behind it.
- Health checks now record what Douyin's page downloads before its first batch, to find what can be skipped next.

## 1.7.47 — 2026-11-20
Real AI labels, fewer skipped videos

- Fixes a bug in the unreleased AI filter. A Douyin field on every post ("you can film an AI follow-along of this") was read as "made with AI", so the filter could hide almost everything. It now reads only Douyin's real AI labels.
- Videos no longer get skipped on slow connections. The app used to give up on a video server after 3.5 seconds with an empty buffer, even while data was arriving. It now checks whether any bytes came in.
- Clearer caption buttons: "Hide English" instead of "Show original" (the Chinese was showing anyway), with bigger tap areas for it and for 词 Words.
- Includes 1.7.45–1.7.46: faster first posts and pulls, and a word to learn while the feed loads.

## 1.7.46 — 2026-11-20
Faster pulls

- Faster pulls after the first screen. Once Douyin's hidden page had brought its first batch, the next pull still waited up to 40 seconds for a "first batch" that had already come. Now it waits only a few seconds for the page's usual second batch, then moves on.
- Includes 1.7.45: the page opens instead of restarting when the feed asks early, the real batch is taken when the first answer has nothing playable, and a word to learn while the feed loads.

## 1.7.45 — 2026-11-20
Faster first posts, a word while you wait

- Faster first posts. Two fixes, both found in the test phone's timeline:
  - If the feed asked before Douyin's hidden page had opened, the app reloaded the half-open page and started it over. It now opens the page and waits for it.
  - The page's first answer often has nothing playable in it, and the real batch follows a few seconds later. The app now takes that next answer straight away instead of showing an empty screen until a retry.
- A word while you wait: if the feed is still loading after 3 seconds, a common Chinese word appears with its pinyin and meaning. Tap it to hear it, or tap Another. After 8 seconds the screen also says why it's taking a while.
- Includes 1.7.44: the related-videos first screen from 1.7.43 is gone (its videos never loaded).

## 1.7.44 — 2026-11-20
Snappy start, fixed

- Fixes 1.7.43: the "related videos" first screen it tried filled the feed with posts whose video servers never sent anything to the test phone, so it's gone. The first screen comes from Douyin's own page again, which the test phone now shows about 16 seconds after a fresh install (it was 89+ seconds).
- Keeps 1.7.43's main fix: parts of the app no longer reload Douyin's hidden page on each other, so posts aren't held up for a minute.
- Includes 1.7.41–1.7.42: the bottom bar always stays and answers the first tap; AI-made posts hidden by default; quick retries until the first post shows.

## 1.7.43 — 2026-11-20
Snappy start

- Faster start, second pass, from the test phone's timeline. Different parts of the app were each reloading Douyin's hidden page while another waited on it, which pushed the first posts past a minute. Now they share one fetch.
- Faster first screen: at launch the app asks Douyin for videos related to ones you've watched before. That answers as soon as the page can sign requests, well before Douyin's page has fetched its own feed. The first to arrive fills the screen.
- Includes 1.7.41–1.7.42: the bottom bar always stays and answers the first tap; AI-made posts hidden by default; no feed-address detour without an account; quick retries until the first post shows.

## 1.7.42 — 2026-11-20
Snappy start

- Faster start, measured: on the test phone Douyin's posts arrived 24 seconds after launch but reached the screen at 144 seconds. Three fixes for that gap. Without an account the app now never asks Douyin's feed address (it never plays for visitors) and takes the posts Douyin's own page has already loaded. Until the first post shows, it checks again every 2 seconds instead of backing off up to 30. And at launch the feed has Douyin's page to itself.
- Includes 1.7.41 (its build stopped on the slow start): the bottom bar always stays put and answers the first tap; AI-made posts are hidden by default (Douyin's AI label, AI绘画, AI创作, AIGC, 数字人…).

## 1.7.41 — 2026-11-20
Snappier start, a bar that stays, no AI

- Faster start, aimed at the 2-minute wait. At launch the feed no longer shares Douyin's hidden page with searches for your interests: they piled requests onto the page while it was still loading, which made it reload itself again and again. The page now gets to bring its first batch alone; interest searches start once the first videos are up.
- Fixed: the bottom bar is always there and answers the first tap. It used to fade out over the feed and slide away on long lists, and a tap on the faded bar only woke it.
- No AI content: AI-made posts are hidden by default. That covers Douyin's own "made with AI" label, and captions or tags like AI绘画, AI创作, AIGC, AI生成 and 数字人. Settings can turn it off.

## 1.7.40 — 2026-11-20
Save the whole transcript

- New: Transcript (⋯ on a video) → "Save all lines to review": every short line said in the video, with its English, becomes a review card in one tap.

## 1.7.39 — 2026-11-20
Quicker checks

- Faster Check sources, more often: it now also tries each video's other servers (Douyin keeps several, and some turn away some networks), so it can answer in about a second instead of falling back to the slow check.

## 1.7.38 — 2026-11-20
Faster first load

- Faster start: the app remembers for a day that Douyin's feed address gives visitors nothing playable, so opening the app goes straight to the way that plays. Before, every launch wasted two downloads of 1.4 MB first.

## 1.7.37 — 2026-11-20
Tone trainer

- New: Tones · 声调 in Learn. Hear a beginner syllable and pick its tone (mā má mǎ mà); the character and its tone show after. Ten at a time, every tone in the mix.

## 1.7.36 — 2026-11-20
Today's goal

- New: a daily goal on the review card: today's reviews out of ten, with a bar (✓ 今天完成了 when done).
- Check sources, measured on the test phone: about half a second when the app already has posts at hand (it was about two minutes before 1.7.33).

## 1.7.35 — 2026-11-20
Verify I'm human works

- Includes 1.7.34, which didn't reach you (my new emulator test for it misbehaved, so its build was cut off): tapping 抖音 Douyin in Settings or "Verify I'm human" no longer crashes the app, and Check sources waits less on slow answers.
- The emulator tests now check every run that the Verify I'm human page is registered.

## 1.7.34 — 2026-11-20
Verify I'm human works

- Fixed: tapping 抖音 Douyin in Settings or "Verify I'm human" crashed the app (the check page was never registered with Android). It opens now, and the emulator tests open it every run.
- Faster: Check sources waits less on slow answers (aim: under 10 seconds).

## 1.7.33 — 2026-11-20
Faster checks, faster feed

- Faster: Check sources (Settings → Sources) answers in seconds, not about two minutes. It checks with posts the app already has, one small request to Douyin, and whether a video file loads, instead of fetching a whole new batch from scratch.
- Faster feed: without an account, Douyin's feed address sends 1.4 MB of posts that can't play. The app now stops asking it after two tries and goes straight to the way that works, which saves seconds every time more posts load.
- Includes 1.7.32: Your words can be filtered (to review, words, lines), and Lines you heard show pinyin.

## 1.7.32 — 2026-11-20
Find your words

- Your words (Learn) can be narrowed to the ones to review, single words, or whole lines.
- The emulator check for "More like this" now really runs on Douyin posts (it had been searching for something left over from Feed, finding nothing, and skipping).

## 1.7.31 — 2026-11-20
Fits small phones

- Fixed: 发现 Search's quick ways in (梗图, 搞笑, 学中文, 美食, 萌宠) all fit on small phones.

## 1.7.30 — 2026-11-20
How hard is it?

- New: captions say how hard they are for a learner (Easy, Medium or Hard, by how many of their words are beginner words), beside 词 Words. It shows once you've used Learn, pinyin or Easier Chinese first in this session.

## 1.7.29 — 2026-11-20
Measure words

- New: measure words. Nouns in 词 Words show which measure word they take (猫: 只 zhī, as in 一只猫; 人: 个 gè, 位 wèi).

## 1.7.28 — 2026-11-20
Two-way quiz

- Quiz: every other question goes the other way. Read the English, pick the Chinese characters. It practises reading as well as listening.

## 1.7.27 — 2026-11-20
Shortcuts

- New: hold the app's icon on your home screen for shortcuts: 复习 Review (starts a review at once), 学习 Learn and 发现 Search.

## 1.7.26 — 2026-11-20
Easier Chinese first

- New: Easier Chinese first (Learn, at the bottom). For You leans toward videos whose captions are mostly beginner words (HSK 1–3), so more of what you watch is within reach. Your taste still leads.
- Your word list and review cards show each word's HSK level.

## 1.7.25 — 2026-11-20
HSK levels

- New: HSK levels. Words show their level in the Chinese proficiency test lists (HSK 1 = first words), so you can tell beginner words from advanced ones.
- New: Learn the basics (Learn): tap HSK 1, 2 or 3 to add the next ten words of that level you haven't saved, commonest first, ready to review.

## 1.7.24 — 2026-11-20
Say the line back

- New: Say it in 词 Words: say a short line back and see how much of it came through, character by character (shadowing practice).
- Learn is tidier: review, the listening quiz and your words come first; the switches are at the bottom (and in 我 Me → 学习 Learn).

## 1.7.23 — 2026-11-20
Right readings

- Fixed: pinyin shows the everyday reading of common characters. 说 is shuō (it said shuì), 看 is kàn, 着 is zhe, 吗 is ma, and 都 and 还 are dōu and hái (they showed surnames). This applies everywhere pinyin shows: over captions, subtitles, comments and in 词 Words.

## 1.7.22 — 2026-11-20
Words waiting

- New: the 学习 Learn tab shows a red count of words due for review, as WeChat marks what's waiting (no notifications, as always).

## 1.7.21 — 2026-11-20
Subtitles out of the way

- New: an × on the English subtitle hides subtitles for that video only (when they cover a face or a recipe). They're back on the next video.

## 1.7.20 — 2026-11-20
Bigger subtitles

- New: Subtitle size (我 Me → 学习 Learn): normal, large or huge for the English of what's said.

## 1.7.19 — 2026-11-20
Transcripts

- New: Transcript (⋯ menu on a video): everything said in it so far, read off its subtitles, with English and tone-coloured pinyin. Tap a line for its words; tap its time to hear it again.
- New: Learn shows the lines you heard lately, in English.
- Includes 1.7.18 (its build stopped on a code-check warning, now fixed): quick ways in on 发现 Search (梗图 Memes, 搞笑, 学中文, 美食, 萌宠), and lines where you heard a word under it in 词 Words.

## 1.7.18 — 2026-11-20
Quick ways in

- New: 发现 Search opens with a row of quick ways in, as on WeChat's Discover page: 梗图 Memes, 搞笑 Funny, 学中文 Learn, 美食 Food, 萌宠 Pets.
- 词 Words shows lines where you heard each word in videos you watched.

## 1.7.17 — 2026-11-20
Tones in colour

- Pinyin in tone colours wherever it shows: over captions, under the English for what's said, and in comments (1 red, 2 orange, 3 green, 4 blue, neutral grey).
- 词 Words: "Save all new words" from a line in one tap.

## 1.7.16 — 2026-11-20
Speak it

- New: Say it, on review cards. Tap the mic, say the word, and the phone's Mandarin listener tells you whether it heard it right (or what it heard instead).
- New: hold the mic in Search to say it in Mandarin (tap still listens in your own language).

## 1.7.15 — 2026-11-20
Memes in one tap

- New: hold For You for one-tap moods: "Funny & memes · 搞笑" (more 搞笑, 梗图, 段子) and "Learning Chinese · 学中文" (more videos made for learners). Each lasts a few hours; Undo is right there.

## 1.7.14 — 2026-11-20
Cleaner subtitles

- Better: the English for what's said leaves out lines the reader isn't sure of, so fewer garbled translations.
- Learn: the "Watch and learn" cards show a few of their videos.

## 1.7.13 — 2026-11-20
Pause after each line

- New: Pause after each line (Learn, or 我 Me → 学习 Learn). The video stops when a subtitle line ends, with its English (and pinyin, if on) left up to read. Tap "go on", or the video, to carry on.

## 1.7.12 — 2026-11-20
我 Me, at a glance

- 我 Me opens with your Chinese at a glance: words saved, words to review, days in a row (tap for Learn).
- Screen and section titles in both languages: 发现 Search, 收藏 Library, 最近 Recent, 猜你喜欢 Based on what you like, 合集 Your mixes, 继续看 Continue.

## 1.7.11 — 2026-11-20
Hear that line again

- New: a replay button on the English subtitle: tap it to hear that line again from the start.
- New: 0.5× speed (⋯ menu → Speed), to catch every syllable.
- 1.7.10, measured: the longest screen hitches went from over a second to a fifth of a second.

## 1.7.10 — 2026-11-20
No more stutter when loading more

- Smoother: when the app needs more posts, it reloads the same hidden Douyin page instead of building a new one each time. Building it held up the screen for a moment, which you could see as a stutter in the video.
- Smoother: your word list and streak are saved in the background.
- 1.7.8–1.7.9 (measured on the test phone): videos start in about half a second while you swipe (was over two seconds). The slowest ones start in about three seconds (was over eight). At a normal pace most are ready before you reach them.

## 1.7.9 — 2026-11-20
Smoother

- Smoother: waking the hidden Douyin page for each request no longer builds a throwaway web view on the main thread (a hitch that could freeze the screen for a moment when the phone was busy).
- Smoother: lines heard in subtitles are saved in the background, not while the video draws.
- Caption English shows three lines at most over the video.

## 1.7.8 — 2026-11-19
Quicker video starts

- Faster: the app connects to the next videos' servers before you reach them, so swiping to one skips the slow first handshake with a server on the other side of the world.
- Faster: if a video server sends nothing at all, the app tries the next one after 3.5 seconds (it used to wait 8).

## 1.7.7 — 2026-11-19
Subtitles in time, a faster app

- Fixed: the English for what's said now keeps time with the speech. It checks four times a second instead of under twice, takes the English down the moment a line ends, and never shows the last line's English under a new one. Clips loop, so from the second time round each line's English appears exactly on time.
- Faster: the hidden Douyin web page the app uses to sign its requests is paused whenever it isn't fetching. Before, Douyin's website scripts kept running in the background all the time, taking processing power from the video you were watching.
- Faster: the video you swipe to gets the connection to itself for its first moment before the next ones start downloading. The translator also loads when the app opens, not on the first caption.
- Includes 1.7.6: Chinese on screen shows in English by default; review cards pick out the word in the line you met it in.

## 1.7.6 — 2026-11-19
English on screen by default

- Chinese on screen now shows in English by default: captions, tags, comments, trending and search results (Settings → Playback → Show Chinese in English turns it off; if you turn it off, it stays off).
- Review cards pick out the word in the line you met it in.

## 1.7.5 — 2026-11-19
Learning from what's said

- Better: Words you keep hearing now counts the subtitle lines read while you watch, not only captions (cleared with your history; nothing kept in private mode).
- New: comments show pinyin too when Pinyin over captions is on.
- Fixed: the feed's tabs (推荐 For You, 关注 Following) no longer get cut off.

## 1.7.4 — 2026-11-19
Words, closer up

- New: Today on Douyin · 热搜 in Learn: today's trending topics with pinyin and English. Tap one for its words, hold it to watch the videos.
- New: each character's own meaning under a word (火 fire · 锅 pot), to see how words are built.
- New: tap any word in the line at the top of 词 Words to hear it.
- New: an English search says the Chinese it searched for, with pinyin; tap it for its words.
- Fixed: in wide videos, the English for what's said sits just above the subtitle, not off to one side of the picture.
- New: a comments button on the side, with Douyin's count, opening straight into the comments.
- The feed's tabs carry Douyin's names (关注 Following, 推荐 For You); topic pages say what the topic means.

## 1.7.3 — 2026-11-19
Practise with your words

- New: Listening quiz (Learn): hear a word you saved, pick what it means. The words you know least come first.
- New: More videos with your words (Learn): For You leans toward captions that use the words you're learning.
- New: Share list (Learn → Your words): word, pinyin, meaning and where you met it, ready for Anki, a spreadsheet or notes.
- Backups now include your Chinese words and how well you know each.

## 1.7.2 — 2026-11-19
Subtitles in English from the first video

- Fixed: Translate what's said now reads subtitles with a model built into the app, so it works from the first video, offline, without waiting for Google to download it.
- Smaller download: 79 MB instead of 93 MB (64-bit phones only, which is every recent phone).
- Faster: on Wi-Fi the next four videos start downloading before you reach them; Search's topic rows appear at once from posts you've already seen.
- Words: meanings no longer cut off mid-word.

## 1.7.1 — 2026-11-19
Learn, ready to use

- Fixed: the dictionary behind 学习 Learn and 词 Words is read correctly (1.7.0 never reached you: the emulator test caught this first).
- New: save a whole line (a caption, a subtitle, a comment) as a card, with its English.
- New: "What's said in it" (⋯ menu): all of a video's on-screen text, as Douyin read it, word by word.
- New: a days-in-a-row streak on the review card.
- Tabs: 首页 Feed, 发现 Search, 学习 Learn, 收藏 Library, 我 Me (Settings), so all five fit on small phones.

## 1.7.0 — 2026-11-19
学习 Learn Chinese

- New tab, 学习 Learn: videos picked for learners (pinyin, everyday speech, idioms, tongue twisters, songs, poems), a dictionary (English, pinyin or 汉字), your word list, and flashcard review that spaces each word out as you learn it.
- New: Translate what's said. The subtitles in a video are read off the picture as they come up and shown in English just above them. Tap one to learn its words.
- New: 词 Words under every Chinese caption, and tapping any comment: the sentence word by word, with pinyin (tone colours), meanings, a voice, and a save button.
- New: Pinyin over captions (Learn or Settings → 学习 Learn), and "Words you keep hearing": the words that come up most in what you watch.
- New: memes and learning topics in Search (梗图, 表情包, 沙雕, 学中文…).
- No account needed: the app no longer asks you to sign in (Douyin accounts need a Chinese phone number). The button in Settings is only for Douyin's "verify you're human" check.
- Fixed: a video that won't start tries Douyin's other servers instead of being skipped.

## 1.6.0 — 2026-11-19
Chinese in English, everywhere

- New: Settings → Playback → Show Chinese in English now covers tags, sounds, comments, trending topics and search results, not just captions.
- New: search in English: "hot pot" searches Douyin for 火锅 (translated on the phone).
- New: Save this moment (⋯ menu): marks the line you want to hear again on the scrub bar; Saved → Moments plays each video from there.
- New: turned sideways, a video gets proper controls: back/forward 10 s, play/pause, speed, and your moments to jump to.
- Tabs show both languages: 首页 Feed, 发现 Search, 收藏 Library, 设置 Settings.
- Faster start: posts kept from last time whose video address has expired are left out.

## 1.5.2 — 2026-11-19
Fewer skipped videos, smarter For You

- Fixed: when one of Douyin's video servers refuses a clip (403/404), it now plays from the next mirror, or a fresh address, instead of being skipped.
- Better: video addresses that carry their expiry in the path are now trusted exactly that long.
- New: enjoy a video (watch it through, replay, like) and what Douyin shows beside it comes next in For You; "More like this" starts from it too.
- From Feed: taste chips in Search, voice search, and a "For you" sort for results.

## 1.5.1 — 2026-11-19
Less waiting signed out

- Faster: creator pages, trending topics and old posts stop waiting ~10 s on Douyin pages that show a signed-out visitor nothing (they're tried again once you sign in).
- Clearer: Settings → 抖音 Douyin says what works signed out (feed, comments, trending) and what shows less (search, creator pages).
- Clearing watch history also clears the posts search remembers, and says so.

## 1.5.0 — 2026-11-19
In English, all the way

- New: Settings → Playback → Translate captions automatically: every Chinese caption shows in English as it comes up.
- Better: creator pages add the creator's posts found among Douyin's related videos, and say plainly that signed out Douyin shows only some (one tap to sign in).
- Better: a saved Douyin clip downloads right away, even on mobile data (unless Data saver is on), since its address won't keep.
- Better: video addresses are trusted until the expiry they carry, so older posts play without a needless look-up.
- Battery: the hidden Douyin pages close a minute after you leave the app (and after background prefetching).
- Accessibility: screen readers hear "Like, 1.2万 likes".

## 1.4.2 — 2026-11-19
Trending that opens

- New: tapping a trending topic in Search shows that topic's own videos (from its Douyin page), signed in or not.
- New: signed out, search says where its results come from, with one tap to sign in for Douyin's full search.
- New: the trending list shows each topic's heat (1100万).
- New: the post's sound under the caption (♪ song – artist).
- New: creators' real pictures in every round avatar (Library, creator pages, Following).

## 1.4.1 — 2026-11-19
Search remembers

- Better: signed-out search remembers the last 3,000 Douyin posts you've scrolled past (on the phone, not in Private mode; Clear history clears it), so it finds more, even right after opening the app.
- Better: creator pages read the posts Douyin puts inside the page itself, so they show more than a couple.
- New: Sign out of Douyin from Settings → 抖音 Douyin.
- New: "More comments" pages through a post's comments, 20 at a time; like counts read the Douyin way.
- New: followed creators' real pictures in the Following row.
- Better: liked, history and saved posts whose video address has expired are looked up again through the post's own page if need be.

## 1.4.0 — 2026-11-19
Feels more like Douyin

- New: "See translation" under Chinese captions in the feed: English right there, tap again for the original.
- New: the heart shows the post's like count the way Douyin writes it (8,421 · 1.2万 · 3.4亿).
- New: creators' real profile pictures next to their names.
- Faster: Douyin's page opens when the app does, so the first posts don't wait for it, and each refill takes the page's second batch too.
- Faster: the Following tab no longer waits on every creator's page when you're signed out.
- Lighter: the hidden page used for creator pages closes after 2 minutes unused.
- New: Share (hold a post) sends the Douyin link to any app.
- Better: search lists posts containing every word you typed first, related ones after.

## 1.3.0 — 2026-11-19
Creator pages without signing in

- Fixed: a creator's page (swipe left on a post) and the Following tab work without a Douyin sign-in. Douyin refuses its post list to visitors, so the app opens the creator's own page out of sight and takes the posts it shows.
- Better: search stops waiting on ways Douyin keeps turning away (its search page shows a visitor a captcha), so results come in seconds instead of about 20.

## 1.2.3 — 2026-11-19
More in For You

- Better: For You takes turns between Douyin's home feed and its 精选 (featured) feed when it needs more, so each refill brings more posts and more variety.

## 1.2.2 — 2026-11-19
Comments that explain themselves

- Better: when Douyin doesn't share a post's comments, the Comments sheet says so (and that signing in usually helps) instead of "No comments to show".
- Fixed: the next feed batch no longer waits 7 seconds for a page nudge Douyin ignores.

## 1.2.1 — 2026-11-19
Faster scrolling

- Better: more For You loads by moving Douyin's page along (like pressing ↓ on the site) instead of opening it again each time, so the next batch comes sooner.
- Better: signed-out search asks for related videos from more of the matching posts, so it finds more.

## 1.2.0 — 2026-11-19
Trending on Douyin

- New: Search opens with Douyin's trending list (抖音热榜), live, top 10 numbered; tap one to search it.
- Better: Signed-out search leans on Douyin's related videos, which it does answer, so a search fills up (about 30 posts for a common topic in testing).

## 1.1.2 — 2026-11-19
Search that doesn't need a sign-in

- New: Signed out, Douyin won't search for you, so search is built from what Douyin does show a visitor: a hashtag's own video list when your search is a tag the feed has seen, Douyin's related videos for posts that match, its trending topics, and everything the feed has loaded.

## 1.1.1 — 2026-11-19
More posts, more ways to search

- Fixed: posts from Douyin's main feed were being skipped as "live" because of an empty field; they show now.
- New: Search also looks up Douyin videos through public web search (no sign-in needed), alongside what's already loaded.

## 1.1.0 — 2026-11-19
Search without signing in

- New: Search works without a Douyin sign-in. The app opens Douyin's own search page out of sight and takes the results it shows a visitor, plus matching posts the feed has already brought in. Signed in, it uses Douyin's search directly (more results, more pages).
- Better: The feed reads Douyin's newer answer format and falls back to what Douyin's own page loads, so it keeps working when Douyin changes its endpoints.
- Better: Each Douyin creator counts as their own place in the feed's spacing (no more one-post-at-a-time loading).

## 1.0.0 — 2026-11-18
抖音 Feed

- New: Douyin (China's TikTok) in one vertical swipe feed: its recommended videos and photo posts, re-ranked on your phone by what you watch.
- New: Translate: hold a post → Translate for the caption in English, and a Translate button in Comments (on the phone, after a one-time download).
- New: Search Douyin, open a creator's page (swipe left on a post), read a post's comments (hold → Comments), and share any Douyin link to the app to play it here.
- New: Settings → 抖音 Douyin (at the top) opens Douyin itself, to sign in or pass its "verify you're human" check if the feed stops loading.
