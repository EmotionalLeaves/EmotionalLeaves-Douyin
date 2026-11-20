package dev.dyfeed.ui.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
@Composable
fun LearnScreen(nav: NavController, vm: LearnViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val context = LocalContext.current
    val captions by vm.captions.collectAsState(initial = emptyList())
    val cards by WordBook.cards.collectAsState()
    val days by WordBook.days.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val due = remember(cards, now) { Srs.due(cards, now) }
    var reviewing by remember { mutableStateOf(false) }
    var quizzing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val pinyinOn by LearnPrefs.pinyin.collectAsState()
    // Read the dictionary in as soon as the screen opens, so lookups are instant.
    val dictReady by produceState(Dict.ready != null) { Dict.get(context); value = true }

    AnimatedContent(if (reviewing) 1 else if (quizzing) 2 else 0, label = "learnMode") { mode ->
        if (mode == 1) {
            Review(onDone = { reviewing = false; now = System.currentTimeMillis() })
            return@AnimatedContent
        }
        if (mode == 2) {
            ListeningQuiz(onDone = { quizzing = false })
            return@AnimatedContent
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item { dev.dyfeed.ui.common.ScreenHeader("学习 Learn", subtitle = "Chinese from the videos you watch") }
            // Review: words due now.
            item {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(dev.dyfeed.ui.theme.BrandGradient)
                        .clickable(enabled = due.isNotEmpty()) { reviewing = true }
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
                        val streak = Srs.streak(days, WordBook.today(now))
                        if (streak > 0) Text(
                            "🔥 $streak day${if (streak == 1) "" else "s"} in a row · 连续$streak 天",
                            color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            // Listening: hear a word, pick what it means.
            if (cards.size >= 4) item {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable { quizzing = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Listening quiz · 听力", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Hear a word you saved, pick what it means", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                    }
                }
            }
            // Pinyin over the feed's captions.
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pinyin over captions", style = MaterialTheme.typography.titleSmall)
                        Text("Every Chinese caption in the feed shows how it's said", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(checked = pinyinOn, onCheckedChange = LearnPrefs::setPinyin)
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
                        Section("Words you keep hearing · 常见词")
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
            item { Section("Watch and learn · 看视频学中文") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TOPICS.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (zh, en, hue) -> TopicCard(zh, en, hue, Modifier.weight(1f)) { nav.navigate(Routes.tag(zh)) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item { Section("Dictionary · 词典") }
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
                items(cards.sortedByDescending { it.addedAt }, key = { it.word }) { c ->
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
                        Text(strength(c), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        IconButton(onClick = { WordBook.remove(c.word) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${c.word}", tint = Color.White.copy(alpha = 0.5f)) }
                    }
                }
            }
            item {
                Text(
                    "Dictionary: CC-CEDICT (MDBG, CC BY-SA 4.0). Voice: your phone's text-to-speech. Translation: Google ML Kit, on the phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(20.dp),
                )
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
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26222E), Color(0xFF17151C))))
                .clickable { shown = true }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (shown) Text(Pinyin.marks(card.pinyin), color = toneColor(Pinyin.tones(card.pinyin).firstOrNull() ?: 5), fontSize = 22.sp)
            Text(
                card.word,
                fontSize = when { card.word.length <= 3 -> 64.sp; card.word.length <= 8 -> 40.sp; else -> 28.sp },
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { Speak.say(context, card.word) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear it again") }
            if (shown) {
                Text(card.english, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                card.example?.let {
                    Spacer(Modifier.height(14.dp))
                    Text("“$it”", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            Text("听力 Listening · ${minOf(index + 1, questions.size)}/${questions.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
        LaunchedEffect(index) { Speak.say(context, q.answer.word) }
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconButton(onClick = { Speak.say(context, q.answer.word) }, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Accent.copy(alpha = 0.18f))) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear it again", tint = Accent, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (picked != null) {
                Text(q.answer.word, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(Pinyin.marks(q.answer.pinyin), color = toneColor(Pinyin.tones(q.answer.pinyin).firstOrNull() ?: 5), fontSize = 18.sp)
            } else Text("What does it mean?", color = Color.White.copy(alpha = 0.6f))
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
                c.english,
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

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun TopicCard(zh: String, en: String, hue: Float, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color.hsl(hue, 0.55f, 0.36f), Color.hsl((hue + 35f) % 360f, 0.6f, 0.2f))))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(zh, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(en, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
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

/** Douyin searches that bring up videos made for learners (and plain everyday speech). */
private val TOPICS = listOf(
    Triple("学中文", "Chinese for learners", 338f),
    Triple("汉语拼音", "Pinyin and tones", 268f),
    Triple("日常口语", "Everyday spoken Chinese", 205f),
    Triple("汉字", "How characters work", 24f),
    Triple("成语故事", "Idiom stories", 290f),
    Triple("绕口令", "Tongue twisters", 12f),
    Triple("儿歌", "Kids' songs: simple words", 190f),
    Triple("中文歌曲", "Chinese songs", 318f),
    Triple("古诗词", "Classic poems", 248f),
    Triple("街头采访", "Street interviews", 352f),
)
