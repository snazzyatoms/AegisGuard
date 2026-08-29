package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 6 (Routes and Checkpoints) contract checks.
 */
class RoutesContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsSafelyDefaultedRoutesSection() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        Map<String, Object> routes = (Map<String, Object>) config.get("routes");
        assertTrue(routes != null, "config.yml must declare a routes section");
        assertFalse(Boolean.TRUE.equals(routes.get("allow_optional_teleport")),
                "Optional teleport must default OFF so discovery never requires teleporting");
    }

    @Test
    void routeServiceIsWiredIntoPluginLifecycle() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("new com.aegisguard.routes.RouteService(this)"));
        assertTrue(plugin.contains("routeService.load()"));
        assertTrue(plugin.contains("routeService.save()"));
        assertTrue(plugin.contains("RouteDiscoveryListener"));
    }

    @Test
    void playerAndAdminGuisExposeRoutesEntryPoints() throws Exception {
        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("routes().open(player)"));

        String adminGui = Files.readString(JAVA_ROOT.resolve("gui/AdminGUI.java"));
        assertTrue(adminGui.contains("open_routes"));
        assertTrue(adminGui.contains("routeAdmin().open(player)"));
    }

    @Test
    void routesNeverAlterClaimBoundaries() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("routes/RouteService.java"));
        assertFalse(service.contains("SelectionService") || service.contains("setMin") || service.contains("expandPlot"),
                "RouteService must never touch claim geometry");
    }

    @Test
    void configSchemaWasBumpedForRoutes() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1277")
                        || migration.contains("CURRENT_SCHEMA = 1278")
                        || migration.contains("CURRENT_SCHEMA = 1280")
                        || migration.contains("CURRENT_SCHEMA = 1281")
                        || migration.contains("CURRENT_SCHEMA = 1282")
                || migration.contains("CURRENT_SCHEMA = 1283")
                || migration.contains("CURRENT_SCHEMA = 1284") || migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286")
                || migration.contains("CURRENT_SCHEMA = 1287")
                || migration.contains("CURRENT_SCHEMA = 1292")
                || migration.contains("CURRENT_SCHEMA = 1294")
                || migration.contains("CURRENT_SCHEMA = 1300")
                || migration.contains("CURRENT_SCHEMA = 1302"),
                "Routes schema bump (1277) must remain current or superseded");
    }

    @Test
    void routesPermissionIsDeclared() throws Exception {
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(pluginYml.contains("aegis.admin.routes"));
    }
}
