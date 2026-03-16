# Plan: Thumbnail Presentation Improvements - Visual Polish and Original Style Restoration

## Objective
1.  **Increase Overlay Opacity**: Change the semi-transparent text background opacity from `0.6f` to `0.8f` for better readability.
2.  **Restore Original Text Style**: When the overlay background is turned OFF, the text must revert to its original style: white color with a subtle shadow to ensure readability against any thumbnail background.

## Technical Context
-   Setting: `LocalCardLayoutOverlayBackground.current` (Boolean).
-   Target Files: `ItemCard.kt`, `BookItemCard.kt`, `SeriesItemCard.kt`, `CollectionItemCard.kt`, `ReadListItemCard.kt`.

## Implementation Steps

### 1. Update Overlay Opacity
In `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/ItemCard.kt`:
-   Modify `CardTextBackground` to use `alpha = 0.8f` instead of `0.6f`.

### 2. Implement Conditional Text Styling
In each item card (`BookItemCard.kt`, `SeriesItemCard.kt`, etc.), update the text rendering logic in the overlay area:

```kotlin
val overlayBackground = LocalCardLayoutOverlayBackground.current
val textColor = if (overlayBackground) MaterialTheme.colorScheme.onSurface else Color.White
val secondaryTextColor = if (overlayBackground) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f)
val textStyle = if (overlayBackground) {
    MaterialTheme.typography.bodyMedium // or relevant style
} else {
    MaterialTheme.typography.bodyMedium.copy(
        shadow = Shadow(
            color = Color.Black,
            offset = Offset(1f, 1f),
            blurRadius = 4f
        )
    )
}
```

#### Specific File Requirements:

-   **`BookItemCard.kt`**: Update `BookImageOverlay`. Apply styling to Book Title, Series Title, and the "Unavailable" label.
-   **`SeriesItemCard.kt`**: Update `SeriesImageCard` and `SeriesSimpleImageCard`. Apply styling to Series Title, Book Count, and "Unavailable" label.
-   **`CollectionItemCard.kt`**: Update `CollectionImageCard`. Apply styling to Collection Name and Item Count.
-   **`ReadListItemCard.kt`**: Update `ReadListImageCard`. Apply styling to Read List Name and Item Count.

### 3. Verification
-   Toggle "Card layout overlay background" in Settings -> Appearance.
-   **Overlay ON**: Verify background is 80% opaque; text uses theme-adaptive colors (Black/White depending on Light/Dark mode).
-   **Overlay OFF**: Verify background is transparent; text is White with a subtle shadow/glow for readability.
