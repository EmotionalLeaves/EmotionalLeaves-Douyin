package dev.dyfeed.ui.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dyfeed.domain.learn.Dict
import dev.dyfeed.domain.learn.Hsk
import dev.dyfeed.domain.learn.Entry
import dev.dyfeed.domain.learn.Pinyin
import dev.dyfeed.domain.learn.Token
import dev.dyfeed.domain.learn.WordBook

/** Tone colours, as many learners' apps use them: 1 red, 2 orange, 3 green, 4 blue, neutral grey. */
fun toneColor(tone: Int): Color = when (tone) {
    1 -> Color(0xFFFF6B6B)
    2 -> Color(0xFFFFB547)
    3 -> Color(0xFF5BD68A)
    4 -> Color(0xFF6BA8FF)
    else -> Color(0xFFB8B8C0)
}

/**
 * A caption (or any Chinese) taken apart: the sentence with pinyin over each word, then every word
 * with its pinyin, meaning, a button to hear it, and one to save it to your words.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordsSheet(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tokens by produceState<List<Token>?>(null, text) { Hsk.get(context); value = Dict.get(context).segment(text) }
    val saved by WordBook.cards.collectAsState()
    val savedWords = saved.mapTo(HashSet()) { it.word }
    val speechProblem by Speak.problem.collectAsState()
    // Open all the way: every word of the line in view, not half of them.
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            // Say the line back (shadowing): the phone listens in Mandarin and scores how much came through.
            var shadow by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableStateOf<Pair<String, Int>?>(null) }
            val sayLine = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
                val said = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS).orEmpty()
                shadow = dev.dyfeed.domain.learn.Shadowing.best(text, said)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("词 Words", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (text.trim().length <= 30) TextButton(onClick = {
                    runCatching {
                        sayLine.launch(
                            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说: ${text.trim()}"),
                        )
                    }
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Say it")
                }
                TextButton(onClick = { Speak.say(context, text) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listen")
                }
            }
            speechProblem?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24)) }
            shadow?.let { (heard, score) ->
                Text(
                    (if (score >= 85) "✓ $score% — 很好! " else "$score% · ") + "Heard: $heard",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (score >= 85) Color(0xFF34D399) else if (score >= 50) Color(0xFFFBBF24) else Color(0xFFF87171),
                )
            }
            val list = tokens
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { dev.dyfeed.ui.common.BrandSpinner() }
                return@Column
            }
            Dict.failure?.let { Text("The dictionary couldn't be read ($it)", color = Color(0xFFFBBF24), style = MaterialTheme.typography.labelMedium) }
            // The sentence, pinyin over each word (tone-coloured).
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Tap a word in the line to hear it.
                list.forEach { t -> Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = t.known) { Speak.say(context, t.text) }) { Ruby(t) } }
            }
            // The whole line in English, and a card for it (short lines only: one you can learn whole).
            val english by produceState<String?>(null, text) { value = dev.dyfeed.domain.lang.ChineseToEnglish.translate(text).getOrNull() }
            english?.let { en ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(en, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.weight(1f))
                    if (text.trim().length <= 40) {
                        val have = text.trim() in savedWords
                        TextButton(onClick = { if (have) WordBook.remove(text.trim()) else WordBook.addSentence(text, list, en) }) {
                            Text(if (have) "Saved ✓" else "Save line")
                        }
                    }
                }
            }
            // Every word of the line you haven't saved, in one tap (the one-character particles left out).
            val unsaved = list.filter { it.known && it.text.length >= 2 && it.text !in savedWords }.distinctBy { it.text }
            if (unsaved.size >= 2) TextButton(onClick = { unsaved.forEach { t -> WordBook.add(t.entries.first(), example = text) } }) {
                Text("Save all ${unsaved.size} new words")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(list.filter { it.known }.distinctBy { it.text }) { t ->
                    WordRow(t.text, t.entries, saved = t.text in savedWords, onSpeak = { Speak.say(context, t.text) }) {
                        if (t.text in savedWords) WordBook.remove(t.text) else WordBook.add(t.entries.first(), example = text)
                    }
                }
            }
            Text(
                "Dictionary: CC-CEDICT (CC BY-SA 4.0). Voice: your phone's text-to-speech.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A word with its pinyin above it, coloured by tone. */
@Composable
fun Ruby(t: Token, big: Boolean = false) {
    val e = t.entries.firstOrNull()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (e != null) {
            Row {
                val syllables = e.pinyin.split(' ')
                syllables.forEachIndexed { i, s ->
                    Text(
                        Pinyin.marks(s) + if (i < syllables.lastIndex) " " else "",
                        color = toneColor(Pinyin.tones(s).first()),
                        fontSize = if (big) 14.sp else 11.sp,
                    )
                }
            }
        } else Spacer(Modifier.height(if (big) 18.dp else 15.dp))
        Text(t.text, fontSize = if (big) 30.sp else 20.sp, color = if (e != null) Color.White else Color.White.copy(alpha = 0.55f))
    }
}

@Composable
fun WordRow(word: String, entries: List<Entry>, saved: Boolean, onSpeak: () -> Unit, onToggleSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onSpeak).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(if (word.length <= 2) 72.dp else 104.dp)) {
            Text(word, fontSize = 26.sp, color = Color.White)
            // Its HSK level: how early learners meet it (1 = first words).
            Hsk.ready?.level(word)?.let { lv ->
                Text(if (lv >= 7) "HSK 7–9" else "HSK $lv", style = MaterialTheme.typography.labelSmall, color = if (lv <= 2) Color(0xFF34D399) else if (lv <= 4) Color(0xFFFFB547) else Color.White.copy(alpha = 0.5f))
            }
        }
        Column(Modifier.weight(1f)) {
            entries.take(3).forEach { e ->
                Text(e.pinyinMarks, color = toneColor(Pinyin.tones(e.pinyin).firstOrNull() ?: 5), style = MaterialTheme.typography.labelLarge)
                Text(e.english, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // Each character's own meaning: 火 fire · 锅 pot (how the word is built).
            if (word.length in 2..4) Dict.ready?.let { d ->
                val parts = word.map { c -> d.lookup(c.toString()).firstOrNull()?.let { e -> "$c ${e.english.substringBefore(';').substringBefore('(').trim().take(18)}" } }
                if (parts.all { it != null }) Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD58A).copy(alpha = 0.8f))
            }
            // Where you heard it: lines from videos you watched (read off their subtitles).
            val heardLines by dev.dyfeed.domain.learn.Heard.lines.collectAsState()
            if (word.length >= 2) heardLines.asReversed().filter { word in it }.distinct().take(2).forEach { line ->
                Text("“${line.take(40)}”", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (entries.firstOrNull()?.traditional?.let { it != word } == true) {
                Text("Traditional: ${entries.first().traditional}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
            }
        }
        IconButton(onClick = onSpeak) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Hear $word") }
        IconButton(onClick = onToggleSave) {
            Icon(
                if (saved) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                contentDescription = if (saved) "Remove $word from your words" else "Save $word to your words",
                tint = if (saved) dev.dyfeed.ui.theme.Accent else Color.White,
            )
        }
    }
}

/** [pinyinLine] with each syllable in its tone's colour; null until the dictionary is in. */
fun pinyinColored(text: String): androidx.compose.ui.text.AnnotatedString? {
    val d = Dict.ready ?: return null
    return androidx.compose.ui.text.buildAnnotatedString {
        var first = true
        d.segment(text).forEach { t ->
            val e = t.entries.firstOrNull()
            if (e == null) {
                val other = t.text.trim()
                if (other.isNotEmpty()) { if (!first) append(" "); append(other); first = false }
                return@forEach
            }
            if (!first) append(" ")
            first = false
            e.pinyin.split(' ').forEach { syl ->
                pushStyle(androidx.compose.ui.text.SpanStyle(color = toneColor(Pinyin.tones(syl).first())))
                append(Pinyin.marks(syl))
                pop()
            }
        }
    }
}

/** Pinyin for a whole line, word by word (for the feed's pinyin line); null until the dictionary is in. */
fun pinyinLine(text: String): String? {
    val d = Dict.ready ?: return null
    return d.segment(text).joinToString(" ") { t -> t.entries.firstOrNull()?.pinyinMarks?.replace(" ", "") ?: t.text.trim() }
        .replace(Regex("""\s+"""), " ").trim()
}
