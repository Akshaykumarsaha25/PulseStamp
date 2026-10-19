package com.instabeats.app.health

import android.app.Activity
import android.content.Context
import android.os.Build
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

    /** Start and end of every Samsung Health workout in the window that has a route. */
    suspend fun routedSessions(start: Instant, end: Instant): List<Pair<Instant, Instant>> =
        sessions(start, end).filter { !it.route.isNullOrEmpty() }.map { it.startTime to it.endTime }

    /** The route of the Samsung Health workout that best matches this one, or null. */
    suspend fun route(start: Instant, end: Instant): List<TrackPoint>? {
        val candidates = sessions(start.minus(SLACK), end.plus(SLACK)).filter { !it.route.isNullOrEmpty() }
        val best = bestOverlap(start, end, candidates.map { it.startTime to it.endTime }) ?: return null
        return candidates[best].route.orEmpty()
            .sortedBy { it.timestamp }
            .map { TrackPoint(it.timestamp, it.latitude.toDouble(), it.longitude.toDouble(), it.altitude?.toDouble()) }
            .takeIf { it.isNotEmpty() }
    }

    private suspend fun sessions(start: Instant, end: Instant): List<ExerciseSession> =
        guarded(emptyList()) {
            val out = mutableListOf<ExerciseSession>()
            var token: String? = null
            do {
                val builder = DataTypes.EXERCISE.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.of(start, end))
                    .setPageSize(PAGE)
                token?.let { builder.setPageToken(it) }
                val response = store.readData(builder.build())
                response.dataList.forEach { point ->
                    out += point.getValue(DataType.ExerciseType.SESSIONS).orEmpty()
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

    companion object {
        private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
        private const val PAGE = 50
        private val SLACK: Duration = Duration.ofMinutes(5)
    }
}
