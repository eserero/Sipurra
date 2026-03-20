package snd.komelia.ui.reader.epub

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Link
import snd.komelia.ui.LocalAccentColor
import snd.komelia.ui.common.components.AppSlider
import snd.komelia.ui.common.components.AppSliderDefaults
import kotlin.math.roundToInt

fun findTocLink(links: List<Link>, hrefStr: String): Link? {
    for (link in links) {
        if (link.href.toString() == hrefStr) return link
        findTocLink(link.children, hrefStr)?.let { return it }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Epub3ControlsCard(
    state: Epub3ReaderState,
    onDismiss: () -> Unit,
    onCardHeightChanged: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onChapterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val positions by state.positions.collectAsState()
    val currentLocator by state.currentLocator.collectAsState()
    val toc by state.tableOfContents.collectAsState()
    val totalPages = positions.size

    val currentPageIndex = remember(currentLocator, positions) {
        currentLocator?.locations?.position
            ?.let { pos -> positions.indexOfFirst { it.locations.position == pos }.takeIf { it >= 0 } }
            ?: 0
    }

    var sliderDraft by remember(currentPageIndex) { mutableStateOf(currentPageIndex.toFloat()) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val accentColor = LocalAccentColor.current

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier
            .onSizeChanged { onCardHeightChanged(it.height) }
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY > 120f) onDismiss()
                                else dragOffsetY = 0f
                            },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { _, delta ->
                                dragOffsetY = (dragOffsetY + delta).coerceAtLeast(0f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }

            // Page slider
            if (totalPages > 1) {
                AppSlider(
                    value = sliderDraft,
                    onValueChange = { sliderDraft = it },
                    onValueChangeFinished = { state.navigateToPosition(sliderDraft.roundToInt()) },
                    valueRange = 0f..(totalPages - 1).toFloat(),
                    steps = 0,
                    accentColor = accentColor,
                    colors = AppSliderDefaults.colors(accentColor = accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            // Page label
            Text(
                text = "Page ${sliderDraft.roundToInt() + 1} of $totalPages",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Chapter chip — show whenever we have a locator; fall back to filename when no title.
            val locator = currentLocator
            if (locator != null) {
                val chapterTitle = locator.title
                    ?: findTocLink(toc, locator.href.toString())?.title
                    ?: locator.href.toString()
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                        .replace('-', ' ')
                        .replace('_', ' ')

                SuggestionChip(
                    onClick = onChapterClick,
                    label = {
                        Text(
                            text = chapterTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                )
            }

            // Settings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Tune, contentDescription = "Reader settings")
                }
            }
        }
    }
}
