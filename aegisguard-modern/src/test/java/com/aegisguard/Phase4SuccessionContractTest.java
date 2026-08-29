package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for 1.4 Phase 4: Guardian Succession and Co-Ownership. */
class Phase4SuccessionContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsSuccessionDefaults() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1309, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("succession"));
        Map<String, Object> succession = (Map<String, Object>) config.get("succession");
        assertEquals(Boolean.TRUE, succession.get("enabled"));
        assertEquals(30, ((Number) succession.get("inactivity_days")).intValue());
        assertEquals(3600, ((Number) succession.get("transfer_cooldown_seconds")).intValue());
        assertEquals(300, ((Number) succession.get("rollback_window_seconds")).intValue());
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308")
                || migration.contains("CURRENT_SCHEMA = 1309"));
    }

    @Test
    void coOwnerLocksHeirAndTransferRollbackAreWired() throws Exception {
        String plot = Files.readString(JAVA.resolve("data/Plot.java"));
        assertTrue(plot.contains("lockMember(playerUUID)"));
        assertTrue(plot.contains("\"co_owner\""));
        assertTrue(plot.contains("setHeir"));
        assertTrue(plot.contains("isCoOwnerOrSteward"));
        assertTrue(Files.readString(JAVA.resolve("data/YMLDataStore.java")).contains("heir"));
        assertTrue(Files.readString(JAVA.resolve("data/SQLDataStore.java")).contains("heir"));
        String service = Files.readString(JAVA.resolve("succession/SuccessionService.java"));
        assertTrue(service.contains("canAssume"));
        assertTrue(service.contains("rollback"));
        assertTrue(service.contains("OWNERSHIP_TRANSFER"));
        String transfer = Files.readString(JAVA.resolve("gui/TransferConfirmGUI.java"));
        assertTrue(transfer.contains("canTransferNow"));
        assertTrue(transfer.contains("recordTransfer"));
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("handleHeir"));
        assertTrue(command.contains("handleSuccession"));
        String gui = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(gui.contains("stewardship().open"));
        assertTrue(Files.readString(JAVA.resolve("gui/GUIListener.java")).contains("StewardshipGUI.Holder"));
    }
}
