package snd.komelia.ui.common.images

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.ui.LocalAnimatedVisibilityScope
import snd.komelia.ui.LocalKomgaEvents
import snd.komelia.ui.LocalSharedTransitionScope
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.sse.KomgaEvent.ThumbnailBookEvent
import snd.komga.client.sse.KomgaEvent.ThumbnailSeriesEvent

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SeriesThumbnail(
    seriesId: KomgaSeriesId,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val komgaEvents = LocalKomgaEvents.current
    var requestData by remember(seriesId) { mutableStateOf(SeriesDefaultThumbnailRequest(seriesId)) }

    LaunchedEffect(seriesId) {
        komgaEvents.collect {
            val eventSeriesId = when (it) {
                is ThumbnailSeriesEvent -> it.seriesId
                is ThumbnailBookEvent -> it.seriesId
                else -> null
            }
            if (eventSeriesId == seriesId) {
                requestData = SeriesDefaultThumbnailRequest(seriesId)
            }
        }
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val inSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "cover-${seriesId.value}"),
                animatedVisibilityScope = animatedVisibilityScope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = { _, _ -> tween(durationMillis = 600, easing = emphasizedEasing) },
                renderInOverlayDuringTransition = false,
            )
        }
    } else Modifier

    ThumbnailImage(
        data = requestData,
        cacheKey = seriesId.value,
        contentScale = contentScale,
        crossfade = !inSharedTransition,
        modifier = modifier.then(sharedModifier),
    )
}

