package com.instabeats.app.model

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
enum class Sport(val fileLabel: String, val gpxType: String?) {
    CYCLING("Cycling", "cycling"),
    INDOOR_CYCLING("IndoorCycling", "cycling"),
    RUNNING("Running", "running"),
    TREADMILL("Treadmill", "running"),
    HIKING("Hiking", "hiking"),
    WALKING("Walking", "walking"),
    OTHER("Workout", null),
}
