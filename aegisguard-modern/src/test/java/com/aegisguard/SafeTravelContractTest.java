package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract checks for shared Safe Travel wiring across teleport entry points.
 */
class SafeTravelContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path LANG_ROOT = Path.of("src/main/resources/lang");

    @Test
    void safeTravelServiceIsWiredIntoPluginLifecycle() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("safeTravelService = new com.aegisguard.travel.SafeTravelService(this)"));
        assertTrue(plugin.contains("registerEvents(safeTravelService"));
        assertTrue(plugin.contains("safeTravelService.reload()"));
        assertTrue(plugin.contains("safeTravel()"));
    }

    @Test
    void teleportEntryPointsUseSharedSafeTravelApi() throws Exception {
        List<Path> files = List.of(
                JAVA_ROOT.resolve("gui/VisitGUI.java"),
                JAVA_ROOT.resolve("routes/RoutesGUI.java"),
                JAVA_ROOT.resolve("gui/PlotMarketGUI.java"),
                JAVA_ROOT.resolve("gui/PlotAuctionGUI.java"),
                JAVA_ROOT.resolve("gui/ZoneBrowseGUI.java"),
                JAVA_ROOT.resolve("gui/ZoneTenantGUI.java"),
                JAVA_ROOT.resolve("gui/AdminPlotListGUI.java"),
                JAVA_ROOT.resolve("commands/AegisCommand.java")
        );
        for (Path file : files) {
            String source = Files.readString(file);
            assertTrue(source.contains("safeTravel()"), file + " must route teleports through SafeTravelService");
        }
    }

    @Test
    void languagePacksIncludeTravelFailureKeys() throws Exception {
        List<String> keys = List.of(
                "travel_fail_cooldown:",
                "travel_fail_combat:",
                "travel_confirm_prompt:",
                "travel_fail_unsafe:"
        );
        for (String pack : List.of("modern_english", "old_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl")) {
            String system = Files.readString(LANG_ROOT.resolve(pack).resolve("system.yml"));
            for (String key : keys) {
                assertTrue(system.contains(key), pack + " missing " + key);
            }
        }
    }

    @Test
    void teleportUtilAcceptsConfigurableSafeSearchRadius() throws Exception {
        String util = Files.readString(JAVA_ROOT.resolve("util/TeleportUtil.java"));
        assertTrue(util.contains("findSafeDestination(Location requested, int maxRadius)"));
    }
}
