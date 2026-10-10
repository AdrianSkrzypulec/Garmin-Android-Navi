package com.navi.garmin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverParserTest {

    @Test
    fun testPolishTurnLeft() {
        val nav = ManeuverParser.parse(
            title = "250 m",
            text = "Skręć w lewo na Marszałkowska",
            subText = "15 min · 4,2 km · 16:30",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.TURN_LEFT, nav.action)
        assertEquals("250 m", nav.distance)
        assertEquals("Marszałkowska", nav.street)
        assertEquals("16:30", nav.eta)
        assertEquals("4.2 km", nav.remainDist)
        assertEquals("15 min", nav.remainTime)
    }

    @Test
    fun testPolishTurnRight() {
        val nav = ManeuverParser.parse(
            title = "Skręć w prawo w ul. Nowogrodzka",
            text = "Za 500 metrów",
            subText = "10 min · 2.5 km · 14:10",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.TURN_RIGHT, nav.action)
        assertEquals("500 m", nav.distance)
        assertEquals("ul. Nowogrodzka", nav.street)
    }

    @Test
    fun testPolishRoundaboutWithExit() {
        val nav = ManeuverParser.parse(
            title = "300 m",
            text = "Na rondzie zjedź 2. zjazdem na Aleje Jerozolimskie",
            subText = "8 min · 3,1 km · 12:45",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.ROUNDABOUT, nav.action)
        assertEquals(2, nav.exitNumber)
        assertEquals("300 m", nav.distance)
    }

    @Test
    fun testPolishUTurn() {
        val nav = ManeuverParser.parse(
            title = "50 m",
            text = "Zawróć, gdy to możliwe",
            subText = null,
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.U_TURN, nav.action)
        assertEquals("50 m", nav.distance)
    }

    @Test
    fun testPolishDestination() {
        val nav = ManeuverParser.parse(
            title = "Cel podróży",
            text = "Dotarłeś do celu",
            subText = "0 min · 0 km",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.DESTINATION, nav.action)
    }

    @Test
    fun testEnglishTurnLeft() {
        val nav = ManeuverParser.parse(
            title = "500 ft",
            text = "Turn left onto Main Street",
            subText = "20 min · 5.5 mi · 4:15 PM",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.TURN_LEFT, nav.action)
        assertEquals("500 ft", nav.distance)
        assertEquals("Main Street", nav.street)
        assertEquals("4:15 PM", nav.eta)
    }

    @Test
    fun testEnglishRoundabout() {
        val nav = ManeuverParser.parse(
            title = "0.2 mi",
            text = "At the roundabout take the 3rd exit",
            subText = null,
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.ROUNDABOUT, nav.action)
        assertEquals(3, nav.exitNumber)
    }

    @Test
    fun testMapyComPolishTurnRight() {
        val nav = ManeuverParser.parse(
            title = "Za 200 m",
            text = "Skręć w prawo w ul. Główna",
            subText = "15 min · 12,4 km · 14:30",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.TURN_RIGHT, nav.action)
        assertEquals("200 m", nav.distance)
        assertEquals("ul. Główna", nav.street)
        assertEquals("14:30", nav.eta)
        assertEquals("12.4 km", nav.remainDist)
        assertEquals("15 min", nav.remainTime)
    }

    @Test
    fun testMapyComPolishRoundabout() {
        val nav = ManeuverParser.parse(
            title = "Po 300 m",
            text = "Na rondzie zjedź 2. zjazdem w ulicę Warszawską",
            subText = "12 min · 3.5 km · 14:20",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.ROUNDABOUT, nav.action)
        assertEquals(2, nav.exitNumber)
        assertEquals("300 m", nav.distance)
        assertEquals("14:20", nav.eta)
    }

    @Test
    fun testMapyComIconResourceNameDetection() {
        val nav = ManeuverParser.parse(
            title = "200 m",
            text = "ul. Marszałkowska",
            subText = "10 min · 3 km · 12:00",
            bigText = null,
            iconResName = "ic_nav_turn_right"
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.TURN_RIGHT, nav.action)
        assertEquals("200 m", nav.distance)
        assertEquals("ul. Marszałkowska", nav.street)
    }

    @Test
    fun testMapyComUnicodeArrowDetection() {
        val nav = ManeuverParser.parse(
            title = "200 m ↱",
            text = "ul. Marszałkowska",
            subText = "10 min · 3 km · 12:00",
            bigText = null
        )

        assertTrue(nav.hasRoute)
        assertEquals(ManeuverType.TURN_RIGHT, nav.action)
        assertEquals("200 m", nav.distance)
    }
}
