package snd.komelia.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.platform.cursorForHand
import kotlin.math.roundToInt

private val navBarPresets: List<Pair<Color?, String>> = listOf(
    null to "Auto",
    Color(0xFF2D3436.toInt()) to "Charcoal",
    Color(0xFF1A1A2E.toInt()) to "Navy",
    Color(0xFF0D3B46.toInt()) to "D.Teal",
    Color(0xFF1B4332.toInt()) to "Forest",
    Color(0xFF3D1A78.toInt()) to "Violet",
    Color(0xFF3B82F6.toInt()) to "Blue",
    Color(0xFF14B8A6.toInt()) to "Teal",
    Color(0xFF8B5CF6.toInt()) to "Purple",
    Color(0xFFEC4899.toInt()) to "Pink",
    Color(0xFFF97316.toInt()) to "Orange",
    Color(0xFF22C55E.toInt()) to "Green",
)

private val accentPresets: List<Pair<Color?, String>> = listOf(
    null to "Auto",
    Color(0xFF3B82F6.toInt()) to "Blue",
    Color(0xFF14B8A6.toInt()) to "Teal",
    Color(0xFF8B5CF6.toInt()) to "Purple",
    Color(0xFFEC4899.toInt()) to "Pink",
    Color(0xFFF97316.toInt()) to "Orange",
    Color(0xFF22C55E.toInt()) to "Green",
    Color(0xFF2D3436.toInt()) to "Charcoal",
    Color(0xFF0D3B46.toInt()) to "D.Teal",
    Color(0xFF1A1A2E.toInt()) to "Navy",
    Color(0xFF1B4332.toInt()) to "Forest",
    Color(0xFF3D1A78.toInt()) to "Violet",
)

@Composable
fun AppearanceSettingsContent(
    cardWidth: Dp,
    onCardWidthChange: (Dp) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    navBarColor: Color?,
    onNavBarColorChange: (Color?) -> Unit,
    accentColor: Color?,
    onAccentColorChange: (Color?) -> Unit,
    useNewLibraryUI: Boolean,
    onUseNewLibraryUIChange: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val strings = LocalStrings.current.settings

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("New library UI", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Floating nav bar, Keep Reading panel, and pill-shaped tabs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = useNewLibraryUI,
                onCheckedChange = onUseNewLibraryUIChange,
                modifier = Modifier.cursorForHand(),
            )
        }

        HorizontalDivider()

        DropdownChoiceMenu(
            label = { Text(strings.appTheme) },
            selectedOption = LabeledEntry(currentTheme, strings.forAppTheme(currentTheme)),
            options = AppTheme.entries.map { LabeledEntry(it, strings.forAppTheme(it)) },
            onOptionChange = { onThemeChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp)
        )

        if (useNewLibraryUI) {
            HorizontalDivider()

            Text("Nav Bar Color", modifier = Modifier.padding(10.dp))
            ColorSwatchRow(
                presets = navBarPresets,
                selectedColor = navBarColor,
                onColorSelected = onNavBarColorChange,
            )

            HorizontalDivider()

            Text("Accent Color (chips & tabs)", modifier = Modifier.padding(10.dp))
            ColorSwatchRow(
                presets = accentPresets,
                selectedColor = accentColor,
                onColorSelected = onAccentColorChange,
            )
        }

        HorizontalDivider()

        Text(strings.imageCardSize, modifier = Modifier.padding(10.dp))
        Slider(
            value = cardWidth.value,
            onValueChange = { onCardWidthChange(it.roundToInt().dp) },
            steps = 24,
            valueRange = 100f..350f,
            colors = AppSliderDefaults.colors(),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("${cardWidth.value}")

            Card(
                Modifier
                    .width(cardWidth)
                    .aspectRatio(0.703f)
            ) {
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    presets: List<Pair<Color?, String>>,
    selectedColor: Color?,
    onColorSelected: (Color?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((color, label) in presets) {
            ColorSwatch(
                color = color,
                label = label,
                isSelected = color == selectedColor,
                onClick = { onColorSelected(color) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color?,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val swatchColor = color ?: MaterialTheme.colorScheme.surfaceVariant
        val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(2.dp, borderColor, CircleShape)
                .clickable { onClick() }
                .cursorForHand(),
        ) {
            if (color == null) {
                Text(
                    "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
