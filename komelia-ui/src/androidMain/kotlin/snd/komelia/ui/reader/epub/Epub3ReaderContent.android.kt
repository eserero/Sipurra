package snd.komelia.ui.reader.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import snd.komelia.settings.model.Epub3NativeSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.storyteller.reader.EpubView
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.reader.epub.audio.AudioMiniPlayer

@Composable
actual fun Epub3ReaderContent(state: EpubReaderState) {
    val activity = LocalContext.current as FragmentActivity
    val epub3State = state as? Epub3ReaderState

    val settingsFlow = remember(epub3State) {
        epub3State?.settings ?: MutableStateFlow(Epub3NativeSettings())
    }
    val settings by settingsFlow.collectAsState()
    val themeBgColor = Color(settings.theme.background)

    Box(modifier = Modifier.fillMaxSize().background(themeBgColor)) {
        AndroidView(
            factory = { ctx ->
                EpubView(context = ctx, activity = activity).also { view ->
                    epub3State?.onEpubViewCreated(view)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 66.dp)
        )

        if (epub3State != null) {
            val showControls by epub3State.showControls.collectAsState()
            val showSettings by epub3State.showSettings.collectAsState()
            val showToc by epub3State.showToc.collectAsState()
            val toc by epub3State.tableOfContents.collectAsState()
            val positions by epub3State.positions.collectAsState()
            val controller by epub3State.mediaOverlayController.collectAsState()

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
                // Scrim — tap outside dismisses
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { epub3State.toggleControls() }
                )
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

            // Bottom navigation card — AnimatedVisibility manages enter/exit animation
            if (positions.isNotEmpty()) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Epub3ControlsCard(
                        state = epub3State,
                        onDismiss = { epub3State.toggleControls() },
                        onCardHeightChanged = { cardHeightPx = it },
                        onSettingsClick = { epub3State.toggleSettings() },
                        onChapterClick = { epub3State.toggleToc() },
                    )
                }
            }

            // AudioMiniPlayer renders above controls card/scrim but below settings card
            controller?.let {
                AudioMiniPlayer(
                    controller = it,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = audioPlayerBottomPadding)
                )
            }

            // Settings card — slides up over controls card and audio mini player
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Epub3SettingsCard(
                    settings = settings,
                    onSettingsChange = epub3State::updateSettings,
                    onDismiss = { epub3State.toggleSettings() },
                )
            }

            // TOC dialog
            if (showToc) {
                Epub3TocDialog(
                    toc = toc,
                    onNavigate = { link ->
                        epub3State.navigateToLink(link)
                        epub3State.showToc.value = false
                    },
                    onDismiss = { epub3State.showToc.value = false },
                )
            }
        }
    }

    BackPressHandler(state::onBackButtonPress)
}
