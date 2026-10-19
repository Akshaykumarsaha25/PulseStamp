package com.pulsestamp.app.gpx

import com.pulsestamp.app.model.HrSample
import com.pulsestamp.app.model.Sport
import com.pulsestamp.app.model.TrackPoint
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Writes GPX 1.1 with Garmin TrackPointExtension v1 heart rate, in the shape Insta360 accepts. */
object GpxBuilder {

    /** Strict UTC, no fractional seconds: the Insta360 Local Files parser rejects anything else. */
    val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .withZone(ZoneOffset.UTC)

    /**
     * Builds the file text. Refuses to run without HR samples, so a file is never
     * written with made-up heart rate.
     */
    fun build(
        name: String,
        sport: Sport,
        start: Instant,
        points: List<TrackPoint>,
        samples: List<HrSample>,
    ): String {
        require(points.isNotEmpty()) { "No track points" }
        val bpm = alignHeartRate(points, samples)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx creator=\"PulseStamp Exporter\" version=\"1.1\"\n")
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("  xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
        sb.append("  <metadata>\n    <time>").append(TIME_FORMAT.format(start)).append("</time>\n  </metadata>\n")
        sb.append("  <trk>\n    <name>").append(escapeXml(name)).append("</name>\n")
        sport.gpxType?.let { sb.append("    <type>").append(it).append("</type>\n") }
        sb.append("    <trkseg>\n")
        points.forEachIndexed { i, p ->
            sb.append("      <trkpt lat=\"").append(fmt("%.7f", p.latitude))
                .append("\" lon=\"").append(fmt("%.7f", p.longitude)).append("\">\n")
            sb.append("        <ele>").append(fmt("%.2f", p.altitudeMeters ?: 0.0)).append("</ele>\n")
            sb.append("        <time>").append(TIME_FORMAT.format(p.time)).append("</time>\n")
            sb.append("        <extensions>\n          <gpxtpx:TrackPointExtension>\n")
            sb.append("            <gpxtpx:hr>").append(bpm[i]).append("</gpxtpx:hr>\n")
            sb.append("          </gpxtpx:TrackPointExtension>\n        </extensions>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun fmt(pattern: String, value: Double) = String.format(Locale.US, pattern, value)

    internal fun escapeXml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
