package snd.komelia.ui.reader.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import coil3.compose.rememberAsyncImagePainter
import com.storyteller.reader.EpubView
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import snd.komelia.image.coil.BookDefaultThumbnailRequest
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komelia.ui.LocalHazeState
import snd.komelia.ui.LocalImmersiveColorAlpha
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalTheme
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.LocalWindowState
import snd.komelia.ui.common.immersive.extractDominantColor
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.reader.ReaderTopBar
import snd.komelia.ui.reader.epub.audio.AudioFullScreenPlayer
import snd.komelia.ui.reader.epub.audio.AudioMiniPlayer

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
actual fun Epub3ReaderContent(state: EpubReaderState) {
    val activity = LocalContext.current as FragmentActivity
    val epub3State = state as? Epub3ReaderState
    val useNewUI2 = LocalUseNewLibraryUI2.current

    val settingsFlow = remember(epub3State) {
        epub3State?.settings ?: MutableStateFlow(Epub3NativeSettings())
    }
    val settings by settingsFlow.collectAsState()
    val themeBgColor = Color(settings.theme.background)

    val coroutineScope = rememberCoroutineScope()
    val playerTransitionState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(playerTransitionState, label = "audio-player")

    val theme = LocalTheme.current
    val readerHazeState = if (theme.transparentBars) rememberHazeState() else null

    CompositionLocalProvider(LocalHazeState provides readerHazeState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeBgColor)
        ) {
            Box(
                Modifier.fillMaxSize().then(
                    if (readerHazeState != null) Modifier.hazeSource(readerHazeState) else Modifier
                )
            ) {
                Spacer(Modifier.fillMaxSize().background(themeBgColor))
                AndroidView(
                    factory = { ctx ->
                        EpubView(context = ctx, activity = activity, shouldApplyInsetsPadding = false).also { view ->
                            epub3State?.onEpubViewCreated(view)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = settings.topMargin.dp, bottom = settings.bottomMargin.dp)
                )
            }

            if (epub3State != null) {
                val showControls by epub3State.showControls.collectAsState()

                if (LocalPlatform.current == MOBILE) {
                    val windowState = LocalWindowState.current
                    DisposableEffect(showControls) {
                        if (showControls) windowState.setFullscreen(false)
                        else windowState.setFullscreen(true)
                        onDispose { windowState.setFullscreen(false) }
                    }
                }

                val showSettings by epub3State.showSettings.collectAsState()
                val showContentDialog by epub3State.showContentDialog.collectAsState()
                val bookmarks by epub3State.bookmarks.collectAsState()
                val toc by epub3State.tableOfContents.collectAsState()
                val positions by epub3State.positions.collectAsState()
                val controller by epub3State.mediaOverlayController.collectAsState()
                val currentLocator by epub3State.currentLocator.collectAsState()

                val dateTimeText by produceState("") {
                    while (true) {
                        val now = java.time.LocalDateTime.now()
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm · EEE, MMM d")
                        value = now.format(formatter)
                        val secondsUntilNextMinute = 60L - now.second
                        kotlinx.coroutines.delay(secondsUntilNextMinute * 1000L)
                    }
                }
                val overlayColor = Color(settings.theme.foreground).copy(alpha = 0.45f)

                val chapterTitle = remember(currentLocator, toc) {
                    currentLocator?.let { loc ->
                        loc.title
                            ?: findTocLink(toc, loc.href)?.title
                            ?: loc.href.toString()
                                .substringAfterLast('/').substringBeforeLast('.')
                                .replace('-', ' ').replace('_', ' ')
                    } ?: ""
                }

                val density = LocalDensity.current
                var cardHeightPx by remember { mutableStateOf(0) }
                val audioPlayerBottomPadding by animateDpAsState(
                    targetValue = if (showControls && positions.isNotEmpty()) {
                        with(density) { cardHeightPx.toDp() } + 10.dp
                    } else {
                        10.dp
                    },
                    label = "AudioPlayerBottomPadding"
                )


                if (showControls) {
                    if (useNewUI2) {
                        val book by epub3State.book.collectAsState()
                        ReaderTopBar(
                            seriesTitle = book?.seriesTitle ?: "",
                            bookTitle = book?.metadata?.title ?: "",
                            onBack = { epub3State.closeWebview() },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    } else {
                        // Top bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopStart)
                                .background(MaterialTheme.colorScheme.surface)
                                .statusBarsPadding()
                        ) {
                            IconButton(onClick = { epub3State.closeWebview() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave")
                            }
                            val book by epub3State.book.collectAsState()
                            Text(
                                text = book?.metadata?.title ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Bottom navigation card
                if (positions.isNotEmpty()) {
                    AnimatedVisibility(
                        visible = showControls,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        if (useNewUI2) {
                            Epub3ControlsCardNewUI(
                                state = epub3State,
                                onSettingsClick = { epub3State.toggleSettings() },
                                onChapterClick = { epub3State.openContentDialog(0) },
                                onBookmarkToggle = { epub3State.toggleBookmark(it) },
                                onCardHeightChanged = { cardHeightPx = it },
                            )
                        } else {
                            Epub3ControlsCard(
                                state = epub3State,
                                onDismiss = { epub3State.toggleControls() },
                                onCardHeightChanged = { cardHeightPx = it },
                                onSettingsClick = { epub3State.toggleSettings() },
                                onChapterClick = { epub3State.openContentDialog(0) },
                                onBookmarkToggle = { epub3State.toggleBookmark(it) },
                            )
                        }
                    }
                }

                // SharedTransitionLayout fills the full screen so shared elements have the full
                // coordinate space to fly between the mini-player pill and the full-screen sheet.
                controller?.let { ctrl ->
                    val book by epub3State.book.collectAsState()

                    val bookId by epub3State.bookId.collectAsState()
                    val coverRequest = remember(bookId) { BookDefaultThumbnailRequest(bookId) }
                    val coverPainter = rememberAsyncImagePainter(model = coverRequest)
                    var dominantColor by remember(bookId) { mutableStateOf<Color?>(null) }
                    LaunchedEffect(bookId) { dominantColor = extractDominantColor(coverPainter) }

                    val immersiveEnabled = LocalImmersiveColorEnabled.current
                    val immersiveAlpha = LocalImmersiveColorAlpha.current
                    val surface = MaterialTheme.colorScheme.surface
                    val playerBackgroundColor = remember(dominantColor, immersiveEnabled, immersiveAlpha) {
                        if (immersiveEnabled && dominantColor != null)
                            dominantColor!!.copy(alpha = immersiveAlpha).compositeOver(surface)
                        else surface
                    }

                    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Mini player at bottom — fades out as shared elements morph upward
                            playerTransition.AnimatedVisibility(
                                visible = { !it },
                                enter = fadeIn(tween(300)),
                                exit = fadeOut(tween(200)),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .then(if (useNewUI2) Modifier.fillMaxWidth().padding(horizontal = 16.dp) else Modifier.padding(horizontal = 2.dp))
                                    .padding(bottom = audioPlayerBottomPadding),
                            ) {
                                AudioMiniPlayer(
                                    controller = ctrl,
                                    bookId = epub3State.bookId.value,
                                    bookTitle = book?.metadata?.title ?: "",
                                    chapterTitle = chapterTitle,
                                    backgroundColor = playerBackgroundColor,
                                    onCoverClick = { coroutineScope.launch { playerTransitionState.animateTo(true) } },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    useNewUI2 = useNewUI2,
                                )
                            }

                            // Full-screen player — sharedBounds on its Surface drives the animation
                            playerTransition.AnimatedVisibility(
                                visible = { it },
                                enter = EnterTransition.None,
                                exit = fadeOut(tween(500)),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AudioFullScreenPlayer(
                                    controller = ctrl,
                                    bookId = epub3State.bookId.value,
                                    bookTitle = book?.metadata?.title ?: "",
                                    chapterTitle = chapterTitle,
                                    backgroundColor = playerBackgroundColor,
                                    positions = positions,
                                    currentLocator = currentLocator,
                                    onNavigateToPosition = epub3State::navigateToPosition,
                                    onDismiss = { coroutineScope.launch { playerTransitionState.animateTo(false) } },
                                    onDrag = { fraction ->
                                        coroutineScope.launch { playerTransitionState.seekTo(fraction, targetState = false) }
                                    },
                                    onDragEnd = { fraction ->
                                        coroutineScope.launch {
                                            if (fraction > 0.15f) playerTransitionState.animateTo(false)
                                            else playerTransitionState.animateTo(true)
                                        }
                                    },
                                    onChapterClick = { epub3State.openContentDialog(0) },
                                    playbackSpeed = settings.playbackSpeed,
                                    onSpeedChange = { epub3State.updateSettings(settings.copy(playbackSpeed = it)) },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this,
                                    modifier = Modifier.fillMaxSize().align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }

                // Settings card
                AnimatedVisibility(
                    visible = showSettings,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val userFonts by remember(epub3State) {
                        epub3State?.userFonts ?: MutableStateFlow(emptyList())
                    }.collectAsState()
                    Epub3SettingsCard(
                        settings = settings,
                        onSettingsChange = epub3State::updateSettings,
                        onDismiss = { epub3State.toggleSettings() },
                        userFonts = userFonts,
                        onLoadFont = { epub3State.loadFont(it) },
                        onDeleteFont = { epub3State.deleteFont(it) },
                    )
                }

                // Date/time overlay — top left, shown when controls are hidden.
                // Delayed enter so the overlay only appears after the fullscreen/inset
                // transition has settled (avoids a jump from status-bar height to zero).
                AnimatedVisibility(
                    visible = settings.showDateTimeOverlay && !showControls,
                    enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = 400)),
                    exit = ExitTransition.None,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Text(
                        text = dateTimeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = overlayColor,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 8.dp, top = 4.dp),
                    )
                }

                // Location overlay — top right, shown when controls are hidden.
                AnimatedVisibility(
                    visible = settings.showLocationOverlay && !showControls && positions.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = 400)),
                    exit = ExitTransition.None,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    val locationIndex = remember(currentLocator, positions) {
                        locatorToPositionIndex(positions, currentLocator)
                    }
                    Text(
                        text = "Loc. ${locationIndex + 1} of ${positions.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = overlayColor,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(end = 8.dp, top = 4.dp),
                    )
                }

                // Content dialog
                AnimatedVisibility(
                    visible = showContentDialog,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Epub3ContentDialog(
                        toc = toc,
                        bookmarks = bookmarks,
                        currentHref = currentLocator?.href,
                        currentLocator = currentLocator,
                        onNavigateLink = { link ->
                            epub3State.navigateToLink(link)
                            epub3State.showContentDialog.value = false
                        },
                        onNavigateLocator = {
                            epub3State.navigateToLocator(it)
                            epub3State.showContentDialog.value = false
                        },
                        onDeleteBookmark = { epub3State.deleteBookmark(it) },
                        onDismiss = { epub3State.showContentDialog.value = false },
                        initialTab = epub3State.initialContentTab
                    )
                }
            }
        }
    }

    BackPressHandler {
        if (playerTransitionState.currentState || playerTransitionState.targetState) {
            coroutineScope.launch { playerTransitionState.animateTo(false) }
        } else state.onBackButtonPress()
    }
}
