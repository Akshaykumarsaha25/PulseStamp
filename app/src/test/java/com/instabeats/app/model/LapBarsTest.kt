package com.instabeats.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LapBarsTest {

    private val t0 = Instant.parse("2026-10-18T06:00:00Z")
    private fun at(sec: Long) = t0.plusSeconds(sec)

    @Test fun usesWatchLapsAndMarksShortestAsBest() {
        val laps = listOf(Lap(at(0), at(300)), Lap(at(300), at(540)), Lap(at(540), at(900)))
        val samples = listOf(HrSample(at(10), 120), HrSample(at(20), 130), HrSample(at(400), 150))
        val result = lapBars(at(0), at(900), laps, samples)
        assertFalse(result.effortOverTime)
        assertEquals(listOf(false, true, false), result.bars.map { it.best })
        assertEquals(125L, result.bars[0].avgBpm)
        assertEquals(150L, result.bars[1].avgBpm)
        assertNull(result.bars[2].avgBpm)
    }

    @Test fun bestLapTieGoesToEarlierLap() {
        val laps = listOf(Lap(at(0), at(300)), Lap(at(300), at(600)))
        assertEquals(listOf(true, false), lapBars(at(0), at(600), laps, emptyList()).bars.map { it.best })
    }

    @Test fun withoutLapsGivesTenEqualSlicesAndNoBest() {
        val result = lapBars(at(0), at(1000), emptyList(), emptyList())
        assertTrue(result.effortOverTime)
        assertEquals(EFFORT_SLICES, result.bars.size)
        result.bars.forEach { assertEquals(100L, it.end.epochSecond - it.start.epochSecond) }
        assertEquals(at(0), result.bars.first().start)
        assertEquals(at(1000), result.bars.last().end)
        assertTrue(result.bars.none { it.best })
    }
}
