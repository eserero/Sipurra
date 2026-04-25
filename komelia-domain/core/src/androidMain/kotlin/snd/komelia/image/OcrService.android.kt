package snd.komelia.image

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import snd.komelia.image.AndroidBitmap.toBitmap
import androidx.compose.ui.geometry.Rect
import snd.komelia.settings.model.OcrLanguage

actual class OcrService {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val japaneseRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val koreanRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    actual suspend fun recognizeText(image: ReaderImage, language: OcrLanguage): List<OcrElementBox> {
        val komeliaImage = image.getOriginalImage().getOrNull() ?: return emptyList()
        val bitmap = when (komeliaImage) {
            is AndroidBitmapBackedImage -> komeliaImage.bitmap
            else -> komeliaImage.toBitmap()
        }

        val recognizer = when (language) {
            OcrLanguage.LATIN -> latinRecognizer
            OcrLanguage.CHINESE -> chineseRecognizer
            OcrLanguage.DEVANAGARI -> devanagariRecognizer
            OcrLanguage.JAPANESE -> japaneseRecognizer
            OcrLanguage.KOREAN -> koreanRecognizer
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()

        val boxes = mutableListOf<OcrElementBox>()
        result.textBlocks.forEachIndexed { blockIdx, block ->
            val blockBoundingBox = block.boundingBox ?: return@forEachIndexed
            val blockRect = Rect(
                left = blockBoundingBox.left.toFloat(),
                top = blockBoundingBox.top.toFloat(),
                right = blockBoundingBox.right.toFloat(),
                bottom = blockBoundingBox.bottom.toFloat()
            )
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
                            blockRect = blockRect,
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
