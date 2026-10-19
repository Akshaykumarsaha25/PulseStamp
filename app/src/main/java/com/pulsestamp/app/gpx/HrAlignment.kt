package com.pulsestamp.app.gpx

import com.pulsestamp.app.model.HrSample
import com.pulsestamp.app.model.TrackPoint

/**
 * Gives each track point the BPM of the nearest-in-time HR sample.
 *
 * Both lists are sorted by time first, then walked once (two pointers), so this is
 * O(n + m) instead of a nearest-sample search per point. On an exact tie the earlier
 * sample wins. Points before the first or after the last sample take that edge sample.
 * The result is in the order of [points] as given.
 */
fun alignHeartRate(points: List<TrackPoint>, samples: List<HrSample>): LongArray {
    require(samples.isNotEmpty()) { "No heart-rate samples to align" }
    val sorted = samples.sortedBy { it.time }
    val order = points.indices.sortedBy { points[it].time }
    val out = LongArray(points.size)

    // atOrBefore = number of samples with time <= current point time
    var atOrBefore = 0
    for (idx in order) {
        val t = points[idx].time.toEpochMilli()
        while (atOrBefore < sorted.size && sorted[atOrBefore].time.toEpochMilli() <= t) atOrBefore++
        val before = sorted.getOrNull(atOrBefore - 1)
        val after = sorted.getOrNull(atOrBefore)
        out[idx] = when {
            before == null -> after!!.bpm
            after == null -> before.bpm
            t - before.time.toEpochMilli() <= after.time.toEpochMilli() - t -> before.bpm
            else -> after.bpm
        }
    }
    return out
}
