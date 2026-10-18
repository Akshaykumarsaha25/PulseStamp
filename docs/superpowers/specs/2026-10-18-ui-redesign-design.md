# InstaBeats UI redesign: design spec

Status: sections 1 and 2 approved; section 3 presented with one open decision; section 4 proposed below and needs approval.

## Goal

Redesign InstaBeats into a polished, Gen Z, Insta360-style app (AI action hero slideshow, frosted glass, Studio palette) with a full flow (Welcome → Home / Workouts / Exports → Workout detail → Export → Insta360 guide), a hard on-device privacy guarantee (no internet permission), and a restructured, unit-tested codebase, shipped through the existing GitHub Actions APK build. The GPX it writes must stay accurate (real HR, real GPS where there is one) and keep loading in Insta360 (Dashboard → Data source → Local files).

## Approved decisions

- **Visual direction "Studio":** true black, yellow `#FFC93C` for actions only, coral `#FF6B5E` for heart rate only, Unbounded 900 wordmark, Manrope body, frosted glass panels (blur on Android 12+, tinted fallback on 9 to 11).
- **Home layout A:** full-bleed hero, stacked "Insta / Beats" wordmark, tagline "Stamp your beats on every frame.", glass card with the latest workout (BPM pulsing at the real rate, lap bars with the best lap in yellow, "Export for Insta360"), glass tab bar.
- **Hero slideshow:** fade only (about 4 s hold, 2.2 s crossfade, one continuous gentle drift/zoom per photo, no blur filters, no progress bars).
- **Privacy on Welcome:** "Your heart rate stays on your phone." No INTERNET permission (`tools:node="remove"`), no account, cloud, sync or analytics, `allowBackup="false"`, files only in `Downloads/InstaBeats`.
- **Screen flow:** Welcome (first run) → Health Connect permission → Home. Tabs Home / Workouts (filters All, With GPS, HR only; 7, 14, 30 days) / Exports (share, delete, How to use, Privacy). Workout detail → export (route consent if needed) → "Export done" sheet (Open Insta360 / Share / How to load it) → Insta360 guide. File names `InstaBeats_<Sport>_<yyyy-MM-dd>.gpx`. HR zones shaded relative to the workout's peak.

### Hero images

Five AI images (FLUX.1-schnell, Apache 2.0, generated locally), 1080×1920, shipped as WebP in `res/drawable-nodpi`, in slideshow order:

| # | Scene | File | Source | Mean luma (title / card zone) |
|---|---|---|---|---|
| 1 | Mountain biker mid-air | `hd_mtb_air_1000` | re-rendered at 1088×1920, cropped | 78 / 33 |
| 2 | Drift night, pink smoke | `up_drift_night_1000` | 768×1344 original, Real-ESRGAN x4 | 89 / 40 |
| 3 | Supermoto (night) | `hd_supermoto_1001` | re-rendered | 75 / 94 |
| 4 | Tiny-planet circuit | `up_planet_circuit_1000` | original, upscaled | 131 / 56 |
| 5 | Helmet-cam night ride | `hd_helmet_pov_1001` | re-rendered | 17 / 72 |

Scrim per image: top alpha = clamp(1 − 60 / titleLuma, 0.40, 0.78); card glass alpha = clamp(1 − 60 / cardLuma, 0.38, 0.70). Before shipping: soften the fake sponsor lettering on the drift car, check `hd_supermoto_1001` for fake text, and check every image at 100% for AI artefacts.

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
- New deps (Kotlin 1.9.24 / Compose BOM 2024.06, no Kotlin 2.0 upgrade): `androidx.navigation:navigation-compose:2.7.7`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `dev.chrisbanes.haze:haze:0.7.3` (Haze 1.x needs Kotlin 2.0 / Compose 1.7), `junit` for tests.
- Manifest: `tools:node="remove"` on INTERNET, `allowBackup="false"`, drop the invalid `READ_EXERCISE_ROUTE`, keep the Health Connect rationale and privacy hooks pointing at the Privacy screen.
- `make_instabeats.py` is retired once this lands; the repo is the source of truth.

## 2. Data flow (approved)

- **List.** Read `ExerciseSessionRecord` for 7, 14 or 30 days, following `pageToken` until it is null (today only the first page is read). Avg and peak BPM per session come from Health Connect `aggregate` (`HeartRateRecord.BPM_AVG`, `BPM_MAX`), not raw samples. The route badge comes from `exerciseRouteResult` (Data / ConsentRequired / NoData).
- **Lap bars.** Raw HR samples are loaded only for the Home-card workout and the detail screen. Laps come from `session.laps`; if there are none, 10 equal time slices labelled "Effort over time" (never fake laps).
- **Export.**
  1. Route: real GPS from the session, or one-time `ExerciseRouteRequestContract` consent, or the existing synthetic 1 point/s track (the guide tells users to hide the map gauge for HR-only files).
  2. HR samples read with paging, flattened and sorted by time.
  3. Merge in one two-pointer pass that gives each track point the nearest-in-time HR sample. Same output as today, but O(n + m) instead of O(n × m).
  4. `GpxBuilder` keeps Garmin TrackPointExtension v1 and strict `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC times. `<type>` is set from the real sport (today it is always "cycling").
  5. Save as `InstaBeats_<Sport>_<yyyy-MM-dd>.gpx`, with a `_HHmm` suffix on collision, then show the Export-done sheet.
- **Exported marks.** The Exports tab lists the app's own files from MediaStore. Workout → file links live in app-private SharedPreferences (backup is off); deleting a file clears its mark.
- **State.** One `AppViewModel` exposing Health Connect status (missing / no access / ready), the workout list, the open workout detail, and export status (idle / working / done / error). Screens read state and send intents back.

## 3. Error handling (presented, one decision open)

Rules: every error says what happened and what to do; it is shown in place (card, sheet or screen), not as a toast; the app never exports made-up data.

- **Health Connect.** Missing or outdated → "Install/Update Health Connect" (Play Store). Unsupported device → explanation, no button. Permission cancelled → "InstaBeats needs workout and heart-rate access to work" + "Open Health Connect settings". Access revoked mid-use → back to Welcome, no crash. Rate-limited → "Health Connect is busy. Try again in a minute." + Retry.
- **Export fixes compared with today.**
  - No HR samples → export blocked (today it writes a fake 85 bpm via `?: 85L`).
  - The synthetic route covers the whole workout (today it stops at 4 h / 14,400 points).
  - A failed save deletes the `IS_PENDING` MediaStore row before showing "Couldn't save the file: <reason>." + Retry.
  - HR gaps keep the nearest-sample behaviour on purpose, because how Insta360 handles a missing `gpxtpx:hr` is unverified.
- **Other.** A file deleted outside the app is dropped from Exports and its mark cleared on refresh. Insta360 not installed → "Open Insta360" hidden. Empty range → "No workouts in the last 14 days. Record one on your watch, then come back." + a show-30-days button. An export keeps running across screen changes.
- **Open decision:** when route consent is refused, ask "Export without GPS?" (Export / Cancel), as recommended, or auto-export HR-only like today.

## 4. Testing (proposed)

- **JVM unit tests** in `app/src/test`, for the pure Kotlin parts only (no Robolectric, no instrumented tests):
  - `GpxBuilder`: strict UTC time format (no fractional seconds, `Z` suffix, device time zone doesn't leak in); `lat`/`lon` written with a `.` decimal even when the default locale uses `,`; XML escaping of `&`, `<`, `>`, `"` in names; sport → `<type>` mapping; output parses as XML with the `gpxtpx` namespace.
  - HR alignment: nearest sample chosen; ties go to the earlier sample; points before the first and after the last sample take the edge sample; unsorted input sorted first; a 4 h workout at 1 Hz on both sides runs in well under a second.
  - Synthetic route: one point per second from start to end with no 4 h cap; no points when the workout has no HR.
  - File naming: sport and date in the device's zone; `_HHmm` on collision.
  - Lap fallback: 10 equal slices when `laps` is empty; the best lap is the shortest, ties go to the earlier lap.
- **CI.** Commit the Gradle wrapper (8.11.1). The workflow runs `./gradlew testDebugUnitTest assembleDebug` on pushes to `main` and on pull requests (today it runs only on `main`/`master` pushes and has no tests). A failing test fails the build, so no APK is uploaded.
- **Manual check before calling it done:** on the phone with the Galaxy Watch 8, export one outdoor workout with GPS, one indoor HR-only workout, and one long workout (over 1000 HR records); import each into Insta360 and confirm the HR gauge, map and speed.

## Out of scope

Kotlin 2.0 / Compose 1.7 upgrade, Haze 1.x, writing any data back to Health Connect, cloud features of any kind.
