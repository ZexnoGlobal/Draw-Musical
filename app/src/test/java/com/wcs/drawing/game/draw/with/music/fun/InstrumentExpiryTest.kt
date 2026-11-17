package com.wcs.drawing.game.draw.with.music.`fun`

import org.junit.Assert.*
import org.junit.Test

class InstrumentExpiryTest {
    private val start=1_800_000_000_000L

    @Test fun rewardCreatesExactlyOneHourOfAccess() {
        val window=InstrumentAccessWindow.earned(start)
        assertEquals(start,window.grantedAt)
        assertEquals(start+3_600_000L,window.expiresAt)
    }

    @Test fun accessEndsAtTheExactHourBoundary() {
        val window=InstrumentAccessWindow.earned(start)
        assertTrue(window.active(start+3_600_000L-1L))
        assertFalse(window.active(start+3_600_000L))
        assertFalse(window.active(start-1L))
    }

    @Test fun aRenewedRewardHasANewOneHourWindow() {
        val renewed=InstrumentAccessWindow.earned(start+3_600_000L)
        assertTrue(renewed.active(start+3_600_000L))
        assertFalse(renewed.active(start+7_200_000L))
    }

    @Test fun theSixFreeInstrumentsUseCanonicalNames() {
        assertEquals(setOf("piano","marimba","bells","synth","guitar","flute"),FREE_INSTRUMENT_NAMES)
        assertTrue(instrumentKey("  PIANO  ") in FREE_INSTRUMENT_NAMES)
        assertFalse(instrumentKey("Strings") in FREE_INSTRUMENT_NAMES)
        assertFalse(instrumentKey("Bass") in FREE_INSTRUMENT_NAMES)
    }
}
