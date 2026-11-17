package com.wcs.drawing.game.draw.with.music.`fun`

import org.junit.Assert.*
import org.junit.Test

class SubscriptionLeaseTest {
    @Test fun acceptsFreshVerification() {assertTrue(subscriptionLeaseValid(1_000L,1_000L))}
    @Test fun endsAtOneHourBoundary() {
        assertTrue(subscriptionLeaseValid(1_000L,1_000L+3_600_000L-1L))
        assertFalse(subscriptionLeaseValid(1_000L,1_000L+3_600_000L))
    }
    @Test fun rejectsMissingVerificationOrResetClock() {
        assertFalse(subscriptionLeaseValid(-1L,0L));assertFalse(subscriptionLeaseValid(1_000L,999L))
    }
}
