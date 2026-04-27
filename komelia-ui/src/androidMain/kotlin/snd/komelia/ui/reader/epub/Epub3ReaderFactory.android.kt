package snd.komelia.ui.reader.epub

import coil3.PlatformContext
import kotlinx.coroutines.CoroutineScope
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.ManagedKomgaEvents
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.sync.ReaderSyncService
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId

actual fun createEpub3ReaderState(
    bookId: KomgaBookId,
    book: KomeliaBook?,
    platformContext: PlatformContext,
    markReadProgress: Boolean,
    bookApi: KomgaBookApi,
    seriesApi: KomgaSeriesApi,
    readListApi: KomgaReadListApi,
    settingsRepository: CommonSettingsRepository,
    epubSettingsRepository: EpubReaderSettingsRepository,
    epubBookmarkRepository: snd.komelia.bookmarks.EpubBookmarkRepository,
    audioPositionRepository: snd.komelia.audiobook.AudioPositionRepository,
    audioBookmarkRepository: snd.komelia.audiobook.AudioBookmarkRepository,
    audioChapterRepository: snd.komelia.audiobook.AudioChapterRepository,
    bookAnnotationRepository: snd.komelia.annotations.BookAnnotationRepository,
    readerSyncService: ReaderSyncService,
    komgaEvents: ManagedKomgaEvents,
    fontsRepository: UserFontsRepository,
    notifications: AppNotifications,
    windowState: AppWindowState,
    platformType: PlatformType,
    coroutineScope: CoroutineScope,
    bookSiblingsContext: BookSiblingsContext,
    transcriptionSettingsRepository: snd.komelia.settings.TranscriptionSettingsRepository,
    whisperModelDownloader: snd.komelia.updates.WhisperModelDownloader?,
    onExit: (KomeliaBook) -> Unit,
): EpubReaderState = Epub3ReaderState(
    bookId = bookId,
    book = book,
    context = platformContext,
    bookApi = bookApi,
    epubSettingsRepository = epubSettingsRepository,
    epubBookmarkRepository = epubBookmarkRepository,
    audioPositionRepository = audioPositionRepository,
    audioBookmarkRepository = audioBookmarkRepository,
    audioChapterRepository = audioChapterRepository,
    bookAnnotationRepository = bookAnnotationRepository,
    readerSyncService = readerSyncService,
    komgaEvents = komgaEvents,
    settingsRepository = settingsRepository,
    fontsRepository = fontsRepository,
    notifications = notifications,
    markReadProgress = markReadProgress,
    windowState = windowState,
    platformType = platformType,
    coroutineScope = coroutineScope,
    bookSiblingsContext = bookSiblingsContext,
    transcriptionSettingsRepository = transcriptionSettingsRepository,
    whisperModelDownloader = whisperModelDownloader,
    onExit = onExit,
)
