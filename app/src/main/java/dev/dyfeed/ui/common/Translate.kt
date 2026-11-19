package dev.dyfeed.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.dyfeed.domain.lang.ChineseToEnglish
import dev.dyfeed.domain.lang.Zh


/**
 * English for a bit of Chinese on screen, while "Show Chinese in English" is on; null when it's
 * off, when [text] has no Chinese, or until the translation is ready (shown at once if cached).
 */
@Composable
fun rememberEnglish(text: String?, force: Boolean = false): String? {
    val on by ChineseToEnglish.on.collectAsState()
    val wanted = (on || force) && text != null && Zh.has(text)
    val state by produceState(initialValue = if (wanted) ChineseToEnglish.cached(text!!) else null, text, wanted) {
        value = if (wanted) ChineseToEnglish.translate(text!!).getOrNull()?.takeIf { it.isNotBlank() && it != text } else null
    }
    return state
}
