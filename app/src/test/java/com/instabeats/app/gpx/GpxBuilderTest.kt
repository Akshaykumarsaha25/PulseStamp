package com.instabeats.app.gpx

import com.instabeats.app.model.HrSample
import com.instabeats.app.model.Sport
import com.instabeats.app.model.TrackPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

class GpxBuilderTest {

    private val start = Instant.parse("2026-10-18T06:30:00.123456789Z")
    private val points = listOf(
        TrackPoint(start, 30.1234567, 76.7654321, 312.5),
        TrackPoint(start.plusSeconds(1), 30.1234600, 76.7654400, null),
    )
    private val samples = listOf(HrSample(start, 120), HrSample(start.plusSeconds(1), 125))

    private val savedLocale = Locale.getDefault()
    private val savedZone = TimeZone.getDefault()

    @After fun restore() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedZone)
    }

    private fun build(name: String = "Morning ride", sport: Sport = Sport.CYCLING) =
        GpxBuilder.build(name, sport, start, points, samples)

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test fun timesAreStrictUtcWithoutFractions() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        val times = Regex("<time>([^<]*)</time>").findAll(build()).map { it.groupValues[1] }.toList()
        assertEquals(listOf("2026-10-18T06:30:00Z", "2026-10-18T06:30:00Z", "2026-10-18T06:30:01Z"), times)
        times.forEach { assertTrue(it, it.matches(Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ"""))) }
    }

    @Test fun coordinatesUseDotDecimalUnderCommaLocale() {
        Locale.setDefault(Locale.GERMANY)
        val xml = build()
        assertTrue(xml.contains("lat=\"30.1234567\" lon=\"76.7654321\""))
        assertTrue(xml.contains("<ele>312.50</ele>"))
        assertFalse(xml.contains("30,1234567"))
    }

    @Test fun missingAltitudeIsWrittenAsZeroLikeBefore() {
        assertTrue(build().contains("<ele>0.00</ele>"))
    }

    @Test fun nameIsXmlEscaped() {
        val xml = build(name = "Ride & <Run> \"fast\"")
        assertTrue(xml.contains("<name>Ride &amp; &lt;Run&gt; &quot;fast&quot;</name>"))
        assertEquals("Ride & <Run> \"fast\"", parse(xml).getElementsByTagName("name").item(0).textContent)
    }

    @Test fun sportSetsTrackType() {
        assertTrue(build(sport = Sport.RUNNING).contains("<type>running</type>"))
        assertTrue(build(sport = Sport.HIKING).contains("<type>hiking</type>"))
        assertFalse(build(sport = Sport.OTHER).contains("<type>"))
    }

    @Test fun outputParsesWithGarminHeartRate() {
        val doc = parse(build())
        val ns = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
        val hr = doc.getElementsByTagNameNS(ns, "hr")
        assertEquals(2, hr.length)
        assertEquals("120", hr.item(0).textContent)
        assertEquals("125", hr.item(1).textContent)
        val gpx = doc.documentElement as Element
        assertEquals("http://www.topografix.com/GPX/1/1", gpx.namespaceURI)
        assertEquals("1.1", gpx.getAttribute("version"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToBuildWithoutHeartRate() {
        GpxBuilder.build("x", Sport.CYCLING, start, points, emptyList())
    }
}
