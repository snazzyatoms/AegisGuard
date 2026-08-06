package com.aegisguard.arena;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArenaWaveSpawnRulesTest {

    @Test
    void scaledMobCountAppliesPartyMultiplier() {
        ArenaWaveSpec wave = ArenaWaveSpec.wave("w1", 10, ArenaRarity.COMMON);
        ArenaScalingTable.Row solo = ArenaScalingTable.lavaDungeonDefaults().forPartySize(1);
        ArenaScalingTable.Row four = ArenaScalingTable.lavaDungeonDefaults().forPartySize(4);

        assertEquals(10, ArenaService.scaledMobCount(wave, solo));
        assertEquals(22, ArenaService.scaledMobCount(wave, four)); // ceil(10 * 2.2)
    }

    @Test
    void bossWavesStayAtSpecCount() {
        ArenaWaveSpec boss = ArenaWaveSpec.milestoneBoss("boss_mid", ArenaRarity.RARE, "milestone_boss");
        ArenaScalingTable.Row four = ArenaScalingTable.lavaDungeonDefaults().forPartySize(4);
        assertEquals(1, ArenaService.scaledMobCount(boss, four));
    }

    @Test
    void resolveMobTypeAcceptsTemplates() {
        assertEquals(EntityType.SKELETON, ArenaService.resolveMobType("skeleton"));
        assertEquals(EntityType.WITHER_SKELETON, ArenaService.resolveMobType("wither_skeleton"));
        assertEquals(EntityType.BLAZE, ArenaService.resolveMobType("blaze"));
        assertEquals(EntityType.ZOMBIE, ArenaService.resolveMobType("not_a_real_mob"));
    }

    @Test
    void spawnWaveIndexTracksIdempotency() {
        ArenaRun run = new ArenaRun(java.util.UUID.randomUUID(), "lava", ArenaMode.PVE_WAVES, java.util.UUID.randomUUID());
        assertEquals(-1, run.getSpawnedWaveIndex());
        run.setWaveIndex(0);
        run.setSpawnedWaveIndex(0);
        assertEquals(0, run.getSpawnedWaveIndex());
        assertTrue(run.getSpawnedWaveIndex() >= run.getWaveIndex());
    }
}
