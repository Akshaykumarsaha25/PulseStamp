package com.pulsestamp.app.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pulsestamp.app.model.HrSample
import com.pulsestamp.app.model.Lap
import com.pulsestamp.app.model.RouteState
import com.pulsestamp.app.model.Sport
import com.pulsestamp.app.model.TrackPoint
import com.pulsestamp.app.model.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

enum class HealthAvailability { READY, NOT_INSTALLED, NEEDS_UPDATE }

/** Everything the app reads from Health Connect. Read-only: nothing is ever written back. */
class HealthRepository(private val context: Context) {

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    /** Asked for together with the required ones; speed is optional. */
    val requestedPermissions = requiredPermissions + HealthPermission.getReadPermission(SpeedRecord::class)

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.READY
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.NOT_INSTALLED
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasAccess(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)

    /** Sessions from the last [days] days, newest first, with avg/peak BPM from aggregates. */
    suspend fun workouts(days: Int): List<WorkoutSummary> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val sessions = readAll(
            ExerciseSessionRecord::class,
            TimeRangeFilter.between(now.minus(days.toLong(), ChronoUnit.DAYS), now),
        ).sortedByDescending { it.startTime }

        // A few aggregate calls at a time, to stay inside Health Connect's rate limits
        val gate = Semaphore(4)
        coroutineScope {
            sessions.map { s ->
                async {
                    gate.withPermit {
                        val agg = client.aggregate(
                            AggregateRequest(
                                metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX),
                                timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime),
                            )
                        )
                        WorkoutSummary(
                            id = s.metadata.id,
                            sport = sportOf(s.exerciseType),
                            title = s.title,
                            start = s.startTime,
                            end = s.endTime,
                            avgBpm = agg[HeartRateRecord.BPM_AVG],
                            peakBpm = agg[HeartRateRecord.BPM_MAX],
                            laps = s.laps.map { Lap(it.startTime, it.endTime) },
                            routeState = when (s.exerciseRouteResult) {
                                is ExerciseRouteResult.Data -> RouteState.HAS_ROUTE
                                is ExerciseRouteResult.ConsentRequired -> RouteState.NEEDS_CONSENT
                                else -> RouteState.NO_ROUTE
                            },
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /** Every HR sample in the window, all pages, sorted by time. */
    suspend fun heartRate(start: Instant, end: Instant): List<HrSample> = withContext(Dispatchers.IO) {
        readAll(HeartRateRecord::class, TimeRangeFilter.between(start, end))
            .flatMap { r -> r.samples.map { HrSample(it.time, it.beatsPerMinute) } }
            .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
            .sortedBy { it.time }
    }

    /** The route already readable for this session, or null when it needs consent or has none. */
    suspend fun routeIfReadable(sessionId: String): List<TrackPoint>? = withContext(Dispatchers.IO) {
        val session = client.readRecord(ExerciseSessionRecord::class, sessionId).record
        (session.exerciseRouteResult as? ExerciseRouteResult.Data)?.exerciseRoute?.let(::toTrack)
    }

    fun toTrack(route: ExerciseRoute): List<TrackPoint> = route.route.map {
        TrackPoint(it.time, it.latitude, it.longitude, it.altitude?.inMeters)
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageSize = 1000, pageToken = token)
            )
            out += response.records
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

    /** Opening Samsung Health makes the watch sync, which pushes new workouts into Health Connect. */
    fun samsungHealthIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH)

    fun playStoreIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.android.apps.healthdata"),
    ).setPackage("com.android.vending")

    companion object {
        private const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"

        fun sportOf(type: Int): Sport = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> Sport.CYCLING
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> Sport.INDOOR_CYCLING
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> Sport.RUNNING
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> Sport.TREADMILL
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> Sport.HIKING
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> Sport.WALKING
            else -> Sport.OTHER
        }
    }
}
