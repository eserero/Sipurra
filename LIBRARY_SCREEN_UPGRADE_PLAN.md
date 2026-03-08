# Library Screen Header and Navigation Upgrade Plan

This document outlines the plan to upgrade the Library screen header to Material 3 standards and improve the bottom navigation behavior.

## 1. Domain & Infrastructure Changes

### CommonSettingsRepository
Add persistence for the last selected library.
- Add `fun getLastSelectedLibraryId(): Flow<KomgaLibraryId?>` to `CommonSettingsRepository`.
- Add `suspend fun putLastSelectedLibraryId(libraryId: KomgaLibraryId?)` to `CommonSettingsRepository`.
- Implement these in:
    - `ExposedSettingsRepository` (SQLite implementation).
    - `LocalStorageSettingsRepository` (Wasm implementation).

## 2. ViewModel Updates

### LibraryViewModel
- Observe the `library` StateFlow and update the `CommonSettingsRepository` with the current `libraryId` whenever it changes (including `null` for "All Libraries").
- Ensure `libraryId` is correctly handled during initialization.

### MainScreenViewModel
- Add a StateFlow `lastSelectedLibraryId` that reads from the repository.
- Provide a navigation method that:
    - Navigates to `LibraryScreen(lastSelectedLibraryId)` if not already on the Library screen.
    - Does nothing if already on the Library screen.

## 3. UI Changes (Library Screen)

### LibraryToolBar Refactoring
Replace the existing `Row` in `LibraryScreen.kt` with a structured Material 3 header.

- **M3 Top App Bar**:
    - Use `TopAppBar` (or `CenterAlignedTopAppBar` depending on design preference).
    - **Navigation Icon**: Add an `IconButton` with `Icons.Rounded.Menu` that calls `mainScreenVm.toggleNavBar()`.
    - **Title**: Show the library name or "All Libraries".
    - **Actions**: Keep the existing 3-dots (overflow) menu on the right.

- **Segmented Buttons**:
    - Create a second row below the `TopAppBar` (within the same `Column` in `LibraryToolBar`).
    - Replace the `FilterChip` components with `SingleChoiceSegmentedButtonRow`.
    - Use `SegmentedButton` for:
        - "Series"
        - "Collections" (if count > 0)
        - "Read Lists" (if count > 0)
    - Ensure the row is non-scrollable and fills the width appropriately.

## 4. UI Changes (Main Screen)

### Bottom Navigation Bar
Update `AppNavigationBar` and `StandardBottomNavigationBar` in `MainScreen.kt`.

- **Selection State**:
    - Update `selected` parameter for the "Libraries" item: `selected = navigator.lastItem is LibraryScreen`.
- **Navigation Logic**:
    - Update `onClick`:
        - Retrieve the `lastSelectedLibraryId` from `MainScreenViewModel`.
        - Call `navigator.replaceAll(LibraryScreen(lastSelectedLibraryId))`.
        - Remove the call to `toggleLibrariesDrawer`.

## 5. Verification Plan

- **Navigation**:
    - Clicking "Libraries" in bottom nav should open the last viewed library.
    - "Libraries" button should be highlighted when viewing any library.
- **Header**:
    - Hamburger menu should open the library management drawer.
    - Library name should be clearly visible in the top bar.
    - Segmented buttons should correctly switch between Series, Collections, and Read Lists.
- **Persistence**:
    - Restarting the app and clicking "Libraries" should return to the last viewed library.

## 6. Additional Improvements

### Segmented Buttons Scrolling
Move the `SingleChoiceSegmentedButtonRow` from the static header to be part of the scrollable content of each tab.
- This ensures that as the user scrolls up, the segmented buttons disappear along with the "Keep Reading" section, providing more vertical space for the main content.
- Implement this by moving the segmented button row into the `LazyVerticalGrid` (or equivalent) of:
    - `BrowseTab` (Series list)
    - `CollectionsTab`
    - `ReadListsTab`

### Segmented Buttons Styling
- Use the app's accent color for the selected state of the `SegmentedButton`.
- Use `SegmentedButtonDefaults.colors()` and provide `activeContainerColor = LocalAccentColor.current`.

## 7. Final Refinements

### Header Elements Migration
Move the total count chips (series/collection/read list counts) and the page size selection dropdown into the `TopAppBar` actions.
- **Placement**: Elements should be placed to the left of the 3-dots overflow menu in the `TopAppBar`.
- **Order**: `[Total Count Chip] [PageSizeSelectionDropdown] [3-dots menu]`.
- **Elements**:
    - **Total Count Chip**: The `SuggestionChip` showing the total number of items (e.g., "123 series").
    - **Page Size Dropdown**: The `PageSizeSelectionDropdown` used to select how many items are viewable before paging.
- **Scope**: Apply this change to all three library tabs (Series, Collections, Read Lists).
- **Behavior**: These elements should be removed from the scrollable content area/secondary toolbar.

### Compact App Bar
Reduce the vertical space occupied by the `TopAppBar` to improve content visibility.
- Explore using `SmallTopAppBar` or manually adjusting the `TopAppBar` height/content padding if it feels too large.
- Ensure consistent styling across all screens using the new header.
