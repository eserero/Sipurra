package snd.komelia.ui.reader.epub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import snd.komelia.bookmarks.EpubBookmark

@Composable
fun Epub3BookmarksDialog(
    bookmarks: List<EpubBookmark>,
    currentLocator: Locator?,
    onAddBookmark: (Locator) -> Unit,
    onDeleteBookmark: (EpubBookmark) -> Unit,
    onNavigate: (Locator) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxHeightDp = (configuration.screenHeightDp * 0.8f).dp

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = "Bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Button(
                    onClick = {
                        if (currentLocator != null) {
                            onAddBookmark(currentLocator)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = currentLocator != null
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Bookmark this page")
                }

                if (bookmarks.isEmpty()) {
                    Text(
                        text = "No bookmarks yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = maxHeightDp)
                    ) {
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
