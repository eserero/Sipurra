package snd.komelia.localfile

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import snd.komelia.db.localfile.LocalFileReadProgressRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadata
import snd.komga.client.book.KomgaBookMetadataUpdateRequest
import snd.komga.client.book.KomgaBookPage
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaBookThumbnail
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.book.Media
import snd.komga.client.book.MediaProfile
import snd.komga.client.book.R2Positions
import snd.komga.client.book.R2Progression
import snd.komga.client.book.ReadProgress
import snd.komga.client.book.WPPublication
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaThumbnailId
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.search.BookConditionBuilder
import snd.komga.client.series.KomgaSeriesId
import snd.komelia.offline.mediacontainer.AndroidPdfExtractor
import com.github.junrar.Archive
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.time.Clock

class LocalFileBookApi(
    private val context: Context,
    private val uriString: String,
    private val readProgressRepo: LocalFileReadProgressRepository,
) : KomgaBookApi {

    val virtualBookId: KomgaBookId = KomgaBookId(
        "local:" + uriString.sha256().take(16)
    )

    private val uri: Uri = Uri.parse(uriString)
    private val filename: String = uri.lastPathSegment?.substringAfterLast('/') ?: "Local File"

    val isEpub: Boolean = filename.endsWith(".epub", ignoreCase = true)
        || context.contentResolver.getType(uri)?.contains("epub") == true
    val isPdf: Boolean = filename.endsWith(".pdf", ignoreCase = true)
        || context.contentResolver.getType(uri)?.contains("pdf") == true
    val isRar: Boolean = filename.endsWith(".cbr", ignoreCase = true)
        || filename.endsWith(".rar", ignoreCase = true)
        || context.contentResolver.getType(uri)?.contains("rar") == true
        || context.contentResolver.getType(uri)?.contains("x-rar-compressed") == true

    private val pdfExtractor = AndroidPdfExtractor(context)

    private val imageEntries: List<String> by lazy {
        if (isEpub || isPdf) emptyList()
        else {
            val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
            val entries = mutableListOf<String>()

            if (isRar) {
                openRar { archive ->
                    archive.fileHeaders.forEach { header ->
                        val ext = header.fileName.substringAfterLast('.', "").lowercase()
                        if (!header.isDirectory && ext in imageExtensions) {
                            entries.add(header.fileName)
                        }
                    }
                }
            } else {
                openZip { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val ext = entry.name.substringAfterLast('.', "").lowercase()
                        if (!entry.isDirectory && ext in imageExtensions) {
                            entries.add(entry.name)
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            entries.sortedWith(naturalOrder())
        }
    }

    private val pdfPageCount: Int by lazy {
        if (isPdf) {
            val platformFile = io.github.vinceglb.filekit.PlatformFile(uri)
            pdfExtractor.getPageCount(platformFile)
        } else 0
    }

    override suspend fun getOne(bookId: KomgaBookId): KomeliaBook {
        require(bookId == virtualBookId)
        val (page, completed) = readProgressRepo.getProgress(bookId) ?: Pair(1, false)
        val storedReadProgress = if (page > 1 || completed) {
            val now = Clock.System.now()
            ReadProgress(
                page = page,
                completed = completed,
                readDate = now,
                created = now,
                lastModified = now,
                deviceId = "",
                deviceName = "",
            )
        } else null

        val now = Clock.System.now()
        val mediaProfile = when {
            isEpub -> MediaProfile.EPUB
            isPdf -> MediaProfile.PDF
            else -> MediaProfile.DIVINA
        }
        val mediaType = when {
            isEpub -> "application/epub+zip"
            isPdf -> "application/pdf"
            isRar -> "application/vnd.rar"
            else -> "application/zip"
        }
        val pagesCount = when {
            isPdf -> pdfPageCount
            else -> imageEntries.size
        }

        return KomeliaBook(
            id = virtualBookId,
            seriesId = KomgaSeriesId("local"),
            seriesTitle = "",
            libraryId = KomgaLibraryId("local"),
            name = filename,
            url = uriString,
            number = 1,
            created = now,
            lastModified = now,
            fileLastModified = now,
            sizeBytes = 0L,
            size = "",
            media = Media(
                status = KomgaMediaStatus.READY,
                mediaType = mediaType,
                pagesCount = pagesCount,
                comment = "",
                epubDivinaCompatible = false,
                epubIsKepub = false,
                mediaProfile = mediaProfile,
            ),
            metadata = KomgaBookMetadata(
                title = filename.substringBeforeLast('.'),
                summary = "", number = "", numberSort = 1f,
                releaseDate = null, authors = emptyList(), tags = emptyList(),
                isbn = "", links = emptyList(),
                titleLock = false, summaryLock = false, numberLock = false,
                numberSortLock = false, releaseDateLock = false, authorsLock = false,
                tagsLock = false, isbnLock = false, linksLock = false,
                created = now, lastModified = now,
            ),
            readProgress = storedReadProgress,
            deleted = false,
            fileHash = "",
            oneshot = true,
            downloaded = true,
            localFileLastModified = null,
            remoteFileUnavailable = false,
        )
    }

    override suspend fun getBookPages(bookId: KomgaBookId): List<KomgaBookPage> {
        require(bookId == virtualBookId)
        if (isPdf) {
            return (1..pdfPageCount).map { index ->
                KomgaBookPage(
                    number = index,
                    fileName = "page$index.jpg",
                    mediaType = "image/jpeg",
                    width = null,
                    height = null,
                    sizeBytes = null,
                    size = "",
                )
            }
        }

        return imageEntries.mapIndexed { index, name ->
            KomgaBookPage(
                number = index + 1,
                fileName = name,
                mediaType = mimeTypeForEntry(name),
                width = null,
                height = null,
                sizeBytes = null,
                size = "",
            )
        }
    }

    override suspend fun getPage(bookId: KomgaBookId, page: Int): ByteArray {
        require(bookId == virtualBookId)
        if (isPdf) {
            val platformFile = io.github.vinceglb.filekit.PlatformFile(uri)
            return pdfExtractor.getPage(platformFile, page)
        }

        val targetEntry = imageEntries.getOrNull(page - 1)
            ?: throw IllegalArgumentException("Page $page not found (${imageEntries.size} pages total)")
        var result: ByteArray? = null

        if (isRar) {
            openRar { archive ->
                archive.fileHeaders.find { it.fileName == targetEntry }?.let { header ->
                    val os = ByteArrayOutputStream()
                    archive.extractFile(header, os)
                    result = os.toByteArray()
                }
            }
        } else {
            openZip { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == targetEntry) {
                        result = zip.readBytes()
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return result ?: throw IllegalStateException("Entry $targetEntry not found in archive")
    }

    override suspend fun markReadProgress(bookId: KomgaBookId, request: KomgaBookReadProgressUpdateRequest) {
        require(bookId == virtualBookId)
        readProgressRepo.saveProgress(
            bookId = bookId,
            page = request.page ?: 1,
            completed = request.completed == true,
        )
    }

    override suspend fun getReadiumProgression(bookId: KomgaBookId): R2Progression? {
        require(bookId == virtualBookId)
        val json = readProgressRepo.getReadiumProgression(bookId) ?: return null
        return runCatching { Json.decodeFromString(R2Progression.serializer(), json) }.getOrNull()
    }

    override suspend fun updateReadiumProgression(bookId: KomgaBookId, progression: R2Progression) {
        require(bookId == virtualBookId)
        val json = Json.encodeToString(R2Progression.serializer(), progression)
        readProgressRepo.saveReadiumProgression(bookId, json)
    }

    override suspend fun hasLocalFile(bookId: KomgaBookId): Boolean = true

    override suspend fun getBookLocalFilePath(bookId: KomgaBookId): String? = null

    override suspend fun downloadBookRawFile(bookId: KomgaBookId, onChunk: suspend (ByteArray) -> Unit) {
        require(bookId == virtualBookId)
        context.contentResolver.openInputStream(uri)!!.use { stream ->
            val buf = ByteArray(65536)
            var n: Int
            while (stream.read(buf).also { n = it } != -1) {
                onChunk(buf.copyOf(n))
            }
        }
    }

    override suspend fun getBookSiblingNext(bookId: KomgaBookId): KomeliaBook? = null
    override suspend fun getBookSiblingPrevious(bookId: KomgaBookId): KomeliaBook? = null

    override suspend fun getBookList(conditionBuilder: BookConditionBuilder, fullTextSearch: String?, pageRequest: KomgaPageRequest?): Page<KomeliaBook> = unsupported()
    override suspend fun getBookList(search: KomgaBookSearch, pageRequest: KomgaPageRequest?): Page<KomeliaBook> = unsupported()
    override suspend fun getLatestBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> = unsupported()
    override suspend fun getBooksOnDeck(libraryIds: List<KomgaLibraryId>?, pageRequest: KomgaPageRequest?): Page<KomeliaBook> = unsupported()
    override suspend fun getDuplicateBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> = unsupported()
    override suspend fun updateMetadata(bookId: KomgaBookId, request: KomgaBookMetadataUpdateRequest) = unsupported()
    override suspend fun analyze(bookId: KomgaBookId) = unsupported()
    override suspend fun refreshMetadata(bookId: KomgaBookId) = unsupported()
    override suspend fun deleteReadProgress(bookId: KomgaBookId) = unsupported()
    override suspend fun deleteBook(bookId: KomgaBookId) = unsupported()
    override suspend fun regenerateThumbnails(forBiggerResultOnly: Boolean) = unsupported()
    override suspend fun getDefaultThumbnail(bookId: KomgaBookId): ByteArray? = null
    override suspend fun getThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId): ByteArray = ByteArray(0)
    override suspend fun getThumbnails(bookId: KomgaBookId): List<KomgaBookThumbnail> = emptyList()
    override suspend fun uploadThumbnail(bookId: KomgaBookId, file: ByteArray, filename: String, selected: Boolean): KomgaBookThumbnail = unsupported()
    override suspend fun selectBookThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId) = unsupported()
    override suspend fun deleteBookThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId) = unsupported()
    override suspend fun getAllReadListsByBook(bookId: KomgaBookId): List<KomgaReadList> = emptyList()
    override suspend fun getPageThumbnail(bookId: KomgaBookId, page: Int): ByteArray = getPage(bookId, page)
    override suspend fun getReadiumPositions(bookId: KomgaBookId): R2Positions = R2Positions(total = 0, positions = emptyList())
    override suspend fun getWebPubManifest(bookId: KomgaBookId): WPPublication = unsupported()
    override suspend fun getBookEpubResource(bookId: KomgaBookId, resourceName: String): ByteArray = unsupported()
    override suspend fun getBookRawFile(bookId: KomgaBookId): ByteArray = unsupported()

    private fun openZip(block: (ZipInputStream) -> Unit) {
        context.contentResolver.openInputStream(uri)!!.use { raw ->
            ZipInputStream(raw).use(block)
        }
    }

    private fun openRar(block: (Archive) -> Unit) {
        context.contentResolver.openInputStream(uri)!!.use { raw ->
            Archive(raw).use(block)
        }
    }

    private fun mimeTypeForEntry(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("Not supported for local files")
}
