Plan: User Font Loading for Epub3 Reader                                                                                                                                                           
                                                                                                                                                                                                    
 Context                                                                                                                                                                                            
                                                                                                                                                                                                    
 The epub3 reader offers only 2 hardcoded fonts (Literata, OpenDyslexic) via a segmented button in the Font & Text settings tab. This plan adds the ability to load custom font files (TTF/OTF)
 which then appear alongside the built-in fonts in a dropdown.

 Font format for users: Recommend TTF — most universally available format. Every font site (Google Fonts, DaFont, Font Squirrel) offers TTF downloads. OTF also works; the picker accepts both.

 Architecture

 - Font storage: Reuse existing UserFontsRepository + UserFont domain classes (already used by TTSU reader). Fonts are shared across both readers.
 - Font serving to Readium: User font files are read as bytes, base64-encoded, and passed as data:font/ttf;base64,... URIs into Readium's CustomFont.uri field. This approach is reliable
 regardless of Readium's internal HTTP server mapping.
 - Font pick flow: FileKit file picker (ttf/otf) → UserFont.saveFontToAppDirectory() → UserFontsRepository.putFont() → update EpubView.pendingProps.customFonts → navigator re-initializes with new
  font registered.
 - Navigator restart behavior: Changing customFonts in EpubView.finalizeProps() triggers destroyNavigator() + initializeNavigator() (existing logic at EpubView.kt:194). The current locator is
 preserved via props!!.locator, so position is restored after the brief re-load. This is acceptable UX when loading a font from the settings card.

 Files to Modify

 1. Epub3ReaderState.kt

 komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderState.kt

 - Add fontsRepository: UserFontsRepository constructor parameter
 - Add val userFonts = MutableStateFlow<List<UserFont>>(emptyList()) property
 - In initialize(), after loading settings at line 195, add:
 userFonts.value = fontsRepository.getAllFonts()
 - In applySettingsToView() (line 118), add before view.finalizeProps():
 view.pendingProps.customFonts = userFonts.value.map { font ->
     val bytes = withContext(Dispatchers.IO) { font.getBytes() }
     val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
     val ext = font.path.name.substringAfterLast(".", "ttf")
     val mime = if (ext == "otf") "font/otf" else "font/ttf"
     CustomFont(uri = "data:$mime;base64,$b64", name = font.canonicalName, type = ext)
 }
 - Note: applySettingsToView must become a suspend fun (or move the data-URI building to a cached map updated when userFonts changes — see note below).
 - Add suspend fun loadFont(file: PlatformFile):
 val name = file.name.substringBeforeLast(".")
 val userFont = UserFont.saveFontToAppDirectory(name, file) ?: return
 fontsRepository.putFont(userFont)
 userFonts.value = fontsRepository.getAllFonts()
 applySettingsToView(settings.value)
 - Add suspend fun deleteFont(font: UserFont):
 fontsRepository.deleteFont(font)
 font.deleteFontFile()
 userFonts.value = fontsRepository.getAllFonts()
 applySettingsToView(settings.value)

 Performance note: Base64-encoding fonts in applySettingsToView is called on every settings change (theme, font size, etc.). Cache the encoded URIs in a Map<String, String> (keyed by font name)
 updated only when userFonts changes, and read from the cache in applySettingsToView.

 2. Epub3ReaderFactory.android.kt

 komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderFactory.android.kt

 fontsRepository is already received as a parameter (line 28) but is NOT forwarded to Epub3ReaderState. Add it to the Epub3ReaderState(...) call.

 3. Epub3SettingsCard.kt

 komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3SettingsCard.kt

 - Add params to Epub3SettingsCard: userFonts: List<UserFont>, onLoadFont: (PlatformFile) -> Unit, onDeleteFont: (UserFont) -> Unit
 - Pass these through to FontTextTab
 - In FontTextTab, replace the hardcoded segmented button block (lines 362–378) with:
   - ExposedDropdownMenuBox showing:
       - Built-in entries: "Literata", "OpenDyslexic"
     - User font entries: each showing font.name with a small delete icon button
   - "Load font…" TextButton or OutlinedButton below the dropdown
   - Use rememberFilePickerLauncher(type = FileKitType.File(listOf("ttf", "otf"))) { file -> file?.let { onLoadFont(it) } } for the button

 4. Epub3ReaderContent.android.kt

 komelia-ui/src/androidMain/kotlin/snd/komelia/ui/reader/epub/Epub3ReaderContent.android.kt

 - Collect epub3State.userFonts.collectAsState()
 - Pass userFonts, onLoadFont = { file -> scope.launch { epub3State.loadFont(file) } }, onDeleteFont = { font -> scope.launch { epub3State.deleteFont(font) } } to Epub3SettingsCard

 No Changes Needed

 - EpubView.kt — customFonts: List<CustomFont> is already wired through Props → FinalizedProps → navigator re-init
 - EpubFragment.kt — already iterates lst.props!!.customFonts and calls addSource(it.uri) (lines 115–123)
 - UserFont.kt, UserFontsRepository — no changes, reuse as-is
 - Database layer — fonts already persisted by ExposedUserFontsRepository

 Verification Steps

 1. Build and install the app
 2. Open an epub3 book, open reader settings → Font & Text tab
 3. Verify dropdown shows "Literata" and "OpenDyslexic"
 4. Tap "Load font…" → file picker opens, navigate to a .ttf file → tap it
 5. Font name appears in the dropdown; select it → book text re-renders in the custom font
 6. Close the book and reopen → custom font is still selected and available
 7. Open settings → delete the custom font → it disappears from dropdown, reader falls back to default
 8. Verify TTSU reader also sees the loaded font (shared repository)