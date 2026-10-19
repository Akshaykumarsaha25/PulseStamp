package com.instabeats.app.model

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
