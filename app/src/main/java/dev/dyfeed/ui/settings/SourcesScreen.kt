package dev.dyfeed.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.dyfeed.data.settings.AppSettings
import dev.dyfeed.data.settings.JsonFeed
import dev.dyfeed.data.settings.SourceCatalog
import dev.dyfeed.source.RegisteredSource
import dev.dyfeed.source.SourceChecker
import dev.dyfeed.ui.common.RowDivider
import dev.dyfeed.ui.common.ScreenHeader
import dev.dyfeed.ui.common.Section
import dev.dyfeed.ui.common.SettingRow
import dev.dyfeed.ui.common.StatusLine
import dev.dyfeed.ui.common.findActivity
import dev.dyfeed.ui.common.openExternal
import dev.dyfeed.ui.theme.Accent
import dev.dyfeed.ui.theme.Bad
import dev.dyfeed.ui.theme.Good
import dev.dyfeed.ui.theme.TextLow
import dev.dyfeed.ui.theme.TextMid
import dev.dyfeed.ui.theme.Warn

/** Sources that only show what you add yourself; everything else is built in. */
private val USER_FILLED = listOf("direct", "local")

/** Display order: the big built-in sources first, in roughly the order people use them. */
private val ORDER = listOf("douyin") + USER_FILLED

private fun iconFor(id: String): ImageVector = when (id) {
    "douyin" -> Icons.Filled.Videocam
    "direct" -> Icons.Filled.Link
    "local" -> Icons.Filled.Folder
    else -> Icons.Filled.Widgets
}

/**
 * Sources, as one list. Each row is a switch; tap the row to see what it pulls from, add to it,
 * or fix it. "Check" tests everything from this phone, and "Find more" adds sources by topic.
 */
@Composable
fun SourcesScreen(nav: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var openId by remember { mutableStateOf<String?>(null) }
    var sheetMessage by remember { mutableStateOf<String?>(null) }
    var healthTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        vm.messages.collect { m -> if (openId != null) sheetMessage = m else snackbar.showSnackbar(m) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    val listed = remember(sources) {
        sources.filter { it.adapter.isConfigured }.sortedBy { ORDER.indexOf(it.adapter.id).let { i -> if (i < 0) 99 else i } }
    }
    val on = listed.filter { it.enabled }
    val feeds = on.sumOf { feedCount(it.adapter.id, s) ?: 1 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "Sources",
                subtitle = "${"%,d".format(feeds)} feeds from ${on.size} sources",
                onBack = { nav.popBackStack() },
            )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            HealthCard(
                sources = listed,
                checks = checks,
                skipped = remember(listed, healthTick) { listed.sumOf { vm.skippedFeeds(it.adapter.id) } },
                onCheck = vm::checkSources,
                onRetrySkipped = { vm.retrySkippedFeeds(); healthTick++ },
            )

            Section("Built in") {
                val built = listed.filter { it.adapter.id !in USER_FILLED }
                built.forEachIndexed { i, src ->
                    if (i > 0) RowDivider()
                    SourceRow(src, s, checks[src.adapter.id], checks.containsKey(src.adapter.id), vm, healthTick) { openId = src.adapter.id }
                }
            }
            Section("Your own", footer = "Add a link or a folder on this phone. Tap one to add to it.") {
                val own = listed.filter { it.adapter.id in USER_FILLED }
                own.forEachIndexed { i, src ->
                    if (i > 0) RowDivider()
                    SourceRow(src, s, checks[src.adapter.id], checks.containsKey(src.adapter.id), vm, healthTick) { openId = src.adapter.id }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
        dev.dyfeed.ui.common.AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    val open = listed.firstOrNull { it.adapter.id == openId }
    if (open != null) {
        SourceSheet(
            src = open,
            s = s,
            check = checks[open.adapter.id],
            vm = vm,
            message = sheetMessage,
            onPickFolder = { folderPicker.launch(null) },
            onDismiss = { openId = null; sheetMessage = null; healthTick++ },
        )
    }
}

/** The sites behind each source that can block a whole state or country. */
private val GEO_SITES = mapOf("douyin" to setOf("douyin.com"))

/** One line of status for a source row: the most important thing about it right now. */
private data class RowStatus(val text: String, val color: Color)

private fun statusOf(
    src: RegisteredSource,
    s: AppSettings,
    check: SourceChecker.Result?,
    checking: Boolean,
    resting: List<String>,
    auth: String?,
    geoBlocked: Set<String> = emptySet(),
): RowStatus {
    val id = src.adapter.id
    val blockedHere = GEO_SITES[id]?.filter { it in geoBlocked }.orEmpty()
    return when {
        !src.enabled -> RowStatus("Off", TextLow)
        blockedHere.isNotEmpty() && !dev.dyfeed.source.net.AppProxy.config.active ->
            RowStatus("Blocked where you are: Settings → Network to get it back", Bad)
        checking && check == null -> RowStatus("Checking…", TextLow)
        check != null && !check.ok -> RowStatus(check.detail, Bad)
        resting.isNotEmpty() -> RowStatus("Paused a few minutes: ${resting.take(2).joinToString(", ")} not responding", Warn)
        auth != null -> RowStatus(auth, TextMid)
        src.adapter.requiresAuth -> RowStatus("Sign in to use", Warn)
        check != null -> RowStatus(check.detail, Good)
        else -> RowStatus(feedSummary(id, s) ?: "On", Good)
    }
}

@Composable
private fun SourceRow(
    src: RegisteredSource,
    s: AppSettings,
    check: SourceChecker.Result?,
    checking: Boolean,
    vm: SettingsViewModel,
    tick: Int,
    onOpen: () -> Unit,
) {
    val auth by vm.authStatus.collectAsStateWithLifecycle()
    val resting = remember(src, tick, check) { if (src.enabled) vm.restingSites(src.adapter.id) else emptyList() }
    val geoBlocked by dev.dyfeed.source.net.GeoBlock.sites.collectAsStateWithLifecycle()
    val status = statusOf(src, s, check, checking, resting, auth[src.adapter.id], geoBlocked)
    SettingRow(
        title = src.adapter.displayName,
        icon = iconFor(src.adapter.id),
        iconTint = if (src.enabled) Accent else TextLow,
        below = { StatusLine(status.text, status.color) },
        onClick = onOpen,
        trailing = {
            Switch(
                checked = src.enabled,
                onCheckedChange = { vm.setSourceEnabled(src.adapter.id, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
            )
        },
    )
}

@Composable
private fun HealthCard(
    sources: List<RegisteredSource>,
    checks: Map<String, SourceChecker.Result?>,
    skipped: Int,
    onCheck: () -> Unit,
    onRetrySkipped: () -> Unit,
) {
    val clipContext = androidx.compose.ui.platform.LocalContext.current
    val running = checks.isNotEmpty() && checks.values.any { it == null }
    val done = checks.isNotEmpty() && !running
    val working = checks.values.count { it?.ok == true }
    Section("Health") {
        Column(Modifier.padding(16.dp)) {
            when {
                running -> {
                    val finished = checks.values.count { it != null }
                    Text("Checking ${finished + 1} of ${checks.size}…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { finished / checks.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                done -> {
                    val all = working == checks.size
                    Text(
                        if (all) "All ${checks.size} sources working" else "$working of ${checks.size} sources working",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (all) Good else Warn,
                    )
                    Text(
                        if (all) "Tested from this phone just now." else "The rest are listed below with what went wrong. Most fix themselves within minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid,
                    )
                }
                else -> {
                    Text("Is everything loading?", style = MaterialTheme.typography.titleMedium)
                    Text("Check Douyin and your other sources from this phone and connection.", style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dev.dyfeed.ui.common.BrandButton(if (done) "Check again" else "Check sources", onClick = onCheck, enabled = !running, compact = true)
                if (done) {
                    OutlinedButton(onClick = {
                        val report = sources.filter { it.adapter.id in checks }.joinToString("\n") { src ->
                            val r = checks[src.adapter.id]
                            "${if (r?.ok == true) "OK  " else "FAIL"} ${src.adapter.displayName}: ${r?.detail}"
                        }
                        dev.dyfeed.ui.common.copySensitive(clipContext, "Source report", report)
                    }) { Text("Copy report") }
                }
            }
        }
        if (skipped > 0) {
            RowDivider(indent = false)
            SettingRow(
                "$skipped feeds skipped",
                subtitle = "They kept answering \"not found\". Retried automatically in two days.",
                trailing = { TextButton(onClick = onRetrySkipped) { Text("Retry now") } },
            )
        }
    }
}


/**
 * Everything about one source: on/off, health, sign-in, what it pulls from (with remove), and the
 * one way to add to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(
    src: RegisteredSource,
    s: AppSettings,
    check: SourceChecker.Result?,
    vm: SettingsViewModel,
    message: String?,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val a = src.adapter
    val context = LocalContext.current
    val auth by vm.authStatus.collectAsStateWithLifecycle()
    val resting = remember(src) { vm.restingSites(a.id) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.Inherit),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(feedSummary(a.id, s) ?: "", style = MaterialTheme.typography.bodyMedium, color = TextMid)
                }
                Switch(
                    checked = src.enabled,
                    onCheckedChange = { vm.setSourceEnabled(a.id, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (check != null) StatusLine(check.detail, if (check.ok) Good else Bad)
                if (resting.isNotEmpty()) StatusLine("Paused a few minutes, not responding: ${resting.joinToString(", ")}", Warn)
                auth[a.id]?.let { StatusLine(it, TextMid) }
                val skipped = vm.skippedFeeds(a.id)
                if (skipped > 0) StatusLine("$skipped feeds skipped for answering \"not found\"", TextLow)
                message?.let { StatusLine(it, Warn) }
            }
            if (a.offersSignIn) {
                Button(
                    onClick = { context.findActivity()?.let { vm.authenticate(it, a.id) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                ) { Text(if (a.id == "douyin") "Verify I'm human" else "Sign in") }
            }

            AddTo(a.id, vm, onPickFolder)
            Entries(a.id, s, vm)
            if (a.id == "douyin") Text(
                "Douyin's own recommended feed, re-ranked here by what you watch. No account needed (Douyin accounts need a Chinese phone number). If the feed stops loading, Douyin may want its \"verify you're human\" check: tap the button above.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** The single "add" control for a source, shaped by what that source takes. */
@Composable
private fun AddTo(id: String, vm: SettingsViewModel, onPickFolder: () -> Unit) {
    when (id) {
        "direct" -> AddSection("Add a link") { OneField("Video, image or page URL", "https://…/clip.mp4", KeyboardType.Uri) { vm.addDirect(it) } }
        "local" -> AddSection("Add a folder") {
            Button(onClick = onPickFolder, colors = ButtonDefaults.buttonColors(containerColor = Accent), modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Filled.Folder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose a folder")
            }
        }
        else -> Unit
    }
}

@Composable
private fun AddSection(title: String, content: @Composable () -> Unit) {
    Section(title) { content() }
}


/** The editable list behind a source (links, folders), filterable, each removable. */
@Composable
private fun Entries(id: String, s: AppSettings, vm: SettingsViewModel) {
    val entries: List<Pair<String, () -> Unit>> = when (id) {
        "direct" -> s.directUrls.map { u -> u to { vm.removeDirect(u); Unit } }
        "local" -> s.localFolders.map { u -> (Uri.parse(u).lastPathSegment?.substringAfter(':') ?: u) to { vm.removeFolder(u); Unit } }
        else -> return
    }
    var filter by remember(id) { mutableStateOf("") }
    val shown = entries.filter { filter.isBlank() || it.first.contains(filter, ignoreCase = true) }
    Section(if (entries.isEmpty()) "Nothing added yet" else "${"%,d".format(entries.size)} in this source") {
        if (entries.size > 12) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Find one") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        shown.take(MAX_SHOWN).forEachIndexed { i, (label, remove) ->
            if (i > 0) RowDivider(indent = false)
            SettingRow(
                label,
                trailing = { IconButton(onClick = remove) { Icon(Icons.Filled.Close, contentDescription = "Remove", tint = TextLow) } },
            )
        }
        if (shown.size > MAX_SHOWN) {
            Text(
                "and ${"%,d".format(shown.size - MAX_SHOWN)} more. Type above to find one.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private const val MAX_SHOWN = 50

@Composable
private fun OneField(label: String, placeholder: String, type: KeyboardType, onAdd: (String) -> Boolean) {
    var text by remember(label) { mutableStateOf("") }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && onAdd(text)) text = "" }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { if (onAdd(text)) text = "" }, enabled = text.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Add") }
    }
}

@Composable
private fun TwoField(label1: String, ph1: String, label2: String, ph2: String, onAdd: (String, String) -> Boolean) {
    var a by remember(label1) { mutableStateOf("") }
    var b by remember(label1) { mutableStateOf("") }
    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(
            value = a, onValueChange = { a = it }, label = { Text(label1) }, placeholder = { Text(ph1) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = b, onValueChange = { b = it }, label = { Text(label2) }, placeholder = { Text(ph2) },
                singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (onAdd(a, b)) { a = ""; b = "" } }, enabled = a.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Add") }
        }
    }
}


@Composable
private fun Field(label: String, value: String, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}


/** How many feeds/entries a source pulls from, or null if it has no countable list. */
internal fun feedCount(id: String, s: AppSettings): Int? = when (id) {
    "direct" -> s.directUrls.size
    "local" -> s.localFolders.size
    else -> null
}

/** One line about what a source is pulling from. */
internal fun feedSummary(id: String, s: AppSettings): String? = when (id) {
    "douyin" -> "Recommended feed, search, creators"
    "direct" -> if (s.directUrls.isEmpty()) "Paste a video or image link" else "${s.directUrls.size} links"
    "local" -> if (s.localFolders.isEmpty()) "Pick a folder on this phone" else "${s.localFolders.size} folders"
    else -> null
}
