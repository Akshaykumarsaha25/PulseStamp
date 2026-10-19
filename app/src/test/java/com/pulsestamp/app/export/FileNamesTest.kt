package com.pulsestamp.app.export

import com.pulsestamp.app.model.Sport
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FileNamesTest {

    // 20:15 UTC on the 18th is 01:45 on the 19th in India
    private val start = Instant.parse("2026-10-18T20:15:00Z")
    private val india = ZoneId.of("Asia/Kolkata")

    @Test fun usesSportAndLocalDate() {
        assertEquals("PulseStamp_Cycling_2026-10-19.gpx", exportFileName(Sport.CYCLING, start, india, emptySet()))
        assertEquals("PulseStamp_Running_2026-10-18.gpx", exportFileName(Sport.RUNNING, start, ZoneId.of("UTC"), emptySet()))
    }

    @Test fun addsStartTimeOnCollision() {
        val taken = setOf("PulseStamp_Cycling_2026-10-19.gpx")
        assertEquals("PulseStamp_Cycling_2026-10-19_0145.gpx", exportFileName(Sport.CYCLING, start, india, taken))
    }

    @Test fun countsUpWhenTimedNameIsTakenToo() {
        val taken = setOf(
            "PulseStamp_Cycling_2026-10-19.gpx",
            "PulseStamp_Cycling_2026-10-19_0145.gpx",
            "PulseStamp_Cycling_2026-10-19_0145_2.gpx",
        )
        assertEquals("PulseStamp_Cycling_2026-10-19_0145_3.gpx", exportFileName(Sport.CYCLING, start, india, taken))
    }
}
