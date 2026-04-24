package snd.komelia.offline.mediacontainer.divina

import com.github.junrar.Archive
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream

class RarExtractor {
    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        val archive = Archive(file.file)
        val bytes = archive.use { rar ->
            rar.fileHeaders.find { it.fileName == entryName }
                ?.let { header ->
                    val os = ByteArrayOutputStream()
                    rar.extractFile(header, os)
                    os.toByteArray()
                }
        }

        if (bytes == null) throw IllegalStateException("rar entry does not exist: $entryName")
        return bytes
    }
}
