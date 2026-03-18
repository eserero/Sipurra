package snd.komelia.ui.reader.epub

import android.content.Context
import cafe.adriel.voyager.navigator.Navigator
import com.storyteller.reader.BookService
import com.storyteller.reader.EpubView
import com.storyteller.reader.EpubViewListener
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
import com.storyteller.reader.OverlayPar
import org.json.JSONArray
import org.json.JSONObject
import snd.komelia.ui.reader.epub.audio.MediaOverlayController
import snd.webview.KomeliaWebview
import java.io.File
import java.net.URL
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

private fun JSONObject.toMap(): Map<String, Any> =
    keys().asSequence().associateWith { key ->
        when (val v = get(key)) {
            is JSONObject -> v.toMap()
            else -> v
        }
    }

class Epub3ReaderState(
    bookId: KomgaBookId,
    book: KomeliaBook?,
    private val context: Context,
    private val bookApi: KomgaBookApi,
    private val notifications: AppNotifications,
    private val markReadProgress: Boolean,
    private val windowState: AppWindowState,
    private val platformType: PlatformType,
    private val coroutineScope: CoroutineScope,
    private val bookSiblingsContext: BookSiblingsContext,
    override val onExit: (KomeliaBook) -> Unit,
) : EpubReaderState {

    override val state = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    override val book = MutableStateFlow(book)

    val bookId = MutableStateFlow(bookId)
    val showControls = MutableStateFlow(false)
    val mediaOverlayController = MutableStateFlow<MediaOverlayController?>(null)
    private val navigator = MutableStateFlow<Navigator?>(null)
    private val bookUuid: String get() = this.bookId.value.value

    // Populated once the EPUB is extracted and ready; handed to EpubView in onEpubViewCreated.
    private var savedLocator: Locator? = null

    fun toggleControls() {
        showControls.value = !showControls.value
    }

    override suspend fun initialize(navigator: Navigator) {
        this.navigator.value = navigator
        if (platformType == PlatformType.MOBILE) windowState.setFullscreen(true)
        if (state.value !is LoadState.Uninitialized) return

        state.value = LoadState.Loading
        notifications.runCatchingToNotifications {
            if (book.value == null) book.value = bookApi.getOne(bookId.value)

            val extractedDir = prepareEpubDirectory()
            val clipsFile = File(extractedDir, "overlay_clips.json")

            val persistedClips: List<OverlayPar>? = if (clipsFile.exists()) {
                try {
                    val json = JSONArray(clipsFile.readText())
                    List(json.length()) { i -> OverlayPar.fromJson(json.getJSONObject(i).toMap()) }
                } catch (e: Exception) {
                    null  // corrupt/old cache → re-parse
                }
            } else null

            BookService.openPublication(bookUuid, extractedDir.toURI().toURL(), clips = persistedClips)

            val clips: List<OverlayPar> = persistedClips ?: run {
                val freshClips = BookService.getOverlayClips(bookUuid)
                if (freshClips.isNotEmpty()) {
                    val json = JSONArray(freshClips.map { JSONObject(it.toJson()) })
                    clipsFile.writeText(json.toString())
                }
                freshClips
            }

            if (clips.isNotEmpty()) {
                val controller = MediaOverlayController(context, coroutineScope, bookUuid, extractedDir)
                controller.initialize(clips)
                mediaOverlayController.value = controller
            }

            val r2Prog = bookApi.getReadiumProgression(bookId.value)
            if (r2Prog != null) {
                val href = Url(r2Prog.locator.href)
                if (href != null) {
                    savedLocator = Locator(
                        href = href,
                        mediaType = MediaType(r2Prog.locator.type) ?: MediaType.XHTML,
                        locations = Locator.Locations(
                            fragments = r2Prog.locator.locations?.fragment ?: emptyList(),
                            position = r2Prog.locator.locations?.position,
                            progression = r2Prog.locator.locations?.progression?.toDouble(),
                            totalProgression = r2Prog.locator.locations?.totalProgression?.toDouble(),
                        )
                    )
                }
            }

            state.value = LoadState.Success(Unit)
        }.onFailure { e ->
            logger.catching(e)
            state.value = LoadState.Error(e)
        }
    }

    fun onEpubViewCreated(view: EpubView) {
        view.listener = object : EpubViewListener {
            override fun onLocatorChange(locator: Locator) {
                savedLocator = locator
                // F1: page navigation → audio seek
                mediaOverlayController.value?.handleUserLocatorChange(locator)
                if (!markReadProgress) return
                coroutineScope.launch {
                    val r2Prog = R2Progression(
                        modified = Clock.System.now(),
                        device = R2Device("komelia-android", "Komelia"),
                        locator = R2Locator(
                            href = locator.href.toString(),
                            type = locator.mediaType.toString(),
                            title = locator.title,
                            locations = R2Location(
                                fragment = locator.locations.fragments,
                                position = locator.locations.position,
                                progression = locator.locations.progression?.toFloat(),
                                totalProgression = locator.locations.totalProgression?.toFloat(),
                            )
                        )
                    )
                    runCatching { bookApi.updateReadiumProgression(bookId.value, r2Prog) }
                        .onFailure { logger.catching(it) }
                }
            }

            override fun onMiddleTouch() {
                toggleControls()
            }

            override fun onDoubleTouch(locator: Locator) {
                // F2: double-tap → seek audio to that paragraph and play
                mediaOverlayController.value?.handleDoubleTap(locator)
            }
        }
        view.pendingProps.bookUuid = bookUuid
        view.pendingProps.locator = savedLocator
        view.finalizeProps()
        mediaOverlayController.value?.attachView(view)
    }

    /** No-op: this reader does not use a WebView. */
    override fun onWebviewCreated(webview: KomeliaWebview) = Unit

    override fun onBackButtonPress() = closeWebview()

    override fun closeWebview() {
        mediaOverlayController.value?.release()
        mediaOverlayController.value = null
        if (platformType == PlatformType.MOBILE) windowState.setFullscreen(false)
        book.value?.let { onExit(it) }
        navigator.value?.let { nav ->
            if (nav.canPop) nav.pop()
            else {
                val screen = book.value
                    ?.let { bookScreen(book = it, bookSiblingsContext = bookSiblingsContext) }
                    ?: BookScreen(bookId = bookId.value, bookSiblingsContext = bookSiblingsContext)
                nav.replaceAll(MainScreen(screen))
            }
        }
    }

    /**
     * Downloads the EPUB zip (if not already cached) and extracts it to
     * `context.cacheDir/epub3/<bookUuid>/`.
     */
    private suspend fun prepareEpubDirectory(): File {
        val extractedDir = File(context.cacheDir, "epub3/$bookUuid").also { it.mkdirs() }
        if (extractedDir.list().isNullOrEmpty()) {
            val localPath = bookApi.getBookLocalFilePath(bookId.value)
            if (localPath != null) {
                // Offline: extract directly from already-local file — zero heap allocation
                BookService.extractArchive(
                    URL("file://$localPath"),
                    extractedDir.toURI().toURL()
                )
            } else {
                // Remote: stream to temp file in 64 KB chunks
                val zipFile = File(context.cacheDir, "epub3/$bookUuid.epub")
                zipFile.outputStream().buffered().use { out ->
                    bookApi.downloadBookRawFile(bookId.value) { chunk -> out.write(chunk) }
                }
                BookService.extractArchive(
                    URL("file://${zipFile.absolutePath}"),
                    extractedDir.toURI().toURL()
                )
                zipFile.delete()
            }
        }
        return extractedDir
    }
}
