# Komelia - Komga media client

## Fork Overview
This is a specialized fork of [Komelia](https://github.com/Gaysuist/Komelia) focused on providing a premier **Android experience** through deep Material 3 integration, high-performance GPU upscaling, and a modernized UI tailored for mobile devices.

### Summary of New Features

#### Library Screen
*   **Material 3**: Standardized M3 `TopAppBar` with cleaner menu location and a hamburger menu to choose the library. Floating FAB to open the filter dialog.
*   **Smaller Thumbnails**: allowing smaller thumbnails in the setting screen (choose 110) which shows 3 thumbnail per row instead of only two. Thumbnails are also nicer with some options to choose from (text below thumbnail, transparent background for text)
*   **Improved Filtering UI**: Improve and cleanup of some of the fields - specifically dialog-based author filter as it was not easy to work with it on mobile.
*   **Library Navigation and Persistence**: clicking on the Library navigation button Automatically remembers the last library you were - so you dont need to choose the library every time.
*   **Segmented Navigation**: Modern M3 `SingleChoiceSegmentedButtonRow` for seamless switching between Series, Collections, and Read Lists.
*   **"Keep Reading" Strip**: A new horizontal quick-access strip in the series list for your recently read books per library.


#### Home Screen
*   **Modernized Navigation**: Section management and settings moved to a Floating Action Button at the bottom-right for easy one-handed use.
*   **Horizontal Layout**: Organized content into horizontal rows (Keep Reading, On Deck, etc.) for a compact and discoverable dashboard. This is similar to how komga web show it.

#### Search
*   **Interactive Search Bar**: Full M3 `SearchBar` implementation with smooth animations, native back-navigation, and clear-text support.
*   **Consistent Toggles**: Search filters now use `SecondaryTabRow` selectors for a unified UI language.

#### Immersive Detail Screens (Book, Series, Oneshot)
*   **Immersive Screen**: Full-bleed cover images extending behind information card for a modern, cinematic feel. ability to swipe left right to move between books, floating buttons to easily read and download.
*   **Publisher Icons**: Showing publisher icon in the immersive screen (if there is a match).
*   **Adaptive Card Color**: Detail card backgrounds dynamically sample and apply dominant colors from the cover artwork. Controlled in the settings.
*   **Elevated Card UI**: Material 3 elevated specifications with standard typography and smooth shared-element transitions from the library list.

#### Reader
*   **Adaptive Backgrounds**: "Blooming" gradient backgrounds that sample edge colors in real-time for both Paged and Panel modes.
*   **High-Performance GPU Upscaling**: Integrated NCNN-powered upscaling (Waifu2x, RealCUGAN, RealSR, Real-ESRGAN) specifically optimized for Android GPU hardware. upscaling is really good but may require some time to complete depending on your mobile cpu/gpu. there are page upscaling indicators showing you what is goingn on.
*   **Swiping Navigation in Page Mode**: swiping now work and allow you to move forward/backward pages smoothly.
*   **Improved Panel Navigation**: Smooth pan-and-zoom animations to eliminate transition jars. Ability to show the full screen before and/or after the panels for context, fixed several issues when changing from portrait to landscape.
*   **Modern Controls**: Floating progress slider (May 2025 M3 spec), dedicated settings FAB and shortcuts in floating menu
*   **Save Current Image**: long-press quick-save to Downloads
*   **Double Tap to Zoom**: ability to configure double-tap to zoom for panel and page modes (make tap navigation a bit slower)
*   **Additional Tap to Navigate Options**: tap left to back and right to forward, tap right to forward and left to back, tap top side to back and lower side to forward, tap lower side to back and top to forward.
*   **Always On**: setting to prevent the screen to turn off when in the reader.

#### Settings Page
*   **Nicer Navigation page**: Refactored menu structure for a more moden m3 look.
*   **New Visual Toggles**: Immersive color strength sliders, unified app-wide accent color presets, and a master toggle for the "New Library UI".
*   **Deep Customization**: Per-mode toggles for tap-to-zoom, configurable tap navigation zones with visual diagrams, and granular adaptive background settings.

<details>
  <summary>Immersive Screenshots</summary>
   <img src="/screenshots/Immersive%2001.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2002.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2003.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2004.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2005.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2006.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2007.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2008.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2009.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2010.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2011.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2012.jpeg" alt="Komelia" width="270">  
   <img src="/screenshots/Immersive%2013.jpeg" alt="Komelia" width="270">  
</details>


---

## Many thanks to these projects, I used them as inspiration and shameleslly use code from them

*   **Storyteller**: An Amazing application to merge audiobooks and epubs to a simless "kindle whisper" like experiense. I used the epub3 engine and added my controls on top.  https://gitlab.com/storyteller-platform/storyteller
*   **waifu2x ncnn Vulkan**: A cool little project that provided ncnn implementation on android, I integrated it into the reader to provide the upscaler functionality and to understand how to manage the ncnn models with gpu acceleration on android- it was very easy to integrate it into Komelia. https://github.com/nihui/waifu2x-ncnn-vulkan
*   **RealSR-NCNN-Android**: This one provided many NCNN models on android and I used it in combination with the waifu2x code to add RealSR, RealCUGAN etc modelsn. https://github.com/tumuyan/RealSR-NCNN-Android


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