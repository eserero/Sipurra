package snd.komelia.ui.reader.epub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import snd.komelia.bookmarks.EpubBookmark
import snd.komelia.ui.LocalAccentColor
import kotlin.math.roundToInt

private fun containsHref(link: Link, targetHref: Url): Boolean {
    if (link.url()?.removeFragment() == targetHref) return true
    return link.children.any { containsHref(it, targetHref) }
}

private fun expandAncestors(links: List<Link>, targetHref: Url, expandedState: MutableMap<String, Boolean>) {
    for (link in links) {
        if (link.children.any { containsHref(it, targetHref) }) {
            expandedState[link.href.toString()] = true
            expandAncestors(link.children, targetHref, expandedState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Epub3ContentDialog(
    toc: List<Link>,
    bookmarks: List<EpubBookmark>,
    currentHref: Url?,
    currentLocator: Locator?,
    onNavigateLink: (Link) -> Unit,
    onNavigateLocator: (Locator) -> Unit,
    onDeleteBookmark: (EpubBookmark) -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier,
) {
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 2f / 3f).dp
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val theme = snd.komelia.ui.LocalTheme.current
    val surfaceColor = if (theme.type == snd.komelia.ui.Theme.ThemeType.DARK) Color(43, 43, 43)
    else MaterialTheme.colorScheme.background

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = surfaceColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
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

            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Contents") },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Bookmarks") },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (page) {
                    0 -> ContentsTab(toc, currentHref, onNavigateLink)
                    1 -> BookmarksTab(bookmarks, onNavigateLocator, onDeleteBookmark)
                }
            }
        }
    }
}

@Composable
private fun ContentsTab(
    toc: List<Link>,
    currentHref: Url?,
    onNavigate: (Link) -> Unit,
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val lazyListState = rememberLazyListState()

    val currentTopLevelIndex = remember(currentHref, toc) {
        if (currentHref == null) -1
        else {
            val target = currentHref.removeFragment()
            toc.indexOfFirst { containsHref(it, target) }
        }
    }

    LaunchedEffect(currentHref) {
        if (currentHref != null) {
            val target = currentHref.removeFragment()
            expandAncestors(toc, target, expandedState)
            if (currentTopLevelIndex >= 0) {
                lazyListState.scrollToItem(currentTopLevelIndex)
            }
        }
    }

    if (toc.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No chapters available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            state = lazyListState,
        ) {
            itemsIndexed(toc) { index, link ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                TocRow(
                    link = link,
                    depth = 0,
                    currentHref = currentHref,
                    expandedState = expandedState,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun TocRow(
    link: Link,
    depth: Int,
    currentHref: Url?,
    expandedState: MutableMap<String, Boolean>,
    onNavigate: (Link) -> Unit,
) {
    val key = link.href.toString()
    val hasChildren = link.children.isNotEmpty()
    val isExpanded = expandedState[key] ?: false
    val title = link.title
        ?: link.href.toString().substringAfterLast('/').substringBeforeLast('.')

    val isCurrentChapter = currentHref != null &&
        link.url()?.removeFragment() == currentHref.removeFragment()

    val accentColor = LocalAccentColor.current ?: MaterialTheme.colorScheme.secondary
    val buttonColors = if (isCurrentChapter) {
        ButtonDefaults.textButtonColors(
            containerColor = accentColor.copy(alpha = 0.15f),
            contentColor = accentColor,
        )
    } else {
        ButtonDefaults.textButtonColors()
    }

    if (hasChildren) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp),
        ) {
            TextButton(
                onClick = { onNavigate(link) },
                colors = buttonColors,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { expandedState[key] = !isExpanded }) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                )
            }
        }
        if (isExpanded) {
            link.children.forEach { child ->
                TocRow(
                    link = child,
                    depth = depth + 1,
                    currentHref = currentHref,
                    expandedState = expandedState,
                    onNavigate = onNavigate,
                )
            }
        }
    } else {
        TextButton(
            onClick = { onNavigate(link) },
            colors = buttonColors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(start = (depth * 16).dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<EpubBookmark>,
    onNavigate: (Locator) -> Unit,
    onDeleteBookmark: (EpubBookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No bookmarks yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn {
            itemsIndexed(bookmarks) { index, bookmark ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BookmarkRow(
                    bookmark = bookmark,
                    onNavigate = onNavigate,
                    onDelete = { onDeleteBookmark(bookmark) }
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: EpubBookmark,
    onNavigate: (Locator) -> Unit,
    onDelete: () -> Unit,
) {
    val locator = runCatching { Locator.fromJSON(JSONObject(bookmark.locatorJson)) }.getOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { locator?.let { onNavigate(it) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val position = locator?.locations?.position ?: locator?.locations?.progression?.let { (it * 100).toInt() } ?: 0
            Text(
                text = "Bookmark $position",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            val rawTitle = locator?.title ?: locator?.href?.toString()?.substringAfterLast('/') ?: "Chapter"
            val snippet = rawTitle.split(Regex("\\s+")).take(10).joinToString(" ")
            
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete bookmark")
        }
    }
}
