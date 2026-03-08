# Reader Motion System Plan: Spring Physics Migration

## 1. Objective
To replace the current fixed-duration (1000ms) page and panel transitions with a unified, physics-based system. This will ensure consistent timing across different screen sizes (phones vs. tablets) and provide a more responsive, premium feel aligned with Material 3 Motion principles.

## 2. Reasoning
*   **Scale Independence**: Fixed-duration `tween` animations cover distance at different physical speeds depending on screen size. On a large tablet, a 1-second slide feels much slower than on a phone. Spring physics use tension and stiffness to calculate velocity based on distance, maintaining a consistent "tempo."
*   **Responsiveness**: Springs naturally handle "velocity handoff." If a user triggers a new navigation while an old one is finishing, the spring animation inherits the current momentum instead of cutting or restarting abruptly.
*   **OS Setting Resilience**: Fixed `tween` durations are directly multiplied by the Android "Animation Duration Scale" developer setting. If this is set high, reader navigation becomes painfully slow. Springs are more resilient to these multipliers.
*   **M3 Compliance**: Material 3 recommends spring-based motion for expressive, natural interactions like page flipping and camera movement.

## 3. Architecture: Centralized Motion Spec
To prevent disjointed animation speeds, we will create a centralized motion configuration.

**New File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderAnimation.kt`
```kotlin
package snd.komelia.ui.reader.image.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object ReaderAnimation {
    /**
     * Unified spring spec for all manual navigation (taps, arrow keys).
     * Damping: NoBouncy (1.0f) for a clean, professional finish.
     * Stiffness: MediumLow (approx 400ms feel) for a snappy, premium response.
     */
    val NavSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
```

## 4. Implementation Details

### A. ScreenScaleState.kt (Camera Movement)
**Function**: `animateTo(offset: Offset, zoom: Float)`
*   **Change**: Replace `tween(durationMillis = 1000)` with `ReaderAnimation.NavSpringSpec`.
*   **Effect**: Panel-to-panel transitions and "Fit to Screen" zooms will use physical momentum.

### B. PagedReaderContent.kt (Paged Mode)
**Function**: `pagerState.animateScrollToPage(...)` inside navigation event collection.
*   **Change**: Replace `tween(durationMillis = 1000)` with `ReaderAnimation.NavSpringSpec`.
*   **Effect**: Tapping left/right will slide the page into place using the same force as the camera.

### C. PanelsReaderContent.kt (Panel Mode)
**Function**: `pagerState.animateScrollToPage(...)` inside navigation event collection.
*   **Change**: Replace `tween(durationMillis = 1000)` with `ReaderAnimation.NavSpringSpec`.
*   **Effect**: Moving to the next physical page while in panel mode will be perfectly synchronized with the camera's zoom/pan spring.

## 5. Verification
1.  **Phone vs. Tablet**: Perform side-by-side tests to ensure the "tempo" of the page turns feels identical despite the physical distance difference.
2.  **Continuous Tapping**: Tap quickly through several panels/pages to verify the animation doesn't "stutter" or reset awkwardly.
3.  **RTL Direction**: Verify springs correctly pull the page in the right direction for RTL reading.
