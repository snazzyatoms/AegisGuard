package com.aegisguard.beacon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.4 pub-test defect coverage: the manager must never show the same teleport twice.
 * {@link BeaconService#duplicateBlockBeacons} keeps the oldest record per block and
 * reports the extras so storage can unbind them. Pure logic — no server required.
 */
class BeaconDedupeTest {

    private TeleportBeacon padAt(String world, int x, int y, int z, long createdAt) {
        TeleportBeacon beacon = new TeleportBeacon(UUID.randomUUID());
        beacon.setWorldName(world);
        beacon.setCoordinates(x, y, z, 0f, 0f);
        beacon.setCreatedAt(createdAt);
        return beacon;
    }

    @Test
    void twoRecordsOnOneBlockCollapseToTheOldest() {
        TeleportBeacon oldest = padAt("world", 10, 64, 10, 1_000L);
        TeleportBeacon newer = padAt("world", 10, 64, 10, 5_000L);

        List<TeleportBeacon> extras = BeaconService.duplicateBlockBeacons(List.of(oldest, newer));

        assertEquals(1, extras.size(), "exactly one duplicate should be flagged for removal");
        assertSame(newer, extras.get(0), "the newer duplicate is the extra; the oldest is kept");
    }

    @Test
    void distinctBlocksAreNeverTreatedAsDuplicates() {
        TeleportBeacon a = padAt("world", 10, 64, 10, 1_000L);
        TeleportBeacon b = padAt("world", 11, 64, 10, 2_000L);
        TeleportBeacon c = padAt("nether", 10, 64, 10, 3_000L);

        assertTrue(BeaconService.duplicateBlockBeacons(List.of(a, b, c)).isEmpty(),
                "different world/x/y/z blocks are separate beacons");
    }

    @Test
    void managerSizeEqualsUniqueBlockCount() {
        TeleportBeacon a1 = padAt("world", 0, 64, 0, 1_000L);
        TeleportBeacon a2 = padAt("world", 0, 64, 0, 2_000L);
        TeleportBeacon a3 = padAt("world", 0, 64, 0, 3_000L);
        TeleportBeacon b = padAt("world", 0, 64, 1, 1_000L);

        List<TeleportBeacon> all = List.of(a1, a2, a3, b);
        int extras = BeaconService.duplicateBlockBeacons(all).size();
        // 4 records, 2 unique blocks -> 2 survivors, 2 extras.
        assertEquals(2, extras);
        assertEquals(2, all.size() - extras, "survivors equal the unique block count");
    }

    @Test
    void arrivalModeDefaultsToClassicAndParsesLeniently() {
        assertEquals(com.aegisguard.data.Plot.ArrivalMode.CLASSIC,
                com.aegisguard.data.Plot.ArrivalMode.parse(null));
        assertEquals(com.aegisguard.data.Plot.ArrivalMode.CLASSIC,
                com.aegisguard.data.Plot.ArrivalMode.parse("nonsense"));
        assertEquals(com.aegisguard.data.Plot.ArrivalMode.BEACON,
                com.aegisguard.data.Plot.ArrivalMode.parse("beacon"));
        assertEquals(com.aegisguard.data.Plot.ArrivalMode.BEACON,
                com.aegisguard.data.Plot.ArrivalMode.parse("  BEACON "));
    }
}
