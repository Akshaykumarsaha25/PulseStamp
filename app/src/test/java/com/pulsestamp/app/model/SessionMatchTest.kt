package com.pulsestamp.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class MergeSamsungTest {

    private val t0 = Instant.parse("2026-10-19T14:23:00Z")
    private fun at(min: Long) = t0.plusSeconds(min * 60)

    private fun health(id: String, from: Long, to: Long, route: RouteState = RouteState.NO_ROUTE) =
        WorkoutSummary(id, Sport.CYCLING, null, at(from), at(to), 120, 150, emptyList(), route)

    private fun samsung(from: Long, to: Long, route: Boolean = true) =
        SamsungSession(at(from), at(to), Sport.CYCLING, null, route, 130, 160)

    @Test fun givesGpsToTheMatchingHealthConnectRide() {
        val out = mergeSamsung(listOf(health("hc", 0, 30)), listOf(samsung(0, 30)))
        assertEquals(1, out.size)
        assertEquals("hc", out[0].id)
        assertEquals(RouteState.HAS_ROUTE, out[0].routeState)
    }

    @Test fun listsARideOnlySamsungHealthHas() {
        val out = mergeSamsung(listOf(health("old", -600, -540)), listOf(samsung(0, 4)))
        assertEquals(2, out.size)
        assertTrue(out[0].fromSamsung)
        assertEquals(at(0), out[0].start)
        assertEquals(RouteState.HAS_ROUTE, out[0].routeState)
        assertEquals(130L, out[0].avgBpm)
        assertEquals("old", out[1].id)
    }

    @Test fun listsSamsungRidesWhenHealthConnectHasNone() {
        val out = mergeSamsung(emptyList(), listOf(samsung(0, 4, route = false)))
        assertEquals(1, out.size)
        assertFalse(out[0].hasGps)
    }

    @Test fun keepsShortSamsungPartsOfALongHealthConnectWorkoutOutOfTheList() {
        val out = mergeSamsung(listOf(health("hc", 0, 60)), listOf(samsung(10, 15, route = false)))
        assertEquals(listOf("hc"), out.map { it.id })
        assertEquals(RouteState.NO_ROUTE, out[0].routeState)
    }
}
