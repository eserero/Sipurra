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
