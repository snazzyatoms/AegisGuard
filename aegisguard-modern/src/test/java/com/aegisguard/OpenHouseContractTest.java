package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertEquals(1311, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("gatherings"));
        Map<String, Object> gatherings = (Map<String, Object>) config.get("gatherings");
        assertEquals(Boolean.TRUE, gatherings.get("enabled"));
        assertEquals(30, ((Number) gatherings.get("default_minutes")).intValue());
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1311"));
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
        Set<String> guiKeys = Set.of("visit_filter_live", "button_gathering", "gathering_button_lore", "gathering_gui_title",
                "gathering_status_name", "gathering_status_plot", "gathering_status_state", "gathering_status_idle",
                "gathering_status_hint", "gathering_start_short", "gathering_start_default",
                "gathering_start_lore", "gathering_pass_on", "gathering_pass_off", "gathering_pass_lore",
                "gathering_stop_name", "gathering_stop_lore", "gathering_need_manage",
                "gathering_need_manage_lore", "gathering_title", "gathering_subtitle", "button_roles_undo",
                "roles_undo_button_lore", "button_roles_lock", "button_roles_unlock",
                "roles_lock_button_lore", "roles_unlock_button_lore", "roles_member_locked_line",
                "roles_undo_empty_lore", "roles_undo_preview_lore", "roles_member_guest_pass_line",
                "roles_member_online_line", "roles_member_offline_line", "roles_lock_status_locked",
                "roles_lock_status_unlocked", "roles_guest_pass_never", "roles_guest_pass_expiring");
        Set<String> systemKeys = Set.of("gathering_disabled", "gathering_usage", "gathering_bad_duration",
                "gathering_save_failed", "gathering_none", "gathering_started",
                "gathering_extended", "gathering_stopped", "gathering_guest_pass",
                "gathering_pass_enabled", "gathering_pass_disabled", "gathering_broadcast",
                "roles_member_locked", "roles_undo_empty", "roles_undo_ok", "roles_lock_failed",
                "roles_lock_ok", "roles_usage");
        try (Stream<Path> langs = Files.list(RESOURCES.resolve("lang"))) {
            var dirs = langs.filter(Files::isDirectory).toList();
            assertEquals(9, dirs.size());
            dirs.forEach(dir -> {
                try {
                    var gui = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dir.resolve("guis.yml").toFile());
                    var system = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dir.resolve("system.yml").toFile());
                    for (String key : guiKeys) assertTrue(gui.isSet(key), dir.getFileName() + " guis " + key);
                    for (String key : systemKeys) assertTrue(system.isSet(key), dir.getFileName() + " system " + key);
                    for (String key : guiKeys) {
                        assertEquals(placeholders(RESOURCES.resolve("lang/modern_english/guis.yml"), key),
                                placeholders(dir.resolve("guis.yml"), key), dir.getFileName() + " guis " + key);
                    }
                    for (String key : systemKeys) {
                        assertEquals(placeholders(RESOURCES.resolve("lang/modern_english/system.yml"), key),
                                placeholders(dir.resolve("system.yml"), key), dir.getFileName() + " system " + key);
                    }
                    if (!dir.getFileName().toString().equals("modern_english")) {
                        var englishGui = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                                RESOURCES.resolve("lang/modern_english/guis.yml").toFile());
                        var englishSystem = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                                RESOURCES.resolve("lang/modern_english/system.yml").toFile());
                        assertNotEquals(englishGui.getString("button_roles_undo"), gui.getString("button_roles_undo"),
                                dir.getFileName() + " roles GUI translation");
                        assertNotEquals(englishSystem.getString("gathering_started"), system.getString("gathering_started"),
                                dir.getFileName() + " gathering translation");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static Set<String> placeholders(Path file, String key) {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
        String text = yaml.isList(key) ? String.join(" ", yaml.getStringList(key)) : yaml.getString(key, "");
        var matcher = java.util.regex.Pattern.compile("\\{[A-Z_]+}").matcher(text);
        Set<String> found = new java.util.HashSet<>();
        while (matcher.find()) found.add(matcher.group());
        return found;
    }
}
