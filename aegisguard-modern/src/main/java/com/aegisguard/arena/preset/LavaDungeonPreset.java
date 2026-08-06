package com.aegisguard.arena.preset;

import com.aegisguard.arena.ArenaDefinition;
import com.aegisguard.arena.ArenaInventoryPolicy;
import com.aegisguard.arena.ArenaMode;
import com.aegisguard.arena.ArenaRarity;
import com.aegisguard.arena.ArenaScalingTable;
import com.aegisguard.arena.ArenaTotemPolicy;
import com.aegisguard.arena.ArenaWaveSpec;

import java.util.List;

/**
 * Factory for the shipped lava_dungeon preset definition (plot binds filled by staff).
 */
public final class LavaDungeonPreset {

    public static final String PRESET_ID = "lava_dungeon";

    private LavaDungeonPreset() {}

    public static ArenaDefinition createDefinition(String arenaId) {
        ArenaDefinition def = new ArenaDefinition(arenaId == null ? "lava_dungeon_1" : arenaId);
        def.setDisplayName("Lava Dungeon");
        def.setPresetId(PRESET_ID);
        def.setMode(ArenaMode.PVE_WAVES);
        def.setMaxActiveRuns(1);
        def.setMinPlayers(1);
        def.setMaxPlayers(4);
        def.setAllowLateJoin(false);
        def.setInventoryPolicy(ArenaInventoryPolicy.SAVE_AND_RESTORE);
        def.setTotemPolicy(ArenaTotemPolicy.CONSUME_AND_ELIMINATE);
        def.setMaxActiveMobs(48);
        def.setRewardCooldownSeconds(1800L);
        def.setMinWaveForPayout(4);
        def.setScaling(ArenaScalingTable.lavaDungeonDefaults());

        List<ArenaWaveSpec> waves = def.getWaves();
        waves.add(ArenaWaveSpec.wave("w1", 6, ArenaRarity.COMMON));
        waves.add(ArenaWaveSpec.wave("w2", 8, ArenaRarity.COMMON));
        waves.add(ArenaWaveSpec.wave("w3", 8, ArenaRarity.UNCOMMON));
        waves.add(ArenaWaveSpec.wave("w4", 10, ArenaRarity.UNCOMMON));
        waves.add(ArenaWaveSpec.milestoneBoss("boss_mid", ArenaRarity.RARE, "milestone_boss"));
        waves.add(ArenaWaveSpec.wave("w6", 12, ArenaRarity.RARE));
        waves.add(ArenaWaveSpec.wave("w7", 12, ArenaRarity.EPIC));
        waves.add(ArenaWaveSpec.finalBoss("boss_final"));

        // Staff must bind plots and spawns before enabling.
        def.revalidate();
        return def;
    }

    public static void applyPresetWaves(ArenaDefinition def) {
        if (def == null) return;
        def.getWaves().clear();
        ArenaDefinition template = createDefinition(def.getId());
        def.getWaves().addAll(template.getWaves());
        def.setPresetId(PRESET_ID);
        def.setTotemPolicy(ArenaTotemPolicy.CONSUME_AND_ELIMINATE);
        def.setMaxActiveRuns(1);
        def.setMinPlayers(1);
        def.setMaxPlayers(4);
        def.setScaling(ArenaScalingTable.lavaDungeonDefaults());
        def.revalidate();
    }
}
