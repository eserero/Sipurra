package snd.komelia.image

import snd.komelia.settings.model.OcrLanguage

actual class OcrService {
    actual suspend fun recognizeText(image: ReaderImage, language: OcrLanguage): List<OcrElementBox> {
        return emptyList()
    }
}
