# Gesture System: Simultaneous Pan/Zoom & Velocity Fix

This document records the implementation of the verified solution for the comic reader's gesture handling system.

## Problems Addressed

1.  **Pan-Zoom Lock**: The gesture detector prevented panning whenever a zoom change was detected, making the UI feel stiff and unresponsive during multi-touch gestures.
2.  **Velocity Jumps (The "Leap" Bug)**: When lifting one finger during a two-finger gesture, the "centroid" (the center point between fingers) would suddenly jump from the center of two fingers to the position of the remaining finger. This one-frame jump was interpreted as extreme velocity, causing the image to fly violently off-screen.
3.  **Continuous Mode Regression**: Previous attempts to fix these issues often broke the native kinetic scrolling of the `LazyColumn` in continuous mode by either consuming events prematurely or introducing asynchronous timing mismatches.

## The Solution: "Surgical Frame Filtering"

The implementation uses a non-invasive approach that filters input data before it reaches the movement logic, ensuring the output behavior remains compatible with native scrolling.

### 1. Pointer Count Stability Tracking
We added tracking for the number of active fingers (`lastIterationPointerCount`) inside the `detectTransformGestures` loop in `ScalableContainer.kt`.

- **Mechanism**: We only apply zoom and pan changes if the pointer count is **stable** (exactly the same as the previous frame).
- **Result**: This automatically ignores the single "jump frame" that occurs at the exact millisecond a finger is added or removed.

### 2. Synchronized Velocity Reset
We added a `resetVelocity()` method to `ScreenScaleState.kt` to clear the `VelocityTracker`'s history.

- **Mechanism**: This is called whenever the finger count changes.
- **Result**: It ensures that the velocity for the "new" gesture (e.g., transitioning from two-finger zoom to one-finger pan) is calculated from a clean slate, preventing the jump from being factored into the momentum.

### 3. Native Scroll Preservation
Crucially, the implementation continues to **not consume** the pointer events.

- **Result**: Because the events are not consumed, the `LazyColumn` in continuous mode can still see the vertical movement and handle it using its own internal, highly-optimized physics. This preserves the smooth, kinetic feel of the vertical scroll while allowing simultaneous pan/zoom.

## Implementation Details

### Files Modified:
- **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ScreenScaleState.kt`**
    - Added `resetVelocity()` helper.
- **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ScalableContainer.kt`**
    - Implemented `lastIterationPointerCount` tracking.
    - Removed the `if/else` block that prevented simultaneous pan/zoom.
    - Integrated `resetVelocity()` calls on pointer count changes.

---

## Smooth Mode-Aware Double Tap to Zoom (Implemented)

### Problem: The "Fit Height" Jump
Previously, double-tapping to zoom out would always return the image to a zoom level of 1.0 (Fit Height). If a user was reading in "Fit Width" or a padded webtoon mode, this behavior was jarring as it forced them out of their preferred layout.

### The Solution: "Layout Base Zoom"
We implemented a system where the reader remembers the "base" zoom level intended by the current reading mode and uses it as the target for zooming out.

#### 1. Base Zoom Tracking
In `ScreenScaleState.kt`, we added a `baseZoom` property.
- **Mechanism**: The layout engines (Paged, Continuous, Panels) now flag their initial zoom calculations as "Base Zoom" using `setZoom(..., updateBase = true)`.
- **Result**: `ScreenScaleState` always knows what the "correct" zoom level is for the current mode (e.g., 1.2x for Fit Width).

#### 2. Animated Mode Toggle
We implemented a `toggleZoom(focus)` function that provides a smooth, kinetic transition.
- **Animation**: Uses a `SpringSpec` (`StiffnessLow`) for a natural, decelerating feel.
- **Logic**: 
    - If current zoom > base: Zoom out to `baseZoom`.
    - If current zoom <= base: Zoom in to `max(base * 2.5, 2.5)`.
- **Focus Preservation**: The tapped point (`focus`) remains stationary under the finger as the image expands or contracts around it.

#### 3. Reader Mode Integration
The solution is integrated across all reader modes to ensure consistent behavior:
- **Paged Mode**: Returns to "Fit Width", "Fit Height", or "Original" as defined by the user settings.
- **Continuous Mode**: Returns to the padded column width (Webtoon style).
- **Panels Mode**: Returns to the specific fit level of the current panel.

### Files Modified:
- **`ScreenScaleState.kt`**: Implemented `baseZoom` tracking and the smooth `toggleZoom` animation.
- **`ReaderContent.kt`**: Integrated `onDoubleTap` into the `ReaderControlsOverlay` gesture detector.
- **`PagedReaderState.kt`, `ContinuousReaderState.kt`, `PanelsReaderState.kt`**: Updated layout logic to set the `baseZoom`.
