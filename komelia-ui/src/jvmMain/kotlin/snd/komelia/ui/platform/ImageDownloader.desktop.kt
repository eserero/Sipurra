package snd.komelia.ui.platform

import java.io.File

actual suspend fun saveImageToDownloads(bytes: ByteArray, filename: String) {
    val downloadsDir = File(System.getProperty("user.home"), "Downloads")
    downloadsDir.mkdirs()
    File(downloadsDir, filename).writeBytes(bytes)
}
