package snd.komelia.ui.reader.epub

import android.content.Context
import cafe.adriel.voyager.navigator.Navigator
import com.storyteller.reader.BookService
import com.storyteller.reader.CustomFont
import com.storyteller.reader.EpubView
import com.storyteller.reader.EpubViewListener
import com.storyteller.reader.OverlayPar
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.fonts.UserFont
import snd.komelia.fonts.UserFontsRepository
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

class Epub3ReaderState(
    bookId: KomgaBookId,
    book: KomeliaBook?,
    private val context: Context,
    private val bookApi: KomgaBookApi,
    private val epubSettingsRepository: EpubReaderSettingsRepository,
    private val fontsRepository: UserFontsRepository,
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
    val userFonts = MutableStateFlow<List<UserFont>>(emptyList())
    val mediaOverlayController = MutableStateFlow<MediaOverlayController?>(null)
    val positions = MutableStateFlow<List<Locator>>(emptyList())
    val currentLocator = MutableStateFlow<Locator?>(null)
    private var epubView: EpubView? = null
    private val navigator = MutableStateFlow<Navigator?>(null)
    private val bookUuid: String get() = this.bookId.value.value
    // Directory the epub is extracted to; set during initialize().
    private var extractedDir: File? = null

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
        val locator = BookService.locateLink(bookUuid, link) ?: run {
            logger.info { "[komelia-epub] NAV-LINK: locateLink returned null for ${link.href}" }
            return
        }
        logger.info {
            "[komelia-epub] NAV-LINK: link=${link.href} → locator=${locator.href} " +
            "currentLocator_before=${currentLocator.value?.href}"
        }
        epubView?.go(locator)
        mediaOverlayController.value?.handleUserLocatorChange(locator)
        // onLocatorChange will fire after go() and call handleUserLocatorChange,
        // exactly like swipe navigation — the locator from EpubView has proper
        // position data, not a TOC anchor fragment.
    }

    fun updateSettings(new: Epub3NativeSettings) {
        settings.value = new
        applySettingsToView(new)
        mediaOverlayController.value?.applyAudioSettings(new)
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
        view.pendingProps.customFonts      = buildCustomFontList()
        view.finalizeProps()
    }

    private fun buildCustomFontList(): List<CustomFont> {
        return userFonts.value.map { font ->
            val ext = font.path.name.substringAfterLast(".", "ttf")
            val fontFileName = "${font.canonicalName}.$ext"
            // Readium serves publication resources at https://readium/publication/<path>.
            // Using a relative path would resolve against https://readium/assets/ (wrong).
            // Using the absolute publication URL routes the font request to DirectoryContainer.
            // Percent-encode the filename so spaces and special chars don't produce invalid URLs.
            val encodedFileName = java.net.URLEncoder.encode(fontFileName, "UTF-8").replace("+", "%20")
            val uri = "https://readium/publication/komelia-user-fonts/$encodedFileName"
            logger.debug { "[epub3-fonts] registering font: name=${font.canonicalName} uri=$uri" }
            CustomFont(uri = uri, name = font.canonicalName, type = ext)
        }
    }

    private suspend fun loadUserFontsIntoCache(fonts: List<UserFont>) {
        copyFontsToEpubDir(fonts)
        userFonts.value = fonts
    }

    private suspend fun copyFontsToEpubDir(fonts: List<UserFont>) {
        val dir = extractedDir ?: run {
            logger.warn { "[epub3-fonts] copyFontsToEpubDir: extractedDir is null, skipping" }
            return
        }
        withContext(Dispatchers.IO) {
            val fontsDir = File(dir, "komelia-user-fonts").also { it.mkdirs() }
            logger.debug { "[epub3-fonts] font staging dir: $fontsDir" }
            for (font in fonts) {
                val ext = font.path.name.substringAfterLast(".", "ttf")
                val destFile = File(fontsDir, "${font.canonicalName}.$ext")
                if (!destFile.exists()) {
                    File(font.path.toString()).copyTo(destFile)
                    logger.debug { "[epub3-fonts] copied ${font.path} -> $destFile" }
                } else {
                    logger.debug { "[epub3-fonts] already exists: $destFile" }
                }
            }
        }
    }

    fun loadFont(file: PlatformFile) {
        coroutineScope.launch {
            val name = file.name.substringBeforeLast(".")
            val userFont = UserFont.saveFontToAppDirectory(name, file) ?: return@launch
            fontsRepository.putFont(userFont)
            loadUserFontsIntoCache(fontsRepository.getAllFonts())
            applySettingsToView(settings.value)
        }
    }

    fun deleteFont(font: UserFont) {
        coroutineScope.launch {
            fontsRepository.deleteFont(font)
            font.deleteFontFile()
            loadUserFontsIntoCache(fontsRepository.getAllFonts())
            applySettingsToView(settings.value)
        }
    }

    override suspend fun initialize(navigator: Navigator) {
        this.navigator.value = navigator
        if (state.value !is LoadState.Uninitialized) return

        logger.debug { "[epub3-init] starting for bookId=${bookId.value.value}" }
        state.value = LoadState.Loading
        notifications.runCatchingToNotifications {
            if (book.value == null) {
                logger.debug { "[epub3-init] fetching book metadata" }
                book.value = bookApi.getOne(bookId.value)
            }

            logger.debug { "[epub3-init] preparing epub directory" }
            val extractedDir = prepareEpubDirectory()
            this.extractedDir = extractedDir
            logger.debug { "[epub3-init] epub directory ready: $extractedDir" }

            // One-time cleanup: delete stale overlay_clips.json cache from old builds
            withContext(Dispatchers.IO) {
                File(extractedDir, "overlay_clips.json").delete()
            }

            // Copy user fonts into the epub directory BEFORE openPublication() so that
            // DirectoryContainer.entries (built via root.walk() inside openPublication) includes them.
            val allFonts = fontsRepository.getAllFonts()
            copyFontsToEpubDir(allFonts)
            userFonts.value = allFonts

            logger.debug { "[epub3-init] opening publication" }
            withContext(Dispatchers.IO) {
                BookService.openPublication(bookUuid, extractedDir.toURI().toURL(), clips = null)
            }
            logger.debug { "[epub3-init] publication opened" }
            tableOfContents.value = BookService.getPublication(bookUuid)?.tableOfContents ?: emptyList()

            val clips: List<OverlayPar> = BookService.getOverlayClips(bookUuid)

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
            logger.info { "[EPUB-DIAG] [INIT] BookID: ${bookId.value.value} | SavedLocator: ${savedLocator?.href}#${savedLocator?.locations?.fragments?.firstOrNull()}" }

            logger.debug { "[epub3-init] succeeded" }
            state.value = LoadState.Success(Unit)
            settings.value = epubSettingsRepository.getEpub3NativeSettings()
            coroutineScope.launch {
                val rt = Runtime.getRuntime()
                val pre = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
                logger.info { "[epub3-diag] getPositions-coroutine START heap=${pre}MB clips=${clips.size}" }
                runCatching { positions.value = BookService.getPositions(bookUuid) }
                    .onFailure { logger.catching(it) }
                val post = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
                logger.info { "[epub3-diag] getPositions-coroutine END heap=${post}MB delta=${post - pre}MB" }
            }

            if (clips.isNotEmpty()) {
                coroutineScope.launch {
                    logger.debug { "[epub3-init] initializing media overlay controller in background (${clips.size} clips)" }
                    runCatching {
                        val controller = MediaOverlayController(context, coroutineScope, bookUuid, extractedDir)
                        controller.initialize(clips, savedLocator)
                        controller.applyAudioSettings(settings.value)
                        mediaOverlayController.value = controller
                        epubView?.let { view ->
                            controller.attachView(view)
                            savedLocator?.let { controller.handleUserLocatorChange(it) }
                        }
                        logger.debug { "[epub3-init] media overlay controller ready" }
                    }.onFailure { e ->
                        logger.error { "[epub3-init] media overlay controller FAILED: ${e::class.qualifiedName}: ${e.message}\n${e.stackTraceToString()}" }
                    }
                }
            }
        }.onFailure { e ->
            logger.error { "[epub3-init] FAILED: ${e::class.qualifiedName}: ${e.message}\n${e.stackTraceToString()}" }
            state.value = LoadState.Error(e)
        }
    }

    fun onEpubViewCreated(view: EpubView) {
        logger.info { "[EPUB-DIAG] [VIEW-READY] SavedLocator: ${savedLocator?.href} | ControllerReady: ${mediaOverlayController.value != null}" }
        logger.info { "[komelia-epub] INIT: savedLocator=${savedLocator?.href} currentLocator=${currentLocator.value?.href}" }
        view.listener = object : EpubViewListener {
            override fun onRawLocatorChange(locator: Locator) {
                logger.info { "[EPUB-DIAG] [RAW-LOCATOR-CB] incoming=${locator.href}#${locator.locations.fragments.firstOrNull()}" }
                currentLocator.value = locator
            }

            override fun onLocatorChange(locator: Locator) {
                logger.info { "[EPUB-DIAG] [TEXT-MOVE] NewLocator: ${locator.href}#${locator.locations.fragments.firstOrNull()} | ControllerReady: ${mediaOverlayController.value != null}" }
                logger.info {
                    "[komelia-epub] LOCATOR-CB: incoming=${locator.href} title=${locator.title} " +
                    "currentLocator=${currentLocator.value?.href}"
                }
                savedLocator = locator
                currentLocator.value = locator
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
                if (showSettings.value || showToc.value || showControls.value) {
                    showSettings.value = false
                    showToc.value = false
                    showControls.value = false
                } else {
                    showControls.value = true
                }
            }

            override fun onDoubleTouch(locator: Locator) {
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
        logger.info {
            "[komelia-epub] NAV-POS: index=$positionIndex → locator=${locator.href} " +
            "currentLocator_before=${currentLocator.value?.href}"
        }
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
    private suspend fun prepareEpubDirectory(): File = withContext(Dispatchers.IO) {
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
        extractedDir
    }
}
