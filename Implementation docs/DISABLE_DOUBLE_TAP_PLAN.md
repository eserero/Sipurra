# Implementation Plan: Disable Double-Tap to Zoom

## Goal
Allow users to disable the double-tap gesture to zoom. This eliminates the system's wait-time for a second tap, making the single-tap (for page/panel turns) feel instantaneous.

---

## 1. Database Migration
**File**: `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/V17__reader_tap_settings.sql`
```sql
ALTER TABLE ImageReaderSettings ADD COLUMN tap_to_zoom BOOLEAN NOT NULL DEFAULT 1;
```

**File**: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/migrations/AppMigrations.kt`
- Add `"V17__reader_tap_settings.sql"` to the `migrations` list.

---

## 2. Persistence Layer Updates

**File**: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/ImageReaderSettingsTable.kt`
```kotlin
    val tapToZoom = bool("tap_to_zoom").default(true)
```

**File**: `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/ImageReaderSettings.kt`
```kotlin
    val tapToZoom: Boolean = true,
```

**File**: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/ImageReaderSettingsRepository.kt`
```kotlin
    fun getTapToZoom(): Flow<Boolean>
    suspend fun putTapToZoom(enabled: Boolean)
```

**File**: `komelia-infra/database/shared/src/commonMain/kotlin/snd/komelia/db/repository/ReaderSettingsRepositoryWrapper.kt`
- Implement `getTapToZoom` and `putTapToZoom`.

**File**: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedImageReaderSettingsRepository.kt`
- Map `tapToZoom` in `get()` and `save()`.

---

## 3. State Management
**File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt` (since it applies to multiple modes)
- Load `tapToZoom` from repository.
- Provide a `onTapToZoomChange(Boolean)` handler.

---

## 4. UI Layer (Settings)
Add a "Tap to zoom" toggle to the reading mode settings for both **Paged** and **Panels** modes.

**Files**:
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/BottomSheetSettingsOverlay.kt` (Mobile)
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/SettingsSideMenu.kt` (Desktop)

---

## 5. Gesture Integration
**File**: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`
- Modify `ReaderControlsOverlay` to accept a `tapToZoom: Boolean` parameter.
- Update `detectTapGestures`:
```kotlin
detectTapGestures(
    onTap = { ... },
    onDoubleTap = if (tapToZoom) { offset -> ... } else null
)
```
