# PaddleOCR Fix Plan (Using RapidOCR4j-Android)

## Objective
To provide a reliable, high-performance OCR engine without introducing complex, manually-maintained computer vision logic. We will integrate the **`rapidocr4j-android`** library, which acts as a robust wrapper for PaddleOCR models (specifically PP-OCRv4/v5) using ONNX Runtime and OpenCV.

## Scope & Impact
### 1. Build Configuration (`komelia-domain/core/build.gradle.kts`)
We will add the library and enforce the critical ONNX Runtime version.
```kotlin
androidMain.dependencies {
    implementation("io.github.hzkitty:rapidocr4j-android:1.0.0") {
        // Enforce 1.23.0 to keep page-by-page navigation working
        exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
    }
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.0")
}
```

### 2. Dependency Injection & Context
`rapidocr4j` requires an Android `Context`.
*   **Modify**: `DependencyContainer` in `komelia-ui` to include `val androidContext: Context?`.
*   **Modify**: `AndroidAppModule.kt` to pass the application context.
*   **Modify**: `OcrService` (Android actual) to receive the context in its constructor.

### 3. Engine Implementation (`AndroidPaddleOcr.kt`)
Rewrite the class to be a thin wrapper around the library.
```kotlin
class AndroidPaddleOcr(
    private val context: Context,
    private val modelDir: Path
) {
    private var engine: RapidOCR? = null

    fun initialize() {
        if (engine != null) return
        val config = OcrConfig().apply {
            Global.detModelPath = modelDir.resolve("det.onnx").toString()
            Global.recModelPath = modelDir.resolve("rec.onnx").toString()
            Global.keysPath = modelDir.resolve("dict.txt").toString()
            Global.isDoAngle = false
            Det.detDBThresh = 0.3f
            Det.maxSideLen = 1024
        }
        engine = RapidOCR.create(context, config)
    }

    suspend fun recognize(bitmap: Bitmap): List<OcrElementBox> = withContext(Dispatchers.Default) {
        initialize()
        val result = engine?.run(bitmap) ?: return@withContext emptyList()
        result.blocks.mapIndexed { index, block ->
            val rect = Rect(
                left = block.box[0].x.toFloat(),
                top = block.box[0].y.toFloat(),
                right = block.box[2].x.toFloat(),
                bottom = block.box[2].y.toFloat()
            )
            OcrElementBox(
                text = block.text,
                imageRect = rect,
                blockRect = rect,
                blockIndex = 0,
                lineIndex = index,
                elementIndex = 0
            )
        }
    }
}
```

## Implementation Steps
1.  **Gradle**: Update `komelia-domain/core/build.gradle.kts` with the library and ONNX constraints.
2.  **Context**: Thread the `Context` through `AndroidAppModule` -> `DependencyContainer` -> `OcrService`.
3.  **Library Code**: Replace the custom math in `AndroidPaddleOcr.kt` with `RapidOCR` library calls.
4.  **Manifest**: Add `tools:replace="android:theme"` to `AndroidManifest.xml` if needed to resolve library conflicts.

## Verification & Testing
*   **Compilation**: Verify no transitive dependency conflicts downgrade `onnxruntime-android`.
*   **Functional**: Open a comic, switch to PaddleOCR, and verify text boxes appear correctly with actual text content from the models.
*   **Regression**: Ensure "Page-by-page navigation" (which relies on ORT 1.23.0) still functions.
