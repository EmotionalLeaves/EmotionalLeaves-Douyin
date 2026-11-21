package dev.dyfeed.ui.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import dev.dyfeed.domain.learn.Card
import dev.dyfeed.domain.learn.Dict
import dev.dyfeed.domain.learn.Entry
import dev.dyfeed.domain.learn.Grade
import dev.dyfeed.domain.learn.Pinyin
import dev.dyfeed.domain.learn.Srs
import dev.dyfeed.domain.learn.WordBook
import dev.dyfeed.ui.Routes
import dev.dyfeed.ui.theme.Accent

/**
 * 学习 Learn: Douyin videos picked for learners, a dictionary, your saved words and their review.
 * Words come from what you watch: "Words" under any caption breaks it apart and saves what you pick.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(nav: NavController, vm: LearnViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val context = LocalContext.current
    val captions by vm.captions.collectAsState(initial = emptyList())
    val cards by WordBook.cards.collectAsState()
    val days by WordBook.days.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val due = remember(cards, now) { Srs.due(cards, now) }
    var reviewing by remember { mutableStateOf(LearnOpen.review.also { LearnOpen.review = false } && Srs.due(WordBook.cards.value, System.currentTimeMillis()).isNotEmpty()) }
    var quizzing by remember { mutableStateOf(false) }
    var toning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var allWords by remember { mutableStateOf(false) }
    var listFilter by remember { mutableStateOf(WORD_FILTERS.first()) }
    var query by remember { mutableStateOf("") }
    val pinyinOn by LearnPrefs.pinyin.collectAsState()
    // Read the dictionary in as soon as the screen opens, so lookups are instant.
    val dictReady by produceState(Dict.ready != null) { Dict.get(context); value = true }

    AnimatedContent(if (reviewing) 1 else if (quizzing) 2 else if (toning) 3 else 0, label = "learnMode") { mode ->
        if (mode == 3) {
            TonesTrainer(onDone = { toning = false })
            return@AnimatedContent
        }
        if (mode == 1) {
            Review(onDone = { reviewing = false; now = System.currentTimeMillis() })
            return@AnimatedContent
        }
        if (mode == 2) {
            ListeningQuiz(onDone = { quizzing = false })
            return@AnimatedContent
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            // Kept to a few things (it had grown to eleven sections and "wasn't helpful"): your review,
            // three kinds of practice, the words from your videos, your list, and a dictionary.
            // Switches are behind the gear.
            item {
                dev.dyfeed.ui.common.ScreenHeader("学习 Learn", subtitle = "Chinese from the videos you watch", actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Learn settings") }
                })
            }
            // Review: words due now.
            item {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(dev.dyfeed.ui.theme.BrandGradient)
                        // Nothing saved yet: off to the feed, where the words are.
                        .clickable(enabled = due.isNotEmpty() || cards.isEmpty()) { if (cards.isEmpty()) nav.navigate(Routes.FEED) { launchSingleTop = true } else reviewing = true }
                        .padding(18.dp),
                ) {
                    Column {
                        Text(
                            when {
                                cards.isEmpty() -> "Start your word list"
                                due.isEmpty() -> "All caught up · 都复习完了"
                                else -> "Review ${due.size} word${if (due.size == 1) "" else "s"} · 复习"
                            },
                            color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            when {
                                cards.isEmpty() -> "Tap Words under a caption in the feed, then save the ones you want to learn."
                                due.isEmpty() -> "${cards.size} words saved. Next one is due ${whenNext(cards, now)}."
                                else -> "${cards.size} words saved. Each one you know comes back later and later."
                            },
                            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                        )
                        // Today's goal: ten reviews.
                        val doneToday by WordBook.reviewedToday.collectAsState()
                        if (cards.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Today: ${minOf(doneToday, DAILY_GOAL)} of $DAILY_GOAL reviewed" + if (doneToday >= DAILY_GOAL) " ✓ 今天完成了!" else "", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (doneToday.toFloat() / DAILY_GOAL).coerceAtMost(1f) },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        val streak = Srs.streak(days, WordBook.today(now))
                        if (streak > 0) Text(
                            "🔥 $streak day${if (streak == 1) "" else "s"} in a row · 连续$streak 天",
                            color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            // Practice: everything to do, side by side, one tap each.
            item { Section("Practice · 练习") }
            item {
                val hsk by produceState<dev.dyfeed.domain.learn.HskTable?>(dev.dyfeed.domain.learn.Hsk.ready) { value = dev.dyfeed.domain.learn.Hsk.get(context) }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PracticeTile("听", "Listening", if (cards.size >= 4) "Hear a word, pick its meaning" else "Save 4 words first", Modifier.weight(1f), enabled = cards.size >= 4) { quizzing = true }
                    PracticeTile("ā á", "Tones", "Hear a sound, pick its tone", Modifier.weight(1f)) { toning = true }
                    val table = hsk
                    val have = remember(cards) { cards.mapTo(HashSet()) { it.word } }
                    val level = remember(cards, table) { table?.let { t -> (1..6).firstOrNull { lv -> have.count { t.level(it) == lv } < t.count(lv) } } ?: 1 }
                    PracticeTile("HSK", "Basics", "+10 HSK $level words", Modifier.weight(1f), enabled = table != null && dictReady) {
                        val d = Dict.ready ?: return@PracticeTile
                        table?.next(level, have, 10)?.forEach { w -> d.lookup(w).firstOrNull()?.let { WordBook.add(it, example = null) } }
                    }
                }
            }
            // The words that keep coming up in what you watch, not yet saved.
            item {
                val savedWords = cards.mapTo(HashSet()) { it.word }
                val frequent by produceState<List<Pair<dev.dyfeed.domain.learn.Token, Int>>>(emptyList(), captions, cards.size, dictReady) {
                    if (captions.isEmpty()) return@produceState
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        dev.dyfeed.domain.learn.Frequent.words(Dict.get(context), captions, savedWords)
                    }
                }
                if (frequent.isNotEmpty()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Section("From your videos · 常见词") }
                            androidx.compose.material3.TextButton(
                                onClick = { frequent.forEach { (t, _) -> WordBook.add(t.entries.first(), example = captions.firstOrNull { t.text in it }) } },
                                modifier = Modifier.padding(end = 8.dp),
                            ) { Text("Save all") }
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(frequent, key = { it.first.text }) { (t, n) ->
                                val e = t.entries.first()
                                Column(
                                    Modifier.width(120.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f))
                                        .clickable { Speak.say(context, t.text) }.padding(12.dp),
                                ) {
                                    Text(e.pinyinMarks, color = toneColor(Pinyin.tones(e.pinyin).firstOrNull() ?: 5), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    Text(t.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text(e.english, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.height(32.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("×$n", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.weight(1f))
                                        Text(
                                            "+ Save",
                                            color = Accent,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { WordBook.add(e, example = captions.firstOrNull { t.text in it }) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (cards.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Section("Your words · 生词本 (${cards.size})") }
                        // Out to Anki, a spreadsheet or notes: word, pinyin, meaning, where you met it.
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "My Chinese words")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, dev.dyfeed.domain.learn.exportWords(cards))
                                context.startActivity(android.content.Intent.createChooser(send, "Share your words"))
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Share list") }
                    }
                }
                if (allWords) item {
                    // Narrow the list down: what's due, single words, whole lines.
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(WORD_FILTERS) { f ->
                            androidx.compose.material3.FilterChip(selected = f == listFilter, onClick = { listFilter = f }, label = { Text(f) })
                        }
                    }
                }
                val allShown = cards.sortedByDescending { it.addedAt }.filter { c ->
                    when (listFilter) {
                        "To review" -> c.due <= now
                        "Words" -> c.word.length <= 4
                        "Lines" -> c.word.length > 4
                        else -> true
                    }
                }
                // The latest few; the rest on request (a long list here buried everything else).
                val shownCards = if (allWords) allShown else allShown.take(WORDS_PREVIEW)
                items(shownCards, key = { it.word }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Speak.say(context, c.word) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.word,
                            fontSize = if (c.word.length > 4) 15.sp else 22.sp,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(if (c.word.length > 4) 128.dp else 88.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(Pinyin.marks(c.pinyin), color = toneColor(Pinyin.tones(c.pinyin).firstOrNull() ?: 5), style = MaterialTheme.typography.labelLarge)
                            Text(c.english, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        val lv = dev.dyfeed.domain.learn.Hsk.ready?.level(c.word)
                        Text((lv?.let { if (it >= 7) "HSK 7–9 · " else "HSK $it · " } ?: "") + strength(c), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        IconButton(onClick = { WordBook.remove(c.word) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${c.word}", tint = Color.White.copy(alpha = 0.5f)) }
                    }
                }
                if (allShown.size > WORDS_PREVIEW) item {
                    androidx.compose.material3.TextButton(onClick = { allWords = !allWords }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(if (allWords) "Show fewer" else "See all ${allShown.size}")
                    }
                }
            }
            item { Section("Look up a word · 词典") }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(if (dictReady) "English, pinyin or 汉字" else "Getting the dictionary ready…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (query.isNotBlank()) {
                item {
                    val found by produceState<List<Entry>?>(null, query, dictReady) {
                        value = null
                        kotlinx.coroutines.delay(250)
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Dict.get(context).search(query, 30) }
                    }
                    val savedWords = cards.mapTo(HashSet()) { it.word }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        when (val f = found) {
                            null -> Text("Looking…", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(12.dp))
                            else -> if (f.isEmpty()) Text("Nothing found", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(12.dp))
                            else f.groupBy { it.simplified }.forEach { (word, entries) ->
                                WordRow(word, entries, saved = word in savedWords, onSpeak = { Speak.say(context, word) }) {
                                    if (word in savedWords) WordBook.remove(word) else WordBook.add(entries.first(), example = null)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Dictionary: CC-CEDICT (MDBG, CC BY-SA 4.0). HSK lists: Complete HSK Vocabulary (drkameleon, MIT). Voice: your phone's text-to-speech. Translation: Google ML Kit, on the phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
    if (showSettings) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Learn settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Column {
                // Pinyin over the feed's captions.
                run {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pinyin over captions", style = MaterialTheme.typography.titleSmall)
                            Text("Every Chinese caption in the feed shows how it's said", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(checked = pinyinOn, onCheckedChange = LearnPrefs::setPinyin)
                    }
                }
                run {
                    val autoPauseOn by LearnPrefs.autoPause.collectAsState()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pause after each line", style = MaterialTheme.typography.titleSmall)
                            Text("Videos stop when a subtitle line ends, to read it before going on", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(checked = autoPauseOn, onCheckedChange = LearnPrefs::setAutoPause)
                    }
                }
                run {
                    val easyOn by LearnPrefs.easy.collectAsState()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Easier Chinese first", style = MaterialTheme.typography.titleSmall)
                            Text("For You leans toward captions made of beginner words (HSK 1–3)", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(checked = easyOn, onCheckedChange = { on ->
                            LearnPrefs.setEasy(on)
                            // Its word lists have to be in memory for For You to use them.
                            if (on) kotlinx.coroutines.MainScope().launch { Dict.get(context); dev.dyfeed.domain.learn.Hsk.get(context) }
                        })
                    }
                }
                run {
                    val boostOn by LearnPrefs.boost.collectAsState()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("More videos with your words", style = MaterialTheme.typography.titleSmall)
                            Text("For You leans toward captions that use words you're learning", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(checked = boostOn, onCheckedChange = LearnPrefs::setBoost)
                    }
                }

                }
            }
        }
    }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
}

/** Flashcards for the words due: the word, tap to see how it's said and what it means, then how well you knew it. */
@Composable
private fun Review(onDone: () -> Unit) {
    val context = LocalContext.current
    val start = remember { System.currentTimeMillis() }
    val queue = remember { Srs.due(WordBook.cards.value, start).take(SESSION).toMutableList() }
    var index by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(0) }
    val card: Card? = queue.getOrNull(index)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("复习 Review · ${minOf(index + 1, queue.size)}/${queue.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Stop reviewing") }
        }
        if (card == null) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.School, contentDescription = null, tint = Accent, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("做得好！Well done", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("$right of ${queue.size} known", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Button(onClick = onDone) { Text("Back to Learn") }
            }
            return@Column
        }
        LaunchedEffect(card.word) { Speak.say(context, card.word) }
        // Say it: the phone's Mandarin recogniser listens, and says whether it heard this word.
        var heard by remember(card.word) { mutableStateOf<String?>(null) }
        val sayIt = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
            val said = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS).orEmpty()
            heard = when {
                said.isEmpty() -> null
                said.any { it.replace(Regex("""[\s，。！？,.!?]"""), "").contains(card.word) } -> "✓ 对了! That sounded right"
                else -> "Heard: ${said.first()}"
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26222E), Color(0xFF17151C))))
                .clickable { shown = true }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (shown) Text(Pinyin.marks(card.pinyin), color = toneColor(Pinyin.tones(card.pinyin).firstOrNull() ?: 5), fontSize = 22.sp)
            if (shown) dev.dyfeed.domain.learn.Hsk.ready?.level(card.word)?.let { lv ->
                Text(if (lv >= 7) "HSK 7–9" else "HSK $lv", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
            }
            Text(
                card.word,
                fontSize = when { card.word.length <= 3 -> 64.sp; card.word.length <= 8 -> 40.sp; else -> 28.sp },
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row {
                IconButton(onClick = { Speak.say(context, card.word) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear it again") }
                IconButton(onClick = {
                    runCatching {
                        sayIt.launch(
                            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说: ${card.word}"),
                        )
                    }
                }) { Icon(Icons.Filled.Mic, contentDescription = "Say it yourself") }
            }
            heard?.let { Text(it, color = if (it.startsWith("✓")) Color(0xFF34D399) else Color(0xFFFBBF24), style = MaterialTheme.typography.labelLarge) }
            if (shown) {
                Text(card.english, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                card.example?.let {
                    Spacer(Modifier.height(14.dp))
                    // Where you met it, the word picked out.
                    val example = androidx.compose.ui.text.buildAnnotatedString {
                        append("“")
                        var rest = it
                        while (rest.isNotEmpty()) {
                            val at = rest.indexOf(card.word)
                            if (at < 0 || card.word.isEmpty()) { append(rest); break }
                            append(rest.substring(0, at))
                            withStyle(androidx.compose.ui.text.SpanStyle(color = Accent, fontWeight = FontWeight.Bold)) { append(card.word) }
                            rest = rest.substring(at + card.word.length)
                        }
                        append("”")
                    }
                    Text(example, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                Text("Tap to see the answer", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (shown) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(Grade.AGAIN, "Again", Color(0xFFF87171)),
                    Triple(Grade.HARD, "Hard", Color(0xFFFBBF24)),
                    Triple(Grade.GOOD, "Good", Color(0xFF34D399)),
                    Triple(Grade.EASY, "Easy", Color(0xFF6BA8FF)),
                ).forEach { (g, label, color) ->
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.22f))
                            .clickable {
                                WordBook.answer(card, g)
                                if (g != Grade.AGAIN) right++
                                shown = false
                                index++
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = color, fontWeight = FontWeight.Bold) }
                }
            }
        } else Spacer(Modifier.height(52.dp))
    }
}

/** Hear a saved word, pick its meaning among four. Ten questions, the words you know least first. */
@Composable
private fun ListeningQuiz(onDone: () -> Unit) {
    val context = LocalContext.current
    val questions = remember { dev.dyfeed.domain.learn.Quiz.questions(WordBook.cards.value) }
    var index by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var right by remember { mutableStateOf(0) }
    val q = questions.getOrNull(index)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("听力 Quiz · ${minOf(index + 1, questions.size)}/${questions.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Stop the quiz") }
        }
        if (q == null) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (right == questions.size) "全对！All right" else "$right of ${questions.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Button(onClick = onDone) { Text("Back to Learn") }
            }
            return@Column
        }
        // Every other question the other way round: read the English, pick the characters.
        val reverse = index % 2 == 1
        LaunchedEffect(index) { if (!reverse) Speak.say(context, q.answer.word) }
        LaunchedEffect(index, picked) { if (reverse && picked != null) Speak.say(context, q.answer.word) }
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (reverse) {
                Text(q.answer.english, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3)
                Spacer(Modifier.height(8.dp))
                if (picked == null) Text("Which is it in Chinese?", color = Color.White.copy(alpha = 0.6f))
            } else {
                IconButton(onClick = { Speak.say(context, q.answer.word) }, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Accent.copy(alpha = 0.18f))) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear it again", tint = Accent, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(14.dp))
                if (picked == null) Text("What does it mean?", color = Color.White.copy(alpha = 0.6f))
            }
            if (picked != null) {
                Text(q.answer.word, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(Pinyin.marks(q.answer.pinyin), color = toneColor(Pinyin.tones(q.answer.pinyin).firstOrNull() ?: 5), fontSize = 18.sp)
            }
        }
        q.choices.forEach { c ->
            val isAnswer = c.word == q.answer.word
            val bg = when {
                picked == null -> Color.White.copy(alpha = 0.07f)
                isAnswer -> Color(0xFF34D399).copy(alpha = 0.3f)
                picked == c.word -> Color(0xFFF87171).copy(alpha = 0.3f)
                else -> Color.White.copy(alpha = 0.04f)
            }
            Text(
                if (reverse) c.word else c.english,
                fontSize = if (reverse) 24.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                    .clickable(enabled = picked == null) { picked = c.word; if (isAnswer) right++ }
                    .padding(16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(
            onClick = { picked = null; index++ },
            enabled = picked != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(if (index + 1 >= questions.size) "Finish" else "Next") }
    }
}

/** Ten syllables said aloud; pick tone 1–4 for each (shown on the syllable itself: mā má mǎ mà). */
@Composable
private fun TonesTrainer(onDone: () -> Unit) {
    val context = LocalContext.current
    val rounds by produceState<List<dev.dyfeed.domain.learn.TonePractice.Round>?>(null) {
        value = dev.dyfeed.domain.learn.TonePractice.rounds(Dict.get(context), dev.dyfeed.domain.learn.Hsk.get(context))
    }
    var index by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var right by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        val list = rounds
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("声调 Tones · ${minOf(index + 1, list?.size ?: 0)}/${list?.size ?: 0}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Stop") }
        }
        if (list == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { dev.dyfeed.ui.common.BrandSpinner() }; return@Column }
        val r = list.getOrNull(index)
        if (r == null) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("$right of ${list.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Button(onClick = onDone) { Text("Back to Learn") }
            }
            return@Column
        }
        LaunchedEffect(index) { Speak.say(context, r.char) }
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconButton(onClick = { Speak.say(context, r.char) }, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Accent.copy(alpha = 0.18f))) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear it again", tint = Accent, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (picked != null) {
                Text(r.char, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(Pinyin.marks(r.syllable + r.tone), color = toneColor(r.tone), fontSize = 22.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { t ->
                val bg = when {
                    picked == null -> Color.White.copy(alpha = 0.07f)
                    t == r.tone -> Color(0xFF34D399).copy(alpha = 0.3f)
                    t == picked -> Color(0xFFF87171).copy(alpha = 0.3f)
                    else -> Color.White.copy(alpha = 0.04f)
                }
                Column(
                    Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                        .clickable(enabled = picked == null) { picked = t; if (t == r.tone) right++ },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(Pinyin.marks(r.syllable + t), color = toneColor(t), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("$t", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(onClick = { picked = null; index++ }, enabled = picked != null, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (index + 1 >= list.size) "Finish" else "Next")
        }
    }
}

/** One kind of practice: a big symbol, its name, what it does. */
@Composable
private fun PracticeTile(symbol: String, title: String, note: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (enabled) 0.08f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick).padding(12.dp),
    ) {
        Text(symbol, color = if (enabled) Accent else Color.White.copy(alpha = 0.35f), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f))
        Text(note, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 2, minLines = 2)
    }
}

/** Words shown in the list before "See all". */
private const val WORDS_PREVIEW = 5

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
}

private fun strength(c: Card): String = when {
    c.reps == 0 -> "new"
    c.intervalDays < 3 -> "learning"
    c.intervalDays < 21 -> "getting there"
    else -> "known"
}

private fun whenNext(cards: List<Card>, now: Long): String {
    val next = cards.minOfOrNull { it.due } ?: return "—"
    val mins = (next - now) / 60_000
    return when {
        mins < 60 -> "in ${mins.coerceAtLeast(1)} min"
        mins < 24 * 60 -> "in ${mins / 60} h"
        else -> "in ${mins / (24 * 60)} days"
    }
}

private const val SESSION = 20
/** A day's goal: a handful of minutes, enough to keep words coming back. */
private const val DAILY_GOAL = 10
private val WORD_FILTERS = listOf("All", "To review", "Words", "Lines")

