package snd.komelia.ui.reader.image.panels

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.github.reactivecircus.cache4k.CacheEvent.Evicted
import io.github.reactivecircus.cache4k.CacheEvent.Expired
import io.github.reactivecircus.cache4k.CacheEvent.Removed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.image.BookImageLoader
import snd.komelia.image.ImageRect
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.ReaderImageResult
import snd.komelia.onnxruntime.OnnxRuntimeException
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import snd.komelia.settings.model.PanelsFullPageDisplayMode
import snd.komelia.ui.reader.image.BookState
import snd.komelia.ui.reader.image.PageMetadata
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookEnd
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookStart
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.common.KomgaReadingDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger { }

class PanelsReaderState(
    private val cleanupScope: CoroutineScope,
    private val settingsRepository: ImageReaderSettingsRepository,
    private val appNotifications: AppNotifications,
    private val readerState: ReaderState,
    private val imageLoader: BookImageLoader,
    private val appStrings: Flow<AppStrings>,
    private val pageChangeFlow: MutableSharedFlow<Unit>,
    private val onnxRuntimeRfDetr: KomeliaPanelDetector,
    val screenScaleState: ScreenScaleState,
) {
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val imageCache = Cache.Builder<PageId, Deferred<PanelsPage>>()
        .maximumCacheSize(10)
        .eventListener {
            val value = when (it) {
                is Evicted -> it.value
                is Expired -> it.value
                is Removed -> it.value
                else -> null
            } ?: return@eventListener

            cleanupScope.launch {
                if (value.isCancelled) return@launch
                value.await().imageResult?.image?.close()
            }
        }
        .build()

    val pageMetadata: MutableStateFlow<List<PageMetadata>> = MutableStateFlow(emptyList())

    val currentPageIndex = MutableStateFlow(PageIndex(0, 0))
    val currentPage: MutableStateFlow<PanelsPage?> = MutableStateFlow(null)
    val transitionPage: MutableStateFlow<TransitionPage?> = MutableStateFlow(null)
    val readingDirection = MutableStateFlow(LEFT_TO_RIGHT)

    val fullPageDisplayMode = MutableStateFlow(PanelsFullPageDisplayMode.NONE)
    val tapToZoom = MutableStateFlow(true)

    suspend fun initialize() {
        readingDirection.value = when (readerState.series.value?.metadata?.readingDirection) {
            KomgaReadingDirection.LEFT_TO_RIGHT -> LEFT_TO_RIGHT
            KomgaReadingDirection.RIGHT_TO_LEFT -> RIGHT_TO_LEFT
            else -> settingsRepository.getPagedReaderReadingDirection().first()
        }
        fullPageDisplayMode.value = settingsRepository.getPanelsFullPageDisplayMode().first()
        tapToZoom.value = settingsRepository.getPanelReaderTapToZoom().first()

        screenScaleState.setScrollState(null)
        screenScaleState.setScrollOrientation(Orientation.Vertical, false)

        combine(
            screenScaleState.transformation,
            screenScaleState.areaSize,
        ) {}
            .drop(1).conflate()
            .onEach {
                currentPage.value?.let { page ->
                    updateImageState(page, screenScaleState, currentPageIndex.value.panel)
                    delay(100)
                }
            }
            .launchIn(stateScope)

        readingDirection.drop(1).onEach { 
            // Simple page reload to ensure correct panel sequence for new direction
            launchPageLoad(currentPageIndex.value.page)
        }.launchIn(stateScope)

        readerState.booksState
            .filterNotNull()
            .onEach { newBook -> onNewBookLoaded(newBook) }
            .launchIn(stateScope)

        val strings = appStrings.first().pagedReader
        appNotifications.add(AppNotification.Normal("Panels ${strings.forReadingDirection(readingDirection.value)}"))
    }

    fun stop() {
        stateScope.coroutineContext.cancelChildren()
        screenScaleState.enableOverscrollArea(false)
        imageCache.invalidateAll()
    }

    private suspend fun updateImageState(
        page: PanelsPage,
        screenScaleState: ScreenScaleState,
        panelIdx: Int
    ) {
        val maxPageSize = screenScaleState.areaSize.value
        val zoomFactor = screenScaleState.transformation.value.scale
        val offset = screenScaleState.transformation.value.offset
        val areaSize = screenScaleState.areaSize.value.toSize()
        val stretchToFit = readerState.imageStretchToFit.value


        if (page.imageResult is ReaderImageResult.Success) {
            val image = page.imageResult.image
            val imageDisplaySize = image.calculateSizeForArea(maxPageSize, stretchToFit) ?: return
            screenScaleState.setTargetSize(imageDisplaySize.toSize())

            val visibleHeight = (imageDisplaySize.height * zoomFactor - areaSize.height) / 2
            val visibleWidth = (imageDisplaySize.width * zoomFactor - areaSize.width) / 2

            val top = ((visibleHeight - offset.y) / zoomFactor).roundToInt()
                .coerceIn(0..imageDisplaySize.height)
            val left = ((visibleWidth - offset.x) / zoomFactor).roundToInt()
                .coerceIn(0..imageDisplaySize.width)

            val visibleArea = IntRect(
                top = top,
                left = left,
                bottom = (top + areaSize.height / zoomFactor)
                    .roundToInt()
                    .coerceAtMost(imageDisplaySize.height),
                right = (left + (areaSize.width) / zoomFactor)
                    .roundToInt()
                    .coerceAtMost(imageDisplaySize.width),
            )

            image.requestUpdate(
                visibleDisplaySize = visibleArea,
                zoomFactor = zoomFactor,
                maxDisplaySize = maxPageSize
            )
        }
    }

    private fun onNewBookLoaded(bookState: BookState) {
        val newPages = bookState.currentBookPages
        val newPageIndex = readerState.readProgressPage.value - 1

        pageMetadata.value = bookState.currentBookPages
        currentPage.value = PanelsPage(
            metadata = newPages[newPageIndex],
            imageResult = null,
            panelData = null
        )
        currentPageIndex.value = PageIndex(newPageIndex, 0)

        launchPageLoad(newPageIndex)
    }

    fun onReadingDirectionChange(readingDirection: PagedReadingDirection) {
        this.readingDirection.value = readingDirection
        stateScope.launch { settingsRepository.putPagedReaderReadingDirection(readingDirection) }
    }

    fun onFullPageDisplayModeChange(mode: PanelsFullPageDisplayMode) {
        this.fullPageDisplayMode.value = mode
        stateScope.launch { settingsRepository.putPanelsFullPageDisplayMode(mode) }
        launchPageLoad(currentPageIndex.value.page)
    }

    fun onTapToZoomChange(enabled: Boolean) {
        this.tapToZoom.value = enabled
        stateScope.launch { settingsRepository.putPanelReaderTapToZoom(enabled) }
    }

    fun nextPanel() {
        val pageIndex = currentPageIndex.value
        val currentPage = currentPage.value
        if (currentPage == null || currentPage.panelData == null) {
            nextPage()
            return
        }
        val panels = currentPage.panelData.panels
        val panelIndex = pageIndex.panel

        if (panelIndex + 1 < panels.size) {
            val nextPanel = panels[panelIndex + 1]
            val areaSize = screenScaleState.areaSize.value
            val targetSize = screenScaleState.targetSize.value.toIntSize()
            val imageSize = currentPage.panelData.originalImageSize
            scrollToPanel(
                imageSize = imageSize,
                screenSize = areaSize,
                targetSize = targetSize,
                panel = nextPanel
            )
            currentPageIndex.update { it.copy(panel = panelIndex + 1) }
        } else {
            nextPage()
        }
    }

    private fun nextPage() {
        val pageIdx = currentPageIndex.value.page
        val currentTransitionPage = transitionPage.value
        when {
            pageIdx < pageMetadata.value.size - 1 -> {
                if (currentTransitionPage != null) this.transitionPage.value = null
                else onPageChange(pageIdx + 1)
            }

            currentTransitionPage == null -> {
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookEnd(
                    currentBook = bookState.currentBook,
                    nextBook = bookState.nextBook
                )
            }

            currentTransitionPage is BookEnd && currentTransitionPage.nextBook != null -> {
                stateScope.launch {
                    currentPage.value = null
                    transitionPage.value = null
                    readerState.loadNextBook()
                }
            }
        }
    }

    fun previousPanel() {
        val pageIndex = currentPageIndex.value
        val currentPage = currentPage.value
        if (currentPage == null || currentPage.panelData == null) {
            previousPage()
            return
        }
        val panels = currentPage.panelData.panels
        val panelIndex = pageIndex.panel

        if (panelIndex - 1 >= 0) {
            val prevPanel = panels[panelIndex - 1]
            val areaSize = screenScaleState.areaSize.value
            val targetSize = screenScaleState.targetSize.value.toIntSize()
            val imageSize = currentPage.panelData.originalImageSize
            scrollToPanel(
                imageSize = imageSize,
                screenSize = areaSize,
                targetSize = targetSize,
                panel = prevPanel
            )
            currentPageIndex.update { it.copy(panel = panelIndex - 1) }
        } else {
            previousPage()
        }
    }

    private fun previousPage() {
        val pageIdx = currentPageIndex.value.page
        val currentTransitionPage = transitionPage.value
        when {
            pageIdx != 0 -> {
                if (currentTransitionPage != null) this.transitionPage.value = null
                else onPageChange(pageIdx - 1, startAtLast = true)
            }

            currentTransitionPage == null -> {
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookStart(
                    currentBook = bookState.currentBook,
                    previousBook = bookState.previousBook
                )
            }

            currentTransitionPage is BookStart && currentTransitionPage.previousBook != null -> {
                stateScope.launch {
                    currentPage.value = null
                    transitionPage.value = null
                    readerState.loadPreviousBook()
                }
            }
        }
    }

    fun onPageChange(page: Int, startAtLast: Boolean = false) {
        if (currentPageIndex.value.page == page) return
        pageChangeFlow.tryEmit(Unit)
        launchPageLoad(page, startAtLast)
    }

    private fun launchPageLoad(pageIndex: Int, startAtLast: Boolean = false) {
        if (pageIndex != currentPageIndex.value.page) {
            val pageNumber = pageIndex + 1
            stateScope.launch { readerState.onProgressChange(pageNumber) }
        }

        pageLoadScope.coroutineContext.cancelChildren()
        pageLoadScope.launch { doPageLoad(pageIndex, startAtLast) }
    }

    private suspend fun doPageLoad(pageIndex: Int, startAtLast: Boolean = false) {
        val pageMeta = pageMetadata.value[pageIndex]
        val downloadJob = launchDownload(pageMeta)
        preloadImagesBetween(pageIndex)

        if (downloadJob.isActive) {
            currentPage.value = PanelsPage(
                metadata = pageMeta,
                imageResult = null,
                panelData = null
            )
            currentPageIndex.update { PageIndex(pageIndex, 0) }
            transitionPage.value = null
            screenScaleState.enableOverscrollArea(false)
            screenScaleState.setZoom(0f, updateBase = true)
        }

        val page = downloadJob.await()
        val sortedPanels = if (page.panelData != null) {
            sortPanels(
                page.panelData.panels,
                page.panelData.originalImageSize,
                readingDirection.value
            )
        } else emptyList()

        val finalPanels = mutableListOf<ImageRect>()
        if (page.panelData != null) {
            val imageSize = page.panelData.originalImageSize
            val fullPageRect = ImageRect(0, 0, imageSize.width, imageSize.height)
            
            // Avoid duplicate view if the AI already detected a full-page panel
            val alreadyHasFullPage = sortedPanels.any { it.width >= imageSize.width * 0.95f && it.height >= imageSize.height * 0.95f }

            val mode = fullPageDisplayMode.value
            val showFirst = mode == PanelsFullPageDisplayMode.BEFORE || mode == PanelsFullPageDisplayMode.BOTH
            val showLast = mode == PanelsFullPageDisplayMode.AFTER || mode == PanelsFullPageDisplayMode.BOTH

            if (sortedPanels.isEmpty()) {
                finalPanels.add(fullPageRect)
            } else if (alreadyHasFullPage && sortedPanels.size == 1) {
                // If it's a splash page (1 large panel), just show it once.
                finalPanels.addAll(sortedPanels)
            } else {
                if (showFirst && !alreadyHasFullPage) finalPanels.add(fullPageRect)
                finalPanels.addAll(sortedPanels)
                if (showLast && !alreadyHasFullPage) finalPanels.add(fullPageRect)
            }
        }

        val pageWithInjectedPanels = if (page.panelData != null) {
            page.copy(panelData = page.panelData.copy(panels = finalPanels))
        } else page

        val containerSize = screenScaleState.areaSize.value
        val initialPanelIdx = if (startAtLast) (finalPanels.size - 1).coerceAtLeast(0) else 0
        val scale = getScaleFor(pageWithInjectedPanels, containerSize, initialPanelIdx)
        
        updateImageState(pageWithInjectedPanels, scale, initialPanelIdx)
        currentPageIndex.update { PageIndex(pageIndex, initialPanelIdx) }
        transitionPage.value = null
        currentPage.value = pageWithInjectedPanels
        screenScaleState.enableOverscrollArea(true)
        screenScaleState.apply(scale)
    }

    private fun preloadImagesBetween(pageIndex: Int) {
        val previousPage = (pageIndex - 1).coerceAtLeast(0)
        val nextPage = (pageIndex + 1).coerceAtMost(pageMetadata.value.size - 1)
        val loadRange = (previousPage..nextPage).filter { it != pageIndex }

        for (index in loadRange) {
            val imageJob = launchDownload(pageMetadata.value[index])
            pageLoadScope.launch {
                val image = imageJob.await()
                val scale = getScaleFor(image, screenScaleState.areaSize.value, 0)
                updateImageState(image, scale, 0)
            }
        }
    }

    private fun launchDownload(meta: PageMetadata): Deferred<PanelsPage> {
        val pageId = meta.toPageId()
        val cached = imageCache.get(pageId)
        if (cached != null && !cached.isCancelled) return cached

        val loadJob: Deferred<PanelsPage> = pageLoadScope.async {
            val imageResult = imageLoader.loadReaderImage(meta.bookId, meta.pageNumber)
            val image = imageResult.image ?: return@async PanelsPage(
                metadata = meta,
                imageResult = imageResult,
                panelData = null
            )

            val originalImage = image.getOriginalImage().getOrNull()
                ?: return@async PanelsPage(
                    metadata = meta,
                    imageResult = imageResult,
                    panelData = null
                )

            val imageSize = IntSize(originalImage.width, originalImage.height)
            val (panels, duration) = measureTimedValue {
                try {
                    onnxRuntimeRfDetr.detect(originalImage).map { it.boundingBox }
                } catch (e: OnnxRuntimeException) {
                    return@async PanelsPage(
                        metadata = meta,
                        imageResult = ReaderImageResult.Error(e),
                        panelData = null
                    )
                }
            }
            logger.info { "page ${meta.pageNumber} panel detection completed in $duration" }

            val panelData = PanelData(
                panels = panels,
                originalImageSize = imageSize,
                panelCoversMajorityOfImage = false // Placeholder for Phase 2
            )

            return@async PanelsPage(
                metadata = meta,
                imageResult = imageResult,
                panelData = panelData
            )
        }
        imageCache.put(pageId, loadJob)
        return loadJob
    }

    private suspend fun getScaleFor(
        page: PanelsPage,
        containerSize: IntSize,
        panelIdx: Int
    ): ScreenScaleState {
        val defaultScale = ScreenScaleState()
        defaultScale.setAreaSize(containerSize)
        defaultScale.setZoom(0f, updateBase = true)
        val image = page.imageResult?.image ?: return defaultScale

        val scaleState = ScreenScaleState()
        val fitToScreenSize = image.calculateSizeForArea(containerSize, true) ?: return defaultScale
        scaleState.setAreaSize(containerSize)
        scaleState.setTargetSize(fitToScreenSize.toSize())
        scaleState.enableOverscrollArea(true)

        val panels = page.panelData?.panels
        if (panels.isNullOrEmpty()) {
            scaleState.setZoom(0f, updateBase = true)
        } else {
            val targetPanel = panels.getOrNull(panelIdx) ?: panels.first()
            val imageSize = image.getOriginalImageSize().getOrNull() ?: return defaultScale
            val (offset, zoom) = getPanelOffsetAndZoom(
                imageSize = imageSize,
                areaSize = containerSize,
                targetSize = fitToScreenSize,
                panel = targetPanel
            )
            scaleState.setZoom(zoom, updateBase = true)
            scaleState.setOffset(offset)
        }

        return scaleState
    }

    private fun scrollToFit() {
        screenScaleState.animateTo(Offset(0f, 0f), 0f)
    }

    private fun scrollToPanel(
        imageSize: IntSize,
        screenSize: IntSize,
        targetSize: IntSize,
        panel: ImageRect,
        skipAnimation: Boolean = false,
    ) {
        val (offset, zoom) = getPanelOffsetAndZoom(
            imageSize = imageSize,
            areaSize = screenSize,
            targetSize = targetSize,
            panel = panel
        )
        if (skipAnimation) {
            screenScaleState.setZoom(zoom, updateBase = true)
            screenScaleState.setOffset(offset)
        } else {
            screenScaleState.animateTo(offset, zoom)
        }
    }

    private fun getPanelOffsetAndZoom(
        imageSize: IntSize,
        areaSize: IntSize,
        targetSize: IntSize,
        panel: ImageRect,
    ): Pair<Offset, Float> {
        val xScale: Float = targetSize.width.toFloat() / imageSize.width
        val yScale: Float = targetSize.height.toFloat() / imageSize.height

        val bboxLeft: Float = panel.left.coerceAtLeast(0) * xScale
        val bboxRight: Float = panel.right.coerceAtMost(imageSize.width) * xScale
        val bboxBottom: Float = panel.bottom.coerceAtMost(imageSize.height) * yScale
        val bboxTop: Float = panel.top.coerceAtLeast(0) * yScale
        val bboxWidth: Float = bboxRight - bboxLeft
        val bboxHeight: Float = bboxBottom - bboxTop

        val scale: Float = min(
            areaSize.width / bboxWidth,
            areaSize.height / bboxHeight
        )
        val fitToScreenScale = max(
            areaSize.width.toFloat() / targetSize.width,
            areaSize.height.toFloat() / targetSize.height
        )
        val zoom: Float = scale / fitToScreenScale

        val bboxHalfWidth: Float = bboxWidth / 2.0f
        val bboxHalfHeight: Float = bboxHeight / 2.0f
        val imageHalfWidth: Float = targetSize.width / 2.0f
        val imageHalfHeight: Float = targetSize.height / 2.0f

        val centerX: Float = (bboxLeft - imageHalfWidth) * -1.0f
        val centerY: Float = (bboxTop - imageHalfHeight) * -1.0f
        val offset = Offset(
            (centerX - bboxHalfWidth) * scale,
            (centerY - bboxHalfHeight) * scale
        )

        return offset to zoom
    }

    data class PanelsPage(
        val metadata: PageMetadata,
        val imageResult: ReaderImageResult?,
        val panelData: PanelData?,
    )

    data class PanelData(
        val panels: List<ImageRect>,
        val originalImageSize: IntSize,
        val panelCoversMajorityOfImage: Boolean,
    )

    data class PageIndex(
        val page: Int,
        val panel: Int,
    )

}
