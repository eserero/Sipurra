# Objective
Integrate text recognition (OCR) and selection into the comic reader using Google ML Kit, adapting the coordinate mapping logic from the official ML Kit `GraphicOverlay.java` sample to Jetpack Compose.

# Background & Motivation
The user wants to seamlessly select and copy text from comic pages (speech bubbles, captions). As discussed with ChatGPT, the Google ML Kit Android Vision sample provides a "hidden gem" in its `GraphicOverlay.java` view. This view elegantly handles the complex mathematics of mapping OCR bounding boxes—which are based on raw image pixels—into screen coordinates, accounting for how the image is scaled or positioned.

Since Komelia uses Jetpack Compose, we cannot use the legacy Android `View` system directly. We need to dissect the underlying logic of `GraphicOverlay` and implement an idiomatic Compose equivalent.

# Analysis of Google's `GraphicOverlay.java` (The Hidden Gem)
At the code level, `GraphicOverlay.java` acts as a transparent canvas sitting on top of an image. Its core responsibility is coordinate transformation.

1.  **State Management:** It holds the intrinsic dimensions of the original image (`imageWidth`, `imageHeight`) and the bounding box of the view itself.
2.  **Scale Factor Calculation:**
    It calculates how much the image was scaled to fit the view:
    ```java
    float scaleFactor = Math.max(
        (float) viewWidth / imageWidth,
        (float) viewHeight / imageHeight
    );
    ```
3.  **Coordinate Mapping Functions:**
    Each `TextGraphic` uses helper functions provided by the overlay to draw its bounding box accurately:
    ```java
    float scale(float imagePixel) {
        return imagePixel * overlay.scaleFactor;
    }
    float translateX(float x) {
        return scale(x) + offsetX;
    }
    float translateY(float y) {
        return scale(y) + offsetY;
    }
    ```
    By feeding the raw ML Kit bounding box `Rect` through these functions, it produces a `RectF` that perfectly aligns with the scaled image on the screen.

# Proposed Compose Adaptation
We will translate this concept into a Jetpack Compose `Canvas` overlaying the `ReaderImageContent`.

## 1. Domain Model
Instead of `TextGraphic` doing the drawing, we will separate state from UI using a persistent data model:

```kotlin
data class OcrElementBox(
    val text: String,
    val imageRect: Rect, // ML Kit raw image coordinates
    val blockIndex: Int,
    val lineIndex: Int,
    val elementIndex: Int,
    var selected: Boolean = false
)
```

## 2. The Compose "GraphicOverlay" (Coordinate Mapping)
We will create a `TextSelectionOverlay` composable. It needs to know the layout geometry of the rendered image. In Komelia, the `ReaderImage` composable handles its own zoom/pan logic.

The overlay needs a function to map `imageToScreen`:
```kotlin
fun mapImageRectToScreen(
    imageRect: Rect,
    intrinsicImageSize: IntSize,
    displayedImageSize: IntSize,
    panOffset: Offset = Offset.Zero // if zoomed/panned
): androidx.compose.ui.geometry.Rect {
    val scaleX = displayedImageSize.width.toFloat() / intrinsicImageSize.width
    val scaleY = displayedImageSize.height.toFloat() / intrinsicImageSize.height
    val scale = minOf(scaleX, scaleY)

    return androidx.compose.ui.geometry.Rect(
        left = (imageRect.left * scale) + panOffset.x,
        top = (imageRect.top * scale) + panOffset.y,
        right = (imageRect.right * scale) + panOffset.x,
        bottom = (imageRect.bottom * scale) + panOffset.y
    )
}
```

## 3. Drawing the Highlights
Inside the `Canvas` block:
```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    boxes.forEach { box ->
        val screenRect = mapImageRectToScreen(box.imageRect, intrinsicSize, displaySize, offset)
        drawRect(
            color = if (box.selected) selectedColor else detectedColor,
            topLeft = screenRect.topLeft,
            size = screenRect.size
        )
    }
}
```

## 4. User Interaction (Touch & Selection)
To make it interactive, we map screen coordinates *back* to image coordinates to hit-test against our `OcrElementBox` models.
```kotlin
Modifier.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { offset ->
           val imagePoint = screenToImage(offset)
           // Hit test against OcrElementBox.imageRect
        },
        onDrag = { change, _ ->
           val currentPoint = screenToImage(change.position)
           // Update selection state for intersected boxes
        }
    )
}
```

## 5. Detailed UX and Interactions

### Visual Representation of Bounding Boxes
*   **Unselected Text:** Detected text blocks will be drawn with a semi-transparent, subtle tint (e.g., a faint gray or blue wash). This indicates the text is interactive without obscuring the artwork underneath.
*   **Selected Text:** When a user taps or drags over a detected bounding box, its color transitions to a more prominent highlight color (matching the standard Android text selection highlight) to clearly define what text is currently selected for copying.

### Zoom and Pan Support
The bounding boxes remain perfectly aligned with the underlying image during zoom and pan operations. The OCR results are stored using the raw image's original pixel coordinates. The `mapImageRectToScreen` function recalculates the exact screen position of these boxes continuously based on the current zoom scale and pan offset of the reader. When the user zooms or pans, the `Canvas` redraws the boxes in real-time, ensuring they stay "glued" to the text on the page.

### Touch Event Handling (OCR vs. Image Menu)
The text selection overlay uses a smart touch-interception strategy via the `pointerInput` modifier:
*   **Inside a Bounding Box:** If a user presses, long-presses, or drags their finger *inside* the bounds of any detected text box, the `TextSelectionOverlay` will consume the touch event to handle text selection. This prevents the standard image long-press menu from appearing.
*   **Outside a Bounding Box:** If a user interacts with the background artwork or anywhere outside the detected text areas, the overlay will ignore the touch event. The event will pass through to the underlying `ReaderImageContent`, triggering the existing long-press behavior (e.g., opening the menu to save or annotate the image).

## 6. Detailed Implementation & Files

This section outlines the specific files to be created or modified, including dependency updates, domain models, and UI integration.

### 6.1. Dependencies
**File:** `gradle/libs.versions.toml`
*   Add the ML Kit Text Recognition dependency:
    ```toml
    [versions]
    mlkit-text-recognition = "16.0.1"

    [libraries]
    mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text-recognition" }
    ```

**File:** `komelia-domain/core/build.gradle.kts`
*   Add the dependency specifically to the `androidMain` source set:
    ```kotlin
    androidMain.dependencies {
        implementation(libs.mlkit.text.recognition)
    }
    ```

### 6.2. Domain Models & Expect/Actual Service
**File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/OcrElementBox.kt` (New File)
*   Define the `OcrElementBox` data class:
    ```kotlin
    package snd.komelia.image
    import androidx.compose.ui.geometry.Rect

    data class OcrElementBox(
        val text: String,
        val imageRect: Rect,
        val blockIndex: Int,
        val lineIndex: Int,
        val elementIndex: Int,
        var selected: Boolean = false
    )
    ```

**File:** `komelia-domain/core/src/commonMain/kotlin/snd/komelia/image/OcrService.kt` (New File)
*   Define the `expect class OcrService`:
    ```kotlin
    package snd.komelia.image

    expect class OcrService() {
        suspend fun recognizeText(image: ReaderImage): List<OcrElementBox>
    }
    ```

**File:** `komelia-domain/core/src/androidMain/kotlin/snd/komelia/image/OcrService.android.kt` (New File)
*   Implement `actual class OcrService` using Google ML Kit.
*   The implementation will attempt to retrieve the `Bitmap` from `AndroidReaderImage` (e.g., by introducing a method or accessing its internals if public).
*   Initialize ML Kit: `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`.
*   Process the image: `val inputImage = InputImage.fromBitmap(bitmap, 0)`.
*   Map the `Text` result to `OcrElementBox` structures.

**Files:** `OcrService.jvm.kt`, `OcrService.wasmJs.kt` (New Files)
*   Implement dummy `actual` classes that immediately return `emptyList<OcrElementBox>()` since ML Kit is unavailable on these platforms.

### 6.3. State Management
**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt` (or corresponding `ReaderViewModel`)
*   Add `ocrResults` and loading state:
    ```kotlin
    val ocrResults = mutableStateOf<List<OcrElementBox>>(emptyList())
    val isOcrLoading = mutableStateOf(false)
    ```
*   Add `suspend fun scanCurrentPageForText(ocrService: OcrService)` which passes the currently active `ReaderImage` to the service, setting `isOcrLoading = true` before and `false` after.

### 6.4. UI Integration
**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/ReaderControlsCard.kt`
*   Add a "Scan Text" `IconButton` or text button that is visible when `image` is active.
*   Clicking it launches a coroutine to call `readerState.scanCurrentPageForText()`.

**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/TextSelectionOverlay.kt` (New File)
*   Create a `TextSelectionOverlay` composable that draws the boxes and manages touch input:
    ```kotlin
    @Composable
    fun TextSelectionOverlay(
        ocrResults: List<OcrElementBox>,
        intrinsicImageSize: IntSize,
        displaySize: IntSize,
        panOffset: Offset,
        onSelectionChanged: (List<OcrElementBox>) -> Unit
    ) {
        // Implementation of mapImageRectToScreen, Canvas drawing, and pointerInput
    }
    ```

**File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderImageContent.kt`
*   Inside `ImageContent(image: ReaderImage)`, wrap the `Image` within a `Box`.
*   Inject the `TextSelectionOverlay` on top of the image.
*   Ensure that the current `imageDisplaySize` and zoom/pan state (likely extracted from the parent layout or state object) are passed to the overlay so it can correctly offset and scale the bounding boxes.

### 6.5. Text Selection Context Menu
*   When `ocrResults.any { it.selected }` is true, render an action menu overlay (e.g., floating near the selected text or pinned to the top app bar).
*   Add a "Copy" button that concatenates the text from all selected `OcrElementBox` instances in order, and copies it to the system clipboard using `LocalClipboardManager.current`.

# Implementation Steps
1.  **Add Dependencies:** Add `com.google.mlkit:text-recognition` to `libs.versions.toml` and the `androidMain` source set.
2.  **Create Domain Models:** Add `OcrElementBox`.
3.  **Create OCR Service:** Implement `expect/actual OcrService` for all platforms, bridging to ML Kit in `androidMain`.
4.  **Integrate into State:** Add `ocrResults`, `isOcrLoading`, and trigger logic in the reader state.
5.  **Reader Controls:** Add a manual "Scan Text" button to trigger the service.
6.  **Implement Overlay:** Build `TextSelectionOverlay` and insert it into `ReaderImageContent`.
7.  **Selection Logic:** Implement dragging to select boxes and a context menu to copy the selected text.