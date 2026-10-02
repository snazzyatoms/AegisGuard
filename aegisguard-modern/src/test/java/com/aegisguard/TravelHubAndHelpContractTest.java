package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the travel-hub + categorized-help pass: the hub GUI must be registered end-to-end
 * (holder, dispatch, accessor, command, module gate), and help must be category-aware with
 * localized category strings in every pack.
 */
class TravelHubAndHelpContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");
    private static final List<String> LOCALES = List.of(
            "modern_english", "old_english", "spanish_ar", "spanish_mx",
            "portuguese_br", "french_fr", "german_de", "italian_it", "polish_pl");

    @Test
    void travelHubIsWiredEndToEnd() throws Exception {
        String gui = Files.readString(JAVA.resolve("gui/TravelHubGUI.java"));
        assertTrue(gui.contains("TravelHubHolder implements InventoryHolder"),
                "hub needs a typed holder");
        assertTrue(gui.contains("plugin.gui().visit().open")
                && gui.contains("plugin.gui().beacons().openManager")
                && gui.contains("plugin.gui().routes().open"),
                "hub must route to atlas, beacons, and routes");
        assertTrue(gui.contains("SafeTravelService.Kind.SPAWN"),
                "spawn quick-action must go through SafeTravel");

        String listener = Files.readString(JAVA.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("instanceof TravelHubHolder"),
                "listener must recognize + dispatch the hub holder");
        assertTrue(listener.contains("travelHub().handleClick"),
                "listener must route clicks to the hub");

        String mgr = Files.readString(JAVA.resolve("gui/GUIManager.java"));
        assertTrue(mgr.contains("travelHub()"), "GUIManager must expose the hub");
        assertTrue(mgr.contains("new TravelHubGUI(plugin)"), "hub must be constructed");

        String cmd = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(cmd.contains("case \"travel\""), "/ag travel must dispatch to the hub");
        assertTrue(cmd.contains("\"travel\""), "travel must be in SUB_COMMANDS for tab-complete");

        String modules = Files.readString(JAVA.resolve("config/Modules.java"));
        assertTrue(modules.contains("\"travel\" ->") || modules.contains(", \"travel\" ->"),
                "travel must be module-gated under TRAVEL");

        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("travelHub().open(player)"),
                "the main-menu travel shortcut must open the hub");
    }

    @Test
    void travelSubMenusOfferReturnToHub() throws Exception {
        String mgr = Files.readString(JAVA.resolve("gui/GUIManager.java"));
        assertTrue(mgr.contains("hubOriginActive"),
                "GUIManager must expose the hub-origin check sub-screens use");
        assertTrue(Files.exists(JAVA.resolve("gui/HubOriginHolder.java")),
                "HubOriginHolder interface must exist");

        String visit = Files.readString(JAVA.resolve("gui/VisitGUI.java"));
        assertTrue(visit.contains("VisitHolder implements HubOriginHolder"),
                "atlas holder must carry the hub-origin flag");
        assertTrue(visit.contains("hub_return"), "atlas needs a hub-return action");
        assertTrue(visit.contains("travelHub().open(player)"),
                "atlas must open the hub when returning");
        assertTrue(visit.contains("hubOriginActive(player)"),
                "atlas rebuilds must re-derive the flag");

        String beacons = Files.readString(JAVA.resolve("beacon/BeaconGUI.java"));
        assertTrue(beacons.contains("extends HubAwareHolder"),
                "beacon holders must carry the hub-origin flag");
        assertTrue(beacons.contains("travelHub().open(player)"),
                "beacon manager must return to the hub when flagged");

        String routes = Files.readString(JAVA.resolve("routes/RoutesGUI.java"));
        assertTrue(routes.contains("RoutesMenuHolder implements HubOriginHolder")
                && routes.contains("RouteDetailHolder implements HubOriginHolder"),
                "route holders must carry the hub-origin flag");
        assertTrue(routes.contains("travelHub().open(player)"),
                "route list must return to the hub when flagged");
    }

    @Test
    void helpIsCategorized() throws Exception {
        String src = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(src.contains("HELP_CATEGORIES"), "help must define categories");
        assertTrue(src.contains("sendHelp(p, args.length > 1 ? args[1] : null)"),
                "player /ag help <category> must pass the category through");
        assertTrue(src.contains("sendHelp(sender, args.length > 1 ? args[1] : null)"),
                "console help must accept a category too");
        assertTrue(src.contains("filterHelpLines"),
                "help lines must be filtered by category");
        assertTrue(src.contains("help_lines_extra"),
                "help must merge the extended command list");
        assertTrue(src.contains("copyPartialMatches(args[1], HELP_CATEGORY_ORDER"),
                "help categories must tab-complete");
    }

    @Test
    void allLocalesHaveTravelAndHelpStrings() throws Exception {
        for (String locale : LOCALES) {
            String guis = Files.readString(RES.resolve("lang/" + locale + "/guis.yml"));
            String system = Files.readString(RES.resolve("lang/" + locale + "/system.yml"));
            String codex = Files.readString(RES.resolve("codex/" + locale + ".yml"));

            for (String key : List.of("travel_hub_title:", "travel_hub_atlas:",
                    "travel_hub_beacons:", "travel_hub_routes:", "travel_hub_discover:",
                    "travel_hub_home:", "travel_hub_spawn:", "travel_hub_unstuck:",
                    "admin_plot_list_empty:", "button_back_hub:", "back_hub_lore:")) {
                assertTrue(guis.contains(key), locale + "/guis.yml missing " + key);
                assertTrue(codex.contains(key), locale + " codex missing " + key);
            }
            for (String key : List.of("help_hint:", "help_essentials_header:",
                    "help_unknown_category:", "help_category_empty:", "help_lines_extra:",
                    "help_category_claims:", "help_category_admin:")) {
                assertTrue(system.contains(key), locale + "/system.yml missing " + key);
                assertTrue(codex.contains(key), locale + " codex missing " + key);
            }
        }
    }
}
