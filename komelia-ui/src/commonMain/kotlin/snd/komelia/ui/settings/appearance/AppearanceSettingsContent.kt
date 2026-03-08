package snd.komelia.ui.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.common.cards.ItemCard
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.platform.cursorForHand
import kotlin.math.roundToInt

private val accentPresets: List<Pair<Color?, String>> = listOf(
    null to "Auto",
    Color(0xFF800020.toInt()) to "Burgundy",
    Color(0xFFE57373.toInt()) to "Muted Red",
    Color(0xFF5783D4.toInt()) to "Secondary Blue",
    Color(0xFF201F23.toInt()) to "Toolbar (Dark)",
    Color(0xFFE1E1E1.toInt()) to "Toolbar (Light)",
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

@Composable
fun AppearanceSettingsContent(
    cardWidth: Dp,
    onCardWidthChange: (Dp) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    accentColor: Color?,
    onAccentColorChange: (Color?) -> Unit,
    useNewLibraryUI: Boolean,
    onUseNewLibraryUIChange: (Boolean) -> Unit,
    cardLayoutBelow: Boolean,
    onCardLayoutBelowChange: (Boolean) -> Unit,
    immersiveColorEnabled: Boolean,
    onImmersiveColorEnabledChange: (Boolean) -> Unit,
    immersiveColorAlpha: Float,
    onImmersiveColorAlphaChange: (Float) -> Unit,
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Card layout", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Show title and metadata below the thumbnail instead of on top",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = cardLayoutBelow,
                onCheckedChange = onCardLayoutBelowChange,
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

            DropdownChoiceMenu(
                label = { Text("Accent Color (chips & tabs)") },
                selectedOption = accentPresets.find { it.first == accentColor }
                    ?.let { LabeledEntry(it.first, it.second) },
                options = accentPresets.map { LabeledEntry(it.first, it.second) },
                onOptionChange = { onAccentColorChange(it.value) },
                inputFieldModifier = Modifier.widthIn(min = 250.dp),
                selectedOptionContent = { ColorLabel(it) },
                optionContent = { ColorLabel(it) }
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Immersive card color", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Tint the detail card background with the cover's dominant color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = immersiveColorEnabled,
                    onCheckedChange = onImmersiveColorEnabledChange,
                    modifier = Modifier.cursorForHand(),
                )
            }

            if (immersiveColorEnabled) {
                Text(
                    "Tint strength: ${(immersiveColorAlpha * 100).roundToInt()}%",
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Slider(
                    value = immersiveColorAlpha,
                    onValueChange = onImmersiveColorAlphaChange,
                    valueRange = 0.05f..0.30f,
                    colors = AppSliderDefaults.colors(accentColor = accentColor),
                    modifier = Modifier.cursorForHand().padding(end = 20.dp),
                )
            }
        }

        HorizontalDivider()

        Text(strings.imageCardSize, modifier = Modifier.padding(10.dp))
        Slider(
            value = cardWidth.value,
            onValueChange = { onCardWidthChange(it.roundToInt().dp) },
            steps = 24,
            valueRange = 100f..350f,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
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

            CompositionLocalProvider(LocalCardLayoutBelow provides cardLayoutBelow) {
                ItemCard(
                    modifier = Modifier.width(cardWidth),
                    image = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Thumbnail")
                        }
                    },
                    content = {
                        if (cardLayoutBelow) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Series Example",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Book Title Example",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorLabel(entry: LabeledEntry<Color?>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val swatchColor = entry.value ?: MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                    if (entry.value == null) Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape
                    )
                    else Modifier
                )
        ) {
            if (entry.value == null) {
                Text(
                    "A",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Text(entry.label)
    }
}
