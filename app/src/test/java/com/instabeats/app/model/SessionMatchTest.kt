package com.instabeats.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SessionMatchTest {

    private val t0 = Instant.parse("2026-10-19T05:00:00Z")
    private fun at(min: Long) = t0.plusSeconds(min * 60)

    @Test fun picksTheSameRideEvenWithSlightlyDifferentTimes() {
        val candidates = listOf(at(-120) to at(-60), at(1) to at(61), at(70) to at(80))
        assertEquals(1, bestOverlap(at(0), at(60), candidates))
    }

    @Test fun picksTheLargestOverlap() {
        val candidates = listOf(at(0) to at(35), at(20) to at(60))
        assertEquals(1, bestOverlap(at(0), at(60), candidates))
    }

    @Test fun ignoresWorkoutsThatOnlyTouchTheWindow() {
        assertNull(bestOverlap(at(0), at(60), listOf(at(50) to at(120))))
        assertNull(bestOverlap(at(0), at(60), listOf(at(60) to at(90))))
        assertNull(bestOverlap(at(0), at(60), emptyList()))
    }
}
