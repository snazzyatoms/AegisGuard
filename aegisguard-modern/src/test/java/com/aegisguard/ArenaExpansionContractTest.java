package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arena-expansion contract checks: floors/map rotation, PvP and boss modes,
 * staging hub, matchmaking queue, beacon arena gates, and schematic
 * instancing. Also pins regressions fixed during review (roster ordering,
 * instanced-start marking, lobby-join snapshot rollback).
 */
class ArenaExpansionContractTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");

    private static String src(String rel) throws Exception {
        return Files.readString(JAVA_ROOT.resolve(rel));
    }

    @Test
    @SuppressWarnings("unchecked")
    void configShipsExpansionKeys() throws Exception {
        Map<String, Object> config;
        try (var input = Files.newInputStream(RES.resolve("config.yml"))) {
            config = new Yaml().load(input);
        }
        Map<String, Object> arena = (Map<String, Object>) config.get("arena");
        assertTrue(arena != null, "config.yml must declare an arena section");

        Map<String, Object> pvp = (Map<String, Object>) arena.get("pvp");
        assertTrue(pvp != null && pvp.containsKey("enabled"),
                "arena.pvp.enabled must gate PVP_* modes");
        assertEquals(Boolean.FALSE, pvp.get("enabled"), "PvP must default off");

        Map<String, Object> staging = (Map<String, Object>) arena.get("staging");
        assertTrue(staging != null, "arena.staging must be declared");
        assertTrue(staging.containsKey("enabled"));
        assertTrue(staging.containsKey("return_on_end"));
        assertTrue(staging.containsKey("world"));

        Map<String, Object> queue = (Map<String, Object>) arena.get("queue");
        assertTrue(queue != null, "arena.queue must be declared");
        assertTrue(queue.containsKey("enabled"));
        assertTrue(queue.containsKey("tick_seconds"));

        Map<String, Object> schematics = (Map<String, Object>) arena.get("schematics");
        assertTrue(schematics != null, "arena.schematics must be declared");
        assertTrue(schematics.containsKey("world"));
        assertTrue(schematics.containsKey("instance_spacing"));
        assertTrue(schematics.containsKey("max_instances"));
        assertTrue(schematics.containsKey("max_blocks"));

        Map<String, Object> beacons = (Map<String, Object>) config.get("teleport_beacons");
        Map<String, Object> gates = (Map<String, Object>) beacons.get("arena_gates");
        assertTrue(gates != null && gates.containsKey("enabled"),
                "teleport_beacons.arena_gates.enabled must gate arena pads");
    }

    @Test
    void modesCoverPvpAndBoss() throws Exception {
        String mode = src("arena/ArenaMode.java");
        for (String m : new String[]{"PVE_WAVES", "PVE_BOSS", "PVP_FFA", "PVP_DUEL"}) {
            assertTrue(mode.contains(m), "ArenaMode must declare " + m);
        }
        assertTrue(mode.contains("isPvp"), "modes must expose an isPvp() check");
    }

    @Test
    void floorsSupportVariantsAndSchematics() throws Exception {
        String def = src("arena/ArenaDefinition.java");
        assertTrue(def.contains("pickFloor"), "run start must pick a floor");
        assertTrue(def.contains("floorAt"), "floor indexing must exist");
        assertTrue(def.contains("setPrimarySchematic"),
                "primary floor must carry schematic state separately");
        String floor = src("arena/ArenaFloor.java");
        assertTrue(floor.contains("getMobSpawns"), "floors carry their own mob spawns");
        assertTrue(floor.contains("setCaptureBounds"), "floors carry capture geometry");
        assertTrue(floor.contains("isInstanced"), "floors must know when they instance");
    }

    @Test
    void pvpModeValidationAndDamageGate() throws Exception {
        String def = src("arena/ArenaDefinition.java");
        assertTrue(def.contains("revalidate(boolean pvpAllowed)"),
                "PvP validation must take the global gate");
        String service = src("arena/ArenaService.java");
        assertTrue(service.contains("pvpArenasAllowed"),
                "service must read arena.pvp.enabled");
        assertTrue(service.contains("isArenaPvpAllowed"),
                "service must expose the PvP-damage exemption check");
        String protection = src("protection/ProtectionManager.java");
        assertTrue(protection.contains("isArenaPvpAllowed"),
                "plot PvP protection must yield to same-run PvP fighters");
    }

    @Test
    void stagingReadsConfigAndFallsBackToExit() throws Exception {
        String staging = src("arena/ArenaStagingService.java");
        assertTrue(staging.contains("arena.staging.enabled"));
        assertTrue(staging.contains("arena.staging.return_on_end"));
        assertTrue(staging.contains("getExitSpawn"),
                "staging must fall back to the arena exit spawn");
        String service = src("arena/ArenaService.java");
        assertTrue(service.contains("arenaStaging().landAfterRun"),
                "post-run restore must route through staging");
    }

    @Test
    void queueKeepsPartyLeaderFirst() throws Exception {
        // Regression: Set.copyOf(members) loses ordering — the leader must be
        // inserted first explicitly or a random member leads the matched run.
        String queue = src("arena/ArenaQueueService.java");
        int leaderAdd = queue.indexOf("members.add(party.getLeaderId())");
        int memberLoop = queue.indexOf("for (UUID mid : party.getMembers())");
        assertTrue(leaderAdd >= 0 && memberLoop > leaderAdd,
                "queued parties must list the leader before other members");
        assertTrue(queue.contains("tick_seconds"),
                "queue must honor the configured matchmaking interval");
    }

    @Test
    void beaconArenaGatesArePersistedAndRouted() throws Exception {
        String beacon = src("beacon/TeleportBeacon.java");
        assertTrue(beacon.contains("linkedTarget"), "beacons carry an arena target");
        assertTrue(beacon.contains("isArenaLinked"));
        String store = src("beacon/BeaconStore.java");
        assertTrue(store.contains("linked_target"),
                "linked_target must persist in YAML and SQL");
        String service = src("beacon/BeaconService.java");
        assertTrue(service.contains("arena:hub"), "local hub gate must exist");
        assertTrue(service.contains("rarena:"), "remote arena gate must exist");
        assertTrue(service.contains("linkToArena"));
    }

    @Test
    void schematicServiceDrivesInstances() throws Exception {
        String schem = src("arena/ArenaSchematicService.java");
        assertTrue(schem.contains("arena-schematics"), "schematic folder must be namespaced");
        assertTrue(schem.contains("prepareInstance"));
        assertTrue(schem.contains("releaseInstance"));
        assertTrue(schem.contains("BuiltInClipboardFormat.SPONGE_SCHEMATIC"),
                "capture must write sponge .schem files");
        assertFalse(schem.contains("Bukkit.getScheduler"),
                "no direct Bukkit scheduling — Folia dispatch only");
    }

    @Test
    void instancedStartReservesRosterBeforePaste() throws Exception {
        // Regression: playerToRun was only marked inside finishStartRun, so a
        // member could join another run during the paste window and get
        // double-booked. The reservation must exist before the paste future.
        String service = src("arena/ArenaService.java");
        int prep = service.indexOf("schematics.prepareInstance(run, floor)");
        int mark = service.indexOf("playerToRun.put(mid, runId)");
        // The instanced continuation inside thenAccept — the non-instanced
        // fork calls finishStartRun earlier and must not match here.
        int finish = service.indexOf(
                "finishStartRun(def, run, runId, party, leader, originServer)", mark);
        assertTrue(prep > 0 && mark > prep && finish > mark,
                "roster must be reserved between paste kickoff and roster start");
        assertTrue(service.contains("unreserveRoster"),
                "failed instanced starts must release the reservation");
    }

    @Test
    void lobbyJoinRestoresSnapshotOnFailure() throws Exception {
        // Regression: the lobby-join catch dropped the participant without
        // restoring a taken snapshot — a late failure meant a lost inventory.
        String service = src("arena/ArenaService.java");
        int join = service.indexOf("joinLobbyRun(Player player, ArenaRun run");
        int catchBlock = service.indexOf("restoreFromFile(player, part.getSnapshotPath())", join);
        assertTrue(join > 0 && catchBlock > join,
                "lobby-join failures must restore taken inventory snapshots");
    }

    @Test
    void stagingArrivalKindLandsInHub() throws Exception {
        String travel = src("network/NetworkTravelService.java");
        assertTrue(travel.contains("ARENA_STAGING"));
        assertTrue(travel.contains("sendToArenaHub"));
        assertTrue(travel.contains("landInArenaStaging"));
        assertTrue(travel.contains("sendToStaging"),
                "staging arrivals must deliver to the configured hub");
    }

    @Test
    void guiExposesQueueAndRemoteArenas() throws Exception {
        String gui = src("arena/ArenaGUI.java");
        assertTrue(gui.contains("toggleQueue"), "arena GUI must offer matchmaking");
        assertTrue(gui.contains("arenaQueue"), "queue service must be reachable from the GUI");
        assertTrue(gui.contains("remoteAt(slot)"), "remote arenas stay clickable");
    }

    @Test
    void wandSelectionSizesArenaZones() throws Exception {
        // A complete Aegis-wand selection claims the admin's exact corners —
        // the radius default is only the no-selection fallback.
        String world = src("arena/ArenaWorldService.java");
        assertTrue(world.contains("hasWorldSelection"),
                "autoZone must detect wand selections");
        int selBranch = world.indexOf("hasWorldSelection(player)");
        int radiusBranch = world.indexOf("auto_zone_radius", selBranch);
        assertTrue(selBranch > 0 && radiusBranch > selBranch,
                "the selection path must run before the radius fallback");
        assertTrue(world.contains("findZonePlot"),
                "zone recovery must match claimed bounds, not player position");
        String cmd = src("arena/ArenaCommand.java");
        assertTrue(cmd.contains("arena_zone_from_selection"),
                "arena create must report selection-sized zones");
    }

    @Test
    void stagingSelectionClaimsProtectedZone() throws Exception {
        String staging = src("arena/ArenaStagingService.java");
        assertTrue(staging.contains("confirmClaim(admin, true)"),
                "staging set must claim the wand selection as a server zone");
        assertTrue(staging.contains("arena.staging.zone_plot"),
                "the claimed zone must be tracked for cleanup");
        assertTrue(staging.contains("arenaUsesPlot"),
                "zone retirement must not delete arena-bound plots");
        assertTrue(staging.contains("SERVER_OWNER_UUID"),
                "only auto-created server zones may be retired");
        String cmd = src("arena/ArenaCommand.java");
        assertTrue(cmd.contains("arena_staging_zone_kept"),
                "clear must report when the zone survives due to an arena");
    }

    @Test
    void sealedRunsGateEntryAndInterference() throws Exception {
        String service = src("arena/ArenaService.java");
        assertTrue(service.contains("runCovering"),
                "sealed regions need a location->run lookup");
        assertTrue(service.contains("isArenaEntryDenied"),
                "entry gate must exist");
        assertTrue(service.contains("blocksArenaInterference"),
                "outside-influence damage gate must exist");
        assertTrue(service.contains("getInstanceOrigin"),
                "instanced run cuboids must be covered, not just plots");
        assertTrue(service.contains("getLobbyPlotId"),
                "the lobby region must seal while the run lives");
        assertTrue(service.contains("hasTeleportAllow"),
                "arena-managed teleports must pass the seal");
        assertTrue(service.contains("arena.seal_runs"),
                "sealing must honor the config toggle");
        // Projectile damage must resolve to the shooter before judging.
        assertTrue(service.contains("proj.getShooter()"),
                "projectiles must resolve to their shooter");

        String protection = src("protection/ProtectionManager.java");
        int seals = 0;
        for (int idx = -1; (idx = protection.indexOf("isArenaEntryDenied", idx + 1)) >= 0; ) seals++;
        assertTrue(seals >= 4,
                "walk, teleport, respawn, and canEnterPlot must all honor the seal");

        String listener = src("arena/ArenaListener.java");
        assertTrue(listener.contains("EntityDamageByEntityEvent")
                        && listener.contains("blocksArenaInterference"),
                "the interference gate must cancel outside damage");
    }

    @Test
    void expansionLangKeysExist() throws Exception {
        String lang = Files.readString(RES.resolve("lang/modern_english/system.yml"));
        for (String key : new String[]{
                "arena_staging_set", "arena_staging_return", "arena_staging_unset",
                "arena_queue_joined", "arena_queue_left", "arena_queue_matched",
                "arena_queue_disabled", "arena_floor_added", "arena_map_saved",
                "arena_mode_set", "arena_pvp_disabled", "beacon_linked_arena",
                "arena_instance_preparing", "arena_instance_unavailable",
                "arena_zone_from_selection", "arena_staging_zone_kept",
                "arena_run_sealed"}) {
            assertTrue(lang.contains(key + ":"),
                    "modern_english must define " + key);
        }
    }
}
