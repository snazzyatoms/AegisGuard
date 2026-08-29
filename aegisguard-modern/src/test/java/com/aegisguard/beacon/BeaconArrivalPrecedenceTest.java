package com.aegisguard.beacon;

import com.aegisguard.notify.PlayerNotificationSettings.ArrivalPreference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconArrivalPrecedenceTest {

    @Test
    void forcePublicArrivalAlwaysUsesBeacon() {
        assertTrue(BeaconService.resolveBeaconArrival(true, false, true, true,
                ArrivalPreference.CLASSIC, false));
        assertTrue(BeaconService.resolveBeaconArrival(true, false, true, true,
                ArrivalPreference.OWNER_DEFAULT, true));
    }

    @Test
    void ownerBeaconModeWinsWithoutOverride() {
        assertTrue(BeaconService.resolveBeaconArrival(false, true, true, false,
                ArrivalPreference.CLASSIC, true));
        assertFalse(BeaconService.resolveBeaconArrival(false, false, true, false,
                ArrivalPreference.BEACON, true));
    }

    @Test
    void travelerClassicOverrideSkipsBeaconWhenAllowed() {
        assertFalse(BeaconService.resolveBeaconArrival(false, true, true, true,
                ArrivalPreference.CLASSIC, true));
    }

    @Test
    void travelerBeaconOverrideAppliesOnlyWhenPadExists() {
        assertTrue(BeaconService.resolveBeaconArrival(false, false, true, true,
                ArrivalPreference.BEACON, true));
        assertFalse(BeaconService.resolveBeaconArrival(false, false, true, true,
                ArrivalPreference.BEACON, false));
    }

    @Test
    void ownerDefaultKeepsPlotMode() {
        assertTrue(BeaconService.resolveBeaconArrival(false, true, true, true,
                ArrivalPreference.OWNER_DEFAULT, true));
        assertFalse(BeaconService.resolveBeaconArrival(false, false, true, true,
                ArrivalPreference.OWNER_DEFAULT, true));
    }
}
