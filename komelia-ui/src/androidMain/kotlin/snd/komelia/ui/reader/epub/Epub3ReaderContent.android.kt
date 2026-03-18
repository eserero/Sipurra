package snd.komelia.ui.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.storyteller.reader.EpubView
import snd.komelia.ui.platform.BackPressHandler

@Composable
actual fun Epub3ReaderContent(state: EpubReaderState) {
    val activity = LocalContext.current as FragmentActivity
    val epub3State = state as? Epub3ReaderState

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                EpubView(context = ctx, activity = activity).also { view ->
                    epub3State?.onEpubViewCreated(view)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (epub3State != null) {
            val showControls by epub3State.showControls.collectAsState()
            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { epub3State.toggleControls() }
                )
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
    }

    BackPressHandler(state::onBackButtonPress)
}
