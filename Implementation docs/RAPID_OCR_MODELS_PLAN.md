# RapidOCR Integration Plan - Phase 2 (Models & Optimization)

## Objective
Enhance the existing RapidOCR integration by removing bundled models to reduce application size, introducing a central mechanism to download all language models as a single ZIP from GitHub, and providing an intuitive UI in the settings for users to select their preferred OCR language model.

## Background & Motivation
The initial RapidOCR integration used the bundled models provided by the `rapidocr4j-android` library, which significantly increased the APK size. Furthermore, only the default Chinese/English model was used. To optimize application size and provide multilingual support, we will extract the models from the build, download them dynamically via GitHub Releases (similar to ONNX and NCNN models), and let users choose between the 7 supported model combinations.

## Scope & Impact

1.  **Build Optimization (`komelia-domain/core/build.gradle.kts` & `komelia-app/build.gradle.kts`)**:
    *   Exclude all `.onnx` files provided by `rapidocr4j-android` to prevent them from being packaged into the final APK.

2.  **Model Definitions (`komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/model/OcrSettings.kt`)**:
    *   Introduce a new enum `RapidOcrModel` with the 7 supported options:
        *   `ENGLISH_CHINESE` (Default)
        *   `ENGLISH_ONLY`
        *   `LATIN_MULTILINGUAL`
        *   `JAPANESE`
        *   `KOREAN`
        *   `ARABIC`
        *   `HEBREW`
    *   Add `val rapidOcrModel: RapidOcrModel = RapidOcrModel.ENGLISH_CHINESE` to `OcrSettings`.
    *   Add `val rapidOcrModelsUrl: String = "https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/RapidOcrModels.zip"` to `ImageReaderSettings`.

3.  **Model Downloading (`komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/RapidOcrModelDownloader.kt` & Repositories)**:
    *   Create a new downloader class, `RapidOcrModelDownloader` (similar to `OnnxModelDownloader`), responsible for fetching the ZIP file, extracting the `.onnx` models, and saving them to the application's internal files directory (e.g., `rapidocr_models/`).
    *   Wire the downloader into `DependencyContainer` and ViewModels.

4.  **Settings UI (`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/BottomSheetSettingsOverlay.kt` & `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/imagereader/ImageReaderSettingsContent.kt`)**:
    *   **Reader Overlay:** Update `OcrModeSettings` (the "Text" tab). When the user selects "RapidOCR" as the engine, display a selector (like a dropdown or chip group) bound to `rapidOcrModel`, identical to how ML Kit language selection is handled.
    *   **Main Settings:** Add a new "RapidOCR Models" section under Image Reader settings to trigger the `RapidOcrModelDownloader`, showing download progress and a button to "Download Models" or "Re-download Models".

5.  **Core Implementation (`komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/OcrService.android.kt`)**:
    *   Modify `OcrService` to construct a custom `OcrConfig` based on the selected `RapidOcrModel`.
    *   Determine the absolute paths to the downloaded model files in internal storage.
    *   Map the enum selection to the specific file names (e.g., `ch_PP-OCRv4_det_infer.onnx`, `ch_ppocr_mobile_v2.0_cls_infer.onnx`, and the specific `*_rec_infer.onnx`).
    *   Initialize `RapidOCR.create(context, config)` with the custom configuration. Handle cases where the models are not yet downloaded by falling back gracefully or prompting the user.

## Implementation Steps

1.  **Exclude Models from Build**: Add `packagingOptions { resources { excludes += "**/*.onnx" } }` (or similar depending on the module) to prevent bundling the ONNX files.
2.  **Define Settings**: Update `OcrSettings`, `ImageReaderSettings`, `ImageReaderSettingsRepositoryTable`, and the SQL migrations to store the URL and the selected `RapidOcrModel`.
3.  **Implement Downloader**: Create `RapidOcrModelDownloader` and integrate it into the DI graph.
4.  **Build Main Settings UI**: Create the download UI component in `ImageReaderSettingsContent`.
5.  **Update Reader Settings UI**: Modify `BottomSheetSettingsOverlay` to show the `RapidOcrModel` selector when RapidOCR is active.
6.  **Update Engine Execution**: Refactor `OcrService.android.kt` to load models from the file system based on the selected enum value and the custom `OcrConfig`.

## Verification & Testing
*   **Size Check**: Verify the generated APK size is significantly smaller than before. Inspect the APK to ensure no `*.onnx` files are present in the `assets` folder.
*   **Download Flow**: Open Main Settings -> Image Reader -> Click "Download Models" for RapidOCR. Verify the ZIP downloads, extracts, and reports success.
*   **Model Switching**: Open a book -> Reader Settings -> Text -> Select "RapidOCR" -> Select different models (e.g., Japanese, Hebrew). Verify text is recognized correctly using the specific model and that the engine does not crash.
*   **Missing Models Graceful Failure**: Attempt to use RapidOCR *before* downloading the models. Ensure the app does not crash and provides a meaningful warning or fallback mechanism.
