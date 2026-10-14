# Changelog

## 6.3 (versionCode 21)

- Rotating the phone no longer rebuilds the screen: the page, scroll position, open menu, live session screen and anything typed stay as they were.
- "Preview notifications" in Notifications sends one sample of each Kadamm notification (live session card, goal reached, progress, time to move, almost there), each titled "Preview", so you can see them before a real one arrives.

## 6.2 (versionCode 20)

- Reminder times are configurable in Notifications. "Time to move": the hours it may remind you (from/until, can span midnight) and how long without steps first (30 min to 2 h). "Almost there": the evening time and how close to the goal (1,000 to 5,000 steps left). Defaults are the automatic times (8 am to 9 pm after 60 minutes; 7 pm at 2,000 steps), with "Reset to automatic".

## 6.1 (versionCode 19)

- Notification settings (menu > Notifications): shows whether notifications are allowed, with an Allow button (Android's permission prompt, or the app's notification settings if it was declined before). Choose what Kadamm sends: details of the live session on the lock screen, daily goal reached, 60% progress, "Time to move" and the evening "Almost there". The reminder switches moved here from Goals & targets.
- Opening animation follows the phone's light or dark mode: it starts on the same colour and logo as Android's own splash screen (white with the blue logo, or black with the black logo), the logo shows as a circle, and the blue circle grows out of it. The app's window colours match too, so there is no white or black flash.

## 6.0 (versionCode 18) — major release

Kadamm 6 brings together everything since 5.2: matched Health Connect steps, the live session screen and lock-screen notification, sharing, the track logo and theme, the ring calendar, walk/run detection, explorable maps, local accounts, and these new features:

- Pace and elevation charts on the session summary, with walking stretches shaded blue and running stretches red. Route points now carry time and altitude, so charts appear for sessions recorded from 6.0 on.
- Personal records on Profile: fastest 1 km and 5 km, longest distance, longest session and most steps in a day. Each opens the session it came from. A new record gets a highlighted card and a burst of confetti the first time you open that session.
- Route library: save a session as a named route ("Morning lap"). Later sessions that follow it (same start and end within 60 m, distance within 15%, same shape) are listed as attempts with your time against your best, on the session summary and on the new Routes page (in the menu), where routes can be renamed or deleted.
- Reminders, with switches in Goals & targets: "Time to move" after an hour without steps (8 am to 9 pm, at most every two hours), and "Almost there" after 7 pm when 2,000 steps or fewer are left. Never during a session.

## 5.5 (versionCode 17)

- New live-session notification: a timer that ticks on its own, distance and pace when collapsed; expanded, it adds steps, active calories, speed, today's steps and a goal progress bar, with Pause/Resume and Finish buttons and a status line (GPS accuracy, paused, indoor). It uses the Kadamm track icon in the status bar.
- The notification is public on the lock screen, so the session can be followed without unlocking. Tapping it opens the live session screen on top of the lock screen; the rest of the app still needs the phone unlocked.
- Notifications use a new silent "Live session" channel at default importance so Samsung shows them in full on the lock screen.

## 5.4 (versionCode 16)

- Health Connect sync now also brings in active minutes for the whole day (minutes with 60+ steps), shown on the Active time tile and counted toward the move-minutes ring, alongside steps, distance and calories.
- Each Kadamm session from the last 30 days is matched against Health Connect: the steps and active calories it recorded during that session's time window are shown on the session summary.
- Session calories are now worked out from pace, time and your body weight (standard MET values for walking and running, active calories only), instead of a flat 65 kcal per km. Treadmill-corrected sessions are recalculated. Body weight can be set in Goals & targets (default 70 kg).

## 5.3 (versionCode 15)

- The whole app now uses the logo palette: black, white and blue (light blue for move minutes). Dashboard tiles and the calendar use shades of blue instead of many colours.
- Routes are smoothed before drawing: GPS wobble is averaged out and the line simplified to 2 m, so walking the same path many times shows one clean line instead of a thick blob. Thinner route line; the start is a dark dot, the finish a blue one.
- Local sign-up and log-in: name, email and password, kept only on this phone (the password is stored as a salted PBKDF2 hash, never in plain text). Shown on first launch, with "Continue as guest". Your name and initials appear on Profile and in the menu; log out from Profile.
- Opening animation: the logo appears, then a blue circle grows out of it to fill the screen and opens onto the dashboard. Also plays after signing up or logging in, and is skipped when the system turns animations off.
- Maps can be explored: drag to move, pinch or double-tap to zoom on the session summary and live screens, with a button to fit the whole route again. The dashboard map stays still so the page scrolls; tap it for a full-screen map. Light maps are toned down so the route stands out.
- Walking and running are split automatically from step cadence (about 150+ steps a minute is running, under ~125 walking) together with sustained GPS speed, and need four agreeing readings before switching. This now works indoors too. Routes draw walking in blue and running in red, with a walk/run key on the summary and on share images.
- Status colours are back: live, connected and goal reached show in green. Each "Today at a glance" tile has its own soft colour (blue distance, orange calories, green active time, purple sessions, teal walking, pink running, amber streak, indigo average).
- Proper vector icons replace text symbols in the bottom bar, glance tiles, menu, goal cards, header settings, menu close and the live map's follow button.
- Redesigned calendar: month summary (steps, goal days, distance), each day drawn as a small ring that fills toward the goal and turns solid blue when reached, a dot for days with sessions, today outlined, streak line, and a selected-day card with steps, progress, distance, active time, calories and sessions.
- The logo follows the theme: white track on brand blue in light mode, and on black with a blue dot in dark mode. The in-app logo follows Kadamm's Light/Dark/System setting, the share image matches its Light/Dark choice, and the app icon switches with the phone's dark mode. White track on brand blue, larger in the header with the wordmark and a version pill.
- New Kadamm logo: a running track with a blue dot marking where you are on it, in black, white and blue. Used for the launcher icon (including themed icons), the app header, the menu and share images.
- Share images come in Light and Dark; the preview lets you switch before sharing.
- Redesigned menu: logo header, profile card, today's progress with a small ring, Activity and Settings sections with counts, a Light/Dark/System switch, a Start activity button, and a full slide-in animation.

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
