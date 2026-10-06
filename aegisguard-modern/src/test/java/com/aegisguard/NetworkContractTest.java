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
 * BungeeCord cross-server contract: the network layer must stay self-disabling
 * on non-shared backends, keep remote plots out of every local index, gate
 * mutations, dedupe relayed chat, and document itself in config.
 */
class NetworkContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path NETWORK = JAVA.resolve("network");

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    @Test
    @SuppressWarnings("unchecked")
    void configDocumentsAndDefaultsNetworkOff() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(in);
        }
        Map<String, Object> network = (Map<String, Object>) config.get("network");
        assertTrue(network != null, "config.yml must ship a network section");
        assertEquals(Boolean.FALSE, network.get("enabled"));
        assertTrue(network.containsKey("server_name"));
        assertTrue(network.containsKey("heartbeat_seconds"));
        assertTrue(network.containsKey("offline_after_seconds"));
        assertTrue(network.containsKey("arrival_ttl_seconds"));
        Map<String, Object> travel = (Map<String, Object>) network.get("travel");
        assertEquals(Boolean.TRUE, travel.get("remote_destinations"));
        assertEquals(Boolean.TRUE, travel.get("cross_server_travel"));
        Map<String, Object> chat = (Map<String, Object>) network.get("chat");
        assertEquals(Boolean.TRUE, chat.get("relay_channels"));
        Map<String, Object> shared = (Map<String, Object>) network.get("shared_player_data");
        assertEquals(Boolean.TRUE, shared.get("enabled"));
    }

    @Test
    void networkServiceGatesOnSharedSqlAndRegistersChannels() throws Exception {
        String service = read(NETWORK.resolve("NetworkService.java"));
        assertTrue(service.contains("\"mysql\".equals(backend) || \"mariadb\".equals(backend)"),
                "network must require mysql/mariadb — sqlite/yml can never share");
        assertTrue(service.contains("registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL)"));
        assertTrue(service.contains("registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this)"));
        assertTrue(service.contains("network.server_name"));
        // Hop uses the proxy Connect subchannel, never raw teleports across servers
        assertTrue(service.contains("out.writeUTF(\"Connect\")"));
        assertFalse(service.contains("player.teleport("));
        // Shutdown must cancel the heartbeat and deregister
        assertTrue(service.contains("plugin.scheduler().cancel(heartbeatTask)"));
        assertTrue(service.contains("deregisterServer"));
    }

    @Test
    void remotePlotsStayOutOfLocalIndexesAndWrites() throws Exception {
        String store = read(JAVA.resolve("data/SQLDataStore.java"));
        assertTrue(store.contains("remotePlots"),
                "SQLDataStore must keep a dedicated remote-plot index");
        // Remote plots must not enter the owner or chunk indexes
        assertTrue(store.contains("if (isRemotePlot(plot))"),
                "cachePlot must branch remote plots away from local indexes");
        // Remote rows must never be written or deleted by this backend
        assertTrue(store.contains("Never write another backend's row"),
                "savePlotInternal must refuse remote writes");
        assertTrue(store.contains("remotePlots.containsKey(plotId)"),
                "removePlot must not DELETE remote rows");
        // Legacy rows get claimed atomically for the configured server
        assertTrue(store.contains("UPDATE aegis_plots SET server = ? WHERE server IS NULL OR server = ''"));
        // Server column exists in schema + upsert
        assertTrue(store.contains("server VARCHAR(64)"));
    }

    @Test
    void travelIsArrivalBackedAndFailsClosed() throws Exception {
        String travel = read(NETWORK.resolve("NetworkTravelService.java"));
        // Pending arrival written before the Connect hop
        int write = travel.indexOf("writeArrival");
        int connect = travel.indexOf("sendToServer(player, targetServer)");
        assertTrue(write >= 0 && connect > write,
                "arrival row must be written before the proxy hop");
        // Offline destination check + TTL + expiry on consume
        assertTrue(travel.contains("isServerOnline"));
        assertTrue(travel.contains("arrival_ttl_seconds"));
        assertTrue(travel.contains("arrival.isExpiredAt(net().networkNow())"),
                "arrival expiry must compare on the normalized DB clock");
        // Landing re-checks lockdown + entry + beacon-arrival rules on the destination
        assertTrue(travel.contains("isLockdownActive"));
        assertTrue(travel.contains("canEnterPlot"));
        assertTrue(travel.contains("requiresBeaconArrival"));
    }

    @Test
    void chatRelayDedupesAndNeverRebroadcasts() throws Exception {
        String chat = read(NETWORK.resolve("NetworkChatService.java"));
        assertTrue(chat.contains("mid"), "every relayed message needs a dedupe id");
        assertTrue(chat.contains("delivered.add(mid)"), "forwarded + polled copies must dedupe");
        assertTrue(chat.contains("origin.equalsIgnoreCase(n.serverName())"),
                "own events must be skipped");
        assertTrue(chat.contains("if (from < 0) return;"),
                "poll must wait for cursor init instead of replaying the table");
        // Receiving end delivers per-player on each target's entity thread
        // (Folia: hasPermission/sendMessage are region-bound).
        assertTrue(chat.contains("plugin.runMain(target"),
                "delivery must run per-player on the target's entity thread");
    }

    @Test
    void joinListenerConsumesArrivalsAndSyncsPlayerData() throws Exception {
        String listener = read(NETWORK.resolve("NetworkJoinListener.java"));
        assertTrue(listener.contains("handleJoin"));
        assertTrue(listener.contains("pullOnJoin"));
        assertTrue(listener.contains("pushNow"),
                "quit must flush shared player data before the player lands elsewhere");
        assertTrue(listener.contains("runEntityLater"),
                "arrival landing must be entity-scheduled (Folia)");
    }

    @Test
    void mergeMethodsReplaceRostersSoKickedMembersStayGone() throws Exception {
        String alliance = read(JAVA.resolve("alliance/AllianceManager.java"));
        assertTrue(alliance.contains("mergeNetworkAlliance"));
        assertTrue(alliance.contains("playerToAlliance.remove(existing)"),
                "alliance merge must drop members missing from the network roster");
        String group = read(JAVA.resolve("groups/GroupManager.java"));
        assertTrue(group.contains("mergeNetworkGroup"));
        assertTrue(group.contains("playerToGroup.remove(existing)"),
                "group merge must drop members missing from the network roster");
    }

    @Test
    void networkKeysExistInAllNineLangPacks() throws Exception {
        Path lang = Path.of("src/main/resources/lang");
        List<String> guisKeys = List.of(
                "visit_remote_line", "network_sending_to_server",
                "visit_fail_server_offline", "network_arrival_gone");
        List<String> systemKeys = List.of(
                "admin_network_offline", "admin_network_header", "admin_network_empty",
                "admin_network_entry", "admin_network_arrivals", "admin_help_network");
        try (var dirs = Files.list(lang)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String guis = read(dir.resolve("guis.yml"));
                for (String key : guisKeys) {
                    assertTrue(guis.contains(key + ":"),
                            dir.getFileName() + "/guis.yml missing " + key);
                }
                String system = read(dir.resolve("system.yml"));
                for (String key : systemKeys) {
                    assertTrue(system.contains(key + ":"),
                            dir.getFileName() + "/system.yml missing " + key);
                }
            }
        }
    }

    @Test
    void setupDocExistsAndCoversTheOneWayFlow() throws Exception {
        Path doc = Path.of("../NETWORK_SETUP.md");
        assertTrue(Files.exists(doc), "NETWORK_SETUP.md must ship beside the module");
        String text = read(doc);
        assertTrue(text.contains("server_name"));
        assertTrue(text.contains("mysql") || text.contains("MySQL"));
    }

    // ------------------------------------------------------------------
    // Hub / server selector / remote pads / broadcast (1.4.0 wave 2)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void configDocumentsHubDefaults() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(in);
        }
        Map<String, Object> network = (Map<String, Object>) config.get("network");
        Map<String, Object> hub = (Map<String, Object>) network.get("hub");
        assertTrue(hub != null, "network.hub section must ship in config.yml");
        assertEquals("", hub.get("server"), "hub.server defaults unset — /hub falls back to spawn");
        assertTrue(hub.containsKey("world") && hub.containsKey("x")
                && hub.containsKey("y") && hub.containsKey("z"),
                "hub landing point keys must exist");
        assertEquals(Boolean.TRUE, hub.get("register_commands"),
                "/hub /lobby registration defaults on for networks");
        assertEquals(Boolean.FALSE, hub.get("redirect_new_players"),
                "first-join redirect must be OFF by default");
    }

    @Test
    void worldSpawnArrivalsAndHubHopExist() throws Exception {
        String models = read(NETWORK.resolve("NetworkModels.java"));
        assertTrue(models.contains("WORLD_SPAWN"),
                "ArrivalKind must carry WORLD_SPAWN for hub/server hops");
        String travel = read(NETWORK.resolve("NetworkTravelService.java"));
        assertTrue(travel.contains("public boolean sendToServer(Player player, String targetServer)"));
        assertTrue(travel.contains("public boolean sendToHub(Player player)"));
        assertTrue(travel.contains("sendToPad"),
                "remote pad hops need a sendToPad producer");
        assertTrue(travel.contains("landAtWorldSpawn"),
                "WORLD_SPAWN arrivals need a landing handler");
        // Hub falls back to local spawn when already on the hub backend
        assertTrue(travel.contains("hub.equalsIgnoreCase(net().serverName())"));
    }

    @Test
    void firstJoinRedirectAndLastServerTracking() throws Exception {
        String data = read(NETWORK.resolve("NetworkPlayerDataService.java"));
        assertTrue(data.contains("last_server"),
                "shared rows must record the hosting backend for admin find");
        assertTrue(data.contains("redirect_new_players"),
                "first-network-join redirect must be config-gated");
        assertTrue(data.contains("runEntityLater"),
                "redirect must be delayed past the join handshake");
    }

    @Test
    void serverSelectorGuiIsRegisteredAndDispatched() throws Exception {
        String gui = read(JAVA.resolve("gui/GUIManager.java"));
        assertTrue(gui.contains("networkServersGUI"));
        assertTrue(gui.contains("public NetworkServersGUI networkServers()"));
        String listener = read(JAVA.resolve("gui/GUIListener.java"));
        assertTrue(listener.contains("NetworkServersHolder"),
                "GUIListener must whitelist + dispatch the servers holder");
        String hub = read(JAVA.resolve("gui/TravelHubGUI.java"));
        assertTrue(hub.contains("networkServers().open"),
                "Travel Hub needs a Servers button");
        assertTrue(hub.contains("sendToHub"),
                "Travel Hub needs the Network Hub quick action");
    }

    @Test
    void remoteBeaconLinksStayPublicAndOneWay() throws Exception {
        String store = read(NETWORK.resolve("NetworkStore.java"));
        assertTrue(store.contains("remotePublicBeacons"));
        assertTrue(store.contains("public_access = 1"),
                "remote link picker must expose public pads only");
        assertTrue(store.contains("server <> ?") || store.contains("server <>"),
                "remote picker must exclude pads on this backend");
        assertTrue(store.contains("clearInboundBeaconLinks"),
                "deleting a pad must clear links pointing at it network-wide");
        String service = read(JAVA.resolve("beacon/BeaconService.java"));
        assertTrue(service.contains("linkRemote"));
        assertTrue(service.contains("executeRemoteTrip"));
        assertTrue(service.contains("tryRemotePad"));
        String gui = read(JAVA.resolve("beacon/BeaconGUI.java"));
        assertTrue(gui.contains("rdest:"),
                "link picker must distinguish remote pads from local");
        String beaconStore = read(JAVA.resolve("beacon/BeaconStore.java"));
        assertTrue(beaconStore.contains("clearInboundBeaconLinks"),
                "local unbind must clear remote inbound links");
    }

    @Test
    void adminBroadcastAndFindRideTheEventBus() throws Exception {
        String chat = read(NETWORK.resolve("NetworkChatService.java"));
        assertTrue(chat.contains("publishEvent(n.serverName(), \"broadcast\""));
        assertTrue(chat.contains("deliverBroadcast"),
                "broadcast events need a delivery path on every backend");
        assertTrue(chat.contains("case \"broadcast\""));
        String admin = read(JAVA.resolve("admin/AdminCommand.java"));
        assertTrue(admin.contains("handleNetworkFind"));
        assertTrue(admin.contains("handleNetworkBroadcast"));
        assertTrue(admin.contains("aegis.admin.broadcast"));
    }

    @Test
    void dynamicHubCommandsAreConfigGatedAndCleanedUp() throws Exception {
        String main = read(JAVA.resolve("AegisGuard.java"));
        assertTrue(main.contains("registerAliasCommand(\"hub\""));
        assertTrue(main.contains("registerAliasCommand(\"lobby\""));
        assertTrue(main.contains("getCommandMap()"),
                "top-level aliases must go through the CommandMap");
        assertTrue(main.contains("unregisterAliasCommands"),
                "aliases must be removed on disable so /reload leaves no stale commands");
        assertTrue(main.contains("network.hub.register_commands"));
        // /ag hub + /ag servers exist for players either way
        String cmd = read(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(cmd.contains("handleNetworkHub"));
        assertTrue(cmd.contains("handleNetworkServers"));
        assertTrue(cmd.contains("\"hub\", \"lobby\", \"servers\""));
    }

    @Test
    void newNetworkKeysExistInAllNineLangPacks() throws Exception {
        Path lang = Path.of("src/main/resources/lang");
        List<String> guisKeys = List.of(
                "network_servers_title", "network_server_name", "network_server_self_name",
                "network_server_online", "network_server_offline", "network_server_players",
                "network_server_version", "network_server_self_hint", "network_server_click",
                "network_server_offline_hint", "network_disabled",
                "travel_hub_network_hub", "travel_hub_network_hub_lore",
                "travel_hub_servers", "travel_hub_servers_lore", "back_to_hub_lore",
                "beacon_linked_remote", "beacon_remote_link_hint", "network_broadcast");
        List<String> systemKeys = List.of(
                "admin_network_find_usage", "admin_network_find_unknown",
                "admin_network_find_result", "admin_network_broadcast_usage",
                "admin_network_broadcast_sent", "admin_network_broadcast_fail");
        try (var dirs = Files.list(lang)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String guis = read(dir.resolve("guis.yml"));
                for (String key : guisKeys) {
                    assertTrue(guis.contains(key + ":"),
                            dir.getFileName() + "/guis.yml missing " + key);
                }
                String system = read(dir.resolve("system.yml"));
                for (String key : systemKeys) {
                    assertTrue(system.contains(key + ":"),
                            dir.getFileName() + "/system.yml missing " + key);
                }
            }
        }
    }
}
