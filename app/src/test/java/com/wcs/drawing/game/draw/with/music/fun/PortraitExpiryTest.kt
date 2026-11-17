package com.wcs.drawing.game.draw.with.music.`fun`

import org.junit.Assert.*
import org.junit.Test

class PortraitExpiryTest {
    private val start=1_800_000_000_000L

    @Test fun onlyFinalAdStartsThe24HourWindow() {
        val partial=PortraitRewardState().earned(2,start)
        assertEquals(1,partial.watched)
        assertEquals(0L,partial.expiresAtMs)
        val finalTime=start+5_000L
        val unlocked=partial.earned(2,finalTime)
        assertEquals(finalTime,unlocked.unlockedAtMs)
        assertEquals(finalTime+86_400_000L,unlocked.expiresAtMs)
    }

    @Test fun expiryBoundaryIsExclusive() {
        val unlocked=PortraitRewardState().earned(1,start)
        assertTrue(unlocked.isUnlocked(start+86_400_000L-1L))
        assertFalse(unlocked.isUnlocked(start+86_400_000L))
    }

    @Test fun expiredPortraitRequiresAllAdsAgain() {
        val unlocked=PortraitRewardState(1).earned(2,start)
        val nextDay=start+86_400_000L
        assertEquals(0,unlocked.effectiveWatched(2,nextDay))
        val renewedPartial=unlocked.earned(2,nextDay)
        assertEquals(PortraitRewardState(1),renewedPartial)
        assertFalse(renewedPartial.isUnlocked(nextDay))
        assertTrue(renewedPartial.earned(2,nextDay+10L).isUnlocked(nextDay+10L))
    }

    @Test fun partialCreditsPersistUntilCompletion() {
        val partial=PortraitRewardState().earned(3,start)
        assertEquals(1,partial.effectiveWatched(3,start+10L*86_400_000L))
    }

    @Test fun activeUnlockIsNotExtendedByExtraCallbacks() {
        val unlocked=PortraitRewardState().earned(1,start)
        assertEquals(unlocked,unlocked.earned(1,start+1_000L))
    }

    @Test fun timestampBeforeGrantDoesNotAuthorizeAccess() {
        val unlocked=PortraitRewardState().earned(1,start)
        assertFalse(unlocked.isUnlocked(start-1L))
    }
}
