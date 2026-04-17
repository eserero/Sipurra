package snd.komelia.ui.reader.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation

/**
 * Transparent overlay composable that renders pin markers for comic annotations.
 * Should be placed in a Box that fills the same area as the page image.
 */
@Composable
fun ComicAnnotationOverlay(
    annotations: List<BookAnnotation>,
    imageBounds: Rect?,
    onAnnotationTap: (BookAnnotation) -> Unit,
) {
    if (imageBounds == null || annotations.isEmpty()) return

    val density = LocalDensity.current
    val pinSizePx = with(density) { 24.dp.toPx() }

    annotations.forEach { annotation ->
        val loc = annotation.location as? AnnotationLocation.ComicLocation ?: return@forEach
        val pinX = imageBounds.left + loc.x * imageBounds.width - pinSizePx / 2
        val pinY = imageBounds.top + loc.y * imageBounds.height - pinSizePx / 2

        Box(
            modifier = Modifier
                .offset { IntOffset(pinX.toInt(), pinY.toInt()) }
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(annotation.highlightColor ?: 0xFFFFEB3B.toInt()).copy(alpha = 0.85f))
                .pointerInput(annotation.id) {
                    detectTapGestures(onTap = { onAnnotationTap(annotation) })
                }
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = "Annotation pin",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
