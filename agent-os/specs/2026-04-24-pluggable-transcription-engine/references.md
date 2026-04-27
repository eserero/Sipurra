# Reference Implementations

## Model Download Pattern
- Interface: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/OnnxModelDownloader.kt`
- Android impl: `komelia-domain/core/src/androidMain/kotlin/snd/komelia/updates/AndroidOnnxModelDownloader.kt`
  - Uses `updateClient.streamFile(url)` which accepts a lambda receiving `HttpResponse`
  - Progress emitted as `UpdateProgress(total, downloaded, label)`

## Settings Repository Pattern
- Interface: `komelia-domain/core/src/commonMain/kotlin/snd/komelia/settings/ImageReaderSettingsRepository.kt`
- Exposed impl: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/settings/ExposedImageReaderSettingsRepository.kt`
- DB table: `komelia-infra/database/sqlite/src/commonMain/kotlin/snd/komelia/db/tables/ImageReaderSettingsTable.kt`

## Model Download UI Pattern (NCNN)
- Content composable: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/imagereader/ncnn/NcnnSettingsContent.kt`
- DownloadDialog: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/imagereader/onnxruntime/DownloadDialog.kt`

## Settings Navigation
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/settings/navigation/SettingsNavigationMenu.kt`
  - Add entry after "Epub Reader" in the "App Settings" group

## ViewModelFactory
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/ViewModelFactory.kt`
- Add `transcriptionSettingsViewModel()` function following existing patterns
