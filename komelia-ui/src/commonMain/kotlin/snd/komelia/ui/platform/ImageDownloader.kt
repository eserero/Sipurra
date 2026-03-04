package snd.komelia.ui.platform

expect suspend fun saveImageToDownloads(bytes: ByteArray, filename: String)

fun ByteArray.imageExtension(): String = when {
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() -> "jpg"
    size >= 4 && this[0] == 0x89.toByte() && this[1] == 0x50.toByte() -> "png"
    size >= 12 && this[8] == 0x57.toByte() && this[9] == 0x45.toByte() -> "webp"
    else -> "jpg"
}

fun String.sanitizeFilename(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_")
