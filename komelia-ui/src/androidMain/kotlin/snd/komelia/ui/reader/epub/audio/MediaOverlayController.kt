package snd.komelia.ui.reader.epub.audio

import android.content.Context
import androidx.core.net.toUri
import com.storyteller.reader.AudiobookPlayer
import com.storyteller.reader.BookService
import com.storyteller.reader.EpubView
import com.storyteller.reader.Listener
import com.storyteller.reader.OverlayPar
import com.storyteller.reader.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.fromEpubHref
import java.io.File

class MediaOverlayController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val bookUuid: String,
    private val extractedDir: File,
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var epubView: EpubView? = null
    private val player = AudiobookPlayer(
        context = context,
        coroutineScope = coroutineScope,
        listener = object : Listener {
            override fun onClipChanged(overlayPar: OverlayPar) {
                val view = epubView ?: return
                view.pendingProps.isPlaying = true
                view.pendingProps.locator = overlayPar.locator
                view.finalizeProps()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) epubView?.clearHighlightFragment()
            }

            override fun onPositionChanged(position: Double) {}

            override fun onTrackChanged(track: Track, position: Double, index: Int) {}
        }
    )

    @OptIn(InternalReadiumApi::class)
    suspend fun initialize(clips: List<OverlayPar>) {
        if (clips.isEmpty()) return

        val publication = BookService.getPublication(bookUuid) ?: return

        val durationByResource = mutableMapOf<String, Double>()
        for (clip in clips) {
            durationByResource[clip.audioResource] =
                (durationByResource[clip.audioResource] ?: 0.0) + (clip.end - clip.start)
        }

        val tracks = durationByResource.keys.mapNotNull { resource ->
            val url = Url.fromEpubHref(resource) ?: return@mapNotNull null
            val link = publication.linkWithHref(url)
            Track(
                uri = File(extractedDir, resource).toUri(),
                bookUuid = bookUuid,
                title = link?.title ?: resource,
                duration = durationByResource[resource] ?: 0.0,
                bookTitle = publication.metadata.title ?: "",
                author = publication.metadata.authors.firstOrNull()?.name,
                coverUri = null,
                relativeUri = resource,
                narrator = null,
                mimeType = link?.mediaType?.toString() ?: "audio/mpeg"
            )
        }

        if (tracks.isNotEmpty()) {
            player.loadTracks(tracks)
        }
    }

    fun attachView(view: EpubView) {
        epubView = view
    }

    fun togglePlayPause() {
        if (_isPlaying.value) player.pause() else player.play()
    }

    fun release() {
        player.unload()
        epubView = null
    }
}
