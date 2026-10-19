package com.pulsestamp.app.ui

import android.app.Activity
import android.app.Application
import android.os.RemoteException
import androidx.health.connect.client.records.ExerciseRoute
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsestamp.app.export.ExportMarks
import com.pulsestamp.app.export.ExportStore
import com.pulsestamp.app.export.exportFileName
import com.pulsestamp.app.gpx.GpxBuilder
import com.pulsestamp.app.gpx.syntheticRoute
import com.pulsestamp.app.health.HealthAvailability
import com.pulsestamp.app.health.HealthRepository
import com.pulsestamp.app.health.SamsungRoutes
import com.pulsestamp.app.model.ExportedFile
import com.pulsestamp.app.model.HrSample
import com.pulsestamp.app.model.LapBars
import com.pulsestamp.app.model.RouteState
import com.pulsestamp.app.model.Sport
import com.pulsestamp.app.model.fromSamsung
import com.pulsestamp.app.model.mergeSamsung
import com.pulsestamp.app.model.TrackPoint
import com.pulsestamp.app.model.WorkoutSummary
import com.pulsestamp.app.model.lapBars
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Access { CHECKING, MISSING, NEEDS_UPDATE, NO_ACCESS, DENIED, READY }

/** Whether rides from Samsung Health can show their GPS. */
enum class SamsungGps { UNAVAILABLE, NEEDS_ACCESS, READY }

/** With GPS comes first and is the default: those are the rides that make an Insta360 map. */
enum class WorkoutFilter(val label: String) { WITH_GPS("With GPS"), ALL("All"), HR_ONLY("HR only") }

/** Raw HR for one workout, loaded only for the Home card and the detail screen. */
data class WorkoutDetail(val workout: WorkoutSummary, val samples: List<HrSample>, val bars: LapBars)

sealed interface ExportStep {
    data object Idle : ExportStep
    data class NeedsConsent(val workoutId: String) : ExportStep
    data class NeedsSamsungAccess(val workoutId: String) : ExportStep
    data class AskWithoutGps(val workoutId: String, val reason: String? = null) : ExportStep
    data class NeedsStoragePermission(val workoutId: String, val route: List<TrackPoint>?) : ExportStep
    data class Working(val hrSamples: Int) : ExportStep
    data class Done(
        val workoutId: String,
        val file: ExportedFile,
        val gpsPoints: Int,
        val hrSamples: Int,
        val hrOnly: Boolean,
    ) : ExportStep
    data class Failed(val message: String, val workoutId: String) : ExportStep
}

data class AppState(
    val access: Access = Access.CHECKING,
    val welcomed: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val rangeDays: Int = 14,
    val filter: WorkoutFilter = WorkoutFilter.WITH_GPS,
    val workouts: List<WorkoutSummary> = emptyList(),
    val latest: WorkoutDetail? = null,
    val detail: WorkoutDetail? = null,
    val export: ExportStep = ExportStep.Idle,
    val exports: List<ExportedFile> = emptyList(),
    val marks: Map<String, String> = emptyMap(),
    val insta360Installed: Boolean = false,
    val samsungGps: SamsungGps = SamsungGps.UNAVAILABLE,
    val samsungError: String? = null,
) {
    val visibleWorkouts: List<WorkoutSummary>
        get() = workouts.filter {
            when (filter) {
                WorkoutFilter.ALL -> true
                WorkoutFilter.WITH_GPS -> it.hasGps
                WorkoutFilter.HR_ONLY -> !it.hasGps
            }
        }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val health = HealthRepository(app)
    val samsung = SamsungRoutes(app)
    val store = ExportStore(app)
    private val marks = ExportMarks(app)
    private val prefs = app.getSharedPreferences("app", 0)

    private val _state = MutableStateFlow(AppState(welcomed = prefs.getBoolean(KEY_WELCOMED, false)))
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Called on start and every resume, so revoked access sends the user back to Welcome and the list stays fresh. */
    fun refreshAccess() {
        viewModelScope.launch {
            val access = when (health.availability()) {
                HealthAvailability.NOT_INSTALLED -> Access.MISSING
                HealthAvailability.NEEDS_UPDATE -> Access.NEEDS_UPDATE
                HealthAvailability.READY -> if (runCatching { health.hasAccess() }.getOrDefault(false)) Access.READY else Access.NO_ACCESS
            }
            _state.update {
                it.copy(
                    access = if (access == Access.NO_ACCESS && it.access == Access.DENIED) Access.DENIED else access,
                    insta360Installed = store.insta360Intent() != null,
                )
            }
            // Reload on every resume: new workouts sync into Health Connect while the app sits in the background
            if (access == Access.READY) {
                loadWorkouts()
                loadExports()
            }
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        if (granted.containsAll(health.requiredPermissions)) {
            prefs.edit().putBoolean(KEY_WELCOMED, true).apply()
            _state.update { it.copy(access = Access.READY, welcomed = true) }
            loadWorkouts()
            loadExports()
        } else {
            _state.update { it.copy(access = Access.DENIED) }
        }
    }

    fun setRange(days: Int) {
        _state.update { it.copy(rangeDays = days) }
        loadWorkouts()
    }

    fun cycleRange() = setRange(
        when (_state.value.rangeDays) { 7 -> 14; 14 -> 30; else -> 7 }
    )

    fun setFilter(filter: WorkoutFilter) = _state.update { it.copy(filter = filter) }

    fun loadWorkouts() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, busy = false) }
            try {
                val days = _state.value.rangeDays
                val list = withSamsung(health.workouts(days), days)
                _state.update { it.copy(workouts = list, loading = false) }
                val newest = list.firstOrNull()
                _state.update { it.copy(latest = newest?.let { w -> loadDetail(w) }) }
            } catch (e: SecurityException) {
                _state.update { it.copy(access = Access.NO_ACCESS, loading = false) }
            } catch (e: RemoteException) {
                _state.update { it.copy(busy = true, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = true, loading = false) }
            }
        }
    }

    /**
     * Samsung Health keeps routes out of Health Connect and sometimes stops syncing to it, so ask
     * Samsung Health too: its rides get GPS, and rides Health Connect is missing are added.
     */
    private suspend fun withSamsung(list: List<WorkoutSummary>, days: Int): List<WorkoutSummary> {
        val gps = when {
            !samsung.available -> SamsungGps.UNAVAILABLE
            samsung.hasAccess() -> SamsungGps.READY
            else -> SamsungGps.NEEDS_ACCESS
        }
        if (gps != SamsungGps.READY) {
            _state.update { it.copy(samsungGps = gps, samsungError = samsung.lastError) }
            return list
        }
        val now = Instant.now()
        val sessions = samsung.workouts(now.minus(days.toLong(), ChronoUnit.DAYS), now)
        _state.update { it.copy(samsungGps = gps, samsungError = samsung.lastError) }
        return mergeSamsung(list, sessions)
    }

    /** Shows Samsung Health's permission screen, then reloads so its routes and rides appear. */
    fun allowSamsung(activity: Activity) {
        viewModelScope.launch {
            samsung.requestAccess(activity)
            loadWorkouts()
        }
    }

    fun openDetail(id: String) {
        val w = _state.value.workouts.firstOrNull { it.id == id } ?: return
        _state.value.latest?.takeIf { it.workout.id == id }?.let { cached ->
            _state.update { it.copy(detail = cached) }
            return
        }
        _state.update { it.copy(detail = WorkoutDetail(w, emptyList(), lapBars(w.start, w.end, w.laps, emptyList()))) }
        viewModelScope.launch {
            runCatching { loadDetail(w) }.onSuccess { d -> _state.update { it.copy(detail = d) } }
        }
    }

    private suspend fun loadDetail(w: WorkoutSummary): WorkoutDetail {
        val samples = heartRate(w)
        return WorkoutDetail(w, samples, lapBars(w.start, w.end, w.laps, samples))
    }

    // ---- Export ----

    fun startExport(id: String) {
        val w = _state.value.workouts.firstOrNull { it.id == id } ?: return
        if (w.routeState == RouteState.NEEDS_CONSENT) {
            _state.update { it.copy(export = ExportStep.NeedsConsent(id)) }
            return
        }
        viewModelScope.launch {
            val route = if (w.routeState == RouteState.HAS_ROUTE && !w.fromSamsung) runCatching { health.routeIfReadable(id) }.getOrNull() else null
            when {
                route != null || !mayHaveSamsungRoute(w) -> continueExport(id, route)
                samsung.hasAccess() -> continueWithSamsungRoute(id, w)
                else -> _state.update { it.copy(export = ExportStep.NeedsSamsungAccess(id)) }
            }
        }
    }

    private fun mayHaveSamsungRoute(w: WorkoutSummary) =
        samsung.available && w.sport != Sport.INDOOR_CYCLING && w.sport != Sport.TREADMILL

    /** Result of Samsung Health's permission screen. Refused or failed → ask before exporting without GPS. */
    fun onSamsungAccess(id: String, granted: Boolean) {
        val w = _state.value.workouts.firstOrNull { it.id == id } ?: return
        if (!granted) {
            _state.update { it.copy(export = ExportStep.AskWithoutGps(id, samsung.lastError?.let { e -> "Samsung Health said: $e" })) }
            return
        }
        viewModelScope.launch {
            continueWithSamsungRoute(id, w)
            loadWorkouts()
        }
    }

    /** No route found → say why and ask, rather than quietly saving a file without GPS. */
    private suspend fun continueWithSamsungRoute(id: String, w: WorkoutSummary) {
        val route = samsung.route(w.start, w.end)
        if (route != null) {
            continueExport(id, route)
        } else {
            _state.update { it.copy(export = ExportStep.AskWithoutGps(id, samsung.routeProblem)) }
        }
    }

    /** Health Connect's heart rate, or what the watch logged in Samsung Health when it has none. */
    private suspend fun heartRate(w: WorkoutSummary): List<HrSample> {
        val samples = if (w.fromSamsung) emptyList() else health.heartRate(w.start, w.end)
        return samples.ifEmpty { if (samsung.available) samsung.heartRate(w.start, w.end) else samples }
    }

    /** Result of the per-workout route consent. Refused → ask before exporting without GPS. */
    fun onRouteConsent(id: String, route: ExerciseRoute?) {
        val points = route?.let { health.toTrack(it) }?.takeIf { it.isNotEmpty() }
        if (points == null) {
            _state.update { it.copy(export = ExportStep.AskWithoutGps(id)) }
        } else {
            continueExport(id, points)
        }
    }

    fun exportWithoutGps(id: String) = continueExport(id, null)

    fun onStoragePermission(granted: Boolean) {
        val step = _state.value.export as? ExportStep.NeedsStoragePermission ?: return
        if (granted) {
            runExport(step.workoutId, step.route)
        } else {
            _state.update { it.copy(export = ExportStep.Failed("Couldn't save the file: storage access was refused.", step.workoutId)) }
        }
    }

    private fun continueExport(id: String, route: List<TrackPoint>?) {
        if (store.needsStoragePermission && !hasLegacyStoragePermission()) {
            _state.update { it.copy(export = ExportStep.NeedsStoragePermission(id, route)) }
        } else {
            runExport(id, route)
        }
    }

    private fun hasLegacyStoragePermission(): Boolean =
        getApplication<Application>().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun runExport(id: String, route: List<TrackPoint>?) {
        val w = _state.value.workouts.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(export = ExportStep.Working(0)) }
        viewModelScope.launch {
            try {
                val samples = heartRate(w)
                if (samples.isEmpty()) {
                    _state.update { it.copy(export = ExportStep.Failed("No heart rate recorded for this workout.", id)) }
                    return@launch
                }
                _state.update { it.copy(export = ExportStep.Working(samples.size)) }
                val points = route?.takeIf { it.isNotEmpty() } ?: syntheticRoute(w.start, w.end)
                val gpx = GpxBuilder.build(w.name, w.sport, w.start, points, samples)
                val existing = store.list().map { it.name }.toSet()
                val name = exportFileName(w.sport, w.start, ZoneId.systemDefault(), existing)
                val file = store.save(name, gpx.toByteArray(Charsets.UTF_8))
                marks.mark(id, file.name)
                _state.update {
                    it.copy(
                        export = ExportStep.Done(id, file, if (route.isNullOrEmpty()) 0 else points.size, samples.size, route.isNullOrEmpty()),
                    )
                }
                loadExports()
            } catch (e: RemoteException) {
                _state.update { it.copy(export = ExportStep.Failed("Health Connect is busy. Try again in a minute.", id)) }
            } catch (e: SecurityException) {
                _state.update { it.copy(export = ExportStep.Idle, access = Access.NO_ACCESS) }
            } catch (e: Exception) {
                _state.update { it.copy(export = ExportStep.Failed("Couldn't save the file: ${e.message ?: "unknown error"}.", id)) }
            }
        }
    }

    fun retryExport() {
        val failed = _state.value.export as? ExportStep.Failed ?: return
        startExport(failed.workoutId)
    }

    fun dismissExport() = _state.update { it.copy(export = ExportStep.Idle) }

    // ---- Exports tab ----

    fun loadExports() {
        viewModelScope.launch {
            val files = runCatching { store.list() }.getOrDefault(emptyList())
            marks.keepOnly(files.map { it.name }.toSet())
            _state.update { it.copy(exports = files, marks = marks.all()) }
        }
    }

    fun delete(file: ExportedFile) {
        viewModelScope.launch {
            store.delete(file)
            loadExports()
        }
    }

    companion object {
        private const val KEY_WELCOMED = "welcomed"
    }
}
