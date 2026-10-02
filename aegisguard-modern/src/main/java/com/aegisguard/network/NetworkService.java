package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.network.NetworkModels.NetworkServer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BungeeCord/Velocity network identity. Registers the standard "BungeeCord"
 * plugin channel plus the namespaced "aegisguard:net" channel, maintains the
 * servers-registry heartbeat, and exposes {@link #sendToServer(Player, String)}
 * for proxy hops.
 *
 * <p>Requires {@code network.enabled: true}, a shared MySQL/MariaDB backend
 * (YML/SQLite are per-server and cannot network), and {@code network.server_name}
 * matching this backend's name in the proxy config. On misconfiguration the
 * service logs a warning and disables itself — the plugin keeps working
 * single-server.</p>
 */
public final class NetworkService implements PluginMessageListener {

    public static final String BUNGEE_CHANNEL = "BungeeCord";
    public static final String AG_CHANNEL = "aegisguard:net";

    private final AegisGuard plugin;
    private final NetworkStore store;

    private volatile boolean networked;
    private volatile String serverName;
    private volatile String displayName;
    private volatile List<NetworkServer> servers = List.of();
    private final AtomicBoolean nameVerified = new AtomicBoolean();

    private Object heartbeatTask;

    public NetworkService(AegisGuard plugin) {
        this.plugin = plugin;
        this.store = new NetworkStore(plugin);
    }

    /** Validate config + backend, register channels, start the heartbeat. */
    public void start() {
        if (!plugin.getConfig().getBoolean("network.enabled", false)) {
            networked = false;
            return;
        }

        String backend = resolveBackend();
        boolean sharedSql = "mysql".equals(backend) || "mariadb".equals(backend);
        if (!sharedSql) {
            plugin.getLogger().warning("[Network] network.enabled requires storage backend 'mysql' or 'mariadb' " +
                    "with the SAME database on every server (found '" + backend + "'). Network features disabled.");
            networked = false;
            return;
        }
        if (!(plugin.store() instanceof SQLDataStore)) {
            plugin.getLogger().warning("[Network] network.enabled but the live data store is not SQLDataStore. Network features disabled.");
            networked = false;
            return;
        }

        String configured = plugin.getConfig().getString("network.server_name", "");
        if (configured == null || configured.isBlank()) {
            plugin.getLogger().warning("[Network] network.enabled is true but network.server_name is empty. " +
                    "Set it to this backend's name in the proxy config. Network features disabled.");
            networked = false;
            return;
        }
        serverName = configured.trim();
        String label = plugin.getConfig().getString("network.display_name", "");
        displayName = label == null || label.isBlank() ? serverName : label.trim();

        try {
            var messenger = plugin.getServer().getMessenger();
            messenger.registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            messenger.registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
            messenger.registerOutgoingPluginChannel(plugin, AG_CHANNEL);
            messenger.registerIncomingPluginChannel(plugin, AG_CHANNEL, this);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Network] Failed to register plugin channels: " + t.getMessage());
            networked = false;
            return;
        }

        networked = true;
        plugin.getLogger().info("[Network] Online as '" + serverName + "' (" + displayName + ").");

        // Immediate + recurring heartbeat (SQL — always off the main thread).
        plugin.scheduler().runAsync(this::heartbeatNow);
        long period = Math.max(5L, plugin.getConfig().getLong("network.heartbeat_seconds", 30L));
        heartbeatTask = plugin.scheduler().runAsyncRepeating(this::heartbeatNow, period, period);
    }

    public void stop() {
        networked = false;
        if (heartbeatTask != null) {
            try { plugin.scheduler().cancel(heartbeatTask); } catch (Throwable ignored) { }
            heartbeatTask = null;
        }
        try {
            store.deregisterServer(serverName);
        } catch (Throwable ignored) { }
        try {
            var messenger = plugin.getServer().getMessenger();
            messenger.unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
            messenger.unregisterIncomingPluginChannel(plugin, AG_CHANNEL, this);
            messenger.unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
            messenger.unregisterOutgoingPluginChannel(plugin, AG_CHANNEL);
        } catch (Throwable ignored) { }
    }

    public NetworkStore store() {
        return store;
    }

    public boolean isNetworked() {
        return networked;
    }

    /** This backend's name as configured (should match the proxy's name for us). */
    public String serverName() {
        return serverName;
    }

    public String displayName() {
        return displayName;
    }

    /** All registered servers (cache refreshed by heartbeat). */
    public List<NetworkServer> servers() {
        return servers;
    }

    /** Registered servers other than this one. */
    public List<NetworkServer> remoteServers() {
        List<NetworkServer> out = new ArrayList<>();
        for (NetworkServer s : servers) {
            if (s != null && s.serverName() != null && !s.serverName().equalsIgnoreCase(serverName)) {
                out.add(s);
            }
        }
        return out;
    }

    public NetworkServer findServer(String name) {
        if (name == null) return null;
        for (NetworkServer s : servers) {
            if (s != null && name.equalsIgnoreCase(s.serverName())) return s;
        }
        return null;
    }

    public long offlineAfterMillis() {
        return Math.max(10L, plugin.getConfig().getLong("network.offline_after_seconds", 120L)) * 1000L;
    }

    public boolean isServerOnline(String name) {
        NetworkServer server = findServer(name);
        return server != null && server.isOnline(offlineAfterMillis());
    }

    /** Whether a plot object belongs to another backend. */
    public boolean isRemote(com.aegisguard.data.Plot plot) {
        return networked && plot != null && plot.isRemote(serverName);
    }

    /** Whether a plot belongs to this backend (or networking is off). */
    public boolean isLocal(com.aegisguard.data.Plot plot) {
        return !isRemote(plot);
    }

    // ------------------------------------------------------------------
    // Heartbeat
    // ------------------------------------------------------------------

    private void heartbeatNow() {
        if (!networked) return;
        try {
            store.heartbeat(serverName, displayName,
                    plugin.getDescription().getVersion(),
                    Bukkit.getOnlinePlayers().size());
            List<NetworkServer> fresh = store.servers();
            if (fresh != null) servers = new CopyOnWriteArrayList<>(fresh);
            store.pruneExpiredArrivals();
            store.pruneEvents(300_000L);
            verifyServerName();
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "[Network] heartbeat failed: " + t.getMessage());
        }
    }

    /** Force an immediate registry refresh (admin command / tests). */
    public void refreshServers() {
        try {
            servers = new CopyOnWriteArrayList<>(store.servers());
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "[Network] server refresh failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Proxy hops (Connect / ConnectOther / Forward)
    // ------------------------------------------------------------------

    /** Ask the proxy to move a player to another backend server. */
    public boolean sendToServer(Player player, String targetServer) {
        if (!networked || player == null || targetServer == null || targetServer.isBlank()) return false;
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE,
                    "[Network] Connect send failed for " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Best-effort Forward of a custom payload to a named server via the first
     * online player as carrier. Returns false when nobody is online to relay —
     * callers must treat DB-backed delivery as the reliable path.
     */
    public boolean forward(String targetServer, String subchannel, byte[] payload) {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (!networked || carrier == null) return false;
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Forward");
            out.writeUTF(targetServer);
            out.writeUTF(subchannel);
            out.writeShort(payload.length);
            out.write(payload);
            carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Verify our configured server_name is one the proxy actually knows.
     * Needs a player online to carry the query — safe to call repeatedly.
     */
    public void verifyServerName() {
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (!networked || carrier == null || nameVerified.get()) return;
        // Plugin messages ride the player's connection — entity thread on Folia.
        try {
            plugin.runMain(carrier, () -> {
                if (nameVerified.get() || !networked) return;
                try {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("GetServers");
                    carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
                } catch (Throwable t) {
                    plugin.getLogger().log(java.util.logging.Level.FINE,
                            "[Network] GetServers query failed: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE,
                    "[Network] GetServers query failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // PluginMessageListener
    // ------------------------------------------------------------------

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (BUNGEE_CHANNEL.equals(channel)) {
            handleBungee(message);
        } else if (AG_CHANNEL.equals(channel)) {
            handleAegis(player, message);
        }
    }

    private void handleBungee(byte[] message) {
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String sub = in.readUTF();
            if ("GetServers".equals(sub)) {
                String joined = in.readUTF();
                boolean known = false;
                List<String> names = new ArrayList<>();
                for (String name : joined.split(",")) {
                    String trimmed = name.trim();
                    names.add(trimmed);
                    if (trimmed.equalsIgnoreCase(serverName)) known = true;
                }
                nameVerified.set(true);
                if (!known) {
                    plugin.getLogger().warning("[Network] Proxy does not list a server named '" + serverName +
                            "'. Known servers: " + names + ". Fix network.server_name to match the proxy config.");
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE,
                    "[Network] Bad BungeeCord reply: " + t.getMessage());
        }
    }

    private void handleAegis(Player player, byte[] message) {
        // Forwarded payloads arrive wrapped as raw bytes on this channel when
        // sent via Forward with a custom subchannel. Keep the hook small: the
        // DB event bus is the reliable path; this only accelerates delivery.
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String kind = in.readUTF();
            if ("ag_chat".equals(kind) && plugin.networkChat() != null) {
                plugin.networkChat().onForwardedChat(player, in);
            }
        } catch (EOFException eof) {
            // short/empty payloads are ignorable
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE,
                    "[Network] Bad aegisguard:net message: " + t.getMessage());
        }
    }

    private String resolveBackend() {
        String configured = plugin.getConfig().getString("storage.type");
        if (configured == null || configured.isBlank()) {
            configured = plugin.getConfig().getString("storage.backend", "sqlite");
        }
        String normalized = configured == null ? "sqlite" : configured.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("sql") || normalized.equals("yml")) return "sqlite";
        return normalized;
    }
}
