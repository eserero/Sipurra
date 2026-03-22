package snd.komelia.ui.reader.epub.audio

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.ui.common.images.ThumbnailImage
import snd.komga.client.book.KomgaBookId

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AudioMiniPlayer(
    controller: MediaOverlayController,
    bookId: KomgaBookId,
    bookTitle: String,
    chapterTitle: String,
    onCoverClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val isPlaying by controller.isPlaying.collectAsState()
    val pillShape = RoundedCornerShape(50)
    with(sharedTransitionScope) {
        Surface(
            modifier = modifier
                .sharedBounds(
                    rememberSharedContentState(key = "audio-player-surface-${bookId.value}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = fadeIn(tween(400, easing = emphasizedEasing)),
                    exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                    boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    clipInOverlayDuringTransition = OverlayClip(pillShape),
                ),
            shape = pillShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                // Cover image — tapping opens the full-screen player
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                        .sharedBounds(
                            rememberSharedContentState(key = "audio-cover-${bookId.value}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            enter = fadeIn(tween(400, easing = emphasizedEasing)),
                            exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                            boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                        )
                        .size(48.dp)
                        .clickable { onCoverClick() },
                ) {
                    ThumbnailImage(
                        data = remember(bookId) { BookDefaultThumbnailRequest(bookId) },
                        cacheKey = bookId.value,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Book title + chapter
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = bookTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.sharedBounds(
                            rememberSharedContentState(key = "audio-book-title-${bookId.value}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            enter = fadeIn(tween(400, easing = emphasizedEasing)),
                            exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                            boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                        ),
                    )
                    Text(
                        text = chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.sharedBounds(
                            rememberSharedContentState(key = "audio-chapter-title-${bookId.value}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            enter = fadeIn(tween(400, easing = emphasizedEasing)),
                            exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                            boundsTransform = { _, _ -> tween(500, easing = emphasizedEasing) },
                        ),
                    )
                }

                // Controls
                IconButton(onClick = controller::seekToPrevClip) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous segment",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(onClick = controller::togglePlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(onClick = controller::seekToNextClip) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next segment",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
