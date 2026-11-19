package dev.dyfeed.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.dyfeed.data.db.BlockEntity
import dev.dyfeed.data.repo.BlockKind
import dev.dyfeed.data.repo.BlockRepository
import dev.dyfeed.data.repo.SettingsExporter
import dev.dyfeed.data.repo.SignalRepository
import dev.dyfeed.data.repo.WrongPasswordException
import dev.dyfeed.data.settings.AppSettings
import dev.dyfeed.data.settings.BooruFeed
import dev.dyfeed.data.settings.Disguise
import dev.dyfeed.data.settings.MastodonFeed
import dev.dyfeed.data.settings.SettingsRepository
import dev.dyfeed.domain.model.AuthContext
import dev.dyfeed.domain.model.AuthResult
import dev.dyfeed.domain.reco.RankWeights
import dev.dyfeed.player.MediaCache
import dev.dyfeed.security.AppLockManager
import dev.dyfeed.security.LauncherDisguise
import dev.dyfeed.source.RegisteredSource
import dev.dyfeed.source.SourceRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/** Shared by all settings screens (each gets its own instance; state lives in the repositories). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val settingsRepo: SettingsRepository,
    private val registry: SourceRegistry,
    private val blocks: BlockRepository,
    private val signals: SignalRepository,
    private val exporter: SettingsExporter,
    private val mediaCache: MediaCache,
    private val lock: AppLockManager,
    private val secrets: dev.dyfeed.security.SecretStore,
    val updater: dev.dyfeed.update.Updater,
    private val feedHealth: dev.dyfeed.data.repo.FeedHealthStore,
    private val checker: dev.dyfeed.source.SourceChecker,
    private val mediaHealth: dev.dyfeed.data.repo.MediaHealth,
    offline: dev.dyfeed.data.repo.OfflineStore,
    library: dev.dyfeed.data.repo.LibraryRepository,
) : ViewModel() {

    /** Saved, liked and followed counts, for the card at the top of Settings. */
    val libraryCounts: StateFlow<Triple<Int, Int, Int>> = kotlinx.coroutines.flow.combine(
        library.count(dev.dyfeed.data.repo.LibraryKind.SAVED),
        library.count(dev.dyfeed.data.repo.LibraryKind.LIKED),
        library.followRows,
    ) { saved, liked, follows -> Triple(saved, liked, follows.size) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), Triple(0, 0, 0))

    /**
     * Sites of [sourceId] being left alone right now: not answering requests (down, rate-limiting),
     * or answering but with files that won't load. They're retried automatically after ~10 minutes.
     */
    fun restingSites(sourceId: String): List<String> {
        val adapter = sources.value.firstOrNull { it.adapter.id == sourceId }?.adapter
        val notAnswering = (adapter as? dev.dyfeed.source.net.CachingAdapter)?.restingGroups().orEmpty()
        val filesDown = mediaHealth.resting().filter { it == sourceId || it.startsWith("$sourceId:") }
            .map { it.substringAfter(':') }
        return (notAnswering + filesDown).distinct()
    }

    /** Results of "Check sources", by source id; null value = being checked right now. */
    private val _checks = MutableStateFlow<Map<String, dev.dyfeed.source.SourceChecker.Result?>>(emptyMap())
    val checks: StateFlow<Map<String, dev.dyfeed.source.SourceChecker.Result?>> = _checks
    private var checkJob: kotlinx.coroutines.Job? = null

    /** Tests every enabled source from this phone, a few at a time, filling [checks] as they finish. */
    fun checkSources() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val enabled = sources.value.filter { it.enabled }.map { it.adapter }
            _checks.value = enabled.associate { it.id to null }
            val gate = kotlinx.coroutines.sync.Semaphore(3)
            kotlinx.coroutines.coroutineScope {
                enabled.forEach { a ->
                    launch {
                        gate.acquire()
                        try {
                            val r = checker.check(a)
                            _checks.update { it + (a.id to r) }
                        } finally {
                            gate.release()
                        }
                    }
                }
            }
        }
    }

    /** Feeds of [sourceId] currently skipped because they kept answering "not found" or empty. */
    fun skippedFeeds(sourceId: String): Int = feedHealth.deadCount(sourceId)

    /** Give every skipped feed another try now instead of after a week. */
    fun retrySkippedFeeds() {
        feedHealth.clear()
        _messages.tryEmit("Skipped feeds will be tried again")
    }

    val settings: StateFlow<AppSettings> = settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val sources: StateFlow<List<RegisteredSource>> = registry.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val blockEntries: StateFlow<List<BlockEntity>> = blocks.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _authStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val authStatus: StateFlow<Map<String, String>> = _authStatus

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepo.update(f) }

    // ---- playback / privacy ----
    fun setCacheSize(mb: Int) {
        mediaCache.configuredSizeMb = mb
        update { it.copy(cacheSizeMb = mb) }
    }
    fun setAutoplayOnMobile(v: Boolean) = update { it.copy(autoplayOnMobileData = v) }
    fun setLivePreviews(v: Boolean) = update { it.copy(livePreviews = v) }
    fun setAutoUpdate(v: Boolean) = update { it.copy(autoUpdate = v) }
    fun setFlipToHide(v: Boolean) = update { it.copy(flipToHide = v) }
    fun setSkipIntros(v: Boolean) = update { it.copy(skipIntros = v) }
    fun setSmartVolume(v: Boolean) = update { it.copy(smartVolume = v) }
    fun setCalculatorLock(v: Boolean) = update { it.copy(calculatorLock = v) }
    fun setBatterySaver(v: Boolean) = update { it.copy(batterySaver = v) }
    fun setReadyAhead(on: Boolean) = update { it.copy(readyAhead = on) }
    fun setVolumeKeysTurnPages(v: Boolean) = update { it.copy(volumeKeysTurnPages = v) }
    fun setHeadphonesOnly(v: Boolean) = update { it.copy(headphonesOnly = v) }
    fun setBreakReminder(min: Int) = update { it.copy(breakReminderMin = min) }
    fun setBestQuality(v: Boolean) = update { it.copy(bestQuality = v) }
    fun setHideFromRecents(v: Boolean) = update { it.copy(hideFromRecents = v) }
    fun setPanicTo(v: String) = update { it.copy(panicTo = v) }
    fun setAccent(p: dev.dyfeed.ui.theme.Palette) {
        dev.dyfeed.ui.theme.Palette.set(p.name) // repaint now, not after the write
        update { it.copy(accent = p.name) }
    }
    fun setDefaultMute(v: Boolean) = update { it.copy(defaultMute = v) }
    fun setHistoryRetention(days: Int) = update { it.copy(historyRetentionDays = days) }
    fun setEncryptedDns(v: Boolean) = update { it.copy(encryptedDns = v) }
    fun setPrivateMode(v: Boolean) = update { it.copy(privateMode = v) }
    fun setDataSaver(v: Boolean) = update { it.copy(dataSaver = v) }
    fun setAutoTranslate(v: Boolean) = update { it.copy(autoTranslate = v) }
    fun setBlurPreviews(v: Boolean) = update { it.copy(blurPreviews = v) }
    fun setKeepSavedOffline(v: Boolean) = update { it.copy(keepSavedOffline = v) }

    /** Empties the video cache (posts watched recently play again from the network). Kept saved posts aren't touched. */
    fun clearVideoCache() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { mediaCache.clear() } }
    fun setFeedType(type: String, shown: Boolean) = update { s ->
        val hidden = if (shown) s.hiddenFeedTypes - type else s.hiddenFeedTypes + type
        // Never everything off: an empty feed would just look broken.
        if (hidden.size >= 4) s else s.copy(hiddenFeedTypes = hidden)
    }

    fun setVideoLength(length: String) = update { it.copy(videoLength = length) }
    fun setHideAiArt(hide: Boolean) = update { it.copy(hideAiArt = hide) }
    fun setPictureInPicture(on: Boolean) = update { it.copy(pictureInPicture = on) }
    fun setProxy(type: String? = null, host: String? = null, port: Int? = null, user: String? = null, password: String? = null, everything: Boolean? = null) = update {
        it.copy(
            proxyType = type ?: it.proxyType,
            proxyHost = host ?: it.proxyHost,
            proxyPort = port ?: it.proxyPort,
            proxyUser = user ?: it.proxyUser,
            proxyPassword = password ?: it.proxyPassword,
            proxyEverything = everything ?: it.proxyEverything,
        )
    }

    /** Space taken by offline copies of saved posts. */
    val offlineBytes = offline.bytes
    fun setLockTimeout(sec: Int) = update { it.copy(lockTimeoutSec = sec) }

    fun setDisguise(d: Disguise) {
        LauncherDisguise.apply(app, d)
        update { it.copy(disguise = d) }
        _messages.tryEmit("Launcher updated. Some launchers take a few seconds to refresh.")
    }

    val lockEnabled: StateFlow<Boolean> = lock.enabled

    fun enableLock(pin: String, confirm: String) = viewModelScope.launch {
        when {
            pin.length < 4 -> _messages.tryEmit("PIN must be at least 4 digits")
            pin != confirm -> _messages.tryEmit("PINs don't match")
            else -> {
                lock.createPin(pin.toCharArray())
                _messages.tryEmit("App lock on")
            }
        }
    }

    fun disableLock(current: String) = viewModelScope.launch {
        _messages.tryEmit(if (lock.disable(current.toCharArray())) "App lock off" else "Wrong PIN")
    }

    fun changePin(current: String, new: String) = viewModelScope.launch {
        if (new.length < 4) { _messages.tryEmit("New PIN must be at least 4 digits"); return@launch }
        val ok = lock.changePin(current.toCharArray(), new.toCharArray())
        _messages.tryEmit(if (ok) "PIN changed" else "Current PIN is wrong")
    }

    // ---- recommendations ----
    fun setWeights(w: RankWeights) = update { it.copy(weights = w) }
    fun setHalfLife(days: Double) = update { it.copy(halfLifeDays = days) }
    fun resetWeights() = update { it.copy(weights = RankWeights(), halfLifeDays = 14.0) }
    private val _interests = MutableStateFlow<Pair<List<Pair<String, Double>>, List<Pair<String, Double>>>?>(null)
    /** (likes, dislikes), strongest first; null until loaded. */
    val interests: StateFlow<Pair<List<Pair<String, Double>>, List<Pair<String, Double>>>?> = _interests

    fun loadInterests() = viewModelScope.launch {
        _interests.value = signals.topInterests()
        val aff = signals.affinities().tags
        _engineInterests.value = signals.engine.use { it.describe(aff) }
        _tasteMap.value = signals.engine.use { it.tasteMap(aff) }
        _eye.value = signals.engine.use { it.visual.describe() }
        _landing.value = signals.engine.use { it.landing(14) }
        _taste.value = signals.engine.use { e ->
            e.snapshot(aff)
            TasteHistory(e.history.entries.toList().takeLast(30).map { it.key to it.value }, HashMap(e.historyNames))
        }
    }

    /** Each day's shares of For You by interest id, and the interests' names. */
    data class TasteHistory(val days: List<Pair<Int, List<Pair<Int, Float>>>>, val names: Map<Int, List<String>>)
    private val _taste = MutableStateFlow(TasteHistory(emptyList(), emptyMap()))
    val tasteHistory: StateFlow<TasteHistory> = _taste

    private val _landing = MutableStateFlow<List<Double?>>(emptyList())
    /** Share of For You's picks that landed, day by day, last 14 days (null: nothing watched). */
    val landing: StateFlow<List<Double?>> = _landing

    private val _eye = MutableStateFlow<Pair<List<String>, List<String>>>(emptyList<String>() to emptyList())
    /** What looks you stay for and don't (see [dev.dyfeed.domain.reco.Visual.Taste.describe]). */
    val eye: StateFlow<Pair<List<String>, List<String>>> = _eye

    private val _tasteMap = MutableStateFlow<dev.dyfeed.domain.reco.TasteEngine.TasteMap?>(null)
    /** Your taste as a picture (see [dev.dyfeed.domain.reco.TasteEngine.tasteMap]). */
    val tasteMap: StateFlow<dev.dyfeed.domain.reco.TasteEngine.TasteMap?> = _tasteMap

    /** The taste engine's interests, as named groups with their share of For You. */
    private val _engineInterests = MutableStateFlow<List<dev.dyfeed.domain.reco.TasteEngine.View>>(emptyList())
    val engineInterests: StateFlow<List<dev.dyfeed.domain.reco.TasteEngine.View>> = _engineInterests

    /** Your taste as a code to give someone (see [dev.dyfeed.domain.reco.TasteCode]). */
    suspend fun tasteCode(): String? = dev.dyfeed.domain.reco.TasteCode.encode(signals.affinities().tags)

    /** Blend someone's taste code into For You for this sitting. False if it isn't a code. */
    fun blendWith(text: String): Boolean {
        val tags = dev.dyfeed.domain.reco.TasteCode.decode(text) ?: return false
        dev.dyfeed.data.repo.SteerState.set(dev.dyfeed.domain.reco.TasteCode.blend(tags))
        return true
    }

    /** Play one interest as its own mix: For You steered toward its tags for a while. */
    fun playMix(view: dev.dyfeed.domain.reco.TasteEngine.View) {
        dev.dyfeed.data.repo.SteerState.set(view.tags.take(3).map { dev.dyfeed.domain.reco.Steer.Directive(dev.dyfeed.domain.reco.Steer.Kind.MORE, it) })
    }

    fun lessOfInterest(index: Int) = viewModelScope.launch { signals.engine.use { it.quieten(index) }; loadInterests() }
    fun forgetEngineInterest(index: Int) = viewModelScope.launch { signals.engine.use { it.forget(index) }; loadInterests() }

    fun addInterests(topics: List<String>) = viewModelScope.launch {
        topics.forEach { runCatching { signals.recordInterest(it) } }
        _interests.value = signals.topInterests()
    }

    fun forgetInterest(feature: String) = viewModelScope.launch {
        signals.forget(feature)
        _interests.value = signals.topInterests()
    }

    fun resetRecommendations() = viewModelScope.launch {
        signals.reset()
        _interests.value = signals.topInterests()
        _messages.tryEmit("For You will start learning again")
    }

    // ---- sources ----
    fun setSourceEnabled(id: String, enabled: Boolean) = viewModelScope.launch { registry.setEnabled(id, enabled) }
    fun moveSource(id: String, delta: Int) = viewModelScope.launch { registry.move(id, delta) }

    fun authenticate(activity: Activity, id: String) {
        val adapter = registry.byId(id) ?: return
        _authStatus.update { it + (id to "Signing in…") }
        viewModelScope.launch {
            val status = when (val r = adapter.authenticate(AuthContext(activity))) {
                AuthResult.Success -> "Signed in"
                AuthResult.NotRequired -> "No sign-in needed"
                is AuthResult.Failure -> r.message
            }
            _authStatus.update { it + (id to status) }
        }
    }


    fun addDirect(url: String): Boolean {
        val u = url.trim().toHttpUrlOrNull() ?: return false.also { _messages.tryEmit("That isn't a valid http(s) URL") }
        update { s -> if (u.toString() in s.directUrls) s else s.copy(directUrls = s.directUrls + u.toString()) }
        return true
    }
    fun removeDirect(url: String) = update { it.copy(directUrls = it.directUrls - url) }

    fun addFolder(uri: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { _messages.tryEmit("Couldn't keep access to that folder"); return }
        update { s -> if (uri.toString() in s.localFolders) s else s.copy(localFolders = s.localFolders + uri.toString()) }
    }
    fun removeFolder(uri: String) {
        runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        update { it.copy(localFolders = it.localFolders - uri) }
    }

    // ---- blocklist ----
    fun block(kind: BlockKind, value: String) = viewModelScope.launch { blocks.block(kind, value) }
    fun unblock(row: BlockEntity) = viewModelScope.launch { blocks.unblock(row.kind, row.value) }
    fun unblockOrigin(origin: String) = viewModelScope.launch { blocks.unblock(BlockKind.ORIGIN.name, dev.dyfeed.domain.filter.Blocklist.normalize(origin)) }
    fun clearNotInterested() = viewModelScope.launch { blocks.clearNotInterested() }

    // ---- export / import ----
    fun exportSettings(uri: Uri, password: String) = viewModelScope.launch {
        runCatching {
            app.contentResolver.openOutputStream(uri, "wt")?.use { exporter.exportTo(it, password.toCharArray()) }
                ?: error("Couldn't open file")
        }.onSuccess { _messages.tryEmit("Backup exported") }
            .onFailure { _messages.tryEmit("Export failed: ${it.message}") }
    }

    // ---- emergency erase PIN ----
    private val _erasePinOn = kotlinx.coroutines.flow.MutableStateFlow(lock.hasErasePin)
    val erasePinOn: kotlinx.coroutines.flow.StateFlow<Boolean> = _erasePinOn

    fun setErasePin(pin: String?) = viewModelScope.launch {
        if (pin != null && (pin.length < 4 || !pin.all(Char::isDigit))) { _messages.tryEmit("Use at least 4 digits"); return@launch }
        val ok = lock.setErasePin(pin?.toCharArray())
        _erasePinOn.value = lock.hasErasePin
        _messages.tryEmit(
            when {
                !ok -> "It can't be the same as your PIN"
                pin == null -> "Emergency PIN removed"
                else -> "Emergency PIN set. Entering it on the lock screen erases everything"
            },
        )
    }

    // ---- automatic weekly backups ----
    private val autoPrefs = app.getSharedPreferences(dev.dyfeed.work.BackupWorker.PREFS, android.content.Context.MODE_PRIVATE)

    /** On (a folder chosen), when the last one was written, and what went wrong last time. */
    data class AutoBackup(val folder: Uri?, val last: Long, val error: String?)

    private val _autoBackup = kotlinx.coroutines.flow.MutableStateFlow(readAutoBackup())
    val autoBackup: kotlinx.coroutines.flow.StateFlow<AutoBackup> = _autoBackup

    private fun readAutoBackup() = AutoBackup(
        autoPrefs.getString(dev.dyfeed.work.BackupWorker.KEY_FOLDER, null)?.let(Uri::parse),
        autoPrefs.getLong(dev.dyfeed.work.BackupWorker.KEY_LAST, 0L),
        autoPrefs.getString(dev.dyfeed.work.BackupWorker.KEY_ERROR, null),
    )

    fun refreshAutoBackup() { _autoBackup.value = readAutoBackup() }

    fun startAutoBackup(folder: Uri, password: String) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(folder, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        secrets.putSecret(dev.dyfeed.work.BackupWorker.SECRET, password)
        autoPrefs.edit().putString(dev.dyfeed.work.BackupWorker.KEY_FOLDER, folder.toString()).apply()
        dev.dyfeed.work.BackupWorker.schedule(app, on = true)
        refreshAutoBackup()
        _messages.tryEmit("Backing up every week; the first one is being written now")
    }

    fun stopAutoBackup() {
        autoPrefs.getString(dev.dyfeed.work.BackupWorker.KEY_FOLDER, null)?.let { f ->
            runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(f), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        secrets.putSecret(dev.dyfeed.work.BackupWorker.SECRET, null)
        autoPrefs.edit().clear().apply()
        dev.dyfeed.work.BackupWorker.schedule(app, on = false)
        refreshAutoBackup()
    }

    fun importSettings(uri: Uri, password: String) = viewModelScope.launch {
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { exporter.importFrom(it, password.toCharArray()) }
                ?: error("Couldn't open file")
        }.onSuccess {
            LauncherDisguise.apply(app, settingsRepo.get().disguise)
            _messages.tryEmit("Backup imported")
        }.onFailure { e ->
            _messages.tryEmit(if (e is WrongPasswordException) "Wrong password" else "Import failed: ${e.message}")
        }
    }
}
