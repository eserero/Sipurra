package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

class DivinaRarExtractor(private val rarExtractor: RarExtractor) : DivinaExtractor {
    override fun mediaTypes(): List<String> = listOf(
        "application/vnd.rar",
        "application/x-rar-compressed",
        "application/rar"
    )

    override fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        return rarExtractor.getEntryBytes(file, entryName)
    }
}
