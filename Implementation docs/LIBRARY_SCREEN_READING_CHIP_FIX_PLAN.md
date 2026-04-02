# Fix Library Screen UI Feedback

This plan details the changes required to address the user's feedback on the Library screen improvements.

## Objective
1. Add a checkmark (`Icons.Default.Done`) to the `FilterChip` components when selected, conforming to Material 3 guidelines.
2. Remove the rounded edges and margins from the `ContinueReadingSection` background, making it span the full width of the screen, and restore the edge-to-edge horizontal scrolling.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/library/LibraryScreen.kt`: UI for the library screen where `LibraryTabChips` and `ContinueReadingSection` reside.

## Implementation Steps

### 1. Fix `FilterChip` Selection State
- **`LibraryScreen.kt`**:
  - Add imports:
    - `androidx.compose.material.icons.filled.Done`
    - `androidx.compose.material3.FilterChipDefaults`
  - In `LibraryTabChips`, update each `FilterChip` (Series, Collections, Read Lists, and Reading) to include the `leadingIcon` parameter:
    ```kotlin
    leadingIcon = if (selectedCondition) {
        {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize)
            )
        }
    } else null
    ```

### 2. Fix `ContinueReadingSection` Layout
- **`LibraryScreen.kt`**:
  - Add imports:
    - `androidx.compose.ui.layout.layout`
    - `androidx.compose.ui.platform.LocalDensity`
    - `androidx.compose.ui.graphics.RectangleShape`
  - In `ContinueReadingSection`, update the `Surface` to use a custom `layout` modifier to negate the parent padding (`10.dp`), remove the `RoundedCornerShape`, and use `RectangleShape`:
    ```kotlin
    val gridPadding = 10.dp
    val density = LocalDensity.current

    Surface(
        modifier = Modifier
            .layout { measurable, constraints ->
                val insetPx = with(density) { gridPadding.roundToPx() }
                val placeable = measurable.measure(
                    constraints.copy(maxWidth = constraints.maxWidth + insetPx * 2)
                )
                layout(constraints.maxWidth, placeable.height) {
                    placeable.place(-insetPx, 0)
                }
            }
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RectangleShape,
        color = containerColor,
    )
    ```
  - Update the padding inside the section to align with the grid padding:
    - The `Text` header gets `padding(start = gridPadding, end = gridPadding, bottom = 12.dp)`.
    - The `LazyRow` gets `contentPadding = PaddingValues(horizontal = gridPadding)` and `horizontalArrangement = Arrangement.spacedBy(7.dp)`.

## Verification
- Verify that a checkmark icon appears inside the "Reading", "Series", "Collections", and "Read Lists" chips when selected.
- Verify that the "Continue Reading" background spans the entire width of the screen, with no margins or rounded corners.
- Verify that the horizontal scrolling of the "Continue Reading" books goes seamlessly to the edge of the screen.
