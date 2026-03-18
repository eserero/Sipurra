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
            BookService.openPublication(bookUuid, extractedDir.toURI().toURL(), clips = null)

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
        }
        view.pendingProps.bookUuid = bookUuid
        view.pendingProps.locator = savedLocator
        view.finalizeProps()
    }

    /** No-op: this reader does not use a WebView. */
    override fun onWebviewCreated(webview: KomeliaWebview) = Unit

    override fun onBackButtonPress() = closeWebview()

    override fun closeWebview() {
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
            val epubBytes = bookApi.getBookRawFile(bookId.value)
            val zipFile = File(context.cacheDir, "epub3/$bookUuid.epub")
            zipFile.writeBytes(epubBytes)
            BookService.extractArchive(
                URL("file://${zipFile.absolutePath}"),
                extractedDir.toURI().toURL()
            )
            zipFile.delete()
        }
        return extractedDir
    }
}
