package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import snd.komelia.image.coil.BookPageThumbnailRequest
import snd.komelia.ui.reader.image.PageMetadata

@Composable
fun ThumbnailCarousel(
    pages: List<PageMetadata>,
    currentPageIndex: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)
    val context = LocalPlatformContext.current
    val imageLoader = SingletonImageLoader.get(context)

    val flingBehavior = ScrollableDefaults.flingBehavior()

    LazyRow(
        state = lazyListState,
        flingBehavior = flingBehavior,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(200.dp)
    ) {
        itemsIndexed(pages) { index, page ->
            BookPageThumbnail(
                page = page,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f)
                    .clickable { onPageChange(index) }
            )
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .debounce(50)
            .collect { visibleItems ->
                if (visibleItems.isEmpty()) return@collect
                val firstIndex = visibleItems.first().index
                val lastIndex = visibleItems.last().index

                val preCacheRange = (firstIndex - 5)..(lastIndex + 5)
                preCacheRange.forEach { index ->
                    if (index in pages.indices && visibleItems.none { it.index == index }) {
                        val page = pages[index]
                        val pageId = page.toPageId()
                        val request = ImageRequest.Builder(context)
                            .data(BookPageThumbnailRequest(page.bookId, page.pageNumber))
                            .memoryCacheKey(pageId.toString())
                            .diskCacheKey(pageId.toString())
                            .precision(Precision.INEXACT)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
    }

    LaunchedEffect(currentPageIndex) {
        if (lazyListState.firstVisibleItemIndex != currentPageIndex) {
            lazyListState.scrollToItem(currentPageIndex)
        }
    }
}

