# New UI 2 - User-Facing Features Summary

This branch implements a major visual and functional overhaul of the Komelia application, primarily focused on the "New UI 2" experience.

### Core Visual Overhaul
*   **New UI 2 Gating:** A comprehensive redesign of the Library, Home, and Search screens, featuring a modern top app bar and updated item card layouts.
*   **Frosted Glass (Haze) Effect:** Implementation of real-time blur/frosted glass effects on navigation bars, top app bars, and reader controls for a more modern, integrated aesthetic.
*   **Modern Themes:** Introduction of "Light Modern" and "Dark Modern" themes specifically designed for transparent status and navigation bars.
*   **Typography:** System-wide upgrade to the Inter font family with refined header alignments.

### Immersive Screen Enhancements
*   **Hero Text Morphing:** A new "flying" animation where the cover image morphs seamlessly into the thumbnail position when expanding the immersive Book, Series, or Oneshot screens.
*   **Standalone Morphing Toggle:** A dedicated "Morphing Immersive Cover" setting allows users to enable this animation independently of the global "New UI 2" flag.

### Reader Improvements (EPUB & Image)
*   **Edge-to-Edge UI:** Readers now support a fully immersive, edge-to-edge experience with translucent, frosted glass control bars.
*   **Configurable Margins:** EPUB reader adds adjustable top and bottom margins (0-20%) to better accommodate various screen shapes and user preferences.
*   **Improved Navigation:** System back button support for closing overlays and exiting the reader.

### Library & Discovery
*   **Reading Filter Chip:** A new dedicated "Reading" filter in the library screen to quickly access ongoing books.
*   **Refined "Continue Reading":** Polished the "Continue Reading" section with a full-width design and optimized spacing.
*   **Improved Iconography:** Updated visuals for "Downloaded" indicators on books and series for better clarity.

### Audio Player
*   **Mini Player Redesign:** The audio mini-player has been updated with larger corner radii and a wider layout to match the "New UI 2" design language.

---

## Commit: 314ea888720d004ee06dad5ff8865c590c32af1e

feat(ui): improve downloaded icon visuals for books and series

---

## Commit: f0af542588de23b916dba78a0a2f98ba8a8a23c1

feat(settings): decouple morphing cover from New UI 2

- introduce 'Morphing Immersive Cover' standalone setting
- add database migration (V33) for new settings
- add toggle to Appearance settings and immersive screen 3-dot menus
- update ImmersiveDetailScaffold and immersive content to use new setting

---

## Commit: 7c06bbdb9e93cd5992e2e1db43e6f147f52e1241

fix(ui): epub reader haze effect and reader UI refinements

- Epub3ReaderContent: fix 'wide rectangle' disruption in haze effect by adding a background Spacer inside hazeSource to cover margins.
- Epub3ControlsCard: replace SuggestionChip with Button for chapter navigation.
- BottomSheetSettingsOverlay: adjust max height of the settings content area to 2/3 of screen height.

---

## Commit: cb13407079cbcb1f619515afa49abe93fc810eb0

fix(ui): epub fullscreen toggle, settings card style, slider colors, and reading chip polish

- Epub3ReaderContent: restore legacy fullscreen toggle (show/hide controls) for both UI1 and UI2
- Epub3SettingsCard: use SecondaryTabRow with transparent container and background color for better frosted glass compatibility; flatten surface elevation
- Slider: use onSurfaceVariant for inactive track/tick so it's visible on frosted glass backgrounds
- LibraryScreen: ContinueReadingSection bleed to full width with RectangleShape; tighten item spacing; align padding with grid; add Done icon to active Reading filter chip

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 909dee1a66aa73471e73b87c9c9a55d9b7a0af3e

feat(library): add 'Reading' filter chip and refine 'Continue Reading' section

---

## Commit: 6a60bf942e46bcb81e10d38e26f91eaae65c8149

feat(epub-reader): add configurable top and bottom margins

- Add 'topMargin' and 'bottomMargin' to Epub3NativeSettings.
- Implement database migration (V32) to persist the new margin settings.
- Update Epub3SettingsCard with sliders for top and bottom margins (0-20% screen height).
- Apply margin padding unconditionally in Epub3ReaderContent.
- Add IMMERSIVE_UI2_CLIPPING_FIX_PLAN implementation doc.
- Fix collapsed offset in ImmersiveDetailScaffold when using morphing cover.

---

## Commit: f40457e29c25a18ac76ff30299c298ac3e76208e

fix(epub-reader): remove blocking overlay and redundant gesture delay

- Remove full-screen blocking overlay in Epub3ReaderContent to allow gestures to reach the reader while controls are visible.
- Remove redundant 400ms delay in Epub3ReaderState's onMiddleTouch, improving control panel responsiveness.
- Update onMiddleTouch to immediately close controls, settings, or TOC if any are open.
- Add implementation plan for EPUB3 reader gesture and overlay fix.

---

## Commit: 9c74102f30f5a42752f532eeba417c0206fc32bd

feat(ui): update audio mini player and progress slider for New UI 2

- Update AudioMiniPlayer to match ReaderControlsCard width and rounding (28.dp) when 'New UI 2' is enabled.
- Implement thumbnail preview for image reader progress slider in 'New UI 2' mode.
- Fix clipping in ReaderControlsCard to allow floating previews.
- Add implementation plans for audio player improvements and clipping fixes.

---

## Commit: ab834cae3c08acb9e2ed4ca3bc5b2b9406d3dc8d

feat(epub-reader): implement edge-to-edge UI with frosted glass bars and update documentation

- Implement 'New UI 2' edge-to-edge mode for EPUB reader.
- Add and update implementation plans for New UI improvements.
- Add example reader HTML for New UI.
- Update EpubView to support conditional insets padding.

---

## Commit: d2b7ee016bc8d813706221cb32af7148b4ef374e

fix(ui): add system back button support to image reader

- Integrate BackPressHandler in ReaderContent to handle Android system back events
- Improve onKeyEvent for Key.Back and Key.Escape to consistently manage overlay visibility (context menu, settings, help dialog) before exiting

---

## Commit: 1177ac48695eaf722875e8c11bfea265b7ede4dc

fix(ui): resolve double-rendering and improve Haze effect in reader controls

- Refactor ReaderControlsCard to use Box + clip + shadow instead of Surface when Haze is active to avoid tonal elevation tints
- Move controls scrim inside hazeSource in Epub3ReaderContent to allow blurring of the dimmed background
- Remove EpubView padding in new UI to ensure content flows behind bars for the frosted glass effect
- Reduce haze tint opacity for better transparency

---

## Commit: 833abd328dc0a6c1a5e9a8dedf8131500f4f4963

feat(ui): implement new reader UI (UI2) for EPUB and image readers

- Add common ReaderTopBar and ReaderControlsCard components
- Implement Epub3ControlsCardNewUI with integrated progress slider and settings
- Implement ImageReaderControlsCardNewUI with reader mode switching and progress
- Integrate Haze effect (frosted glass) in reader screens for improved aesthetics
- Update BottomSheetSettingsOverlay and SettingsContent to support the new UI

---

## Commit: c777b28059e2ac62ef2bf576422c0a24a76c0291

fix(ui): frosted glass, status bar immersion, and chip layout in NewTopAppBar screens

- MainScreen: skip statusBarsPadding() when isModernNewTopBar so NewTopAppBar
  can extend into the Android status bar area
- LibraryScreen/HomeScreen: add per-screen hazeState with hazeSource on inner
  content Box as sibling of NewTopAppBar (Haze requires hazeEffect to be a
  sibling overlay of hazeSource, not a descendant)
- LibraryScreen: wrap newUI2BeforeContent (LibraryHeaderSection + LibraryTabChips)
  in Column to fix overlap in LazyVerticalGrid BoxScope
- HomeContent: wrap topContent (HomeHeaderSection + Toolbar) in Column for
  the same reason

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: e147cc0222be70bc1927ed85c021ebfb7899943b

feat(ui): align headers with thumbnails and update fonts to Inter

- Align Library and Home headers with the thumbnail list
- Update Keep Reading, Browse, and Home section headers to use Inter SemiBold font
- Include implementation plan for alignment and fonts

---

## Commit: b798b28e89d6632e2fca70abe88b2ce146799ae2

feat(ui): apply HazeEffect and UI polish across multiple screens

This commit adds HazeState support to various screens and components (Home, Library, Immersive views, Item Cards) to enable frosted glass effects.

---

## Commit: 6d4b6f68a96918e94c08b98f3b3bf2b839a256f9

feat(ui): apply hazeEffect frosted glass to library top app bar

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 917d787f6f1bb2a0ea1eb0dc7fc73fab3f81458e

feat(ui): apply hazeEffect frosted glass to navigation bar

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: e600deee5f2afdba3e27a492785c4d5285fa131f

feat(ui): wire HazeState into MobileLayout content area

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 2385be12552dbae449cc8729ee8c708ea7dca040

feat(ui): add LocalHazeState composition local

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 03f43412ef10add65c6dd04389fbfead60afefb2

build: add haze library for frosted glass blur effect

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 8db36559d6106a091640e62ccbbea35a746d88f1

feat(ui): add Light Modern and Dark Modern themes with transparent bar infrastructure

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 6fb4eab4e9dc8a677d47235182d2dc27d3597f49

fix(ui): improve immersive screen layout and hero text handling

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

---

## Commit: 94568a79130f88a0fd27edd248c62d7e521fa22d

feat(ui): implement hero text morphing for immersive screens in New UI 2

---

## Commit: 9940a95909f794dc7fe71b4a234a79efb5b3c7c1

feat(ui): implement New UI 2 with thumbnail font upgrade and immersive fixes

- Add 'New UI 2' toggle in appearance settings with database persistence.
- Upgrade thumbnail typography to match Stitch UI design using Inter and Noto Serif.
- Bundle Inter-SemiBold.ttf and NotoSerif-Bold.ttf as compose resources.
- Update LibraryItemCard to use Noto Serif for titles and Inter for metadata when New UI 2 is enabled.
- Refactor immersive screens (Book, Series, Oneshot) to support New UI 2 layout changes.
- Add comprehensive implementation plans and HTML design examples.

---
