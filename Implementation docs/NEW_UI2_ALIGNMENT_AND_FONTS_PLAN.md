# NEW UI 2 ALIGNMENT AND FONTS PLAN

Align the new headers and chips with the thumbnail lists in both Library and Home screens, and update the minor header fonts to Inter.

## Objective
- Align `LibraryHeaderSection` and `LibraryTabChips` with thumbnails in the Library screen.
- Align `HomeHeaderSection` with thumbnails/sections in the Home screen.
- Update "Keep Reading", "Browse", and Home section headers to use the `Inter` font while maintaining `titleLarge` size.
- Ensure all headers are aligned with the left side of the thumbnails.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/library/LibraryScreen.kt`: `LibraryHeaderSection` and `LibraryTabChips`.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/list/SeriesListContent.kt`: `LibrarySectionHeader`.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/home/HomeContent.kt`: `HomeHeaderSection`, `SectionHeader`, and grid-mode headers.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/itemlist/SeriesLists.kt`: Thumbnail grid padding context (`10.dp` for UI 2).

## Implementation Steps

### 1. Library Screen Alignment
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/library/LibraryScreen.kt`
    - In `LibraryHeaderSection`, change `Modifier.padding(horizontal = 16.dp, vertical = 12.dp)` to `Modifier.padding(vertical = 12.dp)`.
    - In `LibraryTabChips`, change `contentPadding = PaddingValues(horizontal = 16.dp)` to `contentPadding = PaddingValues(horizontal = 0.dp)`.
    - *Note:* These are already inside the grid which has `10.dp` padding, so `0.dp` here aligns them at `10.dp`.

### 2. Library Section Headers Font and Alignment
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/series/list/SeriesListContent.kt`
    - In `LibrarySectionHeader`:
        - Change `Modifier.padding(horizontal = 10.dp, vertical = 4.dp)` to `Modifier.padding(vertical = 4.dp)`.
        - Define `inter` font family using `Res.font.Inter_SemiBold`.
        - Apply `fontFamily = inter` and `fontWeight = FontWeight.SemiBold` to the `Text` style.
    - *Note:* `LibrarySectionHeader` is used for "Keep Reading" and "Browse".

### 3. Home Screen Alignment and Font Updates
- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/home/HomeContent.kt`
    - In `HomeHeaderSection`, change `Modifier.padding(horizontal = 16.dp, vertical = 12.dp)` to `Modifier.padding(horizontal = 10.dp, vertical = 12.dp)`.
    - Define `inter` font family using `Res.font.Inter_SemiBold`.
    - In `SectionHeader`:
        - Change `MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)` to use `fontFamily = inter` and `fontWeight = FontWeight.SemiBold`.
        - Keep `horizontal = 10.dp` padding as it aligns with `SectionRow`.
    - In `BookFilterEntry` and `SeriesFilterEntries` (grid mode headers):
        - Apply the same `inter` font and `SemiBold` weight to the headers.
        - Ensure they align with thumbnails (they already have `0.dp` horizontal padding inside a `20.dp` padded grid).

## Verification & Testing
- Build and run the app.
- Navigate to the Library screen and verify that the "Library Name", "Tabs (Series/Collections/etc)", "Keep Reading", and "Browse" headers are all perfectly aligned with the left edge of the thumbnails.
- Navigate to the Home screen and verify that the "Home" header and section headers ("Recently Added", etc.) are aligned with the thumbnails.
- Verify that the font for "Keep Reading", "Browse", and Home section headers is `Inter` (matching the series counter) but the size remains `titleLarge`.
