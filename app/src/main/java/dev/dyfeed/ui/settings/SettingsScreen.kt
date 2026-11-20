package dev.dyfeed.ui.settings

import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.SystemUpdate
import dev.dyfeed.update.installFromUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import dev.dyfeed.ui.theme.Accent
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.dyfeed.BuildConfig
import dev.dyfeed.data.settings.Disguise
import dev.dyfeed.ui.Routes
import dev.dyfeed.ui.common.RadioOption
import dev.dyfeed.ui.common.findActivity
import dev.dyfeed.ui.common.RowDivider
import dev.dyfeed.ui.common.ScreenHeader
import dev.dyfeed.ui.common.SecureDialog
import dev.dyfeed.ui.common.Section
import dev.dyfeed.ui.common.SettingRow
import dev.dyfeed.ui.common.SwitchSetting
import dev.dyfeed.ui.theme.Good
import dev.dyfeed.ui.theme.TextLow
import dev.dyfeed.ui.theme.TextMid
import dev.dyfeed.ui.theme.Warn

private val cacheSteps = listOf(256, 512, 1024, 2048, 4096, 8192)
private val lockSteps = listOf(0 to "Immediately", 30 to "After 30 seconds", 60 to "After 1 minute", 300 to "After 5 minutes")
private val historySteps = listOf(7, 30, 90, 365)

private sealed interface PendingDialog {
    data object ChangePin : PendingDialog
    data object EnableLock : PendingDialog
    data object DisableLock : PendingDialog
    data object PickLockTimeout : PendingDialog
    data object PickDisguise : PendingDialog
    data object PickHistory : PendingDialog
    data object PickCache : PendingDialog
    data object Gestures : PendingDialog
    data object EraseAll : PendingDialog
    data class ExportPassword(val uri: Uri) : PendingDialog
    data class AutoBackupPassword(val folder: Uri) : PendingDialog
    data object ErasePin : PendingDialog
    data object PickBreak : PendingDialog
    data object PickPanic : PendingDialog
    data object RemoveErasePin : PendingDialog
    data class ImportPassword(val uri: Uri) : PendingDialog
}

/**
 * Settings, in one screen. Three places to go (Sources, Your taste, Hidden & blocked) and every
 * other setting right here, so nothing is more than one tap deep.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val lockOn by vm.lockEnabled.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val blocked by vm.blockEntries.collectAsStateWithLifecycle()
    val offlineBytes by vm.offlineBytes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dialog by remember { mutableStateOf<PendingDialog?>(null) }
    var showWhatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) dialog = PendingDialog.ExportPassword(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) dialog = PendingDialog.AutoBackupPassword(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dialog = PendingDialog.ImportPassword(uri)
    }

    val disguiseOptions = listOf(Disguise.NONE to "Feed", Disguise.NOTES to "Notes", Disguise.CALCULATOR to "Calculator", Disguise.BROWSER to "Browser")
    val on = sources.filter { it.enabled }
    val feeds = on.sumOf { feedCount(it.adapter.id, s) ?: 1 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("我 Me", subtitle = "Your library, your Chinese, your settings")
            // Jump straight to a section: the page is long.
            val scroll = rememberScrollState()
            val sectionY = remember { mutableStateMapOf<String, Int>() }
            fun Modifier.anchor(name: String) = onGloballyPositioned { sectionY[name] = it.positionInParent().y.toInt() }
            val jumpNames = listOf("Feed", "Look", "Playback", "Storage", "Privacy", "Network", "Backup", "Updates", "Help")
            // The section you're in is lit, and its chip kept in view.
            val currentSection by remember {
                androidx.compose.runtime.derivedStateOf {
                    if (scroll.value < 8) null
                    else jumpNames.lastOrNull { n -> (sectionY[n] ?: Int.MAX_VALUE) <= scroll.value + 48 }
                }
            }
            val chipRow = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(currentSection) { currentSection?.let { chipRow.animateScrollToItem(jumpNames.indexOf(it)) } }
            androidx.compose.foundation.lazy.LazyRow(
                state = chipRow,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                items(jumpNames) { name ->
                    androidx.compose.material3.FilterChip(
                        selected = name == currentSection,
                        onClick = { sectionY[name]?.let { y -> scope.launch { scroll.animateScrollTo(y) } } },
                        label = { Text(name) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.White),
                    )
                }
            }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {

            // At a glance: what you've kept, and a way straight to it.
            val counts by vm.libraryCounts.collectAsStateWithLifecycle()
            LibraryCard(counts) { nav.navigate(Routes.LIBRARY) { launchSingleTop = true } }
            // Your Chinese at a glance, as the library is above: words, due today, days in a row.
            val words by dev.dyfeed.domain.learn.WordBook.cards.collectAsStateWithLifecycle()
            val days by dev.dyfeed.domain.learn.WordBook.days.collectAsStateWithLifecycle()
            val now = remember { System.currentTimeMillis() }
            LearnCard(
                words = words.size,
                due = dev.dyfeed.domain.learn.Srs.due(words, now).size,
                streak = dev.dyfeed.domain.learn.Srs.streak(days, dev.dyfeed.domain.learn.WordBook.today(now)),
            ) { nav.navigate(Routes.LEARN) { launchSingleTop = true } }

            Section(footer = "If the feed stops loading, open Douyin here and pass its \"verify you're human\" check.") {
                // The one place to sort out Douyin: sign in, or clear its human check.
                val authStatus by vm.authStatus.collectAsStateWithLifecycle()
                val signedIn = remember(authStatus) { dev.dyfeed.source.douyin.DouyinWeb.isSignedIn() }
                val activityContext = androidx.compose.ui.platform.LocalContext.current
                SettingRow(
                    "抖音 Douyin",
                    subtitle = authStatus["douyin"] ?: if (signedIn) "Signed in: full search and creator pages"
                    else "Works without an account (Douyin accounts need a Chinese phone number). Tap if Douyin asks you to prove you're human.",
                    icon = Icons.Filled.PlayCircle,
                    iconTint = if (signedIn) Good else Accent,
                    chevron = true,
                    onClick = { activityContext.findActivity()?.let { vm.authenticate(it, "douyin") } },
                )
                RowDivider()
                SettingRow(
                    "Sources",
                    subtitle = "Douyin, shared links and folders on this phone",
                    icon = Icons.Filled.Dns,
                    chevron = true,
                    onClick = { nav.navigate(Routes.SOURCES) },
                )
                RowDivider()
                SettingRow(
                    "Your taste",
                    subtitle = "What For You has learned, and how it ranks",
                    icon = Icons.Filled.AutoAwesome,
                    iconTint = Warn,
                    chevron = true,
                    onClick = { nav.navigate(Routes.TASTE) },
                )
                RowDivider()
                val hiddenCount = blocked.count { it.kind != "ITEM" }
                SettingRow(
                    "Hidden & blocked",
                    subtitle = if (hiddenCount == 0) "Tags, creators and sources you never want to see" else null,
                    value = if (hiddenCount > 0) "$hiddenCount" else null,
                    icon = Icons.Filled.Block,
                    iconTint = TextLow,
                    chevron = true,
                    onClick = { nav.navigate(Routes.BLOCKLIST) },
                )
            }

            // What the main feeds show: everything by default; switch a kind off to leave it out.
            Section("Feed shows", modifier = Modifier.anchor("Feed"), footer = "Clips are under 3 minutes; full videos are 3 minutes and up. Kinds and lengths apply to the main feeds; search and tag pages still show them all.") {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    listOf("video" to "Videos", "image" to "Photo posts").forEach { (type, label) ->
                        val shown = type !in s.hiddenFeedTypes
                        androidx.compose.material3.FilterChip(
                            selected = shown,
                            onClick = { vm.setFeedType(type, !shown) },
                            label = { Text(label) },
                            leadingIcon = if (shown) ({ androidx.compose.material3.Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White),
                        )
                    }
                }
                if ("video" !in s.hiddenFeedTypes) {
                    RowDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Video length", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val options = listOf("any" to "Any", "short" to "Clips", "long" to "Full videos")
                            options.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    selected = s.videoLength == value,
                                    onClick = { vm.setVideoLength(value) },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, options.size),
                                    icon = {},
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                    }
                }
            }

            Section("Look", modifier = Modifier.anchor("Look")) {
                AccentPicker(dev.dyfeed.ui.theme.Palette.current, onPick = vm::setAccent)
            }

            Section("学习 Learn", modifier = Modifier.anchor("Learn")) {
                val pinyinOn by dev.dyfeed.ui.learn.LearnPrefs.pinyin.collectAsStateWithLifecycle()
                SwitchSetting(
                    "Pinyin over captions",
                    pinyinOn,
                    subtitle = "How every Chinese caption is said, tone by tone. Tap 词 Words under a caption for each word's meaning and voice.",
                    icon = Icons.Filled.School,
                    onChange = dev.dyfeed.ui.learn.LearnPrefs::setPinyin,
                )
                RowDivider()
                val live by dev.dyfeed.ui.learn.LearnPrefs.liveSubs.collectAsStateWithLifecycle()
                SwitchSetting(
                    "Translate what's said",
                    live,
                    subtitle = "The subtitles in a video, read off the picture as they come up and shown in English just above them (on the phone). Tap one to learn its words.",
                    icon = Icons.Filled.ClosedCaption,
                    onChange = dev.dyfeed.ui.learn.LearnPrefs::setLiveSubs,
                )
                RowDivider()
                val subSize by dev.dyfeed.ui.learn.LearnPrefs.subSize.collectAsStateWithLifecycle()
                SettingRow(
                    "Subtitle size",
                    subtitle = "How big the English for what's said is",
                    value = dev.dyfeed.ui.learn.LearnPrefs.subSizeNames[subSize],
                    icon = Icons.Filled.FormatSize,
                    onClick = dev.dyfeed.ui.learn.LearnPrefs::nextSubSize,
                )
                RowDivider()
                val autoPause by dev.dyfeed.ui.learn.LearnPrefs.autoPause.collectAsStateWithLifecycle()
                SwitchSetting(
                    "Pause after each line",
                    autoPause,
                    subtitle = "The video stops when a subtitle line ends, so you can read its English (and pinyin) before going on",
                    icon = Icons.Filled.PauseCircle,
                    onChange = dev.dyfeed.ui.learn.LearnPrefs::setAutoPause,
                )
                RowDivider()
                val slow by dev.dyfeed.ui.learn.LearnPrefs.slowVoice.collectAsStateWithLifecycle()
                SwitchSetting(
                    "Slower voice",
                    slow,
                    subtitle = "Words and sentences read aloud a little slower, to catch every tone",
                    icon = Icons.Filled.RecordVoiceOver,
                    onChange = dev.dyfeed.ui.learn.LearnPrefs::setSlowVoice,
                )
            }

            Section("Playback", modifier = Modifier.anchor("Playback")) {
                SwitchSetting(
                    "Show Chinese in English",
                    s.autoTranslate,
                    subtitle = "Captions, tags, sounds, comments, trending and search results, translated on the phone (a one-time 30 MB download). Searching in English finds Chinese posts either way.",
                    icon = Icons.Filled.Translate,
                    onChange = vm::setAutoTranslate,
                )
                RowDivider()
                SwitchSetting("Start muted", s.defaultMute, icon = Icons.AutoMirrored.Filled.VolumeOff, onChange = vm::setDefaultMute)
                RowDivider()
                SwitchSetting(
                    "Autoplay on mobile data",
                    s.autoplayOnMobileData,
                    subtitle = if (s.autoplayOnMobileData) null else "Videos wait for a tap when you're off Wi-Fi",
                    icon = Icons.Filled.SignalCellularAlt,
                    onChange = vm::setAutoplayOnMobile,
                )
                RowDivider()
                SettingRow(
                    "Break reminder",
                    subtitle = "A gentle nudge after a long stretch of watching",
                    value = if (s.breakReminderMin == 0) "Off" else "${s.breakReminderMin} min",
                    icon = Icons.Filled.SelfImprovement,
                    onClick = { dialog = PendingDialog.PickBreak },
                )
                RowDivider()
                SwitchSetting(
                    "Picture-in-picture",
                    s.pictureInPicture,
                    subtitle = "Leave the app while a video plays and it keeps going in a small window. Other apps and people nearby can see that window",
                    icon = Icons.Filled.PictureInPictureAlt,
                    onChange = vm::setPictureInPicture,
                )
                RowDivider()
                SwitchSetting(
                    "Data saver",
                    s.dataSaver,
                    subtitle = if (s.dataSaver) "Videos up to 480p" else "Videos up to 1080p",
                    icon = Icons.Filled.DataSaverOn,
                    onChange = vm::setDataSaver,
                )
                RowDivider()
                val batteryLow by dev.dyfeed.player.Battery.low.collectAsStateWithLifecycle()
                // What the app has downloaded, refreshed while this screen is open.
                var used by remember { mutableStateOf(dev.dyfeed.data.repo.DataMeter.session() to dev.dyfeed.data.repo.DataMeter.month()) }
                LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(3_000); used = dev.dyfeed.data.repo.DataMeter.session() to dev.dyfeed.data.repo.DataMeter.month() } }
                SettingRow(
                    "Data used",
                    subtitle = "${dev.dyfeed.data.repo.DataMeter.format(used.first)} since you opened the app · ${dev.dyfeed.data.repo.DataMeter.format(used.second)} this month",
                    icon = Icons.Filled.DataUsage,
                    iconTint = TextLow,
                )
            }

            // Space on the phone: what's kept for good, and what's cached to replay quickly.
            Section("Storage", modifier = Modifier.anchor("Storage")) {
                SettingRow(
                    "Video cache",
                    subtitle = "More space, fewer re-downloads",
                    value = formatMb(s.cacheSizeMb),
                    icon = Icons.Filled.SdStorage,
                    onClick = { dialog = PendingDialog.PickCache },
                    trailing = {
                        TextButton(onClick = {
                            vm.clearVideoCache()
                            scope.launch { snackbar.showSnackbar("Video cache cleared") }
                        }) { Text("Clear") }
                    },
                )
                RowDivider()
                SwitchSetting(
                    "Keep saved posts offline",
                    s.keepSavedOffline,
                    subtitle = if (s.keepSavedOffline) "Play without a connection; videos download on Wi-Fi" +
                        (if (offlineBytes > 0) " · ${formatBytes(offlineBytes)}" else "")
                    else "Saved posts stream like everything else",
                    icon = Icons.Filled.DownloadForOffline,
                    onChange = vm::setKeepSavedOffline,
                )
            }

            Section(
                "Privacy & security",
                modifier = Modifier.anchor("Privacy"),
                footer = "Your PIN, history and everything For You learns stay on this phone.",
            ) {
                if (lockOn) {
                    val eraseOn by vm.erasePinOn.collectAsStateWithLifecycle()
                }
                SwitchSetting(
                    "App lock",
                    lockOn,
                    subtitle = if (lockOn) null else "Ask for a PIN when the app opens",
                    icon = Icons.Filled.Lock,
                    iconTint = Good,
                ) { want -> dialog = if (want) PendingDialog.EnableLock else PendingDialog.DisableLock }
                if (lockOn) {
                    RowDivider()
                    SettingRow(
                        "Lock again",
                        value = lockSteps.firstOrNull { it.first == s.lockTimeoutSec }?.second ?: "${s.lockTimeoutSec} s",
                        icon = Icons.Filled.Timer,
                        iconTint = Good,
                        onClick = { dialog = PendingDialog.PickLockTimeout },
                    )
                    RowDivider()
                    SettingRow("Change PIN", icon = Icons.Filled.Password, iconTint = Good, chevron = true, onClick = { dialog = PendingDialog.ChangePin })
                }
                RowDivider()
                SwitchSetting(
                    "Private mode",
                    s.privateMode,
                    subtitle = if (s.privateMode) "Always incognito: no history, seen marks or resume points, and For You learns nothing"
                    else "Always incognito. For a while only, use Go incognito in a post's hold menu",
                    icon = Icons.Filled.VisibilityOff,
                    iconTint = Good,
                    onChange = vm::setPrivateMode,
                )
                RowDivider()
                SettingRow(
                    "Keep watch history",
                    value = "${s.historyRetentionDays} days",
                    icon = Icons.Filled.History,
                    iconTint = Good,
                    onClick = { dialog = PendingDialog.PickHistory },
                )
            }

            // How the app reaches the sites: private lookups, and a way around state and country blocks.
            Section("Network", modifier = Modifier.anchor("Network")) {
                SwitchSetting(
                    "Encrypted DNS",
                    s.encryptedDns,
                    subtitle = if (s.encryptedDns) "Every lookup goes privately over 1.1.1.1"
                    else "Automatic when a network blocks a site. On: always",
                    icon = Icons.Filled.Shield,
                    iconTint = Good,
                    onChange = vm::setEncryptedDns,
                )
                RowDivider()
                val geoBlocked by dev.dyfeed.source.net.GeoBlock.sites.collectAsStateWithLifecycle()
                SettingRow(
                    "Blocked where you are",
                    subtitle = when {
                        s.proxyType != "off" -> "Through ${if (s.proxyType == "orbot") "Orbot" else s.proxyType.uppercase()}" + if (geoBlocked.isNotEmpty()) " · ${geoBlocked.size} site${if (geoBlocked.size == 1) "" else "s"}" else ""
                        geoBlocked.isNotEmpty() -> "${geoBlocked.size} site${if (geoBlocked.size == 1) "" else "s"} blocked here. Tap to get ${if (geoBlocked.size == 1) "it" else "them"} back"
                        else -> "Sites that block your state or country"
                    },
                    icon = Icons.Filled.VpnLock,
                    iconTint = if (geoBlocked.isNotEmpty() && s.proxyType == "off") dev.dyfeed.ui.theme.Bad else Good,
                    chevron = true,
                    onClick = { nav.navigate(dev.dyfeed.ui.Routes.NETWORK) },
                )
            }

            Section("Backup", modifier = Modifier.anchor("Backup"), footer = "Settings, hidden things, follows, saved and liked posts, collections and your Chinese words, in one file encrypted with a password you choose. Your PIN, sign-ins and history aren't included; saved videos download again after an import.") {
                SettingRow("Export backup", icon = Icons.Filled.Upload, iconTint = TextLow, onClick = { exportLauncher.launch("douyin-feed-backup.json") })
                RowDivider()
                SettingRow("Import backup", icon = Icons.Filled.Download, iconTint = TextLow, onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) })
                RowDivider()
                val auto by vm.autoBackup.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { while (true) { vm.refreshAutoBackup(); kotlinx.coroutines.delay(5_000) } }
                SwitchSetting(
                    "Back up every week",
                    auto.folder != null,
                    subtitle = when {
                        auto.folder == null -> "To a folder you pick (a cloud drive's works too), encrypted with a password; the last 4 are kept"
                        auto.error != null -> "Last try failed: ${auto.error}"
                        auto.last > 0 -> "Last backup " + android.text.format.DateUtils.getRelativeTimeSpanString(auto.last)
                        else -> "Writing the first one…"
                    },
                    icon = Icons.Filled.CloudUpload,
                    onChange = { on -> if (on) runCatching { folderLauncher.launch(null) } else vm.stopAutoBackup() },
                )
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val clipContext = androidx.compose.ui.platform.LocalContext.current
            Section(
                "Updates",
                modifier = Modifier.anchor("Updates"),
                footer = "New versions come from the project's GitHub releases, each one tested on an emulator before it's published. Android asks you to confirm the first update; after that they can go in by themselves when you leave the app.",
            ) {
                UpdatesCard(vm.updater)
                RowDivider()
                SwitchSetting(
                    "Update automatically",
                    s.autoUpdate,
                    subtitle = "Look for new versions every few hours and download them on Wi-Fi",
                    icon = Icons.Filled.Autorenew,
                    onChange = vm::setAutoUpdate,
                )
                RowDivider()
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                SettingRow("Every version on GitHub", subtitle = "Release notes and APKs", icon = Icons.Filled.History, iconTint = TextLow, chevron = true, onClick = { runCatching { uri.openUri(dev.dyfeed.update.Updater.RELEASES_PAGE) } })
            }

            var crash by remember { mutableStateOf(dev.dyfeed.CrashLog.read(context)) }
            Section("Help", modifier = Modifier.anchor("Help"), footer = if (crash != null) "The app closed unexpectedly last time. The report has the app and Android versions and where it failed, nothing you were viewing." else null) {
                SettingRow("Gestures", subtitle = "Everything you can do on a post", icon = Icons.Filled.TouchApp, iconTint = TextLow, chevron = true, onClick = { dialog = PendingDialog.Gestures })
                if (crash != null) {
                    RowDivider()
                    SettingRow(
                        "Copy crash report",
                        icon = Icons.Filled.BugReport,
                        iconTint = dev.dyfeed.ui.theme.Bad,
                        onClick = { dev.dyfeed.ui.common.copySensitive(clipContext, "Crash report", crash!!) },
                        trailing = { TextButton(onClick = { dev.dyfeed.CrashLog.clear(context); crash = null }) { Text("Clear") } },
                    )
                }
            }

            Section {
                SettingRow(
                    "Erase everything",
                    subtitle = "Saves, likes, history, what For You learned, sources and settings",
                    icon = Icons.Filled.DeleteForever,
                    iconTint = dev.dyfeed.ui.theme.Bad,
                    titleColor = dev.dyfeed.ui.theme.Bad,
                    onClick = { dialog = PendingDialog.EraseAll },
                )
            }

            Text(
                "抖音 Feed ${BuildConfig.VERSION_NAME} · What's new",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).clickable { showWhatsNew = true }.padding(vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
        dev.dyfeed.ui.common.AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showWhatsNew) dev.dyfeed.ui.common.WhatsNewDialog { showWhatsNew = false }

    when (val d = dialog) {
        PendingDialog.ChangePin -> ChangePinDialog(onDismiss = { dialog = null }) { cur, new -> vm.changePin(cur, new); dialog = null }
        PendingDialog.EnableLock -> TwoPinDialog(
            title = "Set a PIN",
            first = "PIN (4–8 digits)",
            second = "Confirm PIN",
            action = "Turn on",
            onDismiss = { dialog = null },
        ) { a, b -> vm.enableLock(a, b); dialog = null }
        PendingDialog.DisableLock -> OnePinDialog(title = "Turn off app lock", label = "Current PIN", action = "Turn off", onDismiss = { dialog = null }) {
            vm.disableLock(it); dialog = null
        }
        PendingDialog.PickLockTimeout -> ChoiceDialog(
            title = "Lock again",
            note = "How long the app can be in the background before it asks for the PIN.",
            options = lockSteps,
            selected = s.lockTimeoutSec,
            onDismiss = { dialog = null },
        ) { vm.setLockTimeout(it); dialog = null }
        PendingDialog.PickDisguise -> ChoiceDialog(
            title = "Home screen icon",
            note = "The icon and name shown on your home screen.",
            options = disguiseOptions,
            selected = s.disguise,
            onDismiss = { dialog = null },
        ) { vm.setDisguise(it); dialog = null }
        PendingDialog.PickHistory -> ChoiceDialog(
            title = "Keep watch history",
            note = "Posts you've seen don't come back while they're in history. Older history is deleted.",
            options = historySteps.map { it to "$it days" },
            selected = s.historyRetentionDays,
            onDismiss = { dialog = null },
        ) { vm.setHistoryRetention(it); dialog = null }
        PendingDialog.PickPanic -> ChoiceDialog(
            title = "Leave to",
            note = "When you leave in a hurry, the app locks (if the lock is on) and this opens instead, so the screen shows something ordinary.",
            options = PANIC_TARGETS,
            selected = s.panicTo,
            onDismiss = { dialog = null },
        ) { vm.setPanicTo(it); dialog = null }
        PendingDialog.PickBreak -> ChoiceDialog(
            title = "Break reminder",
            note = "After this long watching without stopping, a quiet message offers a break or a sleep timer. Nothing else changes.",
            options = listOf(0 to "Off", 30 to "30 min", 45 to "45 min", 60 to "1 h", 90 to "1 h 30 min", 120 to "2 h"),
            selected = s.breakReminderMin,
            onDismiss = { dialog = null },
        ) { vm.setBreakReminder(it); dialog = null }
        PendingDialog.PickCache -> ChoiceDialog(
            title = "Video cache",
            note = "Space kept for videos you've watched, so they don't download again. Takes effect next launch.",
            options = cacheSteps.map { it to formatMb(it) },
            selected = s.cacheSizeMb,
            onDismiss = { dialog = null },
        ) { vm.setCacheSize(it); dialog = null }
        is PendingDialog.ExportPassword -> PasswordDialog("Encrypt export", confirmTwice = true, onDismiss = { dialog = null }) {
            vm.exportSettings(d.uri, it); dialog = null
        }
        is PendingDialog.AutoBackupPassword -> PasswordDialog("Password for weekly backups", confirmTwice = true, onDismiss = { dialog = null }) {
            vm.startAutoBackup(d.folder, it); dialog = null
        }
        PendingDialog.ErasePin -> PasswordDialog("Emergency PIN (digits)", confirmTwice = true, minLength = 4, onDismiss = { dialog = null }) {
            vm.setErasePin(it); dialog = null
        }
        PendingDialog.RemoveErasePin -> AlertDialog(
            onDismissRequest = { dialog = null },
            properties = SecureDialog,
            title = { Text("Remove the emergency PIN?") },
            text = { Text("Entering it will no longer erase anything.") },
            confirmButton = { TextButton(onClick = { vm.setErasePin(null); dialog = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        is PendingDialog.ImportPassword -> PasswordDialog("Decrypt import", confirmTwice = false, onDismiss = { dialog = null }) {
            vm.importSettings(d.uri, it); dialog = null
        }
        PendingDialog.Gestures -> AlertDialog(
            onDismissRequest = { dialog = null },
            properties = SecureDialog,
            title = { Text("Gestures") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Swipe up / down" to "Next or previous post",
                        "Tap" to "Pause or play",
                        "Double-tap" to "Like",
                        "Hold the right side of a video" to "Double speed while held (set a speed in the hold menu)",
                        "Press and hold" to "More: comments, why it's here, not interested, block, copy link, save to phone",
                        "Swipe left on a post" to "Everything by its creator",
                        "Swipe right on a post" to "Save it",
                        "Pinch in on the feed" to "Zoom out to the Wall (on a tag or creator page: its grid)",
                        "Tap with two fingers" to "Sound on or off",
                        "Hold the heart" to "Love it: liked, saved to Favourites, and much more like it",
                        "Hold \"For You\"" to "Choose its mood (familiar, balanced, adventurous) and what the feeds show",
                        "Swipe sideways on a photo post" to "Next or previous picture",
                        "Pinch" to "Zoom; zoomed in, one finger moves around",
                        "Pull down on the first post" to "Fresh posts",
                        "Double-tap an edge of a long video (or any video sideways)" to "Skip 10 s back or ahead; keep tapping to skip more",
                        "Volume up" to "Sound on",
                        "Tap @name or #tag" to "Everything by them, or with that tag",
                    ).forEach { (g, what) ->
                        Column {
                            Text(g, style = MaterialTheme.typography.titleSmall)
                            Text(what, style = MaterialTheme.typography.bodyMedium, color = dev.dyfeed.ui.theme.TextMid)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
        )
        PendingDialog.EraseAll -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            AlertDialog(
                onDismissRequest = { dialog = null },
                properties = SecureDialog,
                title = { Text("Erase everything?") },
                text = { Text("Everything this app has stored on this phone is deleted and the app closes, as if it had just been installed. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
                    }) { Text("Erase", color = dev.dyfeed.ui.theme.Bad) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        null -> Unit
    }
}

private fun formatBytes(b: Long) = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "${b shr 20} MB"
    else -> "${(b shr 10).coerceAtLeast(1)} KB"
}

private fun formatMb(mb: Int) = if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    note: String? = null,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecureDialog,
        title = { Text(title) },
        text = {
            Column {
                note?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                }
                options.forEach { (value, label) -> RadioOption(label, value == selected) { onPick(value) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecureDialog,
        title = { Text("Change PIN") },
        text = {
            Column {
                PinField("Current PIN", current) { current = it }
                PinField("New PIN (4–8 digits)", new) { new = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current, new) }, enabled = current.isNotEmpty() && new.length >= 4) { Text("Change") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TwoPinDialog(title: String, first: String, second: String, action: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecureDialog,
        title = { Text(title) },
        text = {
            Column {
                PinField(first, a) { a = it }
                PinField(second, b) { b = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(a, b) }, enabled = a.length >= 4 && b.length >= 4) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OnePinDialog(title: String, label: String, action: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecureDialog,
        title = { Text(title) },
        text = { PinField(label, a) { a = it } },
        confirmButton = { TextButton(onClick = { onConfirm(a) }, enabled = a.isNotEmpty()) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(8)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun PasswordDialog(title: String, confirmTwice: Boolean, minLength: Int = 8, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val valid = pw.length >= minLength && (!confirmTwice || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecureDialog,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text(if (minLength >= 8) "Password (8+ characters)" else "At least $minLength digits") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, label = { Text("Repeat password") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = valid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Saved · Liked · Following, as three numbers on one card. */
@Composable
private fun LibraryCard(counts: Triple<Int, Int, Int>, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(vertical = 16.dp),
    ) {
        listOf(counts.first to "Saved", counts.second to "Liked", counts.third to "Following").forEachIndexed { i, (n, label) ->
            if (i > 0) Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%,d".format(n),
                    style = MaterialTheme.typography.titleLarge.copy(brush = dev.dyfeed.ui.theme.BrandGradient),
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
            }
        }
    }
}

/** 学习 at a glance: saved words, due now, days in a row; opens Learn. */
@Composable
private fun LearnCard(words: Int, due: Int, streak: Int, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(vertical = 14.dp),
    ) {
        listOf(words to "Words · 词", due to "To review", streak to "Days in a row").forEachIndexed { i, (n, label) ->
            if (i > 0) Box(Modifier.width(0.5.dp).height(36.dp).align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.outlineVariant))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%,d".format(n), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD58A))
                Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
            }
        }
    }
}

/** The app's colour: a row of gradient swatches, the chosen one ringed and named. */
@Composable
private fun AccentPicker(current: dev.dyfeed.ui.theme.Palette, onPick: (dev.dyfeed.ui.theme.Palette) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Colour", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(if (current == dev.dyfeed.ui.theme.Palette.CHAMELEON) "Chameleon: follows the post" else current.label, style = MaterialTheme.typography.labelLarge, color = dev.dyfeed.ui.theme.Accent)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            dev.dyfeed.ui.theme.Palette.entries.forEach { p ->
                val on = p == current
                val ring by androidx.compose.animation.core.animateDpAsState(if (on) 3.dp else 0.dp, label = "swatchRing")
                Box(
                    Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .border(ring, Color.White, androidx.compose.foundation.shape.CircleShape)
                        .padding(if (on) 5.dp else 0.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (p == dev.dyfeed.ui.theme.Palette.CHAMELEON) androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color(0xFFFF3D68), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFFF3D68))) else p.gradient)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onPick(p)
                        }
                        .semantics { contentDescription = p.label + if (on) ", chosen" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    else if (p == dev.dyfeed.ui.theme.Palette.CHAMELEON) Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** This version, what GitHub has, and the one button for the next step. */
@Composable
private fun UpdatesCard(updater: dev.dyfeed.update.Updater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(dev.dyfeed.ui.theme.BrandGradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Version ${updater.currentVersion}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val line = when (val st = state) {
                    dev.dyfeed.update.Updater.State.Idle -> "Tap Check to look for a newer one"
                    dev.dyfeed.update.Updater.State.Checking -> "Looking on GitHub…"
                    is dev.dyfeed.update.Updater.State.UpToDate -> "Up to date"
                    is dev.dyfeed.update.Updater.State.Available -> "${st.release.version} is out" + if (st.release.size > 0) " · ${st.release.size / 1_048_576} MB" else ""
                    is dev.dyfeed.update.Updater.State.Downloading -> "Downloading ${st.release.version}… ${(st.progress * 100).toInt()}%"
                    is dev.dyfeed.update.Updater.State.Ready -> "${st.release.version} is downloaded and ready"
                    is dev.dyfeed.update.Updater.State.Installing -> "Installing ${st.release.version}…"
                    is dev.dyfeed.update.Updater.State.Failed -> st.message
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = if (state is dev.dyfeed.update.Updater.State.Failed) dev.dyfeed.ui.theme.Bad else TextMid)
            }
            when (val st = state) {
                is dev.dyfeed.update.Updater.State.Ready -> dev.dyfeed.ui.common.BrandButton("Install", onClick = { updater.installFromUi(context) }, compact = true)
                is dev.dyfeed.update.Updater.State.Available -> dev.dyfeed.ui.common.BrandButton("Download", onClick = updater::downloadNow, compact = true)
                is dev.dyfeed.update.Updater.State.Failed -> TextButton(onClick = { if (st.release != null) updater.downloadNow() else updater.checkAndDownload() }) { Text("Try again") }
                dev.dyfeed.update.Updater.State.Checking, is dev.dyfeed.update.Updater.State.Downloading, is dev.dyfeed.update.Updater.State.Installing -> dev.dyfeed.ui.common.BrandSpinner(size = 22.dp, stroke = 2.5.dp)
                else -> TextButton(onClick = updater::checkAndDownload) { Text("Check") }
            }
        }
        val st = state
        if (st is dev.dyfeed.update.Updater.State.Downloading) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
        }
        val notes = (st as? dev.dyfeed.update.Updater.State.Ready)?.release?.notes ?: (st as? dev.dyfeed.update.Updater.State.Available)?.release?.notes
        if (!notes.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                notes.lines().filter { it.isNotBlank() }.take(6).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
                maxLines = 8,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private val PANIC_TARGETS = listOf("home" to "Home screen", "browser" to "Browser (Google)", "calculator" to "Calculator", "messages" to "Messages")
