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
}
