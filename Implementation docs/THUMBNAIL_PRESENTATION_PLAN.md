# Plan: Improved Thumbnail Presentation

## Objective
Enhance the visual presentation of thumbnails across the application (Library, Home, Series, etc.) by implementing rounded corners on all sides, a theme-adaptive semi-transparent text layer with fixed height, and Material 3 aligned elevation for a "hovering" effect.

## Technical Constraints
- Changes must only apply when text is rendered **inside** the thumbnail (i.e., when `LocalCardLayoutBelow.current` is `false`).
- The text layer must have a fixed height regardless of content length (2 lines of text).
- Text must be aligned to the top of this layer.
- Shapes and shadows must align with Material 3 guidelines.

## Key Files
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/ItemCard.kt`: Base card logic and shared overlay components.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/BookItemCard.kt`: Book-specific image overlay.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/SeriesItemCard.kt`: Series-specific image overlay.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/CollectionItemCard.kt`: Collection-specific image overlay.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/ReadListItemCard.kt`: Read list-specific image overlay.

## Implementation Steps

### 1. Update Base Card (`ItemCard.kt`)

#### A. Rounded Corners and Elevation
Modify the `ItemCard` composable:
- Update the `shape` calculation: When `cardLayoutBelow` is `false`, use `RoundedCornerShape(8.dp)` for all corners instead of just the top.
- Update `CardDefaults.cardColors` and add `elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)` to the `Card` to provide the "hovering" shadow.
- Update `imageShape`: Ensure it also uses `RoundedCornerShape(8.dp)` for all corners when `cardLayoutBelow` is `false`.

#### B. New Overlay Components
Create or update shared overlay components in `ItemCard.kt`:
- **`CardTextBackground`**: A new composable for the semi-transparent layer.
  ```kotlin
  @Composable
  fun CardTextBackground(modifier: Modifier = Modifier) {
      Box(
          modifier = modifier
              .fillMaxWidth()
              .height(60.dp) // Fixed height for ~2 lines + padding
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
      )
  }
  ```
- **`CardTopGradient`**: Update or create a top gradient to ensure white icons (like the unread tick) remain visible on light thumbnails.
  ```kotlin
  @Composable
  fun CardTopGradient() {
      Box(
          Modifier.fillMaxWidth().height(40.dp)
              .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)))
      )
  }
  ```

### 2. Update Book Thumbnails (`BookItemCard.kt`)
Modify `BookImageOverlay`:
- Replace `CardGradientOverlay()` with the new `CardTopGradient()`.
- Wrap the bottom section (containing titles and progress bar) in a `Box` that includes `CardTextBackground()`.
- Ensure the `Column` containing the text has a fixed `height(60.dp)` and `verticalArrangement = Arrangement.Top`.
- Ensure the `LinearProgressIndicator` (if present) is anchored to the very bottom of this fixed-height area.

### 3. Update Series Thumbnails (`SeriesItemCard.kt`)
Modify `SeriesImageOverlay`:
- Similarly replace the gradient and wrap the bottom text in `CardTextBackground()`.
- Use a fixed-height `Column` (60.dp) with `Arrangement.Top`.
- Handle the `Unavailable` text gracefully within the fixed-height constraints.

### 4. Update Collection and Read List Thumbnails
Modify `CollectionImageOverlay` and `ReadListImageOverlay` in their respective files:
- Apply the same pattern: semi-transparent background, fixed 60.dp height, and top-aligned text.

### 5. Shared Typography Alignment
Ensure all `CardOutlinedText` or `Text` calls inside these overlays are using standard `MaterialTheme.typography` styles that contrast well with the `surface` color at 60% alpha.

## Verification Checklist
- [ ] Thumbnails in grid view have 8.dp rounded corners on all four sides.
- [ ] A subtle shadow is visible beneath the cards (M3 elevation).
- [ ] The text background color changes correctly between Light and Dark themes.
- [ ] One-line titles are aligned to the top of the 60.dp background layer.
- [ ] The background layer height is identical across all cards in the grid.
- [ ] Layout remains unchanged when the "text below thumbnail" setting is enabled.
