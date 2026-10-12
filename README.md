# Kadamm for Android

Kadamm is a native Android walking and running tracker. It records outdoor routes with GPS and OpenStreetMap road tiles, and supports indoor/treadmill sessions using the phone step sensor and a configurable stride estimate. Sessions are stored locally. Google sign-in is not integrated.

The app includes Light, Dark, and System themes; configurable daily step and distance targets; indoor stride calibration; an activity calendar and history; weekly activity charts; estimated calories; and walk/run classification. During outdoor sessions the map follows location and shows GPS accuracy. Indoor sessions do not require GPS and do not create a route map. Users can correct estimated treadmill distance from the treadmill display after finishing.

Daily steps come from the phone's hardware step counter (`TYPE_STEP_COUNTER`), read by `DaySteps` while the app is open, during sessions, and every 15 minutes in the background by `StepSyncJob`. This needs the Physical activity permission and no other app. Counting starts from the first reading after the permission is granted, so steps taken earlier that day are not included. Android only guarantees the hardware counter keeps running while some app is listening; on Samsung phones Samsung Health keeps it running.

Android Health Connect is an optional second source. The day's total is the larger of the phone count and the Health Connect count, never their sum. Kadamm requests only READ_STEPS and aggregates records outside Kadamm session time windows, then adds verified session steps. Samsung Health data must first be shared to Health Connect in Samsung Health settings; granting Kadamm access to Health Connect alone does not expose Samsung Health's private total. Health Connect access is optional; denial or provider unavailability leaves session-based data available. The Health Connect implementation uses the AndroidX 1.1.0-alpha12 client to remain compatible with this project's Android SDK 35 / Android Gradle Plugin 8.7.3 toolchain. See the Android Developers guidance for Health Connect step access: https://developer.android.com/health-and-fitness/health-connect/features/steps

The map uses standard OpenStreetMap tiles. They do not provide satellite imagery; satellite mode is unavailable until a separately licensed provider and credentials are configured.

Calories are estimates (65 kcal/km). Indoor distance is marked estimated until corrected and uses the user-configured stride length (default 0.72 m per step). Without an available step sensor, indoor step and distance tracking are unavailable rather than fabricated.

## Build

Run `./build.sh` to build with the bundled toolchain in `tools/android-build` (JDK 17, Android SDK 35, Gradle 8.9). The APK is copied to `releases/kadamm-v<version>.apk`. `./build.sh install` also installs it on a USB-connected phone. The folder also opens in Android Studio. The application ID remains com.stride.run so updates preserve installed app data.

Kadamm 5.0 is versionCode 12. Every released APK is kept in `releases/`.

## Device capabilities

- Outdoor recording requires location permission and an enabled location provider.
- All-day step counting and indoor recording require Android activity recognition permission and a hardware step counter (indoor sessions can also use a step detector).
- Health Connect requires the provider to be installed and updated, plus user-granted step access.
- OpenStreetMap tiles require internet access. Route records and activity history remain stored on device.
