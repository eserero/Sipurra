# Library UI Changes — `feature/library-ui`

## Overview

This branch adds a new library UI layer on top of `main`. All new UI behaviour is
controlled by a single **"New library UI"** toggle in **Settings → Appearance**.
The toggle defaults to **ON** for all installs and DB upgrades.

The branch is intentionally narrow: it contains **no immersive detail screens**,
no adaptive card colour, and no hero panels. Those live on `feature/ui-improvements`.

---

## What Changed

### 1. Floating Pill Nav Bar (mobile)

| Toggle | Behaviour |
|--------|-----------|
| ON  | Floating pill-shaped bottom nav bar overlays content; background colour follows the "Nav Bar Color" picker |
| OFF | Original M3 `Scaffold`-based nav bar with a `HorizontalDivider` at the top |

`MainScreen.kt` — `MobileLayout()` branches on `LocalUseNewLibraryUI`:
- **ON** → `Box` + floating `PillBottomNavigationBar` + `statusBarsPadding()`
- **OFF** → `Scaffold` + `StandardBottomNavigationBar` (restored verbatim from `main`)

### 2. Keep Reading Panel (library screen)

A horizontal scroll strip of in-progress books appears at the top of the library
series list, above the Browse section header.

| Toggle | Behaviour |
|--------|-----------|
| ON  | Shows "Keep Reading" header + `LazyRow` of in-progress books, then "Browse" header |
| OFF | Shows only the existing toolbar (no Keep Reading, no Browse header) |

Files: `SeriesListContent.kt`, `LibrarySeriesTabState.kt`, `LibraryViewModel.kt`,
`ViewModelFactory.kt`.

`LibrarySeriesTabState` loads up to 20 in-progress books on initialise via
`loadKeepReadingBooks()` using `KomgaReadStatus.IN_PROGRESS` sorted by last-read date.

### 3. Home Screen — Horizontal Section Rows

| Toggle | Behaviour |
|--------|-----------|
| ON + "All" filter | `LazyColumn` where each section (Keep Reading, On Deck, …) is a header + horizontal `LazyRow` |
| ON + specific filter | `LazyVerticalGrid` for that one section |
| OFF | Original `LazyVerticalGrid` showing all/filtered items together |

File: `HomeContent.kt`.

### 4. Pill-Shaped Filter Chips

| Toggle | Behaviour |
|--------|-----------|
| ON  | `RoundedCornerShape(50%)` — full pill |
| OFF | `FilterChipDefaults.shape` — M3 default (small rounded rect) |

`AppFilterChipDefaults.shape()` is now a `@Composable` function that reads
`LocalUseNewLibraryUI` and returns the appropriate shape. Callers in
`LibraryScreen.kt` and `HomeContent.kt` were updated from `.shape` to `.shape()`.

### 5. Nav Bar Color Picker & Accent Color Picker

Two new `FlowRow` color-swatch pickers in **Settings → Appearance**:
- **Nav Bar Color** — 6 presets (Auto / Charcoal / Teal / Navy / Forest / Violet)
- **Accent Color** — 12 presets — tints filter chips and tab chips

The pickers are only visible when the toggle is ON (they have no effect with the
standard nav bar).

Persisted in DB (migration V13, column `nav_bar_color` / `accent_color`).
Provided as `LocalNavBarColor` / `LocalAccentColor` composition locals from `MainView`.

### 6. ExtraBold Section Headers

Section headers in both the home screen and the library series list use
`FontWeight.ExtraBold` with no horizontal divider line.

| Toggle | Where |
|--------|-------|
| ON  | Home screen section headers + "Keep Reading" / "Browse" headers in library |
| OFF | Original home screen grid headers (ExtraBold styling still applies when specific filter is selected) |

### 7. "New Library UI" Master Toggle

Added to **Settings → Appearance** at the top of the section:
- Label: **"New library UI"**
- Description: *"Floating nav bar, Keep Reading panel, and pill-shaped tabs"*
- When OFF: Nav Bar Color and Accent Color pickers are hidden

---

## Files Touched

### Settings / Database Infrastructure

| File | Change |
|------|--------|
| `komelia-domain/core/.../settings/CommonSettingsRepository.kt` | Added `getNavBarColor`, `putNavBarColor`, `getAccentColor`, `putAccentColor`, `getUseNewLibraryUI`, `putUseNewLibraryUI` |
| `komelia-infra/database/shared/.../AppSettings.kt` | Added `navBarColor: Long?`, `accentColor: Long?`, `useNewLibraryUI: Boolean = true` |
| `komelia-infra/database/shared/.../SettingsRepositoryWrapper.kt` | Implemented the 6 new methods above |
| `komelia-infra/database/sqlite/.../AppSettingsTable.kt` | Added `nav_bar_color`, `accent_color`, `use_new_library_ui` columns |
| `komelia-infra/database/sqlite/.../ExposedSettingsRepository.kt` | Read/write the 3 new columns |
| `komelia-infra/database/sqlite/.../migrations/AppMigrations.kt` | Added V13 and V14 entries |
| `komelia-infra/database/sqlite/.../migrations/V13__ui_colors.sql` | `ALTER TABLE AppSettings ADD COLUMN nav_bar_color TEXT` + `accent_color TEXT` |
| `komelia-infra/database/sqlite/.../migrations/V14__new_library_ui.sql` | `ALTER TABLE AppSettings ADD COLUMN use_new_library_ui INTEGER NOT NULL DEFAULT 1` |

### UI Layer

| File | Change |
|------|--------|
| `komelia-ui/.../ui/CompositionLocals.kt` | Added `LocalNavBarColor`, `LocalAccentColor`, `LocalUseNewLibraryUI` |
| `komelia-ui/.../ui/MainView.kt` | Collects all 3 settings flows and provides them as composition locals |
| `komelia-ui/.../ui/MainScreen.kt` | `MobileLayout` branches on toggle: pill nav (ON) vs standard nav (OFF). Both nav implementations live side-by-side |
| `komelia-ui/.../ui/home/HomeContent.kt` | Horizontal section rows (ON) vs vertical grid (OFF); ExtraBold headers; pill chips |
| `komelia-ui/.../ui/library/LibraryScreen.kt` | Pill chip shape + border wired up; Keep Reading params passed to `SeriesListContent` |
| `komelia-ui/.../ui/library/LibrarySeriesTabState.kt` | Added `keepReadingBooks` state + `loadKeepReadingBooks()` + `bookMenuActions()` |
| `komelia-ui/.../ui/library/LibraryViewModel.kt` | Passes `bookApi` down to `LibrarySeriesTabState` |
| `komelia-ui/.../ui/ViewModelFactory.kt` | Passes `bookApi` to `LibraryViewModel` |
| `komelia-ui/.../ui/series/list/SeriesListContent.kt` | Keep Reading strip gated by toggle; "Browse" header gated by toggle |
| `komelia-ui/.../ui/common/components/DescriptionChips.kt` | `AppFilterChipDefaults.shape` changed from `val` to `@Composable fun shape()` that returns pill or default based on toggle |
| `komelia-ui/.../ui/settings/appearance/AppSettingsScreen.kt` | Wires `useNewLibraryUI` / `onUseNewLibraryUIChange` to content |
| `komelia-ui/.../ui/settings/appearance/AppSettingsViewModel.kt` | Added `useNewLibraryUI` state + `onUseNewLibraryUIChange()` |
| `komelia-ui/.../ui/settings/appearance/AppearanceSettingsContent.kt` | Toggle switch at top; color pickers hidden when OFF; `Switch` import added |

---

## Toggle Gate Reference

```
LocalUseNewLibraryUI.current
```

Used in:
- `MainScreen.kt` — which nav bar layout to use
- `HomeContent.kt` — whether `DisplayContent` uses `LazyColumn` rows or `LazyVerticalGrid`
- `SeriesListContent.kt` — whether Keep Reading strip and Browse header are shown
- `DescriptionChips.kt` (`AppFilterChipDefaults.shape()`) — pill vs default chip shape
- `AppearanceSettingsContent.kt` — whether color pickers are shown

---

## DB Migration History (this branch)

| Version | File | Purpose |
|---------|------|---------|
| V13 | `V13__ui_colors.sql` | Add `nav_bar_color`, `accent_color` columns |
| V14 | `V14__new_library_ui.sql` | Add `use_new_library_ui` column (DEFAULT 1 = new UI ON) |

Existing installs upgrading from `main` will get `use_new_library_ui = 1` automatically
via the `DEFAULT 1` on the SQL `ALTER TABLE`, so the new UI is ON for everyone on upgrade.

---

## What Is NOT in This Branch

- Immersive detail layouts (`ImmersiveBookContent`, `ImmersiveSeriesContent`, `ImmersiveOneshotContent`)
- Adaptive card colour (`LocalUseAdaptiveCardColor`, `AdaptiveCardColor.*`)
- DB migrations V15+ (adaptive card colour from `feature/ui-improvements`)
- Hero panels, blurred backgrounds, read-progress overlays on book/oneshot screens
- Oneshot screen redesign
