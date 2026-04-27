package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

interface PdfExtractor {
    fun getPage(file: PlatformFile, pageNumber: Int): ByteArray
    fun getPageCount(file: PlatformFile): Int
}
