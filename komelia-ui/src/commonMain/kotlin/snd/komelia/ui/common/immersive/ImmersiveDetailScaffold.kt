package snd.komelia.ui.common.immersive

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.common.images.ThumbnailImage
import kotlin.math.roundToInt

private enum class CardDragValue { COLLAPSED, EXPANDED }

private class DirectionalSnapSpec : AnimationSpec<Float> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Float, V>
    ): VectorizedAnimationSpec<V> {
        val expandSpec = tween<Float>(
            durationMillis = 500,
            easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        ).vectorize(converter)
        val collapseSpec = tween<Float>(
            durationMillis = 200,
            easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
        ).vectorize(converter)
        return object : VectorizedAnimationSpec<V> {
            override val isInfinite = false
            private fun pick(initialValue: V, targetValue: V) =
                if (converter.convertFromVector(targetValue) < converter.convertFromVector(initialValue)) expandSpec else collapseSpec
            override fun getDurationNanos(initialValue: V, initialVelocity: V, targetValue: V) =
                pick(initialValue, targetValue).getDurationNanos(initialValue, initialVelocity, targetValue)
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V) =
                pick(initialValue, targetValue).getValueFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)
            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V) =
                pick(initialValue, targetValue).getVelocityFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImmersiveDetailScaffold(
    coverData: Any,
    coverKey: String,
    cardColor: Color?,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    topBarContent: @Composable () -> Unit,
    fabContent: @Composable () -> Unit,
    cardContent: @Composable ColumnScope.(expandFraction: Float) -> Unit,
) {
    val density = LocalDensity.current
    val backgroundColor = cardColor ?: MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val collapsedOffset = screenHeight * 0.65f
        val collapsedOffsetPx = with(density) { collapsedOffset.toPx() }

        // Persist expanded/collapsed across back-navigation
        var savedExpanded by rememberSaveable { mutableStateOf(false) }

        val state = remember(collapsedOffsetPx) {
            AnchoredDraggableState(
                initialValue = if (savedExpanded) CardDragValue.EXPANDED else CardDragValue.COLLAPSED,
                anchors = DraggableAnchors {
                    CardDragValue.COLLAPSED at collapsedOffsetPx
                    CardDragValue.EXPANDED at 0f
                },
                positionalThreshold = { d -> d * 0.5f },
                velocityThreshold = { with(density) { 100.dp.toPx() } },
                // M3 Emphasize Decelerate (expand, 500ms) / Emphasize Accelerate (collapse, 200ms)
                snapAnimationSpec = DirectionalSnapSpec(),
                decayAnimationSpec = exponentialDecay(),
            )
        }

        val cardOffsetPx = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
        val expandFraction = (1f - cardOffsetPx / collapsedOffsetPx).coerceIn(0f, 1f)

        var innerScrollPx by rememberSaveable { mutableFloatStateOf(0f) }

        LaunchedEffect(state.currentValue) {
            savedExpanded = state.currentValue == CardDragValue.EXPANDED
            if (state.currentValue == CardDragValue.COLLAPSED) innerScrollPx = 0f
        }

        val nestedScrollConnection = remember(state) {
            object : NestedScrollConnection {
                var preScrollConsumedY = 0f
                var lastGestureWasExpand = false

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    val delta = available.y
                    return if (delta < 0 && currentOffset > 0f) {
                        val consumed = state.dispatchRawDelta(delta)
                        preScrollConsumedY = consumed
                        if (consumed != 0f) lastGestureWasExpand = true
                        Offset(0f, consumed)
                    } else {
                        preScrollConsumedY = 0f
                        Offset.Zero
                    }
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val innerConsumedY = consumed.y - preScrollConsumedY
                    if (innerConsumedY != 0f)
                        innerScrollPx = (innerScrollPx - innerConsumedY).coerceAtLeast(0f)

                    val delta = available.y
                    return if (delta > 0 && source == NestedScrollSource.UserInput) {
                        val cardConsumed = state.dispatchRawDelta(delta)
                        if (cardConsumed != 0f) lastGestureWasExpand = false
                        Offset(0f, cardConsumed)
                    } else Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    if (currentOffset <= 0f || currentOffset >= collapsedOffsetPx) return Velocity.Zero

                    return when {
                        available.y > 0f -> {
                            // Downward fling: snap to COLLAPSED
                            state.settle(available.y)
                            available
                        }
                        available.y < 0f || lastGestureWasExpand -> {
                            // Upward fling OR last drag was expanding: snap to EXPANDED
                            state.settle(-1000f)
                            available
                        }
                        else -> {
                            // Slow stop after a collapse drag: settle by positional threshold
                            state.settle(0f)
                            Velocity.Zero
                        }
                    }
                }
            }
        }

        val topCornerRadiusDp = lerp(28f, 0f, expandFraction).dp
        val statusBarDp = LocalRawStatusBarHeight.current
        val statusBarPx = with(density) { statusBarDp.toPx() }

        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {

            // Layer 1: Cover image — fades out as card expands
            // Extends by the card corner radius so it fills behind the rounded corners
            // When immersive=true, shifts up behind the status bar
            ThumbnailImage(
                data = coverData,
                cacheKey = coverKey,
                contentScale = ContentScale.Crop,
                modifier = if (immersive)
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, -statusBarPx.roundToInt()) }
                        .height(collapsedOffset + topCornerRadiusDp + statusBarDp)
                        .graphicsLayer { alpha = 1f - expandFraction }
                else
                    Modifier
                        .fillMaxWidth()
                        .height(collapsedOffset + topCornerRadiusDp)
                        .graphicsLayer { alpha = 1f - expandFraction }
            )

            // Layer 2: Card
            val cardShape = RoundedCornerShape(topStart = topCornerRadiusDp, topEnd = topCornerRadiusDp)
            Column(
                modifier = Modifier
                    .offset { IntOffset(0, cardOffsetPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(screenHeight)
                    .nestedScroll(nestedScrollConnection)
                    .anchoredDraggable(state, Orientation.Vertical, enabled = innerScrollPx <= 0f)
                    .shadow(elevation = 2.dp, shape = cardShape)
                    .clip(cardShape)
                    .background(backgroundColor)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    cardContent(expandFraction)
                }
            }

            // Layer 3: Thumbnail — fades in as card expands, moves with the card
            // Positioned at card top + drag handle (28dp) + small gap (8dp), left-aligned with 16dp margin
            val thumbAlpha = (expandFraction * 2f - 1f).coerceIn(0f, 1f)
            // Clip container: top edge sits at status bar bottom; clipToBounds hides thumbnail above it
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight - statusBarDp)
                    .offset { IntOffset(0, statusBarPx.roundToInt()) }
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = with(density) { 16.dp.toPx() }.roundToInt(),
                                y = (cardOffsetPx + with(density) { (28.dp + 20.dp).toPx() } - innerScrollPx - statusBarPx)
                                    .roundToInt()
                            )
                        }
                        .graphicsLayer { alpha = thumbAlpha }
                ) {
                    ThumbnailImage(
                        data = coverData,
                        cacheKey = coverKey,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 110.dp, height = (110.dp / 0.703f))
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            // Layer 4: FAB — fixed at bottom, always visible, above system nav bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                fabContent()
            }

            // Layer 5: Top bar
            Box(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                topBarContent()
            }
        }
    }
}
