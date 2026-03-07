package snd.komelia.image

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Paint.FILTER_BITMAP_FLAG
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import snd.komelia.image.AndroidBitmap.toBitmap
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.processing.ImageProcessingPipeline

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual typealias RenderImage = Bitmap

class AndroidReaderImage(
    imageDecoder: KomeliaImageDecoder,
    imageSource: ImageSource,
    processingPipeline: ImageProcessingPipeline,
    stretchImages: StateFlow<Boolean>,
    pageId: PageId,
    upsamplingMode: StateFlow<UpsamplingMode>,
    downSamplingKernel: StateFlow<ReduceKernel>,
    linearLightDownSampling: StateFlow<Boolean>,
    private val ncnnUpscaler: AndroidNcnnUpscaler?,
) : TilingReaderImage(
    imageDecoder = imageDecoder,
    imageSource = imageSource,
    processingPipeline = processingPipeline,
    stretchImages = stretchImages,
    upsamplingMode = upsamplingMode,
    downSamplingKernel = downSamplingKernel,
    linearLightDownSampling = linearLightDownSampling,
    pageId = pageId,
) {
    @Volatile
    private var cachedUpscaledImage: KomeliaImage? = null

    override val upscaleStatus = MutableStateFlow<UpscaleStatus>(UpscaleStatus.Idle)

    init {
        ncnnUpscaler?.isReady
            ?.drop(1)
            ?.filter { it }
            ?.onEach { retryUpscaleOnLoad() }
            ?.launchIn(processingScope)

        // Feature 1: React to upscaleOnLoad toggle
        ncnnUpscaler?.settingsFlow
            ?.drop(1)
            ?.map { it?.upscaleOnLoad ?: false }
            ?.distinctUntilChanged()
            ?.onEach { upscaleOnLoad ->
                if (upscaleOnLoad) {
                    retryUpscaleOnLoad()
                } else {
                    val old = cachedUpscaledImage
                    cachedUpscaledImage = null
                    upscaleStatus.value = UpscaleStatus.Idle
                    if (old != null && old !== image.value) old.close()
                    reloadLastRequest()
                }
            }
            ?.launchIn(processingScope)

        // Feature 4: React to engine/model changes - always clear old upscale result
        ncnnUpscaler?.settingsFlow
            ?.drop(1)
            ?.map { Pair(it?.engine, it?.model) }
            ?.distinctUntilChanged()
            ?.onEach { _ ->
                val upscaleOnLoad = ncnnUpscaler.settingsFlow.value?.upscaleOnLoad ?: false
                val old = cachedUpscaledImage
                cachedUpscaledImage = null
                upscaleStatus.value = UpscaleStatus.Idle
                if (old != null && old !== image.value) old.close()
                if (upscaleOnLoad) {
                    retryUpscaleOnLoad()
                } else {
                    reloadLastRequest()
                }
            }
            ?.launchIn(processingScope)
    }

    override fun closeTileBitmaps(tiles: List<ReaderImageTile>) {
        tiles.forEach { it.renderImage?.recycle() }
    }

    override fun createTilePainter(
        tiles: List<ReaderImageTile>,
        displaySize: IntSize,
        scaleFactor: Double
    ): TiledPainter {
        return AndroidTiledPainter(
            tiles = tiles,
            upsamplingMode = upsamplingMode.value,
            scaleFactor = scaleFactor,
            displaySize = displaySize
        )
    }

    override fun requestUpdate(maxDisplaySize: IntSize, zoomFactor: Float, visibleDisplaySize: IntRect) {
        AndroidNcnnUpscaler.currentPageNumber.set(pageId.pageNumber)
        super.requestUpdate(maxDisplaySize, zoomFactor, visibleDisplaySize)
    }

    override suspend fun loadImage() {
        super.loadImage()
        val currentImage = image.value ?: return
        val willUpscale = ncnnUpscaler?.willUpscale(currentImage) == true
        if (willUpscale) {
            upscaleStatus.value = UpscaleStatus.Upscaling
            AndroidNcnnUpscaler.registerActivity(pageId.pageNumber)
        }
        val upscaled = try {
            ncnnUpscaler?.checkAndUpscale(currentImage, pageId.pageNumber)
        } catch (e: Throwable) {
            if (willUpscale) AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
            upscaleStatus.value = UpscaleStatus.Idle
            throw e
        }
        currentCoroutineContext().ensureActive()
        if (upscaled != null && upscaled !== currentImage) {
            cachedUpscaledImage = upscaled
            upscaleStatus.value = UpscaleStatus.Upscaled
            if (willUpscale) AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Upscaled)
        } else {
            upscaleStatus.value = UpscaleStatus.Idle
            if (willUpscale) AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
        }
    }

    override suspend fun resizeImage(image: KomeliaImage, scaleWidth: Int, scaleHeight: Int): ReaderImageData {
        if (ncnnUpscaler != null && (scaleWidth > image.width || scaleHeight > image.pageHeight)) {
            return upscaleImage(image, scaleWidth, scaleHeight)
        }

        return image.resize(
            scaleWidth = scaleWidth,
            scaleHeight = scaleHeight,
            linear = linearLightDownSampling.value,
            kernel = downSamplingKernel.value
        ).toReaderImageData()
    }

    override suspend fun getImageRegion(
        image: KomeliaImage,
        imageRegion: IntRect,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData {
        var region: KomeliaImage? = null
        var resized: KomeliaImage? = null
        try {
            if (ncnnUpscaler != null && (scaleWidth > imageRegion.width || scaleHeight > imageRegion.height)) {
                return upscaleRegion(image, imageRegion, scaleWidth, scaleHeight)
            }

            region = image.extractArea(imageRegion.toImageRect())
            if (scaleWidth > imageRegion.width || scaleHeight > imageRegion.height) {
                val regionData = region.toReaderImageData()
                return regionData
            }
            resized = region.resize(
                scaleWidth = scaleWidth,
                scaleHeight = scaleHeight,
                linear = linearLightDownSampling.value,
                kernel = downSamplingKernel.value
            )
            return resized.toReaderImageData()
        } finally {
            if (resized != null) {
                region?.close()   // region was intermediate — safe to recycle
                // do NOT close resized — tile owns resized.bitmap
            } else {
                // resized == null means either we returned early (upscaleRegion path),
                // or region was returned directly. In the upscaleRegion path both are null.
                // In the direct-region path (no ncnnUpscaler, zoom-in case), region is
                // VipsBackedImage so closing it is safe (toBitmap() produced an independent copy).
                if (region !is AndroidBitmapBackedImage) {
                    region?.close()
                }
                // if region is AndroidBitmapBackedImage, tile owns region.bitmap — don't close
            }
        }
    }

    private suspend fun retryUpscaleOnLoad() {
        if (cachedUpscaledImage != null) return   // already upscaled, skip
        val currentImage = image.value ?: return
        upscaleStatus.value = UpscaleStatus.Upscaling
        AndroidNcnnUpscaler.registerActivity(pageId.pageNumber)
        var resultStatus: UpscaleStatus = UpscaleStatus.Idle
        val upscaled = try {
            ncnnUpscaler?.checkAndUpscale(currentImage, pageId.pageNumber)
        } catch (e: Throwable) {
            AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
            upscaleStatus.value = UpscaleStatus.Idle
            throw e
        }
        currentCoroutineContext().ensureActive()
        if (upscaled != null && upscaled !== currentImage) {
            cachedUpscaledImage = upscaled
            upscaleStatus.value = UpscaleStatus.Upscaled
            resultStatus = UpscaleStatus.Upscaled
            reloadLastRequest()
        } else {
            upscaleStatus.value = UpscaleStatus.Idle
        }
        AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, resultStatus)
    }

    private suspend fun upscaleImage(
        image: KomeliaImage,
        scaleWidth: Int,
        scaleHeight: Int,
    ): ReaderImageData {
        val upscaled = when {
            cachedUpscaledImage === image -> image          // image IS the cached upscaled version — use directly
            cachedUpscaledImage != null -> cachedUpscaledImage  // have a 2x cache for original image
            else -> {
                upscaleStatus.value = UpscaleStatus.Upscaling
                AndroidNcnnUpscaler.registerActivity(pageId.pageNumber)
                val newUpscaled = try {
                    ncnnUpscaler?.upscale(image, pageId.pageNumber)
                } catch (e: Throwable) {
                    AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
                    throw e
                }
                if (newUpscaled != null) {
                    upscaleStatus.value = UpscaleStatus.Upscaled
                    AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Upscaled)
                } else {
                    AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
                }
                cachedUpscaledImage = newUpscaled
                newUpscaled
            }
        }

        if (upscaled != null) {
            if (upscaled.width > scaleWidth && upscaled.pageHeight > scaleHeight) {
                val resized = upscaled.resize(
                    scaleWidth = scaleWidth,
                    scaleHeight = scaleHeight,
                    linear = linearLightDownSampling.value,
                    kernel = downSamplingKernel.value
                )
                // do NOT close resized — tile owns resized.bitmap
                return resized.toReaderImageData()
            } else {
                // Resize to target so tile gets a fresh independent bitmap,
                // not a shared reference to cachedUpscaledImage.bitmap
                val resized = upscaled.resize(
                    scaleWidth = scaleWidth,
                    scaleHeight = scaleHeight,
                    linear = linearLightDownSampling.value,
                    kernel = downSamplingKernel.value
                )
                // do NOT close resized — tile owns resized.bitmap
                return resized.toReaderImageData()
            }
        } else {
            return image.toReaderImageData()
        }
    }

    private suspend fun upscaleRegion(
        image: KomeliaImage,
        imageRegion: IntRect,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData {
        val upscaled = when {
            cachedUpscaledImage === image -> image          // image IS the cached upscaled version — use directly
            cachedUpscaledImage != null -> cachedUpscaledImage  // have a 2x cache for original image
            else -> {
                upscaleStatus.value = UpscaleStatus.Upscaling
                AndroidNcnnUpscaler.registerActivity(pageId.pageNumber)
                val newUpscaled = try {
                    ncnnUpscaler?.upscale(image, pageId.pageNumber)
                } catch (e: Throwable) {
                    AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
                    throw e
                }
                if (newUpscaled != null) {
                    upscaleStatus.value = UpscaleStatus.Upscaled
                    AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Upscaled)
                } else {
                    AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
                }
                cachedUpscaledImage = newUpscaled
                newUpscaled
            }
        }

        var region: KomeliaImage? = null
        var resized: KomeliaImage? = null

        try {
            if (upscaled != null) {
                // assume upscaling is done by integer fraction (2x)
                val scaleRatio = upscaled.width.toDouble() / image.width
                val targetRegion = ImageRect(
                    left = (imageRegion.left * scaleRatio).toInt(),
                    right = (imageRegion.right * scaleRatio).toInt(),
                    top = (imageRegion.top * scaleRatio).toInt(),
                    bottom = (imageRegion.bottom * scaleRatio).toInt()
                )
                region = upscaled.extractArea(targetRegion)

                // downscale if region is bigger than requested scale
                if (region.width > scaleWidth || region.pageHeight > scaleHeight) {
                    resized = region.resize(
                        scaleWidth = scaleWidth,
                        scaleHeight = scaleHeight,
                        linear = linearLightDownSampling.value,
                        kernel = downSamplingKernel.value
                    )
                    return resized.toReaderImageData()
                } else {
                    return region.toReaderImageData()
                }
            } else {
                region = image.extractArea(imageRegion.toImageRect())
                return region.toReaderImageData()
            }
        } finally {
            if (resized != null) {
                region?.close()   // region was intermediate — safe to recycle its bitmap now
                // do NOT close resized — tile owns resized.bitmap
            } else if (region !is AndroidBitmapBackedImage) {
                // region is VipsBackedImage (upscale fallback path) — toReaderImageData() created
                // an independent bitmap copy, so closing region here is safe
                region?.close()
            }
            // if region is AndroidBitmapBackedImage: tile owns region.bitmap — don't close
        }
    }

    override fun close() {
        super.close()
        cachedUpscaledImage?.let {
            if (it !== image.value) {
                it.close()
            }
        }
        cachedUpscaledImage = null
        upscaleStatus.value = UpscaleStatus.Idle
        AndroidNcnnUpscaler.unregisterActivity(pageId.pageNumber, UpscaleStatus.Idle)
    }

    private suspend fun KomeliaImage.toReaderImageData(): ReaderImageData {
        if (this.pagesLoaded == 1) {
            return ReaderImageData(width, height, listOf(this.toAndroidBitmap()), null)
        }

        val frames = mutableListOf<RenderImage>()
        val delays = pageDelays?.let { mutableListOf<Long>() }
        for (i in 0 until this.pagesLoaded) {
            val bitmap = this.extractArea(
                ImageRect(
                    left = 0,
                    right = width,
                    top = pageHeight * i,
                    bottom = pageHeight * (i + 1),
                )
            ).toAndroidBitmap()

            frames.add(bitmap)
            delays?.add(this.pageDelays?.getOrNull(i)?.toLong() ?: defaultFrameDelay)
        }
        return ReaderImageData(width, pageHeight, frames, delays)
    }

    private fun KomeliaImage.toAndroidBitmap(): Bitmap {
        return when (this) {
            is AndroidBitmapBackedImage -> this.bitmap
            else -> this.toBitmap()
        }
    }

    private fun IntRect.toImageRect() =
        ImageRect(left = left, top = top, right = right, bottom = bottom)


    private class AndroidTiledPainter(
        private val tiles: List<ReaderImageTile>,
        private val upsamplingMode: UpsamplingMode,
        private val scaleFactor: Double,
        private val displaySize: IntSize,
    ) : TiledPainter() {
        override val intrinsicSize: Size = displaySize.toSize()
        private val paintFlags = when {
            scaleFactor > 1.0 && upsamplingMode != UpsamplingMode.NEAREST -> FILTER_BITMAP_FLAG
            else -> 0
        }

        override fun DrawScope.onDraw() {
            tiles.forEach { tile ->
                if (tile.renderImage != null && !tile.renderImage.isRecycled && tile.isVisible) {
                    val bitmap: Bitmap = tile.renderImage
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        tile.displayRegion.toAndroidRectF(),
                        Paint().apply { flags = paintFlags },
                    )

//                    drawContext.canvas.drawRect(
//                        tile.displayRegion,
//                        Paint().apply {
//                            style = PaintingStyle.Stroke
//                            color = Color.Green
//                        }
//                    )
                }

            }
        }

        override fun withSamplingMode(upsamplingMode: UpsamplingMode): TiledPainter {
            return AndroidTiledPainter(
                tiles = tiles,
                upsamplingMode = upsamplingMode,
                scaleFactor = scaleFactor,
                displaySize = displaySize,
            )
        }
    }
}