package snd.komelia.ui.common.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale

@Composable
fun ThumbnailImage(
    data: Any,
    cacheKey: String,
    contentScale: ContentScale = ContentScale.Fit,
    crossfade: Boolean = true,
    usePlaceholderKey: Boolean = true,
    placeholder: Painter? = NoopPainter,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val request = remember(data, cacheKey, crossfade, usePlaceholderKey) {
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(cacheKey)
            .apply { if (usePlaceholderKey) placeholderMemoryCacheKey(cacheKey) }
            .diskCacheKey(cacheKey)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .crossfade(crossfade)
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        placeholder = placeholder,
        contentScale = contentScale,
        filterQuality = FilterQuality.None
    )
}

object NoopPainter : Painter() {
    override val intrinsicSize: Size = Size.Zero
    override fun DrawScope.onDraw() {}
}
