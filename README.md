# Komelia - Komga media client

## Fork Overview
This is a specialized fork of [Komelia](https://github.com/Gaysuist/Komelia) focused on providing a premier **Android experience** through deep Material 3 integration, high-performance GPU upscaling, and a modernized UI tailored for mobile devices.

### Summary of New Features

#### Library Screen
*   **Material 3 Header**: Standardized M3 `TopAppBar` with integrated metadata chips (series/collection counts) and page size selectors for better information density.
*   **Mobile-Friendly Author Filter**: A new dialog-based author filter with search capabilities, replacing the standard dropdown for a superior touch experience.
*   **Library Persistence**: Automatically remembers and restores the last viewed library upon app restart.
*   **Segmented Navigation**: Modern M3 `SingleChoiceSegmentedButtonRow` for seamless switching between Series, Collections, and Read Lists.
*   **Advanced Filtering**: persistent Extended Floating Action Button (FAB) that opens an M3 Modal Bottom Sheet with integrated search and chip-based selection.
*   **"Keep Reading" Strip**: A new horizontal quick-access strip in the series list for your recently read books.

#### Home Screen
*   **Modernized Navigation**: Section management and settings moved to a Floating Action Button at the bottom-right for easy one-handed use.
*   **Horizontal Layout**: Organized content into horizontal rows (Keep Reading, On Deck, etc.) for a compact and discoverable dashboard.

#### Search
*   **Interactive Search Bar**: Full M3 `SearchBar` implementation with smooth animations, native back-navigation, and clear-text support.
*   **Consistent Toggles**: Search filters now use `SecondaryTabRow` selectors for a unified UI language.

#### Immersive Detail Screens (Book, Series, Oneshot)
*   **Immersive Detail Scaffold**: Full-bleed cover images extending behind the status bar for a modern, cinematic feel.
*   **Adaptive Tinting**: Detail card backgrounds dynamically sample and apply dominant colors from the cover artwork.
*   **Elevated Card UI**: Material 3 elevated specifications with standard typography and smooth shared-element transitions from the library list.
*   **Synchronized State**: Consistent tabbed layouts across all detail screens with synchronized expand/collapse behavior.

#### Reader
*   **Adaptive Backgrounds**: "Blooming" gradient backgrounds that sample edge colors in real-time for both Paged and Panel modes.
*   **High-Performance GPU Upscaling**: Integrated NCNN-powered upscaling (Waifu2x, RealCUGAN, RealSR, Real-ESRGAN) specifically optimized for Android GPU hardware.
*   **Kinetic Gesture System**: Completely rewritten engine with "sticky" paged swiping, RTL-aware directional barriers, and natural kinetic momentum.
*   **Advanced Panel Navigation**: "Full Page Context" injection and unified smooth pan-and-zoom animations to eliminate transition jars.
*   **Modern Controls**: Floating progress slider (May 2025 M3 spec), dedicated settings FAB, and long-press quick-save to Downloads.

#### Settings Page
*   **Modular Architecture**: Refactored menu structure for better maintainability and navigation.
*   **New Visual Toggles**: Immersive color strength sliders, unified app-wide accent color presets, and a master toggle for the "New Library UI".
*   **Deep Customization**: Per-mode toggles for tap-to-zoom, configurable tap navigation zones with visual diagrams, and granular adaptive background settings.

---

### Downloads:

- Latest prebuilt release is available at https://github.com/Snd-R/Komelia/releases
- Google Play Store https://play.google.com/store/apps/details?id=io.github.snd_r.komelia
- F-Droid https://f-droid.org/packages/io.github.snd_r.komelia/
- AUR package https://aur.archlinux.org/packages/komelia

## Screenshots

<details>
  <summary>Mobile</summary>
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="Komelia" width="270">  
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" alt="Komelia" width="270">  
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="Komelia" width="270">  
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" alt="Komelia" width="270">  
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" alt="Komelia" width="270">  
   <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" alt="Komelia" width="270">  
</details>

<details>
  <summary>Tablet</summary>
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/1.jpg" alt="Komelia" width="400" height="640">  
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/2.jpg" alt="Komelia" width="400" height="640">  
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/3.jpg" alt="Komelia" width="400" height="640">  
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/4.jpg" alt="Komelia" width="400" height="640">  
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/5.jpg" alt="Komelia" width="400" height="640">  
   <img src="/fastlane/metadata/android/en-US/images/tenInchScreenshots/6.jpg" alt="Komelia" width="400" height="640">  
</details>

<details>
  <summary>Desktop</summary>
   <img src="/screenshots/1.jpg" alt="Komelia" width="1280">  
   <img src="/screenshots/2.jpg" alt="Komelia" width="1280">  
   <img src="/screenshots/3.jpg" alt="Komelia" width="1280">  
   <img src="/screenshots/4.jpg" alt="Komelia" width="1280">  
   <img src="/screenshots/5.jpg" alt="Komelia" width="1280">  
</details>

[//]: # (![screenshots]&#40;./screenshots/screenshot.jpg&#41;)

## Native libraries build instructions

Android and JVM targets require C and C++ compiler for native libraries as well nodeJs for epub reader build

The recommended way to build native libraries is by using docker images that contain all required build dependencies\
If you want to build with system toolchain and dependencies try running:\
`./gradlew komeliaBuildNonJvmDependencies` (Linux Only)

## Desktop App Build

Requires jdk 17 or higher

To build with docker container, replace <*platform*> placeholder with your target platform\
Available platforms include: `linux-x86_64`, `windows-x86_64`

- `docker build -t komelia-build-<platfrom> . -f ./cmake/<paltform>.Dockerfile `
- `docker run -v .:/build komelia-build-<paltform>`
- `./gradlew <platform>_copyJniLibs` - copy built shared libraries to resource directory that will be bundled with the
  app
- `./gradlew buildWebui` - build and copy epub reader webui (npm is required for build)

Then choose your packaging option:
- `./gradlew :komelia-app:run` to launch desktop app
- `./gradlew :komelia-app:packageReleaseUberJarForCurrentOS` package jar file (output in `komelia-app/build/compose/jars`)
- `./gradlew :komelia-app:packageReleaseDeb` package Linux deb file (output in `komelia-app/build/compose/binaries`)
- `./gradlew :komelia-app:packageReleaseMsi` package Windows msi installer (output in `komelia-app/build/compose/binaries`)

## Android App Build

To build with docker container, replace <*arch*> placeholder with your target architecture\
Available architectures include:  `aarch64`, `armv7a`, `x86_64`, `x86`

- `docker build -t komelia-build-android . -f ./cmake/android.Dockerfile `
- `docker run -v .:/build komelia-build-android <arch>`
- `./gradlew <arch>_copyJniLibs` - copy built shared libraries to resource directory that will be bundled with the app
- `./gradlew buildWebui` - build and copy epub reader webui (npm is required for build)

Then choose app build option:

- `./gradlew :komelia-app:assembleDebug` debug apk build (output in `komelia-app/build/outputs/apk/debug`)
- `./gradlew :komelia-app:assembleRelease` unsigned release apk build (output in
  `komelia-app/build/outputs/apk/release`)

## Komf Extension Build

run`./gradlew :komelia-komf-extension:app:packageExtension` \
output archive will be in `./komelia-komf-extension/app/build/distributions`