package snd.komelia.ui.oneshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeries
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
import snd.komelia.image.coil.SeriesDefaultThumbnailRequest
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalUseNewLibraryUI
import snd.komelia.ui.common.immersive.ImmersiveDetailFab
import snd.komelia.ui.common.immersive.ImmersiveDetailScaffold
import snd.komelia.ui.platform.PlatformType

class OneshotScreen(
    val seriesId: KomgaSeriesId,
    private val bookSiblingsContext: BookSiblingsContext,
    @Transient private val series: KomgaSeries? = null,
    @Transient private val book: KomeliaBook? = null,
) : ReloadableScreen {
    constructor(series: KomgaSeries, bookSiblingsContext: BookSiblingsContext) : this(
        seriesId = series.id,
        bookSiblingsContext = bookSiblingsContext,
        series = series,
        book = null
    )

    constructor(book: KomeliaBook, bookSiblingsContext: BookSiblingsContext) : this(
        seriesId = book.seriesId,
        bookSiblingsContext = bookSiblingsContext,
        series = null,
        book = book
    )

    override val key: ScreenKey = seriesId.toString()

    @OptIn(InternalVoyagerApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(seriesId.value) {
            viewModelFactory.getOneshotViewModel(seriesId, series, book)
        }
        val reloadEvents = LocalReloadEvents.current
        LaunchedEffect(seriesId) {
            vm.initialize()
            reloadEvents.collect { vm.reload() }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val platform = LocalPlatform.current
        val useNewUI = LocalUseNewLibraryUI.current
        if (platform == PlatformType.MOBILE && useNewUI) {
            ImmersiveDetailScaffold(
                coverData = SeriesDefaultThumbnailRequest(seriesId),
                coverKey = "series-$seriesId",
                cardColor = LocalAccentColor.current,
                immersive = true,
                topBarContent = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, top = 8.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .clickable { onBackPress(navigator, vm.series.value?.libraryId) },
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
                            text = vm.series.collectAsState().value?.metadata?.title ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Immersive Oneshot Boilerplate")
                        Text("Scroll anywhere on the card to see the cover shrink animation.")

                        // Add some height to enable scrolling/dragging if needed
                        Spacer(Modifier.height(1000.dp))
                    }
                }
            )

            BackPressHandler {
                vm.series.value?.let { onBackPress(navigator, it.libraryId) }
            }
            return
        }

        val state = vm.state.collectAsState().value
        val book = vm.book.collectAsState().value
        val library = vm.library.collectAsState().value
        val series = vm.series.collectAsState().value
        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            when {
                state is LoadState.Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = vm::reload
                )

                book == null || series == null || library == null -> LoadingMaxSizeIndicator()
                else -> OneshotScreenContent(
                    series = series,
                    book = book,
                    library = library,
                    onLibraryClick = { navigator.push(LibraryScreen(it.id)) },
                    onBookReadClick = { markReadProgress ->
                        navigator.parent?.push(
                            readerScreen(
                                book = book,
                                markReadProgress = markReadProgress,
                                bookSiblingsContext = bookSiblingsContext,
                            )
                        )
                    },
                    oneshotMenuActions = vm.bookMenuActions,
                    collections = vm.collectionsState.collections,
                    onCollectionClick = { collection -> navigator.push(CollectionScreen(collection.id)) },
                    onSeriesClick = { navigator.push(seriesScreen(it)) },

                    readLists = vm.readListsState.readLists,
                    onReadListClick = { navigator.push(ReadListScreen(it.id)) },
                    onReadlistBookClick = { book, readList ->
                        navigator push bookScreen(
                            book = book,
                            bookSiblingsContext = BookSiblingsContext.ReadList(readList.id)
                        )
                    },
                    onFilterClick = { filter ->
                        navigator.popUntilRoot()
                        navigator.dispose(navigator.lastItem)
                        navigator.replaceAll(LibraryScreen(book.libraryId, filter))
                    },
                    onBookDownload = vm::onBookDownload,
                    onBookDownloadDelete = vm::onBookDownloadDelete,
                    cardWidth = vm.cardWidth.collectAsState().value,
                )
            }
            BackPressHandler {
                vm.series.value?.let { onBackPress(navigator, it.libraryId) }
            }
        }
    }

    private fun onBackPress(navigator: Navigator, libraryId: KomgaLibraryId?) {
        if (navigator.canPop) {
            navigator.pop()
        } else {
            when (val context = bookSiblingsContext) {
                is BookSiblingsContext.ReadList -> navigator.replace(ReadListScreen(context.id))
                BookSiblingsContext.Series -> libraryId?.let { navigator.replace(LibraryScreen(it)) }
            }

        }
    }
}