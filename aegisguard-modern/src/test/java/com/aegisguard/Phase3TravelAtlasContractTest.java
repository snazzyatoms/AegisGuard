package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for 1.4 Phase 3: Travel Atlas GUI consolidation and traveler override. */
class Phase3TravelAtlasContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsTravelerOverrideDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1309, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> beacons = (Map<String, Object>) config.get("teleport_beacons");
        assertEquals(Boolean.TRUE, beacons.get("allow_traveler_override"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308")
                || migration.contains("CURRENT_SCHEMA = 1309"));
    }

    @Test
    void visitGuiOwnsAtlasTabsAndBeaconManagerDelegates() throws Exception {
        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("enum AtlasTab"));
        assertTrue(visit.contains("MY_BEACONS"));
        assertTrue(visit.contains("ARRIVAL"));
        assertTrue(visit.contains("openAtlas"));
        assertTrue(visit.contains("atlas_tab_destinations"));
        assertTrue(visit.contains("atlas_arrival_cue_beacon"));
        assertTrue(visit.contains("requiresBeaconArrival(player, plot)"));
        String beaconGui = Files.readString(JAVA.resolve("beacon/BeaconGUI.java"));
        assertTrue(beaconGui.contains("openAtlas(player, com.aegisguard.gui.VisitGUI.AtlasTab.MY_BEACONS)"));
        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("visit().open(player, 0, VisitGUI.VisitMode.WARPS)"));
        assertFalse(player.contains("beacons().openManager"));
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("case \"beacon\""));
        assertTrue(command.contains("openManager"));
    }

    @Test
    void travelerPreferencePersistsWithPlotPermit() throws Exception {
        String settings = Files.readString(JAVA.resolve("notify/PlayerNotificationSettings.java"));
        assertTrue(settings.contains("enum ArrivalPreference"));
        assertTrue(settings.contains("preferred_arrival"));
        assertTrue(Files.readString(JAVA.resolve("notify/NotificationManager.java"))
                .contains("cyclePreferredArrival"));
        String plot = Files.readString(JAVA.resolve("data/Plot.java"));
        assertTrue(plot.contains("setAllowTravelerOverride"));
        String service = Files.readString(JAVA.resolve("beacon/BeaconService.java"));
        assertTrue(service.contains("resolveBeaconArrival"));
        assertTrue(service.contains("allow_traveler_override"));
    }
}
