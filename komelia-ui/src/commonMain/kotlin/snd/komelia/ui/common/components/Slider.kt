package snd.komelia.ui.common.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppSliderDefaults {
    @Composable
    fun colors(
        accentColor: Color? = null,
        thumbColor: Color = accentColor ?: MaterialTheme.colorScheme.tertiaryContainer,
        activeTrackColor: Color = accentColor ?: MaterialTheme.colorScheme.tertiary,
        activeTickColor: Color = (accentColor ?: MaterialTheme.colorScheme.tertiaryContainer).copy(alpha = 0.5f),
        inactiveTrackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        inactiveTickColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        disabledThumbColor: Color = Color.Unspecified,
        disabledActiveTrackColor: Color = Color.Unspecified,
        disabledActiveTickColor: Color = Color.Unspecified,
        disabledInactiveTrackColor: Color = Color.Unspecified,
        disabledInactiveTickColor: Color = Color.Unspecified
    ) = SliderDefaults.colors(
        thumbColor = thumbColor,
        activeTrackColor = activeTrackColor,
        activeTickColor = activeTickColor,
        inactiveTrackColor = inactiveTrackColor,
        inactiveTickColor = inactiveTickColor,
        disabledThumbColor = disabledThumbColor,
        disabledActiveTrackColor = disabledActiveTrackColor,
        disabledActiveTickColor = disabledActiveTickColor,
        disabledInactiveTrackColor = disabledInactiveTrackColor,
        disabledInactiveTickColor = disabledInactiveTickColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accentColor: Color? = null,
    colors: SliderColors = AppSliderDefaults.colors(accentColor),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    val thumbWidth by animateDpAsState(
        targetValue = if (isInteracting) 2.dp else 4.dp,
        label = "ThumbWidthAnimation"
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            val thumbColor = if (enabled) {
                accentColor ?: MaterialTheme.colorScheme.tertiaryContainer
            } else {
                Color.Unspecified
            }

            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = 44.dp)
                    .background(thumbColor, RoundedCornerShape(50))
            )
        }
    )
}