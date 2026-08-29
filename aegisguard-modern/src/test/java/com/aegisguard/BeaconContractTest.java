package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path LANG = Path.of("src/main/resources/lang");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsTeleportBeaconsOnByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1307, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("teleport_beacons"));
        Map<String, Object> section = (Map<String, Object>) config.get("teleport_beacons");
        assertEquals(Boolean.TRUE, section.get("enabled"));
        assertEquals(3, ((Number) section.get("max_per_plot")).intValue());
        assertEquals(Boolean.TRUE, section.get("give_starter_pads"));
        assertEquals("LODESTONE", section.get("starter_pad_material"));
        Map<String, Object> charges = (Map<String, Object>) section.get("charges");
        assertEquals("owner_choice", charges.get("mode"));
        assertEquals(Boolean.TRUE, charges.get("pay_plot_owner"));
        assertEquals(Boolean.TRUE, charges.get("allow_vault"));
    }

    @Test
    void beaconsAreWiredAndNeverUseRawPlayerTeleport() throws Exception {
        String plugin = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("new com.aegisguard.beacon.BeaconService(this)"));
        assertTrue(plugin.contains("BeaconListener"));
        assertTrue(plugin.contains("beaconService.load()"));

        String service = Files.readString(JAVA.resolve("beacon/BeaconService.java"));
        assertFalse(service.contains("player.teleport("));
        assertTrue(service.contains("SafeTravelService.Kind.BEACON"));
        assertTrue(service.contains("canEnterPlot"));
        assertTrue(service.contains("handlePublicListingTravel"));
        assertTrue(service.contains("giveStarterPads"));
        assertTrue(service.contains("teleportFuture()"));
        assertTrue(service.contains("destinationReady"));
        assertTrue(service.contains("recentlyTraveled"));
        assertTrue(service.contains("charges()"));
        assertTrue(service.contains("reassignPlot"));
        assertTrue(service.contains("tripLocks"));
        assertTrue(service.contains("clearPlayerState"));

        String beacon = Files.readString(JAVA.resolve("beacon/TeleportBeacon.java"));
        assertTrue(beacon.contains("owners = true"));
        assertTrue(beacon.contains("publicAccess"));
        assertTrue(beacon.contains("linkedBeaconId"));
    }

    @Test
    void listingsRefuseWithoutPublicBeaconWhenModuleOn() throws Exception {
        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("handlePublicListingTravel"));
        assertTrue(visit.contains("safeTravel()"));
        String auction = Files.readString(JAVA.resolve("gui/PlotAuctionGUI.java"));
        assertTrue(auction.contains("handlePublicListingTravel"));
        assertTrue(auction.contains("safeTravel()"));
        String market = Files.readString(JAVA.resolve("gui/PlotMarketGUI.java"));
        assertTrue(market.contains("handlePublicListingTravel"));
        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("visit().open") || player.contains("openAtlas"));
        String visitGui = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertTrue(visitGui.contains("AtlasTab"));
        assertTrue(visitGui.contains("openAtlas"));
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("case \"beacon\""));
        assertTrue(command.contains("giveStarterPads"));
        String modules = Files.readString(JAVA.resolve("config/Modules.java"));
        assertTrue(modules.contains("TELEPORT_BEACONS"));
        String travel = Files.readString(JAVA.resolve("travel/SafeTravelService.java"));
        assertTrue(travel.contains("Kind.BEACON"));
        assertTrue(travel.contains("kind != Kind.BEACON"));
    }

    @Test
    void languagePacksIncludeBeaconKeys() throws Exception {
        List<String> keys = List.of(
                "beacon_no_public_arrival:",
                "beacon_not_linked:",
                "beacon_created:",
                "beacon_arrived:",
                "beacon_pad_gone:",
                "beacon_travel_failed:",
                "beacon_paid_owner:"
        );
        for (String pack : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            String system = Files.readString(LANG.resolve(pack).resolve("system.yml"));
            for (String key : keys) {
                assertTrue(system.contains(key), pack + " missing " + key);
            }
            String guis = Files.readString(LANG.resolve(pack).resolve("guis.yml"));
            assertTrue(guis.contains("beacon_manager_title:"), pack + " missing beacon GUI keys");
            assertTrue(guis.contains("button_beacons:"), pack + " missing beacon menu button");
            assertTrue(guis.contains("beacon_need_plot_lore:"), pack + " missing beacon_need_plot_lore");
            assertTrue(guis.contains("{NAME}"), pack + " confirm/go should include {NAME}");
            assertTrue(guis.contains("{PURPOSE}"), pack + " purpose button should include {PURPOSE}");
            assertTrue(guis.contains("beacon_toggle_owners:"), pack + " missing toggle keys");
            assertTrue(guis.contains("beacon_charges_off:"), pack + " missing charge policy GUI keys");
            assertTrue(guis.contains("beacon_confirm_fee:"), pack + " missing confirm fee key");
            assertTrue(guis.contains("beacon_item_name:"), pack + " missing pad item name");
            assertTrue(guis.contains("beacon_cooldown:"), pack + " missing cooldown key");
            assertTrue(guis.contains("beacon_preset_click:"), pack + " missing preset click key");
            assertTrue(guis.contains("{SECONDS}"), pack + " cooldown should include {SECONDS}");
        }
    }

    @Test
    void listenerProtectsTravelAndCleansDestroyedPads() throws Exception {
        String listener = Files.readString(JAVA.resolve("beacon/BeaconListener.java"));
        assertTrue(listener.contains("PlotDeleteEvent"));
        assertTrue(listener.contains("EntityExplodeEvent"));
        assertTrue(listener.contains("BlockPistonExtendEvent"));
        assertTrue(listener.contains("hasBlockingInventory"));
        assertTrue(listener.contains("recentlyTraveled"));
        assertFalse(listener.contains("player.teleport("));
    }

    @Test
    void linkGuiAllowsManagedPadsOnOtherPlots() throws Exception {
        String gui = Files.readString(JAVA.resolve("beacon/BeaconGUI.java"));
        assertTrue(gui.contains("linkablePads"));
        assertTrue(gui.contains("canManage(player, candidate)"));
        assertTrue(gui.contains("{NAME}"));
        assertTrue(gui.contains("canEditVaultFees"));
        assertTrue(gui.contains("confirmLore"));
        String charges = Files.readString(JAVA.resolve("beacon/BeaconCharges.java"));
        assertTrue(charges.contains("OWNER_CHOICE"));
        assertTrue(charges.contains("pay_plot_owner"));
        assertTrue(charges.contains("always_vault_cost"));
        assertTrue(gui.contains("player.closeInventory()"));
        String merge = Files.readString(JAVA.resolve("gui/ClaimMergeGUI.java"));
        assertTrue(merge.contains("reassignPlot"));
        String store = Files.readString(JAVA.resolve("beacon/BeaconStore.java"));
        assertTrue(store.contains("dirty = true"));
    }
}
