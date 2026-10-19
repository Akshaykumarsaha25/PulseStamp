package com.pulsestamp.app.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyntheticRouteTest {

    private val start = Instant.parse("2026-10-18T06:00:00Z")

    @Test fun onePointPerSecondInclusive() {
        val route = syntheticRoute(start, start.plusSeconds(10))
        assertEquals(11, route.size)
        assertEquals(start, route.first().time)
        assertEquals(start.plusSeconds(10), route.last().time)
        route.zipWithNext().forEach { (a, b) -> assertEquals(1, b.time.epochSecond - a.time.epochSecond) }
    }

    @Test fun coversWorkoutsLongerThanFourHours() {
        val end = start.plusSeconds(6 * 3600)
        val route = syntheticRoute(start, end)
        assertEquals(6 * 3600 + 1, route.size)
        assertEquals(end, route.last().time)
    }

    @Test fun keepsOriginalCoordinates() {
        val route = syntheticRoute(start, start.plusSeconds(2))
        assertEquals(SYNTHETIC_LAT, route[0].latitude, 1e-12)
        assertEquals(SYNTHETIC_LON, route[0].longitude, 1e-12)
        assertEquals(SYNTHETIC_LAT + 0.00001, route[2].latitude, 1e-12)
    }

    @Test fun endBeforeStartGivesNothing() {
        assertTrue(syntheticRoute(start, start.minusSeconds(1)).isEmpty())
    }
}
