# Implementation Plan: Paged Mode Sticky Swipe Navigation

## Goal
Implement a high-quality "Sticky Swipe" navigation for the Paged Reader. 
- **Requirement 1 (The Barrier)**: If the image is zoomed in, swiping should pan the image. When hitting the edge, the movement must stop (no immediate page turn). A second, separate swipe starting from the edge is required to turn the page.
- **Requirement 2 (The Control)**: Page turns must be manual and controllable. The user should see the next page sliding in under their finger.
- **Requirement 3 (Kinetic Completion)**: Releasing a swipe should smoothly and kinetically complete the transition to the next page or snap back to the current one.
- **Requirement 4 (Safety)**: These changes must not affect Continuous (Webtoon) mode or Panels mode.

---

## 1. TransformGestureDetector.kt
**Change**: Convert the gesture detector to be fully synchronous and lifecycle-aware.
- Make the `onGesture` callback a `suspend` function.
- Add an `onEnd` `suspend` callback.
- **Reason**: This allows the gesture loop to wait for the UI (Pager/LazyColumn) to finish its `scrollBy` before processing the next millisecond of touch data. This is the foundation of "frame-perfect" kinetic movement.

```kotlin
suspend fun PointerInputScope.detectTransformGestures(
    panZoomLock: Boolean = false,
    onGesture: suspend (changes: List<PointerInputChange>, centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    onEnd: suspend () -> Unit = {}
) {
    // Wrap existing loop in awaitEachGesture
    // Call onGesture with 'suspend'
    // Invoke onEnd() when the touch loop finishes (all fingers up)
}
```

---

## 2. ScreenScaleState.kt
**Change**: Add the "Sticky" logic and synchronize the scrolling handoff.

- **New Properties**:
    - `edgeHandoffEnabled: Boolean` (Default: `false`). Only set to `true` by Paged Mode.
    - `gestureStartedAtHorizontalEdge: Boolean`: Internal flag to track if a swipe is allowed to turn the page.
    - `cumulativePagerScroll: Float`: Tracks total pager movement during one gesture to prevent skipping multiple pages.
    - `isGestureInProgress: MutableStateFlow<Boolean>`: Used for UI snapping.
    - `isFlinging: MutableStateFlow<Boolean>`: Used for UI snapping.

- **Methods**:
    - `onGestureStart()`: Sets `gestureStartedAtHorizontalEdge = isAtHorizontalEdge()`.
    - `isAtHorizontalEdge()`: Returns true if image is zoomed out OR currently clamped to a left/right boundary.
    - `addPan(...)`: Convert to `suspend`. Only call `applyScroll` if `!edgeHandoffEnabled || gestureStartedAtHorizontalEdge`.
    - `applyScroll(...)`: Convert to `suspend`. Remove `scrollScope.launch`. Implement the "Single-Page Constraint" by clamping `cumulativePagerScroll` to `+/- ScreenWidth`.

---

## 3. ScalableContainer.kt
**Change**: Integrate the new gesture lifecycle.

```kotlin
.pointerInput(areaSize) {
    detectTransformGestures(
        onGesture = { changes, centroid, pan, zoom, _ ->
            // On first iteration of a new touch:
            if (!scaleState.isGestureInProgress.value) {
                scaleState.onGestureStart()
                scaleState.isGestureInProgress.value = true
            }
            
            // ... existing pointer count / velocity reset logic ...
            
            if (pointerCountStable) {
                scaleState.addPan(changes, pan) // Now a suspend call
            }
        },
        onEnd = {
            scaleState.isGestureInProgress.value = false
        }
    )
}
```

---

## 4. PagedReaderState.kt
**Change**: Configure the state and provide data for the Pager.

- **Initialization**: Set `screenScaleState.edgeHandoffEnabled = true` and `screenScaleState.enableOverscrollArea(true)`.
- **New Helper**: `getImage(PageMetadata): ReaderImageResult`.
    - **Reason**: The Pager needs to load the "Next" spread while the user is still swiping. This method provides direct access to the image cache/loader.

---

## 5. PagedReaderContent.kt
**Change**: Replace static layout with a controlled `HorizontalPager`.

- **Pager Setup**:
    - `val pagerState = rememberPagerState(...)`
    - `userScrollEnabled = false`: Crucial. The Pager must not handle its own touches; it only moves when `ScreenScaleState` calls `scrollBy`.
- **Sync Logic**:
    - `LaunchedEffect(pagerState)`: Call `scaleState.setScrollState(pagerState)`.
    - `LaunchedEffect(pagerState.currentPage)`: Update `pagedReaderState.onPageChange`.
- **Snapping Effect**:
    - Add a `LaunchedEffect(isGestureInProgress, isFlinging)`.
    - If both are false and the pager is "between" pages, call `pagerState.animateScrollToPage(target)`.
- **Rendering**:
    - The pager items will render either a `TransitionPage` (Start/End) or a `DoublePageLayout`/`SinglePageLayout` using the new `getImage` helper.
