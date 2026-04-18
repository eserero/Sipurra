package snd.komelia.ui.reader.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import snd.komelia.annotations.AnnotationLocation
import snd.komelia.annotations.BookAnnotation

/**
 * A Google Maps–style teardrop/pin rendered with Canvas.
 *
 * The tip of the pin (bottom-center) is the anchor point — it corresponds to the
 * annotation's exact (x, y) position on the page.  The caller should position the
 * composable so that the tip aligns with the tap point.
 */
@Composable
private fun MapPin(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val r = size.width * 0.38f          // circle radius
        val cy = r + size.width * 0.05f     // circle center Y (slight top offset)

        val path = Path().apply {
            moveTo(cx, size.height)          // bottom tip
            cubicTo(
                cx - r * 0.55f, size.height * 0.72f,
                cx - r,          cy + r * 0.6f,
                cx - r,          cy,
            )
            arcTo(
                rect = Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            cubicTo(
                cx + r,          cy + r * 0.6f,
                cx + r * 0.55f, size.height * 0.72f,
                cx,              size.height,
            )
            close()
        }

        drawPath(path, color = color)

        // Subtle inner circle for depth
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = r * 0.45f,
            center = Offset(cx, cy),
        )
    }
}

/**
 * Transparent overlay composable that renders Google Maps–style pin markers for
 * comic annotations.  Should be placed in a Box that fills the same area as the
 * page image.
 */
@Composable
fun ComicAnnotationOverlay(
    annotations: List<BookAnnotation>,
    imageBounds: Rect?,
    onAnnotationTap: (BookAnnotation) -> Unit,
) {
    if (imageBounds == null || annotations.isEmpty()) return

    val density = LocalDensity.current
    val pinSizeDp = 36.dp
    val pinSizePx = with(density) { pinSizeDp.toPx() }

    annotations.forEach { annotation ->
        val loc = annotation.location as? AnnotationLocation.ComicLocation ?: return@forEach

        // Anchor: the tip (bottom-center) of the pin lands on (locX, locY).
        val locX = imageBounds.left + loc.x * imageBounds.width
        val locY = imageBounds.top + loc.y * imageBounds.height
        val pinLeft = locX - pinSizePx / 2f   // center the pin horizontally
        val pinTop  = locY - pinSizePx         // tip at bottom → shift up by full height

        val pinColor = Color(annotation.highlightColor ?: 0xFFFFEB3B.toInt())

        MapPin(
            color = pinColor,
            modifier = Modifier
                .offset { IntOffset(pinLeft.toInt(), pinTop.toInt()) }
                .size(pinSizeDp)
                .pointerInput(annotation.id) {
                    detectTapGestures(onTap = { onAnnotationTap(annotation) })
                },
        )
    }
}
