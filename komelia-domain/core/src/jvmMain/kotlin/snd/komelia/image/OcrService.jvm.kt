package snd.komelia.image

import snd.komelia.settings.model.OcrSettings

actual class OcrService {
    actual suspend fun recognizeText(image: ReaderImage, settings: OcrSettings): List<OcrElementBox> {
        return emptyList()
    }
}
