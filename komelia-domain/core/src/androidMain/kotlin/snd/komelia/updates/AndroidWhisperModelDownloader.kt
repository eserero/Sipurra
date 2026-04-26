package snd.komelia.updates

import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.readByteArray
import java.io.File

private const val MODEL_URL =
    "https://github.com/eserero/Sipurra/releases/download/model/ggml-base-q5_1.bin"

class AndroidWhisperModelDownloader(
    private val updateClient: UpdateClient,
    private val filesDir: File,
) : WhisperModelDownloader {

    private val modelsDir = File(filesDir, "whisper_models")
    private val modelFile = File(modelsDir, "ggml-base-q5_1.bin")

    private val _isDownloaded = MutableStateFlow(modelFile.exists() && modelFile.length() > 0)

    override fun whisperBaseDownload(): Flow<UpdateProgress> = flow {
        modelsDir.mkdirs()
        downloadFile(MODEL_URL, modelFile)
        _isDownloaded.value = modelFile.exists() && modelFile.length() > 0
    }.flowOn(Dispatchers.IO)

    override fun isModelDownloaded(): Flow<Boolean> = _isDownloaded

    override fun modelFilePath(): String = modelFile.absolutePath

    private suspend fun FlowCollector<UpdateProgress>.downloadFile(url: String, file: File) {
        updateClient.streamFile(url) { response ->
            val length = response.headers["Content-Length"]?.toLong() ?: 0L
            emit(UpdateProgress(length, 0, url))
            val channel = response.bodyAsChannel().counted()

            file.outputStream().buffered().use { outputStream ->
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    while (!packet.exhausted()) {
                        val bytes = packet.readByteArray()
                        outputStream.write(bytes)
                    }
                    outputStream.flush()
                    emit(UpdateProgress(length, channel.totalBytesRead, url))
                }
            }
        }
    }
}
