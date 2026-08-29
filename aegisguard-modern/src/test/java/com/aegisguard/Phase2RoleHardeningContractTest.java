package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for 1.4 Phase 2: restore-safe roles, member locks, audit undo. */
class Phase2RoleHardeningContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsProtectRolesDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1306, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> snapshots = (Map<String, Object>) config.get("snapshots");
        Map<String, Object> restore = (Map<String, Object>) snapshots.get("restore");
        assertEquals(Boolean.TRUE, restore.get("protect_roles"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1306"));
    }

    @Test
    void restoreMergesRolesUnlessOverwriteRequested() throws Exception {
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        assertTrue(manager.contains("restoreMembersAndRoles"));
        assertTrue(manager.contains("shouldOverwriteRoles"));
        assertTrue(manager.contains("protect_roles"));
        assertTrue(manager.contains("overwriteRoles"));
        String snapshotState = Files.readString(JAVA.resolve("snapshots/PlotSnapshotState.java"));
        assertTrue(snapshotState.contains("settings.locked_members"));
        assertTrue(snapshotState.contains("static final int SCHEMA = 3"));
    }

    @Test
    void plotLocksAndAntiTamperArePersisted() throws Exception {
        String plot = Files.readString(JAVA.resolve("data/Plot.java"));
        assertTrue(plot.contains("lockMember"));
        assertTrue(plot.contains("isMemberLocked"));
        assertTrue(plot.contains("undoLastRoleChange"));
        assertTrue(plot.contains("deserializeRoleFlags(String serialized, boolean replace)"));
        assertTrue(Files.readString(JAVA.resolve("data/YMLDataStore.java")).contains("locked-members"));
        assertTrue(Files.readString(JAVA.resolve("data/SQLDataStore.java")).contains("lockedMembers"));
    }

    @Test
    void rolesCommandWiresLockUnlockUndoAndAudit() throws Exception {
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("handleRoles"));
        assertTrue(command.contains("case \"roles\""));
        assertTrue(command.contains("AuditCategory.ROLE_CHANGE"));
        String category = Files.readString(JAVA.resolve("audit/AuditCategory.java"));
        assertTrue(category.contains("ROLE_CHANGE"));
        String gui = Files.readString(JAVA.resolve("audit/AuditAdminGUI.java"));
        assertTrue(gui.contains("case ROLE_CHANGE"));
    }
}
