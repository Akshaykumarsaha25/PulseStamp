package com.pulsestamp.app.export

import com.pulsestamp.app.model.Sport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
private val TIME = DateTimeFormatter.ofPattern("HHmm", Locale.US)

/**
 * `PulseStamp_<Sport>_<yyyy-MM-dd>.gpx` in the device's zone. If that name is taken,
 * the workout's start time is added (`_HHmm`); if that is taken too, `_2`, `_3`, ...
 */
fun exportFileName(sport: Sport, start: Instant, zone: ZoneId, existing: Set<String>): String {
    val local = start.atZone(zone)
    val base = "PulseStamp_${sport.fileLabel}_${DATE.format(local)}"
    val plain = "$base.gpx"
    if (plain !in existing) return plain
    val timed = "${base}_${TIME.format(local)}"
    if ("$timed.gpx" !in existing) return "$timed.gpx"
    var n = 2
    while ("${timed}_$n.gpx" in existing) n++
    return "${timed}_$n.gpx"
}
