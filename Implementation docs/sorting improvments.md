# Library Default Sorting & Sort Dropdown Implementation Plan

## Objective
Change the default sorting of the library Series tab to "recently added" (`DATE_ADDED_DESC`) and introduce a new sorting dropdown UI component next to the thumbnail size (page size) dropdown.

## Key Files & Context
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/library/LibrarySeriesTabState.kt`: Contains the initialization of `filterState` where the default sort is defined.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/library/LibraryScreen.kt`: Contains the main library UI, including the `LibraryHeaderSection` and the `TopAppBar` actions where the thumbnail size dropdown is located.

## Implementation Steps

1. **Change Default Sort (`LibrarySeriesTabState.kt`)**
   - Locate the `filterState` initialization (around line 81).
   - Change `defaultSort = SeriesSort.TITLE_ASC` to `defaultSort = SeriesSort.DATE_ADDED_DESC`.

2. **Create SortSelectionDropdown Composable (`LibraryScreen.kt` or a shared component file)**
   - Add a private `@Composable fun LibrarySortDropdown(currentSort: LibrarySeriesTabState.SeriesSort, onSortChange: (LibrarySeriesTabState.SeriesSort) -> Unit)` to `LibraryScreen.kt`.
   - Implement it using an `IconButton` with `Icons.Default.FilterList` as the icon.
   - The dropdown should open a `DropdownMenu` iterating over `LibrarySeriesTabState.SeriesSort.entries`.
   - For each option, render a `DropdownMenuItem` showing the label via `LocalStrings.current.seriesFilter.forSeriesSort(it)`.
   - Apply a background color (`MaterialTheme.colorScheme.secondaryContainer`) and text color (`MaterialTheme.colorScheme.onSecondaryContainer`) to the currently selected option to visually indicate it, identical to how it's done in `PageSizeSelectionDropdown`.

3. **Integrate Dropdown into LibraryHeaderSection (`LibraryScreen.kt`)**
   - Update the `LibraryHeaderSection` signature to optionally accept `sortOrder: LibrarySeriesTabState.SeriesSort? = null` and `onSortChange: ((LibrarySeriesTabState.SeriesSort) -> Unit)? = null`.
   - Find where `PageSizeSelectionDropdown` is called inside `LibraryHeaderSection`.
   - Wrap it in a `Row(verticalAlignment = Alignment.CenterVertically)`.
   - Inside the row, if `sortOrder` and `onSortChange` are not null, place the new `LibrarySortDropdown` followed by a small `Spacer(Modifier.width(8.dp))` before the `PageSizeSelectionDropdown`.

4. **Pass Sort State in SeriesTabContent (`LibraryScreen.kt`)**
   - In `SeriesTabContent`, collect the filter state: `val currentFilter = seriesTabState.filterState.state.collectAsState().value`.
   - Update the `LibraryHeaderSection` call within `SeriesTabContent` to pass `sortOrder = currentFilter.sortOrder` and `onSortChange = seriesTabState.filterState::onSortOrderChange`.

5. **Update TopAppBar Actions (`LibraryScreen.kt`)**
   - Locate the `actions = { ... }` block for the `TopAppBar` (around line 630).
   - Inside the `if (totalCount != 0)` check, just before `PageSizeSelectionDropdown(pageSize, onPageSizeChange)`, add the `LibrarySortDropdown`.
   - To do this, check if `currentTab == SERIES`. If so, get the sort order using `val currentFilter = seriesTabState.filterState.state.collectAsState().value` and pass it to `LibrarySortDropdown` along with `seriesTabState.filterState::onSortOrderChange`.

## Verification & Testing
- Open the application and navigate to the library.
- Verify that the default sorting for the series list is now "recently added" (Date Added Descending).
- Ensure a filter icon is present to the left of the grid/thumbnail size icon in the library header/toolbar.
- Click the filter icon to open the dropdown and verify all sorting options are listed, with the active one highlighted.
- Select a different sort option and verify that the library content re-sorts correctly and the dropdown highlights the newly selected item upon reopening.
