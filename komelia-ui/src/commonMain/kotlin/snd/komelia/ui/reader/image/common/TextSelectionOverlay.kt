package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import snd.komelia.image.OcrElementBox

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun TextSelectionOverlay(
    modifier: Modifier = Modifier,
    ocrResults: List<OcrElementBox>,
    intrinsicImageSize: IntSize,
    onSelectionChanged: (List<OcrElementBox>) -> Unit,
) {
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var initialOcrResults by remember { mutableStateOf<List<OcrElementBox>?>(null) }
    val detectedColor = Color.Blue.copy(alpha = 0.1f)
    val selectedColor = Color.Blue.copy(alpha = 0.3f)
    val strokeColor = Color.Blue.copy(alpha = 0.5f)
    val clipboardManager = LocalClipboardManager.current
    val viewConfiguration = LocalViewConfiguration.current

    val currentOcrResults by rememberUpdatedState(ocrResults)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(intrinsicImageSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val imagePoint = screenToImage(down.position, size, intrinsicImageSize)
                        
                        val hit = currentOcrResults.find { 
                            val r = it.imageRect
                            // Slightly expand hit area to make it easier to grab a word
                            val expanded = Rect(r.left - 10f, r.top - 10f, r.right + 10f, r.bottom + 10f)
                            expanded.contains(imagePoint) 
                        }

                        if (hit == null) {
                            return@awaitEachGesture
                        }

                        down.consume()
                        var isDrag = false
                        selectionRect = Rect(down.position, down.position)
                        initialOcrResults = currentOcrResults
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            
                            if (change.pressed) {
                                val pos = change.position
                                val distance = (pos - down.position).getDistance()
                                
                                if (!isDrag && distance > viewConfiguration.touchSlop) {
                                    isDrag = true
                                }
                                
                                if (isDrag) {
                                    change.consume()
                                    selectionRect = Rect(down.position, pos)
                                    val imageSelectionRect = screenToImageRect(selectionRect!!, size, intrinsicImageSize)

                                    val base = initialOcrResults ?: currentOcrResults
                                    val newResults = base.map {
                                        if (it.imageRect.overlaps(imageSelectionRect)) it.copy(selected = true)
                                        else it
                                    }
                                    currentOnSelectionChanged(newResults)
                                }
                            } else {
                                if (!isDrag) {
                                    val newResults = currentOcrResults.map {
                                        if (it == hit) it.copy(selected = !it.selected)
                                        else it
                                    }
                                    currentOnSelectionChanged(newResults)
                                }
                                selectionRect = null
                                initialOcrResults = null
                                break
                            }
                        }
                    }
                }
        ) {
            ocrResults.forEach { box ->
                val screenRect = imageToScreenRect(box.imageRect, intrinsicImageSize, size.toIntSize())
                drawRect(
                    color = if (box.selected) selectedColor else detectedColor,
                    topLeft = screenRect.topLeft,
                    size = screenRect.size,
                    style = Fill
                )
                if (box.selected) {
                    drawRect(
                        color = strokeColor,
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        style = Stroke(width = 1f)
                    )
                }
            }

            selectionRect?.let {
                drawRect(
                    color = selectedColor.copy(alpha = 0.2f),
                    topLeft = it.topLeft,
                    size = it.size,
                    style = Fill
                )
                drawRect(
                    color = strokeColor,
                    topLeft = it.topLeft,
                    size = it.size,
                    style = Stroke(width = 1f)
                )
            }
        }

        val selectedBoxes = ocrResults.filter { it.selected }
        if (selectedBoxes.isNotEmpty()) {
            Button(
                onClick = {
                    val text = selectedBoxes
                        .sortedWith(compareBy({ it.blockIndex }, { it.lineIndex }, { it.elementIndex }))
                        .joinToString(" ") { it.text }
                    clipboardManager.setText(AnnotatedString(text))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Text("Copy")
            }
        }
    }
}

private fun screenToImage(
    screenPoint: Offset,
    screenSize: IntSize,
    imageSize: IntSize
): Offset {
    val scaleX = screenSize.width.toFloat() / imageSize.width
    val scaleY = screenSize.height.toFloat() / imageSize.height
    return Offset(screenPoint.x / scaleX, screenPoint.y / scaleY)
}

private fun screenToImageRect(
    screenRect: Rect,
    screenSize: IntSize,
    imageSize: IntSize
): Rect {
    val scaleX = screenSize.width.toFloat() / imageSize.width
    val scaleY = screenSize.height.toFloat() / imageSize.height
    return Rect(
        left = screenRect.left / scaleX,
        top = screenRect.top / scaleY,
        right = screenRect.right / scaleX,
        bottom = screenRect.bottom / scaleY
    )
}

private fun imageToScreenRect(
    imageRect: Rect,
    imageSize: IntSize,
    screenSize: IntSize
): Rect {
    val scaleX = screenSize.width.toFloat() / imageSize.width
    val scaleY = screenSize.height.toFloat() / imageSize.height
    return Rect(
        left = imageRect.left * scaleX,
        top = imageRect.top * scaleY,
        right = imageRect.right * scaleX,
        bottom = imageRect.bottom * scaleY
    )
}
