package snd.komelia.ui.reader.epub.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.storyteller.reader.AudiobookPlayer
import com.storyteller.reader.Listener
import com.storyteller.reader.OverlayPar
import com.storyteller.reader.Track
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snd.komelia.audiobook.AudioBookmark
import snd.komelia.audiobook.AudioBookmarkRepository
import snd.komelia.audiobook.AudioFolderTrack
import snd.komelia.audiobook.AudioPosition
import snd.komelia.audiobook.AudioPositionRepository
import snd.komelia.settings.model.Epub3NativeSettings
import snd.komga.client.book.KomgaBookId
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val AUDIO_EXTENSIONS = setOf("mp3", "mp4", "m4a", "m4b", "ogg", "aac", "flac", "opus")
private val AUDIO_FOLDER_NAMES = setOf("audio", "audiobook")

class AudiobookFolderController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val bookUuid: String,
    private val bookId: KomgaBookId,
    private val extractedDir: File,
    private val audioPositionRepository: AudioPositionRepository,
    private val audioBookmarkRepository: AudioBookmarkRepository,
    private val onBookmarkChange: () -> Unit = {},
) : EpubAudioController {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume

    private val _elapsedSeconds = MutableStateFlow(0.0)
    override val elapsedSeconds: StateFlow<Double> = _elapsedSeconds

    private val _totalDurationSeconds = MutableStateFlow(0.0)
    override val totalDurationSeconds: StateFlow<Double> = _totalDurationSeconds

    private val _tracks = MutableStateFlow<List<AudioFolderTrack>>(emptyList())
    val tracks: StateFlow<List<AudioFolderTrack>> = _tracks

    private val _currentTrackIndex = MutableStateFlow(0)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex

    private val _audioBookmarks = MutableStateFlow<List<AudioBookmark>>(emptyList())
    val audioBookmarks: StateFlow<List<AudioBookmark>> = _audioBookmarks

    private val _isCurrentPositionBookmarked = MutableStateFlow(false)
    val isCurrentPositionBookmarked: StateFlow<Boolean> = _isCurrentPositionBookmarked

    private var loadedTracks: List<Track> = emptyList()
    private var elapsedTimeJob: Job? = null

    private val player: AudiobookPlayer = AudiobookPlayer(
        context = context,
        coroutineScope = coroutineScope,
        listener = object : Listener {
            override fun onClipChanged(overlayPar: OverlayPar) {
                // Not used for unsynchronized folder playback
            }

            override fun onPositionChanged(position: Double) {
                // Not used — elapsed time is tracked via polling loop
            }

            override fun onTrackChanged(track: Track, position: Double, index: Int) {
                _currentTrackIndex.value = index
                updateIsCurrentPositionBookmarked()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startElapsedTimePolling()
                } else {
                    elapsedTimeJob?.cancel()
                    elapsedTimeJob = null
                    savePosition()
                }
            }
        }
    )

    suspend fun initialize() {
        // Build track lists on IO (file scanning + metadata reading); loadTracks() must run on Main
        // because MediaController.buildAsync() requires a looper thread.
        data class TrackData(
            val folderTracks: List<AudioFolderTrack>,
            val playerTracks: List<Track>,
        )

        val trackData = withContext(Dispatchers.IO) {
            val audioFiles = detectAudioFiles(extractedDir)
            if (audioFiles.isEmpty()) {
                logger.warn { "[audiobook-folder] No audio files found in $extractedDir" }
                return@withContext null
            }
            logger.info { "[audiobook-folder] Found ${audioFiles.size} audio files" }

            val retriever = MediaMetadataRetriever()
            val folderTracks = mutableListOf<AudioFolderTrack>()
            val playerTracks = mutableListOf<Track>()

            audioFiles.forEachIndexed { index, file ->
                val durationSeconds = try {
                    retriever.setDataSource(file.absolutePath)
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    durationMs / 1000.0
                } catch (e: Exception) {
                    logger.warn { "[audiobook-folder] Failed to read duration for ${file.name}: ${e.message}" }
                    0.0
                }

                val title = cleanTrackTitle(file.nameWithoutExtension)
                val relativeUri = file.relativeTo(extractedDir).path.replace('\\', '/')
                val mimeType = mimeTypeForExtension(file.extension.lowercase())

                folderTracks.add(
                    AudioFolderTrack(
                        index = index,
                        title = title,
                        durationSeconds = durationSeconds,
                    )
                )

                playerTracks.add(
                    Track(
                        uri = file.toUri(),
                        bookUuid = bookUuid,
                        title = title,
                        duration = durationSeconds * 1000.0,
                        bookTitle = "",
                        author = null,
                        coverUri = null,
                        relativeUri = relativeUri,
                        narrator = null,
                        mimeType = mimeType,
                    )
                )
            }

            retriever.release()
            TrackData(folderTracks, playerTracks)
        } ?: return

        val (folderTracks, playerTracks) = trackData

        _tracks.value = folderTracks
        _totalDurationSeconds.value = folderTracks.sumOf { it.durationSeconds }
        loadedTracks = playerTracks

        // audioPositionRepository.getPosition() already dispatches to IO internally
        val savedPosition = audioPositionRepository.getPosition(bookId)
        val initialIndex = savedPosition?.trackIndex?.coerceIn(0, playerTracks.lastIndex) ?: 0
        val initialPositionMs = savedPosition?.positionSeconds ?: 0.0

        logger.info {
            "[audiobook-folder] Loading ${playerTracks.size} tracks, " +
            "initialIndex=$initialIndex initialPosition=${initialPositionMs}s"
        }

        // Must be called on Main — MediaController.buildAsync() requires a looper thread
        player.loadTracks(playerTracks, initialIndex, initialPositionMs)
        _currentTrackIndex.value = initialIndex
        _elapsedSeconds.value = folderTracks.take(initialIndex).sumOf { it.durationSeconds } + initialPositionMs

        coroutineScope.launch {
            audioBookmarkRepository.getBookmarks(bookId).collect { bookmarks ->
                _audioBookmarks.value = bookmarks
                updateIsCurrentPositionBookmarked()
            }
        }
    }

    override fun togglePlayPause() {
        if (_isPlaying.value) {
            player.pause()
        } else {
            player.play(automaticRewind = false)
        }
    }

    override fun seekToNext() {
        player.next()
        updateIsCurrentPositionBookmarked()
    }

    override fun seekToPrev() {
        player.prev()
        updateIsCurrentPositionBookmarked()
    }

    override fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _volume.value = clamped
        player.setVolume(clamped)
    }

    override fun applyAudioSettings(settings: Epub3NativeSettings) {
        player.setRate(settings.playbackSpeed)
        player.setAutomaticRewind(
            enabled = settings.rewindEnabled,
            afterInterruption = settings.rewindAfterInterruption,
            afterBreak = settings.rewindAfterBreak,
        )
    }

    override fun release() {
        savePosition()
        elapsedTimeJob?.cancel()
        elapsedTimeJob = null
        player.unload()
    }

    fun seekToTrack(index: Int) {
        val tracks = loadedTracks
        if (index < 0 || index >= tracks.size) return
        val track = tracks[index]
        player.seekTo(track.relativeUri, 0.0, skipEmit = false)
        updateIsCurrentPositionBookmarked()
    }

    fun seekToTrackPosition(index: Int, positionSeconds: Double) {
        val tracks = loadedTracks
        if (index < 0 || index >= tracks.size) return
        val track = tracks[index]
        player.seekTo(track.relativeUri, positionSeconds, skipEmit = false)
        // Immediately update StateFlows so the UI doesn't jump back when paused
        val prevDuration = _tracks.value.take(index).sumOf { it.durationSeconds }
        _elapsedSeconds.value = prevDuration + positionSeconds
        _currentTrackIndex.value = index
        updateIsCurrentPositionBookmarked()
    }

    override fun seekRelative(deltaSeconds: Double) {
        val targetElapsed = (_elapsedSeconds.value + deltaSeconds)
            .coerceIn(0.0, _totalDurationSeconds.value)
        var cumulative = 0.0
        for ((index, track) in _tracks.value.withIndex()) {
            val trackEnd = cumulative + track.durationSeconds
            if (trackEnd >= targetElapsed || index == _tracks.value.lastIndex) {
                seekToTrackPosition(index, targetElapsed - cumulative)
                return
            }
            cumulative = trackEnd
        }
    }

    fun toggleAudioBookmark(currentTrackTitle: String) {
        val index = _currentTrackIndex.value
        val positionSeconds = positionInCurrentTrack()
        val existingBookmark = _audioBookmarks.value.firstOrNull { bookmark ->
            bookmark.trackIndex == index && kotlin.math.abs(bookmark.positionSeconds - positionSeconds) < 5.0
        }
        if (existingBookmark != null) {
            coroutineScope.launch {
                audioBookmarkRepository.deleteBookmark(existingBookmark.id)
                onBookmarkChange()
            }
        } else {
            coroutineScope.launch {
                audioBookmarkRepository.saveBookmark(
                    AudioBookmark(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        trackIndex = index,
                        positionSeconds = positionSeconds,
                        trackTitle = currentTrackTitle,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                onBookmarkChange()
            }
        }
    }

    fun deleteAudioBookmark(id: String) {
        coroutineScope.launch {
            audioBookmarkRepository.deleteBookmark(id)
            onBookmarkChange()
        }
    }

    private fun positionInCurrentTrack(): Double {
        return player.getPosition()
    }

    private fun startElapsedTimePolling() {
        elapsedTimeJob?.cancel()
        elapsedTimeJob = coroutineScope.launch {
            while (true) {
                val index = player.getCurrentTrackIndex()
                _currentTrackIndex.value = index
                val prevDuration = _tracks.value.take(index).sumOf { it.durationSeconds }
                _elapsedSeconds.value = prevDuration + player.getPosition()
                updateIsCurrentPositionBookmarked()
                delay(500)
            }
        }
    }

    private fun savePosition() {
        val index = _currentTrackIndex.value
        val positionSeconds = player.getPosition()
        if (loadedTracks.isEmpty()) return
        coroutineScope.launch {
            audioPositionRepository.savePosition(
                AudioPosition(
                    bookId = bookId,
                    trackIndex = index,
                    positionSeconds = positionSeconds,
                    savedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun updateIsCurrentPositionBookmarked() {
        val index = _currentTrackIndex.value
        val positionSeconds = player.getPosition()
        _isCurrentPositionBookmarked.value = _audioBookmarks.value.any { bookmark ->
            bookmark.trackIndex == index && kotlin.math.abs(bookmark.positionSeconds - positionSeconds) < 5.0
        }
    }

    companion object {
        fun detectAudioFiles(extractedDir: File): List<File> {
            return extractedDir.walkTopDown()
                .filter { it.isDirectory }
                .filter { dir -> dir.name.lowercase() in AUDIO_FOLDER_NAMES }
                .flatMap { dir ->
                    dir.listFiles()
                        ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                        ?.sortedBy { it.name }
                        ?: emptyList()
                }
                .toList()
        }

        fun cleanTrackTitle(nameWithoutExtension: String): String {
            return nameWithoutExtension
                .replace(Regex("^\\d+[-_.\\s]*"), "")
                .replace(Regex("[-_]"), " ")
                .trim()
                .replaceFirstChar { it.uppercase() }
                .ifBlank { nameWithoutExtension }
        }

        private fun mimeTypeForExtension(ext: String): String {
            return when (ext) {
                "mp3" -> "audio/mpeg"
                "mp4" -> "audio/mp4"
                "m4a" -> "audio/mp4"
                "m4b" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "aac" -> "audio/aac"
                "flac" -> "audio/flac"
                "opus" -> "audio/opus"
                else -> "audio/mpeg"
            }
        }
    }
}
