package snd.komelia.ui.reader.epub.audio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import org.readium.r2.shared.publication.Locator
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalImmersiveColorAlpha
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komga.client.book.KomgaBookId
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudioFullScreenPlayer(
    controller: MediaOverlayController,
    bookId: KomgaBookId,
    bookTitle: String,
    chapterTitle: String,
    positions: List<Locator>,
    currentLocator: Locator?,
    onNavigateToPosition: (Int) -> Unit,
    onDismiss: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val volume by controller.volume.collectAsState()
    val accentColor = LocalAccentColor.current

    val coverRequest = remember(bookId) { BookDefaultThumbnailRequest(bookId) }
    val coverPainter = rememberAsyncImagePainter(model = coverRequest)
    var dominantColor by remember(bookId) { mutableStateOf<Color?>(null) }
    LaunchedEffect(bookId) { dominantColor = extractDominantColor(coverPainter) }

    val immersiveEnabled = LocalImmersiveColorEnabled.current
    val immersiveAlpha = LocalImmersiveColorAlpha.current
    val surface = MaterialTheme.colorScheme.surface
    val backgroundColor = remember(dominantColor, immersiveEnabled, immersiveAlpha) {
        if (immersiveEnabled && dominantColor != null)
            dominantColor!!.copy(alpha = immersiveAlpha).compositeOver(surface)
        else surface
    }

    val dragAnimatable = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val currentIndex = remember(currentLocator, positions) {
        positions.indexOfFirst { it.href == currentLocator?.href }.coerceAtLeast(0)
    }
    var sliderDraft by remember(currentIndex) { mutableStateOf(currentIndex.toFloat()) }

    val containerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    // The modifier fills the available space from the parent (Alignment.BottomCenter in the Box).
    // The Surface itself wraps its content height — it's a bottom sheet, not a true full-screen
    // composable. The sharedBounds animates from pill bounds to sheet bounds.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        with(sharedTransitionScope) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Bottom)
                    // layout {} shifts the Surface's actual layout position when dragging.
                    // Placed BEFORE sharedBounds so it captures the current drag position —
                    // the container-transform transition starts from where the finger is.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(
                                0,
                                dragAnimatable.value.roundToInt().coerceAtLeast(0),
                            )
                        }
                    }
                    .sharedBounds(
                        rememberSharedContentState(key = "audio-player-surface-${bookId.value}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        enter = fadeIn(tween(400, easing = emphasizedEasing)),
                        exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                        boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        clipInOverlayDuringTransition = OverlayClip(containerShape),
                    )
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (dragAnimatable.value > 120f) {
                                        // sharedBounds already captured the offset position
                                        // (via the layout {} above). Trigger onDismiss directly
                                        // so the transition starts from the drag position.
                                        onDismiss()
                                    } else {
                                        dragAnimatable.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                            },
                            onVerticalDrag = { _, delta ->
                                coroutineScope.launch {
                                    dragAnimatable.snapTo(
                                        (dragAnimatable.value + delta).coerceAtLeast(0f)
                                    )
                                }
                            },
                        )
                    },
                shape = containerShape,
                color = backgroundColor,
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }

                    // Cover image — sharedBounds morphs the small pill thumbnail to large square
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .aspectRatio(1f)
                            .sharedBounds(
                                rememberSharedContentState(key = "audio-cover-${bookId.value}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                enter = fadeIn(tween(400, easing = emphasizedEasing)),
                                exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                                boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                            ),
                    ) {
                        ThumbnailImage(
                            data = coverRequest,
                            cacheKey = bookId.value,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Book title
                    Text(
                        text = bookTitle,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .padding(top = 16.dp, bottom = 4.dp)
                            .sharedBounds(
                                rememberSharedContentState(key = "audio-book-title-${bookId.value}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                enter = fadeIn(tween(400, easing = emphasizedEasing)),
                                exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                                boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                            ),
                    )

                    // Chapter title
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .sharedBounds(
                                rememberSharedContentState(key = "audio-chapter-title-${bookId.value}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                enter = fadeIn(tween(400, easing = emphasizedEasing)),
                                exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                                boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                            ),
                    )

                    // Page slider
                    if (positions.size > 1) {
                        AppSlider(
                            value = sliderDraft,
                            onValueChange = { sliderDraft = it },
                            onValueChangeFinished = { onNavigateToPosition(sliderDraft.roundToInt()) },
                            valueRange = 0f..(positions.size - 1).toFloat(),
                            accentColor = accentColor,
                            colors = AppSliderDefaults.colors(accentColor = accentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .padding(top = 16.dp),
                        )
                    }

                    // Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        IconButton(onClick = controller::seekToPrevClip) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous segment")
                        }
                        FilledIconButton(
                            onClick = controller::togglePlayPause,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        IconButton(onClick = controller::seekToNextClip) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next segment")
                        }
                    }

                    // Volume
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .padding(top = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.VolumeDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        AppSlider(
                            value = volume,
                            onValueChange = controller::setVolume,
                            valueRange = 0f..1f,
                            accentColor = accentColor,
                            colors = AppSliderDefaults.colors(accentColor = accentColor),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
