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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.Locator
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

    // Timestamp of last audio-driven navigation — used to suppress feedback loops in F1.
    private var audioNavigatingAt = 0L
    private var lastHighlightedClip: OverlayPar? = null
    private var pendingUserLocator: Locator? = null   // page the user navigated to while paused
    private var pageTurnJob: Job? = null              // scheduled mid-clip page flip

    private val player: AudiobookPlayer = AudiobookPlayer(
        context = context,
        coroutineScope = coroutineScope,
        listener = object : Listener {
            override fun onClipChanged(overlayPar: OverlayPar) {
                pageTurnJob?.cancel()
                val view = epubView ?: return
                audioNavigatingAt = System.currentTimeMillis()
                lastHighlightedClip = overlayPar
                view.pendingProps.isPlaying = true
                view.pendingProps.locator = overlayPar.locator
                view.finalizeProps()
                if (_isPlaying.value) schedulePageTurnIfNeeded(overlayPar)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    pageTurnJob?.cancel()
                    epubView?.clearHighlightFragment()
                }
            }

            override fun onPositionChanged(position: Double) {
                if (!_isPlaying.value) return
                val currentClip = player.getCurrentClip() ?: return
                if (currentClip == lastHighlightedClip) return
                audioNavigatingAt = System.currentTimeMillis()
                lastHighlightedClip = currentClip
                val view = epubView ?: return
                view.pendingProps.isPlaying = true
                view.pendingProps.locator = currentClip.locator
                view.finalizeProps()
            }

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

    fun seekToNextClip() {
        val allClips = BookService.getOverlayClips(bookUuid)
        val current = player.getCurrentClip() ?: return
        val idx = allClips.indexOf(current)
        val next = allClips.getOrNull(idx + 1) ?: return
        player.seekTo(next.audioResource, next.start, skipEmit = false)
    }

    fun seekToPrevClip() {
        val allClips = BookService.getOverlayClips(bookUuid)
        val current = player.getCurrentClip() ?: return
        val idx = allClips.indexOf(current)
        val prev = allClips.getOrNull(idx - 1) ?: return
        player.seekTo(prev.audioResource, prev.start, skipEmit = false)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            player.pause()
        } else {
            _isPlaying.value = true          // Optimistic: onClipChanged needs this true
            val pending = pendingUserLocator
            pendingUserLocator = null
            if (pending != null) {
                findClipForLocator(pending)?.let { clip ->
                    player.seekTo(clip.audioResource, clip.start, skipEmit = true)
                }
                player.play(automaticRewind = false)
            } else {
                player.play()                // Normal play with automatic rewind
            }
        }
    }

    /** F1: Called when the user navigates to a new page. Seeks audio to the first clip of that page. */
    fun handleUserLocatorChange(locator: Locator) {
        // If audio set a locator within the last 500ms this was audio-driven — avoid feedback loop
        if (System.currentTimeMillis() - audioNavigatingAt < 500L) return
        if (!_isPlaying.value) {
            // Paused: remember where to start, don't highlight
            pendingUserLocator = locator
            return
        }
        // Playing: check for cross-page spanning paragraph
        handlePlayingLocatorChange(locator)
    }

    private fun handlePlayingLocatorChange(locator: Locator) {
        val currentClip = player.getCurrentClip()
        val newClip = findClipForLocator(locator)

        if (currentClip != null && newClip != null) {
            val allClips = BookService.getOverlayClips(bookUuid)
            val currentIdx = allClips.indexOf(currentClip)
            val newIdx = allClips.indexOf(newClip)

            if (newIdx == currentIdx + 1) {
                // New page's first clip is immediately adjacent — might be a spanning paragraph.
                // Check if the current fragment overflows (not entirely on screen).
                val fragmentId = currentClip.locator.locations.fragments.firstOrNull()
                if (fragmentId != null) {
                    epubView?.checkIsEntirelyOnScreen(fragmentId) { isOnScreen ->
                        if (!isOnScreen) {
                            // Spanning paragraph — user just paged through the overflow. Don't seek.
                            audioNavigatingAt = System.currentTimeMillis()
                            return@checkIsEntirelyOnScreen
                        }
                        // Fully fits on previous page — user intentionally jumped. Seek.
                        doSeekToLocator(locator)
                    }
                    return   // wait for async callback
                }
            }
        }
        doSeekToLocator(locator)
    }

    private fun doSeekToLocator(locator: Locator) {
        val clip = findClipForLocator(locator) ?: return
        player.seekTo(clip.audioResource, clip.start, skipEmit = true)
    }

    private fun schedulePageTurnIfNeeded(clip: OverlayPar) {
        val allClips = BookService.getOverlayClips(bookUuid)
        val currentIdx = allClips.indexOf(clip)
        val nextClip = allClips.getOrNull(currentIdx + 1) ?: return
        // href check removed — getFragmentVisibilityRatio handles "no overflow" via ratio >= 1.0
        val fragmentId = clip.locator.locations.fragments.firstOrNull() ?: return
        val clipDuration = (clip.end - clip.start).coerceAtLeast(0.1)

        epubView?.getFragmentVisibilityRatio(fragmentId) { ratio ->
            if (ratio >= 1.0) return@getFragmentVisibilityRatio   // fully visible, nothing to do
            val delayMs = (clipDuration * ratio * 1000).toLong()
            pageTurnJob = coroutineScope.launch {
                delay(delayMs)
                if (!_isPlaying.value) return@launch
                val view = epubView ?: return@launch
                audioNavigatingAt = System.currentTimeMillis()
                view.go(nextClip.locator)   // full locator so Readium finds the right column
            }
        }
    }

    /** F2: Called on double-tap. Seeks audio to that paragraph and starts playing. */
    fun handleDoubleTap(locator: Locator) {
        val clip = findClipForLocator(locator) ?: return
        player.seekTo(clip.audioResource, clip.start, skipEmit = false)
        if (!_isPlaying.value) player.play(automaticRewind = false)
    }

    private fun findClipForLocator(locator: Locator): OverlayPar? {
        val fragment = locator.locations.fragments.firstOrNull()
        return if (fragment != null) {
            runCatching { BookService.getClip(bookUuid, locator) }.getOrNull()
        } else {
            BookService.getFragments(bookUuid, locator).firstOrNull()
        }
    }

    fun release() {
        player.unload()
        epubView = null
    }
}
