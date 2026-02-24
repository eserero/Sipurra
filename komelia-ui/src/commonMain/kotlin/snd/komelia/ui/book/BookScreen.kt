package snd.komelia.ui.book

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.ImageReaderScreen
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komga.client.book.KomgaBookId
import snd.komga.client.series.KomgaSeriesId
import kotlin.jvm.Transient

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.common.immersive.ImmersiveDetailFab
import snd.komelia.ui.common.immersive.ImmersiveDetailScaffold
import snd.komelia.ui.platform.PlatformType

fun bookScreen(
    book: KomeliaBook,
    bookSiblingsContext: BookSiblingsContext? = null
): Screen {
    val context = bookSiblingsContext ?: BookSiblingsContext.Series
    return if (book.oneshot) OneshotScreen(book, context)
    else BookScreen(
        book = book,
        bookSiblingsContext = context
    )
}

class BookScreen(
    val bookId: KomgaBookId,
    private val bookSiblingsContext: BookSiblingsContext,
    @Transient
    val book: KomeliaBook? = null,
) : ReloadableScreen {
    constructor(book: KomeliaBook, bookSiblingsContext: BookSiblingsContext) : this(book.id, bookSiblingsContext, book)

    override val key: ScreenKey = bookId.toString()

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(bookId.value) { viewModelFactory.getBookViewModel(bookId, book) }
        val navigator = LocalNavigator.currentOrThrow
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(Unit) {
            vm.initialize()
            vm.book.value?.let { if (it.oneshot) navigator.replace(OneshotScreen(it, bookSiblingsContext)) }
            reloadEvents.collect { vm.reload() }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventsHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val platform = LocalPlatform.current
        val useNewUI = LocalUseNewLibraryUI.current
        if (platform == PlatformType.MOBILE && useNewUI) {
            ImmersiveDetailScaffold(
                coverData = BookDefaultThumbnailRequest(bookId),
                coverKey = "book-$bookId",
                cardColor = LocalAccentColor.current,
                immersive = true,
                topBarContent = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, top = 8.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .clickable { onBackPress(navigator, vm.book.value?.seriesId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                fabContent = {
                    ImmersiveDetailFab(
                        onReadClick = {},
                        onReadIncognitoClick = {},
                        onDownloadClick = {},
                        accentColor = LocalAccentColor.current,
                    )
                },
                cardContent = { expandFraction ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .padding(start = (126.dp * expandFraction).coerceAtLeast(0.dp))
                    ) {
                        Text(
                            text = vm.book.collectAsState().value?.metadata?.title ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Immersive Book Boilerplate")
                        Text("Scroll anywhere on the card to see the cover shrink animation.")

                        // Add some height to enable scrolling/dragging if needed
                        Spacer(Modifier.height(1000.dp))
                    }
                }
            )

            BackPressHandler { onBackPress(navigator, vm.book.value?.seriesId) }
            return
        }

        val book = vm.book.collectAsState().value

        ScreenPullToRefreshBox(
            screenState = vm.state,
            onRefresh = vm::reload
        ) {
            BookScreenContent(
                library = vm.library,
                book = book,
                bookMenuActions = vm.bookMenuActions,
                onBookReadPress = { markReadProgress ->
                    navigator.parent?.push(
                        if (book != null) readerScreen(
                            book = book,
                            bookSiblingsContext = bookSiblingsContext,
                            markReadProgress = markReadProgress
                        )
                        else ImageReaderScreen(
                            bookId = bookId,
                            bookSiblingsContext = bookSiblingsContext,
                            markReadProgress = markReadProgress
                        )
                    )
                },
                onBookDownload = vm::onBookDownload,
                onBookDownloadDelete = vm::onBookDownloadDelete,

                readLists = vm.readListsState.readLists,
                onReadListClick = { navigator.push(ReadListScreen(it.id)) },
                onReadListBookPress = { book, readList ->
                    if (book.id != bookId) navigator.push(
                        bookScreen(
                            book = book,
                            bookSiblingsContext = BookSiblingsContext.ReadList(readList.id)
                        )
                    )
                },
                onParentSeriesPress = { book?.seriesId?.let { seriesId -> navigator.push(SeriesScreen(seriesId)) } },
                onFilterClick = { filter ->
                    navigator.push(LibraryScreen(requireNotNull(book?.libraryId), filter))
                },
                cardWidth = vm.cardWidth.collectAsState().value,
            )

            BackPressHandler { onBackPress(navigator, book?.seriesId) }
        }
    }

    private fun onBackPress(navigator: Navigator, seriesId: KomgaSeriesId?) {
        if (navigator.canPop) {
            navigator.pop()
        } else {
            when (val context = bookSiblingsContext) {
                is BookSiblingsContext.ReadList -> navigator.replace(ReadListScreen(context.id))
                BookSiblingsContext.Series -> seriesId?.let { navigator.replace(SeriesScreen(it)) }
            }

        }
    }

}