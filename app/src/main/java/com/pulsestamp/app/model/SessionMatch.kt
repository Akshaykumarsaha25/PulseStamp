package com.pulsestamp.app.model

import java.time.Duration
import java.time.Instant

/**
 * Index of the candidate window that overlaps [start]..[end] the most, or null when none covers
 * at least half of it. Matches a Health Connect workout to the same ride in another app.
 */
fun bestOverlap(start: Instant, end: Instant, candidates: List<Pair<Instant, Instant>>): Int? {
    val length = Duration.between(start, end).toMillis().coerceAtLeast(1)
    var best: Int? = null
    var bestMs = 0L
    candidates.forEachIndexed { i, (s, e) ->
        val from = maxOf(s, start)
        val to = minOf(e, end)
        val ms = Duration.between(from, to).toMillis()
        if (ms * 2 >= length && ms > bestMs) {
            best = i
            bestMs = ms
        }
    }
    return best
}

/** A workout as Samsung Health has it, read through its own SDK. */
data class SamsungSession(
    val start: Instant,
    val end: Instant,
    val sport: Sport,
    val title: String?,
    val hasRoute: Boolean,
    val avgBpm: Long?,
    val peakBpm: Long?,
)

/** Id prefix for workouts that only Samsung Health has. */
const val SAMSUNG_ID = "samsung:"

val WorkoutSummary.fromSamsung: Boolean get() = id.startsWith(SAMSUNG_ID)

/**
 * Health Connect workouts plus what Samsung Health knows: a ride Samsung has a route for gets
 * GPS, and a Samsung workout that never reached Health Connect is listed on its own. Newest first.
 */
fun mergeSamsung(health: List<WorkoutSummary>, samsung: List<SamsungSession>): List<WorkoutSummary> {
    val routed = samsung.filter { it.hasRoute }.map { it.start to it.end }
    val upgraded = health.map { w ->
        if (w.routeState == RouteState.NO_ROUTE && bestOverlap(w.start, w.end, routed) != null) {
            w.copy(routeState = RouteState.HAS_ROUTE)
        } else {
            w
        }
    }
    val windows = health.map { it.start to it.end }
    // Same workout if either one covers at least half of the other
    val missing = samsung
        .filter { s -> bestOverlap(s.start, s.end, windows) == null && health.none { bestOverlap(it.start, it.end, listOf(s.start to s.end)) != null } }
        .map { s ->
            WorkoutSummary(
                id = SAMSUNG_ID + s.start.toEpochMilli(),
                sport = s.sport,
                title = s.title,
                start = s.start,
                end = s.end,
                avgBpm = s.avgBpm,
                peakBpm = s.peakBpm,
                laps = emptyList(),
                routeState = if (s.hasRoute) RouteState.HAS_ROUTE else RouteState.NO_ROUTE,
            )
        }
    return (upgraded + missing).sortedByDescending { it.start }
}
