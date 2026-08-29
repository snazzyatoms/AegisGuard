package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arena module contract checks (1.3.0 optional feature).
 */
class ArenaContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsArenaEnabledByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(input);
        }
        Map<String, Object> arena = (Map<String, Object>) config.get("arena");
        assertTrue(arena != null, "config.yml must declare an arena section");
        assertEquals(Boolean.TRUE, arena.get("enabled"), "arena.enabled must default true");
        Map<String, Object> defaults = (Map<String, Object>) arena.get("defaults");
        assertTrue(defaults != null);
        assertEquals(1, ((Number) defaults.get("max_active_runs_per_arena")).intValue());
    }

    @Test
    void versionIsOnThe14Line() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<version>1.4.0</version>"));
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(pluginYml.contains("version: 1.4.0"));
    }

    @Test
    void arenaServiceIsWiredIntoPluginLifecycle() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("new com.aegisguard.arena.ArenaService(this)"));
        assertTrue(plugin.contains("arenaService.load()"));
        assertTrue(plugin.contains("arenaService.save()"));
        assertTrue(plugin.contains("recoverIncompleteRunsOnEnable()"));
        assertTrue(plugin.contains("ArenaListener"));
    }

    @Test
    void playerAndAdminGuisExposeArenaEntryPoints() throws Exception {
        String playerGui = Files.readString(JAVA_ROOT.resolve("gui/PlayerGUI.java"));
        assertTrue(playerGui.contains("arena().open(player)"));

        String adminGui = Files.readString(JAVA_ROOT.resolve("gui/AdminGUI.java"));
        assertTrue(adminGui.contains("open_arena"));
        assertTrue(adminGui.contains("arenaAdmin().open(player)"));
    }

    @Test
    void permissionsDeclared() throws Exception {
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(pluginYml.contains("aegis.arena.use"));
        assertTrue(pluginYml.contains("aegis.arena.admin"));
        assertTrue(pluginYml.contains("aegis.arena.steward"));
    }

    @Test
    void schemaBumpedForArena() throws Exception {
        String migration = Files.readString(JAVA_ROOT.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1285")
                || migration.contains("CURRENT_SCHEMA = 1286")
                || migration.contains("CURRENT_SCHEMA = 1287")
                || migration.contains("CURRENT_SCHEMA = 1294")
                || migration.contains("CURRENT_SCHEMA = 1300")
                || migration.contains("CURRENT_SCHEMA = 1304")
                || migration.contains("CURRENT_SCHEMA = 1305")
                || migration.contains("CURRENT_SCHEMA = 1306")
                || migration.contains("CURRENT_SCHEMA = 1307")
                || migration.contains("CURRENT_SCHEMA = 1308"));
    }

    @Test
    void safeTravelHasArenaKind() throws Exception {
        String travel = Files.readString(JAVA_ROOT.resolve("travel/SafeTravelService.java"));
        assertTrue(travel.contains("ARENA"));
    }

    @Test
    void arenaServiceDoesNotTouchClaimGeometry() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("arena/ArenaService.java"));
        assertFalse(service.contains("SelectionService") || service.contains("expandPlot"),
                "ArenaService must never expand claim geometry");
    }

    @Test
    void tryStartBusyAndUnboundMessagingPresent() throws Exception {
        String service = Files.readString(JAVA_ROOT.resolve("arena/ArenaService.java"));
        assertTrue(service.contains("arena.defaults.max_active_runs_per_arena"),
                "Must read per-arena max from config defaults path");
        assertTrue(service.contains("arena_busy"),
                "Busy refuse must use localized arena_busy key");
        assertTrue(service.contains("arena_not_ready"),
                "Unbound plots must surface actionable start errors via arena_not_ready");
        assertTrue(service.contains("spawnCurrentWave"),
                "Wave spawn loop must be wired after start/advance");
        assertTrue(service.contains("tickRuns"),
                "Leadership/disconnect grace must tick");
    }

    @Test
    void arenaTickTaskIsWired() throws Exception {
        String plugin = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("startArenaTickTask()"));
        assertTrue(plugin.contains("arenaService.tickRuns()"));
    }

    @Test
    void arenaPackageDoesNotUseBukkitScheduler() throws Exception {
        Path arenaDir = JAVA_ROOT.resolve("arena");
        try (var stream = Files.walk(arenaDir)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(javaFiles.isEmpty());
            for (Path file : javaFiles) {
                String src = Files.readString(file);
                assertFalse(src.contains("Bukkit.getScheduler()"),
                        "Arena must not call Bukkit.getScheduler() directly: " + file.getFileName());
            }
        }
        String scheduler = Files.readString(arenaDir.resolve("ArenaScheduler.java"));
        assertTrue(scheduler.contains("runForEntity"));
        assertTrue(scheduler.contains("runAtLocation"));
        assertTrue(scheduler.contains("runGlobal"));
        assertTrue(scheduler.contains("runAsync"));
        assertTrue(scheduler.contains("isFolia()"));
        String service = Files.readString(arenaDir.resolve("ArenaService.java"));
        assertTrue(service.contains("new ArenaScheduler(plugin)"));
        assertTrue(service.contains("schedulerPath="));
        assertTrue(service.contains("TeleportUtil.safeTeleport"),
                "Arena player teleports must use teleportAsync / entity-scheduler fallback, not Entity#teleport");
        assertFalse(service.contains("player.teleport(loc)"),
                "Sync Entity#teleport is not Folia-safe for cross-region arena entry");
    }
}
