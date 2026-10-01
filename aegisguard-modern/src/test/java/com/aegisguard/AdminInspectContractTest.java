package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the /agadmin inspect feature: dispatch + tab-complete + help wiring, the
 * AdminPlotInspectGUI holder routing, and full localization coverage.
 */
class AdminInspectContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void commandIsWired() throws Exception {
        String src = Files.readString(JAVA.resolve("admin/AdminCommand.java"));
        assertTrue(src.contains("\"inspect\""), "SUB_COMMANDS must list inspect");
        assertTrue(src.contains("case \"inspect\" -> handleAdminInspect"),
                "dispatch must route inspect to handleAdminInspect");
        assertTrue(src.contains("handleAdminInspect"), "handler must exist");
        assertTrue(src.contains("admin_help_inspect"), "help must mention inspect");
        // inspect <player> shares the plots owner-name tab completion.
        assertTrue(src.contains("args[0].equalsIgnoreCase(\"inspect\") && args.length == 2")
                        || src.contains("args[0].equalsIgnoreCase(\"plots\") || args[0].equalsIgnoreCase(\"inspect\")"),
                "inspect must tab-complete player names");
    }

    @Test
    void guiAndListenerAreWired() throws Exception {
        String gui = Files.readString(JAVA.resolve("gui/AdminPlotInspectGUI.java"));
        assertTrue(gui.contains("InspectHolder"), "AdminPlotInspectGUI must declare a holder");
        assertTrue(gui.contains("open(Player player, Plot plot)"), "open(player, plot) must exist");
        assertTrue(gui.contains("handleClick"), "click handler must exist");

        String listener = Files.readString(JAVA.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("InspectHolder"), "GUIListener must route InspectHolder");
        assertTrue(listener.contains("adminInspect()"), "GUIListener must call adminInspect()");

        String manager = Files.readString(JAVA.resolve("gui/GUIManager.java"));
        assertTrue(manager.contains("adminInspect()"), "GUIManager must expose adminInspect()");
    }

    @Test
    void inspectActionsReuseRegistry() throws Exception {
        String gui = Files.readString(JAVA.resolve("gui/AdminPlotInspectGUI.java"));
        assertTrue(gui.contains("SafeTravelService.Kind.STAFF"),
                "teleport must go through the staff safe-travel path");
        assertTrue(gui.contains("plotList().openFor"),
                "registry action must reuse the owner-filtered plot list");
        assertTrue(gui.contains("GuiClicks.destructive"),
                "delete must require the destructive click pattern");
    }

    @Test
    void langKeysExistEverywhere() throws Exception {
        Yaml yaml = new Yaml();
        String[] guiKeys = {"admin_inspect_title", "admin_inspect_owner", "admin_inspect_location",
                "admin_inspect_location_lore", "admin_inspect_identity", "admin_inspect_identity_lore",
                "admin_inspect_status", "admin_inspect_status_lore", "admin_inspect_teleport",
                "admin_inspect_teleport_lore", "admin_inspect_registry", "admin_inspect_registry_lore",
                "admin_inspect_delete", "admin_inspect_delete_lore", "admin_inspect_zone_server",
                "admin_inspect_zone_player", "admin_inspect_lockdown_on", "admin_inspect_lockdown_off",
                "admin_inspect_none"};
        for (String lang : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            Map<String, Object> guis;
            try (var in = Files.newInputStream(RES.resolve("lang/" + lang + "/guis.yml"))) {
                guis = yaml.load(in);
            }
            for (String k : guiKeys) {
                assertTrue(guis.containsKey(k), lang + "/guis.yml missing " + k);
            }
            Map<String, Object> sys;
            try (var in = Files.newInputStream(RES.resolve("lang/" + lang + "/system.yml"))) {
                sys = yaml.load(in);
            }
            assertTrue(sys.containsKey("admin_help_inspect"), lang + "/system.yml missing admin_help_inspect");
            assertTrue(sys.containsKey("admin_inspect_none"), lang + "/system.yml missing admin_inspect_none");
        }
    }
}
