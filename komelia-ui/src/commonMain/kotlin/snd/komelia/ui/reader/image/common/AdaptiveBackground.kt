package snd.komelia.ui.reader.image.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AdaptiveBackground(
    edgeColors: Pair<Int, Int>?,
    isVerticalGaps: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val topColor = remember(edgeColors) { edgeColors?.first?.let { Color(it) } ?: Color.Transparent }
    val bottomColor = remember(edgeColors) { edgeColors?.second?.let { Color(it) } ?: Color.Transparent }

    val animatedTop by animateColorAsState(
        targetValue = topColor,
        animationSpec = tween(durationMillis = 500)
    )
    val animatedBottom by animateColorAsState(
        targetValue = bottomColor,
        animationSpec = tween(durationMillis = 500)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (edgeColors != null) {
                    val brush = if (isVerticalGaps) {
                        Brush.verticalGradient(listOf(animatedTop, animatedBottom))
                    } else {
                        Brush.horizontalGradient(listOf(animatedTop, animatedBottom))
                    }
                    drawRect(brush)
                }
            }
    ) {
        content()
    }
}
