package snd.komelia.image

expect class OcrService() {
    suspend fun recognizeText(image: ReaderImage): List<OcrElementBox>
}
