package snd.komelia.image

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import snd.komelia.image.AndroidBitmap.toBitmap
import androidx.compose.ui.geometry.Rect

actual class OcrService {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    actual suspend fun recognizeText(image: ReaderImage): List<OcrElementBox> {
        val komeliaImage = image.getOriginalImage().getOrNull() ?: return emptyList()
        val bitmap = when (komeliaImage) {
            is AndroidBitmapBackedImage -> komeliaImage.bitmap
            else -> komeliaImage.toBitmap()
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()

        val boxes = mutableListOf<OcrElementBox>()
        result.textBlocks.forEachIndexed { blockIdx, block ->
            block.lines.forEachIndexed { lineIdx, line ->
                line.elements.forEachIndexed { elementIdx, element ->
                    val rect = element.boundingBox ?: return@forEachIndexed
                    boxes.add(
                        OcrElementBox(
                            text = element.text,
                            imageRect = Rect(
                                left = rect.left.toFloat(),
                                top = rect.top.toFloat(),
                                right = rect.right.toFloat(),
                                bottom = rect.bottom.toFloat()
                            ),
                            blockIndex = blockIdx,
                            lineIndex = lineIdx,
                            elementIndex = elementIdx
                        )
                    )
                }
            }
        }
        return boxes
    }
}
