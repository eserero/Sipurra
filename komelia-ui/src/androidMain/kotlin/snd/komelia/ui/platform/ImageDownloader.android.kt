package snd.komelia.ui.platform

import android.content.ContentValues
import android.provider.MediaStore
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.context

actual suspend fun saveImageToDownloads(bytes: ByteArray, filename: String) {
    val context = FileKit.context
    val mimeType = when (filename.substringAfterLast('.')) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, filename)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
    context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    context.contentResolver.update(uri, values, null, null)
}
