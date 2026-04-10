# Building Komelia APKs

## Debug APK

Already signed with the Android debug keystore — install directly.

```bash
./gradlew :komelia-app:assembleDebug
```

Output: `komelia-app/build/outputs/apk/debug/komelia-app-debug.apk`

---

## Release APK

The release build is unsigned. After building, sign it with the debug keystore.

### 1. Build

```bash
./gradlew :komelia-app:assembleRelease
```

Output: `komelia-app/build/outputs/apk/release/komelia-app-release-unsigned.apk`

### 2. Align and sign

```bash
cd komelia-app/build/outputs/apk/release

~/Android/Sdk/build-tools/35.0.0/zipalign -p -f 4 \
  komelia-app-release-unsigned.apk \
  komelia-app-release-aligned.apk

~/Android/Sdk/build-tools/35.0.0/apksigner sign \
  --ks ~/.android/debug.keystore \
  --ks-pass pass:android \
  --ks-key-alias androiddebugkey \
  --key-pass pass:android \
  --out komelia-app-release-signed.apk \
  komelia-app-release-aligned.apk
```

Output: `komelia-app/build/outputs/apk/release/komelia-app-release-signed.apk`

---

## If Gradle serves a cached APK after changing assets/images

Force the package step to re-run:

```bash
rm -f komelia-app/build/outputs/apk/release/komelia-app-release-unsigned.apk
./gradlew :komelia-app:packageRelease
```

Then sign as above.

---

## Installing via ADB

```bash
adb install komelia-app/build/outputs/apk/release/komelia-app-release-signed.apk
```

If you get "INSTALL_FAILED_UPDATE_INCOMPATIBLE" (signature mismatch with an existing install):

```bash
adb uninstall io.github.snd_r.komelia
adb install komelia-app/build/outputs/apk/release/komelia-app-release-signed.apk
```

---

## Creating a New Release in GitHub

When you are ready to create a new release, follow these steps:

### Part 1: Updates in the Application Codebase
1. **Update the App Version Name:**
   In `gradle/libs.versions.toml`, update the `app-version` variable.
   ```toml
   app-version = "0.18.5" # Example new version
   ```
2. **Update the App Version Code:**
   In `komelia-app/build.gradle.kts`, increment the `versionCode` (found around line 116).
   ```kotlin
   versionCode = 19 # Must be higher than the previous release
   ```
3. **Update the Hardcoded Version:**
   In `komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt`, update `AppVersion.current`.
   ```kotlin
   val current = AppVersion(0, 18, 5) // Example new version
   ```

### Part 2: Building the Artifacts
Follow the instructions above in **Release APK** to create the release APK:
- Run `./gradlew :komelia-app:assembleRelease`
- Align and Sign the APK to produce `komelia-app-release-signed.apk`.

### Part 3: Updates in GitHub (Creating the Release)
1. **Create the Tag:** In your `eserero/Komelia` repository, create a new tag matching the version you just set in the code (e.g., `0.18.5` or `v0.18.5`).
   **Note:** Both formats are supported, but it's recommended to stay consistent. The application will automatically remove a leading `v` if present.
2. **Draft a New Release:** Go to GitHub Releases and draft a new release pointing to the tag you created.
3. **Upload Assets:** Drag and drop your compiled `komelia-app-release-signed.apk` (and any desktop artifacts like `.msi` or `.deb` if applicable) into the release assets section. 
   **CRITICAL:** Ensure the Android file ends with `.apk`. The Android auto-updater specifically looks for `assets.firstOrNull { it.name.endsWith(".apk") }`.
4. **Publish Release:** Add your release notes and publish. The application will now detect this as the newest update.
