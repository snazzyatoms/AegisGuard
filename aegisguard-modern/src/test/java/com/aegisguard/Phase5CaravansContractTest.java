package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for 1.4 Phase 5: Caravans & Trade Routes. */
class Phase5CaravansContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsCaravanDefaults() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1315, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("caravans"));
        Map<String, Object> caravans = (Map<String, Object>) config.get("caravans");
        assertEquals(Boolean.TRUE, caravans.get("enabled"));
        assertEquals(3, ((Number) caravans.get("max_active_per_player")).intValue());
        assertEquals(Boolean.TRUE, caravans.get("insurance_enabled"));
        assertEquals(Boolean.TRUE, caravans.get("require_vault"));
        Map<String, Object> events = (Map<String, Object>) caravans.get("events");
        assertEquals(15, ((Number) events.get("ambush_weight")).intValue());
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308")
                || migration.contains("CURRENT_SCHEMA = 1315")
                || migration.contains("CURRENT_SCHEMA = 1315")
                || migration.contains("CURRENT_SCHEMA = 1315"));
        String modulesSrc = Files.readString(JAVA.resolve("config/Modules.java"));
        assertTrue(modulesSrc.contains("CARAVANS"));
        assertTrue(modulesSrc.contains("case \"caravan\""));
    }

    @Test
    void caravanPackageAndAtlasTabAreWired() throws Exception {
        assertTrue(Files.exists(JAVA.resolve("caravans/Caravan.java")));
        assertTrue(Files.exists(JAVA.resolve("caravans/TradeRoute.java")));
        assertTrue(Files.exists(JAVA.resolve("caravans/CaravanRules.java")));
        assertTrue(Files.exists(JAVA.resolve("caravans/CaravanStore.java")));
        assertTrue(Files.exists(JAVA.resolve("caravans/CaravanService.java")));
        assertTrue(Files.exists(JAVA.resolve("caravans/CaravanListener.java")));
        assertTrue(Files.exists(JAVA.resolve("caravans/CaravanGUI.java")));
        String store = Files.readString(JAVA.resolve("caravans/CaravanStore.java"));
        assertTrue(store.contains("caravans.yml"));
        assertTrue(store.contains("aegis_caravans"));
        assertTrue(store.contains("borrowCaravanConnection"));
        String service = Files.readString(JAVA.resolve("caravans/CaravanService.java"));
        assertTrue(service.contains("resumeOverdue"));
        assertTrue(service.contains("AuditCategory.CARAVAN"));
        assertTrue(service.contains("charge"));
        assertTrue(service.contains("runMain(owner"),
                "Caravan arrival notices must hop to the owner's entity scheduler");
        assertTrue(service.contains("scheduler().runAt(loc, fx)"),
                "Caravan arrival particles must run on the destination region");
        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertFalse(visit.contains("CARAVANS"), "caravans no longer live inside the atlas");
        String caravanGui = Files.readString(JAVA.resolve("caravans/CaravanGUI.java"));
        assertTrue(caravanGui.contains("HubOriginHolder"),
                "caravan menu is a standalone hub-aware screen");
        assertTrue(caravanGui.contains("caravan_menu_title"));
        assertTrue(caravanGui.contains("hub_return"));
        String hub = Files.readString(JAVA.resolve("gui/TravelHubGUI.java"));
        assertTrue(hub.contains("caravans().open(player)"),
                "hub must expose the caravan menu");
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("handleCaravan"));
        assertTrue(command.contains("case \"caravan\""));
        String plugin = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("CaravanService"));
        assertTrue(plugin.contains("CaravanListener"));
        assertTrue(plugin.contains("startCaravanTickTask"));
        String listener = Files.readString(JAVA.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("CaravanGUI.Holder"));
        String audit = Files.readString(JAVA.resolve("audit/AuditCategory.java"));
        assertTrue(audit.contains("CARAVAN"));
    }

    /**
     * Escorts must actually be reachable: the GUI carries an escort pick into
     * dispatch, the destination-owner double-dip guard is a live check (not
     * dead code), the escorts_enabled gate exists, and hired/paid notices are
     * localized chat keys.
     */
    @Test
    void escortPickerIsWiredEndToEnd() throws Exception {
        String gui = Files.readString(JAVA.resolve("caravans/CaravanGUI.java"));
        assertTrue(gui.contains("escortChoice"),
                "caravan GUI must remember the escort pick");
        assertTrue(gui.contains("cycle_escort"),
                "a toggle must cycle eligible online players");
        assertTrue(gui.contains("escortId"),
                "dispatch must receive the picked escort");
        assertTrue(gui.contains("Bukkit.getPlayer(pick)"),
                "stale escort picks must prune when the player goes offline");

        String service = Files.readString(JAVA.resolve("caravans/CaravanService.java"));
        assertTrue(service.contains("escort.equals(route.destPlotOwner())"),
                "destination owner must not double-dip toll + escort cut");
        assertFalse(service.contains("escort.equals(route.destPlotOwner()) && escort.equals(player.getUniqueId())"),
                "the double-dip guard must not be dead code (self-escort is already nulled)");
        assertTrue(service.contains("escortsEnabled()"),
                "dispatch must honor caravans.escorts_enabled");
        assertTrue(service.contains("caravan_escort_hired"),
                "the hired escort must be notified");
        assertTrue(service.contains("caravan_escort_paid"),
                "the escort payout must be notified");

        String lang = Files.readString(RESOURCES.resolve("lang/modern_english/system.yml"));
        for (String key : new String[]{
                "caravan_escort_hired", "caravan_escort_paid", "caravan_escort_offline"}) {
            assertTrue(lang.contains(key + ":"),
                    "modern_english must define " + key);
        }
        String guis = Files.readString(RESOURCES.resolve("lang/modern_english/guis.yml"));
        for (String key : new String[]{
                "caravan_escort_picked", "caravan_escort_none", "caravan_escort_lore"}) {
            assertTrue(guis.contains(key + ":"),
                    "modern_english guis must define " + key);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void escortsEnabledShipsInConfig() throws Exception {
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = new Yaml().load(in);
        }
        Map<String, Object> caravans = (Map<String, Object>) config.get("caravans");
        assertEquals(Boolean.TRUE, caravans.get("escorts_enabled"),
                "caravans.escorts_enabled must ship enabled by default");
    }

    @Test
    void vaultHookInvokesNonPublicProviders() throws Exception {
        // Economy providers registered as private/anonymous classes are
        // uninvocable unless the reflected Method is made accessible — every
        // has/charge/deposit would silently return false (found live on rig).
        String hook = Files.readString(JAVA.resolve("economy/VaultHook.java"));
        assertTrue(hook.contains("setAccessible(true)"),
                "VaultHook must setAccessible on reflected provider methods");
    }
}
