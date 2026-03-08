# Material 3 Alignment Plan: Navigation, FABs, and Menus - COMPLETED Feb 2026

This document outlines the plan to align the core navigation, Floating Action Buttons (FABs), and Dropdown Menus across the Komelia application with the Material 3 (M3) specification.

## 1. Bottom Navigation Bar Alignment (New UI Mode Only) - DONE

### Target Files & Functions
1.  **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/MainScreen.kt`**
    *   **Modify**: `MobileLayout(navigator, vm)` to use a single `Scaffold` for both UI modes.
    *   **Delete**: `PillBottomNavigationBar(...)` and `PillNavItem(...)` composables.
    *   **Create**: `AppNavigationBar(navigator, toggleLibrariesDrawer)` using M3 `NavigationBar`.
2.  **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/MobileSettingsScreen.kt`**
    *   **Modify**: `Content()` to remove the bottom `Spacer` and `windowInsetsBottomHeight`.
3.  **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/itemlist/SeriesLists.kt`**
    *   **Modify**: `SeriesLazyCardGrid(...)` to review and likely remove/reduce the `65.dp` bottom content padding in `LazyVerticalGrid` to rely on `Scaffold` padding.
4.  **`komelia-ui/src/commonMain/kotlin/snd/komelia/ui/home/HomeContent.kt`**
    *   **Modify**: `DisplayContent(...)` to remove the manual `50.dp` bottom padding in `LazyColumn` and `LazyVerticalGrid`.

### Implementation Details

#### 1.1 Unify `MobileLayout` in `MainScreen.kt`
Refactor `MobileLayout` to use a single `Scaffold` regardless of the UI mode. This ensures consistent anchoring and proper window inset handling via `PaddingValues`.

*   **Scaffold structure**:
    ```kotlin
    val isImmersiveScreen = navigator.lastItem is SeriesScreen || 
                            navigator.lastItem is BookScreen || 
                            navigator.lastItem is OneshotScreen
    
    Scaffold(
        bottomBar = {
            if (!isImmersiveScreen) {
                if (useNewLibraryUI) {
                    AppNavigationBar(
                        navigator = navigator,
                        toggleLibrariesDrawer = { coroutineScope.launch { vm.toggleNavBar() } }
                    )
                } else {
                    StandardBottomNavigationBar(
                        navigator = navigator,
                        toggleLibrariesDrawer = { coroutineScope.launch { vm.toggleNavBar() } },
                        modifier = Modifier
                    )
                }
            }
        }
    ) { paddingValues ->
        ModalNavigationDrawer(
            drawerState = vm.navBarState,
            drawerContent = { LibrariesNavBar(vm, navigator) },
            content = {
                Box(Modifier.padding(paddingValues).consumeWindowInsets(paddingValues).statusBarsPadding()) {
                    // Apply AnimatedContent/CurrentScreen logic here
                }
            }
        )
    }
    ```
*   **Removal**: Delete `PillBottomNavigationBar` and `PillNavItem`.

#### 1.2 Implement `AppNavigationBar` (M3 Expressive)
*   **Component**: Use `androidx.compose.material3.NavigationBar`.
*   **Styling**: 
    *   Set `alwaysShowLabel = true`.
    *   Use `LocalNavBarColor.current` for `containerColor`.
*   **Items & Icons (Rounded variants)**:
    1.  **Libraries**: `Icons.Rounded.LocalLibrary`. Toggles side drawer via `vm.toggleNavBar()`.
    2.  **Home**: `Icons.Rounded.Home`. `navigator.replaceAll(HomeScreen())`. Selected if `lastItem is HomeScreen`.
    3.  **Search**: `Icons.Rounded.Search`. `navigator.push(SearchScreen(null))`. Selected if `lastItem is SearchScreen`.
    4.  **Settings**: `Icons.Rounded.Settings`. **CRITICAL**: Use `navigator.push(MobileSettingsScreen())` (NOT `navigator.parent!!.push`). This keeps the bottom bar visible. Selected if `lastItem is SettingsScreen`.

#### 1.3 `MobileSettingsScreen.kt` Cleanup
*   **Function**: `Content()`
*   **Change**: Delete `Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))` and any hardcoded bottom padding in the main `Column`. The `Scaffold`'s `paddingValues` in `MainScreen` will manage this.

#### 1.4 Padding Cleanup in Lists
*   **`SeriesLists.kt`**: In `SeriesLazyCardGrid`, reduce `bottom = navBarBottom + 65.dp` in `contentPadding` to rely on the parent `Scaffold` padding.
*   **`HomeContent.kt`**: In `DisplayContent`, remove `contentPadding = PaddingValues(bottom = 50.dp)` from `LazyColumn` and `LazyVerticalGrid`.

### Changes included:
*   **Exclusion**: Immersive screens (`SeriesScreen`, `BookScreen`, `OneshotScreen`) hide the bar.
*   **Anchoring**: The bar is anchored to the bottom using `Scaffold`.
*   **UI Isolation**: `StandardBottomNavigationBar` remains unchanged for users with "New UI" disabled.

---

## 2. Floating Action Buttons (FABs) - DONE

### Target
*   **File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/immersive/ImmersiveDetailFab.kt`
*   **Current State**: Custom split-pill design for "Read Now" and "Incognito".
*   **M3 Target**: Separate into standard M3 FAB components (Expressive update).
*   **Changes**:
    *   "Read Now": **`ExtendedFloatingActionButton`** with `Icons.AutoMirrored.Rounded.MenuBook`.
        *   **Color**: Use `LocalNavBarColor.current ?: MaterialTheme.colorScheme.primaryContainer`.
        *   **Shape**: Default M3 Squircle (`large`).
    *   "Incognito": **`FloatingActionButton`** with `Icons.Rounded.VisibilityOff`.
        *   **Color**: Default M3 FAB color (`PrimaryContainer`).
        *   **Shape**: Default M3 Squircle (`large`).
    *   "Download": **`FloatingActionButton`** with `Icons.Rounded.Download`.
        *   **Color**: Default M3 FAB color (`PrimaryContainer`).
        *   **Shape**: Default M3 Squircle (`large`).
    *   Ensure proper spacing and alignment at the bottom of the immersive screen.

---

## 3. Dropdown Menus (Action Menus) - DONE

### Targets
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/menus/SeriesActionsMenu.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/menus/BookActionsMenu.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/menus/OneshotActionsMenu.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/menus/LibraryActionsMenu.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/menus/CollectionActionsMenu.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/menus/ReadListActionsMenu.kt`

### M3 Target
*   Standardize visual hierarchy with **leading icons** and M3 typography.

### Changes
*   **Leading Icons**: Added icons to all items (e.g., Edit, Delete, Mark as Read, Analyze).
*   **Typography**: Use `MaterialTheme.typography.labelLarge`.
*   **Colors**: Use `MaterialTheme.colorScheme.error` for destructive actions via `MenuItemColors`.
*   **Cleanup**: Removed manual hover background overrides in favor of standard M3 states.

---

## 4. Card & Toolbar Triggers - DONE

### Targets
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/SeriesItemCard.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/BookItemCard.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/SeriesImageCard.kt`
*   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/cards/BookImageCard.kt`

### Changes
*   Ensure all 3-dot triggers use `IconButton` with `Icons.Rounded.MoreVert`.
*   Verify 48dp touch targets for all menu triggers.

---

## 5. Verification Plan - DONE
1.  **Navigation**: Ensure the M3 NavigationBar appears only when "New UI" is ON and is hidden on immersive screens.
2.  **FABs**: Verify the new FAB layout doesn't overlap content and respects accent colors.
3.  **Menus**: Confirm icons are aligned and destructive actions are correctly colored.
