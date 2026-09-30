package com.aegisguard;

import com.aegisguard.config.Modules;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the per-plot biome feature: config plumbing, module gating,
 * command wiring, and the Folia-safe apply path must all stay wired.
 */
class BiomeFeatureContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void shippedConfigShipsBiomeSection() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        Map<String, Object> biomes = (Map<String, Object>) config.get("biomes");
        assertNotNull(biomes, "config.yml must ship a biomes section");
        assertEquals(Boolean.TRUE, biomes.get("enabled"));
        assertTrue(((Number) biomes.get("cost_per_change")).doubleValue() >= 0.0);
        List<String> allowed = (List<String>) biomes.get("allowed");
        assertNotNull(allowed, "biomes.allowed must exist");
        assertTrue(allowed.contains("PLAINS"), "biomes.allowed must include PLAINS");
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("biomes"), "modules.biomes must default on");
    }

    @Test
    void biomeSubcommandIsModuleGated() {
        assertEquals(Modules.Id.BIOMES, Modules.commandModule("biome"));
        assertEquals(Modules.Id.BIOMES, Modules.commandModule("BIOME"));
    }

    @Test
    void commandAndGuiAreWired() throws Exception {
        String cmd = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(cmd.contains("\"biome\""), "/aegis must list the biome subcommand");
        assertTrue(cmd.contains("handleBiome"), "biome case must dispatch to handleBiome");
        assertTrue(cmd.contains("plugin.gui().biome().open"), "handleBiome must open BiomeGUI");

        String gui = Files.readString(JAVA.resolve("gui/BiomeGUI.java"));
        assertTrue(gui.contains("BiomeHolder"), "BiomeGUI must declare a holder");
        assertTrue(gui.contains("Modules.Id.BIOMES"), "BiomeGUI must gate on modules.biomes");
        assertTrue(gui.contains("KEY_BIOME"), "BiomeGUI must tag items via PDC");

        String listener = Files.readString(JAVA.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("BiomeHolder"), "GUIListener must route BiomeHolder clicks");

        String manager = Files.readString(JAVA.resolve("gui/GUIManager.java"));
        assertTrue(manager.contains("biome()"), "GUIManager must expose biome()");
    }

    @Test
    void applyPathIsRegionScheduledAndBatched() throws Exception {
        String svc = Files.readString(JAVA.resolve("biomes/BiomeService.java"));
        assertTrue(svc.contains("runAt("), "Biome apply must run on the owning region thread");
        assertTrue(svc.contains("runGlobalLater"), "Biome apply must spread work over ticks");
        assertTrue(svc.contains("setCustomBiome"), "Biome apply must persist plot.customBiome");
        assertTrue(svc.contains("world.setBiome"), "Biome apply must call World#setBiome");
        // No direct Bukkit scheduler use anywhere in the apply path.
        org.junit.jupiter.api.Assertions.assertFalse(
                svc.contains("Bukkit.getScheduler()"),
                "BiomeService must not use Bukkit.getScheduler() directly");
    }

    @Test
    void langKeysExistEverywhere() throws Exception {
        Yaml yaml = new Yaml();
        String[] guiKeys = {"biome_gui_title", "biome_effect_line", "biome_click_apply",
                "biome_relog_hint", "biome_name_plains", "biome_name_cherry_grove"};
        String[] sysKeys = {"biome_not_in_plot", "biome_applying", "biome_applied"};
        for (String lang : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> guis;
            try (var in = Files.newInputStream(RES.resolve("lang/" + lang + "/guis.yml"))) {
                guis = yaml.load(in);
            }
            for (String k : guiKeys) {
                assertTrue(guis.containsKey(k), lang + "/guis.yml missing " + k);
            }
            Map<String, Object> sys;
            try (var in = Files.newInputStream(RES.resolve("lang/" + lang + "/system.yml"))) {
                sys = yaml.load(in);
            }
            for (String k : sysKeys) {
                assertTrue(sys.containsKey(k), lang + "/system.yml missing " + k);
            }
        }
    }
}
