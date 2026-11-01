# Kadamm project context

Baseline reference, written from the code at v7.3 (versionCode 29, commit edb530f, 2026-11-01), updated for 7.5. `CLAUDE.md` has the build and release rules; this file maps the code.

## Overview

Kadamm is a native Android walk/run tracker (application id `com.stride.run`, originally "Stride"). It records outdoor GPS sessions on OpenStreetMap tiles and indoor/treadmill sessions from the step sensor, and keeps an all-day step total. It has no backend: all data is stored on the phone in SharedPreferences.

| Area | Technology |
|---|---|
| Languages | Java 17 (UI, service, logic), Kotlin 2.0.21 (Health Connect only) |
| UI | Plain Android `View`s built in code; no XML layouts except notifications and widgets (`res/layout`). No Compose, no AppCompat, no Material library |
| Build | Gradle 8.9 and AGP 8.7.3, run offline from `tools/android-build` (JDK 17, SDK 35; not in git). minSdk 26, target/compile 35 |
| Dependencies | `androidx.health.connect:connect-client:1.1.0-alpha12`, `androidx.activity:activity-ktx:1.9.3`, `play-services-location:21.3.0` (activity recognition only) |
| Persistence | SharedPreferences holding JSON strings |
| Network | OSM tiles only (`https://tile.openstreetmap.org/`, `RouteView`), cached in `cacheDir/osm-tile-cache` |
| Auth | Local, single account per install (`LocalAccount`: PBKDF2-SHA256, 120k iterations, random salt). Guest mode. No server |
| Tests | `tests/e2e.py`: adb + uiautomator UI tests on a real phone. No unit tests |

## Entry points

- `MainActivity`: the launcher activity. Intent extras: `page` (opens a page), `startWalk` (starts a walk).
- `RecorderService`: foreground service (`location|health`) that records a session.
- `StepSyncJob`: periodic JobService (15 min, persisted) that samples steps, then runs Health Connect sync, reminders and widgets.
- `AutoTrack.Receiver`: boot, package-replaced and activity-transition broadcasts.
- `StepsWidget`, `WeekWidget`: app widget providers.
- `HealthConnectActivity` (consent and sync screen), `HealthConnectRationaleActivity` (privacy rationale; also the `VIEW_PERMISSION_USAGE` alias).

## Code map (`app/src/main/java/com/stride/run`)

| File | Responsibility |
|---|---|
| `MainActivity.java` (~1240 lines, ~90k tokens, mostly dense one-liners) | Every screen, navigation, drawer, swipe pager, auth overlay, intro animation, live overlay, full-screen map, share, backup/restore, permissions, reports, achievements |
| `RecorderService.java` | Session state machine (start, pause/toggle, stop, discard, set type, restore), GPS smoothing, altitude, cadence-based walk/run detection, step sensor, auto-finish, live notification (Android 16 Live Update / Now Bar promotion) |
| `DaySteps.java` | Hardware step counter → per-day deltas (`kadamm_steps.days`); **the single place that decides a day's total** (`todayTotal`, `allDayTotals`), today's distance (`distanceToday`), active calories (`activeKcalToday`, never HC total calories) and move minutes (`moveMinutesToday`); streaks; goal notifications |
| `HealthSync.kt` | Health Connect read: per-source aggregation (takes the highest single source, not the HC sum), past days, active minutes, per-session windows (re-read up to 6 h after a session ends) |
| `HealthConnectActivity.kt`, `HealthConnectRationaleActivity.kt` | HC consent UI and rationale |
| `AutoTrack.java` | Activity Recognition transitions → starts `RecorderService` with `auto=true`; `autoStopMin` setting |
| `Reminders.java` | Inactivity and evening nudges, daily summary, weekly report notifications |
| `Messages.java` | Phrase rotation so a notification wording isn't repeated in a day (`kadamm_messages`) |
| `Coach.java` | On-device "insight" sentences for the Today summary (no AI service) |
| `Insights.java` | Route analysis (pace, elevation, splits, records) and saved routes (`kadamm_routes`) |
| `Progress.java` | Date ranges for reports, period summaries, 16 badges, challenges (`kadamm_badges`) |
| `Calories.java` | MET-based calorie estimate (weight from settings) |
| `LocalAccount.java` | Local sign-up and sign-in |
| `KadammTheme.java` | The only color palette: semantic tokens for light/dark |
| `PullDown.java` | Swipe-down-to-close gesture used by sub-pages, the live screen and the start sheet |
| Views | `MiniChartView` (tile visuals: progress, mini bars, split, streak chain), `RouteView` (OSM tile map, route, markers; pan/zoom; static `gestureActive`), `ProgressRingView`, `WeeklyActivityView`, `ActivityChartView`, `BarChartView`, `SessionChartView`, `DayCellView`, `ConfettiView`, `SeekBarControl` |
| Sharing | `ShareCard` (session image), `ReportCard` (report image); `MainActivity.shareSheet` (WhatsApp / More apps / Copy for any text or file), `showSharePreview` (images: WhatsApp, Save to Photos, More), `shareApp` (sends the installed APK). Files go through the FileProvider `com.stride.run.files` (`cache/share/`); the manifest `<queries>` lists WhatsApp so it can be detected |
| Widgets | `StepsWidget`, `WeekWidget` (RemoteViews from `res/layout/widget_*.xml`) |

## Navigation and UI

- One activity. `page` holds a string; `renderPageNow()` clears `content` and calls `renderX()`. Pages: Today, History, Profile (bottom-nav tabs, swipeable via `dispatchTouchEvent`), plus Goals, Health, Routes, Notifications, Reports, Achievements, Permissions, Auto, Storage and Detail (session; uses `lastSession`). `onBackPressed` handles overlays and pages.
- Overlays on `root`: drawer (`drawerLayer`), live session (`liveLayer`), full-screen map (`mapLayer`), auth (`authLayer`).
- Helpers to reuse: `tx`, `col`, `row`, `lp`, `dp`, `shape`, `panelShape`, `card`, `button`, `rippleOn`, `icon/iconRes`, `blend`, `brand(eyebrow,headline,sub)`, `detailRow`, `sessionRow`, `healthRow`, `metric`, `value`, `dialog()`, `toast`.
- Theme: `applyTheme()` copies `KadammTheme` tokens into fields (`bg`, `panel`, `raised`, `lime` = blue accent, `violet`, `ink`, `muted`, `line`, `positive`, `warning`, `critical`). Modes: light, dark, system (`kadamm_settings.themeMode`). Map style: Auto / Light / Dark.
- Conventions: cards with radius 16 and a 1dp border, 48–54dp touch targets, content descriptions on controls, animations skipped when `animator_duration_scale` is 0 (`reducedMotion`).

## Data and storage

All data is in SharedPreferences, mostly JSON strings:

| Prefs file | Keys / contents |
|---|---|
| `stride_state` | Live session state written by `RecorderService` and polled every second by `MainActivity.updateLive`: `active`, `paused`, `elapsed`, `distance`, `steps`, `currentType`, `activityMode` (outdoor/indoor), `gpsFix`, `latitude`/`longitude`/`accuracy`, `walk/runDistanceKm`, `walk/runDurationMs`, `elevGainM`, `speedKmh`, `moving`… |
| `kadamm_history` | `sessions`: a JSON array of session objects, appended in chronological order. Fields: `id`, `day` (yyyy-MM-dd), `date`, `time`, `type` (Walk/Run/Mixed), `distanceKm`, `durationMs`, `steps`, `stepsAvailable`, `stepsEstimated`, `distanceEstimated`, `distanceCorrected`, `calories`, `walk/run*`, `route` (JSON string of points `{lat,lon,a,s,t…}`), `auto` |
| `kadamm_steps` (`DaySteps.PREFS`) | `days` {day: steps} from the phone counter, `lastCounter`, `startedAt`… |
| `kadamm_health` | `healthConnected`, `healthDate`, `healthDayTotal`, `healthDays` {day: steps}, `healthPhoneAtSync`, `healthSyncedAt`, `hcBackground`, `healthActiveMin` |
| `kadamm_settings` | `stepGoal` (10000), `themeMode`, `strideLengthCm`, `weightKg`, notification switches and times, `autoTrack`, `autoStopMin`, challenge targets, permission-asked flags |
| `kadamm_account` | `name`, `email`, `salt`, `hash`, `loggedIn`, `guest` |
| `kadamm_routes`, `kadamm_badges`, `kadamm_messages` | Saved routes, badges already seen, notifications sent today |

- Step rule: the day total is `max(phone counter, Health Connect live, session steps)`, never a sum (`DaySteps.todayTotal`, `allDayTotals`, `merge`).
- Backup (`MainActivity.onActivityResult`/`restore`): a JSON file through SAF with `kadamm_history.sessions`, `kadamm_routes.routes`, `kadamm_steps.days`, `kadamm_health.healthDays` and `settings`. Restore merges sessions and routes by `id`, keeps the higher steps per day, and overwrites settings.
- `android:allowBackup="true"`: Android auto-backup can include all prefs.

## Main flows

1. **Start session**: Today → activity choice (sets `activityType`, `stride_state.activityMode`) → `onRecord()` checks GPS and runtime permissions → `command(ACTION_START)` → `RecorderService.startSession` → GPS and step listeners write to `stride_state` → the 1 s `refresh` runnable updates the UI → `openLive()` → `askBackground()`.
2. **Finish**: `finishActivity()` → `ACTION_STOP` → `stopSession()` → `save()` appends the session to `kadamm_history` (auto sessions under 3 min, 200 m and 300 steps are dropped) → after 500 ms the Detail page opens with the last session.
3. **Auto-track**: transition broadcast → `AutoTrack.Receiver` → `RecorderService` with `auto=true` → `checkAutoFinish` stops it after `autoStopMin` without movement.
4. **Steps in the background**: `StepSyncJob` → `DaySteps.sample` → `HealthSync.refresh` (if background read is granted) → `Reminders.check` → `StepsWidget.refresh`.
5. **Process death**: `RecorderService.recover()` is called from `MainActivity.onCreate`; `ACTION_RESTORE` restarts from `stride_state`.

## Critical shared dependencies

- `stride_state` keys are a contract between `RecorderService`, `MainActivity`, `DaySteps`, `Calories`, `Reminders` and `AutoTrack`. Renaming one breaks several screens silently, because missing keys read as defaults.
- The session JSON schema is read by MainActivity, DaySteps, Insights, Progress, Coach, Reminders, widgets, ShareCard and backup. Old sessions may lack newer fields, so readers use `optX` with fallbacks (for example calories default to `distanceKm*65`).
- `dateKey(o)` uses `day`, falling back to `date`. Days are local-time `yyyy-MM-dd` (`Locale.US`).
- Several features assume `history()` is chronological ("last 3", `h.length()-1` after finish).
- `DaySteps.allDayTotals` feeds the calendar, streaks, weekly chart, reports, badges and widgets.
- `KadammTheme` is the only palette. Views take it through `chartTheme()`.
- `RouteView.gestureActive` (static) gates the tab-swipe pager.
- `APP_VERSION` in MainActivity must match `version.properties`; it also goes into backups.

## Known issues and risks (from the code; not fixed)

Confirmed in the code:
- MainActivity defines methods that are never called: `renderActivityInsights`, `calendarColor`, `distanceOn`, `smallChoice`, `todayStepsByType`, `todayStepsFor`, `runDistanceOn`, `tint`, `showHealthConnectInfo`. The `ActivityChartView` it creates is therefore unused at runtime.
- `restore()` only re-applies Boolean, Integer and String settings; Long and Float values in the backup are skipped. It also swallows every exception, so a partial restore still shows "Backup restored".
- Restored sessions are appended after the existing ones, so `kadamm_history` can end up out of chronological order.
- `distanceAll()` calls `optJSONObject(i).optDouble` without a null check.
- Errors are mostly swallowed (`catch(Exception ignored){}`) in persistence, permissions and HC sync, so failures are silent.
- `saveSession` and other writes rewrite the whole sessions JSON string on every save, which grows with route data. `history()` re-parses it on most renders, several times per Today render.

Potential risks:
- The whole UI is rebuilt on every `renderPageNow()`, with dense single-line methods, so diffs to MainActivity need care.
- Health Connect uses an alpha client (`1.1.0-alpha12`).
- `ACCESS_BACKGROUND_LOCATION`, `POST_PROMOTED_NOTIFICATIONS` and the Samsung-specific behaviour depend on OEM and OS version, and are only verified on the SM-S721B / Android 16.
- The local account is a convenience lock, not a security boundary: the data is not encrypted and is not tied to the account.
- Builds use the debug signing key (intended, to keep installed data across updates).
- Release APKs (~100 MB total) are committed to git.
- `res/font/great_vibes.ttf` and `parisienne.ttf` (from Aalap) draw the "crafted by V" signature added at the end of every page by `renderPageNow`.

## Baseline status (2026-11-01)

- Build: `gradle --offline assembleDebug` succeeded (up to date). The output is byte-identical to `releases/kadamm-v7.3.apk`.
- Device: SM-S721B is connected over adb with v7.3 (versionCode 29) installed.
- `tests/e2e.py` was **not run**: it drives the real phone, installs a mock GPS provider and records and deletes a session. There are 8 tests: launch, drawer, swipe, health_screens, sync, step_permission_states, widget, recording.
- Not inspected line by line: the full rendering bodies of the Reports, Achievements, Notifications, Routes, live overlay and auth screens in MainActivity; drawing code in the custom views; the HealthSync aggregation internals; and the full RecorderService walk/run detection. They were mapped by method and data flow only, so re-read them before changing them.
