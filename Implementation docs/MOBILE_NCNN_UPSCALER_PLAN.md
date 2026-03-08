# Mobile NCNN Upscaler Plan

## Overview

Integrate high-performance GPU upscaling from the `RealSR-NCNN-Android` project into Komelia as a native library. We work incrementally across 4 phases, starting with the existing project and building confidence before touching Komelia.

---

## Phase 0: Build the Existing Projects (Baseline)

Prove the full pipeline works end-to-end before changing anything.

### What we have

- `Assets/realsr/` — pre-built CLI binaries + models already downloaded (from the v1.12.0 GitHub release). Contains `realsr-ncnn`, `realcugan-ncnn`, `waifu2x-ncnn`, `srmd-ncnn`, `mnnsr-ncnn`, `resize-ncnn`, `Anime4k`, the shared `.so` files, and all model folders.
- `3rdparty/ncnn-android-vulkan-shared/` — folder exists but ABI dirs are missing (needed for Phase 1+).

### Building the GUI APK

The GUI is a shell over the CLI binaries. Since assets are already available:

1. **Copy assets** into the GUI project:
   ```
   Assets/realsr/ → RealSR-NCNN-Android-GUI/app/src/main/assets/realsr/
   ```
   The `Zone.Identifier` sidecar files from Windows can be ignored — Android build system will skip them.

2. **Build the APK** from the GUI project root:
   ```bash
   cd RealSR-NCNN-Android-GUI
   ./gradlew assembleDebug
   ```

3. **Install and test** on a physical Android device to confirm the CLI pipeline works.

### Building the CLI from source (needed for Phase 1+)

The CLI modules need 3 native dependencies in `3rdparty/`:

| Dependency | Source | Extract to |
|---|---|---|
| ncnn (vulkan-shared) | [ncnn GitHub releases](https://github.com/Tencent/ncnn/releases) → `ncnn-*-android-vulkan-shared.zip` | `3rdparty/ncnn-android-vulkan-shared/arm64-v8a/` |
| libwebp | [webmproject/libwebp](https://github.com/webmproject/libwebp) source zip | `3rdparty/libwebp/` |
| OpenCV Android SDK | [opencv.org/releases](https://opencv.org/releases/) → `opencv-*-android-sdk.zip` | `3rdparty/opencv-android-sdk/sdk/` |

Then build a CLI module (e.g. RealSR):
```bash
cd RealSR-NCNN-Android-CLI
./gradlew :RealSR:assembleRelease
# Binary lands at: RealSR/build/intermediates/cmake/release/obj/arm64-v8a/realsr-ncnn
```

Run `3rdparty/copy_cli_build_result.bat` (or manually copy) to move built binaries into the GUI assets.

---

## Phase 1: Replace CLI Shell-Out with JNI (in the GUI App)

**Goal:** Remove the `sh`-based process execution and disk I/O. Call the engine directly from Java/Kotlin via JNI, passing pixel data in memory.

**Where:** Modify `RealSR-NCNN-Android-GUI` — not Komelia. This gives us a clean reference implementation.

**What changes:**

- Add a new CMake-based native module to the GUI project that links against ncnn.
- Write a JNI bridge (`realsr_jni.cpp`) that:
  - Takes a `byte[]` of raw ARGB pixels + width + height from Java.
  - Converts to `ncnn::Mat`.
  - Calls the chosen engine's `load()` + `process()` directly.
  - Returns the upscaled `byte[]`.
- Replace `ImageProcessor.java` (which runs a shell process) with a Java class that calls `System.loadLibrary("realsr-jni")` and invokes the native method.
- The GUI's `MainActivity` calls this new class instead of building CLI command strings.

**Why this order:** We keep the GUI app's existing UI intact so we have a visual test harness. The only thing that changes is how the upscaling happens internally.

---

## Phase 2: Library-ify the GUI Project

**Goal:** Make the JNI upscaler reusable — extract it into an Android `library` module within the GUI project.

**What changes:**

- Create a new Gradle `library` module (e.g., `:ncnn-upscaler`) inside the GUI project.
- Move the JNI code and CMake config into this module.
- Expose a clean Kotlin API:
  ```kotlin
  class NcnnUpscaler(modelDir: String, modelType: ModelType, scale: Int) {
      fun upscale(pixels: ByteArray, width: Int, height: Int): ByteArray
      fun close()
  }
  ```
- The GUI app's `app` module depends on `:ncnn-upscaler`.
- The library has no Android UI dependencies — just `minSdk`, NDK, and ncnn.

---

## Phase 3: Integrate into Komelia

**Goal:** Wire the `:ncnn-upscaler` library into Komelia's image pipeline.

**What changes in Komelia:**

1. **Module reference:** Add `:ncnn-upscaler` (or publish it as a local Maven artifact) and depend on it in `komelia-app/build.gradle.kts` for Android targets only.
2. **Upscale mode:** Add `NCNN_GPU` to the existing upscale mode enum.
3. **Settings:** Add a model directory picker and model preset selector to `UpscalerSettings`.
4. **Pipeline hook:** Update `AndroidReaderImageFactory` to instantiate `NcnnUpscaler` when `NCNN_GPU` is selected and pipe decoded image bytes through it.
5. **Model management:** User places `.param`/`.bin` model files in a directory on device storage; the library reads them from that path.

---

## Key Architectural Decisions

- **In-memory processing:** No temp files. Pixel data flows: decode → `byte[]` → JNI → ncnn → `byte[]` → display.
- **File-based models:** Models are not bundled in the APK. User provides a local path. This keeps APK size small and lets users pick their own models.
- **arm64-v8a only:** Target the dominant Android ABI. Can add `armeabi-v7a` later if needed.
- **Phases are independently shippable:** Phase 0 gives a working APK. Phase 1 gives a faster APK. Phases 2+3 give Komelia integration.
