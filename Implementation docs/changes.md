# git changes
commit 515f711eecfd41e3719754094b8625bf9adeb244
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 8 03:39:48 2026 +0200

    feat(library): upgrade header to Material 3 and improve navigation
    
    - Migrate library count chips and page size selection to TopAppBar actions.
    - Implement persistence for the last selected library across app restarts.
    - Refactor library navigation to use the last viewed library by default.
    - Replace FilterChips with SingleChoiceSegmentedButtonRow for tab switching.
    - Clean up redundant toolbars and UI components in library content views.
    - Add support for immersive card colors based on cover dominant color.
    - Add migration scripts for library persistence and immersive color settings.

commit 1a035c06ac07fd9dd7f61ca37fcdbc8cfdba5512
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 8 01:25:41 2026 +0200

    feat(settings): refactor navigation and improve reader image upscaling
    
    - Refactor settings navigation menu into dedicated components for better maintainability.
    - Enhance Android NCNN upscaler and ReaderImage implementation for better performance and stability.
    - Add MATERIAL_3_SETTINGS_UPGRADE_PLAN.md.
    - Fix filter chip unselected text color to use theme default (onSurface).

commit 0e05fad110848046820e9108317f9753cf4ce449
Author: eserero <eserero@hotmail.com>
Date:   Sat Mar 7 20:59:38 2026 +0200

    fix(search): prevent crash and improve tab visibility on empty results

commit 929ccfdbf28da49749e90bdabb91ffa31dde9287
Author: eserero <eserero@hotmail.com>
Date:   Sat Mar 7 18:26:33 2026 +0200

    fix(reader): resolve coordinate mismatch and crash during panel upscaling
    
    - Ensure PanelsReaderState uses consistent image dimensions from PanelData for scaling
    - Fix Android crash by keeping VipsImage as source during pre-emptive upscaling
    - Eliminate race condition caused by double-updating originalSize flow

commit 18fde3fd7dce02687006f190c7746c4d1cace2b8
Author: eserero <eserero@hotmail.com>
Date:   Fri Mar 6 21:46:38 2026 +0200

    feat(series-filter): implement dialog-based author filter for mobile
    
    Replace the author filter dropdown with a centralized AppDialog to improve the mobile experience. The new FilterDialogMultiChoiceWithSearch matches the Tag Filter UI's look and feel, including integrated search/reset, chip-based selection, and consistent styling.

commit 0ff503390eb59814808391d80a84327f2b12510f
Author: eserero <eserero@hotmail.com>
Date:   Fri Mar 6 17:10:30 2026 +0200

    feat(android): add RealSR and Real-ESRGAN support to NCNN upscaler
    
    - Integrated RealSR engine into the native upscaler module
    - Added high-quality realsr-general-v3 (4x) and real-esrganv3-anime-x2 (2x) models
    - Updated JNI layer to handle unified RealSR/Real-ESRGAN engine types
    - Enhanced Kotlin layer with dynamic scale detection and model path construction
    - Hardened native RealSR implementation with null checks to prevent SIGSEGV on load failure
    - Updated Settings UI with new engine options and localized labels

commit 9bdcb3109c16b703d928fae3ebcc8b6d82922080
Author: eserero <eserero@hotmail.com>
Date:   Thu Mar 5 21:49:17 2026 +0200

    feat(android): implement high-performance NCNN GPU upscaler
    
    - Added :komelia-infra:ncnn-upscaler module with JNI support for Waifu2x and RealCUGAN
    - Integrated NCNN upscaler into the Android image loading pipeline
    - Implemented pre-emptive upscaling for low-resolution images
    - Added comprehensive settings UI for upscaler configuration on Android
    - Hardened integration with a global singleton and thread-safe JNI layer to prevent Vulkan context conflicts

commit 80da02470942565d3c93956632793f070c98d777
Author: eserero <eserero@hotmail.com>
Date:   Thu Mar 5 02:17:28 2026 +0200

    feat(library): replace inline filter with ExtendedFAB + ModalBottomSheet
    
    Move the series filter trigger from a toolbar IconButton to a persistent
    ExtendedFAB at bottom-right. Filter fields now open in an M3
    ModalBottomSheet with a scrollable content area and a sticky footer
    containing Reset/Hide FABs. The filter FAB fades out when the sheet is
    open and disappears in edit mode. The FilterList icon turns yellow and
    the Reset FAB uses the accent color when a filter is active.
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit b2c5c0b8a5be1f13b6fc5288078d084d20683cbf
Author: eserero <eserero@hotmail.com>
Date:   Thu Mar 5 01:10:00 2026 +0200

    feat(reader): long-press to save page to Downloads
    
    Long-pressing anywhere on a reader image shows an M3 AnimatedDropdownMenu
    anchored to the press position with a \"Save image\" action. Tapping it
    fetches the raw page bytes via the Komga API, detects the image format
    (jpg/png/webp), and writes the file to the platform Downloads folder with
    a sanitized filename like BookName_p003.jpg.
    
    Platform implementations:
    - Android: MediaStore.Downloads (API 29+, no permissions required)
    - Desktop JVM: ~/Downloads/ via java.io.File
    - wasmJs: no-op stub
    
    Works in paged, continuous, and panels reader modes.
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit c5ba1ad2a30f42f11a665366c37483cb3fe92b76
Author: eserero <eserero@hotmail.com>
Date:   Thu Mar 5 00:11:49 2026 +0200

    feat(home): replace MoreVert button with settings FAB
    
    Move the home screen filter-edit trigger from an IconButton pinned to
    the filter-chip row into a Material 3 FloatingActionButton at the
    bottom-right corner, matching the reader's settings FAB style (accent
    color, RoundedCornerShape(16.dp)).
    
    Also fix PullToRefreshBox missing fillMaxSize, which caused the FAB to
    briefly appear near the top of the screen on first render.
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit 7a19845d840e48ba362e8d6149039ff1e6d54819
Author: eserero <eserero@hotmail.com>
Date:   Tue Mar 3 19:35:26 2026 +0200

    feat(search): M3 SearchBar with spacing fix and SecondaryTabRow selector
    
    - Replace old SearchTextField on mobile with M3 SearchBar (state-driven
      API) that animates expand/collapse, shows back arrow when expanded,
      and shows clear button when query is non-empty
    - Add 8dp padding (start/end/top) to the Column wrapper so the bar has
      breathing room from screen edges and the status bar
    - Replace FilterChip pair in SearchToolBar with SecondaryTabRow + Tab
      for consistency with the rest of the app's binary-toggle pattern
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit dd631974d91ddec75533a81151c1f765e4f318d4
Author: eserero <eserero@hotmail.com>
Date:   Tue Mar 3 19:17:06 2026 +0200

    feat(reader): add tap navigation mode setting
    
    Adds a configurable tap navigation mode for the image reader:
    - New ReaderTapNavigationMode enum: LEFT_RIGHT, RIGHT_LEFT, HORIZONTAL_SPLIT, REVERSED_HORIZONTAL_SPLIT
    - DB migration V20 + Exposed table/repository plumbing
    - NavigationSettings composable with radio buttons, per-mode description subtitles, and a TapNavigationDiagram canvas that labels zones with Prev/Next/Menu
    - Navigation tab moved to the middle slot (between Reading mode and Image settings) in the bottom sheet overlay
    - Fix smart-cast on delegated imageSize in PagedReaderContent
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit f4f30f907b205429626d0118f7b0dc15f9581152
Author: eserero <eserero@hotmail.com>
Date:   Tue Mar 3 09:33:07 2026 +0200

    feat(ui): refine shared transitions, animated menus, and appearance settings
    
    - Introduce AnimatedDropdownMenu for M3-style enter/exit animations.
    - Replace standard DropdownMenu with AnimatedDropdownMenu in all action menus.
    - Enhance shared transitions in immersive screens (Book, Oneshot, DetailScaffold) with better overlay rendering and timing.
    - Refine navigation logic to prevent redundant screen replacements.
    - Modernize appearance settings: replace nav bar color with accent color and use DropdownChoiceMenu for accent presets.
    - Fix immersive screen bottom bar visibility and styling for New UI.

commit 9ce318e6feac2f8039804ff0c7d62b31dcadc946
Author: eserero <eserero@hotmail.com>
Date:   Tue Mar 3 02:37:41 2026 +0200

    feat(reader): modernize reader UI with floating slider and settings FAB
    
    - Update progress slider to May 2025 Material 3 spec (16dp track, animated thumb).
    - Implement floating slider design by removing solid backgrounds and adding semi-transparent tracks.
    - Replace top settings icon with a bottom-right Floating Action Button (FAB) using 'Tune' icon.
    - Enhance settings bottom sheet with standard M3 drag handle and scrim.
    - Integrate user-defined accent color across all reader and appearance sliders.

commit 9762d81222ad38d8f2a34ac19daced7813820f3f
Author: eserero <eserero@hotmail.com>
Date:   Mon Mar 2 01:21:25 2026 +0200

    fix(ui): use stable window height for immersive collapsedOffset
    
    BoxWithConstraints.maxHeight shrinks by ~80dp when the Material NavigationBar
    disappears during the library→book transition. The previous fix added
    systemNavBarHeight back, but the instability was from the app nav bar, not the
    OS nav bar.
    
    Replace `maxHeight + rawNavBar` with `windowHeight − statusBar − navBar`
    using LocalWindowInfo.current.containerSize, which is invariant to whether
    the app NavigationBar is visible.
    
    Also add LocalRawNavBarHeight CompositionLocal (needed for the calculation)
    and remove the now-duplicate statusBarDp declaration.
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit 01e7fb9f482ab962b09b7b50835a1d3620bdf3b0
Author: eserero <eserero@hotmail.com>
Date:   Mon Mar 2 01:21:06 2026 +0200

    docs: update README with fork improvements and new features

commit 3e58997ab0f975c64fc57fccba6995b7f87c3d08
Author: eserero <eserero@hotmail.com>
Date:   Mon Mar 2 00:54:45 2026 +0200

    feat(home): refine immersive oneshot screen and dropdown styling
    
    - Align immersive oneshot screen layout with the series screen (tabs for tags, collections, and read lists).
    - Ensure collections and read lists are initialized in OneshotViewModel.
    - Remove solid backgrounds and shadows from thumbnail count and filter dropdowns for a cleaner, transparent look.
    - Add thin borders to selected book view mode buttons.

commit 4d960b6568d2935840f7b4e6656a88359bcd1474
Author: eserero <eserero@hotmail.com>
Date:   Mon Mar 2 00:17:48 2026 +0200

    style(ui): strictly enforce 1-line-per-segment in 'Below' card layout

commit 4548c2fef8ad774976113986de00fba43d008f78
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 23:12:04 2026 +0200

    feat(ui): enhance immersive screens and navigation with adaptive colors and accent presets

commit f1b5d9da10239910a12b3f678635cdd342bc6ed3
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 21:45:18 2026 +0200

    fix(ui): eliminate double animation and cover flash on immersive book open
    
    Hold the pager at 1 page (always showing the tapped book) until the
    shared-element transition settles before expanding to the full sibling
    count. This prevents two race-condition bugs that were deterministic per
    (book, navigation-source) pair:
    
    - Bug A (cover flash): siblingBooks loading mid-transition caused page 0
      to show siblingBooks[0] (a different book) because the shared-element
      cover key changed mid-flight.
    
    - Bug B (double card slide): new pages entering composition while
      AnimatedVisibility was still \"entering\" fired a second slideInVertically
      on ImmersiveDetailScaffold.
    
    Key changes in ImmersiveBookContent:
    - pagerExpanded / initialScrollDone are now separate flags: pager expands
      first (so scrollToPage can run), then initialScrollDone flips only after
      the pager has settled on the correct page — preventing the brief window
      where page 0 shows the wrong cover.
    - selectedBook is guarded by initialScrollDone so onBookChange is not
      called with the wrong sibling before the pager is initialised, which
      previously propagated back as a changed `book` prop and corrupted the
      pageBook guard.
    - transitionIsSettled (derivedStateOf over animatedVisibilityScope) gates
      the scroll so no new pages enter composition during the enter transition.
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit 97a8ec6b94398f83885ff86bbaf7eed6b752325a
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 20:14:28 2026 +0200

    fix(reader): prevent image loading getting stuck on page change
    
    Ongoing image downloads were being cancelled when changing pages due to aggressive cancelChildren() on the page load scope.
    Introduced targeted cancellation of the spread/page loading job while allowing individual image loading tasks to continue.

commit 0f0dc05d627a81e62c36d994ad2eeb5e2c0ae128
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 18:10:29 2026 +0200

    feat(reader): implement four-side sampling and corner blending for adaptive background
    
    This commit completes Phase 3 of the adaptive background implementation, optimizing for Panel Mode:
    - Update EdgeSampling to support all four sides (top, bottom, left, right).
    - Update ReaderImageUtils to sample all four edges and provide 1D color lines.
    - Implement trapezoidal gradient zones with 45-degree corner mitering in AdaptiveBackground.
    - Move AdaptiveBackground outside of the ScalableContainer in Panel Mode for static rendering.
    - Update PanelsReaderContent to calculate real-time imageBounds for background alignment.

commit 9bdc9d9d0f1be3c3bf603232a1efd6a9b83f2273
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 17:42:04 2026 +0200

    feat(reader): implement image-relative adaptive background blooming
    
    This commit implements Phase 2 and 2.5 of the adaptive background plan:
    - Add EdgeSampling and EdgeSample data classes for richer edge data.
    - Update ReaderImageUtils to efficiently sample edge average colors.
    - Pre-calculate image display sizes in reader states to avoid composition-time suspend calls.
    - Rewrite AdaptiveBackground to draw gradients relative to image edges rather than screen center.
    - Implement sub-pixel rounding fixes and 1px overlap to eliminate background gaps.
    - Add ByteArray.toImageBitmap extension for raw RGBA buffer conversion across platforms.

commit 928e32334ab92c6b3e345b2ade6bef171ede39d3
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 15:58:10 2026 +0200

    fix(reader): correct inverted logic for adaptive background sampling

commit c41708f301cc1c47a5973aa5728802748419f494
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 15:31:33 2026 +0200

    feat(reader): add adaptive edge-sampled gradient backgrounds
    
    - Add efficient edge-sampling color utility using libvips.
    - Implement AdaptiveBackground Composable with animated transitions.
    - Integrate adaptive backgrounds into Paged and Panel reader modes.
    - Add settings toggles and persistence for both reader modes.
    - Include database migration (V18) for new settings.

commit ea7453aa71621795176fa717a93450aaa1c8755d
Author: eserero <eserero@hotmail.com>
Date:   Sun Mar 1 14:42:18 2026 +0200

    fix(ui): correct panel reader centering and rotation transitions

commit eb5f007c5271c76b89f3d5ea94665f6c2344c430
Author: eserero <eserero@hotmail.com>
Date:   Sat Feb 28 22:15:14 2026 +0200

    style(ui): improve Home and Library toolbars
    
    - Relocate Home screen edit button to top right as a menu icon.
    - Align Home screen chips with section headers.
    - Fix Library screen toolbar layout with fixed name and right-aligned menu.

commit 38de3e00042da0e0e922d07fb20643946903997f
Author: eserero <eserero@hotmail.com>
Date:   Sat Feb 28 21:29:15 2026 +0200

    feat(ui): refine Library screen toolbar layout
    
    - Move library options menu to the right.
    - Make library name clickable to toggle the navigation drawer.
    - Truncate long library names with ellipsis (max width 150dp).
    - Optimize spacing between name and chips.
    - Add end padding to chips row to prevent overlap with menu button.

commit 36672b833b105b8e8e1c56d94e7b700981a3470e
Author: eserero <eserero@hotmail.com>
Date:   Sat Feb 28 20:23:16 2026 +0200

    style(ui): update chips to Material 3 squarish design and fix navigation crash
    
    - Update 'AppFilterChipDefaults' and 'NoPaddingChip' to use Material 3 squarish shape (8dp).
    - Lighten unselected chip borders using 'outline' color and 'Dp.Hairline'.
    - Add 'AppSuggestionChipDefaults' and apply to count chips in Library and Series screens.
    - Fix IllegalArgumentException on Android by adding navigation guards to prevent redundant screen pushes.

commit d4b5b399dc939d0976e3fb2466dd15e0ab2d9076
Author: eserero <eserero@hotmail.com>
Date:   Sat Feb 28 02:31:00 2026 +0200

    style(ui): align core navigation, FABs, and menus with Material 3 specification
    
    - Refactor MainScreen mobile layout to use a single Scaffold and M3 NavigationBar.
    - Update immersive detail FABs to use standard M3 Extended FAB and standard FAB with squircle shapes.
    - Standardize visual hierarchy in all action menus with leading icons and M3 typography.
    - Style destructive menu actions with 'error' color tokens and remove manual hover overrides.
    - Update toolbar and item card triggers to use rounded icon variants (MoreVert).
    - Clean up redundant window inset spacers and hardcoded list padding.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 0530e2b79c502a5fe7bbcb22864aa7a85a38e86d
Author: eserero <eserero@hotmail.com>
Date:   Sat Feb 28 00:59:44 2026 +0200

    style(ui): align immersive views with Material 3 typography and elevated card specs
    
    - Align typography in Series, Book, and Oneshot immersive screens with Material 3 tokens (headlineSmall, titleMedium, labelSmall).
    - Replace manually scaled headlines and hardcoded 10sp text with standard M3 scale steps.
    - Upgrade card in ImmersiveDetailScaffold to Material 3 Elevated Card specs (6.dp elevation).
    - Adjust scaffold background to 'surface' to provide visual contrast for the elevated card shadow.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 995703b2574ac64d1612bb867116968ad10fe924
Author: eserero <eserero@hotmail.com>
Date:   Sat Feb 28 00:16:49 2026 +0200

    refactor(ui): simplify immersive scaffold and fix state restoration
    
    - Remove manual scroll tracking (innerScrollPx) and overlay thumbnail from ImmersiveDetailScaffold.
    - Move thumbnails directly into card content for Books, Series, and Oneshots to ensure perfect scroll synchronization.
    - Persist card expansion state (isExpanded) in ViewModels to ensure correct state restoration when navigating back.
    - Fix layout issue where extra space was reserved for thumbnails when the card was downsized.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 70d0c49152c11d89f7a715c03b2847543316cfe3
Author: eserero <eserero@hotmail.com>
Date:   Fri Feb 27 23:55:32 2026 +0200

    fix(ui): improve immersive detail view layout and interaction
    
    - Fix ImmersiveDetailFab layout by removing weights and adding a leading Spacer for correct right-alignment.
    - Synchronize BookViewModel with ImmersiveBookContent pager to ensure correct book context for download and navigation.
    - Refactor ImmersiveDetailScaffold nested scroll logic to ensure a hard stop at the top when expanding the card.
    - Prevent state drift in card scrolling by consuming upward velocity and delta in onPreScroll/onPreFling.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit f3ecacd18abe8129f17fc833eac1ae580e935657
Author: eserero <eserero@hotmail.com>
Date:   Fri Feb 27 20:30:05 2026 +0200

    reader: feature reader improvements and additional documentation

commit 55e273cfea7a65ed25e4c339881bb3a21251522f
Author: Eyal <eyal@local>
Date:   Fri Feb 27 20:24:04 2026 +0200

    fix(paged): resolve initial book loading hang
    
    - Remove incorrect early return in jumpToPage() that prevented loading on startup.
    - Matches the fix applied to Panel mode for consistent startup behavior.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 0ed5f0337cb4e156b26d7bc0afc700f150b9924b
Author: Eyal <eyal@local>
Date:   Fri Feb 27 19:59:37 2026 +0200

    feat(reader): implement density-aware spring physics for consistent transitions
    
    - Centralize navigation physics in ReaderAnimation.navSpringSpec(density).
    - Update ScreenScaleState to store and use display density for camera transitions.
    - Normalize page sliding and camera movement tempo across devices (phone vs tablet).
    - Ensure smooth side-by-side page sliding in Panel mode transitions by rendering neighbors.
    - Resolves sluggish feel on tablets and jarring snap on phones.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit e6e0ac6677728eec6474f08cd74cdf5f0af2f97d
Author: Eyal <eyal@local>
Date:   Fri Feb 27 14:02:47 2026 +0200

    feat(reader): smooth animated transitions for Paged and Panel modes

commit 6a7e9d39daafaddb4d0d0ff7d27a80f477b67b99
Author: Eyal <eyal@local>
Date:   Fri Feb 27 12:22:25 2026 +0200

    feat(android): upgrade panel detection model to rf-detr-med
    
    - Update download URL to point to the higher-accuracy Medium model.
    - Update detector search path to load 'rf-detr-med.onnx' instead of 'nano'.
    - Improves panel detection reliability on Android devices.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit b16c2470f78e1ed7c1a78719d8672391b14e084b
Author: Eyal <eyal@local>
Date:   Fri Feb 27 11:57:49 2026 +0200

    feat(reader): implement mode-specific 'Tap to zoom' toggle
    
    - Add separate 'Tap to zoom' settings for Paged and Panel reader modes.
    - Implement database migration (V17) to persist mode-specific tap configuration.
    - Update persistence layer (Table, Data Class, Repository) to support new settings.
    - Enhance gesture system:
        - Conditionally enable double-tap to zoom based on the active reader's configuration.
        - Disabling double-tap allows for instantaneous single-tap response (faster page/panel turns).
    - UI Enhancements:
        - Add 'Tap to zoom' toggle to mobile bottom sheet for Paged and Panels modes.
        - Add 'Tap to zoom' toggle to desktop side menu for Paged and Panels modes.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 5668bb6d4bd7472a8cb1371f9117b70b65bdcdcb
Author: Eyal <eyal@local>
Date:   Fri Feb 27 10:43:54 2026 +0200

    feat(reader): implement unified smooth pan and zoom for panel navigation
    
    - Add synchronized animateTo(Offset, Float) to ScreenScaleState:
        - Uses linear interpolation to animate offset and zoom simultaneously over 1000ms.
        - Prevents jerky 'zoom then scroll' jumps during panel transitions.
    - Update PanelsReaderState to use unified animation for panel-to-panel movement.
    - Add skipAnimation support to scrollToPanel for instant positioning during initial loads.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit ca5dd1e560f9cc22bd2e298c389bf4ad20190e3b
Author: Eyal <eyal@local>
Date:   Fri Feb 27 01:43:27 2026 +0200

    feat(reader): implement full page context sequence for panel navigation
    
    - Add 'Show full page' setting to Panel Reader with options: None, Before, After, Both.
    - Implement intelligent full-page injection in PanelsReaderState:
        - Automatically show full page before/after panel sequences based on settings.
        - Prevent redundant views on splash pages (1 large panel) or empty detections.
        - Support seamless bidirectional navigation (forward/backward) through the injected sequence.
    - Persistence and Data Model:
        - Add PanelsFullPageDisplayMode enum to domain layer.
        - Implement database migration (V16) to persist display mode setting.
        - Update repository and Exposed implementation for SQLite storage.
    - UI Enhancements:
        - Add 'Show full page' selection to mobile bottom sheet reader settings.
        - Add 'Show full page' dropdown to desktop side menu reader settings.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 940a7a025b09acec5e474d7616493bf1a2c104f0
Author: Eyal <eyal@local>
Date:   Thu Feb 26 23:59:52 2026 +0200

    feat(reader): implement kinetic paged sticky swipe with RTL support
    
    - Implement RTL-aware directional sticky barrier: prevents accidental page turns when zoomed in.
    - Implement kinetic momentum: uses dispatchRawDelta and performFling for smooth, natural movement.
    - Implement robust kinetic snapping: automatically settles on the closest page when gesture or fling ends.
    - Replace static paged layout with controlled HorizontalPager for frame-perfect synchronization.
    - Fix RTL gesture reversal: ensure scroll orientation and barriers update reactively to direction changes.
    - Restore synchronous pan/zoom math: eliminates race conditions and restores precise gesture control.
    - Optimize image loading: refined LaunchedEffect and remembered spread metadata for smoother paging.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit bd083418b75f9dc281d4bc506dd4fdb7e0701327
Author: Eyal <eyal@local>
Date:   Thu Feb 26 20:19:26 2026 +0200

    feat(ui): implement shared transitions and gesture system fixes
    
    - Add SharedTransitionLayout and AnimatedContent for smooth library UI transitions.
    - Enhance ImmersiveDetailScaffold and thumbnails with shared bounds and visibility animations.
    - Implement 'Gesture System Fix':
        - Fix pan-zoom lock and velocity jumps by tracking pointer count stability in ScalableContainer.
        - Add smooth double-tap to zoom in ReaderControlsOverlay with base zoom tracking in ScreenScaleState.
        - Synchronize velocity resets on pointer count changes to prevent kinetic scrolling jumps.
    - Update Paged, Continuous, and Panels reader states to support base zoom.
    - Add implementation and research documents:
        - GESTURE_SYSTEM_FIX.md: Documentation for the implemented gesture fixes.
        - PAGED_SWIPE_PLAN.md: Proposed plan for paged mode sticky swipe.
        - PANEL_VIEWER_RESEARCH.md: Research for upcoming panel viewer improvements.
        - REORG_TASKS.md: Task list for repository reorg.
    
    Co-Authored-By: Gemini CLI <noreply@google.com>

commit 4b3324f5dbaeed27db17396b05bd10af91beaefc
Author: Eyal <eyal@local>
Date:   Wed Feb 25 00:53:57 2026 +0200

    New library UI: ImmersiveBookContent + ImmersiveOneshotContent + scaffold pager sync
    
    - Extract ImmersiveBookContent and ImmersiveOneshotContent composables from
      BookScreen/OneshotScreen; screens now delegate to these dedicated composables
    - ImmersiveDetailScaffold: add initiallyExpanded param + onExpandChange callback
      for synchronized expand/collapse state across pager pages; use remember instead
      of rememberSaveable so adjacent pager pages don't restore stale state; snapTo
      correct anchor when parent-driven initiallyExpanded changes
    - ImmersiveSeriesContent: add nav bar + 80 dp bottom content padding to grid
      so last row isn't hidden behind nav bar
    - Minor import cleanup in BookScreen / OneshotScreen
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit 40d0de3acd0ae51e0bf2205f4e1255acdbebf289
Author: Eyal <eyal@local>
Date:   Tue Feb 24 17:22:16 2026 +0200

    New library UI: ImmersiveSeriesContent + card shadow
    
    - Add ImmersiveSeriesContent using ImmersiveDetailScaffold; wired into
      SeriesScreen behind MOBILE && useNewLibraryUI flag
    - ImmersiveDetailFab: read / read-incognito / download with confirmation dialog
    - Card layout: title (2/3 headlineMedium) → writers+year → SeriesDescriptionRow
      → summary → chip tags → tab row → books/collections content
    - Bulk-select support: top bar swaps to BulkActionsContainer in selection mode,
      BottomPopupBulkActionsPanel shown when books selected
    - SeriesDescriptionRow: add showReleaseYear param (default true)
    - ImmersiveDetailScaffold: add 2dp shadow before clip so shadow isn't clipped
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit 2aa61515274349f67d7612495da251dff6456c5d
Author: Eyal <eyal@local>
Date:   Tue Feb 24 02:33:18 2026 +0200

    New library UI: immersive detail scaffold with cover behind status bar
    
    - Add ImmersiveDetailScaffold: draggable card over full-bleed cover image,
      animated corner radius, nested scroll, FAB and top bar layers
    - Add LocalRawStatusBarHeight composition local to capture status bar height
      before statusBarsPadding() consumes it in MainScreen
    - Wire immersive=true in SeriesScreen, BookScreen, OneshotScreen; replace
      IconButton (40dp M3 minimum) with Box+clickable for true 36dp circle size;
      add edge padding so button is not flush with screen edges
    - Rename migration V14__new_library_ui → V14__immersive_layout; add V15 for
      new library UI settings
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

commit 3c527c474977995b73870c153c278a0835226ef6
Author: Eyal <eyal@local>
Date:   Sun Feb 22 06:41:05 2026 +0200

    New library UI: floating pill nav bar, Keep Reading panel, pill chips, color pickers
    
    - Floating pill-shaped bottom nav bar (mobile) with nav bar color picker
    - Keep Reading horizontal strip in library series list
    - Home screen: horizontal section rows (Keep Reading, On Deck, etc.) in new UI mode
    - Pill-shaped filter chips; accent color picker for chips and tabs
    - New Library UI master toggle in Settings → Appearance (defaults ON)
    - Collections and read lists: tighter grid spacing in new UI mode
    - Nav bar icon color fix (correct contrast in dark/light mode)
    - DB migrations V13 (nav_bar_color, accent_color) and V14 (use_new_library_ui)
    
    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>

## Changes by Impact Area

### Library Screen
- **Sun Mar 8 03:39:48 2026**: feat(library): upgrade header to Material 3 and improve navigation
    - Migrate library count chips and page size selection to TopAppBar actions.
    - Implement persistence for the last selected library across app restarts.
    - Refactor library navigation to use the last viewed library by default.
    - Replace FilterChips with SingleChoiceSegmentedButtonRow for tab switching.
    - Clean up redundant toolbars and UI components in library content views.
    - Add support for immersive card colors based on cover dominant color.
    - Add migration scripts for library persistence and immersive color settings.
- **Fri Mar 6 21:46:38 2026**: feat(series-filter): implement dialog-based author filter for mobile
    Replace the author filter dropdown with a centralized AppDialog to improve the mobile experience. The new FilterDialogMultiChoiceWithSearch matches the Tag Filter UI's look and feel, including integrated search/reset, chip-based selection, and consistent styling.
- **Thu Mar 5 02:17:28 2026**: feat(library): replace inline filter with ExtendedFAB + ModalBottomSheet
    Move the series filter trigger from a toolbar IconButton to a persistent ExtendedFAB at bottom-right. Filter fields now open in an M3 ModalBottomSheet with a scrollable content area and a sticky footer containing Reset/Hide FABs. The filter FAB fades out when the sheet is open and disappears in edit mode. The FilterList icon turns yellow and the Reset FAB uses the accent color when a filter is active.
- **Sat Feb 28 22:15:14 2026**: style(ui): improve Home and Library toolbars
    - Fix Library screen toolbar layout with fixed name and right-aligned menu.
- **Sat Feb 28 21:29:15 2026**: feat(ui): refine Library screen toolbar layout
    - Move library options menu to the right.
    - Make library name clickable to toggle the navigation drawer.
    - Truncate long library names with ellipsis (max width 150dp).
    - Optimize spacing between name and chips.
    - Add end padding to chips row to prevent overlap with menu button.
- **Sat Feb 28 20:23:16 2026**: style(ui): update chips to Material 3 squarish design and fix navigation crash
    - Update 'AppFilterChipDefaults' and 'NoPaddingChip' to use Material 3 squarish shape (8dp).
    - Lighten unselected chip borders using 'outline' color and 'Dp.Hairline'.
    - Add 'AppSuggestionChipDefaults' and apply to count chips in Library and Series screens.
- **Sun Feb 22 06:41:05 2026**: New library UI: floating pill nav bar, Keep Reading panel, pill chips, color pickers
    - Floating pill-shaped bottom nav bar (mobile) with nav bar color picker
    - Keep Reading horizontal strip in library series list
    - Pill-shaped filter chips; accent color picker for chips and tabs
    - New Library UI master toggle in Settings → Appearance (defaults ON)
    - Collections and read lists: tighter grid spacing in new UI mode
    - Nav bar icon color fix (correct contrast in dark/light mode)
    - DB migrations V13 (nav_bar_color, accent_color) and V14 (use_new_library_ui)

### Home Screen
- **Thu Mar 5 00:11:49 2026**: feat(home): replace MoreVert button with settings FAB
    Move the home screen filter-edit trigger from an IconButton pinned to the filter-chip row into a Material 3 FloatingActionButton at the bottom-right corner, matching the reader's settings FAB style (accent color, RoundedCornerShape(16.dp)).
    Also fix PullToRefreshBox missing fillMaxSize, which caused the FAB to briefly appear near the top of the screen on first render.
- **Mon Mar 2 00:54:45 2026**: feat(home): refine immersive oneshot screen and dropdown styling
    - Remove solid backgrounds and shadows from thumbnail count and filter dropdowns for a cleaner, transparent look.
- **Sat Feb 28 22:15:14 2026**: style(ui): improve Home and Library toolbars
    - Relocate Home screen edit button to top right as a menu icon.
    - Align Home screen chips with section headers.
- **Sun Feb 22 06:41:05 2026**: New library UI: floating pill nav bar, Keep Reading panel, pill chips, color pickers
    - Home screen: horizontal section rows (Keep Reading, On Deck, etc.) in new UI mode

### Search
- **Sat Mar 7 20:59:38 2026**: fix(search): prevent crash and improve tab visibility on empty results
- **Tue Mar 3 19:35:26 2026**: feat(search): M3 SearchBar with spacing fix and SecondaryTabRow selector
    - Replace old SearchTextField on mobile with M3 SearchBar (state-driven API) that animates expand/collapse, shows back arrow when expanded, and shows clear button when query is non-empty
    - Add 8dp padding (start/end/top) to the Column wrapper so the bar has breathing room from screen edges and the status bar
    - Replace FilterChip pair in SearchToolBar with SecondaryTabRow + Tab for consistency with the rest of the app's binary-toggle pattern

### Book/Series/Oneshot Immersive Screens
- **Sun Mar 8 03:39:48 2026**: feat(library): upgrade header to Material 3 and improve navigation
    - Add support for immersive card colors based on cover dominant color.
- **Tue Mar 3 09:33:07 2026**: feat(ui): refine shared transitions, animated menus, and appearance settings
    - Enhance shared transitions in immersive screens (Book, Oneshot, DetailScaffold) with better overlay rendering and timing.
    - Fix immersive screen bottom bar visibility and styling for New UI.
- **Mon Mar 2 01:21:25 2026**: fix(ui): use stable window height for immersive collapsedOffset
    BoxWithConstraints.maxHeight shrinks by ~80dp when the Material NavigationBar disappears during the library→book transition. The previous fix added systemNavBarHeight back, but the instability was from the app nav bar, not the OS nav bar.
    Replace `maxHeight + rawNavBar` with `windowHeight − statusBar − navBar` using LocalWindowInfo.current.containerSize, which is invariant to whether the app NavigationBar is visible.
- **Mon Mar 2 00:54:45 2026**: feat(home): refine immersive oneshot screen and dropdown styling
    - Align immersive oneshot screen layout with the series screen (tabs for tags, collections, and read lists).
    - Ensure collections and read lists are initialized in OneshotViewModel.
- **Sun Mar 1 23:12:04 2026**: feat(ui): enhance immersive screens and navigation with adaptive colors and accent presets
- **Sun Mar 1 21:45:18 2026**: fix(ui): eliminate double animation and cover flash on immersive book open
    Hold the pager at 1 page (always showing the tapped book) until the shared-element transition settles before expanding to the full sibling count. This prevents two race-condition bugs that were deterministic per (book, navigation-source) pair:
    - Bug A (cover flash): siblingBooks loading mid-transition caused page 0 to show siblingBooks[0] (a different book) because the shared-element cover key changed mid-flight.
    - Bug B (double card slide): new pages entering composition while AnimatedVisibility was still \"entering\" fired a second slideInVertically on ImmersiveDetailScaffold.
    Key changes in ImmersiveBookContent:
    - pagerExpanded / initialScrollDone are now separate flags: pager expands first (so scrollToPage can run), then initialScrollDone flips only after the pager has settled on the correct page — preventing the brief window where page 0 shows the wrong cover.
    - selectedBook is guarded by initialScrollDone so onBookChange is not called with the wrong sibling before the pager is initialised, which previously propagated back as a changed `book` prop and corrupted the pageBook guard.
    - transitionIsSettled (derivedStateOf over animatedVisibilityScope) gates the scroll so no new pages enter composition during the enter transition.
- **Sat Feb 28 02:31:00 2026**: style(ui): align core navigation, FABs, and menus with Material 3 specification
    - Update immersive detail FABs to use standard M3 Extended FAB and standard FAB with squircle shapes.
- **Sat Feb 28 00:59:44 2026**: style(ui): align immersive views with Material 3 typography and elevated card specs
    - Align typography in Series, Book, and Oneshot immersive screens with Material 3 tokens (headlineSmall, titleMedium, labelSmall).
    - Replace manually scaled headlines and hardcoded 10sp text with standard M3 scale steps.
    - Upgrade card in ImmersiveDetailScaffold to Material 3 Elevated Card specs (6.dp elevation).
    - Adjust scaffold background to 'surface' to provide visual contrast for the elevated card shadow.
- **Sat Feb 28 00:16:49 2026**: refactor(ui): simplify immersive scaffold and fix state restoration
    - Remove manual scroll tracking (innerScrollPx) and overlay thumbnail from ImmersiveDetailScaffold.
    - Move thumbnails directly into card content for Books, Series, and Oneshots to ensure perfect scroll synchronization.
    - Persist card expansion state (isExpanded) in ViewModels to ensure correct state restoration when navigating back.
    - Fix layout issue where extra space was reserved for thumbnails when the card was downsized.
- **Fri Feb 27 23:55:32 2026**: fix(ui): improve immersive detail view layout and interaction
    - Fix ImmersiveDetailFab layout by removing weights and adding a leading Spacer for correct right-alignment.
    - Synchronize BookViewModel with ImmersiveBookContent pager to ensure correct book context for download and navigation.
    - Refactor ImmersiveDetailScaffold nested scroll logic to ensure a hard stop at the top when expanding the card.
    - Prevent state drift in card scrolling by consuming upward velocity and delta in onPreScroll/onPreFling.
- **Wed Feb 25 00:53:57 2026**: New library UI: ImmersiveBookContent + ImmersiveOneshotContent + scaffold pager sync
    - Extract ImmersiveBookContent and ImmersiveOneshotContent composables from BookScreen/OneshotScreen; screens now delegate to these dedicated composables
    - ImmersiveDetailScaffold: add initiallyExpanded param + onExpandChange callback for synchronized expand/collapse state across pager pages; use remember instead of rememberSaveable so adjacent pager pages don't restore stale state; snapTo correct anchor when parent-driven initiallyExpanded changes
    - ImmersiveSeriesContent: add nav bar + 80 dp bottom content padding to grid so last row isn't hidden behind nav bar
- **Tue Feb 24 17:22:16 2026**: New library UI: ImmersiveSeriesContent + card shadow
    - Add ImmersiveSeriesContent using ImmersiveDetailScaffold; wired into SeriesScreen behind MOBILE && useNewLibraryUI flag
    - ImmersiveDetailFab: read / read-incognito / download with confirmation dialog
    - Card layout: title (2/3 headlineMedium) → writers+year → SeriesDescriptionRow → summary → chip tags → tab row → books/collections content
    - Bulk-select support: top bar swaps to BulkActionsContainer in selection mode, BottomPopupBulkActionsPanel shown when books selected
    - ImmersiveDetailScaffold: add 2dp shadow before clip so shadow isn't clipped
- **Tue Feb 24 02:33:18 2026**: New library UI: immersive detail scaffold with cover behind status bar
    - Add ImmersiveDetailScaffold: draggable card over full-bleed cover image, animated corner radius, nested scroll, FAB and top bar layers
    - Wire immersive=true in SeriesScreen, BookScreen, OneshotScreen; replace IconButton (40dp M3 minimum) with Box+clickable for true 36dp circle size; add edge padding so button is not flush with screen edges

### Reader
- **Sun Mar 8 01:25:41 2026**: feat(settings): refactor navigation and improve reader image upscaling
    - Enhance Android NCNN upscaler and ReaderImage implementation for better performance and stability.
- **Sat Mar 7 18:26:33 2026**: fix(reader): resolve coordinate mismatch and crash during panel upscaling
    - Ensure PanelsReaderState uses consistent image dimensions from PanelData for scaling
    - Fix Android crash by keeping VipsImage as source during pre-emptive upscaling
    - Eliminate race condition caused by double-updating originalSize flow
- **Fri Mar 6 17:10:30 2026**: feat(android): add RealSR and Real-ESRGAN support to NCNN upscaler
    - Integrated RealSR engine into the native upscaler module
    - Added high-quality realsr-general-v3 (4x) and real-esrganv3-anime-x2 (2x) models
    - Updated JNI layer to handle unified RealSR/Real-ESRGAN engine types
    - Enhanced Kotlin layer with dynamic scale detection and model path construction
    - Hardened native RealSR implementation with null checks to prevent SIGSEGV on load failure
- **Thu Mar 5 21:49:17 2026**: feat(android): implement high-performance NCNN GPU upscaler
    - Added :komelia-infra:ncnn-upscaler module with JNI support for Waifu2x and RealCUGAN
    - Integrated NCNN upscaler into the Android image loading pipeline
    - Implemented pre-emptive upscaling for low-resolution images
    - Hardened integration with a global singleton and thread-safe JNI layer to prevent Vulkan context conflicts
- **Thu Mar 5 01:10:00 2026**: feat(reader): long-press to save page to Downloads
    Long-pressing anywhere on a reader image shows an M3 AnimatedDropdownMenu anchored to the press position with a \"Save image\" action. Tapping it fetches the raw page bytes via the Komga API, detects the image format (jpg/png/webp), and writes the file to the platform Downloads folder with a sanitized filename like BookName_p003.jpg. Works in paged, continuous, and panels reader modes.
- **Tue Mar 3 19:17:06 2026**: feat(reader): add tap navigation mode setting
    Adds a configurable tap navigation mode for the image reader:
    - New ReaderTapNavigationMode enum: LEFT_RIGHT, RIGHT_LEFT, HORIZONTAL_SPLIT, REVERSED_HORIZONTAL_SPLIT
    - DB migration V20 + Exposed table/repository plumbing
    - NavigationSettings composable with radio buttons, per-mode description subtitles, and a TapNavigationDiagram canvas that labels zones with Prev/Next/Menu
    - Navigation tab moved to the middle slot (between Reading mode and Image settings) in the bottom sheet overlay
    - Fix smart-cast on delegated imageSize in PagedReaderContent
- **Tue Mar 3 02:37:41 2026**: feat(reader): modernize reader UI with floating slider and settings FAB
    - Update progress slider to May 2025 Material 3 spec (16dp track, animated thumb).
    - Implement floating slider design by removing solid backgrounds and adding semi-transparent tracks.
    - Replace top settings icon with a bottom-right Floating Action Button (FAB) using 'Tune' icon.
    - Enhance settings bottom sheet with standard M3 drag handle and scrim.
    - Integrate user-defined accent color across all reader and appearance sliders.
- **Sun Mar 1 20:14:28 2026**: fix(reader): prevent image loading getting stuck on page change
    Ongoing image downloads were being cancelled when changing pages due to aggressive cancelChildren() on the page load scope. Introduced targeted cancellation of the spread/page loading job while allowing individual image loading tasks to continue.
- **Sun Mar 1 18:10:29 2026**: feat(reader): implement four-side sampling and corner blending for adaptive background
    - Update EdgeSampling to support all four sides (top, bottom, left, right).
    - Update ReaderImageUtils to sample all four edges and provide 1D color lines.
    - Implement trapezoidal gradient zones with 45-degree corner mitering in AdaptiveBackground.
    - Move AdaptiveBackground outside of the ScalableContainer in Panel Mode for static rendering.
    - Update PanelsReaderContent to calculate real-time imageBounds for background alignment.
- **Sun Mar 1 17:42:04 2026**: feat(reader): implement image-relative adaptive background blooming
    - Add EdgeSampling and EdgeSample data classes for richer edge data.
    - Update ReaderImageUtils to efficiently sample edge average colors.
    - Pre-calculate image display sizes in reader states to avoid composition-time suspend calls.
    - Rewrite AdaptiveBackground to draw gradients relative to image edges rather than screen center.
    - Implement sub-pixel rounding fixes and 1px overlap to eliminate background gaps.
- **Sun Mar 1 15:58:10 2026**: fix(reader): correct inverted logic for adaptive background sampling
- **Sun Mar 1 15:31:33 2026**: feat(reader): add adaptive edge-sampled gradient backgrounds
    - Add efficient edge-sampling color utility using libvips.
    - Implement AdaptiveBackground Composable with animated transitions.
    - Integrate adaptive backgrounds into Paged and Panel reader modes.
- **Sun Mar 1 14:42:18 2026**: fix(ui): correct panel reader centering and rotation transitions
- **Fri Feb 27 20:30:05 2026**: reader: feature reader improvements and additional documentation
- **Fri Feb 27 20:24:04 2026**: fix(paged): resolve initial book loading hang
    - Remove incorrect early return in jumpToPage() that prevented loading on startup.
- **Fri Feb 27 19:59:37 2026**: feat(reader): implement density-aware spring physics for consistent transitions
    - Centralize navigation physics in ReaderAnimation.navSpringSpec(density).
    - Update ScreenScaleState to store and use display density for camera transitions.
    - Normalize page sliding and camera movement tempo across devices (phone vs tablet).
    - Ensure smooth side-by-side page sliding in Panel mode transitions by rendering neighbors.
- **Fri Feb 27 14:02:47 2026**: feat(reader): smooth animated transitions for Paged and Panel modes
- **Fri Feb 27 12:22:25 2026**: feat(android): upgrade panel detection model to rf-detr-med
    - Update detector search path to load 'rf-detr-med.onnx' instead of 'nano'.
- **Fri Feb 27 11:57:49 2026**: feat(reader): implement mode-specific 'Tap to zoom' toggle
    - Add separate 'Tap to zoom' settings for Paged and Panel reader modes.
    - Enhance gesture system: conditionally enable double-tap to zoom; disabling it allows instantaneous single-tap response.
- **Fri Feb 27 10:43:54 2026**: feat(reader): implement unified smooth pan and zoom for panel navigation
    - Add synchronized animateTo(Offset, Float) to ScreenScaleState to animate offset and zoom simultaneously over 1000ms.
- **Fri Feb 27 01:43:27 2026**: feat(reader): implement full page context sequence for panel navigation
    - Add 'Show full page' setting to Panel Reader with options: None, Before, After, Both.
    - Implement intelligent full-page injection in PanelsReaderState.
- **Thu Feb 26 23:59:52 2026**: feat(reader): implement kinetic paged sticky swipe with RTL support
    - Implement RTL-aware directional sticky barrier, kinetic momentum, and robust kinetic snapping.
    - Replace static paged layout with controlled HorizontalPager.
    - Fix RTL gesture reversal and restore synchronous pan/zoom math.
- **Thu Feb 26 20:19:26 2026**: feat(ui): implement shared transitions and gesture system fixes
    - Fix pan-zoom lock and velocity jumps by tracking pointer count stability in ScalableContainer.
    - Add smooth double-tap to zoom in ReaderControlsOverlay with base zoom tracking in ScreenScaleState.

### Settings Page
- **Sun Mar 8 03:39:48 2026**: feat(library): upgrade header to Material 3 and improve navigation
    - Added persistence settings for the last selected library.
    - Added **immersive card color** settings (Toggle + Alpha slider).
- **Sun Mar 8 01:25:41 2026**: feat(settings): refactor navigation and improve reader image upscaling
    - Refactor settings navigation menu into dedicated components for better maintainability.
- **Fri Mar 6 17:10:30 2026**: feat(android): add RealSR and Real-ESRGAN support to NCNN upscaler
    - Updated Settings UI with new **upscaler engine options** (RealSR, Real-ESRGAN).
- **Thu Mar 5 21:49:17 2026**: feat(android): implement high-performance NCNN GPU upscaler
    - Added comprehensive settings UI for **NCNN upscaler configuration** on Android.
- **Tue Mar 3 19:17:06 2026**: feat(reader): add tap navigation mode setting
    - Added configurable **tap navigation mode** settings (LEFT_RIGHT, RIGHT_LEFT, HORIZONTAL_SPLIT, REVERSED_HORIZONTAL_SPLIT).
- **Tue Mar 3 09:33:07 2026**: feat(ui): refine shared transitions, animated menus, and appearance settings
    - Added **accent color picker** with presets.
- **Sun Mar 1 15:31:33 2026**: feat(reader): add adaptive edge-sampled gradient backgrounds
    - Added settings toggles for **Adaptive Backgrounds** in both Paged and Panel reader modes.
- **Fri Feb 27 11:57:49 2026**: feat(reader): implement mode-specific 'Tap to zoom' toggle
    - Added mode-specific **'Tap to zoom' toggle** for Paged and Panel modes.
- **Fri Feb 27 01:43:27 2026**: feat(reader): implement full page context sequence for panel navigation
    - Added **'Show full page' setting** to Panel Reader (None, Before, After, Both).
- **Sun Feb 22 06:41:05 2026**: New library UI: floating pill nav bar, Keep Reading panel, pill chips, color pickers
    - Added **New Library UI master toggle** in Settings → Appearance.
    - Added **nav bar color picker**.

## Summary of New Features (Final State)

### Library Screen
*   **Material 3 Header**: The library screen now uses a standard M3 `TopAppBar`. Metadata like total series/collection counts and the page size selector have been moved from the content area into the header actions for better visibility.
*   **Mobile-Friendly Author Filter**: A new dialog-based author filter with search capabilities has replaced the standard dropdown for a better mobile experience.
*   **Library Persistence**: The app now remembers and automatically restores the last viewed library upon restart.
*   **Segmented Navigation**: Tab switching between Series, Collections, and Read Lists now uses an M3 `SingleChoiceSegmentedButtonRow`.
*   **Advanced Filtering**: The inline filter has been replaced with a persistent Extended Floating Action Button (FAB) that opens a Modal Bottom Sheet. It includes integrated search, chip-based selection, and a dedicated mobile-friendly UI.
*   **"Keep Reading" Strip**: A new horizontal strip in the series list provides quick access to recently read books.
*   **Enhanced Layout**: Collections and Read Lists now feature tighter grid spacing, and the library name can be clicked to toggle the navigation drawer.

### Home Screen
*   **Modernized Navigation**: Section management (filtering/editing) has been moved to a Floating Action Button at the bottom-right, matching the reader's style.
*   **Horizontal Sections**: Content is now organized into horizontal rows (e.g., Keep Reading, On Deck) for a more compact and discoverable layout.
*   **Refined Header**: The edit button is now located in the top-right menu, and filter chips are perfectly aligned with section headers.

### Search
*   **Interactive Search Bar**: Replaced the old search field with a Material 3 `SearchBar` that animates, supports a \"clear\" button, and handles back-navigation natively.
*   **Consistent Toggles**: Search filters now use `SecondaryTabRow` selectors, ensuring consistency with the rest of the application's design patterns.

### Book/Series/Oneshot Immersive Screens
*   **Immersive Detail Scaffold**: A unified \"Immersive\" view featuring a full-bleed cover image that extends behind the status bar.
*   **Adaptive Tinting**: The detail card background now dynamically tints itself based on the dominant color extracted from the book or series cover.
*   **Elevated Card UI**: Uses Material 3 elevated card specifications with standard M3 typography and smooth shared-element transitions for thumbnails.
*   **Synchronized Navigation**: All screens (Series, Book, Oneshot) now follow a consistent tabbed layout (Tags, Collections, Read Lists) and synchronize their expand/collapse state.

### Reader
*   **Adaptive Backgrounds**: Implemented \"blooming\" gradient backgrounds that sample colors from the edges of the current page or panel, creating a more immersive experience in both Paged and Panel modes.
*   **High-Performance Upscaling**: Integrated an NCNN-powered GPU upscaler supporting multiple engines (Waifu2x, RealCUGAN, RealSR, Real-ESRGAN) for high-quality image enhancement on Android.
*   **Kinetic Gesture System**: A completely rewritten gesture engine providing \"sticky\" paged swiping, RTL-aware directional barriers, and smooth kinetic momentum for natural navigation.
*   **Advanced Panel Navigation**: Added \"Full Page Context\" injection (showing the full page before/after panels) and unified pan-and-zoom animations to eliminate jerky transitions.
*   **Modern Controls**: Features a floating progress slider (May 2025 M3 spec), a dedicated settings FAB, and a long-press menu to save pages directly to the Downloads folder.
*   **Configurable Navigation**: Added per-mode \"Tap to zoom\" toggles and a tap-navigation diagram to customize how page turns and zoom actions are triggered.

### Settings Page
*   **New Visual Settings**:
    *   **Immersive Detail Colors**: Toggle and alpha strength slider for dominant color card tinting.
    *   **Unified Accent Color**: A new color picker with presets to customize chips, tabs, and sliders app-wide.
    *   **New UI Master Toggle**: Easy switch to enable/disable the modern library and home screen layouts.
    *   **Smaller Thumbnail Configuration**: Allow for a smaller thumbnail configuration - choosing 110 will show 3 thumbnails per row in the library and home views.
*   **New Reader Settings**:
    *   **NCNN GPU Upscaler**: Comprehensive configuration for Android, including multiple engine selections (Waifu2x, RealSR, ESRGAN).
    *   **Custom Tap Navigation**: Multiple modes for tap-based page turning with a visual zone diagram.
    *   **Adaptive Backgrounds**: Toggles for edge-sampled gradient backgrounds in paged and panel readers.
    *   **Panel Navigation Context**: Configuration for 'Show full page' behavior (Before/After/Both) during panel sequences.
*   **Modular Navigation**: The settings menu has been refactored into dedicated navigation components, improving maintainability and the overall structure of the settings area.
