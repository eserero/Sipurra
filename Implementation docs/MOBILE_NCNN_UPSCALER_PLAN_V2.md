# Mobile NCNN Upscaler Plan (JNI-First)

## Overview
Integrate high-performance, in-memory GPU upscaling into Komelia using `ncnn-android-waifu2x-demo` as the architectural foundation. This approach prioritizes JNI and `AndroidBitmap` for maximum performance, avoiding the slow shell-out and disk I/O of previous methods.

---

## Phase 0: Build the Baseline
Prove the JNI-based Vulkan pipeline works on a real device before integration.

### Steps:
1. **Clone the Project:** Clone `ArchieMeng/ncnn-android-waifu2x-demo` into `third_party/`.
2. **Dependency Setup:**
   - Download `ncnn-android-vulkan-shared.zip` from [ncnn releases](https://github.com/Tencent/ncnn/releases).
   - Extract and place headers/libs in `app/src/main/jni/ncnn-android-vulkan-shared/`.
3. **Build APK:** Run `./gradlew assembleDebug`.
4. **Verification:** Install on a physical Android device and verify that upscaling an image works via Vulkan.

---

## Phase 0.1: Multi-Engine Demo (Waifu2x + RealCUGAN)
Extend the demo app to support multiple engines and models via a UI dropdown.

### Steps:
1. **Source Integration:**
   - Create `app/src/main/jni/realcugan/`.
   - Copy `realcugan.cpp/h` and all `.comp.hex.h` headers from `/home/eyal/RealSR-NCNN-Android/RealSR-NCNN-Android-CLI/RealCUGAN/src/main/jni/`.
2. **JNI Expansion:**
   - Create a unified JNI wrapper or extend `waifu2x_jni.cpp` to handle a `modelType` parameter (0 for Waifu2x, 1 for RealCUGAN).
   - Implement `RealCUGAN` lifecycle in JNI (init, load, process, destroy).
3. **Model Assets:**
   - Copy `up2x-conservative.param/bin` from `/home/eyal/RealSR-NCNN-Android/Assets/realsr/models-se/` to `app/src/main/assets/models-realcugan/`.
4. **UI Update:**
   - Add a `Spinner` to `res/layout/main.xml` for selecting between "Waifu2x (Anime)" and "RealCUGAN (Conservative)".
   - Update `MainActivity.kt` to pass the selected model type to the JNI layer.

### Phase 0.1 Completion Summary (March 5, 2026)
**Status: COMPLETED**
- **JNI Integration:** Integrated RealCUGAN source into `jni/realcugan/`. Modified `RealCUGAN` class to support `AAssetManager` loading.
- **Unified JNI:** Extended `waifu2x_jni.cpp` to handle both engines via `engineType` parameter. Added `RawDestroy` for memory management.
- **Model Assets:** Added `up2x-conservative` models for RealCUGAN to the Android assets.
- **UI & Kotlin API:** Updated `Processor` and `Waifu2x.kt` to handle multi-engine initialization and model path resolution. Added a selection `Spinner` to the demo app UI.
- **Build:** Verified successful APK generation via `./gradlew assembleDebug`.

---

## Phase 1: Library-ify
Transform the demo app into a reusable Android Library module `:ncnn-upscaler`.

### Steps:
1. **Module Creation:** Create a new Android Library module named `:ncnn-upscaler` under `komelia-infra/`.
2. **Refactor JNI:**
   - Move all engine code and `CMakeLists.txt` to the new module.
   - Refactor JNI to provide a generic interface for loading models from arbitrary filesystem paths.
3. **Kotlin API Wrapper:**
   - Create a `NcnnUpscaler` class.
   - Expose methods: `setEngine(type)`, `loadModel(path)`, `upscale(bitmap)`, and `release()`.

---

### Phase 1 Completion Summary (March 5, 2026)
**Status: COMPLETED**
- **Module Creation:** Created a new Android Library module `:komelia-infra:ncnn-upscaler`.
- **JNI Refactoring:**
    - Unified JNI logic into `upscaler_jni.cpp` under the `io.github.snd_r.komelia.infra.ncnn` package.
    - Integrated both Waifu2x and RealCUGAN engines into a single shared library (`libncnn-upscaler.so`).
    - Added support for loading models from both Android assets (via `AAssetManager`) and arbitrary filesystem paths (via `fopen`).
    - Implemented `AndroidBitmap` locking/unlocking for zero-copy pixel access.
- **Kotlin API:**
    - Created `NcnnUpscaler` class with a clean, lifecycle-aware API.
    - Methods: `init(engineType, gpuId, ttaMode, numThreads)`, `load(assetManager, paramPath, modelPath)`, `loadModel(paramPath, modelPath)`, `process(bitmapIn, bitmapOut)`, `setScale`, `setNoise`, `setTileSize`, and `release`.
- **Build System:**
    - Configured `CMakeLists.txt` to handle SPIR-V shader compilation for Waifu2x.
    - Linked with prebuilt NCNN Vulkan libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
    - Integrated into the main Gradle build via `settings.gradle.kts`.

#### How to Compile
To build the library AAR, run the following command from the project root:
```bash
./gradlew :komelia-infra:ncnn-upscaler:assembleDebug
```
The resulting AAR will be located at:
`komelia-infra/ncnn-upscaler/build/outputs/aar/ncnn-upscaler-debug.aar`

#### How to Use
1. **Initialize the engine:**
```kotlin
val upscaler = NcnnUpscaler()
upscaler.init(NcnnUpscaler.ENGINE_WAIFU2X, gpuId = 0, ttaMode = false, numThreads = 4)
```
2. **Load a model:**
   - From Assets: `upscaler.load(context.assets, "models/model.param", "models/model.bin")`
   - From Filesystem: `upscaler.loadModel("/path/to/model.param", "/path/to/model.bin")`
3. **Upscale a Bitmap:**
```kotlin
val bitmapOut = Bitmap.createBitmap(bitmapIn.width * 2, bitmapIn.height * 2, bitmapIn.config)
upscaler.process(bitmapIn, bitmapOut)
```
4. **Clean up:**
```kotlin
upscaler.release()
```

#### How to Add More Models
To add new upscaling models to the library, follow these steps:

1.  **Prepare Model Files:** Ensure you have both the `.param` (network structure) and `.bin` (trained weights) files for the NCNN engine.
2.  **Add to Assets:** Place your model files into a subdirectory under `komelia-infra/ncnn-upscaler/src/main/assets/`.
    - Example: `komelia-infra/ncnn-upscaler/src/main/assets/models-my-new-model/`
3.  **Loading the Model:**
    - Use the `load` method with the path relative to the `assets` folder:
    ```kotlin
    upscaler.load(context.assets, "models-my-new-model/my_model.param", "models-my-new-model/my_model.bin")
    ```
4.  **JNI Prepadding Logic (Important):**
    - If the new model requires specific `prepadding` (to avoid tile border artifacts), you must update the logic in `komelia-infra/ncnn-upscaler/src/main/jni/upscaler_jni.cpp` inside the `Java_io_github_snd_1r_komelia_infra_ncnn_NcnnUpscaler_load` function.
    - The JNI layer currently detects `models-cunet` and `models-upconv_7` to set prepadding automatically. You can add a similar check for your new model directory.

---

## Phase 2: Komelia Integration & UI
Wire the library into Komelia's image pipeline.

### Steps:
1. **Pipeline Hook:** Integrate into `AndroidReaderImageFactory`.
2. **Settings UI:** Add "Upscaler" section in Android settings with Engine and Model selection.
3. **Lifecycle Management:** Bind to Reader's view model.

### Phase 2 Completion Summary (March 5, 2026)
**Status: COMPLETED (Integration Done, Stability Issues Pending)**

#### 1. Implementation Details
Integrated the NCNN upscaler into the Android image pipeline and added the necessary UI/Settings.

*   **Native Layer (JNI):**
    *   Updated `upscaler_jni.cpp` to use `ncnn::VulkanDevice` for hardware budget queries.
    *   Added explicit `createGpuInstance()` and `destroyGpuInstance()` methods to manage Vulkan lifecycle outside of `JNI_OnLoad`.
    *   Implemented safety checks in JNI to prevent SIGSEGV when GPU devices are missing or engine pointers are null.
*   **Infrastructure & Data:**
    *   **Database:** Added `NcnnUpscalerSettings` to `ImageReaderSettingsTable` with a SQLite migration (`V21`).
    *   **Shared Libraries:** Created `NcnnSharedLibraries` to safely load `libncnn.so` followed by `libncnn-upscaler.so`.
    *   **Models:** Included `models-cunet` and `models-realcugan` in the assets.
*   **Image Pipeline:**
    *   **AndroidNcnnUpscaler:** A Kotlin wrapper that manages the native upscaler lifecycle, handles model loading from assets, and performs the actual upscaling between Bitmaps.
    *   **AndroidBitmapBackedImage:** A new `KomeliaImage` implementation that wraps a standard Android `Bitmap`, allowing the upscaled result to flow back into the viewer.
    *   **Pipeline Hook:** Updated `AndroidReaderImage` to detect when upscaling is required (display size > original size) and route through NCNN.
*   **UI & Settings:**
    *   Added a new "NCNN Upscaler" section to Image Reader settings (Android only).
    *   Integrated settings into both the global settings and the reader's bottom sheet.
    *   Configured engine selection (Waifu2x/RealCUGAN), model selection, and TTA mode.

#### 2. Current Problem: Immediate Crash
Despite the implementation, enabling the upscaler causes an immediate application crash with no caught exception. The app enters a "boot loop" state where it crashes on launch because it attempts to re-initialize the upscaler from the saved settings.

**Symptoms:**
*   Crash happens exactly when `enabled = true` is saved/processed.
*   No standard Kotlin/Java stack trace is visible in some logs, suggesting a native SIGSEGV or a fatal signal during Vulkan initialization.
*   Clearing app data is required to reset the setting and allow the app to start.

#### 3. Attempted Resolutions
Several strategies have been tried to stabilize the native initialization:

1.  **Lazy Vulkan Init:** Moved `create_gpu_instance()` from `JNI_OnLoad` to a manual call triggered only when the upscaler is first enabled.
2.  **Loading Order:** Explicitly loading the base `ncnn` library before the upscaler wrapper.
3.  **Safety Guards:** Added `isAvailable` checks and heavy try-catch blocks in Kotlin.
4.  **JNI Type Correction:** Fixed a type mismatch in the budget query logic.
5.  **Default Model Fix:** Changed the default model to a verified asset path (`models-cunet/scale2.0x_model`).

---

## Technical Implementation Details (for AI/Implementation)

### 1. JNI Unified Interface
The JNI layer should expose a polymorphic-like interface.
```cpp
// upscaler_jni.cpp
extern "C" JNIEXPORT jint JNICALL
Java_pro_archiemeng_upscaler_NcnnUpscaler_init(JNIEnv* env, jobject thiz, jint engine_type, jint gpu_id, jboolean tta_mode, jint num_threads);

extern "C" JNIEXPORT jint JNICALL
Java_pro_archiemeng_upscaler_NcnnUpscaler_load(JNIEnv* env, jobject thiz, jobject asset_manager, jstring param_path, jstring model_path);

extern "C" JNIEXPORT jint JNICALL
Java_pro_archiemeng_upscaler_NcnnUpscaler_process(JNIEnv* env, jobject thiz, jobject bitmap_in, jobject bitmap_out);
```

### 2. CMakeLists.txt Configuration
Must handle multiple shader generation macros.
```cmake
# SPIR-V Generation for both engines
waifu2x_add_shader(waifu2x_preproc.comp)
...
realcugan_add_shader(realcugan_preproc.comp)
...
```

### 3. RealCUGAN JNI Adaptation
`realcugan.cpp` from the CLI needs its `load` method adapted to use `AAssetManager` if loading from assets, or `fopen` if loading from files.
Ensure `tilesize` and `prepadding` are correctly initialized for `up2x-conservative` (Scale: 2, Tilesize: 400, Prepadding: 18).

#### 4. Memory Management
- **Critical:** Always call `net.clear()` and delete pipeline objects in the engine destructor to prevent Vulkan memory leaks on Android.
- Use `AndroidBitmap_lockPixels` and `AndroidBitmap_unlockPixels` for zero-copy access to `Bitmap` data.

---

## Phase 3: Pre-emptive Upscaling & Advanced Controls
Add an option to upscale images immediately after decoding if they fall below a user-defined size threshold. This ensures consistent high quality even at 1:1 zoom for small sources.

### Steps:

1.  **Settings & Persistence:**
    *   **Data Model:** Update `NcnnUpscalerSettings` to include `upscaleOnLoad: Boolean` and `upscaleOnLoadThreshold: Int` (e.g., width in pixels).
    *   **Database:** Create a new migration (`V22`) to add `ncnn_upscale_on_load` and `ncnn_upscale_threshold` columns to `ImageReaderSettingsTable`.
    *   **Default Values:** `upscaleOnLoad = false`, `upscaleThreshold = 1200`.

2.  **UI Extension:**
    *   Add a Toggle for "Upscale on Load" in the NCNN Settings section.
    *   Add a slider or text input for "Min Width Threshold" (e.g., 800px to 2000px).
    *   Include a tooltip explaining that this will trigger upscaling immediately when a page is opened if its width is smaller than the threshold.

3.  **Pipeline Integration:**
    *   **Hook Point:** Modify `AndroidNcnnUpscaler` to expose a `checkAndUpscale(image: KomeliaImage): KomeliaImage` method.
    *   **Logic:**
        ```kotlin
        if (settings.upscaleOnLoad && image.width < settings.upscaleThreshold) {
            return upscale(image) ?: image
        }
        return image
        ```
    *   **Implementation:** In `TilingReaderImage.loadImage()` (or its Android override), call this check immediately after the image is decoded and passed through the `processingPipeline`.
    *   **Result:** The "Original Image" held by the reader will now be the upscaled version. This naturally flows into the existing tiling/zoom logic without additional changes.

4.  **Performance & Feedback:**
    *   **Logging:** Add high-visibility logs when a pre-emptive upscale is triggered: `[NCNN] Pre-emptive upscale triggered: ${image.width}px < ${threshold}px`.
    *   **Async execution:** Ensure this upscaling happens on the `processingScope` to avoid blocking the main thread during page transitions.

### Phase 5 Goals:
*   Add high-quality `realsr-general-v3` and `real-esrganv3-anime-x2` models.
*   Incorporate `RealSR` engine into the native upscaler module.
*   Update UI and domain logic to support new engines and models.

### Phase 5 Completion Summary (March 6, 2026)
**Status: COMPLETED**
- **Native Integration:**
    - Integrated `RealSR` engine source from `RealSR-NCNN-Android-CLI` into `komelia-infra:ncnn-upscaler`.
    - Added JNI `load` method with `AAssetManager` support to `RealSR` class.
    - Updated `upscaler_jni.cpp` to handle `ENGINE_REALSR` (2) and `ENGINE_REAL_ESRGAN` (3) using the unified `RealSR` engine.
    - Configured `CMakeLists.txt` to include `realsr/realsr.cpp` and necessary include paths.
- **Model Assets:**
    - Added `realsr-general-v3` (as `models-realsr/x4.bin/param`) to Android assets.
    - Added `real-esrganv3-anime-x2` (as `models-realesrgan/x2.bin/param`) to Android assets.
- **Kotlin & Domain Update:**
    - Updated `NcnnUpscaler.kt` with new engine constants.
    - Updated `NcnnUpscalerSettings.kt` with `REALSR` and `REAL_ESRGAN` engines.
    - Enhanced `AndroidNcnnUpscaler.kt` to handle new engine types and specific model file naming (e.g., `x4.bin`).
    - Made `upscale` method scale-aware based on the selected model.
- **UI & Strings:**
    - Added localized labels for RealSR and Real-ESRGAN in `EnStrings.kt`.
    - Updated `NcnnSettingsContent.kt` with new engine options and model selection logic.
    - Added a helper to set default models when switching engines in the UI.
- **Build:** Verified successful AAR generation with `./gradlew :komelia-infra:ncnn-upscaler:assembleDebug`.


### Phase 3 Completion Summary (March 5, 2026)
**Status: COMPLETED (Integrated, Stability Issues Persist)**

#### 1. Implementation Details
*   **Settings:** Added `upscaleOnLoad` and `upscaleThreshold` to `NcnnUpscalerSettings` with full persistence (DataStore + SQLite migration `V22`).
*   **UI:** Implemented "Upscale on load" toggle and "Min width threshold" input in the Android settings.
*   **Pipeline:** Integrated `checkAndUpscale` into `AndroidReaderImage.loadImage()`. Images smaller than the threshold are now upscaled immediately after decoding.
*   **Diagnostics:** Added a built-in "View Logs" button in the NCNN settings to capture `logcat` directly from the device.

#### 2. Root Cause Analysis (RCA)
The persistent `VK_ERROR_DEVICE_LOST (-4)` errors, despite the engines working in a standalone demo app, point to integration-specific architectural failures:

*   **Instance Leakage (Primary Cause):** `AndroidNcnnUpscaler` is not a singleton in `AndroidAppModule`. A new instance is created every time a reader is opened. This results in multiple overlapping Vulkan initializations and multiple active "owners" of the GPU context.
*   **Native State Race Condition:** The JNI layer (`upscaler_jni.cpp`) uses static global engine pointers. Each Kotlin `AndroidNcnnUpscaler` instance has its own `Mutex`, which only protects against concurrent access *within that specific instance*. Multiple instances can therefore concurrently call native methods, leading to non-thread-safe access to the shared NCNN `Net` and `VkDevice`.
*   **Vulkan UI Conflict:** Komelia's hardware-accelerated Compose UI (via Skia) runs on the same process. Without strict, process-wide global synchronization, NCNN and the UI pipeline compete for GPU queues, triggering driver resets (TDR) when command buffers are saturated or corrupted by leaked instances.

#### 3. Revised Resolution Plan
To achieve the stability observed in the demo app, the integration must be strictly hardened:

1.  **Strict Singleton Pattern:** Refactor `AndroidAppModule` to treat `AndroidNcnnUpscaler` as a singleton, ensuring only one instance exists and manages the native state for the entire app lifecycle.
2.  **Process-Wide GPU Mutex:** Move the synchronization `Mutex` to a global/companion object level to ensure absolute mutual exclusion for all GPU-bound upscaling operations, regardless of which component triggers them.
3.  **Idempotent Instance Management:** Update JNI to safely handle re-initialization and ensure `ncnn::create_gpu_instance()` is managed as a single-instance resource.
4.  **Optional: ONNX Linkage Cleanup:** Fix the unrelated `UnsatisfiedLinkError` in `OnnxRuntime.enumerateDevices()` to remove noise from the logs and stabilize the Settings UI.

---

### 5. Implementation Summary

#### Core JNI / Native (komelia-infra/ncnn-upscaler)
- `upscaler_jni.cpp`: The bridge between Kotlin and NCNN. It manages static global pointers for `Waifu2x` and `RealCUGAN` engines.
  - **Enhancements:** Added a `std::mutex` for thread-safe access to globals, improved null checks, and increased logging to diagnose `vkQueueSubmit` failures. Capped `tilesize` at 128 for Android stability.
- `waifu2x/`, `realcugan/`: Contains the C++ implementation for the upscaling engines.
- `CMakeLists.txt`: Configures the native build, including linking with the NCNN Android Vulkan library.

#### Kotlin / Infra (komelia-infra)
- `NcnnUpscaler.kt`: Kotlin class defining `external` methods for JNI.
- `NcnnSharedLibraries.kt`: Handles the loading of native libraries (`omp`, `ncnn`, `upscaler`).
- `ImageReaderSettings.kt` & `ExposedImageReaderSettingsRepository.kt`: Persistent storage for NCNN settings (engine, model, GPU ID, TTA, etc.) in the SQLite database.
- `V21__ncnn_upscaler_settings.sql`, `V22__ncnn_upscale_on_load.sql`: Database migration scripts for NCNN settings.

#### Domain / Business Logic (komelia-domain)
- `AndroidNcnnUpscaler.kt`: The primary coordinator for NCNN operations.
  - **Singleton & Global Lock:** Modified to be a singleton with a `companion object` global `Mutex` to prevent concurrent GPU access across different app components.
  - **Lifecycle:** Manages `createGpuInstance()` and engine re-initialization based on settings changes.
- `AndroidBitmapBackedImage.kt`: A `KomeliaImage` implementation that wraps an Android `Bitmap`, used as the output for upscaling.
- `AndroidReaderImageFactory.kt`: Integrates the NCNN upscaler into the image loading pipeline, allowing for pre-emptive upscaling during page loads.
- `NcnnUpscalerSettings.kt`: Domain model for NCNN configuration.

#### UI & Settings (komelia-ui)
- `ImageReaderSettingsScreen.kt` & `ViewModel`: Provides the UI for configuring NCNN settings in the app's general settings.
- `BottomSheetSettingsOverlay.kt`: Adds quick-access NCNN toggles and threshold adjustments directly within the reader's bottom sheet.
- `EnStrings.kt`: Added localized strings for all NCNN-related UI elements.

#### App Integration
- `AndroidAppModule.kt`: Wired the `AndroidNcnnUpscaler` as a singleton and injected it into the `ReaderImageFactory`.
- `settings.gradle.kts`: Included the new `:komelia-infra:ncnn-upscaler` module in the project build.

---

## Phase 5: Expanding Model Support (RealSR & Real-ESRGAN)
Add high-quality `realsr-general-v3` and `real-esrganv3-anime-x2` models to provide more choices for different content types (photographic vs anime).

### Detailed Steps:

1. **Source Integration (Native):**
    *   **RealSR:**
        *   Create `komelia-infra/ncnn-upscaler/src/main/jni/realsr/`.
        *   Copy the following files from `/home/eyal/RealSR-NCNN-Android/RealSR-NCNN-Android-CLI/RealSR/src/main/jni/`:
            *   `realsr.cpp`, `realsr.h`
            *   `realsr_preproc.comp.hex.h`, `realsr_postproc.comp.hex.h`
            *   `realsr_preproc_tta.comp.hex.h`, `realsr_postproc_tta.comp.hex.h`
    *   **Real-ESRGAN:**
        *   Create `komelia-infra/ncnn-upscaler/src/main/jni/realesrgan/`.
        *   Copy the following files from `/home/eyal/RealSR-NCNN-Android/RealSR-NCNN-Android-CLI/RealESRGAN/src/main/jni/`:
            *   `realesrgan.cpp`, `realesrgan.h`
            *   `realesrgan_preproc.comp.hex.h`, `realesrgan_postproc.comp.hex.h`
            *   `realesrgan_preproc_tta.comp.hex.h`, `realesrgan_postproc_tta.comp.hex.h`

2. **JNI Layer Expansion (`upscaler_jni.cpp`):**
    *   **Includes:** Add `#include "realsr/realsr.h"` and `#include "realesrgan/realesrgan.h"`.
    *   **Globals:** Add `static RealSR* realsr = 0;` and `static RealESRGAN* realesrgan = 0;`.
    *   **`init`:** Handle `ENGINE_REALSR` (type 2) and `ENGINE_REALESRGAN` (type 3). Initialize the corresponding engine class.
    *   **`load`:**
        *   Add handling for `current_engine == 2` and `current_engine == 3`.
        *   For `RealSR`, set `prepadding = 0` (it typically handles padding internally or doesn't need it the same way).
        *   For `Real-ESRGAN`, set `prepadding = 10` (verify based on model).
    *   **`process`:** Add routing to `realsr->process()` and `realesrgan->process()`.
    *   **`release`:** Ensure both new engine pointers are deleted and nulled.

3. **Build System Update (`CMakeLists.txt`):**
    *   Add `realsr` and `realesrgan` to `include_directories`.
    *   Update `add_library(ncnn-upscaler ...)` to include `realsr/realsr.cpp` and `realesrgan/realesrgan.cpp`.

4. **Kotlin & Domain Update:**
    *   **`NcnnUpscaler.kt`:** Add `const val ENGINE_REALSR = 2` and `const val ENGINE_REALESRGAN = 3`.
    *   **`NcnnUpscalerSettings.kt`:** Add `REALSR` and `REAL_ESRGAN` to `NcnnEngine` enum.
    *   **`AndroidNcnnUpscaler.kt`:** Update engine initialization logic to support the new types.

5. **Model Assets:**
    *   **RealSR:** Copy `realsr-general-v3` folder from `/home/eyal/RealSR-NCNN-Android/Assets/realsr/models-se/` to `komelia-infra/ncnn-upscaler/src/main/assets/models-realsr/`.
    *   **Real-ESRGAN:** Copy `real-esrganv3-anime` folder from `/home/eyal/RealSR-NCNN-Android/Assets/realsr/models-se/` to `komelia-infra/ncnn-upscaler/src/main/assets/models-realesrgan/`.

6. **UI & Localization:**
    *   **`EnStrings.kt`:** Add labels for "RealSR" and "Real-ESRGAN".
    *   **`NcnnSettingsContent.kt`:** Update the engine selection dropdown and conditionally show models for the new engines.
    *   **Models Mapping:** Ensure the UI knows which models belong to which engine.

