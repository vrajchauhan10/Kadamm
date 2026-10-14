# Changelog

## 5.2 (versionCode 14)

- Daily steps now match Samsung Health. Health Connect was adding Samsung Health and the phone's own step tracker together (13 Oct showed 28,425 instead of 15,789). Kadamm reads each source separately and uses the most complete one, for today, past days, distance and calories. It syncs every time the app opens, and the phone sensor keeps the number rising between syncs.
- Kadamm session steps and calories are shown as part of the day ("In Kadamm sessions: …"), never added on top.
- Heart rate and sleep removed from the dashboard and from the permissions requested. Active calories are read when allowed.
- New live session screen in Kadamm's black, white and blue map theme: full-screen greyscale map with a blue route, time, average pace, distance, steps, calories and speed, a large Pause button, then Resume and Finish. It opens on Start, and again whenever the app is reopened during a session.
- Share now creates an image in the same theme: route map, Kadamm logo, and distance, time, pace, steps, calories and elevation, with a preview before sharing.
- Sessions keep running in the background: a refused restart no longer ends the session, an interrupted session is resumed for up to 90 minutes, and Kadamm asks once to be exempt from battery optimisation. Tracking readiness shows the background status.
- Redesigned Goals & targets: today's progress against each goal, colour-coded goal cards with − / + steppers, tinted sliders and presets. Move-minute and stride goals included.
- Dashboard tiles each have their own colour and icon.
- Back from Goals, History, Profile or the step-sources page returns to Today instead of closing the app; back closes the live screen and the menu first.
- New Kadamm logo (two footprints mid-stride) as the launcher icon and in the app header.

## 5.1 (versionCode 13)

- Fixed the app appearing frozen: if Android killed the recorder while a session was active, the saved state stayed "active" forever, so the timer never moved and Pause/Finish did nothing. On launch Kadamm now resumes a session interrupted in the last 10 minutes and saves an older one to History. A system restart of the recorder no longer starts a blank session.
- Fixed outdoor walking distance staying at 0.00 km: the movement reference was reset on every GPS fix, so a walker never crossed the 4 m threshold.
- GPS: only fixes within 20 m accuracy are used, positions are Kalman-smoothed, sustained accurate movement counts without step evidence, and elevation gain, current speed and moving time are recorded. A prompt appears when Location is off.
- Health Connect also reads distance, calories burned, heart rate and sleep (each optional), shows them on the dashboard, and has a Sync now control with last-synced time. Consent and rationale screens follow the app theme; dialogs match Light/Dark.
- New palette (blue, green, orange), a second ring for move minutes with its own goal, heart-rate and sleep cards.
- Drawer redesigned: full-width 52 dp rows with ripple and an active indicator; the Kadamm wordmark is a 48 dp button.
- Main tabs follow the finger when swiping and slide between pages; buttons and rows have press feedback.
- Home-screen widget with the step and move-minute rings.
- StrictMode (network and slow calls on the main thread) is enabled in debug builds.
- `tests/e2e.py` runs on-device checks for launch, drawer, swipe, health screens, sync, permission states, widget and recording.

## 5.0 (versionCode 12)

- Kadamm now counts steps all day on its own, using the phone's hardware step counter. Daily steps no longer depend on Samsung Health sharing with Health Connect. A background job reads the counter every 15 minutes, so the total stays current while the app is closed and survives a reboot.
- Today's total is the most complete of the phone counter and Health Connect (never their sum), with Kadamm session steps filling session windows. The dashboard, calendar, weekly chart, streaks and the live notification all read the same figure.
- Health Connect now refreshes silently each time Kadamm opens and every two minutes while it is on screen, and imports up to 30 past daily totals for the calendar. The connection screen shows which apps supplied the steps.
- New Today screen: greeting header with a step-source chip, an animated goal ring (cyan, violet, amber, then green at 100%), percent complete, steps remaining, and a Start activity button, followed by a bento grid of distance, active time, calories, sessions, walking, running, streak and 7-day average.
- "Daily goal achieved" and "You're making progress" notifications, each at most once a day.
- Weekly chart shows full-day steps against a dashed goal line. Streaks count current, longest and total goal days, and an unfinished today never breaks a streak.
- Saved sessions can be renamed, shared as text, and deleted.
- The map shows your surroundings before you start instead of an empty grid, and route lines scale with screen density.
- Fixed: outdoor distance stayed at 0 when Physical activity permission was denied, because an unreadable step sensor was still used to confirm movement.
- Fixed: starting an indoor session without Physical activity permission could crash on Android 14 and later.
- Fixed: Tracking readiness always reported Health Connect as "Not connected".
- Fixed: calendar dates were hard to read on coloured days; day colours are now tinted.
- The Health Connect screen now follows the Light/Dark theme.

## 4.0.3 (versionCode 11)

- Declared Samsung Health package visibility so the Health Connect setup button can open Samsung Health on Android 11 and later.

## 4.0.2 (versionCode 10)

- Daily step progress now combines the latest Health Connect steps outside Kadamm session intervals with saved and live Kadamm session steps.
- The live tracking notification now shows today's total and remaining steps to the configured goal.
- Health Connect explains when Samsung Health has not shared today's steps, links back to Samsung Health, and refreshes when the user returns.
- Active session intervals are excluded during Health Connect aggregation to avoid double-counting session steps.

## 4.0.1 (versionCode 9)

- Added Health Connect's required privacy-rationale activity and Android 14+ permission-usage alias, with an on-device explanation of step-data use.
- Verified the step access request opens the Android Health Connect consent screen on Samsung Android 16.

## 4.0 (versionCode 8)

- Added configurable daily step and distance targets, live distance-goal progress, and indoor stride calibration.
- Added Indoor / treadmill sessions that use phone step sensors for distance estimates without requiring GPS; users can correct estimated distance against treadmill display after saving.
- Added Android Health Connect READ_STEPS permission and a connection/sync screen. Local-day step totals exclude Kadamm session time windows before combining verified session steps.
- Added live session details and Pause/Resume/Finish actions to the persistent tracking notification.
- Added indoor/outdoor mode labels, Android 14+ health/location foreground-service types, and session start/end timestamps.
- Updated the in-app version label to v4.0.


## 3.0 (versionCode 7)

- Added a discreet v3.0 label beside the Kadamm wordmark.
- Added a 10,000-step session-tracking goal, daily metrics, weekly step/distance chart, calorie estimates, and goal streaks.
- Added GPS accuracy/loss feedback, map follow control, route point filtering, sustained-speed walk/run classification, and manual type correction.
- Added discard confirmation, saved walk/run breakdowns, richer session summaries, and activity intensity colors in the calendar.
- Clearly labels step data as Kadamm session-only until Android Health Connect integration is connected; no estimated distance-to-step conversion is used.

## 2.2 (versionCode 6)

- Corrected swipe order: swipe left through Today, History, and Profile; swipe right to return.
- Added visible walking and not-walking status based on GPS movement and step-sensor changes, with on-screen notifications when movement changes.

## 2.1 (versionCode 5)

- Added a profile and today's walking summary drawer from the Kadamm header.
- Added horizontal swipes between Today and History and enlarged bottom navigation icons.
- Applied Android system-bar insets so the header and navigation clear status and gesture bars.
- Calendar cells now emphasize today; long-press a date to see its session count.

## 2.0 (versionCode 4)

- Added shared semantic theme tokens and Light, Dark, and System appearance settings.
- Added activity summaries and distance charts for 7-day, 30-day, 12-month, and all-time ranges.
- Added GPS permission and step-sensor readiness indicators using the device's actual capabilities.
- Refined accessible focus states and theme-aware maps, cards, and navigation.
- Preserved native GPS recording, step tracking, session history, and route summaries.

## 1.1.0 (versionCode 3)

- Renamed the app to Kadamm and refreshed the native dark interface.
- Added today’s walking session and distance totals, Walk/Run selection, and bottom navigation.
- Added a calendar-based session history and a local guest profile.
- Completed activities now save a route and show distance, elapsed time, average speed, pace, and steps.
- Fixed the stop action so Android does not start a new location foreground service while finishing.

## 1.0.1 (versionCode 2)

- Refined route card sizing on phone screens.

## 1.0.0 (versionCode 1)

- Initial native Android run/walk tracker.
- GPS distance and elapsed-time tracking with pause and resume.
- Android step sensor support with distance-based fallback.
- Local guest profile and route visualization.
## 2.2 (versionCode 6)

- Corrected horizontal navigation: swipe left from Today to History to Profile; swipe right to go back.
- Added live walking detection from GPS movement and step sensor activity, with on-screen status and movement notifications.
