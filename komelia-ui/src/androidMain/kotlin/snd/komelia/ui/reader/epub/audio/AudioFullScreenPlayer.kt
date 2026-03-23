package snd.komelia.ui.reader.epub.audio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Locator
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.accentFilterChipColors
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komelia.ui.reader.epub.Epub3PageNavigatorRow
import snd.komga.client.book.KomgaBookId

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

private fun formatHMS(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatTimeLeft(seconds: Double): String = "−" + formatHMS(seconds)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudioFullScreenPlayer(
    controller: MediaOverlayController,
    bookId: KomgaBookId,
    bookTitle: String,
    chapterTitle: String,
    backgroundColor: Color,
    positions: List<Locator>,
    currentLocator: Locator?,
    onNavigateToPosition: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDrag: (fraction: Float) -> Unit,
    onDragEnd: (fraction: Float) -> Unit,
    onChapterClick: () -> Unit,
    playbackSpeed: Double,
    onSpeedChange: (Double) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val volume by controller.volume.collectAsState()
    val elapsedSeconds by controller.elapsedSeconds.collectAsState()
    val totalDurationSeconds by controller.totalDurationSeconds.collectAsState()
    val accentColor = LocalAccentColor.current
    val immersiveColorEnabled = LocalImmersiveColorEnabled.current

    val coverRequest = remember(bookId) { BookDefaultThumbnailRequest(bookId) }

    var playerHeightPx by remember { mutableIntStateOf(1) }

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
                    .onGloballyPositioned { playerHeightPx = it.size.height }
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
                        var dragOffsetY = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val fraction = (dragOffsetY / playerHeightPx).coerceIn(0f, 1f)
                                onDragEnd(fraction)
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                onDragEnd(0f)
                                dragOffsetY = 0f
                            },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                                onDrag((dragOffsetY / playerHeightPx).coerceIn(0f, 1f))
                            },
                        )
                    },
                shape = containerShape,
                color = Color.Transparent,
            ) {
                Box {
                    if (immersiveColorEnabled) {
                        ThumbnailImage(
                            data = coverRequest,
                            cacheKey = bookId.value,
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
                            .background(backgroundColor.copy(alpha = 0.72f)),
                    )
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    with(animatedVisibilityScope) {
                        val fadeModifier = Modifier.animateEnterExit(
                            enter = fadeIn(tween(300, delayMillis = 150)),
                            exit = fadeOut(tween(150)),
                        )

                        // Drag handle
                        Box(
                            modifier = fadeModifier.fillMaxWidth(),
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
                            textAlign = TextAlign.Center,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .padding(top = 16.dp, bottom = 4.dp),
                        )

                        // Chapter title — tap to open TOC
                        SuggestionChip(
                            onClick = onChapterClick,
                            label = {
                                Text(
                                    text = chapterTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                        )

                        // Page slider
                        if (positions.size > 1) {
                            Epub3PageNavigatorRow(
                                positions = positions,
                                currentLocator = currentLocator,
                                onNavigateToPosition = onNavigateToPosition,
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .padding(top = 16.dp),
                            )

                            // Three-column time row: elapsed | time-remaining | total
                            val remaining = (totalDurationSeconds - elapsedSeconds).coerceAtLeast(0.0)
                            Row(
                                modifier = fadeModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatHMS(elapsedSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatTimeLeft(remaining),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatHMS(totalDurationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = fadeModifier
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
                            modifier = fadeModifier
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

                        // Speed chips — 2 rows with label and accent color
                        val speeds = listOf(1.0, 1.25, 1.5, 1.75, 2.0)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = fadeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp)
                                .padding(top = 8.dp),
                        ) {
                            Text(
                                text = "Speed",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                speeds.chunked(3).forEach { rowSpeeds ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowSpeeds.forEach { speed ->
                                            val selected = kotlin.math.abs(playbackSpeed - speed) < 0.01
                                            FilterChip(
                                                selected = selected,
                                                onClick = { onSpeedChange(speed) },
                                                label = { Text("${speed}×") },
                                                colors = accentFilterChipColors(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                } // Box
            }
        }
    }
}
