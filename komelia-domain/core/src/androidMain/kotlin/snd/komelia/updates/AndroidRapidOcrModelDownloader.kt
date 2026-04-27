package snd.komelia.updates

import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.io.readByteArray
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.io.IOUtils
import snd.komelia.AppNotifications
import snd.komelia.updates.RapidOcrModelDownloader.CompletionEvent
import snd.komelia.updates.RapidOcrModelDownloader.CompletionEvent.RapidOcrModelsDownloaded
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class AndroidRapidOcrModelDownloader(
    private val updateClient: UpdateClient,
    private val appNotifications: AppNotifications,
    private val dataDir: Path,
) : RapidOcrModelDownloader {
    override val downloadCompletionEvents = MutableSharedFlow<CompletionEvent>()

    override fun download(url: String): Flow<UpdateProgress> {
        return flow {
            emit(UpdateProgress(0, 0, url))
            val archiveFile = createTempFile("RapidOcrModels.zip")
            archiveFile.toFile().deleteOnExit()

            appNotifications.runCatchingToNotifications {
                downloadFile(url, archiveFile)
                emit(UpdateProgress(0, 0))
                val targetDir = dataDir.resolve("rapidocr_models")
                if (targetDir.exists()) targetDir.toFile().deleteRecursively()
                targetDir.createDirectories()
                extractZipArchive(archiveFile, targetDir)
                archiveFile.deleteIfExists()
                downloadCompletionEvents.emit(RapidOcrModelsDownloaded)
            }.onFailure { archiveFile.deleteIfExists() }
        }.flowOn(Dispatchers.IO)
    }

    override fun isDownloaded(): Boolean {
        val targetDir = dataDir.resolve("rapidocr_models")
        return targetDir.exists() && targetDir.listDirectoryEntries().isNotEmpty()
    }

    private suspend fun FlowCollector<UpdateProgress>.downloadFile(url: String, file: Path) {
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

    private fun extractZipArchive(from: Path, to: Path) {
        ZipArchiveInputStream(from.inputStream().buffered()).use { archiveStream ->
            var entry: ZipArchiveEntry? = archiveStream.nextEntry
            while (entry != null) {
                val targetPath = to.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.createDirectories(targetPath.parent)
                    targetPath.outputStream()
                        .use { output -> IOUtils.copy(archiveStream, output) }
                }
                entry = archiveStream.nextEntry
            }
        }
    }
}
