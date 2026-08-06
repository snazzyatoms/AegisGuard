package com.aegisguard.arena;

import com.aegisguard.arena.preset.LavaDungeonPreset;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArenaRunStateMachineTest {

    @Test
    void cleanupRunsOnlyOnce() {
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, UUID.randomUUID());
        assertTrue(run.markCleanupDone());
        assertFalse(run.markCleanupDone());
    }

    @Test
    void wipeWhenNoFightersRemain() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, a);
        run.getOrCreate(a).setState(ParticipantState.ELIMINATED);
        run.getOrCreate(b).setState(ParticipantState.ELIMINATED);
        assertEquals(0, run.countFighting());
    }

    @Test
    void pendingRecoveryIsIdempotent() {
        ArenaPendingRecovery pending = new ArenaPendingRecovery(
                UUID.randomUUID(), UUID.randomUUID(), "snap.yml",
                new ArenaSpawnPoint(null, "world", 0, 64, 0, 0, 0));
        assertTrue(pending.tryComplete("token-1"));
        assertFalse(pending.tryComplete("token-1"));
        assertFalse(pending.tryComplete("token-2"));
        assertEquals(ArenaPendingRecovery.Status.COMPLETE, pending.getStatus());
    }

    @Test
    void definitionRequiresMinimumSetup() {
        ArenaDefinition def = LavaDungeonPreset.createDefinition("lava_test");
        assertFalse(def.isConfigValid());
        assertFalse(def.isEnabled());
        assertNotNull(def.getConfigError());
        assertFalse(def.getWaves().isEmpty());
        assertEquals(ArenaTotemPolicy.CONSUME_AND_ELIMINATE, def.getTotemPolicy());
        assertEquals(1, def.getMaxActiveRuns());
    }

    @Test
    void scalingTableMatchesLavaDefaults() {
        ArenaScalingTable.Row solo = ArenaScalingTable.lavaDungeonDefaults().forPartySize(1);
        ArenaScalingTable.Row four = ArenaScalingTable.lavaDungeonDefaults().forPartySize(4);
        assertEquals(1.0, solo.mobCount, 0.001);
        assertEquals(2.20, four.mobCount, 0.001);
        assertEquals(1.75, four.rewardMultiplier, 0.001);
    }

    @Test
    void scoreTieBreakPrefersDeeperWaveThenScoreThenTime() {
        assertTrue(ArenaScoreService.compareRecords(5, 100, 1000, 4, 999, 100) < 0);
        assertTrue(ArenaScoreService.compareRecords(5, 200, 1000, 5, 100, 100) < 0);
        assertTrue(ArenaScoreService.compareRecords(5, 100, 500, 5, 100, 900) < 0);
    }
}
