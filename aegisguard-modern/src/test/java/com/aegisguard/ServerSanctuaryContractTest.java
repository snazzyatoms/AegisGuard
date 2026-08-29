package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server-plot Keep Health / Keep Hunger sanctuary (schema 1306). */
class ServerSanctuaryContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsKeepVitalsOffByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1308, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> protections = (Map<String, Object>) config.get("protections");
        assertEquals(Boolean.FALSE, protections.get("keep_health"));
        assertEquals(Boolean.FALSE, protections.get("keep_hunger"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308"));
    }

    @Test
    void sanctuaryIsServerPlotOnlyAndFoliaEventBased() throws Exception {
        String protection = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(protection.contains("keepsHealth("));
        assertTrue(protection.contains("keepsHunger("));
        assertTrue(protection.contains("onSanctuaryDamage"));
        assertTrue(protection.contains("onSanctuaryFood"));
        assertTrue(protection.contains("plot.isServerZone() && plot.getFlag(\"keep_health\""));
        assertTrue(protection.contains("plot.isServerZone() && plot.getFlag(\"keep_hunger\""));
        assertTrue(protection.contains("FoodLevelChangeEvent"));
        assertFalse(protection.contains("runGlobalRepeating") && protection.contains("keepsHealth"),
                "Sanctuary must not poll on the global scheduler");
        assertFalse(protection.contains("case \"keep_health\""),
                "keep_health must not inherit server-zone default-true protection");
        assertFalse(protection.contains("case \"keep_hunger\""),
                "keep_hunger must not inherit server-zone default-true protection");

        String exhaustion = Files.readString(JAVA.resolve("protection/SanctuaryExhaustionListener.java"));
        assertTrue(exhaustion.contains("onSanctuaryExhaustion"));
        assertTrue(exhaustion.contains("EntityExhaustionEvent"));
        assertTrue(exhaustion.contains("keepsHunger("));

        String pluginMain = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(pluginMain.contains("registerSanctuaryExhaustionListener"));
        assertTrue(pluginMain.contains("org.bukkit.event.entity.EntityExhaustionEvent"));

        String presets = Files.readString(JAVA.resolve("protection/ProtectionPreset.java"));
        assertFalse(presets.contains("keep_health"));
        assertFalse(presets.contains("keep_hunger"));

        String flags = Files.readString(JAVA.resolve("gui/PlotFlagsGUI.java"));
        assertTrue(flags.contains("keep_health"));
        assertTrue(flags.contains("keep_hunger"));
        assertTrue(flags.contains("case 19 ->"));
        assertTrue(flags.contains("case 20 ->"));
        assertTrue(flags.contains("plot.isServerZone()"));

        String selection = Files.readString(JAVA.resolve("selection/SelectionService.java"));
        assertTrue(selection.contains("keep_health"));
        assertTrue(selection.contains("keep_hunger"));
    }
}
