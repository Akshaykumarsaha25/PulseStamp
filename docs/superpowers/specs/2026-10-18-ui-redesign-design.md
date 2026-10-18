# InstaBeats UI redesign: design spec

Status: draft for review. Sections marked *approved* were agreed during brainstorming; sections 2 to 4 are new and need review.

## Goal

Replace the single-screen prototype (`MainActivity.kt`, 438 lines) with a small, structured app that looks finished and produces correct GPX files for the Insta360 overlay: real heart rate only, real route when there is one, correct sport type, and nothing dropped on long workouts.

## Approved decisions (summary)

- **Visual direction "Studio":** true black, yellow `#FFC93C` for actions only, coral `#FF6B5E` for heart rate only, Unbounded 900 wordmark, Manrope body, frosted glass panels (blur on Android 12+, tinted fallback on 9 to 11).
- **Home layout A:** full-bleed hero slideshow, stacked "Insta / Beats" wordmark, tagline "Stamp your beats on every frame.", glass card with the latest workout (BPM pulsing at the real rate, lap bars with the best lap in yellow, "Export for Insta360"), glass tab bar.
- **Hero slideshow:** fade only (about 4 s hold, 2.2 s crossfade, one slow drift/zoom per photo, no blur, no progress bars). Per-image scrim strength from measured brightness.
- **Privacy:** "Your heart rate stays on your phone." No INTERNET permission (`tools:node="remove"`), no account, cloud, sync or analytics, `allowBackup="false"`, files only in `Downloads/InstaBeats`.
- **Screen flow:** Welcome (first run) → Health Connect permission → Home. Tabs Home / Workouts (filters All, With GPS, HR only; 7, 14, 30 days) / Exports (share, delete, How to use, Privacy). Workout detail → export (route consent if needed) → "Export done" sheet (Open Insta360 / Share / How to load it) → Insta360 guide. File names `InstaBeats_<Sport>_<yyyy-MM-dd>.gpx`. HR zones shaded relative to the workout's peak.

### Hero images (picked on the full-HD comparison page)

Shipped as WebP in `res/drawable-nodpi` at 1080×1920, in this order:

| # | Scene | Source |
|---|---|---|
| 1 | Mountain biker mid-air | new full-HD render `hd_mtb_air_1000` |
| 2 | Drift night | original `ai_drift_night_1000`, AI-upscaled to 1080×1920 |
| 3 | Supermoto | new full-HD render `hd_supermoto_1001` |
| 4 | Tiny-planet circuit | original `ai_planet_circuit_1000`, AI-upscaled to 1080×1920 |
| 5 | Helmet-cam night ride | new full-HD render `hd_helmet_pov_1001` |

Before shipping, each is checked at 100% for AI artefacts (wheels, spokes, fake text).

## 1. Structure (approved)

```
app/src/main/java/com/instabeats/app/
├── MainActivity.kt            setContent + NavHost only
├── model/Models.kt            WorkoutSummary, HrSample, TrackPoint, Lap, ExportedFile
├── health/HealthRepository.kt availability, permissions, sessions, HR, routes, laps
├── gpx/GpxBuilder.kt          pure Kotlin (no Android imports): points + HR → GPX text
├── export/ExportStore.kt      MediaStore save/list/delete, share, open Insta360
└── ui/
    ├── AppViewModel.kt        single ViewModel; screens render its state
    ├── theme/                 colours, bundled Unbounded + Manrope (OFL) in res/font
    ├── components/            Glass, HeroSlideshow (fade), PulsingBpm, LapBars, HrChart
    └── screens/               Welcome, Home, Workouts, WorkoutDetail, ExportSheet, Guide, Exports
```

- UI never touches Health Connect or files; only the ViewModel calls the repository and the store.
- New deps (Kotlin 1.9.24 / Compose BOM 2024.06, no Kotlin 2.0 upgrade): `androidx.navigation:navigation-compose:2.7.7`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `dev.chrisbanes.haze:haze:0.7.3`, `junit` for tests.
- Manifest: `tools:node="remove"` on INTERNET, `allowBackup="false"`, drop the invalid `READ_EXERCISE_ROUTE`, keep the Health Connect rationale and privacy hooks pointing at the Privacy screen.
- `make_instabeats.py` is retired once this lands; the repo is the source of truth.

## 2. Data flow

### 2.1 Models

```kotlin
data class HrSample(val time: Instant, val bpm: Int)
data class TrackPoint(val time: Instant, val lat: Double, val lon: Double, val eleMeters: Double?)
data class Lap(val start: Instant, val end: Instant, val lengthMeters: Double?)
enum class RouteState { HAS_ROUTE, NEEDS_CONSENT, NO_ROUTE }
data class WorkoutSummary(
    val id: String, val sport: Sport, val title: String?,
    val start: Instant, val end: Instant,
    val avgHr: Int?, val peakHr: Int?, val hrSampleCount: Int,
    val avgSpeedMps: Double?, val maxSpeedMps: Double?,
    val laps: List<Lap>, val routeState: RouteState,
)
```

`Sport` is our own enum (Cycling, IndoorCycling, Running, Treadmill, Hiking, Walking, Other) mapped once from `ExerciseSessionRecord.exerciseType`. It drives the card icon, the file name and the GPX `<trk><type>` (`cycling`, `running`, `hiking`, `walking`, else omitted). Today the type is hard-coded to `cycling` for every workout.

### 2.2 Paged reads

Every `readRecords` call goes through one helper in `HealthRepository`:

```kotlin
suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
    val out = mutableListOf<T>(); var token: String? = null
    do {
        val r = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
        out += r.records; token = r.pageToken
    } while (token != null)
    return out
}
```

This replaces the current single-page reads, which silently drop everything after the first 1000 records (sessions and HR alike).

### 2.3 Listing workouts

1. `readAll(ExerciseSessionRecord)` for the chosen window (7, 14 or 30 days), newest first.
2. For the summaries, `aggregate` is used rather than pulling raw samples: `HeartRateRecord.BPM_AVG`, `BPM_MAX`, `MEASUREMENTS_COUNT`, `SpeedRecord.SPEED_AVG`, `SPEED_MAX`, one `aggregate` call per session, run in parallel with a small concurrency limit (4) to stay inside Health Connect's rate limits.
3. `routeState` comes from `session.exerciseRouteResult` (Data → HAS_ROUTE, ConsentRequired → NEEDS_CONSENT, NoData → NO_ROUTE).
4. Laps come from `session.laps`. Best lap = shortest duration; ties keep the earlier one.

The Workouts filters work on these summaries: "With GPS" = HAS_ROUTE or NEEDS_CONSENT, "HR only" = NO_ROUTE and `hrSampleCount > 0`.

### 2.4 Export

1. **Route.** HAS_ROUTE uses the route from the session. NEEDS_CONSENT launches `ExerciseRouteRequestContract` for that session id; a returned route is used, `null` (denied) falls through to step 1b. NO_ROUTE goes to 1b.
   - **1b. HR-only.** A synthetic track of one point per HR sample, all at the **same** fixed coordinate. The current code drifts the point by a micro-offset each second, which makes Insta360 show a fake speed and distance; a fixed point keeps speed at 0. The coordinate is a constant in `GpxBuilder` (the current Chandigarh point), and the export sheet labels the file "Heart rate only, no map". Ask: confirm the fixed point should stay Chandigarh, or use 0,0.
2. **Heart rate.** `readAll(HeartRateRecord)` over the session window, flatten `samples`, sort by time.
3. **Merge** (`GpxBuilder.alignHr`, pure Kotlin). Both lists are sorted, so a single two-pointer pass assigns each track point its nearest HR sample in O(points + samples), replacing the O(points × samples) `minByOrNull`. A sample is used only if it is within **10 s** of the point; otherwise the point gets no `<gpxtpx:hr>` element. There is no invented default: the current code fills 85 bpm before the first sample and repeats the last value across gaps of any length.
4. **Build.** `GpxBuilder.build(name, sport, points, hrByPoint)` writes GPX 1.1 with `gpxtpx` TrackPointExtension v1: times as strict `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC, `lat`/`lon` with 7 decimals, `<ele>` only when the altitude is known (today missing altitude is written as 0 m, which draws a fake elevation profile), names XML-escaped.
5. **Save.** `ExportStore.save(fileName, bytes)`: on Android 10+ MediaStore Downloads with `RELATIVE_PATH = Download/InstaBeats` and `IS_PENDING`; on Android 9 the legacy public Downloads directory, which needs `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` (currently missing, so saving fails on Android 9). If `InstaBeats_<Sport>_<date>.gpx` already exists, the next free `_2`, `_3` suffix is used.
6. **Sheet.** The ViewModel emits `ExportResult(uri, fileName, pointCount, hrMatched, hrOnly)`; the sheet shows it with Open Insta360 / Share / How to load it.

Speed stays derived by Insta360 from the GPS points; `SpeedRecord` feeds the in-app summary only. The `READ_SPEED` permission stays because of that.

### 2.5 State

`AppViewModel` holds one `StateFlow<AppState>` (availability, permissions, window, workouts, selected workout detail, export in progress or result, exports list). Screens collect it and call ViewModel functions; no screen keeps its own copy of Health Connect data.

## 3. Error handling

Errors are values, not toasts: the repository and store return `Result`-style sealed types, and the ViewModel turns them into a message on the screen where the user is.

| Situation | What the user sees |
|---|---|
| Health Connect not installed or needs an update (`SDK_UNAVAILABLE`, `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`) | Full-screen card with a button to the Play Store listing |
| Permissions denied or revoked later | Permission screen again, with "Open Health Connect" to fix it in settings; checked on every resume |
| No workouts in the window | Empty state that suggests a longer window |
| Route consent denied | Export continues as HR-only, and the sheet says why there is no map |
| Workout has no HR samples | Export button disabled, "No heart rate recorded for this workout" |
| HR exists but none within 10 s of any point | Export allowed; the sheet warns "Heart rate didn't line up with the route" |
| Health Connect rate limit or `RemoteException` | "Health Connect is busy, try again", with a retry button |
| MediaStore insert/write fails | "Couldn't save to Downloads" with the reason; a half-written pending entry is deleted |
| Insta360 not installed | "Open Insta360" becomes "Get Insta360" (Play Store) |

Reads run on `Dispatchers.IO` inside `viewModelScope`; leaving a screen cancels its work, and a cancelled export deletes its pending MediaStore entry.

## 4. Testing

- **Unit tests (JVM, `app/src/test`)** for the pure parts:
  - `GpxBuilder`: strict UTC time format (no fractions, `Z` suffix, local time zones don't leak in); `lat`/`lon` formatting with `Locale.US` under a comma-decimal default locale; `<ele>` omitted when unknown; XML escaping of `&`, `<`, `"` in names; sport → `<type>` mapping; output parses as XML with the `gpxtpx` namespace.
  - `alignHr`: nearest sample chosen; ties pick the earlier sample; gap over 10 s gives no HR; no HR before the first sample; unsorted input handled; 4 h of 1 Hz points × 1 Hz samples finishes quickly.
  - Synthetic track: one point per sample, constant coordinate.
  - File naming: sport and date in the device's zone, collision suffixes.
- **CI:** add the Gradle wrapper (8.11.1) to the repo, run `./gradlew testDebugUnitTest assembleDebug` on pushes to `main` and on pull requests (today the workflow only runs on `main`/`master` pushes and has no tests).
- **Manual check on the Galaxy Watch 8 / phone:** one outdoor ride with GPS, one indoor workout (HR only), one workout over 1000 HR records; import each into Insta360 (Dashboard → Data source → Local files) and check the HR gauge, map and speed.

## Out of scope

Kotlin 2.0 / Compose 1.7 upgrade, Haze 1.x, writing any data back to Health Connect, cloud features of any kind.

## Open questions

1. HR-only fixed coordinate: keep the current Chandigarh point, or 0,0?
2. HR gap threshold of 10 s: fine as a fixed value, or should it be a setting?
