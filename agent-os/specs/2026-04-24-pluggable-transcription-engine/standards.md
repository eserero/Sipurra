# Relevant Standards

## Compose UI / ViewModels
- ViewModels use `StateScreenModel` from Voyager (`cafe.adriel.voyager.core.model.StateScreenModel`)
- State is a sealed class or data class exposed as `StateFlow`
- Screens are `object` or `data class` implementing `cafe.adriel.voyager.core.screen.Screen`

## Settings Screens
- Settings content is split: `*Screen.kt` (Voyager Screen) + `*ViewModel.kt` + `*Content.kt` (composable)
- ViewModel is created via `ViewModelFactory` and retrieved with `getScreenModel()`
- Settings are persisted via `ExposedRepository` (SQLite + Flyway-style migrations)

## Dialogs
- Reuse `DownloadDialog` from `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/imagereader/onnxruntime/DownloadDialog.kt`

## Database Migrations
- SQL files go in `komelia-infra/database/sqlite/src/commonMain/composeResources/files/migrations/app/`
- Named `V{N}__description.sql` — next is V47
- Kotlin table objects go in `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/`

## Native / JNI
- CMake build wired via `externalNativeBuild` in `build.gradle.kts`
- JNI sources in `src/main/cpp/`
