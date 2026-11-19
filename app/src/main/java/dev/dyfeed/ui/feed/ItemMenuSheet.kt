package dev.dyfeed.ui.feed

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.SecureFlagPolicy
import dev.dyfeed.domain.model.MediaItem
import dev.dyfeed.ui.common.openExternal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemMenuSheet(
    item: MediaItem,
    vm: FeedViewModel,
    onDismiss: () -> Unit,
    /** Opens an endless feed built around this post (its creator and tags). */
    onMoreLikeThis: (() -> Unit)? = null,
    /** Opens a tag's or creator's page ("Why you're seeing this"). */
    onOpenPage: ((String) -> Unit)? = null,
    /** Downloads this and the next posts of the feed to watch offline. */
    onForTheRoad: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var pickTag by remember { mutableStateOf<String?>(null) } // "block" while picking a tag to hide
    val creator = remember(item.key) { Display.creator(item) }
    val tags = remember(item.key) { Display.tags(item, max = 24) }
    val source = Display.source(item, vm.sourceName(item.sourceId))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.Inherit),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            val mode = pickTag
            if (mode == "collect") {
                CollectionPicker(item, vm, onBack = { pickTag = null }, onDone = onDismiss)
                return@Column
            }
            if (mode == "translate") {
                ListItem(
                    headlineContent = { Text("Translation", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { pickTag = null },
                )
                HorizontalDivider()
                val original = listOfNotNull(item.title, item.tags.takeIf { it.isNotEmpty() }?.joinToString("  ") { "#$it" }).joinToString("\n")
                val english by androidx.compose.runtime.produceState<Result<String>?>(initialValue = null, item.key) {
                    value = dev.dyfeed.domain.lang.ChineseToEnglish.translate(original)
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    val r = english
                    when {
                        r == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            dev.dyfeed.ui.common.BrandSpinner()
                            Spacer(Modifier.width(12.dp))
                            Text(if (dev.dyfeed.domain.lang.ChineseToEnglish.ready) "Translating…" else "Getting the translator ready (first time only, about 30 MB)…", style = MaterialTheme.typography.bodyMedium)
                        }
                        r.isSuccess -> Text(r.getOrThrow(), style = MaterialTheme.typography.bodyLarge)
                        else -> Text("Couldn't translate: ${r.exceptionOrNull()?.message ?: "no connection for the first download?"}", color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }
            if (mode == "comments") {
                var translateOn by remember { mutableStateOf(dev.dyfeed.domain.lang.ChineseToEnglish.auto) }
                ListItem(
                    headlineContent = { Text("评论 · Comments", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                    trailingContent = {
                        androidx.compose.material3.TextButton(onClick = { translateOn = !translateOn }) {
                            Text(if (translateOn) "Original" else "Translate")
                        }
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { pickTag = null },
                )
                HorizontalDivider()
                // Page by page: the first 20, then "More comments" for the next.
                var list by remember(item.key) { mutableStateOf<List<dev.dyfeed.source.douyin.DouyinAdapter.Comment>?>(null) }
                var failure by remember(item.key) { mutableStateOf<Throwable?>(null) }
                var next by remember(item.key) { mutableStateOf<Long?>(null) }
                var loadingMore by remember(item.key) { mutableStateOf(false) }
                val commentScope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.compose.runtime.LaunchedEffect(item.key) {
                    vm.comments(item).onSuccess { (page, more) -> list = page; next = more }.onFailure { failure = it; list = emptyList() }
                }
                val english by androidx.compose.runtime.produceState<List<String?>>(initialValue = emptyList(), list, translateOn) {
                    value = list?.takeIf { translateOn }?.let { l -> dev.dyfeed.domain.lang.ChineseToEnglish.translateAll(l.map { it.body }) } ?: emptyList()
                }
                val shown = list
                val failed = failure
                when {
                    shown == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { dev.dyfeed.ui.common.BrandSpinner() }
                    shown.isEmpty() -> Text(
                        when {
                            failed == null -> "No comments yet"
                            !dev.dyfeed.source.douyin.DouyinWeb.isSignedIn() -> "Douyin didn't share the comments this time (${failed.message?.removePrefix("Douyin: ") ?: "no answer"}). Try again in a moment."
                            else -> "Couldn't load comments: ${failed.message ?: "no answer"}"
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(20.dp),
                    )
                    else -> shown.forEachIndexed { i, c ->
                        // Tap a comment: its words, to learn from (real everyday Chinese).
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = dev.dyfeed.domain.lang.Zh.has(c.body)) { dev.dyfeed.ui.learn.LearnWords.request.value = c.body }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(dev.dyfeed.ui.feed.Display.count(c.score.toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(c.body, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            if (translateOn) {
                                Text(
                                    english.getOrNull(i) ?: "…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                if (next != null && !shown.isNullOrEmpty()) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val from = next ?: return@TextButton
                            loadingMore = true
                            commentScope.launch {
                                vm.comments(item, from).onSuccess { (page, more) -> list = (list.orEmpty() + page).distinct(); next = more }
                                    .onFailure { next = null }
                                loadingMore = false
                            }
                        },
                        enabled = !loadingMore,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) { Text(if (loadingMore) "Loading…" else "More comments") }
                }
                return@Column
            }
            if (mode == "sleep") {
                ListItem(
                    headlineContent = { Text("Stop playing after…", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { pickTag = null },
                )
                HorizontalDivider()
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SleepTimer.choices.forEach { m ->
                        androidx.compose.material3.FilterChip(
                            selected = false,
                            onClick = { SleepTimer.start(m); onDismiss() },
                            label = { Text(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min") },
                        )
                    }
                    if (SleepTimer.endsAt.collectAsStateWithLifecycle().value != null) {
                        androidx.compose.material3.FilterChip(selected = true, onClick = { SleepTimer.cancel(); onDismiss() }, label = { Text("Turn off") })
                    }
                }
                return@Column
            }
            if (mode != null) {
                ListItem(
                    headlineContent = { Text("Hide posts tagged…", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { pickTag = null },
                )
                HorizontalDivider()
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { (label, raw) ->
                        AssistChip(
                            onClick = {
                                vm.blockTag(raw)
                                onDismiss()
                            },
                            label = { Text("#$label") },
                        )
                    }
                }
                return@Column
            }

            // What you're acting on.
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))) { Thumbnail(item); dev.dyfeed.ui.common.MediaMask() }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(creator?.let { "@$it" } ?: source, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (creator != null) source else (item.title ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Why it's here, in plain words: what you follow first, then what you like. Tap one to go there.
            val why by androidx.compose.runtime.produceState(initialValue = emptyList<FeedViewModel.Why>(), item.key) { value = vm.whyThis(item) }
            if (why.isNotEmpty()) {
                Text(
                    "WHY YOU'RE SEEING THIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                )
                FlowRow(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    why.forEach { w ->
                        Text(
                            w.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (w.strong) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (w.strong) dev.dyfeed.ui.theme.BrandGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                                .then(if (w.page != null && onOpenPage != null) Modifier.clickable { onDismiss(); onOpenPage(w.page) } else Modifier)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            HorizontalDivider()

            // The everyday actions as one row of round buttons; the "less of this" ones as a list below.
            val handsFree by HandsFree.on.collectAsState()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            ) {
                QuickAction(Icons.Filled.CreateNewFolder, "Collection", modifier = Modifier.weight(1f)) { pickTag = "collect" }
                QuickAction(Icons.Filled.Autorenew, if (handsFree) "Stop hands-free" else "Hands-free", lit = handsFree, modifier = Modifier.weight(1f)) { HandsFree.set(!handsFree); onDismiss() }
                if (item.type == dev.dyfeed.domain.model.MediaType.VIDEO) {
                    // Tap to step through 1×, 1.25×, 1.5×, 2×, 0.75×; it stays for every video.
                    val speed by PlaybackSpeed.value.collectAsState()
                    QuickAction(Icons.Filled.Speed, "Speed ${PlaybackSpeed.label(speed)}", lit = speed != 1f, modifier = Modifier.weight(1f)) { PlaybackSpeed.next() }
                }
                QuickAction(Icons.AutoMirrored.Filled.OpenInNew, "Open in 抖音", modifier = Modifier.weight(1f)) { openExternal(context, item.pageUrl); onDismiss() }
            }
            HorizontalDivider()
            if (onMoreLikeThis != null) {
                ListItem(
                    headlineContent = { Text("More like this", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("An endless feed built around this post: its creator and tags") },
                    leadingContent = {
                        Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dev.dyfeed.ui.theme.BrandGradient), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { onDismiss(); onMoreLikeThis() },
                )
            }
            if (item.sourceId == dev.dyfeed.source.douyin.DouyinParser.SOURCE_ID) {
                MenuRow("Comments", Icons.Filled.ChatBubbleOutline, "What people said under it on Douyin, with translation") { pickTag = "comments" }
                if (!item.title.isNullOrBlank() || item.tags.isNotEmpty()) {
                    MenuRow("Translate", Icons.Filled.Translate, "The caption and tags in English") { pickTag = "translate" }
                    if (dev.dyfeed.domain.lang.Zh.has(item.title)) MenuRow("Learn the words", Icons.Filled.School, "The caption word by word: pinyin, meaning, a voice, and save them to review") {
                        dev.dyfeed.ui.learn.LearnWords.request.value = item.title; onDismiss()
                    }
                }
            }
            if (item.type == dev.dyfeed.domain.model.MediaType.VIDEO) {
                MenuRow("Save this moment", Icons.Filled.BookmarkAdd, "Marks where you are (a line worth hearing again): it shows on the scrub bar, and Saved → Moments plays it from there") { MomentGrab.request.value = item.key; onDismiss() }
            }
            MenuRow("Not interested", Icons.Filled.ThumbDown, "Show fewer posts like this") { vm.notInterested(item); onDismiss() }
            if (tags.isNotEmpty()) MenuRow("Hide a tag…", Icons.Filled.Block, "Never show posts with that tag") { pickTag = "block" }
            if (creator != null) {
                MenuRow("Block @$creator", Icons.Filled.PersonOff, "Never show their posts") { vm.blockCreator(item); onDismiss() }
            }
            if (item.pageUrl.isNotBlank()) {
                MenuRow("Share", Icons.Filled.Share, "Send the Douyin link to another app") {
                    val text = listOfNotNull(item.title?.take(80), item.pageUrl).joinToString("\n")
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    onDismiss()
                }
                MenuRow("Copy link", Icons.Filled.Link) {
                    dev.dyfeed.ui.common.copySensitive(context, "Link", item.pageUrl)
                    onDismiss()
                }
            }
            // Kept on the phone already: copy it out to Downloads (visible to the gallery and other apps).
            if (vm.canExport(item)) MenuRow("Save to phone", Icons.Filled.Download, "Into Downloads, where your gallery and other apps can see it") {
                vm.exportToDownloads(item)
                onDismiss()
            }
            val report = vm.reportUrl(item)
            if (report != null) {
                MenuRow("Report to $source", Icons.Filled.Flag) { openExternal(context, report); onDismiss() }
            }
        }
    }
}

/** A round button with its name under it, for the menu's top row. */
@Composable
private fun QuickAction(icon: ImageVector, label: String, lit: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .then(
                    if (lit) Modifier.background(dev.dyfeed.ui.theme.BrandGradient)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        // Rows sit on the sheet's own colour (the default surface showed as darker bands).
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Tick the collections this post belongs in, or start a new one with it. */
@Composable
private fun CollectionPicker(item: MediaItem, vm: FeedViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val collections by vm.collections.collectAsState()
    val inThem by androidx.compose.runtime.produceState<Set<String>?>(initialValue = null, item.key) { value = vm.collectionsOf(item) }
    var newName by remember { mutableStateOf("") }
    ListItem(
        headlineContent = { Text("Add to collection", fontWeight = FontWeight.Bold) },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onBack),
    )
    HorizontalDivider()
    val current = inThem ?: return
    // Secret collections (".name") aren't listed, unless this post is already in one: type the
    // full name below to add to one.
    collections.filter { !it.name.startsWith(".") || it.name in current }.forEach { c ->
        val checked = c.name in current
        ListItem(
            headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(if (c.count == 1) "1 post" else "${c.count} posts") },
            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
            trailingContent = {
                androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clickable { vm.setInCollection(item, c.name, add = !checked); onDone() },
        )
    }
    // A new collection, starting with this post.
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedTextField(
            value = newName,
            onValueChange = { newName = it.take(dev.dyfeed.data.repo.LibraryRepository.MAX_COLLECTION_NAME) },
            placeholder = { Text(if (collections.isEmpty()) "Name your first collection" else "New collection (start with . to hide it)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (newName.isNotBlank()) { vm.setInCollection(item, newName, add = true); onDone() }
            }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.TextButton(
            enabled = newName.isNotBlank(),
            onClick = { vm.setInCollection(item, newName, add = true); onDone() },
        ) { Text("Create") }
    }
    Spacer(Modifier.size(8.dp))
}
