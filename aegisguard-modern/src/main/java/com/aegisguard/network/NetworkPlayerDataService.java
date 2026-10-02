package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockData;
import com.aegisguard.notify.PlayerNotificationSettings;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Shared per-player data across the network: claim-block balances and
 * preference settings travel with the player via {@code aegis_player_data}.
 *
 * <p>A player can only be online on one backend at a time, so the hosting
 * server owns that player's row — writes replace the whole blob and readers
 * pull on join. Keys use a {@code key=value;} blob with backslash escaping.</p>
 */
public final class NetworkPlayerDataService {

    private final AegisGuard plugin;
    /** In-memory blob cache for the players currently hosted on this backend. */
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingPush = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPushedBlob = new ConcurrentHashMap<>();
    /**
     * Players whose shared row was pulled this session — i.e. the players this
     * backend OWNS. Writes are only allowed for these ids: pushing stale local
     * data for a player hosted on another server would clobber live rows when
     * bulk save loops (e.g. ClaimBlockManager.save()) push every cached id.
     */
    private final Set<UUID> pullReady = ConcurrentHashMap.newKeySet();
    private Object flushTask;

    public NetworkPlayerDataService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private NetworkService net() {
        return plugin.network();
    }

    public boolean ready() {
        NetworkService n = net();
        return n != null && n.isNetworked()
                && plugin.getConfig().getBoolean("network.shared_player_data.enabled", true);
    }

    /** Periodic flush of queued pushes (2s). */
    public void start() {
        if (!ready()) return;
        flushTask = plugin.scheduler().runAsyncRepeating(this::flushPending, 2L, 2L);
    }

    public void stop() {
        if (flushTask != null) {
            try { plugin.scheduler().cancel(flushTask); } catch (Throwable ignored) { }
            flushTask = null;
        }
        shutdownFlush();
        flushPending();
        cache.clear();
        pendingPush.clear();
        lastPushedBlob.clear();
        pullReady.clear();
    }

    /**
     * Final synchronous write of every locally-tracked player while the SQL pool
     * is still open — onDisable saves claim blocks after this service's flush
     * loop stops, so late pushes would otherwise be dropped.
     */
    private void shutdownFlush() {
        if (!ready()) return;
        try {
            // Only rows this backend owns — players whose shared row was pulled
            // this session. Bulk-flushing every cached id would clobber live
            // rows owned by other backends.
            for (UUID id : pullReady) {
                Map<String, String> fields = cache.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
                collect(id, fields);
                if (!fields.isEmpty()) {
                    String blob = encode(fields);
                    net().store().savePlayerData(id, blob);
                    lastPushedBlob.put(id, blob);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NetworkData] shutdown flush failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Pull on join
    // ------------------------------------------------------------------

    /** Load the shared row for a joining player and apply it locally. */
    public void pullOnJoin(Player player) {
        if (!ready() || player == null) return;
        UUID id = player.getUniqueId();
        plugin.scheduler().runAsync(() -> {
            try {
                String blob = net().store().loadPlayerData(id);
                if (blob != null && !blob.isEmpty()) {
                    Map<String, String> fields = decode(blob);
                    cache.put(id, new ConcurrentHashMap<>(fields));
                    applyLocal(player, fields);
                }
                // Mark only after the pull resolves — writes before ownership is
                // proven could overwrite a live row with unloaded defaults.
                pullReady.add(id);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.FINE, "[NetworkData] pull failed for " + id + ": " + t.getMessage());
            }
        });
    }

    private void applyLocal(Player player, Map<String, String> fields) {
        try {
            if (plugin.getClaimBlockManager() != null && fields.containsKey("claim.earned")) {
                ClaimBlockData data = plugin.getClaimBlockManager().getOrCreate(player.getUniqueId());
                data.setEarnedBlocks(parseLong(fields.get("claim.earned")));
                data.setBoughtBlocks(parseLong(fields.get("claim.bought")));
                data.setBonusBlocks(parseLong(fields.get("claim.bonus")));
                data.setSpentBlocks(parseLong(fields.get("claim.spent")));
                data.setClaimedStarter(Boolean.parseBoolean(fields.getOrDefault("claim.starter", "false")));
                data.setPlaytimeEarningEnabled(Boolean.parseBoolean(fields.getOrDefault("claim.playtime", "true")));
                data.setLandSpendReconciled(Boolean.parseBoolean(fields.getOrDefault("claim.reconciled", "true")));
                String lots = fields.get("claim.lots");
                if (lots != null && !lots.isEmpty()) {
                    data.deserializeLots(java.util.Arrays.asList(lots.split(",")));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NetworkData] claim apply failed: " + t.getMessage());
        }

        try {
            String style = fields.get("pref.style");
            if (style != null && !style.isBlank() && plugin.codex() != null) {
                String applied = style;
                plugin.runMain(player, () -> plugin.codex().setPlayerStyle(player, applied));
            }
        } catch (Throwable ignored) { }

        try {
            if (plugin.notifications() != null) {
                PlayerNotificationSettings settings = plugin.notifications().getSettings(player.getUniqueId());
                boolean touched = false;
                String mode = fields.get("notify.mode");
                if (mode != null && !mode.isBlank()) {
                    settings.setMode(com.aegisguard.notify.NotificationMode.fromString(mode));
                    touched = true;
                }
                if (fields.containsKey("notify.greetings")) {
                    settings.setGreetingsEnabled(Boolean.parseBoolean(fields.get("notify.greetings")));
                    touched = true;
                }
                if (fields.containsKey("notify.admin_updates")) {
                    settings.setAdminUpdatesEnabled(Boolean.parseBoolean(fields.get("notify.admin_updates")));
                    touched = true;
                }
                String arrival = fields.get("notify.arrival");
                if (arrival != null && !arrival.isBlank()) {
                    settings.setPreferredArrival(
                            PlayerNotificationSettings.ArrivalPreference.parse(arrival));
                    touched = true;
                }
                if (touched) plugin.notifications().updateSettings(settings);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NetworkData] notify apply failed: " + t.getMessage());
        }
    }

    public void evictOnQuit(UUID playerId) {
        if (playerId == null) return;
        pendingPush.remove(playerId);
        cache.remove(playerId);
        lastPushedBlob.remove(playerId);
        // No longer hosted here — later bulk saves must not rewrite this row.
        pullReady.remove(playerId);
    }

    // ------------------------------------------------------------------
    // Push on change
    // ------------------------------------------------------------------

    /** Queue the player's claim-block + preference state for a network write. */
    public void push(UUID playerId) {
        if (!ready() || playerId == null || !pullReady.contains(playerId)) return;
        Map<String, String> fields = cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        collect(playerId, fields);
        // Skip the queue when nothing changed since the last write — the bulk
        // save path calls this for every cached player.
        String blob = encode(fields);
        if (blob.equals(lastPushedBlob.get(playerId))) return;
        pendingPush.put(playerId, System.currentTimeMillis());
    }

    private void collect(UUID playerId, Map<String, String> fields) {
        try {
            if (plugin.getClaimBlockManager() != null) {
                // getCached, not getOrCreate — fabricating an empty ledger here
                // would write zeros over a live shared row.
                ClaimBlockData data = plugin.getClaimBlockManager().getCached(playerId);
                if (data != null) {
                    fields.put("claim.earned", String.valueOf(data.getEarnedBlocks()));
                    fields.put("claim.bought", String.valueOf(data.getBoughtBlocks()));
                    fields.put("claim.bonus", String.valueOf(data.getBonusBlocks()));
                    fields.put("claim.spent", String.valueOf(data.getSpentBlocks()));
                    fields.put("claim.starter", String.valueOf(data.hasClaimedStarter()));
                    fields.put("claim.playtime", String.valueOf(data.isPlaytimeEarningEnabled()));
                    fields.put("claim.reconciled", String.valueOf(data.isLandSpendReconciled()));
                    List<String> lots = data.serializeLots();
                    fields.put("claim.lots", lots == null ? "" : String.join(",", lots));
                }
            }
        } catch (Throwable ignored) { }

        try {
            if (plugin.notifications() != null) {
                PlayerNotificationSettings s = plugin.notifications().getSettings(playerId);
                fields.put("notify.mode", String.valueOf(s.getMode()));
                fields.put("notify.greetings", String.valueOf(s.isGreetingsEnabled()));
                fields.put("notify.admin_updates", String.valueOf(s.isAdminUpdatesEnabled()));
                fields.put("notify.arrival", String.valueOf(s.getPreferredArrival()));
            }
        } catch (Throwable ignored) { }

        try {
            if (plugin.codex() != null) {
                Player online = plugin.getServer().getPlayer(playerId);
                if (online != null) {
                    String style = plugin.codex().getPlayerStyle(online);
                    if (style != null && !style.isBlank()) fields.put("pref.style", style);
                }
            }
        } catch (Throwable ignored) { }
    }

    /** Immediately update one preference key + queue the write. */
    public void set(UUID playerId, String key, String value) {
        if (!ready() || playerId == null || key == null || !pullReady.contains(playerId)) return;
        cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(key, value == null ? "" : value);
        pendingPush.put(playerId, System.currentTimeMillis());
    }

    private void flushPending() {
        if (!ready() || pendingPush.isEmpty()) return;
        Map<UUID, Long> batch = new HashMap<>(pendingPush);
        pendingPush.keySet().removeAll(batch.keySet());
        for (UUID id : batch.keySet()) {
            if (!pullReady.contains(id)) continue;
            Map<String, String> fields = cache.get(id);
            if (fields == null) continue;
            String blob = encode(fields);
            try {
                net().store().savePlayerData(id, blob);
                lastPushedBlob.put(id, blob);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.FINE, "[NetworkData] push failed for " + id + ": " + t.getMessage());
            }
        }
    }

    /** Flush a single player immediately (e.g. before a proxy hop). */
    public void pushNow(UUID playerId) {
        if (!ready() || playerId == null || !pullReady.contains(playerId)) return;
        Map<String, String> fields = cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        collect(playerId, fields);
        String blob = encode(fields);
        plugin.scheduler().runAsync(() -> {
            try {
                net().store().savePlayerData(playerId, blob);
                lastPushedBlob.put(playerId, blob);
            } catch (Throwable ignored) { }
        });
    }

    // ------------------------------------------------------------------
    // Blob codec (key=value; with backslash escaping)
    // ------------------------------------------------------------------

    private String encode(Map<String, String> fields) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(e.getKey()).append('=').append(escape(e.getValue()));
        }
        return out.toString();
    }

    private Map<String, String> decode(String blob) {
        Map<String, String> out = new HashMap<>();
        if (blob == null) return out;
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (char c : blob.toCharArray()) {
            if (esc) { cur.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == ';') { parts.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        parts.add(cur.toString());
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            out.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return out;
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace(";", "\\;");
    }

    private long parseLong(String raw) {
        try { return Long.parseLong(raw); } catch (Throwable t) { return 0L; }
    }
}
