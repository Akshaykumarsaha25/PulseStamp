package com.instabeats.app.ui

import android.app.Application
import android.os.RemoteException
import androidx.health.connect.client.records.ExerciseRoute
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.instabeats.app.export.ExportMarks
import com.instabeats.app.export.ExportStore
import com.instabeats.app.export.exportFileName
import com.instabeats.app.gpx.GpxBuilder
import com.instabeats.app.gpx.syntheticRoute
import com.instabeats.app.health.HealthAvailability
import com.instabeats.app.health.HealthRepository
import com.instabeats.app.health.SamsungRoutes
import com.instabeats.app.model.ExportedFile
import com.instabeats.app.model.HrSample
import com.instabeats.app.model.LapBars
import com.instabeats.app.model.RouteState
import com.instabeats.app.model.Sport
import com.instabeats.app.model.bestOverlap
import com.instabeats.app.model.TrackPoint
import com.instabeats.app.model.WorkoutSummary
import com.instabeats.app.model.lapBars
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class Access { CHECKING, MISSING, NEEDS_UPDATE, NO_ACCESS, DENIED, READY }

enum class WorkoutFilter(val label: String) { ALL("All"), WITH_GPS("With GPS"), HR_ONLY("HR only") }

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
    val filter: WorkoutFilter = WorkoutFilter.ALL,
    val workouts: List<WorkoutSummary> = emptyList(),
    val latest: WorkoutDetail? = null,
    val detail: WorkoutDetail? = null,
    val export: ExportStep = ExportStep.Idle,
    val exports: List<ExportedFile> = emptyList(),
    val marks: Map<String, String> = emptyMap(),
    val insta360Installed: Boolean = false,
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
                val list = withSamsungRoutes(health.workouts(_state.value.rangeDays))
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

    /** Samsung Health keeps routes out of Health Connect; mark the rides it has a route for. */
    private suspend fun withSamsungRoutes(list: List<WorkoutSummary>): List<WorkoutSummary> {
        if (list.none { it.routeState == RouteState.NO_ROUTE } || !samsung.hasAccess()) return list
        val windows = samsung.routedSessions(list.minOf { it.start }, list.maxOf { it.end })
        return list.map { w ->
            if (w.routeState == RouteState.NO_ROUTE && bestOverlap(w.start, w.end, windows) != null) {
                w.copy(routeState = RouteState.HAS_ROUTE)
            } else {
                w
            }
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
        val samples = health.heartRate(w.start, w.end)
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
            val route = if (w.routeState == RouteState.HAS_ROUTE) runCatching { health.routeIfReadable(id) }.getOrNull() else null
            when {
                route != null || !mayHaveSamsungRoute(w) -> continueExport(id, route)
                samsung.hasAccess() -> continueExport(id, samsung.route(w.start, w.end))
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
            _state.update { it.copy(export = ExportStep.AskWithoutGps(id, samsung.lastError)) }
            return
        }
        viewModelScope.launch {
            continueExport(id, samsung.route(w.start, w.end))
            loadWorkouts()
        }
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
                val samples = health.heartRate(w.start, w.end)
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
