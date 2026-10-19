package com.instabeats.app.health

import android.app.Activity
import android.content.Context
import android.os.Build
import com.instabeats.app.model.HrSample
import com.instabeats.app.model.SamsungSession
import com.instabeats.app.model.Sport
import com.instabeats.app.model.TrackPoint
import com.instabeats.app.model.bestOverlap
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataType.ExerciseType.PredefinedExerciseType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * GPS routes straight from Samsung Health. Samsung Health sends workouts and heart rate to
 * Health Connect but never the route, so for Galaxy Watch rides this is the only way to get GPS.
 * Works on the phone, no network: the SDK talks to the Samsung Health app.
 */
class SamsungRoutes(private val context: Context) {

    private val permissions by lazy {
        setOf(
            Permission.of(DataTypes.EXERCISE, AccessType.READ),
            Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ),
        )
    }

    private val store: HealthDataStore by lazy { HealthDataService.getStore(context) }

    /** Why the last call failed, in Samsung Health's words, for the export sheet. */
    var lastError: String? = null
        private set

    /** The SDK needs Android 10+ and the Samsung Health app. */
    val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH) != null

    suspend fun hasAccess(): Boolean = available && guarded(false) {
        store.getGrantedPermissions(permissions).containsAll(permissions)
    }

    /** Shows Samsung Health's own permission screen. */
    suspend fun requestAccess(activity: Activity): Boolean = available && guarded(false, activity) {
        store.requestPermissions(permissions, activity).containsAll(permissions)
    }

    /** Every Samsung Health workout in the window, so rides missing from Health Connect still show. */
    suspend fun workouts(start: Instant, end: Instant): List<SamsungSession> =
        sessions(start, end).map { (session, sport, title) ->
            SamsungSession(
                start = session.startTime,
                end = session.endTime,
                sport = sport,
                title = session.customTitle ?: title,
                hasRoute = !session.route.isNullOrEmpty(),
                avgBpm = session.meanHeartRate?.takeIf { it > 0 }?.toLong(),
                peakBpm = session.maxHeartRate?.takeIf { it > 0 }?.toLong(),
            )
        }

    /** Why the last [route] came back empty, in plain words, for the export sheet. */
    var routeProblem: String? = null
        private set

    /** The route of the Samsung Health workout that best matches this one, or null. */
    suspend fun route(start: Instant, end: Instant): List<TrackPoint>? {
        val near = sessions(start.minus(SLACK), end.plus(SLACK)).map { it.first }
        val error = lastError
        val points = match(start, end, near) { !it.route.isNullOrEmpty() }?.route.orEmpty()
            .sortedBy { it.timestamp }
            .map { TrackPoint(it.timestamp, it.latitude.toDouble(), it.longitude.toDouble(), it.altitude?.toDouble()) }
            .takeIf { it.isNotEmpty() }
        routeProblem = when {
            points != null -> null
            error != null -> "Samsung Health said: $error"
            near.isEmpty() -> "Samsung Health has no workout at this time."
            near.all { it.route.isNullOrEmpty() } -> "Samsung Health has this workout but gave no GPS route for it."
            else -> "Samsung Health's route didn't line up with this workout's time."
        }
        return points
    }

    /** Heart rate the watch logged during the matching workout, for rides Health Connect doesn't have. */
    suspend fun heartRate(start: Instant, end: Instant): List<HrSample> {
        val near = sessions(start.minus(SLACK), end.plus(SLACK)).map { it.first }
        return match(start, end, near) { s -> s.log.orEmpty().any { it.heartRate != null } }?.log.orEmpty()
            .mapNotNull { log -> log.heartRate?.takeIf { it > 0 }?.let { HrSample(log.timestamp, it.toLong()) } }
            .sortedBy { it.time }
    }

    private fun match(start: Instant, end: Instant, near: List<ExerciseSession>, keep: (ExerciseSession) -> Boolean): ExerciseSession? {
        val candidates = near.filter(keep)
        val best = bestOverlap(start, end, candidates.map { it.startTime to it.endTime }) ?: return null
        return candidates[best]
    }

    /** Each session with the sport and title of the workout it belongs to. */
    private suspend fun sessions(start: Instant, end: Instant): List<Triple<ExerciseSession, Sport, String?>> =
        guarded(emptyList()) {
            val out = mutableListOf<Triple<ExerciseSession, Sport, String?>>()
            var token: String? = null
            do {
                val builder = DataTypes.EXERCISE.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.of(start, end))
                    .setPageSize(PAGE)
                token?.let { builder.setPageToken(it) }
                val response = store.readData(builder.build())
                response.dataList.forEach { point ->
                    val sport = sportOf(point.getValue(DataType.ExerciseType.EXERCISE_TYPE))
                    val title = point.getValue(DataType.ExerciseType.CUSTOM_TITLE)
                    point.getValue(DataType.ExerciseType.SESSIONS).orEmpty().forEach { out += Triple(it, sport, title) }
                }
                token = response.pageToken
            } while (!token.isNullOrEmpty())
            out
        }

    private suspend fun <T> guarded(fallback: T, activity: Activity? = null, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                block().also { lastError = null }
            } catch (e: ResolvablePlatformException) {
                lastError = e.errorMessage ?: e.message
                // e.g. Samsung Health needs an update or the user must accept its terms
                if (activity != null && e.hasResolution) withContext(Dispatchers.Main) { e.resolve(activity) }
                fallback
            } catch (e: HealthDataException) {
                lastError = e.errorMessage ?: e.message
                fallback
            } catch (e: Exception) {
                lastError = e.message
                fallback
            }
        }

    private fun sportOf(type: PredefinedExerciseType?): Sport = when (type) {
        PredefinedExerciseType.BIKING, PredefinedExerciseType.MOUNTAIN_BIKING -> Sport.CYCLING
        PredefinedExerciseType.STATIONARY_BIKING -> Sport.INDOOR_CYCLING
        PredefinedExerciseType.RUNNING, PredefinedExerciseType.TRACK_RUNNING -> Sport.RUNNING
        PredefinedExerciseType.TREADMILL -> Sport.TREADMILL
        PredefinedExerciseType.HIKING -> Sport.HIKING
        PredefinedExerciseType.WALKING -> Sport.WALKING
        else -> Sport.OTHER
    }

    companion object {
        private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
        private const val PAGE = 50
        private val SLACK: Duration = Duration.ofMinutes(5)
    }
}
