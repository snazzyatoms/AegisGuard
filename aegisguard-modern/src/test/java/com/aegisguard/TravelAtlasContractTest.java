package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract coverage for the 1.4 Travel Atlas arrival choice and the beacon anti-exploit /
 * anti-duplicate hardening. Mirrors the repository's source-and-config scanning style so the
 * behavioural wiring cannot silently regress.
 */
class TravelAtlasContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsArrivalChoiceAndCooldownDefaults() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        Map<String, Object> beacons = (Map<String, Object>) config.get("teleport_beacons");
        // Server override defaults OFF so the per-plot owner choice is respected.
        assertEquals(Boolean.FALSE, beacons.get("force_public_arrival"));
        // Stand-confirm delay raised from the old 2.5s.
        assertEquals(7, ((Number) beacons.get("prompt_cooldown_seconds")).intValue());
        assertTrue(((Number) beacons.get("create_cooldown_seconds")).intValue() > 0);
    }

    @Test
    void pluginAndPomsAreOnThe14Line() throws Exception {
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("version: 1.4.0"), "plugin.yml must declare 1.4.0");
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<version>1.4.0</version>"), "module pom must be 1.4.0");
    }

    @Test
    void plotPersistsArrivalModeEverywhere() throws Exception {
        String plot = Files.readString(JAVA.resolve("data/Plot.java"));
        assertTrue(plot.contains("enum ArrivalMode"));
        assertTrue(plot.contains("CLASSIC"));
        assertTrue(plot.contains("getArrivalMode"));
        assertTrue(plot.contains("requiresBeaconArrival"));

        // Persisted across all three stores + versioned snapshot map.
        assertTrue(Files.readString(JAVA.resolve("data/YMLDataStore.java")).contains("arrival-mode"));
        assertTrue(Files.readString(JAVA.resolve("data/SQLDataStore.java")).contains("arrivalMode"));
        String snapshot = Files.readString(JAVA.resolve("snapshots/PlotSnapshotState.java"));
        assertTrue(snapshot.contains("settings.arrival_mode"));
    }

    @Test
    void listingsRouteByPerPlotArrivalModeAndFailClosed() throws Exception {
        for (String gui : new String[]{"gui/VisitGUI.java", "gui/PlotMarketGUI.java", "gui/PlotAuctionGUI.java"}) {
            String src = Files.readString(JAVA.resolve(gui));
            assertTrue(src.contains("requiresBeaconArrival"), gui + " must gate on the plot arrival mode");
            assertTrue(src.contains("handlePublicListingTravel"), gui + " keeps the fail-closed beacon path");
        }
        // Fail closed: no silent fallback when a beacon plot lacks a public pad.
        String service = Files.readString(JAVA.resolve("beacon/BeaconService.java"));
        assertTrue(service.contains("beacon_no_public_arrival"));
        assertTrue(service.contains("requiresBeaconArrival"));
        assertTrue(service.contains("force_public_arrival"));
    }

    @Test
    void beaconsAreDedupedOnePerBlockAndOneLinkPerUuid() throws Exception {
        String service = Files.readString(JAVA.resolve("beacon/BeaconService.java"));
        // create() must consult getAt(block) first so a bound block never gets a second UUID.
        assertTrue(service.contains("getAt(block.getLocation())"));
        assertTrue(service.contains("duplicateBlockBeacons"));
        String store = Files.readString(JAVA.resolve("beacon/BeaconStore.java"));
        assertTrue(store.contains("dedupeByBlock"));
        assertTrue(store.contains("duplicateBlockBeacons"));
        // Listener still edits an existing pad instead of inserting a clone.
        String listener = Files.readString(JAVA.resolve("beacon/BeaconListener.java"));
        assertTrue(listener.contains("service.getAt(block.getLocation())"));
        assertTrue(listener.contains("openEdit"));
    }

    @Test
    void linkWizardEnforcesSocialAndInboundRules() throws Exception {
        String service = Files.readString(JAVA.resolve("beacon/BeaconService.java"));
        assertTrue(service.contains("canLinkTo"));
        assertTrue(service.contains("never A->A") || service.contains("dest.getId().equals(origin.getId())"));
        assertTrue(service.contains("isPublicAccess"));
        assertTrue(service.contains("allowsAllianceEntry"));
        String gui = Files.readString(JAVA.resolve("beacon/BeaconGUI.java"));
        assertTrue(gui.contains("canLinkTo"), "link picker must apply canLinkTo");
        assertTrue(gui.contains("beacon_denied"), "denied social links get a clear message");
    }

    @Test
    void standPromptIsConfigurableAndNeverStacksConfirms() throws Exception {
        String service = Files.readString(JAVA.resolve("beacon/BeaconService.java"));
        assertTrue(service.contains("prompt_cooldown_seconds"));
        assertFalse(service.contains("< 2500L"), "the old hard-coded 2.5s prompt delay must be gone");
        assertTrue(service.contains("sparkleForArrival"));
        String listener = Files.readString(JAVA.resolve("beacon/BeaconListener.java"));
        assertTrue(listener.contains("hasBeaconConfirmOpen"), "never open a second confirm");
        assertTrue(listener.contains("sparkleForArrival"), "sparkle while lingering before confirm");
    }

    @Test
    void arrivalCommandIsWiredForOwners() throws Exception {
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("case \"arrival\""));
        assertTrue(command.contains("handleArrival"));
        assertTrue(command.contains("setArrivalMode"));
    }
}
