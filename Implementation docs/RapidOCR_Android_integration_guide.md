# Integration & Highlighting Guide for RapidOCR4j-Android

This guide explains how to integrate the library into your Android application to identify and highlight text within images.

---

## 1. Prerequisites & Setup

### Dependency Configuration
Add the library to your `app/build.gradle` file. Ensure your `minSdkVersion` is at least **29**.

```groovy
dependencies {
    // Main Library
    implementation 'io.github.hzkitty:rapidocr4j-android:1.0.0'
}

android {
    defaultConfig {
        minSdkVersion 29
    }
}
```

### Core Engine Versions
The library is built upon the following specific engine versions.

*   **ONNX Runtime (Android):** `com.microsoft.onnxruntime:onnxruntime-android:1.18.0`
*   **OpenCV (Android):** `org.opencv:opencv:4.9.0`
*   **Java Version:** Java 11

### Managing ONNX Version Conflicts
If your application requires a newer version of ONNX Runtime (e.g., **1.23.0**), you can safely override the library's default version. The inference API used in this project is backward compatible.

Add this to your `app/build.gradle` to force the higher version:

```groovy
dependencies {
    // Force your specific version
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.23.0'
    implementation 'io.github.hzkitty:rapidocr4j-android:1.0.0'
}

// Optional: Ensure no other version is used transitively
configurations.all {
    resolutionStrategy {
        force 'com.microsoft.onnxruntime:onnxruntime-android:1.23.0'
    }
}
```

---

## 2. Build Optimization (APK Size)

Because this library includes native C++ binaries (OpenCV and ONNX Runtime), it supports multiple hardware architectures (ABIs). By default, including the library will add binaries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

### Size Impact
*   **Unoptimized APK:** Can increase by **100MB+** because it bundles all architectures.
*   **Optimized:** Can be reduced to **~30-40MB** using the methods below.

### Strategy 1: Android App Bundles (.aab) - Recommended
Publishing as an App Bundle is the best solution. Google Play will serve only the relevant binaries for the user's specific device architecture.

### Strategy 2: ABI Filtering
If you need to ship a single APK and want to exclude older or unnecessary architectures, use `abiFilters` in your `app/build.gradle`:

```groovy
android {
    defaultConfig {
        ndk {
            // Include only modern 64-bit ARM binaries (most common)
            abiFilters "arm64-v8a"
            
            // Optional: add "armeabi-v7a" for older phones or "x86_64" for emulators
        }
    }
}
```

---

## 3. Identifying Text (Basic Usage)

Initialize the `RapidOCR` engine and pass a `Bitmap` to identify text.

```java
import io.github.hzkitty.RapidOCR;
import io.github.hzkitty.entity.OcrConfig;
import io.github.hzkitty.entity.OcrResult;
import io.github.hzkitty.entity.RecResult;

// 1. Initialize with default config (Chinese/English v4)
RapidOCR rapidOCR = RapidOCR.create(context);

// 2. Run OCR on a Bitmap
OcrResult result = rapidOCR.run(myBitmap);

// 3. Process results
for (RecResult item : result.getRecResultList()) {
    String text = item.getText();
    float score = item.getConfidence();
    org.opencv.core.Point[] box = item.getBox(); // Corner points (TL, TR, BR, BL)
}
```

---

## 4. Model Requirement Matrix

To achieve the full process of **identifying**, **highlighting**, and **converting** text, you need three types of models:

1.  **Detection:** Finds text boxes (for identification and highlighting).
2.  **Classification:** Detects text orientation/angle (for robustness).
3.  **Recognition:** Reads the characters (for text conversion).

| Target Language | Detection (Finds Boxes) | Classification (Angle) | Recognition (Reads Text) |
| :--- | :--- | :--- | :--- |
| **English & Chinese** | `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `ch_PP-OCRv4_rec` |
| **English (Only)** | `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `en_PP-OCRv4_rec` |
| **Latin Multilingual**| `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `latin_PP-OCRv3_rec` |
| **Japanese** | `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `japan_PP-OCRv4_rec` |
| **Korean** | `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `korean_PP-OCRv4_rec` |
| **Arabic** | `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `arabic_PP-OCRv4_rec` |
| **Hebrew** | `ch_PP-OCRv4_det` | `ch_ppocr_v2.0_cls` | `he_PP-OCRv3_rec` |

---

## 5. Download Links (ONNX Format)

These models are pre-converted for ONNXRuntime. For convenience, all supported models are packaged into a single ZIP file hosted on GitHub.

*   **Release URL:** [RapidOcrModels.zip](https://github.com/eserero/Sipurra/releases/download/model/RapidOcrModels.zip) (~66 MB)

### Included Models (Renamed for App)
| Task | Original Model | Filename in ZIP |
| :--- | :--- | :--- |
| **Detection** | `ch_PP-OCRv4_det` | `ch_PP-OCRv4_det_infer.onnx` |
| **Classification**| `ch_ppocr_v2.0_cls` | `ch_ppocr_mobile_v2.0_cls_infer.onnx` |
| **Rec (EN/ZH)** | `ch_PP-OCRv4_rec` | `ch_PP-OCRv4_rec_infer.onnx` |
| **Rec (EN Only)** | `en_PP-OCRv4_rec` | `en_PP-OCRv4_rec_infer.onnx` |
| **Rec (Latin)** | `latin_PP-OCRv3_rec` | `latin_PP-OCRv3_rec_infer.onnx` |
| **Rec (Japanese)**| `japan_PP-OCRv4_rec` | `japan_PP-OCRv4_rec_infer.onnx` |
| **Rec (Korean)** | `korean_PP-OCRv4_rec` | `korean_PP-OCRv4_rec_infer.onnx` |
| **Rec (Arabic)** | `arabic_PP-OCRv4_rec` | `arabic_PP-OCRv4_rec_infer.onnx` |
| **Rec (Hebrew)** | `he_PP-OCRv3_rec` | `<currently missing from ZIP>` |

---

## 6. Implementation: Switching Models & Languages

To switch between different model combinations or languages, update the specific model paths in the `OcrConfig` object.

### Comprehensive Configuration Example
This example shows how to explicitly set all three stages of the pipeline (Detection, Classification, and Recognition).

```java
OcrConfig config = new OcrConfig();

// 1. Set the Detection Model (Universal v4 - Finds text)
config.getDet().setModelPath("ch_PP-OCRv4_det_infer.onnx");

// 2. Set the Classification Model (Universal v2 - Detects angle)
config.getCls().setModelPath("ch_ppocr_mobile_v2.0_cls_infer.onnx");

// 3. Set the Recognition Model (Language-Specific - Reads text)
// e.g., For Hebrew v3:
config.getRec().setModelPath("he_PP-OCRv3_rec_infer.onnx");

// Optional: Enable or Disable specific modules
config.getGlobal().setUseCls(true); // Set to false if you don't need angle detection
config.getGlobal().setUseDet(true); 
config.getGlobal().setUseRec(true);

// Initialize engine with the custom config
RapidOCR rapidOCR = RapidOCR.create(context, config);
```

### Path Resolution
*   **Assets:** If your models are in `app/src/main/assets/`, simply provide the filename.
*   **Internal Storage:** If models are downloaded at runtime, provide the absolute path to the file on the device.

**Note:** The library automatically extracts character dictionaries from the ONNX metadata, so you do not need to provide separate `.txt` dictionary files.

---

## 7. Model Versions & Support

*   **v3 Support:** This library fully supports **PaddleOCR v3** models.
*   **Hebrew Support:** Hebrew is supported via the `he_PP-OCRv3` model.
*   **v5 Compatibility:** **Not supported.** Stick with v4 or v3 models.

---

## 9. Building without Bundled Models

If you want to minimize your initial APK/AAR size and download models at runtime, you can build the library without any bundled models.

### How to Exclude Models
You can prevent Gradle from bundling the ONNX files by adding an exclude rule to your `OcrLibrary/build.gradle`:

```groovy
android {
    sourceSets {
        main {
            // Exclude all models from being bundled in the AAR
            assets.exclude '**/*.onnx'
        }
    }
}
```

### Initializing with External Models
When models are not in the `assets` folder, you **must** provide the absolute path to the files on the device storage.

```java
OcrConfig config = new OcrConfig();

// Use absolute paths to files downloaded to internal storage
String modelDir = context.getFilesDir().getAbsolutePath() + "/models/";

config.getDet().setModelPath(modelDir + "det_v4.onnx");
config.getCls().setModelPath(modelDir + "cls_v2.onnx");
config.getRec().setModelPath(modelDir + "rec_hebrew_v3.onnx");

// The engine detects the absolute path and loads from the file system instead of Assets
RapidOCR rapidOCR = RapidOCR.create(context, config);
```

---
