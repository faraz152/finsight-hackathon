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

## Test Lab (end-to-end capture test, no laptop needed)
Privacy tab → **Test Lab** card (top). It posts REAL notifications from FinSight on channel `finsight_test_bank` ("Test bank alerts"); `NotificationCaptureService` accepts our own package ONLY on that channel, so they go NotificationManager → listener → `Repository.ingest` like a real bank alert.
- Needs notification access ON (card warns + "Enable" button) and, on Android 13+, `POST_NOTIFICATIONS` (asked on the first Test Lab tap).
- Chip = one sample; "Send all samples" = burst, 1.5 s apart. "Last captured" shows the newest 5 notification transactions (merchant, amount, category, `notification`, `merged 2x` for the duplicate pair) + scam flags. OTP is dropped, so it shows nothing.
- Samples (sender, `{TID}` template, expected outcome): `commonMain/.../data/TestSamples.kt`; every send gets a fresh 12-digit TID (fingerprint `ref:<TID>`), the duplicate pair reuses one TID → merged. `TestSamplesTest` runs all of them through `Repository.ingest`.
- Code: `androidMain/.../android/TestNotifier.kt` (channel + `send` / `sendBurst`), wired via `AppState.sendTestNotification/sendTestBurst/requestPostNotifications` in `FinSightApp.kt` + `MainActivity.kt`.
- Logs: `$ADB logcat -s FinSight` → `captured from com.finsight.app → Accepted(...)`.

From the laptop instead (posts as `com.android.shell`, debug builds only):
```bash
scripts/send_test_sms.sh 10 1.5      # 10 random samples, 1.5 s apart, fresh TIDs; ADB=... to override adb path
```
Keep its `SAMPLES` in sync with `TestSamples.kt` (`TestSamplesTest` checks it).

## Demo replay
Home → "Import demo SMS" replays `assets/sms_test_templates.json` (58 messages) through the pipeline.

## On-device Gemma (optional, LiteRT-LM)
Library: `com.google.ai.edge.litertlm:litertlm-android:0.8.0` (CPU backend). The model is never in the APK; no file → app runs rules-only, nothing breaks.

1. On huggingface.co, log in and accept the Gemma licence at https://huggingface.co/litert-community/Gemma3-1B-IT (gated).
2. Download **`gemma3-1b-it-int4.litertlm`** (~584 MB, generic CPU/GPU build; NOT the `_sm8xxx`/`_mt69xx`/`Tensor` NPU builds, NOT `.task`):
   ```bash
   # needs a HF token with read access: huggingface-cli login, or export HF_TOKEN=hf_...
   curl -L -H "Authorization: Bearer $HF_TOKEN" -o ~/Downloads/gemma3-1b-it-int4.litertlm \
     https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm
   ```
3. Install the app once (creates the app's external files dir), then push under the fixed name `model.litertlm`:
   ```bash
   ./gradlew :composeApp:installDebug
   $ADB shell mkdir -p /sdcard/Android/data/com.finsight.app/files
   $ADB push ~/Downloads/gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.finsight.app/files/model.litertlm
   $ADB shell am force-stop com.finsight.app && $ADB shell am start -n com.finsight.app/com.finsight.android.MainActivity
   ```
   Use `installDebug` / `adb install -r` afterwards; **uninstalling deletes the model**.
4. Verify: `$ADB logcat -s LiteRtLlm` → `Gemma loaded from model.litertlm in N ms`, then `Gemma <ms> → food` per reviewed transaction. Import demo SMS: uncertain rows (yellow/red) get a "Gemma: x; rule: y → …" line in their explanation and source HYBRID/LLM. `LiteRT-LM load failed` → wrong file/format; the app stays rules-only.
5. Too slow (> ~3 s per call)? It runs in the background one transaction at a time, so the UI is unaffected; the demo still works rules-first.

## Gemini (optional, for Coach HYBRID/CLOUD)
`finsight-app/local.properties`: `GEMINI_API_KEY=...` and optional `GEMINI_MODEL=gemini-3.8-flash`. Rebuild. No key → on-device answers only (badge "On-device").

## Common failures
- Plugin/dep resolution offline: drop `--offline`, needs network once.
- `SDK location not found`: `local.properties` needs `sdk.dir=/Users/maqsad/Library/Android/sdk`.
- JVM target mismatch: module uses JVM 11 target; keep `compileOptions` and `jvmTarget` both 11.
- Compose compiler error: Kotlin and `org.jetbrains.kotlin.plugin.compose` must both be 2.0.21.
- Don't upgrade versions during the hackathon.
