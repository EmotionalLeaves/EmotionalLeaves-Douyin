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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dyfeed.domain.learn.Dict
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
    val tokens by produceState<List<Token>?>(null, text) { value = Dict.get(context).segment(text) }
    val saved by WordBook.cards.collectAsState()
    val savedWords = saved.mapTo(HashSet()) { it.word }
    val speechProblem by Speak.problem.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("词 Words", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { Speak.say(context, text) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listen")
                }
            }
            speechProblem?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFBBF24)) }
            val list = tokens
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { dev.dyfeed.ui.common.BrandSpinner() }
                return@Column
            }
            // The sentence, pinyin over each word (tone-coloured).
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { t -> Ruby(t) }
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
            LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
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
        Text(word, fontSize = 26.sp, color = Color.White, modifier = Modifier.width(92.dp))
        Column(Modifier.weight(1f)) {
            entries.take(3).forEach { e ->
                Text(e.pinyinMarks, color = toneColor(Pinyin.tones(e.pinyin).firstOrNull() ?: 5), style = MaterialTheme.typography.labelLarge)
                Text(e.english, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 3)
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

/** Pinyin for a whole line, word by word (for the feed's pinyin line); null until the dictionary is in. */
fun pinyinLine(text: String): String? {
    val d = Dict.ready ?: return null
    return d.segment(text).joinToString(" ") { t -> t.entries.firstOrNull()?.pinyinMarks?.replace(" ", "") ?: t.text.trim() }
        .replace(Regex("""\s+"""), " ").trim()
}
