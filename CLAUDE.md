# Kadamm

Native Android walk/run tracker (app id `com.stride.run`, originally "Stride"). Java views built in code, no XML layouts; Health Connect code is Kotlin. Sessions and step totals are stored in SharedPreferences on the device.

## Build and install

- `./build.sh` builds offline with the toolchain in `tools/android-build` (JDK 17, Android SDK 35, Gradle 8.9; not in git) and writes `releases/kadamm-v<version>.apk`.
- `./build.sh install` also installs on the USB-connected phone. adb is `tools/android-build/sdk/platform-tools/adb`.
- `python3 tests/e2e.py [name ...]` runs the on-device UI checks (screenshots in `tests/screens/`). It refuses to tap unless Kadamm is in the foreground, so do not run it while the phone is being used.
- The test phone is a Samsung SM-S721B on Android 16. Verify UI changes on it; there is no emulator.

## Releasing a change

Every round of changes gets a new version: bump `version.properties`, set `APP_VERSION` in `MainActivity.java` to match, add a `CHANGELOG.md` entry, and keep the old APKs in `releases/`. Keep the application id and debug signing key so updates preserve installed data.

## Code map

- `MainActivity.java` — all screens (Today, History, Profile, Goals, step sources, session detail). Most of it is dense one-line methods from earlier versions; newer methods are multi-line.
- `RecorderService.java` — foreground service that records a session (GPS, step sensor, notification).
- `DaySteps.java` — all-day step totals from the hardware step counter, and the one place that decides a day's total across phone sensor, Health Connect and sessions. `StepSyncJob` samples it every 15 minutes.
- `HealthSync.kt` / `HealthConnectActivity.kt` — Health Connect step import and consent.
- `RouteView`, `ProgressRingView`, `WeeklyActivityView`, `ActivityChartView` — custom drawn views.

## Rules

- Never show estimated or made-up sensor data as real; show an unavailable state instead.
- Step sources are combined by taking the most complete count, never by adding them.

## Working rules

- Android app, tested on a physical device over USB (adb).
- Work autonomously. Do not ask questions unless blocked. Make reasonable assumptions and list them at the end.
- Read only the files needed. Use grep/glob before opening files. Never re-read unchanged files.
- Edit with small diffs, never rewrite whole files.
- Keep replies short: no explanations of obvious steps, no code echoes.
- Verify every fix on the device (adb install, logcat, screenshots) before moving on.
