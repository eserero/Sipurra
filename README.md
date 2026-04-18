# Sipurra - Komga media client

## What's New in this Fork
Sipurra is a fork of [Komelia](https://github.com/Snd-R/Komelia) focused on improving the Android experience. It contains all the good in Komelia and adding many new featurs:
*  Completly revamped UX 
*  New epub3 reader supporting immersive Audio + Text reading (based on storyteller)
*  Audio books playback
*  Many new features in the comic reader (AI upscaling, additional navigation options, improved panel mode and more)
*  Much more - read below to find out :-)

### New UX and Themes
*   **Material 3**: Standardized M3 `TopAppBar` with cleaner menu location and a hamburger menu to choose the library. Floating FAB to open the filter dialog. Standardize Navigation Bar.
*   **Smaller Thumbnails**: allowing smaller thumbnails in the setting screen (choose 110) which shows 3 thumbnail per row instead of only two. Thumbnails are also nicer with some options to choose from (text below thumbnail, transparent background for text)
*   **New Dark and Light Themes**: clearer colors, supporting "Haze" effect (transparency with blur) for toolbars and some of the floating elements.

### Library Screen
*   **New screen design**: cleaner headers, easier sorting access and new UX elements. 
*   **"Keep Reading" Strip**: A new horizontal quick-access strip in the series list for your recently read books per library.
*   **Improved Filtering UI**: Improve and cleanup of some of the search elements and fixed some bugs
*   **Library Navigation and Persistence**: clicking on the Library navigation button Automatically remembers the last library you were - so you dont need to choose the library every time.

| Library Light Theme | Library Dark Theme | Library with Continue Reading |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Library Screen Light Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Library Screen Dark Modern theme.jpg" width="250"> | <img src="screenshots/New UI 2/Library Screen Light modern theme with Continue reading.jpg" width="250"> |

### Home Screen
*   **New screen design**: similar to the library screen, Section management and settings moved to a Floating Action Button at the bottom-right for easy one-handed use.
*   **Horizontal Layout**: Organized content into horizontal rows (Keep Reading, On Deck, etc.) for a compact and discoverable dashboard. This is similar to how komga web show it.

### Search
*   **Updated screen design**: similar to library and home screens with Full M3 `SearchBar` implementation with smooth animations, native back-navigation, and clear-text support.

### Immersive Detail Screens (Book, Series, Oneshot)
*   **Immersive Screen**: Full-bleed cover images extending behind information card for a modern, cinematic feel. ability to swipe left right to move between books, floating buttons to easily read and download.
*   **Publisher Icons**: Showing publisher icon in the immersive screen (if there is a match).
*   **Adaptive Card Color**: Detail card backgrounds dynamically sample and apply dominant colors and blurred texture from the cover artwork. Controlled in the settings.
*   **Elevated Card UI**: Material 3 elevated specifications with standard typography and smooth shared-element transitions from the library list.

| Immersive Series (Collapsed) | Immersive Series (Expanded) | Immersive Series (Alt) |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Immersive Series Screen Collapsed.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Series Screen Expanded.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Series Screen Collapsed 2.jpg" width="250"> |

| Immersive Series (Alt Expanded) | Immersive Book View | Immersive Book (Expanded) |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Immersive Series Screen Expanded 2.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Book view.jpg" width="250"> | <img src="screenshots/New UI 2/Immersive Book with expanded button.jpg" width="250"> |

### Image/Comic Reader
*   **New Controls UX**: Aligned with the new epub reader and matching the new dark/light teams and immersive card color. provide easy access to switching between reading modes (Page, continous, panel), turn on/of upscaling and lock screen rotation.
*   **Adaptive Backgrounds**: "Blooming" gradient backgrounds that sample edge colors in real-time for both Paged and Panel modes.
*   **High-Performance GPU Upscaling**: Integrated NCNN-powered upscaling (Waifu2x, RealCUGAN, RealSR, Real-ESRGAN) specifically optimized for Android GPU hardware. upscaling is really good but may require some time to complete depending on your mobile cpu/gpu. there are page upscaling indicators showing you what is goingn on.
*   **Swiping Navigation in Page Mode**: swiping now work and allow you to move forward/backward pages smoothly.
*   **Improved Panel Navigation**: Smooth pan-and-zoom animations. Ability to show the full screen before and/or after the panels for context, fixed several issues when changing from portrait to landscape.
*   **Save Current Image**: long-press quick-save to Downloads
*   **Double Tap to Zoom**: ability to configure double-tap to zoom for panel and page modes (make tap navigation a bit slower)
*   **Additional Navigation Options**: tap left to back and right to forward, tap right to forward and left to back, tap top side to back and lower side to forward, tap lower side to back and top to forward.
*   **Keep Screen Awake**: setting to prevent the screen to turn off when in the reader. (in the image reader setting page)

**Adaptive Backgrounds**
| Light Theme | Dark Theme | Controls Hidden |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Adaptive Background with Light Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Adaptive Background with Dark Modern Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Adaptive Background without controls.jpg" width="250"> |

**GPU Upscaling Comparison**
| Without Upscaling | With Upscaling |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Adaptive backgrouund without upscaling.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - adaptive background with upscaling.jpg" width="250"> |

| Without Upscaling (Controls) | With Upscaling (Controls) |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Comic page with controls without upscaling.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Comic page with controls with upscaling.jpg" width="250"> |

| Without Upscaling (B&W) | With Upscaling (B&W) |
| :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader - Comic page with controls without upscaling B&W.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader - Comic page with controls with upscaling B&W.jpg" width="250"> |

### Epub Reader with support for Epub 3 audio layer and Audio Books
*   **New Epub Viewer**: Completely new epub viewer that can be toggled in settings. based on Storyteller and Redium Kotlin Tookit - support epubs with audio layer to a combined text + Audio reading (You can easily create such books using storyteller
*   **Audio Book player**: integrated audio books player, with chapter navigation , double tap on a text row to choose sentenses to play, including a mini player and integrated full screen "audio book" interface. Audio books must alwasy be inside the book epub file (which is a zip file) and there are 2 modes: 1. if you are using Storyteller to create an immersive reading experience you will get a synchronized text and audio experiece. 2. if you just have an audiobook or audio folder inside your epub then you can use it as a normal audio book without text synchronization.
*   **New controls**: completly new reading controls matching the new comic reader controls - easily select chapter, bookmark, search and lock screen rotation.
*   **New setting screen**: Theme and appearance selection, margins, fonts and audio settings. 
*   **Better Navigation**: Ability to swipe to turn pages (swipe left/right to move about, scroll based reading etc.)
*   **Bookmark and Search**: new implementation of chapter, bookmarking and text search
*   **Keep Screen Awake**: setting to prevent the screen to turn off when in the reader. (in the image reader setting page - both use the same settings)


**Controls and Audio Player**
| Light Theme | Dark Theme | Mini Player |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader with control panel and mini audio player Light Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader with control panel and mini audio player Dark Theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader with mini audio controls.jpg" width="250"> |

**Expanded Audio Player**
| Light Theme | Dark Theme |
| :---: | :---: |
| <img src="screenshots/New UI 2/Epu3 reader expanded audio player light theme.jpg" width="250"> | <img src="screenshots/New UI 2/Epu3 reader expanded audio player Dark theme.jpg" width="250"> |

**Reader Features**
| Table of Contents | Bookmarks | Search |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader Table of Content.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader Bookmarks.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader Search.jpg" width="250"> |

### Settings Page
*   **Nicer Navigation page**: Refactored menu structure for a more moden m3 look.
*   **New Visual Toggles**: Immersive color strength sliders, unified app-wide accent color presets, and a master toggle for the "New Library UI".
*   **Deep Customization**: Per-mode toggles for tap-to-zoom, configurable tap navigation zones with visual diagrams, and granular adaptive background settings.

**Comic Reader Settings**
| Reading Modes | Image Settings | Navigation |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Comic Reader Settings - Reading Modes.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader Settings - Image Settings.jpg" width="250"> | <img src="screenshots/New UI 2/Comic Reader Settings -  Navigation.jpg" width="250"> |

**Epub Reader Settings**
| Appearance | Fonts | Audio |
| :---: | :---: | :---: |
| <img src="screenshots/New UI 2/Epub3 reader settings - Appearance.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader settings - Fonts.jpg" width="250"> | <img src="screenshots/New UI 2/Epub3 reader settings - audio.jpg" width="250"> |

### General improvements
*   **Prefer local files**: If the files are downloaded, they will be used instead of downloading from the server - this is very helpfull for large comics or epub files with audio - which can get pretty large. This is working even in online mode although the app will still go to the server to fetch metadata.
*   **Ability to open local files through android context**: Ability to open cbz and epub files from android. it will not let you manage these files, but it will remember the page and bookmarks based on the file location. maybe in the future I will add more local file management.
*   **Reload local cache**: It is possible to reload files downloaded by a previous installation or to transfer downloaded files to a new device and then just reload them to a new installation of the application. you can do this in the aplication setting offline screen - there is a new button "scan for existing files" - it will link all the existing files to the app and provide a report.




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