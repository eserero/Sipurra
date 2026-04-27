# RapidOCR Integration Plan

## Objective
Integrate the `RapidOCR4j-Android` library as an alternative OCR engine to the existing ML Kit implementation. Both engines will share the same processing pipeline and UI, allowing users to switch between them in the reader settings. In this initial integration, we will use the built-in models provided by the library.

## Scope & Impact
*   **Dependencies (`komelia-domain/core/build.gradle.kts`)**:
    *   Add `implementation("io.github.hzkitty:rapidocr4j-android:1.0.0")`.
    *   Force `onnxruntime-android` to version `1.23.0` to prevent regressions or conflicts with existing native libraries.
*   **Size Optimization (`komelia-app/build.gradle.kts`)**:
    *   Add `abiFilters` (e.g., `armeabi-v7a`, `arm64-v8a`, `x86_64`) to `defaultConfig` to prevent the APK from bloating by 100MB+.
*   **Min SDK & Compatibility**:
    *   The library requires `minSdkVersion 29`, but the project currently uses `26`.
    *   We will use `tools:overrideLibrary="io.github.hzkitty.rapidocr"` in `AndroidManifest.xml` and ensure RapidOCR is only invoked on devices with API 29+.
*   **Settings Model (`komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/OcrSettings.kt`)**:
    *   Add an `OcrEngine` enum with `ML_KIT` and `RAPID_OCR`.
    *   Add `val engine: OcrEngine = OcrEngine.ML_KIT` to `OcrSettings`.
*   **Settings UI (`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/BottomSheetSettingsOverlay.kt`)**:
    *   Update `OcrModeSettings` (the "Text" tab) to include a selector for the OCR engine, allowing users to toggle between ML Kit and RapidOCR.
    *   Hide or disable the RapidOCR option if the device API level is below 29.
*   **Core Implementation (`komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/OcrService.android.kt`)**:
    *   Update `OcrService` to accept the `Context` required by `RapidOCR` initialization.
    *   Update `recognizeText` to branch based on the selected `OcrEngine`.
    *   For ML Kit, retain the current implementation.
    *   For RapidOCR:
        *   Initialize `RapidOCR.create(context)` (using the built-in default models).
        *   Pass the Android `Bitmap` to `rapidOCR.run(bitmap)`.
        *   Map the bounding box points (`org.opencv.core.Point[]`) from `RecResult` into Komelia's `OcrElementBox`.
        *   Return the resulting list of `OcrElementBox`.

## Implementation Steps
1.  **Dependency Addition**: Update `komelia-domain/core/build.gradle.kts` to include the `rapidocr4j-android` dependency and configure `resolutionStrategy` to force `onnxruntime-android:1.23.0`.
2.  **Size Optimization**: Update `komelia-app/build.gradle.kts` to include `abiFilters`.
3.  **Manifest & Compatibility**: Add `tools:overrideLibrary` to `AndroidManifest.xml` and `tools:replace="android:theme"` to the `<application>` tag.
4.  **Settings Expansion**: Create the `OcrEngine` enum and update `OcrSettings` to store the user's preferred engine. Update the corresponding `BottomSheetSettingsOverlay` UI to render the selection controls (with API level check).
5.  **Service & Context Layering**: Ensure `OcrService.android.kt` has access to the Android `Context`.
6.  **Engine Logic**: Modify the `recognizeText` function in `OcrService.android.kt` to dynamically dispatch to either ML Kit or RapidOCR.
7.  **Output Mapping**: Implement the mapping logic to extract the text and 4-point bounding boxes from RapidOCR's `OcrResult` into a list of `OcrElementBox`.

## Verification & Testing
*   Verify that the project compiles with the new dependencies. Apply `tools:replace="android:theme"` in the app manifest if requested by the library.
*   Test ML Kit text recognition to ensure no regressions.
*   Toggle the setting to RapidOCR and test text recognition on a sample image. Verify the bounding boxes are accurate and the segment merging feature remains operational.
