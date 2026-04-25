package snd.komelia.image

actual class OcrService {
    actual suspend fun recognizeText(image: ReaderImage): List<OcrElementBox> {
        return emptyList()
    }
}
