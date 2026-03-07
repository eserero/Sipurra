# Implementation Plan: Material 3 Settings Visual Upgrade

## Objective
Improve the visual appearance of the Settings navigation menu on Android/Mobile by adopting Material 3 "Grouped List" patterns. Related settings should be visually grouped into card-like containers with rounded corners, utilizing M3 `ListItem` and standard dividers.

## Target Files
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/navigation/SettingsNavigationMenu.kt`

## Proposed Architectural Changes

### 1. New Helper Components
Create two private or internal helper composables within `SettingsNavigationMenu.kt` to encapsulate the new styling:

#### `SettingsGroup`
- **Purpose**: A container for a logical set of settings.
- **Parameters**: `title: String?`, `content: @Composable ColumnScope.() -> Unit`.
- **Styling**:
    - If a title is provided, display it above the "card" using `MaterialTheme.typography.titleSmall` with appropriate padding.
    - Wrap the `content` in a `Surface`.
    - **Shape**: `RoundedCornerShape(12.dp)` or `16.dp` (standard M3 Medium/Large).
    - **Color**: `MaterialTheme.colorScheme.surfaceContainer` (or `surfaceContainerLow/High` depending on desired contrast).
    - **Padding**: Add vertical spacing between groups (e.g., `16.dp`).

#### `SettingsListItem`
- **Purpose**: A styled wrapper around the Material 3 `ListItem` component.
- **Parameters**: `label: String`, `onClick: () -> Unit`, `isSelected: Boolean`, `trailingContent: @Composable (() -> Unit)? = null`.
- **Styling**:
    - Use `ListItem` from the M3 library.
    - **Headline**: `Text(label, style = MaterialTheme.typography.bodyLarge)`.
    - **Colors**: 
        - Default background: `Color.Transparent` (as it sits on the `SettingsGroup` Surface).
        - Selected state: Use `ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)` to highlight the active item.
    - **Interactions**: Ensure a consistent `56.dp` minimum height and standard M3 ripple effects.

### 2. Refactoring `SettingsNavigationMenu`
Refactor the main `Column` in `SettingsNavigationMenu` to use the new grouping logic. Remove the existing `HorizontalDivider` calls between groups and replace the flat list with the following structure:

- **Group: App Settings**
    - Appearance
    - Image Reader
    - Epub Reader (conditional)
    - Updates (conditional)
    - Offline Mode
- **Group: User Settings** (conditional)
    - My Account
    - My Authentication Activity
- **Group: Server Settings** (conditional/admin only)
    - General
    - Users
    - Authentication Activity
    - Media Management
    - Announcements
- **Group: Komf Settings** (conditional/admin only)
    - Connection
    - Processing
    - Providers
    - Notifications
    - Job History
- **Group: Actions**
    - Log Out

### 3. Inner Dividers
Inside each `SettingsGroup`, add a `HorizontalDivider` between `SettingsListItem`s. 
- **Constraint**: Do not place a divider after the last item in a group to maintain the clean card look.

## Visual Requirements
- **Consistency**: The implementation must work on both Mobile and Desktop. On Desktop, the `SettingsGroup` will provide a clean, segmented look to the sidebar.
- **Theming**: Use `MaterialTheme.colorScheme` tokens exclusively to ensure perfect dark/light theme support.
- **Spacing**: Ensure adequate outer padding so cards don't touch the screen edges on mobile.

## Verification Steps
1. Verify that settings items are clearly grouped into separate cards.
2. Confirm that dividers only appear between items *within* a group.
3. Check that the "Selected" state correctly highlights the active screen using the M3 secondary container color.
4. Toggle dark mode to ensure all surface colors and text remain legible.

## Improvements (New Task)

### 1. Inset Dividers
The horizontal dividers within each `SettingsGroup` must be **inset** (not reach the edges of the card). Use `Modifier.padding(horizontal = 16.dp)` on the `HorizontalDivider`.

### 2. Card Color Alignment
The `SettingsGroup` card colors must align with the application's "auto" color system (based on FAB default colors):
- **Dark Mode**: Use `Color(red = 43, green = 43, blue = 43)` (matches FAB default from Light Mode).
- **Light Mode**: Use `MaterialTheme.colorScheme.primaryContainer` (matches FAB default from Dark Mode).

### 3. Height Consistency
Ensure consistent `SettingsListItem` height across platforms:
- **Mobile**: `56.dp`
- **Desktop**: `48.dp`
