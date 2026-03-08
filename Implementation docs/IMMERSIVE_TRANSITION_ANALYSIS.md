# Analysis: Immersive Detail Screen Smoothness and Flickering

## 1. Problem Description
Users experience visual instability when navigating to a book or oneshot page in immersive mode from a series page. The issues include:
*   **Visual "Jumps"**: The content card moves inconsistently during the opening animation.
*   **Layering Issues**: The cover image appears above the content card briefly before the card "pops" to the front.
*   **Image Flickering**: The image seems to load once, then "flashes" or reloads shortly after the screen opens.

These issues do not occur when opening the immersive series screen from the Library or Home screens.

---

## 2. 1-to-1 Comparison: Why Series Works and Books Don't

| Feature | Series Transition (Working) | Book Transition (Buggy) |
| :--- | :--- | :--- |
| **Layout Level** | Root of the screen. | Nested inside a **HorizontalPager**. |
| **Constraints** | Uses stable screen height. | Recalculates height every frame due to morphing. |
| **Data Flow** | Data is static for the screen. | **Asynchronous**: Pager re-layouts after opening. |
| **Crossfade** | Managed by `SeriesThumbnail`. | **Hardcoded to `true`** in the scaffold. |

---

## 3. Implementation Plan: Replicating Series Smoothness

To make the book transition behave identically to the working series transition, the following changes are required:

### I. Stabilize Layout Constraints (Fixes the "Jump")
The current scaffold applies `sharedBounds` to a `BoxWithConstraints`. As the shared transition morphs the thumbnail into the full screen, the `maxHeight` changes every frame, causing the card's position (calculated as `0.65 * maxHeight`) to jump.
*   **Fix**: Move the `sharedBounds` modifier from the `BoxWithConstraints` to the inner `Box`. This ensures the layout logic always sees the full stable screen height, while the contents still morph visually.

### II. Synchronize Pager State (Fixes the "Flicker")
The series screen is smooth because it is static. The book screen flickers because the `HorizontalPager` starts with 1 item and then "jumps" or reloads once the full list of books arrives from the server.
*   **Fix**: Update `SeriesScreen` to pass the currently loaded list of books to the `BookScreen` during navigation. This allows the pager to initialize with the correct page count and index from frame one, making it as stable as the series screen.

### III. Explicit Layering (Fixes the Layering Issue)
When using shared transitions, elements are rendered in a top-level overlay. Standard composition order can be ignored in this mode.
*   **Fix**: Apply explicit `.zIndex(0f)` to the background image and `.zIndex(1f)` to the content card in `ImmersiveDetailScaffold`. This forces the card to remain on top of the image throughout the entire animation.

### IV. Disable Redundant Animations (Fixes the Image Flash)
During a shared transition, the image is already being visually morphed from the source thumbnail. If the destination image also performs its own `crossfade`, it creates a visual "double load" flash.
*   **Fix**: Update the scaffold to disable the `ThumbnailImage` crossfade whenever a shared transition is active, matching the logic already used in the working series thumbnail.

### V. Transition Key Alignment (Fixes Oneshots)
Navigating to a oneshot from a book list currently fails to trigger a shared transition because of an ID mismatch (Source uses `bookId`, Destination uses `seriesId`).
*   **Fix**: Update `ImmersiveOneshotContent` to use the `bookId` for its transition key when navigated to from a book list context.
