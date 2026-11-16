# Kadamm 8.2: what was added

Version 8.2 (versionCode 35). It is installed on the SM-S721B and the APK is `releases/kadamm-v8.2.apk`.

The goal of this release was the depth of analysis in the Zepp and Strava screenshots you shared, using only the phone's sensors (step counter, GPS and barometer), with a way to plug a watch in later. Every number comes from data the phone recorded. Where data is missing, the card says so; nothing is estimated and shown as measured.

## 1. Session analysis (after every walk or run, indoor or outdoor)

Open any session from History. Each card has an ⓘ button that explains how its numbers are worked out.

| Card | What it shows | How it is calculated | Indoor? |
|---|---|---|---|
| Overview | Distance, a slow-to-fast scale, workout time, average pace, steps, active kcal, average cadence, effort, moving time, average speed, elevation gain | Moving pace is moving time ÷ distance. The scale runs 2–7 km/h for walks and 6–14 km/h for runs | Yes (distance is from steps × your stride, flagged as estimated) |
| Kadamm Intelligence | A short plain-language summary | Built from the zones, the split trend, pace consistency and relative effort below; no AI service | Yes |
| Relative effort | Effort score, fitness change, and where the session sits against your 3-week average (bands like Strava's) | Effort = minutes in each zone × weight (0.2, 0.5, 1, 1.5, 2.5). The comparison uses your sessions from the previous 21 days | Yes |
| Intensity zones | Five zones (Light, Easy, Moderate, Brisk, Vigorous): % and time in each | Cadence, if steps were recorded over time (CADENCE-Adults research: 100 spm ≈ 3 METs, 130 spm ≈ 6 METs). Otherwise GPS speed. Otherwise the session's average cadence | Yes |
| WHO intensity minutes | Moderate minutes + 2 × vigorous minutes, toward 150 a week | WHO physical activity guideline | Yes |
| Pace | Chart, average, fastest, grade-adjusted pace (GAP), consistency | Points over 150 m windows. GAP uses Minetti's energy cost of moving on a slope. Consistency is the spread of pace over 250 m stretches | Yes (from the step timeline) |
| Details per km | Pace, a speed bar, cadence, climb, cumulative time; fastest km marked ▶; first half against second half | Interpolated exactly at each kilometre mark | Yes |
| Cadence | Average, max, steps per km, and a zone-coloured dot chart (like Zepp's) | Steps over 30-second windows | Yes |
| Stride | Measured average and max against your setting, with a chart | GPS distance ÷ steps over 30-second windows; only outdoors, because indoor stride is your setting, not a measurement | Outdoor only |
| Heart rate | Average, max, chart, heart-rate zones | Read from Health Connect when a watch has written heart rate. Max HR = 208 − 0.7 × age (Tanaka) | Yes, with a watch |
| Elevation | Gain, loss, range, chart; a gradient donut (uphill / flat / downhill, like Zepp's) | Barometer for new sessions; heavily smoothed GPS altitude for older ones, labelled as less precise | Barometer works indoors |
| Best efforts | 400 m, 1 km, 1 mile, 2 miles, 5 km, 10 km, half marathon, each with an all-time rank and medal (like Strava's) | Fastest stretch of that length anywhere in the GPS track | No (indoor distance is estimated) |
| Matched sessions | "Trending faster" or "slower", with a pace trend chart | Earlier sessions within ±20 % of the distance, same indoor/outdoor mode | Yes |
| Health markers | Walking speed in m/s with a verdict, moving %, stops, kcal per km | Walking speed is a "vital sign": 1.0 m/s and above is linked with longer life (Studenski, JAMA 2011) | Outdoor walks |
| Running fitness | Estimated VO₂max (VDOT) and 5 km and 10 km predictions | Jack Daniels' VDOT formula; Riegel's formula for predictions | Runs only |

### Your Sun 15 Nov walk (5.49 km), as an example

- **Overview:** 1:18:18, average pace 14'16"/km, 8,443 steps, 287 kcal, average cadence 108 spm, effort 65.
- **Effort:** higher than usual; your 3-week average is 26, from 16 sessions. Fitness rose by 1.3 that day.
- **Zones** (from GPS speed, because this session predates the step timeline):
  - Moderate 60 % (46:30)
  - Easy 33 % (25:29)
  - Brisk 4 %
  - Light 3 %
  - That adds up to 50 intensity minutes.
- **Splits:**
  - km 1: 14'34"
  - km 2: 14'16"
  - km 3: 14'41"
  - km 4: 14'11" (fastest)
  - km 5: 14'11"
  - last 0.48 km: 13'35"
  - **Half split:** first half 14'33", second half 14'04". Kadamm Intelligence calls this a negative split (you got faster).
  - **Pace consistency:** 4 % variation, which counts as very steady.
- **Best efforts:**
  - Your fastest 5 km ever (1:11:27)
  - Your 3rd fastest 2 miles
  - #6 for 1 km (13:52)
- **Stride:** 65 cm measured, against your setting of 72 cm.
- **Walking speed:** 1.17 m/s, in the healthy range.

I checked the splits and the best 1 km independently in Python against the raw GPS points; they match to the second.

## 2. New data recorded from now on

Starting with sessions recorded on 8.2:

- **Every GPS point** also stores the session step count (`k`) and the barometer altitude (`b`).
- **A step timeline:** every 5 seconds, a sample of time, steps, distance and barometer altitude. It is recorded indoors too, which is what gives indoor sessions cadence, pace and splits over time.

The on-device recording test confirmed both are saved: 11 timeline samples and the barometer points.

Older sessions still get every card their data supports. The cadence chart over time and the gradient appear only for new sessions; those cards say so.

## 3. Ready for a watch

- **Heart-rate access:** Health Connect now asks for an optional heart-rate permission. The heart-rate card on a session has a link that requests it.
- **Automatic import:** once a watch syncs heart rate to Samsung Health or Health Connect (Galaxy Watch, Amazfit via Zepp, Fitbit and others), each session's heart rate is imported automatically, about 30 minutes after it ends.
- **What heart rate adds:** a heart-rate chart, heart-rate zones, and an effort score based on heart rate instead of movement.
- **Age setting:** Goals and targets has a new Age setting, used for maximum heart rate.
- **Without a watch:** nothing is invented. The card says there is no watch data for the session.

## 4. Badges: 45 in four tiers

Badges are drawn as hexagonal medals: bronze for Easy, silver for Medium, gold for Hard and amethyst for Extreme.

The Achievements page has:

- a hero with your total and a count per tier;
- an "In progress" strip (like Zepp's);
- a grid for each tier, with a progress ring on locked badges;
- a detail sheet when you tap a badge.

You currently have **22 of 45**: Easy 12/12, Medium 9/12, Hard 1/12, Extreme 0/9. Next up are 20K day (16,013 / 20,000) and 100K week (74,849 / 100,000).

| Tier | Badges |
|---|---|
| Easy | First steps, 5K day, First kilometre, 3-day streak, Ten sessions, 10 km club, 100K steps, Half hour, First run, Early bird, Night owl, Weekend warrior |
| Medium | 10K day, 7-day streak, 5 km session, 50 km club, Regular (25 sessions), Half a million steps, Hour of power, 50K week, WHO week (150 intensity min), Four a week, Speed walker, Treadmill trooper |
| Hard | 20K day, 30-day streak, Goal crusher, 10 km session, 100 km club, Committed (100 sessions), Millionaire, 100K week, Double dose (300 intensity min), Seven-day sweep, Power walker, Two-hour trek |
| Extreme | 30K day, Century streak, Half marathon, 500 km club, Five million steps, 500K month, Year of walking, Mountain goat (1,000 m climbed), Sub-30 5K |

The ids of the old badges are kept, so badges you've already seen don't celebrate again.

## 5. Reports, History and Profile

- **Reports, new "Health & fitness" section:**
  - **Intensity minutes:** a ring against the WHO's 150 a week (150 for every week that has passed, for a month).
  - **Zone mix:** the share of time in each zone.
  - **Comparison:** against the same number of days of the period before.
  - **Fitness, fatigue and form:** with a 60-day fitness chart. This is the Banister model behind Strava's Fitness & Freshness and TrainingPeaks.
  - **Walking quality:** walking speed and cadence against the period before.
- **History, Month at a glance:**
  - The line stops at today instead of dropping to zero for future days.
  - Goal days get green dots, with a labelled goal line and peak and today markers.
  - The chart is taller.
  - Daily average, goal days and best day are shown under it.
- **Profile:** a Fitness snapshot card. You currently have:
  - fitness 11, form −6 (neutral);
  - this week's WHO minutes, in a ring;
  - 30-day walking speed 1.08 m/s and cadence 107 spm.

## 6. Smoothness

| What was slow | Fix |
|---|---|
| The 1 MB session history was parsed again every second on the live screen, and on every page | Parsed once and cached until the stored text changes |
| Profile took 460 ms to build (places coverage, records, full-size photo decode) | All three are cached, and the photo is decoded at display size. Profile now builds in about 45 ms |
| Places took 290 ms | Coverage is cached, so it now builds in 16 ms |
| Session analysis on first open | Computed in a background thread when the app opens |
| Page fade and slide animations redrew the whole page every frame | They run on hardware layers and start after the new page's first layout |
| The goal celebration stuttered | The card and backdrop animate on hardware layers; confetti skips pieces that are off screen and doesn't rotate dots |

I measured both versions on the phone with `dumpsys gfxinfo`, switching tabs and scrolling the same way (I reinstalled 8.1 briefly to get the baseline):

| | 8.1 | 8.2 |
|---|---|---|
| Janky frames | 3.1 % | 1.0 % |
| 99th-percentile frame time | 200 ms | 16 ms |
| Median frame time | 6 ms | 6 ms |

## 7. Verification

- **Test suite:** all 8 on-device tests pass (`tests/e2e.py`), plus the no-crash/ANR check. The recording test now also checks the step timeline.
- **Screens checked on the phone:** session analysis, Achievements, Reports, Profile and History in the dark theme; Achievements and History in the light theme. Your theme was set back to dark.
- **Not seen on screen:**
  - heart rate (no watch);
  - a new session's cadence chart, stride chart and gradient donut (these need a real walk recorded on 8.2);
  - the running fitness card (no GPS run in your history).

## 8. What a phone can't give honestly (and so isn't shown)

These metrics are left out because they need a watch or chest strap:

- Real-time performance
- Training effect (aerobic and anaerobic)
- Power
- Ground contact time
- Vertical oscillation and vertical ratio
- Post-workout heart rate

Heart-rate metrics turn on by themselves once heart rate is available in Health Connect.

VO₂max is shown only for GPS runs. The usual walking tests need heart rate.

## Assumptions

- **Zones without a watch:** from cadence (or GPS speed for older sessions), not heart rate.
- **Effort weights:** 0.2, 0.5, 1, 1.5 and 2.5 per minute, chosen so a typical 30-minute brisk walk scores about 45.
- **Fitness numbers:** use your own effort scale, so compare them with your earlier values, not with Strava's.
- **Maximum heart rate:** zones stay off until you set your age.

---

# Kadamm 8.3 additions

| Area | What's new | How it's calculated |
|---|---|---|
| Navigation | Back and swipe-down return to the screen you came from, at the same scroll position; a "‹ Profile" pill on sub-pages | A back stack of pages with their scroll positions |
| Session map | Route / Pace / Grade modes, with top-3 record pins | Pace: 30-second speed, coloured from this walk's 10th to 90th percentile. Grade: barometer slope over 30 m, ±2 % thresholds |
| Pace zones | Z1 recovery to Z6 anaerobic | Threshold = your best 30-minute pace from any GPS session; zones at 78 / 88 / 94 / 101 / 105 % of threshold speed |
| Grade-adjusted pace | Your pace against your pace on flat ground | Minetti energy cost of walking on slopes; barometer sessions only |
| Elevation | Area chart, gain, loss, average, min, max, range | Height above sea level: GPS ellipsoid height plus Android's geoid offset; barometer readings anchored to it |
| Cadence and stride for older walks | Dot charts minute by minute | Samsung Health per-minute steps through Health Connect (`hcMin`) |
| Gait steadiness | How much your stride length varies | Coefficient of variation of the 30-second stride |
| Today's plan | Daily % and weekly completion bars | 60 % step goal + 40 % brisk minutes (30 a day), each capped at 150 % |
| Movement quality | Peak-30 cadence, brisk minutes, longest sitting, steps by hour | Health Connect minute steps. Peak-30 = mean of the day's 30 best minutes (Tudor-Locke). Sitting = minutes under 10 steps, 7 am–11 pm |
| Training load | Acute:chronic ratio with a verdict | 7-day ÷ 28-day average daily effort; 0.8–1.3 is the sweet spot |
| When you move | Weekday × hour heatmap | Average steps per hour over the period |
| Pace curve | Best pace for 1–60 minutes, all time against the last 90 days | The most distance covered within each duration on the GPS track |
| Badges | Hero medal, streak flame, shelves for in progress, personal bests and each tier | Same 45 badges and rules as 8.2 |

On your data:
- **Today:** plan 76 %, this week 11 %, peak-30 cadence 101, 24 brisk minutes, longest sitting 2 h 03.
- **Pace zones:** threshold 12'39"/km. Nov 15 split as Z4 22 %, Z3 28 %, Z2 27 %, Z1 19 %.
- **Training load:** ratio 0.74, lighter than usual.
- **Pace curve:** 30 minutes at 12'39", 1 hour at 14'12".

Not built in this round: matched-route segments (racing your past self on repeated stretches). Saved routes still compare whole attempts.
