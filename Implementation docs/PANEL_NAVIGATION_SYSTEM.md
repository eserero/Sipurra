# Panel-by-Panel Navigation System: High-Level Overview

This document describes how the panel detection and navigation system works in the Comic Reader.

## 1. Detection Engine (The "Brain")
The system uses a machine learning model (`RF-Detr`) to identify panels within a page spread.

- **Model Format**: ONNX (Open Neural Network Exchange).
- **Runtime**: `OnnxRuntimeRfDetr` (implemented via ONNX Runtime).
- **Core Interface**: `KomeliaPanelDetector` handles the model lifecycle, including initialization and inference.
- **Input**: A `KomeliaImage` object (the raw decoded page).
- **Output**: A list of `DetectResult` objects, each containing:
    - `classId`: The type of detection (usually represents a panel).
    - `confidence`: How certain the model is about the detection.
    - `boundingBox`: An `ImageRect` (Left, Top, Right, Bottom) in the coordinate space of the original image.

## 2. Pre-Processing & Sorting (`PanelsReaderState.kt`)
Once the model returns raw coordinates, the reader performs several logical steps to make them "readable":

- **Sorting**: The raw list of panels is sorted based on the current **Reading Direction** (Left-to-Right or Right-to-Left). This ensures that "Next Panel" follows the logical flow of the comic.
- **Coverage Analysis**: The system calculates the total area covered by detected panels versus the total image area. 
    - If panels cover > 80% of the image, the system flags it.
    - If detection coverage is low, it might use a "Find Trim" fallback to ignore blank margins.
- **Caching**: Panel coordinates are cached alongside the image data in a `Cache` (using `cache4k`) to avoid re-running inference on every page turn.

## 3. UI Navigation Logic
The `PanelsReaderState` manages the state of which panel is currently "active" using a `PageIndex` (page number + panel index).

### The "Next" Command (`nextPanel()`)
When the user requests the next panel:
1.  **Check Current Page**: If there's another panel in the sorted list for the current page, it scrolls to it.
2.  **Boundary Logic**: 
    - If it was the last panel, it first zooms out to show the full page (`scrollToFit`).
    - If the user clicks again while zoomed out, it moves to the next physical page.
3.  **Calculation**: It uses `getPanelOffsetAndZoom()` to convert the panel's bounding box into a specific `Offset` and `Zoom` level for the `ScreenScaleState`.

### Screen Centering Math
The most critical part of the system is the coordinate transformation:
- **Scale Calculation**: It determines the maximum scale that allows the panel to fit entirely within the screen dimensions without being clipped.
- **Offset Transformation**: It calculates the precise X and Y translation needed to place the center of the panel bounding box exactly in the center of the viewport.

## 4. Execution
The transition is handled by `ScreenScaleState.scrollTo(offset)` and `setZoom()`. Because `ScreenScaleState` uses animated state holders (via `AnimationState`), the move from one panel to the next is a smooth, kinetic slide and zoom effect rather than a jump.

---

## Technical Summary of the Flow:
1. **Load Page** → **Run AI Inference** → **Get Bounding Boxes**.
2. **Sort Boxes** based on LTR/RTL settings.
3. **Calculate Viewport** (Zoom + Offset) to isolate the box.
4. **Animate `ScreenScaleState`** to the calculated viewport.
5. **Update Index** to track progress within the page.
