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
        assertEquals(1315, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> beacons = (Map<String, Object>) config.get("teleport_beacons");
        assertEquals(Boolean.TRUE, beacons.get("allow_traveler_override"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308")
                || migration.contains("CURRENT_SCHEMA = 1315")
                || migration.contains("CURRENT_SCHEMA = 1315")
                || migration.contains("CURRENT_SCHEMA = 1315"));
    }

    @Test
    void atlasIsDestinationsOnlyAndBeaconManagerOwnsThePadList() throws Exception {
        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertFalse(visit.contains("AtlasTab"), "atlas top-tabs removed — atlas is destinations-only");
        assertFalse(visit.contains("MY_BEACONS"), "beacons live in their own menu now");
        assertFalse(visit.contains("openAtlas"));
        assertFalse(visit.contains("buildBeaconsTab"));
        assertFalse(visit.contains("buildArrivalTab"));
        assertTrue(visit.contains("atlas_arrival_cue_beacon"), "destination lore keeps the arrival cue");
        assertTrue(visit.contains("requiresBeaconArrival(player, plot)"),
                "beacon-arrival plots still route landings through public pads");
        String beaconGui = Files.readString(JAVA.resolve("beacon/BeaconGUI.java"));
        assertTrue(beaconGui.contains("class ListHolder"), "beacon pad list lives in BeaconGUI");
        assertTrue(beaconGui.contains("class ArrivalHolder"), "arrival rules moved into the beacon menu");
        assertTrue(beaconGui.contains("openArrival"));
        assertTrue(beaconGui.contains("arrival_beacon"), "arrival tab actions ported to BeaconGUI");
        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("travelHub().open(player)"),
                "the travel shortcut now routes through the unified hub");
        assertFalse(player.contains("beacons().openManager"));
        String hub = Files.readString(JAVA.resolve("gui/TravelHubGUI.java"));
        assertTrue(hub.contains("visit().open(player, 0, VisitGUI.VisitMode.DISCOVER)")
                || hub.contains("visit().open(player, 0, false)"),
                "the hub must reach the atlas");
        assertTrue(hub.contains("caravans().open(player)"), "hub exposes the caravan menu");
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
