package com.aegisguard.succession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuccessionRulesTest {

    @Test
    void inactivityRequiresLastSeenAndFullGracePeriod() {
        long now = 100L * 86_400_000L;
        assertFalse(SuccessionService.isInactive(0L, now, 30));
        assertFalse(SuccessionService.isInactive(now - 10L * 86_400_000L, now, 30));
        assertTrue(SuccessionService.isInactive(now - 30L * 86_400_000L, now, 30));
        assertTrue(SuccessionService.isInactive(now - 40L * 86_400_000L, now, 30));
    }

    @Test
    void transferCooldownCountsRemainingMillis() {
        assertEquals(0L, SuccessionService.remainingCooldownMs(0L, 10_000L, 5_000L));
        assertEquals(0L, SuccessionService.remainingCooldownMs(1_000L, 10_000L, 5_000L));
        assertEquals(2_000L, SuccessionService.remainingCooldownMs(7_000L, 10_000L, 5_000L));
    }
}
