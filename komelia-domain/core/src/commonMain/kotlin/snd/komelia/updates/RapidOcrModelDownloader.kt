package snd.komelia.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface RapidOcrModelDownloader {
    val downloadCompletionEvents: SharedFlow<CompletionEvent>
    fun download(url: String): Flow<UpdateProgress>
    fun isDownloaded(): Boolean

    sealed interface CompletionEvent {
        data object RapidOcrModelsDownloaded : CompletionEvent
    }
}
