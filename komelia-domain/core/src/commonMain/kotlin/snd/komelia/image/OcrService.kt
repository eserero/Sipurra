package snd.komelia.image

import snd.komelia.settings.model.OcrSettings

expect class OcrService() {
    suspend fun recognizeText(image: ReaderImage, settings: OcrSettings): List<OcrElementBox>
}
