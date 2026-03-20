package snd.komelia.ui.reader.epub

import android.content.Context
import cafe.adriel.voyager.navigator.Navigator
import com.storyteller.reader.BookService
import com.storyteller.reader.EpubView
import com.storyteller.reader.EpubViewListener
import com.storyteller.reader.OverlayPar
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.settings.model.Epub3ColumnCount
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.settings.model.Epub3TextAlign
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.reader.epub.audio.MediaOverlayController
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
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
    private val epubSettingsRepository: EpubReaderSettingsRepository,
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
    val showSettings = MutableStateFlow(false)
    val showToc = MutableStateFlow(false)
    val tableOfContents = MutableStateFlow<List<Link>>(emptyList())
    val settings = MutableStateFlow(Epub3NativeSettings())
    val mediaOverlayController = MutableStateFlow<MediaOverlayController?>(null)
    val positions = MutableStateFlow<List<Locator>>(emptyList())
    val currentLocator = MutableStateFlow<Locator?>(null)
    private var epubView: EpubView? = null
    private var pendingControlsToggleJob: Job? = null
    private val navigator = MutableStateFlow<Navigator?>(null)
    private val bookUuid: String get() = this.bookId.value.value

    // Populated once the EPUB is extracted and ready; handed to EpubView in onEpubViewCreated.
    private var savedLocator: Locator? = null

    fun toggleControls() {
        showControls.value = !showControls.value
    }

    fun toggleSettings() {
        showSettings.value = !showSettings.value
    }

    fun toggleToc() {
        showToc.value = !showToc.value
    }

    fun navigateToLink(link: Link) {
        val locator = BookService.locateLink(bookUuid, link) ?: return
        epubView?.go(locator)
        mediaOverlayController.value?.handleUserLocatorChange(locator)
    }

    fun updateSettings(new: Epub3NativeSettings) {
        settings.value = new
        applySettingsToView(new)
        coroutineScope.launch { epubSettingsRepository.putEpub3NativeSettings(new) }
    }

    private fun applySettingsToView(s: Epub3NativeSettings) {
        val view = epubView ?: return
        val ta = when (s.textAlign) {
            Epub3TextAlign.JUSTIFY -> TextAlign.JUSTIFY
            Epub3TextAlign.LEFT    -> TextAlign.LEFT
            Epub3TextAlign.CENTER  -> TextAlign.CENTER
            Epub3TextAlign.RIGHT   -> TextAlign.RIGHT
        }
        view.pendingProps.foreground       = s.theme.foreground
        view.pendingProps.background       = s.theme.background
        view.pendingProps.fontFamily       = FontFamily(s.fontFamily)
        view.pendingProps.fontSize         = s.fontSize
        view.pendingProps.lineHeight       = s.lineHeight
        view.pendingProps.paragraphSpacing = s.paragraphSpacing
        view.pendingProps.textAlign        = ta
        view.pendingProps.readaloudColor   = s.readAloudColor.colorInt
        view.pendingProps.scroll           = s.scroll
        view.pendingProps.columnCount      = when (s.columnCount) {
            Epub3ColumnCount.AUTO -> ColumnCount.AUTO
            Epub3ColumnCount.ONE  -> ColumnCount.ONE
            Epub3ColumnCount.TWO  -> ColumnCount.TWO
        }
        view.pendingProps.pageMargins      = s.pageMargins
        view.pendingProps.publisherStyles  = s.publisherStyles
        view.finalizeProps()
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
            tableOfContents.value = BookService.getPublication(bookUuid)?.tableOfContents ?: emptyList()

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
            settings.value = epubSettingsRepository.getEpub3NativeSettings()
            coroutineScope.launch {
                runCatching { positions.value = BookService.getPositions(bookUuid) }
                    .onFailure { logger.catching(it) }
            }
        }.onFailure { e ->
            logger.catching(e)
            state.value = LoadState.Error(e)
        }
    }

    fun onEpubViewCreated(view: EpubView) {
        view.listener = object : EpubViewListener {
            override fun onLocatorChange(locator: Locator) {
                savedLocator = locator
                currentLocator.value = locator
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
                pendingControlsToggleJob?.cancel()
                pendingControlsToggleJob = coroutineScope.launch {
                    delay(400L)   // JS double-tap window is 350ms; 400ms gives headroom
                    toggleControls()
                }
            }

            override fun onDoubleTouch(locator: Locator) {
                pendingControlsToggleJob?.cancel()
                // F2: double-tap → seek audio to that paragraph and play
                mediaOverlayController.value?.handleDoubleTap(locator)
            }
        }
        view.pendingProps.bookUuid = bookUuid
        view.pendingProps.locator = savedLocator
        view.finalizeProps()
        this.epubView = view
        currentLocator.value = savedLocator
        applySettingsToView(settings.value)
        mediaOverlayController.value?.attachView(view)
        // Pre-seed pendingUserLocator so first play starts from reading position,
        // not from audio track position 0, even if Readium hasn't fired onLocatorChange yet.
        savedLocator?.let { mediaOverlayController.value?.handleUserLocatorChange(it) }
    }

    /** No-op: this reader does not use a WebView. */
    override fun onWebviewCreated(webview: KomeliaWebview) = Unit

    override fun onBackButtonPress() = closeWebview()

    fun navigateToPosition(positionIndex: Int) {
        val locator = positions.value.getOrNull(positionIndex) ?: return
        epubView?.go(locator)
        mediaOverlayController.value?.handleUserLocatorChange(locator)
    }

    override fun closeWebview() {
        this.epubView = null
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
