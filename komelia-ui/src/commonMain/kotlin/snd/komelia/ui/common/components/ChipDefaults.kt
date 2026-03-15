package snd.komelia.ui.common.components

import androidx.compose.material3.InputChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import snd.komelia.ui.LocalAccentColor

@Composable
fun accentInputChipColors() = run {
    val accentColor = LocalAccentColor.current
    if (accentColor != null) {
        val onAccent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        InputChipDefaults.inputChipColors(
            selectedContainerColor = accentColor,
            selectedLabelColor = onAccent,
            selectedLeadingIconColor = onAccent,
            selectedTrailingIconColor = onAccent,
        )
    } else {
        InputChipDefaults.inputChipColors()
    }
}
