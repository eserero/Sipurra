# Upstream Update Report (0.18.4 -> 0.18.5)

This document summarizes all changes from `upstream/main` that have occurred since the fork diverged.

## 1. Core Dependency Updates (`gradle/libs.versions.toml`)
Upstream has performed a major refresh of the dependency stack.

| Dependency | Old Version | New Version |
| :--- | :--- | :--- |
| **Kotlin** | 2.3.0 | **2.3.21** |
| **Compose Multiplatform** | 1.11.0-alpha01 | **1.11.0-beta03** |
| **Compose Material3** | 1.11.0-alpha01 | **1.11.0-alpha07** |
| **Android Gradle Plugin** | 8.12.3 | **8.13.2** |
| **Ktor** | 3.3.3 | **3.4.3** |
| **Kotlinx Serialization** | 1.9.0 | **1.11.0** |
| **Kotlinx IO** | 0.8.2 | **0.9.0** |
| **Exposed (DB)** | 1.0.0-rc-4 | **1.2.0** |
| **Coil (Images)** | 3.3.0 | **3.4.0** |
| **AndroidX Core KTX** | 1.17.0 | **1.18.0** |
| **AndroidX Activity** | 1.11.0 | **1.13.0** |
| **Gradle Wrapper** | 8.11 | **8.12** |

## 2. Critical Logic Fixes
### Login Stability (`LoginViewModel.kt`)
*   **Change:** Replaced `offlineUsers.first { ... }` with `offlineUsers.firstOrNull { ... }`.
*   **Impact:** Prevents a `NoSuchElementException` crash during the login flow if a server entry exists in the local database but has no associated offline user profiles.

## 3. Native & ONNX Runtime (AI Scaling)
These changes improve the stability and compatibility of the built-in upscaling features.

*   **`komelia_onnxruntime.c`**: Added robust handling for `OrtMemoryInfo` and `OrtArenaCfg`. It now explicitly creates memory info objects to prevent segmentation faults during tensor allocation on certain platforms.
*   **`onnxruntime.cmake`**: Updated to fetch a specific patch that enables compatibility with the latest Dawn (WebGPU) headers.
*   **`DesktopOnnxRuntimeInstaller.kt`**: Refined the library detection logic to correctly identify platform-specific extensions (`.dll`, `.so`, `.dylib`) when verifying the installation of the ONNX runtime.

## 4. UI Polish & Layout
*   **`MainScreen.kt`**: Fixed drawer padding issues. The library list drawer now respects window insets more accurately, preventing UI elements from being hidden behind system bars or cut off.
*   **`NavigationMenuContent.kt`**: Added vertical padding to the navigation menu header to improve visual balance.
*   **`PosterTab.kt`**: Minor alignment tweak for poster previews.

## 5. Versioning
*   **App Version**: 0.18.5
*   **Version Code**: 19
