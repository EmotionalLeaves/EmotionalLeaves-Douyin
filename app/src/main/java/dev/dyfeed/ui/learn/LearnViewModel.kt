package dev.dyfeed.ui.learn

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dyfeed.data.repo.LibraryKind
import dev.dyfeed.data.repo.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class LearnViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    /** Captions of what you've watched lately (and liked, and saved): where your words come from. */
    val captions: Flow<List<String>> = kotlinx.coroutines.flow.combine(
        library.items(LibraryKind.HISTORY),
        library.items(LibraryKind.LIKED),
        library.items(LibraryKind.SAVED),
    ) { h, l, s -> (h.take(300) + l + s).mapNotNull { it.title }.filter { dev.dyfeed.domain.lang.Zh.has(it) }.distinct() }
}
