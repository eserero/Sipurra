# Proposal: Adaptive Library Card Layout

## Objective
Introduce a new layout option for library items (Books, Series, Collections, Read Lists) that places text metadata below the thumbnail in a structured Material 3 Filled Card, improving readability and providing a more traditional "bookshelf" aesthetic.

## 1. User Interface Changes

### Appearance Settings
- **New Toggle**: `Card Layout`
- **Options**: 
    - `Overlay` (Current default): Text appears on top of the thumbnail with a gradient overlay.
    - `Below`: Text appears in a dedicated area below the thumbnail.
- **Description**: "Show title and metadata below the thumbnail instead of on top."
- **Preview**: The card size slider preview in the settings will adapt to show the selected layout.

### Card Design (`Below` Layout)
- **Dimensions**:
    - **Width**: Strictly aligned to the `cardWidth` setting (same as `Overlay` layout).
    - **Height**: Calculated dynamically based on the thumbnail's aspect ratio plus the fixed height of the text area.
- **Container**: 
    - **Type**: Material 3 Filled Card.
    - **Colors**: `surfaceContainerHighest` for the container, following Material 3 guidelines for both Light and Dark themes.
    - **Corners**: The card container will have rounded corners on all four sides (M3 standard, typically 12dp).
- **Thumbnail**:
    - Fills the top, left, and right edges of the card.
    - Maintains the existing 0.703 aspect ratio.
    - **Corners**: Rounded corners **only on the top** to match the card's top profile; bottom of the thumbnail remains square where it meets the text area.
- **Text Area**:
    - Located directly beneath the thumbnail.
    - **Title**: Maximum of 2 lines, ellipsis on overflow.
    - **Padding**: 8dp to 10dp padding around text elements to ensure M3 spacing standards.

## 2. Technical Implementation

### Persistence (`komelia-domain` & `komelia-infra`)
- Add `cardLayoutBelow` to `AppSettings` data class in `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/AppSettings.kt`.
- **Database Migrations**:
    - Add `V19__card_layout_below.sql` migration for SQLite (Android/Desktop) in `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/`.
    - Update `AppSettingsTable.kt` in `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/` to include the new column.
    - Update `ExposedSettingsRepository.kt` in `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/` to map the new column to the `AppSettings` object.
- Update `CommonSettingsRepository` and `SettingsRepositoryWrapper` to handle this new preference.

### UI State (`komelia-ui`)
- Define `LocalCardLayoutBelow` in `CompositionLocals.kt`.
- Update `MainView.kt` to collect the setting and provide it to the composition.
- Enhance `ItemCard` in `ItemCard.kt` to act as the layout engine:
    - If `Overlay`: Use existing `Box` structure.
    - If `Below`: Use `Column` with thumbnail followed by a content area.

### Component Updates
- **SeriesItemCard.kt**: 
    - Extract `SeriesImageOverlay` logic.
    - Pass title and unread count to `ItemCard`'s content slot when in `Below` mode.
- **BookItemCard.kt**:
    - Ensure the read progress bar and "unread" indicators are correctly positioned.
    - Handle book titles and series titles (if enabled) in the text area.
- **Other Cards**: Apply similar changes to `CollectionItemCard.kt` and `ReadListItemCard.kt`.

## 3. Implementation Phases
1.  **Phase 1: Domain & Infrastructure**: Update settings storage and repository layers.
2.  **Phase 2: Settings UI**: Add the toggle to the Appearance settings screen and ViewModel.
3.  **Phase 3: Base Component Refactoring**: Update `ItemCard` to support dual-layout switching.
4.  **Phase 4: Content Implementation**: Update Series, Book, Collection, and Read List cards to fill the "Below" layout content slot.
5.  **Phase 5: Visual Polish**: Finalize padding, M3 colors, and layout constraints (max 2 lines).

## 4. Default Behavior
- The default will remain as the `Overlay` layout to preserve the current user experience until explicitly changed by the user.
