package snd.komelia.image

import snd.komelia.settings.model.OcrLanguage

expect class OcrService() {
    suspend fun recognizeText(image: ReaderImage, language: OcrLanguage): List<OcrElementBox>
}
