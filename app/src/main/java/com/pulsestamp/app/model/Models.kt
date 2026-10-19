package com.pulsestamp.app.model

import java.time.Instant

/** One heart-rate reading from Health Connect. */
data class HrSample(val time: Instant, val bpm: Long)

/** One GPS (or synthetic) position. Altitude is null when the watch didn't record it. */
data class TrackPoint(
    val time: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
)

/** A lap as recorded by the watch. */
data class Lap(val start: Instant, val end: Instant)

/**
 * Our own sport list, mapped once from Health Connect's exercise type.
 * [fileLabel] goes into export file names, [gpxType] into GPX `<trk><type>` (null = omitted).
 */
enum class Sport(val fileLabel: String, val gpxType: String?, val displayName: String) {
    CYCLING("Cycling", "cycling", "Ride"),
    INDOOR_CYCLING("IndoorCycling", "cycling", "Indoor bike"),
    RUNNING("Running", "running", "Run"),
    TREADMILL("Treadmill", "running", "Treadmill run"),
    HIKING("Hiking", "hiking", "Hike"),
    WALKING("Walking", "walking", "Walk"),
    OTHER("Workout", null, "Workout"),
}

/** Where a workout's GPS route stands in Health Connect. */
enum class RouteState { HAS_ROUTE, NEEDS_CONSENT, NO_ROUTE }

/** One row in the workout list; HR numbers come from Health Connect aggregates. */
data class WorkoutSummary(
    val id: String,
    val sport: Sport,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val avgBpm: Long?,
    val peakBpm: Long?,
    val laps: List<Lap>,
    val routeState: RouteState,
) {
    val name: String get() = title?.takeIf { it.isNotBlank() } ?: sport.displayName
    val hasGps: Boolean get() = routeState != RouteState.NO_ROUTE
}

/** A GPX file this app saved in Downloads/PulseStamp. */
data class ExportedFile(val uri: String, val name: String, val createdMillis: Long, val sizeBytes: Long)
