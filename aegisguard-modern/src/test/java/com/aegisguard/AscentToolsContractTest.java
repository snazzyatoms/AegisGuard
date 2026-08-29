package com.aegisguard;

import com.aegisguard.protection.ProtectionPreset;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keep XP/Inventory sanctuary, claim presets, flight skill, and staff seasons. */
class AscentToolsContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsAscentDefaults() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1308, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> protections = (Map<String, Object>) config.get("protections");
        assertEquals(Boolean.FALSE, protections.get("keep_xp"));
        assertEquals(Boolean.FALSE, protections.get("keep_inventory"));
        Map<String, Object> claims = (Map<String, Object>) config.get("claims");
        assertEquals(Boolean.TRUE, claims.get("preset_chooser"));
        Map<String, Object> flight = (Map<String, Object>) config.get("flight_skill");
        assertEquals(Boolean.TRUE, flight.get("enabled"));
        assertEquals(Boolean.TRUE, flight.get("staff_always"));
        Map<String, Object> seasons = (Map<String, Object>) config.get("seasons");
        assertEquals(Boolean.TRUE, seasons.get("enabled"));
        assertEquals(5, ((Number) seasons.get("max_featured_plots")).intValue());
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1308"));
    }

    @Test
    void sanctuaryAddsXpAndInventoryWithoutHubFlight() throws Exception {
        String protection = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(protection.contains("keepsXp("));
        assertTrue(protection.contains("keepsInventory("));
        assertTrue(protection.contains("onSanctuaryDeath"));
        assertTrue(protection.contains("plot.isServerZone() && plot.getFlag(\"keep_xp\""));
        assertTrue(protection.contains("plot.isServerZone() && plot.getFlag(\"keep_inventory\""));
        assertFalse(protection.contains("keep_flight") || protection.contains("hub_flight"));

        String flags = Files.readString(JAVA.resolve("gui/PlotFlagsGUI.java"));
        assertTrue(flags.contains("keep_xp"));
        assertTrue(flags.contains("keep_inventory"));
        assertTrue(flags.contains("ProtectionPreset.SPAWN"));
        assertTrue(flags.contains("ProtectionPreset.HUB"));
        assertFalse(flags.contains("togglePaid(player, plot, \"fly\""));
    }

    @Test
    void presetsNeverWriteSanctuaryFlags() {
        for (ProtectionPreset preset : ProtectionPreset.values()) {
            assertFalse(preset.flagBundle().containsKey("keep_health"));
            assertFalse(preset.flagBundle().containsKey("keep_hunger"));
            assertFalse(preset.flagBundle().containsKey("keep_xp"));
            assertFalse(preset.flagBundle().containsKey("keep_inventory"));
            assertFalse(preset.flagBundle().containsKey("fly"));
            assertFalse(preset.flagBundle().containsKey("hearth"));
        }
        assertTrue(ProtectionPreset.forPlot(true).contains(ProtectionPreset.SPAWN));
        assertTrue(ProtectionPreset.forPlot(false).contains(ProtectionPreset.HOME));
        assertFalse(ProtectionPreset.forPlot(false).contains(ProtectionPreset.SPAWN));
    }

    @Test
    void flightSkillAndSeasonAreWired() throws Exception {
        assertTrue(Files.exists(JAVA.resolve("protection/FlightSkillService.java")));
        assertTrue(Files.exists(JAVA.resolve("season/SeasonService.java")));
        assertTrue(Files.exists(JAVA.resolve("season/SeasonAdminGUI.java")));
        String flight = Files.readString(JAVA.resolve("protection/FlightSkillService.java"));
        assertTrue(flight.contains("staff_always"));
        assertTrue(flight.contains("hasFlightReward"));
        String pluginMain = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(pluginMain.contains("FlightSkillService"));
        assertTrue(pluginMain.contains("SeasonService"));
        String admin = Files.readString(JAVA.resolve("admin/AdminCommand.java"));
        assertTrue(admin.contains("handleAdminSeason"));
        assertTrue(admin.contains("handleAdminSkill"));
        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("isFeaturedPlot"));
        String routes = Files.readString(JAVA.resolve("routes/RoutesGUI.java"));
        assertTrue(routes.contains("sortRoutes"));
    }
}
