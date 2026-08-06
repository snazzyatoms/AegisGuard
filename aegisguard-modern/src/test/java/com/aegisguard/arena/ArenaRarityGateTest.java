package com.aegisguard.arena;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArenaRarityGateTest {

    @Test
    void waveGatedMaxRarityNeverExceeded() {
        ArenaRarityGate gate = ArenaRarityGate.defaults();
        for (int seed = 0; seed < 500; seed++) {
            ArenaRarity rolled = gate.roll(ArenaRarity.UNCOMMON, seed);
            assertTrue(rolled.isAtMost(ArenaRarity.UNCOMMON), "rolled " + rolled);
        }
    }

    @Test
    void sameSeedIsDeterministicForPartyFairness() {
        ArenaRarityGate gate = ArenaRarityGate.defaults();
        assertEquals(gate.roll(ArenaRarity.LEGENDARY, 42), gate.roll(ArenaRarity.LEGENDARY, 42));
    }
}
