package com.pulsestamp.app.gpx

import com.pulsestamp.app.model.TrackPoint
import java.time.Instant

/**
 * Track for workouts without GPS, so Insta360 still has timestamps to hang heart rate on:
 * one point per second from [start] to [end] inclusive, covering the whole workout.
 * Same coordinates and drift as the original exporter.
 */
fun syntheticRoute(start: Instant, end: Instant): List<TrackPoint> {
    if (end.isBefore(start)) return emptyList()
    val seconds = end.epochSecond - start.epochSecond
    return (0..seconds).map { step ->
        val offset = step * 0.000005
        TrackPoint(
            time = start.plusSeconds(step),
            latitude = SYNTHETIC_LAT + offset,
            longitude = SYNTHETIC_LON + offset,
        )
    }
}

const val SYNTHETIC_LAT = 30.7046
const val SYNTHETIC_LON = 76.7179
