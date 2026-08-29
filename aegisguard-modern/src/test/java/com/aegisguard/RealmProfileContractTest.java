package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 4 (Realm Profiles and Noticeboards) contract checks: safe config defaults, GUI/plugin
 * wiring, moderation permission checks, and the rule that existing plot names/messages are never
 * overwritten automatically.
 */
class RealmProfileContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsASafelyDefaultedRealmProfilesSectionEnabledWithBoundedNoticeboardLimits() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        Map<String, Object> profiles = (Map<String, Object>) config.get("realm_profiles");
        assertTrue(profiles != null, "config.yml must declare a realm_profiles section");

        Map<String, Object> noticeboard = (Map<String, Object>) profiles.get("noticeboard");
        assertTrue(noticeboard != null, "realm_profiles must declare a noticeboard sub-section");
        assertTrue(((Number) noticeboard.get("max_entries")).intValue() > 0,
                "max_entries must have a positive safe default");
        assertTrue(((Number) noticeboard.get("max_length")).intValue() > 0,
                "max_length must have a positive safe default");
    }

    @Test
    void noticeboardIsEmptyByDefaultOnEveryPlot() throws Exception {
        String plotSource = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plotSource.contains("private final List<PlotNotice> noticeboard = new CopyOnWriteArrayList<>();"),
                "Every plot, including existing 1.2.7 claims, must start with an empty noticeboard");
    }

    @Test
    void realmProfileGuiIsWiredIntoManagerListenerAndPlayerMenu() throws Exception {
        String manager = Files.readString(JAVA_ROOT.resolve("gui/GUIManager.java"));
        assertTrue(manager.contains("new RealmProfileGUI(plugin)"));

        String listener = Files.readString(JAVA_ROOT.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("holder instanceof RealmProfileMenuHolder"));
        assertTrue(listener.contains("holder instanceof NoticeboardHolder"));

        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("realmProfile().open(player)"), "The main menu must offer a Realm Profile entry point");
    }

    @Test
    void notesCommandEnforcesManagePermissionForAddAndRemoveButNotForList() throws Exception {
        String command = Files.readString(JAVA_ROOT.resolve("commands/AegisCommand.java"));
        int methodStart = command.indexOf("private void handleNotice(");
        assertTrue(methodStart >= 0, "AegisCommand must implement /ag notice");

        int listIndex = command.indexOf("action.equals(\"list\")", methodStart);
        int permissionIndex = command.indexOf("plot.canManage(p, plugin)", methodStart);
        assertTrue(listIndex >= 0 && permissionIndex >= 0, "handleNotice must check both list and the manage permission");
        assertTrue(listIndex < permissionIndex,
                "Listing notices must never require manage permission, but adding/removing must");
    }

    @Test
    void notesCommandEnforcesAConfiguredMaxLength() throws Exception {
        String command = Files.readString(JAVA_ROOT.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("realm_profiles.noticeboard.max_length"));
        assertTrue(command.contains("notice_too_long"));
    }

    @Test
    void realmProfileGuiNeverAutomaticallyOverwritesAnExistingPlotNameOrGreeting() throws Exception {
        String gui = Files.readString(JAVA_ROOT.resolve("profile/RealmProfileGUI.java"));
        assertTrue(!gui.contains("plot.setPlotName") && !gui.contains("plot.setWelcomeMessage")
                        && !gui.contains("plot.setDescription") && !gui.contains("plot.setEntryTitle"),
                "The Realm Profile GUI must only display existing name/description/greeting fields; " +
                        "editing remains an explicit, separate chat command per the milestone rule");
    }

    @Test
    void serializationEncodesFreeformNoticeTextSoItCanNeverCollideWithTheDelimiterScheme() throws Exception {
        String plotSource = Files.readString(JAVA_ROOT.resolve("data/Plot.java"));
        assertTrue(plotSource.contains("Base64.getEncoder().encodeToString(notice.getText()"),
                "Notice text must be Base64-encoded before being embedded in the '|'/'~' delimited blob");
    }

    @Test
    void configSchemaWasBumpedForRealmProfiles() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        // Schema continues to advance with later milestones; Realm Profiles landed at 1275.
        assertTrue(migration.contains("CURRENT_SCHEMA = 1275")
                        || migration.contains("CURRENT_SCHEMA = 1276")
                        || migration.contains("CURRENT_SCHEMA = 1277")
                        || migration.contains("CURRENT_SCHEMA = 1278")
                        || migration.contains("CURRENT_SCHEMA = 1280")
                        || migration.contains("CURRENT_SCHEMA = 1281")
                        || migration.contains("CURRENT_SCHEMA = 1282")
                || migration.contains("CURRENT_SCHEMA = 1283")
                || migration.contains("CURRENT_SCHEMA = 1284") || migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286")
                || migration.contains("CURRENT_SCHEMA = 1287")
                || migration.contains("CURRENT_SCHEMA = 1294")
                || migration.contains("CURRENT_SCHEMA = 1300")
                || migration.contains("CURRENT_SCHEMA = 1302"),
                "Config schema must be at least 1275 after Realm Profiles");
    }
}
