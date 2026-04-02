package snd.komelia.ui.reader.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.Epub3ColumnCount
import snd.komelia.settings.model.Epub3ReadAloudColor
import snd.komelia.settings.model.Epub3TextAlign
import snd.komelia.settings.model.Epub3Theme
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Epub3SettingsCard(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding(),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY > 120f) onDismiss()
                                else dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }

            // Tab row — sticky, not scrollable
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Appearance") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Font & Text") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Audio") },
                )
            }

            // Tab content — scrollable, fixed height = tallest tab
            TabContentLayout(
                selectedTab = selectedTab,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                tabs = listOf(
                    { AppearanceTab(settings, onSettingsChange, accentColor) },
                    { FontTextTab(settings, onSettingsChange, accentColor) },
                    { AudioTab(settings, onSettingsChange, accentColor) },
                ),
            )
        }
    }
}

@Composable
private fun TabContentLayout(
    selectedTab: Int,
    modifier: Modifier = Modifier,
    tabs: List<@Composable () -> Unit>,
) {
    Layout(
        content = { tabs.forEach { it() } },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val maxHeight = placeables.maxOf { it.height }
        layout(constraints.maxWidth, maxHeight) {
            placeables[selectedTab].placeRelative(0, 0)
        }
    }
}

@Composable
private fun AppearanceTab(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    accentColor: Color,
) {
    Column {
        // Theme chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Epub3Theme.entries.forEach { theme ->
                ThemeChip(
                    theme = theme,
                    selected = settings.theme == theme,
                    accentColor = accentColor,
                    onClick = { onSettingsChange(settings.copy(theme = theme)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Reading Mode
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Reading Mode", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    selected = !settings.scroll,
                    onClick = { onSettingsChange(settings.copy(scroll = false)) },
                    label = { Text("Page") },
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    selected = settings.scroll,
                    onClick = { onSettingsChange(settings.copy(scroll = true)) },
                    label = { Text("Scroll") },
                )
            }
        }

        // Column count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Columns", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            val colOptions = listOf(
                Epub3ColumnCount.AUTO to "Auto",
                Epub3ColumnCount.ONE  to "1",
                Epub3ColumnCount.TWO  to "2",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                colOptions.forEachIndexed { i, (col, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        selected = settings.columnCount == col,
                        onClick = { onSettingsChange(settings.copy(columnCount = col)) },
                        label = { Text(label) },
                    )
                }
            }
        }

        // Page margins slider
        SliderRow(
            label = "Margins",
            valueLabel = "${"%.1f".format(settings.pageMargins)}×",
            value = settings.pageMargins.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(pageMargins = it.toDouble())) },
            valueRange = 0.5f..2.0f,
            accentColor = accentColor,
        )

        val screenHeight = LocalConfiguration.current.screenHeightDp.toFloat()
        val maxMargin = screenHeight * 0.2f

        // Top margin slider
        SliderRow(
            label = "Top margin",
            valueLabel = "${settings.topMargin.toInt()}dp",
            value = settings.topMargin,
            onValueChange = { onSettingsChange(settings.copy(topMargin = it)) },
            valueRange = 0f..maxMargin,
            accentColor = accentColor,
        )

        // Bottom margin slider
        SliderRow(
            label = "Bottom margin",
            valueLabel = "${settings.bottomMargin.toInt()}dp",
            value = settings.bottomMargin,
            onValueChange = { onSettingsChange(settings.copy(bottomMargin = it)) },
            valueRange = 0f..maxMargin,
            accentColor = accentColor,
        )

        // Read-aloud highlight
        Text(
            text = "Read-aloud highlight",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Epub3ReadAloudColor.entries.forEach { c ->
                val solidColor = Color(c.colorInt.toLong() or 0xFF000000L)
                val selected = settings.readAloudColor == c
                FilterChip(
                    selected = selected,
                    onClick = { onSettingsChange(settings.copy(readAloudColor = c)) },
                    modifier = Modifier.then(
                        if (selected) Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                        else Modifier
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = solidColor.copy(alpha = 0.5f),
                        containerColor = solidColor.copy(alpha = 0.2f),
                    ),
                    label = {},
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(solidColor.copy(alpha = 0.8f))
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FontTextTab(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    accentColor: Color,
) {
    Column {
        // Font family
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Font", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                listOf("Literata", "OpenDyslexic").forEachIndexed { i, name ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        selected = settings.fontFamily == name,
                        onClick = { onSettingsChange(settings.copy(fontFamily = name)) },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        // Text alignment
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Align", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
            val alignOptions = listOf(
                Epub3TextAlign.JUSTIFY to Icons.Default.FormatAlignJustify,
                Epub3TextAlign.LEFT    to Icons.AutoMirrored.Filled.FormatAlignLeft,
                Epub3TextAlign.CENTER  to Icons.Default.FormatAlignCenter,
                Epub3TextAlign.RIGHT   to Icons.AutoMirrored.Filled.FormatAlignRight,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                alignOptions.forEachIndexed { i, (align, icon) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(i, 4),
                        selected = settings.textAlign == align,
                        onClick = { onSettingsChange(settings.copy(textAlign = align)) },
                        label = { Icon(icon, contentDescription = align.name) },
                    )
                }
            }
        }

        // Font size slider
        SliderRow(
            label = "Font size",
            valueLabel = "${(settings.fontSize * 100).roundToInt()}%",
            value = settings.fontSize.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(fontSize = it.toDouble())) },
            valueRange = 0.7f..2.0f,
            accentColor = accentColor,
        )

        // Line height slider
        SliderRow(
            label = "Line height",
            valueLabel = "${"%.1f".format(settings.lineHeight)}×",
            value = settings.lineHeight.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(lineHeight = it.toDouble())) },
            valueRange = 1.0f..2.0f,
            accentColor = accentColor,
        )

        // Paragraph spacing slider
        SliderRow(
            label = "Para spacing",
            valueLabel = "${"%.1f".format(settings.paragraphSpacing)}×",
            value = settings.paragraphSpacing.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(paragraphSpacing = it.toDouble())) },
            valueRange = 0.0f..2.0f,
            accentColor = accentColor,
        )

        // Publisher styles toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Publisher styles", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = settings.publisherStyles,
                onCheckedChange = { onSettingsChange(settings.copy(publisherStyles = it)) },
            )
        }
    }
}

@Composable
private fun AudioTab(
    settings: Epub3NativeSettings,
    onSettingsChange: (Epub3NativeSettings) -> Unit,
    accentColor: Color,
) {
    Column {
        // Playback speed slider
        SliderRow(
            label = "Speed",
            valueLabel = "${"%.2f".format(settings.playbackSpeed)}×",
            value = settings.playbackSpeed.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(playbackSpeed = it.toDouble())) },
            valueRange = 0.5f..4.0f,
            accentColor = accentColor,
        )

        // Auto Rewind toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Auto Rewind", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = settings.rewindEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(rewindEnabled = it)) },
            )
        }

        // Conditional rewind sliders
        AnimatedVisibility(visible = settings.rewindEnabled) {
            Column {
                SliderRow(
                    label = "Interruption",
                    valueLabel = "${settings.rewindAfterInterruption.roundToInt()} s",
                    value = settings.rewindAfterInterruption.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(rewindAfterInterruption = it.toDouble())) },
                    valueRange = 0f..30f,
                    accentColor = accentColor,
                )
                SliderRow(
                    label = "Long break",
                    valueLabel = "${settings.rewindAfterBreak.roundToInt()} s",
                    value = settings.rewindAfterBreak.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(rewindAfterBreak = it.toDouble())) },
                    valueRange = 0f..60f,
                    accentColor = accentColor,
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.width(112.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 0,
            accentColor = accentColor,
            colors = AppSliderDefaults.colors(accentColor = accentColor),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ThemeChip(
    theme: Epub3Theme,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val bgColor = Color(theme.background.toLong() or 0xFF000000L)
    val fgColor = Color(theme.foreground.toLong() or 0xFF000000L)
    val borderColor = if (selected) accentColor else outline
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = fgColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = theme.label,
                color = fgColor,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
        }
    }
}
