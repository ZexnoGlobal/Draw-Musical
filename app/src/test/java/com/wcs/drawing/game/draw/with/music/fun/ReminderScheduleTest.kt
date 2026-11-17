package com.wcs.drawing.game.draw.with.music.`fun`

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {
    private fun time(value:String)=Instant.parse(value).toEpochMilli()
    @Test fun beforeEveningSchedulesToday() {
        assertEquals(time("2026-10-23T14:00:00Z"),nextReminderTime(time("2026-10-23T12:00:00Z"),ZoneId.of("Asia/Karachi")))
    }
    @Test fun atBoundarySchedulesTomorrow() {
        assertEquals(time("2026-10-24T14:00:00Z"),nextReminderTime(time("2026-10-23T14:00:00Z"),ZoneId.of("Asia/Karachi")))
    }
    @Test fun afterEveningSchedulesTomorrow() {
        assertEquals(time("2026-10-24T14:00:00Z"),nextReminderTime(time("2026-10-23T19:00:00Z"),ZoneId.of("Asia/Karachi")))
    }
    @Test fun springDaylightSavingUsesLocalEvening() {
        assertEquals(time("2026-03-08T23:00:00Z"),nextReminderTime(time("2026-03-08T01:00:00Z"),ZoneId.of("America/New_York")))
    }
    @Test fun autumnDaylightSavingUsesLocalEvening() {
        assertEquals(time("2026-11-02T00:00:00Z"),nextReminderTime(time("2026-11-01T01:00:00Z"),ZoneId.of("America/New_York")))
    }
}
