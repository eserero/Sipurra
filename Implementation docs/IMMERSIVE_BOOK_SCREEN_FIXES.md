# Plan: Immersive Screen Bug Fixes

## Objective
Fix layout and styling issues reported in the new immersive book/series screen redesign:
1. Ensure the morphing cover image extends correctly under the Android status bar without a white/black gap.
2. Fix the large gap between text and description when the cover is expanded by not reserving space for the collapsed thumbnail upfront.
3. Align the small thumbnail's styling (shadow depth) and corners with other thumbnails in the app.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/MainScreen.kt`: Manages root layout insets and padding for all screens.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/immersive/ImmersiveDetailScaffold.kt`: Houses the morphing image logic and elevation.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/book/immersive/ImmersiveBookContent.kt`: Layout for Book.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/immersive/ImmersiveSeriesContent.kt`: Layout for Series.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/oneshot/immersive/ImmersiveOneshotContent.kt`: Layout for Oneshot.

## Implementation Steps

### 1. Fix Status Bar Padding (MainScreen.kt)
The `statusBarsPadding()` modifier on the root `Box` prevents any content from drawing behind the status bar.
- **Action**: Remove the unconditional `.statusBarsPadding()` from the outer `Box` in `MobileLayout`.
- **Action**: Wrap the `screen.Content()` call with a `Box` that applies `.statusBarsPadding()` only if the screen is *not* one of the immersive screens.

### 2. Remove Pre-reserved Space (Content Files)
The header `Box` currently has its `heightIn(min = ...)` tied to the thumbnail height, which reserves space even when the card is collapsed.
- **Action**: In `ImmersiveBookContent.kt`, `ImmersiveSeriesContent.kt`, and `ImmersiveOneshotContent.kt`, update the thumbnail container `Box` to have its layout height strictly controlled by `thumbnailHeight * expandFraction`.
- **Action**: Ensure the `ThumbnailImage` itself maintains its full size via `requiredSize` so it can be sampled/positioned correctly by the morphing overlay, but use `.graphicsLayer { alpha = if (expandFraction > 0.99f) 1f else 0f }` to keep it invisible until fully morphed.

### 3. Match Thumbnail Styling (Scaffold and Content)
Standard thumbnails in Komelia (e.g., in Series lists) use `2.dp` shadow and `8.dp` corners.
- **Action**: In `ImmersiveDetailScaffold.kt`, change the target `currentElevation` from `lerp(0.dp, 6.dp, ...)` to `lerp(0.dp, 2.dp, ...)`.
- **Action**: In all content files, change the static thumbnail shadow from `6.dp` to `2.dp`.

## Verification & Testing
- **Status Bar**: Open a book on Android; verify the cover image is flush with the top edge and visible behind the status bar icons.
- **Layout Gap**: When the cover is expanded (collapsed state of the scrollable content), verify there is no large gap between the title and the description.
- **Styling**: Verify the thumbnail has a subtle shadow consistent with the rest of the app's UI.
