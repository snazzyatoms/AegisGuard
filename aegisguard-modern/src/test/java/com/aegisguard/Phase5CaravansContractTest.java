package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(1305, ((Number) config.get("config_schema")).intValue());
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
        assertTrue(migration.contains("CURRENT_SCHEMA = 1305"));
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
        assertTrue(visit.contains("CARAVANS"));
        assertTrue(visit.contains("atlas_tab_caravans"));
        assertTrue(visit.contains("attachAtlasChrome"));
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
}
