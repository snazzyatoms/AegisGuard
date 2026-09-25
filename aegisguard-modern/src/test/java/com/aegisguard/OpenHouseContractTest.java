package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract coverage for Open House gatherings and Roles GUI lock/undo. */
class OpenHouseContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsGatheringsModule() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1312, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("gatherings"));
        Map<String, Object> gatherings = (Map<String, Object>) config.get("gatherings");
        assertEquals(Boolean.TRUE, gatherings.get("enabled"));
        assertEquals(30, ((Number) gatherings.get("default_minutes")).intValue());
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1312"));
        assertTrue(Files.readString(JAVA.resolve("config/Modules.java")).contains("GATHERINGS"));
    }

    @Test
    void servicePersistsAndExpiresVisitorPasses() throws Exception {
        String service = Files.readString(JAVA.resolve("gatherings/GatheringService.java"));
        assertTrue(service.contains("StartResult"));
        assertTrue(service.contains("welcomeVisitor"));
        assertTrue(service.contains("GuestPassPreset.VISITOR"));
        assertTrue(service.contains("revokeGuestPass"));
        assertTrue(Files.readString(JAVA.resolve("gatherings/GatheringStore.java")).contains("gatherings.yml"));
        assertTrue(Files.readString(JAVA.resolve("AegisGuard.java")).contains("startGatheringTickTask"));
        assertTrue(Files.readString(JAVA.resolve("AegisGuard.java")).contains("gatheringService.save()"));
    }

    @Test
    void atlasAndHubExposeLiveOpenHouse() throws Exception {
        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("LIVE"));
        assertTrue(visit.contains("plugin.gatherings().isLive(plot)"));
        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("button_gathering"));
        assertTrue(player.contains("showGatherings"));
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("handleGathering"));
        assertTrue(command.contains("\"gathering\""));
        String gui = Files.readString(JAVA.resolve("gatherings/GatheringGUI.java"));
        assertTrue(gui.contains("button_back"));
        assertTrue(gui.contains("button_exit"));
    }

    @Test
    void rolesGuiWiresLockUnlockUndo() throws Exception {
        String roles = Files.readString(JAVA.resolve("gui/RolesGUI.java"));
        assertTrue(roles.contains("button_roles_lock"));
        assertTrue(roles.contains("button_roles_unlock"));
        assertTrue(roles.contains("button_roles_undo"));
        assertTrue(roles.contains("lockMember"));
        assertTrue(roles.contains("unlockMember"));
        assertTrue(roles.contains("undoLastRoleChange"));
        assertTrue(roles.contains("peekLastRoleChange"));
        assertTrue(roles.contains("MEMBERS_PER_PAGE = 43"));
        assertTrue(roles.contains("roles_member_guest_pass_line"));
        assertTrue(roles.contains("getActiveGuestPass"));
        assertTrue(roles.contains("isMemberLocked"));
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("aegis.gathering"));
    }

    @Test
    void languagePacksShipOpenHouseKeys() throws Exception {
        try (Stream<Path> langs = Files.list(RESOURCES.resolve("lang"))) {
            langs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    String guis = Files.readString(dir.resolve("guis.yml"));
                    String system = Files.readString(dir.resolve("system.yml"));
                    assertTrue(guis.contains("button_gathering:"), dir.getFileName() + " guis");
                    assertTrue(guis.contains("button_roles_lock:"), dir.getFileName() + " lock");
                    assertTrue(guis.contains("button_roles_undo:"), dir.getFileName() + " undo");
                    assertTrue(guis.contains("roles_undo_empty_lore:"), dir.getFileName() + " undo empty");
                    assertTrue(guis.contains("roles_member_guest_pass_line:"), dir.getFileName() + " guest pass");
                    assertTrue(system.contains("gathering_started:"), dir.getFileName() + " system");
                    assertTrue(system.contains("roles_undo_ok:"), dir.getFileName() + " undo ok");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
