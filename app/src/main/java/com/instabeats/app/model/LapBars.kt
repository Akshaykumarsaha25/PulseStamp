package com.instabeats.app.model

import java.time.Duration
import java.time.Instant

data class LapBar(val start: Instant, val end: Instant, val avgBpm: Long?, val best: Boolean)

/** [effortOverTime] = the watch recorded no laps, so the bars are equal time slices. */
data class LapBars(val bars: List<LapBar>, val effortOverTime: Boolean)

const val EFFORT_SLICES = 10

/**
 * Bars for the Home card and workout detail. Uses the watch's laps; the best lap is the
 * shortest (ties go to the earlier lap). Without laps, 10 equal slices labelled
 * "Effort over time" with no best bar, never fake laps.
 */
fun lapBars(start: Instant, end: Instant, laps: List<Lap>, samples: List<HrSample>): LapBars {
    if (laps.isNotEmpty()) {
        val sorted = laps.sortedBy { it.start }
        val best = sorted.indices.minByOrNull { Duration.between(sorted[it].start, sorted[it].end) }
        return LapBars(
            sorted.mapIndexed { i, lap -> LapBar(lap.start, lap.end, avgBpm(samples, lap.start, lap.end), i == best) },
            effortOverTime = false,
        )
    }
    val totalMs = Duration.between(start, end).toMillis()
    if (totalMs <= 0) return LapBars(emptyList(), effortOverTime = true)
    val bars = (0 until EFFORT_SLICES).map { i ->
        val s = start.plusMillis(totalMs * i / EFFORT_SLICES)
        val e = if (i == EFFORT_SLICES - 1) end else start.plusMillis(totalMs * (i + 1) / EFFORT_SLICES)
        LapBar(s, e, avgBpm(samples, s, e), best = false)
    }
    return LapBars(bars, effortOverTime = true)
}

/** Mean BPM of samples in [from, to), or null if there are none. */
private fun avgBpm(samples: List<HrSample>, from: Instant, to: Instant): Long? {
    val inRange = samples.filter { !it.time.isBefore(from) && it.time.isBefore(to) }
    if (inRange.isEmpty()) return null
    return Math.round(inRange.sumOf { it.bpm }.toDouble() / inRange.size)
}
