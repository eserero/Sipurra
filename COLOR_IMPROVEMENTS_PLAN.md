# Color Improvements Plan

This document outlines the changes required to unify the navigation bar colors, adjust settings screen card backgrounds, and update button colors to respect the app's accent color.

## 1. Unify Main Navigation Bar Color
**Goal:** The navigation bar should consistently use the theme's gray (`surfaceVariant`) across all screens in both light and dark themes, instead of switching between black, white, and gray.

- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/MainScreen.kt`
- **Target:** `MobileLayout` and `AppNavigationBar` components.
- **Changes:**
    - In `MobileLayout`, update the `AppNavigationBar` call.
    - Currently, it uses a conditional check: `if (isImmersiveScreen) MaterialTheme.colorScheme.surfaceVariant else LocalNavBarColor.current ?: MaterialTheme.colorScheme.surface`.
    - **New Logic:** It should always default to `MaterialTheme.colorScheme.surfaceVariant` if `LocalNavBarColor.current` is not set.
    - Ensure `StandardBottomNavigationBar` (used in the older UI layout) also aligns with this color scheme if applicable.

## 2. Settings Screen Card Background (Light Theme)
**Goal:** In light theme, the settings screen card backgrounds are currently too dark (`primaryContainer`). They should be updated to match the lighter gray used for cards in other parts of the app (like the search screen).

- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/navigation/SettingsNavigationComponents.kt`
- **Target:** `SettingsGroup` component.
- **Changes:**
    - Update the `containerColor` logic.
    - **Current Logic:** `val containerColor = if (theme.type == Theme.ThemeType.DARK) Color(43, 43, 43) else MaterialTheme.colorScheme.primaryContainer`.
    - **New Logic:** For light theme, use `MaterialTheme.colorScheme.surfaceVariant`. 
    - Note: `surfaceVariant` in the light theme is defined as `Color(240, 240, 240)`, which is lighter and matches the M3 standard for cards better than the current `primaryContainer` (212, 212, 212).

## 3. Read Buttons Accent Color
**Goal:** The "Read" and "Read Incognito" buttons in the book list view (e.g., in the immersive series screen) should use the user-selected accent color instead of the hardcoded tertiary (gold/orange) color.

- **File:** `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/common/BookReadButton.kt`
- **Target:** `BookReadButton` component.
- **Changes:**
    - Access `LocalAccentColor.current`.
    - **Container Color:** Use the accent color if available. If not, fallback to a sensible default like `MaterialTheme.colorScheme.primaryContainer` or `MaterialTheme.colorScheme.secondary`.
    - **Content Color:** Instead of a hardcoded color, calculate the content color based on the container's luminance to ensure the text ("Read") and icon remain readable.
    - **Logic Example:**
      ```kotlin
      val accentColor = LocalAccentColor.current
      val containerColor = accentColor ?: MaterialTheme.colorScheme.tertiaryContainer
      val contentColor = if (accentColor != null) {
          if (containerColor.luminance() > 0.5f) Color.Black else Color.White
      } else {
          MaterialTheme.colorScheme.onTertiary
      }
      ```

## Summary of Impact
- **Consistency:** The bottom navigation bar will feel like a stable part of the UI regardless of the screen being viewed.
- **Aesthetics:** Light theme settings will look cleaner and less "heavy."
- **Personalization:** One of the most prominent action buttons in the app will now correctly reflect the user's chosen theme accent.
