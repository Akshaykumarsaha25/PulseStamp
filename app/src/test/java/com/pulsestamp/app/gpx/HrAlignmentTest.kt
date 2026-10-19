package com.pulsestamp.app.gpx

import com.pulsestamp.app.model.HrSample
import com.pulsestamp.app.model.TrackPoint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HrAlignmentTest {

    private val t0 = Instant.parse("2026-10-18T06:00:00Z")
    private fun pt(sec: Long) = TrackPoint(t0.plusSeconds(sec), 0.0, 0.0)
    private fun hr(sec: Long, bpm: Long) = HrSample(t0.plusSeconds(sec), bpm)

    @Test fun picksNearestSample() {
        val out = alignHeartRate(listOf(pt(0), pt(4), pt(6), pt(10)), listOf(hr(0, 100), hr(10, 150)))
        assertArrayEquals(longArrayOf(100, 100, 150, 150), out)
    }

    @Test fun tieGoesToEarlierSample() {
        assertArrayEquals(longArrayOf(100), alignHeartRate(listOf(pt(5)), listOf(hr(0, 100), hr(10, 150))))
    }

    @Test fun pointsOutsideSamplesTakeEdgeSample() {
        val out = alignHeartRate(listOf(pt(0), pt(100)), listOf(hr(30, 110), hr(40, 120)))
        assertArrayEquals(longArrayOf(110, 120), out)
    }

    @Test fun unsortedInputIsHandledAndOutputKeepsPointOrder() {
        val out = alignHeartRate(listOf(pt(20), pt(0), pt(10)), listOf(hr(20, 130), hr(0, 100), hr(10, 115)))
        assertArrayEquals(longArrayOf(130, 100, 115), out)
    }

    @Test fun fourHoursAtOneHertzIsFast() {
        val n = 4 * 3600L
        val points = (0 until n).map { pt(it) }
        val samples = (0 until n).map { hr(it, 60 + it % 120) }
        val began = System.nanoTime()
        val out = alignHeartRate(points, samples)
        val ms = (System.nanoTime() - began) / 1_000_000
        assertEquals(60 + (n - 1) % 120, out.last())
        assertTrue("took $ms ms", ms < 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noSamplesIsRejected() {
        alignHeartRate(listOf(pt(0)), emptyList())
    }
}
