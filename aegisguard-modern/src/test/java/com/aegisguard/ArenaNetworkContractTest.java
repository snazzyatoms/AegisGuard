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
 * Cross-server arena contract checks: dedicated arena world, network arena
 * registry, ARENA_JOIN arrivals, lobby phase, and return-home routing.
 */
class ArenaNetworkContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    private static String src(String rel) throws Exception {
        return Files.readString(JAVA_ROOT.resolve(rel));
    }

    @Test
    @SuppressWarnings("unchecked")
    void configShipsArenaNetworkKeys() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var input = Files.newInputStream(RES.resolve("config.yml"))) {
            config = yaml.load(input);
        }
        Map<String, Object> arena = (Map<String, Object>) config.get("arena");
        assertTrue(arena != null, "config.yml must declare an arena section");
        assertTrue(arena.containsKey("lobby_seconds"),
                "arena.lobby_seconds must be declared (0 = instant start)");
        Map<String, Object> world = (Map<String, Object>) arena.get("world");
        assertTrue(world != null, "arena.world must be declared");
        assertEquals(Boolean.FALSE, world.get("enabled"),
                "dedicated arena world must stay opt-in");
        assertTrue(world.containsKey("name"));
        assertTrue(world.containsKey("generator"));
        assertTrue(world.containsKey("auto_zone_radius"));
        assertTrue(world.containsKey("cleanup_on_run_end"));

        Map<String, Object> network = (Map<String, Object>) config.get("network");
        Map<String, Object> arenas = (Map<String, Object>) network.get("arenas");
        assertTrue(arenas != null, "network.arenas must be declared");
        assertEquals(Boolean.TRUE, arenas.get("publish"));
        Map<String, Object> ret = (Map<String, Object>) network.get("arena_return");
        assertTrue(ret != null && ret.containsKey("fallback_server"),
                "network.arena_return.fallback_server must exist for offline-origin returns");
    }

    @Test
    void schemaCarriesArenaRegistryAndArrivalPayload() throws Exception {
        String sql = src("data/SQLDataStore.java");
        assertTrue(sql.contains("aegis_network_arenas"),
                "shared schema must include the arena registry table");
        assertTrue(sql.contains("ensureColumn(s, \"aegis_network_arrivals\", \"payload\""),
                "arrivals table must gain the payload column via ensureColumn migration");
    }

    @Test
    void networkModelsCarryArenaJoinAndRemoteArena() throws Exception {
        String models = src("network/NetworkModels.java");
        assertTrue(models.contains("ARENA_JOIN"));
        assertTrue(models.contains("String payload"));
        assertTrue(models.contains("record RemoteArena"));
    }

    @Test
    void networkStoreRoundTripsArenaCatalog() throws Exception {
        String store = src("network/NetworkStore.java");
        assertTrue(store.contains("publishArenas"));
        assertTrue(store.contains("remoteArenas"));
        assertTrue(store.contains("clearOwnArenas"));
        assertTrue(store.contains("arrival.payload()"),
                "writeArrival must persist the payload column");
    }

    @Test
    void heartbeatPublishesAndStopClearsCatalog() throws Exception {
        String svc = src("network/NetworkService.java");
        assertTrue(svc.contains("publishArenaCatalog"),
                "heartbeat must publish the arena catalog");
        assertTrue(svc.contains("clearOwnArenas"),
                "stop() must clear this backend's arena advertisements");
    }

    @Test
    void travelServiceCarriesArenaJoinAndReturnHome() throws Exception {
        String travel = src("network/NetworkTravelService.java");
        assertTrue(travel.contains("sendToArenaJoin"));
        assertTrue(travel.contains("landInArena"));
        assertTrue(travel.contains("sendHomeFromArena"),
                "return-home must exist with offline-origin fallback chain");
        assertTrue(travel.contains("arena_already_in_run"),
                "in-run players must be refused a second arena hop");
    }

    @Test
    void arenaServiceHasLobbyPhaseAndRemoteJoin() throws Exception {
        String svc = src("arena/ArenaService.java");
        assertTrue(svc.contains("ArenaRunState.LOBBY"));
        assertTrue(svc.contains("enterLobby"));
        assertTrue(svc.contains("tickLobbies"));
        assertTrue(svc.contains("joinLobbyRun"));
        assertTrue(svc.contains("startLobbyNow"));
        assertTrue(svc.contains("findOpenLobby"));
        assertTrue(svc.contains("joinRemote"));
        assertTrue(svc.contains("sendHomeIfRemote"));
        assertTrue(svc.contains("networkCatalog"));
        assertTrue(svc.contains("lobbySeconds()"));
    }

    @Test
    void participantAndRecoveryCarryOriginServer() throws Exception {
        String part = src("arena/ArenaParticipant.java");
        assertTrue(part.contains("getOriginServer"));
        assertTrue(part.contains("setOriginServer"));
        String rec = src("arena/ArenaPendingRecovery.java");
        assertTrue(rec.contains("getOriginServer"),
                "pending recovery must carry origin so relog-on-host routes home");
    }

    @Test
    void worldServiceIsWiredAndFoliaSafe() throws Exception {
        String plugin = src("AegisGuard.java");
        assertTrue(plugin.contains("new com.aegisguard.arena.ArenaWorldService(this)"));
        assertTrue(plugin.contains("arenaWorldService.ensureWorld()"));

        String svc = src("arena/ArenaWorldService.java");
        assertTrue(svc.contains("arena.world.enabled"));
        assertTrue(svc.contains("autoZone"));
        assertTrue(svc.contains("cleanupFloor"));
        assertTrue(svc.contains("isChunkLoaded"),
                "floor sweep must iterate loaded chunks, not world.getEntities() (Folia regions)");
        assertFalse(svc.contains("Bukkit.getScheduler()"),
                "arena world service must use the arena scheduler");
    }

    @Test
    void commandAndGuiRouteRemoteJoins() throws Exception {
        String cmd = src("arena/ArenaCommand.java");
        assertTrue(cmd.contains("joinRemote"),
                "/ag arena join must route server:id and remote ids through joinRemote");
        assertTrue(cmd.contains("remoteArenas"),
                "arena list must append remote catalog entries");
        assertTrue(cmd.contains("autoZone"),
                "arena create must auto-provision a zone in the arena world");

        String gui = src("arena/ArenaGUI.java");
        assertTrue(gui.contains("remoteAt"),
                "menu holder must carry remote arena slots");
        assertTrue(gui.contains("sendToArenaJoin"),
                "remote band clicks must hop via sendToArenaJoin");
    }

    @Test
    void arenaLangKeysExistInEveryPack() throws Exception {
        List<String> keys = List.of(
                "arena_lobby_open", "arena_lobby_joined", "arena_lobby_member_joined",
                "arena_lobby_countdown", "arena_lobby_started_early", "arena_lobby_closed",
                "arena_lobby_full", "arena_not_leader", "arena_remote_offline",
                "arena_remote_offline_lore", "arena_remote_online", "arena_remote_server_line",
                "arena_return_home", "arena_auto_zoned", "arena_network_travel",
                "arena_unavailable", "arena_list_remote_header", "arena_list_remote_entry");
        List<String> locales = List.of(
                "modern_english", "old_english", "french_fr", "german_de", "italian_it",
                "polish_pl", "portuguese_br", "spanish_ar", "spanish_mx");
        for (String loc : locales) {
            String lang = Files.readString(RES.resolve("lang/" + loc + "/system.yml"));
            String codex = Files.readString(RES.resolve("codex/" + loc + ".yml"));
            for (String key : keys) {
                assertTrue(lang.contains(key + ":"),
                        "lang/" + loc + "/system.yml missing " + key);
                assertTrue(codex.contains(key + ":"),
                        "codex/" + loc + ".yml missing " + key);
            }
        }
    }

    @Test
    void lobbyTickRunsOnGlobalSchedulerNotBukkit() throws Exception {
        String svc = src("arena/ArenaService.java");
        assertTrue(svc.contains("runGlobalRepeating(this::tickLobbies"),
                "lobby countdown must ride the arena/global scheduler, not Bukkit");
    }
}
