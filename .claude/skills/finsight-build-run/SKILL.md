---
name: finsight-build-run
description: Build, install and run the FinSight KMP Android app, post test bank notifications, run demo replay, and fix Gradle/build errors. Use whenever the user asks to build, run, install, test on phone/emulator, or when a Gradle build fails in finsight-app/.
---

# Build & run FinSight

Gradle root: `finsight-app/`. Module: `:composeApp` (Android target only).

## Build / install
```bash
cd finsight-app
./gradlew :composeApp:assembleDebug --offline || ./gradlew :composeApp:assembleDebug
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB devices                                   # must list a device
./gradlew :composeApp:installDebug
$ADB shell am start -n com.finsight.app/com.finsight.android.MainActivity
```
APK: `finsight-app/composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

No phone? Start an emulator: `~/Library/Android/sdk/emulator/emulator -list-avds`, then `emulator -avd <name> &`.

## Xiaomi / MIUI
`INSTALL_FAILED_USER_RESTRICTED` → Developer options: enable "Install via USB" and "USB debugging (Security settings)", keep phone unlocked, tap Install on the prompt.

## Grant notification access (needed for live capture)
In app: Privacy tab → "Enable notification access", or:
```bash
$ADB shell cmd notification allow_listener com.finsight.app/com.finsight.android.NotificationCaptureService
```

## Post a fake bank notification (live-capture demo)
The listener accepts SMS apps + `com.android.shell` in debug builds.
```bash
# Quote the WHOLE command for the remote shell, or the text is split into words ("No amount").
$ADB shell "cmd notification post -S bigtext -t 8558 fs1 'Rs 1,250.00 sent to KFC Gulberg via JazzCash. Fee: Rs 0.00. Balance: Rs 8,750.00. TID: 033050058799.'"
```
Verify syntax on the device with `$ADB shell cmd notification help` if it fails.

## Demo replay
Home → "Import demo SMS" replays `assets/sms_test_templates.json` (58 messages) through the pipeline.

## Gemini (optional, for Coach HYBRID/CLOUD)
`finsight-app/local.properties`: `GEMINI_API_KEY=...` and optional `GEMINI_MODEL=gemini-flash-latest`. Rebuild. No key → on-device answers only (badge "On-device").

## Common failures
- Plugin/dep resolution offline: drop `--offline`, needs network once.
- `SDK location not found`: `local.properties` needs `sdk.dir=/Users/maqsad/Library/Android/sdk`.
- JVM target mismatch: module uses JVM 11 target; keep `compileOptions` and `jvmTarget` both 11.
- Compose compiler error: Kotlin and `org.jetbrains.kotlin.plugin.compose` must both be 2.0.21.
- Don't upgrade versions during the hackathon.
