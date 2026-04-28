# Upstream Merge Plan (0.18.4 -> 0.18.5)

This plan outlines the phased approach for merging critical updates from the upstream repository into our forked codebase, ensuring stability and compatibility with our local enhancements (such as multi-server support).

## Phase 1: Login Stability & Multi-Server Verification
**Objective:** Ensure the login flow is robust and compatible with the latest Komga versions and our multi-server implementation.

### Changes
*   **Verify Fix:** Confirm `offlineUser.value = offlineServer?.let { server -> offlineUsers.firstOrNull { it.serverId == server.id } }` in `LoginViewModel.kt`.
    *   *Status:* Already applied in local `main`.
*   **Review:**
    *   Ensure `firstOrNull` correctly handles cases where a server profile exists but no offline user data has been synced yet.
    *   Verify that `onServerProfileSelect` correctly updates the `url` and `user` fields, triggering the reactive check for `offlineUser`.

### Verification & Testing
- [ ] **Manual Test (Online):** Log in to a newly added server profile.
- [ ] **Manual Test (Offline):** Switch to a server that has offline content and verify the "Go Offline" button appears correctly.
- [ ] **Manual Test (Edge Case):** Switch to a server that has NO offline content and verify the app doesn't crash (testing the `firstOrNull` fix).

---

## Phase 2: Dependency Upgrades
**Objective:** Update the core tech stack to match upstream version 0.18.5.

### Changes
*   **Update `gradle/libs.versions.toml`:**
    *   Kotlin: `2.3.21`
    *   Compose Multiplatform: `1.11.0-beta03`
    *   AGP: `8.13.2`
    *   Ktor: `3.4.3`
    *   AndroidX stack (Activity, Core, WorkManager).
    *   Exposed: `1.2.0`
*   **Update `gradle/wrapper/gradle-wrapper.properties`:** Gradle `8.12`.
*   **Update `komelia-app/build.gradle.kts`:** Increment `versionCode` to 19.
*   **Update `AppVersion.kt`:** Set `current` to `0.18.5`.

### Verification & Testing
- [ ] **Clean Build:** Run `./gradlew clean :komelia-app:assembleDebug` to ensure no compilation regressions with Kotlin 2.3.21.
- [ ] **Desktop Build:** Run `./gradlew :komelia-app:run` to verify Compose Multiplatform 1.11.0-beta03.
- [ ] **Regression Check:** Verify basic networking (Ktor 3.4.3) and database operations (Exposed 1.2.0).

---

## Phase 3: ONNX Runtime & Native Stability
**Objective:** Improve stability for AI-assisted features (Panel Detection) on both Android and Desktop.

### Changes
*   **Native Fixes:** Update `komelia-infra/onnxruntime/native/src/onnxruntime/komelia_onnxruntime.c` with robust memory/arena management.
*   **Build Config:** Update `cmake/external/onnxruntime.cmake` to use the patched version compatible with latest Dawn headers.
*   **Installer Logic:** Apply changes to `DesktopOnnxRuntimeInstaller.kt` for improved platform-specific library detection.

### Verification & Testing
- [ ] **Native Build:** Verify that the CMake build for both Android (JNI) and Desktop completes successfully.
- [ ] **Panel Viewer (Desktop):** Enable Panel Viewer and verify detection runs without segmentation faults.
- [ ] **Upscaling (Android/Desktop):** Verify that ONNX-based upscalers still initialize and process images correctly with the new memory info handling.

---

## Rollback Plan
If any phase introduces critical regressions:
1. Revert specific commit for that phase.
2. If dependency updates cause breaking UI changes, revert `gradle/libs.versions.toml` and investigate specific library incompatibilities.