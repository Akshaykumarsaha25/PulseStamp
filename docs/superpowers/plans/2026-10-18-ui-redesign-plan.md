# InstaBeats UI redesign: implementation plan

Spec: `docs/superpowers/specs/2026-10-18-ui-redesign-design.md`. Visual reference: the approved clickable preview (built from `welcome-privacy-2.html`, `home-a-v7.html`, `flow.html`), including About as the fourth tab.

There is no local Android SDK, so every step ends with a push and a green GitHub Actions run (`./gradlew testDebugUnitTest assembleDebug`). Pure Kotlin logic stays in `model/`, `gpx/`, `export/FileNames.kt` and is unit-tested.

## Steps

1. **Pure core (done).** `GpxBuilder`, `alignHeartRate`, `syntheticRoute`, `exportFileName`, `lapBars`, 23 JUnit tests, Gradle wrapper, CI on PRs.
2. **Assets.** Five hero photos as WebP in `res/drawable-nodpi` (`hero_mtb_air`, `hero_drift_night`, `hero_supermoto`, `hero_planet_circuit`, `hero_helmet_pov`). Unbounded 900 and Manrope 400/600/800 as static, Latin-subset TTFs in `res/font` (OFL texts in `docs/licenses/`). Pulse Frame launcher icon (done).
3. **Build + manifest.** Navigation Compose 2.7.7, lifecycle-viewmodel-compose 2.8.4, Haze 0.7.3. `versionName` 2.0. Manifest: remove INTERNET (`tools:node="remove"`), `allowBackup="false"`, drop `READ_EXERCISE_ROUTE`, `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` for Android 9 saves, FileProvider for sharing on Android 9, `<queries>` for Health Connect and Insta360, rationale and permission-usage intents open the Privacy screen.
4. **Data layer.**
   - `model/Models.kt`: `WorkoutSummary`, `RouteState`, `ExportedFile`, `Sport.displayName`.
   - `health/HealthRepository.kt`: SDK status, granted permissions, paged `readAll`, session list with `aggregate` avg/peak BPM, HR samples, laps, route result, sport mapping.
   - `export/ExportStore.kt`: MediaStore save (pending row deleted on failure), Android 9 file save, list, delete, share intent, Insta360 launch intent; `ExportMarks` in app-private SharedPreferences.
5. **ViewModel.** `ui/AppViewModel.kt`: `AppState` (Health Connect status, workouts, range and filter, open detail with HR samples and lap bars, export step, exports list). Export steps: idle → needs consent → ask "Export without GPS?" → needs storage permission (Android 9 only) → working → done or error. Export runs in `viewModelScope`, so it survives screen changes.
6. **Theme and components.** `ui/theme/Theme.kt` (Studio colours, fonts, text styles). `ui/components/`: `Glass` (Haze blur on 12+, tinted fallback), `HeroSlideshow` (fade only, 4.2 s hold, 2.2 s crossfade, drift/zoom, per-image scrim from measured luma), `PulsingBpm`, `LapBarsView`, `HrChart` (zones from peak), `Buttons` (yellow, ghost, chips), `GlassTabBar`.
7. **Screens.** Welcome (privacy list, Connect Health Connect), access denied, Health Connect missing, Home, Workouts (filters, 7/14/30 days, exported marks, empty state), Workout detail, Export sheet and dialogs, Insta360 guide, Exports (share, delete, How to use, Privacy), Privacy, About.
8. **Wire-up.** `MainActivity` = edge-to-edge `setContent` + `NavHost` (welcome, main tabs, detail, guide, privacy); re-check access on resume.
9. **Verify.** CI green; then a manual pass on the phone: outdoor GPS workout, indoor HR-only, a long workout, import each into Insta360.

## Not in this pass

Softening the AI lettering on the drift photo, the app name decision, and a license choice: all still open with the user.
