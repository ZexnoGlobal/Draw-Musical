package com.wcs.drawing.game.draw.with.music.`fun`

import org.junit.Assert.*
import org.junit.Test

class RootNoteExpiryTest {
    private val start=1_800_000_000_000L

    @Test fun rewardCreatesExactlyOneHourOfAccess() {
        val window=RootNoteAccessWindow.earned(start)
        assertEquals(start,window.grantedAt)
        assertEquals(start+3_600_000L,window.expiresAt)
    }

    @Test fun accessEndsAtTheExactHourBoundary() {
        val window=RootNoteAccessWindow.earned(start)
        assertTrue(window.active(start+3_600_000L-1L))
        assertFalse(window.active(start+3_600_000L))
        assertFalse(window.active(start-1L))
    }

    @Test fun aRenewedRewardHasANewOneHourWindow() {
        val renewed=RootNoteAccessWindow.earned(start+3_600_000L)
        assertTrue(renewed.active(start+3_600_000L))
        assertFalse(renewed.active(start+7_200_000L))
    }

    @Test fun onlyTheFirstFourKeysAreFree() {
        assertEquals(setOf("c","c#","d","d#"),FREE_ROOT_NOTE_NAMES)
        assertTrue(rootNoteKey(" C# ") in FREE_ROOT_NOTE_NAMES)
        listOf("E","F","F#","G","G#","A","A#","B").forEach {
            assertFalse(rootNoteKey(it) in FREE_ROOT_NOTE_NAMES)
        }
    }

    @Test fun keysHaveIndependentWindows() {
        val windows=mapOf("e" to RootNoteAccessWindow.earned(start))
        assertTrue(windows["e"]!!.active(start))
        assertNull(windows["f"])
    }
}
