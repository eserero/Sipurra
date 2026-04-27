# Implementation Plan for PaddleOCR (ONNX)

## Background & Motivation
Komelia currently uses Google ML Kit for OCR on Android, which supports limited languages. PaddleOCR (specifically PP-OCRv5 via ONNX Runtime) provides robust multilingual support. The goal is to integrate a new OCR engine on Android using the ONNX models, leveraging Microsoft's official `onnxruntime-android` dependency to run the inference pipeline purely in Kotlin without needing complex C++ JNI code.

## Scope & Impact
*   **Android Dependencies**: Add `com.microsoft.onnxruntime:onnxruntime-android:1.23.0` to `komelia-domain/core`.
*   **App Build Config**: Configure `pickFirst` in `komelia-app/build.gradle.kts` for `libonnxruntime.so` to resolve packaging conflicts between existing custom JNI and the new Microsoft dependency.
*   **Settings Model**: Introduce an `OcrEngine` setting enum (ML Kit vs. PaddleOCR). Store PaddleOCR ONNX models URL in settings.
*   **Android OCR Engine**: Build `AndroidPaddleOcr` using `OrtSession` and `OrtEnvironment`. Modify `OcrService.android.kt` to route traffic based on the selected engine.
*   **Model Downloader**: Download PaddleOCR models (`det.onnx`, `rec.onnx`) on demand, reusing the app's existing model download infrastructure.

## Implementation Steps

### 1. Build Configuration & Dependency Resolution (CRITICAL)
**CRITICAL NOTE:** We must use exactly `com.microsoft.onnxruntime:onnxruntime-android:1.23.0`. The reason for this is because the **page by page navigation** feature depends on this specific `libonnxruntime.so` version. It is critical that we use this exact version to ensure we do not break the page by page navigation functionality.

*   **Files to Modify:**
    *   `komelia-domain/core/build.gradle.kts`: Add `implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.0")` to the `androidMain.dependencies` block.
    *   `komelia-app/build.gradle.kts`: Inside the `packaging { jniLibs { ... } }` block, add rules to prevent `.so` duplicates:
        ```kotlin
        pickFirst("**/libonnxruntime.so")
        pickFirst("**/libonnxruntime_providers_shared.so")
        ```

### 2. Model Preparation & Upload
*   **Step to Create and Host the Model Bundle:**
    1.  Download the pre-converted PP-OCRv5 ONNX models from Hugging Face (e.g., `monkt/paddleocr-onnx` or `SWHL/RapidOCR`).
    2.  Zip the necessary detection (`det.onnx`) and recognition (`rec.onnx`) models into an archive named `PaddleOCRModels.zip`.
    3.  Upload this `PaddleOCRModels.zip` file to the `model` release tag in the `eserero/Komelia` (or `Snd-R/komelia-onnxruntime`) GitHub repository.
    4.  The exact link for the app to download the models will then be: `https://github.com/eserero/Komelia/releases/download/model/PaddleOCRModels.zip`

### 3. Settings & UI Modifications
*   **Files to Modify:**
    *   `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/OcrSettings.kt`: Add `val engine: OcrEngine = OcrEngine.ML_KIT` to `OcrSettings`. Create an `OcrEngine` enum (`ML_KIT`, `PADDLE_OCR`).
    *   `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/ImageReaderSettings.kt`: Add properties for `ocrEngine` (string default "ML_KIT") and `paddleOcrModelsUrl` (string default `https://github.com/eserero/Komelia/releases/download/model/PaddleOCRModels.zip`).
    *   `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/ImageReaderSettingsTable.kt`: Add the corresponding database columns (`ocr_engine`, `paddle_ocr_url`).
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/...` (OCR Settings UI component): Add a SegmentedButton or Dropdown to switch between ML Kit and PaddleOCR. When PaddleOCR is selected, disable or hide the language selector since PaddleOCR's `rec.onnx` is implicitly multilingual.
*   **Files to Add:**
    *   `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/VXX__paddleocr_settings.sql`: Create a new DB migration file to add the `ocr_engine` and `paddle_ocr_url` columns to the `ImageReaderSettings` table.

### 4. Model Download Management
*   **Files to Modify:**
    *   `komelia-domain/core/src/androidMain/kotlin/snd/komelia/updates/AndroidOnnxModelDownloader.kt` (or create a new `AndroidPaddleOcrModelDownloader`): Implement logic to fetch `PaddleOCRModels.zip` from the exact GitHub release link, extract the `.onnx` files, and save them into the app's internal data directory for inference.

### 5. PaddleOCR Implementation
*   **Files to Add:**
    *   `komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/AndroidPaddleOcr.kt`: Create this new class to handle the ONNX inference pipeline.
        *   Initialize `OrtEnvironment.getEnvironment()`.
        *   Load `det.onnx` and `rec.onnx` via `OrtSession`.
        *   Implement preprocessing: resize `Bitmap` to tensor requirements.
        *   Execute detection: run `det.onnx` to get bounding boxes.
        *   Execute recognition: for each bounding box, crop the `Bitmap`, convert to tensor, run `rec.onnx`, and extract the text string.
        *   Map the results into a `List<OcrElementBox>`.
*   **Files to Modify:**
    *   `komelia-domain/core/src/androidMain/kotlin/snd/komelia/ocr/OcrService.android.kt`: Inject the settings and the initialized `AndroidPaddleOcr`. In `recognizeText()`, check the selected engine. Route to ML Kit recognizers or PaddleOCR accordingly.

## Verification & Testing
*   **Packaging**: Build Android APK and ensure no duplicate `libonnxruntime.so` build errors occur.
*   **Runtime Stability (Critical)**:
    1.  Test PaddleOCR on an image and ensure the pipeline completes and returns text.
    2.  Test the existing **page by page navigation** and **Panel Detector** to ensure they do not crash when sharing the `.so` library provided by the `pickFirst` rule.
*   **UI/UX**: Verify that switching to PaddleOCR triggers the model download if missing.

## Migration & Rollback
*   If sharing `libonnxruntime.so` via `pickFirst` breaks the page by page navigation due to unforeseen ABI incompatibilities (despite matching v1.23.0), the fallback strategy is to remove the Microsoft dependency and migrate the PaddleOCR logic natively into the `komelia-infra/onnxruntime` C++ wrapper.