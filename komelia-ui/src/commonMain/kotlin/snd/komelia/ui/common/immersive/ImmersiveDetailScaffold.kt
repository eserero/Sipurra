package snd.komelia.ui.common.immersive

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.snapTo
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import snd.komelia.ui.LocalAnimatedVisibilityScope
import snd.komelia.ui.LocalImmersiveColorAlpha
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.LocalRawNavBarHeight
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalSharedTransitionScope
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

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ImmersiveDetailScaffold(
    coverData: Any,
    coverKey: String,
    cardColor: Color?,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    initiallyExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    publisherLogo: ImageBitmap? = null,
    topBarContent: @Composable () -> Unit,
    fabContent: @Composable () -> Unit,
    cardContent: @Composable ColumnScope.(expandFraction: Float) -> Unit,
) {
    val density = LocalDensity.current
    val immersiveColorEnabled = LocalImmersiveColorEnabled.current
    val immersiveColorAlpha = LocalImmersiveColorAlpha.current
    val surface = MaterialTheme.colorScheme.surface
    val backgroundColor = if (cardColor != null && immersiveColorEnabled) {
        cardColor.copy(alpha = immersiveColorAlpha).compositeOver(surface)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val scrimColor = if (immersiveColorEnabled) backgroundColor.copy(alpha = 0.72f) else backgroundColor

    // Read shared transition scopes OUTSIDE BoxWithConstraints (which uses SubcomposeLayout).
    // SubcomposeLayout defers content composition to the layout phase, so any CompositionLocal
    // reads inside it happen too late for SharedTransitionLayout's composition-phase matching.
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    // Whether a shared transition is in progress — used to suppress crossfade during animation.
    val inSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null

    // Cover image is the shared element — flies from source thumbnail to its destination position.
    // Must be computed outside BoxWithConstraints for composition-phase matching.
    val coverSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "cover-$coverKey"),
                animatedVisibilityScope = animatedVisibilityScope,
                enter = fadeIn(tween(400, easing = emphasizedEasing)),
                exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                boundsTransform = { _, _ -> tween(durationMillis = 600, easing = emphasizedEasing) },
            )
        }
    } else Modifier

    // Scaffold fades in behind the flying cover image and sliding card.
    // No slide here — the card handles its own slide via cardOverlayModifier. A slide on
    // BoxWithConstraints would move the layout positions of all children, causing the card to
    // double-animate and causing the cover sharedBounds destination to shift mid-transition.
    val scaffoldEnterExitModifier = if (animatedVisibilityScope != null) {
        with(animatedVisibilityScope) {
            Modifier.animateEnterExit(
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200, easing = emphasizedAccelerateEasing))
            )
        }
    } else Modifier

    // Card enters in the SharedTransition overlay at z=0.5 — always above the cover image (z=0
    // from sharedBounds default). This prevents the z-flip where the source thumbnail image
    // would otherwise appear on top of the card during the container-transform crossfade.
    val cardOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.5f)
                    .animateEnterExit(
                        enter = slideInVertically(tween(500, easing = emphasizedEasing)) { it / 4 }
                                + fadeIn(tween(300)),
                        exit = slideOutVertically(tween(400, easing = emphasizedAccelerateEasing)) { it / 4 }
                               + fadeOut(tween(300))
                    )
            }
        }
    } else Modifier

    val uiEnterExitModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.75f)
                    .animateEnterExit(
                        enter = fadeIn(tween(durationMillis = 500)),
                        exit = fadeOut(tween(durationMillis = 100))
                    )
            }
        }
    } else Modifier

    val fabOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                    .animateEnterExit(
                        enter = fadeIn(tween(300, delayMillis = 50)),
                        exit = slideOutVertically(tween(200, easing = emphasizedAccelerateEasing)) { it / 2 }
                               + fadeOut(tween(150))
                    )
            }
        }
    } else Modifier

    BoxWithConstraints(modifier = modifier.fillMaxSize().then(scaffoldEnterExitModifier)) {
        val screenHeight = maxHeight
        val statusBarDp = LocalRawStatusBarHeight.current
        val navBarDp    = LocalRawNavBarHeight.current
        val windowHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
        // Use actual window height (invariant to app nav bar showing/hiding) for stable collapsedOffset.
        val stableScreenHeight = windowHeightDp - statusBarDp - navBarDp
        val collapsedOffset = stableScreenHeight * 0.65f
        val collapsedOffsetPx = with(density) { collapsedOffset.toPx() }

        // Use remember (not rememberSaveable) so pager pages don't restore stale saved state.
        var savedExpanded by remember { mutableStateOf(initiallyExpanded) }

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

        // Snap already-composed pages (e.g. adjacent in a pager) when the parent changes the
        // shared expand state. Skips the snap if the card is already in the right position.
        LaunchedEffect(initiallyExpanded) {
            val target = if (initiallyExpanded) CardDragValue.EXPANDED else CardDragValue.COLLAPSED
            if (state.currentValue != target) {
                state.snapTo(target)
            }
            savedExpanded = initiallyExpanded
        }

        LaunchedEffect(state.currentValue) {
            savedExpanded = state.currentValue == CardDragValue.EXPANDED
            onExpandChange(savedExpanded)
        }

        val nestedScrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    return if (delta < 0 && currentOffset > 0f) {
                        state.dispatchRawDelta(delta)
                        Offset(0f, delta)
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    return if (delta > 0 && source == NestedScrollSource.UserInput) {
                        Offset(0f, state.dispatchRawDelta(delta))
                    } else Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    if (available.y < 0f && currentOffset > 0f) {
                        state.settle(-1000f)
                        return available
                    }
                    return Velocity.Zero
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
                        available.y < 0f -> {
                            // Upward fling: snap to EXPANDED
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
        val statusBarPx = with(density) { statusBarDp.toPx() }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

            // Layer 1: Cover image — shared element that flies from source thumbnail.
            // coverSharedModifier placed first so sharedBounds captures the element at its
            // final layout position; geometry modifiers then refine size/offset within that.
            // crossfade suppressed during the transition to avoid the placeholder→loaded flash.
            ThumbnailImage(
                data = coverData,
                cacheKey = coverKey,
                crossfade = !inSharedTransition,
                usePlaceholderKey = false,
                contentScale = ContentScale.Crop,
                modifier = if (immersive)
                    Modifier
                        .then(coverSharedModifier)
                        .fillMaxWidth()
                        .offset { IntOffset(0, -statusBarPx.roundToInt()) }
                        .height(collapsedOffset + topCornerRadiusDp + statusBarDp)
                        .graphicsLayer { alpha = 1f - expandFraction }
                else
                    Modifier
                        .then(coverSharedModifier)
                        .fillMaxWidth()
                        .height(collapsedOffset + topCornerRadiusDp)
                        .graphicsLayer { alpha = 1f - expandFraction }
            )

            // Layer 2: Card — rendered in the SharedTransition overlay at z=0.5, above the
            // cover image (z=0 from sharedBounds default). This ensures the card is always
            // above the cover during the transition; after the transition, both return to
            // normal layout where card (drawn later) is naturally above the cover.
            val cardShape = RoundedCornerShape(topStart = topCornerRadiusDp, topEnd = topCornerRadiusDp)
            Column(
                modifier = Modifier
                    .then(cardOverlayModifier)
                    .offset { IntOffset(0, cardOffsetPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(screenHeight)
                    .nestedScroll(nestedScrollConnection)
                    .anchoredDraggable(state, Orientation.Vertical)
                    .shadow(elevation = 6.dp, shape = cardShape)
                    .clip(cardShape)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (immersiveColorEnabled) {
                        ThumbnailImage(
                            data = coverData,
                            cacheKey = coverKey,
                            contentScale = ContentScale.Crop,
                            placeholder = null,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(radius = 40.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(scrimColor),
                    )
                    Column(modifier = Modifier.fillMaxSize()) {
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
                }
            }

            // Layer 2.5: Publisher hero badge — visible when collapsed, fades with cover
            if (publisherLogo != null) {
                val badgeHeight = 40.dp
                val badgeHeightPx = with(density) { (badgeHeight + 12.dp).toPx() }
                Box(
                    modifier = Modifier
                        .then(uiEnterExitModifier)
                        .offset { IntOffset(
                            x = with(density) { 12.dp.roundToPx() },
                            y = (collapsedOffsetPx - badgeHeightPx).roundToInt()
                        ) }
                        .graphicsLayer { alpha = (1f - expandFraction * 2f).coerceIn(0f, 1f) }
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Image(
                        bitmap = publisherLogo,
                        contentDescription = null,
                        modifier = Modifier.heightIn(min = 28.dp, max = 40.dp).widthIn(min = 60.dp, max = 120.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Layer 3: Top bar
            Box(modifier = Modifier.fillMaxWidth().then(uiEnterExitModifier).statusBarsPadding()) {
                topBarContent()
            }
        }

        // Layer 4: FAB — in overlay at z=1, above everything
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(fabOverlayModifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        ) {
            fabContent()
        }
    }
}
