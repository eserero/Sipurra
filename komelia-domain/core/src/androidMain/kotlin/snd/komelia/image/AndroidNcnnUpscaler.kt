package snd.komelia.image

import android.content.Context
import android.graphics.Bitmap
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.snd_r.komelia.infra.ncnn.NcnnSharedLibraries
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_REALCUGAN
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_REALSR
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_REAL_ESRGAN
import io.github.snd_r.komelia.infra.ncnn.NcnnUpscaler.Companion.ENGINE_WAIFU2X
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import snd.komelia.image.AndroidBitmap.toSoftwareBitmap
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.NcnnEngine
import snd.komelia.settings.model.NcnnUpscalerSettings

private val logger = KotlinLogging.logger {}

class AndroidNcnnUpscaler(
    private val context: Context,
    private val settingsRepository: ImageReaderSettingsRepository,
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var ncnn: NcnnUpscaler? = null
    @Volatile private var currentSettings: NcnnUpscalerSettings? = null
    private var gpuInstanceCreated = false
    val isReady = MutableStateFlow(false)
    val settingsFlow = MutableStateFlow<NcnnUpscalerSettings?>(null)

    companion object {
        private val jniMutex = Mutex()
        val globalUpscaleActivities: MutableStateFlow<Map<Int, UpscaleStatus>> =
            MutableStateFlow(emptyMap())

        private val generation = AtomicInteger(0)
        val currentPageNumber = AtomicInteger(-1)

        private data class UpscaleRequest(
            val generation: Int,
            val pageNumber: Int,
            val block: suspend () -> KomeliaImage?,
            val result: CompletableDeferred<KomeliaImage?> = CompletableDeferred()
        )

        private val requestChannel = Channel<UpscaleRequest>(Channel.UNLIMITED)
        private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        init {
            workerScope.launch { runWorker() }
        }

        private suspend fun runWorker() {
            val pending = ArrayDeque<UpscaleRequest>()
            while (true) {
                if (pending.isEmpty()) pending.add(requestChannel.receive())
                while (true) { pending.add(requestChannel.tryReceive().getOrNull() ?: break) }

                val current = currentPageNumber.get()
                val idx = pending.indexOfFirst { it.pageNumber == current }
                val work = if (idx >= 0) pending.removeAt(idx) else pending.removeFirst()

                if (work.generation != generation.get()) {
                    work.result.complete(null)
                    continue
                }
                val image = withContext(NonCancellable) { work.block() }
                work.result.complete(image)
            }
        }

        internal fun cancelPendingRequests() {
            generation.incrementAndGet()
        }

        internal fun registerActivity(pageNumber: Int) {
            globalUpscaleActivities.update { it + (pageNumber to UpscaleStatus.Upscaling) }
        }

        internal fun unregisterActivity(pageNumber: Int, status: UpscaleStatus) {
            globalUpscaleActivities.update { map ->
                if (status == UpscaleStatus.Upscaled) map + (pageNumber to UpscaleStatus.Upscaled)
                else map - pageNumber
            }
        }
    }

    fun initialize() {
        if (!NcnnSharedLibraries.isAvailable) {
            logger.warn { "NCNN shared libraries are not available. Upscaler will be disabled." }
            return
        }

        scope.launch {
            jniMutex.withLock {
                if (!gpuInstanceCreated) {
                    val result = NcnnUpscaler().createGpuInstance()
                    if (result == 0) {
                        gpuInstanceCreated = true
                        logger.info { "NCNN GPU instance created" }
                    } else {
                        logger.error { "Failed to create NCNN GPU instance: $result" }
                    }
                }
            }
        }

        settingsRepository.getNcnnUpscalerSettings()
            .onEach { settings ->
                try {
                    jniMutex.withLock {
                        if (settings.enabled) {
                            if (ncnn == null || shouldReinit(settings)) {
                                reinit(settings)
                            } else {
                                // upscaleOnLoad toggled while engine is the same — update settingsFlow
                                currentSettings = settings
                                settingsFlow.value = settings
                            }
                        } else {
                            ncnn?.release()
                            ncnn = null
                            currentSettings = null
                            isReady.value = false
                            settingsFlow.value = null
                        }
                    }
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to initialize NCNN upscaler" }
                }
            }.launchIn(scope)
    }

    fun willUpscale(image: KomeliaImage): Boolean {
        val settings = currentSettings ?: return false
        return settings.enabled && settings.upscaleOnLoad && image.width < settings.upscaleThreshold
    }

    suspend fun upscale(image: KomeliaImage, pageNumber: Int): KomeliaImage? {
        val request = UpscaleRequest(
            generation = generation.get(),
            pageNumber = pageNumber,
            block = block@{
                // All bitmap lifecycle owned by the block
                val bitmapIn = when (image) {
                    is AndroidBitmapBackedImage -> {
                        if (image.bitmap.config == Bitmap.Config.HARDWARE) {
                            image.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        } else {
                            image.bitmap
                        }
                    }
                    is VipsBackedImage -> image.vipsImage.toSoftwareBitmap()
                    else -> return@block null
                }
                var bitmapOut: Bitmap? = null
                try {
                    val result = jniMutex.withLock {
                        val upscaler = ncnn ?: return@withLock -1
                        val scale = parseModelScaleAndNoise(currentSettings?.model ?: "").first
                        bitmapOut = Bitmap.createBitmap(
                            bitmapIn.width * scale,
                            bitmapIn.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        upscaler.process(bitmapIn, bitmapOut!!)
                    }
                    if (result != 0) {
                        logger.error { "NCNN upscaling failed with code $result" }
                        bitmapOut?.recycle()
                        null
                    } else {
                        AndroidBitmapBackedImage(bitmapOut!!)
                    }
                } finally {
                    if (image !is AndroidBitmapBackedImage) {
                        bitmapIn.recycle()
                    } else if (bitmapIn != image.bitmap) {
                        bitmapIn.recycle()
                    }
                }
            }
        )

        return try {
            requestChannel.send(request)
            request.result.await()
        } catch (e: Throwable) {
            logger.error(e) { "Failed to upscale image" }
            null
        }
    }

    suspend fun checkAndUpscale(image: KomeliaImage, pageNumber: Int): KomeliaImage {
        val settings = currentSettings ?: return image
        if (settings.enabled && settings.upscaleOnLoad && image.width < settings.upscaleThreshold) {
            logger.info { "[NCNN] Pre-emptive upscale triggered: ${image.width}px < ${settings.upscaleThreshold}px" }
            return upscale(image, pageNumber) ?: image
        }
        return image
    }

    private fun shouldReinit(newSettings: NcnnUpscalerSettings): Boolean {
        val current = currentSettings ?: return true
        return current.engine != newSettings.engine ||
                current.model != newSettings.model ||
                current.gpuId != newSettings.gpuId ||
                current.ttaMode != newSettings.ttaMode ||
                current.numThreads != newSettings.numThreads
    }

    private fun parseModelScaleAndNoise(modelPath: String): Pair<Int, Int> {
        val modelName = modelPath.substringAfterLast("/")
        return when {
            modelName == "scale2.0x_model" -> Pair(2, -1)
            modelName.startsWith("noise") && modelName.contains("scale2.0x") -> {
                val noise = modelName.removePrefix("noise").substringBefore("_").toIntOrNull() ?: 0
                Pair(2, noise)
            }
            modelName.startsWith("noise") -> {
                val noise = modelName.removePrefix("noise").substringBefore("_").toIntOrNull() ?: 0
                Pair(1, noise)
            }
            modelName.contains("up2x") -> Pair(2, 0)
            modelName.contains("realsr") -> Pair(4, -1)
            modelName.contains("x2") -> Pair(2, -1)
            modelName.contains("x4") -> Pair(4, -1)
            else -> Pair(2, -1)
        }
    }

    private fun reinit(settings: NcnnUpscalerSettings) {
        ncnn?.release()
        val newNcnn = NcnnUpscaler()
        val engineType = when (settings.engine) {
            NcnnEngine.WAIFU2X -> ENGINE_WAIFU2X
            NcnnEngine.REALCUGAN -> ENGINE_REALCUGAN
            NcnnEngine.REALSR -> ENGINE_REALSR
            NcnnEngine.REAL_ESRGAN -> ENGINE_REAL_ESRGAN
        }
        newNcnn.init(engineType, settings.gpuId, settings.ttaMode, settings.numThreads)

        val (scale, noise) = parseModelScaleAndNoise(settings.model)
        newNcnn.setScale(scale)
        newNcnn.setNoise(noise)

        val modelPath = settings.model
        val paramPath: String
        val binPath: String

        if (settings.engine == NcnnEngine.REALSR || settings.engine == NcnnEngine.REAL_ESRGAN) {
            val scale = parseModelScaleAndNoise(modelPath).first
            paramPath = "$modelPath/x$scale.param"
            binPath = "$modelPath/x$scale.bin"
        } else {
            paramPath = "$modelPath.param"
            binPath = "$modelPath.bin"
        }

        val loadResult = newNcnn.load(context.assets, paramPath, binPath)
        if (loadResult != 0) {
            logger.error { "Failed to load NCNN model $modelPath: $loadResult" }
            newNcnn.release()
            ncnn = null
            currentSettings = null
            settingsFlow.value = null
        } else {
            ncnn = newNcnn
            currentSettings = settings
            settingsFlow.value = settings
            isReady.value = true
        }
    }

    override fun close() {
        scope.cancel()
        AndroidNcnnUpscaler.cancelPendingRequests()
        runBlocking {
            jniMutex.withLock {
                ncnn?.release()
                ncnn = null
            }
        }
        if (gpuInstanceCreated) {
            NcnnUpscaler().destroyGpuInstance()
            gpuInstanceCreated = false
        }
    }
}
