package snd.komelia.updates

import kotlinx.coroutines.flow.Flow

interface WhisperModelDownloader {
    fun whisperBaseDownload(): Flow<UpdateProgress>
    fun isModelDownloaded(): Flow<Boolean>
    fun modelFilePath(): String
}
