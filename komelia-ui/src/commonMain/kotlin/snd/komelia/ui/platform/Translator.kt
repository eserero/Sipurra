package snd.komelia.ui.platform

import androidx.compose.runtime.Composable

@Composable
expect fun rememberTranslator(): (String) -> Unit
