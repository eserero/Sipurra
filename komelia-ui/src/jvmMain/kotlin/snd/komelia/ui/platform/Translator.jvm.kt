package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberTranslator(): (String) -> Unit {
    return { _ -> }
}
