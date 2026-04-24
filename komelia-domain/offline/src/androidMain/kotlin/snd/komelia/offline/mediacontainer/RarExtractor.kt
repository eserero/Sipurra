package snd.komelia.offline.mediacontainer

import com.github.junrar.Archive
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import java.io.ByteArrayOutputStream

class RarExtractor {
    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        val archive = when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> Archive(androidFile.file)
            is AndroidFile.UriWrapper -> {
                val inputStream = FileKit.context.contentResolver.openInputStream(androidFile.uri)
                    ?: error("Could not open input stream for ${androidFile.uri}")
                Archive(inputStream)
            }
        }

        val bytes = archive.use { rar ->
            rar.fileHeaders.find { it.fileName == entryName }
                ?.let { header ->
                    val os = ByteArrayOutputStream()
                    rar.extractFile(header, os)
                    os.toByteArray()
                }
        }

        if (bytes == null) error("rar entry does not exist: $entryName")
        return bytes
    }
}
