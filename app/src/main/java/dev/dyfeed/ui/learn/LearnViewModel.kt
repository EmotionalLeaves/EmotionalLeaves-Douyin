package dev.dyfeed.ui.learn

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dyfeed.data.repo.LibraryKind
import dev.dyfeed.data.repo.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class LearnViewModel @Inject constructor(
    library: LibraryRepository,
    private val registry: dev.dyfeed.source.SourceRegistry,
) : ViewModel() {
    /** Pictures of posts already seen on [topic] (from the remembered index), for its card. */
    suspend fun thumbs(topic: String): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        (registry.byId(dev.dyfeed.source.douyin.DouyinParser.SOURCE_ID) as? dev.dyfeed.source.douyin.DouyinAdapter)
            ?.loadedMatching(topic).orEmpty().mapNotNull { it.thumbnailUrl }.distinct().take(3)
    }

    /** What China is talking about today (Douyin's trending list): real, current Chinese to learn from. */
    suspend fun trending(): List<String> =
        (registry.byId(dev.dyfeed.source.douyin.DouyinParser.SOURCE_ID) as? dev.dyfeed.source.douyin.DouyinAdapter)
            ?.let { runCatching { it.trending() }.getOrDefault(emptyList()) }.orEmpty()

    /** Captions of what you've watched lately (and liked, and saved): where your words come from. */
    val captions: Flow<List<String>> = kotlinx.coroutines.flow.combine(
        library.items(LibraryKind.HISTORY),
        library.items(LibraryKind.LIKED),
        library.items(LibraryKind.SAVED),
        dev.dyfeed.domain.learn.Heard.lines,
    ) { h, l, s, heard -> (h.take(300) + l + s).mapNotNull { it.title }.filter { dev.dyfeed.domain.lang.Zh.has(it) }.distinct() + heard }
}
