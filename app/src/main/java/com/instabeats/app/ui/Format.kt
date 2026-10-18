package com.instabeats.app.ui

import com.instabeats.app.model.LapBars
import com.instabeats.app.model.WorkoutSummary
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val STAMP = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun dayLabel(t: Instant): String = DAY.format(t.atZone(ZoneId.systemDefault()))

fun duration(w: WorkoutSummary): String {
    val m = Duration.between(w.start, w.end).toMinutes()
    return if (m >= 60) "%d h %02d".format(m / 60, m % 60) else "$m min"
}

/** "8 laps", or "Effort over time" when the watch recorded none. */
fun unitsLabel(w: WorkoutSummary): String =
    if (w.laps.isEmpty()) "Effort over time" else "${w.laps.size} lap" + if (w.laps.size == 1) "" else "s"

/** "Sun 18 Oct · 52 min · 8 laps" */
fun metaLine(w: WorkoutSummary) = "${dayLabel(w.start)} · ${duration(w)} · ${unitsLabel(w)}"

fun lapTime(d: Duration): String {
    val ms = d.toMillis()
    return "%d:%02d.%d".format(ms / 60_000, (ms / 1000) % 60, (ms / 100) % 10)
}

/** Line under the lap bars: "Best lap 4 · 1:38.9 · 184 bpm" or "Effort over time". */
fun bestLine(bars: LapBars): String {
    if (bars.effortOverTime) return "Effort over time"
    val i = bars.bars.indexOfFirst { it.best }
    if (i < 0) return ""
    val b = bars.bars[i]
    val bpm = b.avgBpm?.let { " · $it bpm" } ?: ""
    return "Best lap ${i + 1} · ${lapTime(Duration.between(b.start, b.end))}$bpm"
}

/** "Today 18:42" or "12 Oct 21:30" */
fun madeLabel(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    return if (t.toLocalDate() == today) "Today ${CLOCK.format(t)}" else STAMP.format(t)
}

fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1e6) else "${(bytes + 999) / 1000} KB"
