# AGP Upgrade Analysis & Plan

This document outlines the strategy for upgrading the Android Gradle Plugin (AGP) and the Gradle Wrapper to resolve R8 Kotlin metadata parsing errors caused by a version mismatch with Kotlin 2.3.0.

## 1. Current State vs. Target State

Based on the project's current configuration, the proposed upgrade path is as follows:

| Component | Current Version | Target Version (Proposed) | Notes |
| :--- | :--- | :--- | :--- |
| **Kotlin** | `2.3.0` | `2.3.0` (Keep) | Bleeding-edge; requires a very modern AGP. |
| **Android Gradle Plugin (AGP)**| `8.12.3` | `9.1.0` | Bundles an R8 compiler that natively supports Kotlin 2.3.0 metadata. |
| **Gradle Wrapper** | `9.2.1` | `9.3.1` | AGP 9.1.0 requires at least Gradle 9.3.1. |
| **Compose Multiplatform** | `1.11.0-alpha01` | `1.11.0-alpha01` (Keep) | Monitor for compatibility issues during sync. |

---

## 2. Risk Analysis: What Might Break

An upgrade to AGP 9.x is a major version jump and may require secondary updates to plugins or build scripts.

### 🔴 High Risk
*   **Protobuf Plugin (`com.google.protobuf:0.9.4`):** Major AGP/Gradle upgrades often break older third-party plugins. We may need to upgrade this if configuration fails.
*   **Kotlin Multiplatform (KMP) Integration:** AGP 9.x has stricter rules for KMP. Shared modules (e.g., `komelia-domain:core`, `komelia-ui`) may require minor DSL tweaks for Android targets.
*   **Compose Multiplatform:** The `org.jetbrains.compose` plugin might require a minor bump if it detects an AGP version mismatch.

### 🟡 Medium Risk
*   **Removed Android DSLs:** AGP 9.x removes several deprecated DSL blocks. If `komelia-app/build.gradle.kts` uses older syntax (like `resConfigs` instead of `localeFilters`), it will fail.
*   **R8 "Full Mode" Repackaging:** AGP 9.1.0 enables aggressive repackaging by default. This can break reflection in libraries like Serialization or Ktor. If the app crashes on launch, we must add `-dontrepackage` to `android.pro`.

### 🟢 Low Risk
*   **Java/JDK Requirements:** AGP 9.x requires JDK 17+. Since the project already uses Gradle 9.2.1, the environment is likely already compatible.

---

## 3. Step-by-Step Upgrade Plan

1.  **Upgrade the Gradle Wrapper:**
    ```bash
    ./gradlew wrapper --gradle-version 9.3.1
    ```
2.  **Update `gradle/libs.versions.toml`:**
    Set `agp = "9.1.0"` in the `[versions]` block.
3.  **Run a Configuration Sync:**
    Execute `./gradlew tasks` to verify build script compatibility and identify any plugin breaks.
4.  **Build a Release APK:**
    Execute `./gradlew :komelia-app:assembleRelease` to trigger R8 and verify the metadata errors are resolved.
5.  **Runtime Verification:**
    Install the APK and ensure the app boots and functions correctly (checking for reflection-related crashes).

---

## 4. Rollback Strategy

If the upgrade requires excessive refactoring of the KMP structure or results in unresolvable plugin conflicts, we will:
1.  Revert `gradle/libs.versions.toml` to `agp = "8.12.3"`.
2.  Revert the Gradle wrapper using `git checkout gradle/wrapper/`.
3.  Apply a ProGuard workaround in `komelia-app/android.pro`:
    ```proguard
    -dontwarn kotlin.Metadata
    -keep class kotlin.Metadata { *; }
    ```
