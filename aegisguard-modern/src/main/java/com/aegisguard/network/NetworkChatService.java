package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import com.aegisguard.network.NetworkModels.NetworkEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Cross-server chat relay for alliance / group / staff channels.
 *
 * <p>Publish side: after local delivery, the channel's send path appends a
 * {@code chat} row to {@code aegis_network_events} carrying the pre-formatted
 * label, sender, recipient UUID set, and text. Poll side: every second an async
 * task reads events past our cursor, skips our own origin, and delivers to the
 * matching online members on this backend — so relay works with zero players
 * online on either end (DB is the transport). A best-effort {@code Forward}
 * message accelerates delivery when a carrier player is online.</p>
 */
public final class NetworkChatService {

    private final AegisGuard plugin;
    private final AtomicLong cursor = new AtomicLong(-1L);
    private final Set<String> delivered = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private Object pollTask;
    private long lastPrune;

    public NetworkChatService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private NetworkService net() {
        return plugin.network();
    }

    private boolean ready() {
        NetworkService n = net();
        return n != null && n.isNetworked()
                && plugin.getConfig().getBoolean("network.chat.relay_channels", true);
    }

    public void start() {
        if (!ready()) return;
        // Start at the table tail — replays on boot would re-deliver stale chat.
        plugin.scheduler().runAsync(() -> {
            try {
                cursor.set(maxEventId());
            } catch (Throwable t) {
                cursor.set(0L);
            }
        });
        pollTask = plugin.scheduler().runAsyncRepeating(this::poll, 1L, 1L);
    }

    public void stop() {
        if (pollTask != null) {
            try { plugin.scheduler().cancel(pollTask); } catch (Throwable ignored) { }
            pollTask = null;
        }
        delivered.clear();
    }

    // ------------------------------------------------------------------
    // Publish
    // ------------------------------------------------------------------

    /**
     * Relay a sent channel message to other servers. {@code members} are the
     * recipient UUIDs as resolved by the origin server (null → permission-based
     * delivery, used by staff chat).
     */
    public void relay(String channel, String label, Player speaker, Set<UUID> members, String message) {
        if (!ready() || speaker == null || message == null || message.isEmpty()) return;
        NetworkService n = net();
        if (n == null) return;

        Map<String, String> fields = new HashMap<>();
        fields.put("mid", UUID.randomUUID().toString());
        fields.put("channel", channel);
        fields.put("label", label == null ? "" : label);
        fields.put("sender", speaker.getName());
        fields.put("text", message);
        if (members != null) {
            StringBuilder csv = new StringBuilder();
            for (UUID id : members) {
                if (id == null) continue;
                if (csv.length() > 0) csv.append(',');
                csv.append(id);
            }
            fields.put("members", csv.toString());
        }
        String payload = encode(fields);

        plugin.scheduler().runAsync(() -> n.store().publishEvent(n.serverName(), "chat", payload));
        // Best-effort instant delivery when a player can carry the Forward.
        try {
            var out = com.google.common.io.ByteStreams.newDataOutput();
            out.writeUTF("ag_chat");
            out.writeUTF(payload);
            byte[] body = out.toByteArray();
            for (var server : n.remoteServers()) {
                n.forward(server.serverName(), NetworkService.AG_CHANNEL, body);
            }
        } catch (Throwable ignored) { }
    }

    /** Instant-delivery hook for forwarded chat (optional fast path). */
    public void onForwardedChat(Player carrier, DataInputStream in) {
        try {
            String payload = in.readUTF();
            deliver(payload);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Poll + deliver
    // ------------------------------------------------------------------

    private void poll() {
        if (!ready()) return;
        NetworkService n = net();
        if (n == null) return;
        long from = cursor.get();
        if (from < 0) return; // cursor init still in flight — don't replay the table
        try {
            List<NetworkEvent> events = n.store().eventsAfter(from);
            if (events.isEmpty()) {
                maybePrune();
                return;
            }
            long max = from;
            for (NetworkEvent event : events) {
                max = Math.max(max, event.id());
                if (!"chat".equals(event.kind())) continue;
                String origin = event.originServer();
                if (origin != null && origin.equalsIgnoreCase(n.serverName())) continue;
                deliver(event.payload());
            }
            cursor.set(max);
            maybePrune();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[NetworkChat] poll failed: " + t.getMessage());
        }
    }

    private void deliver(String payload) {
        if (payload == null || payload.isEmpty()) return;
        Map<String, String> fields = decode(payload);
        String channel = fields.get("channel");
        String label = fields.get("label");
        String senderName = fields.get("sender");
        String text = fields.get("text");
        if (channel == null || text == null || text.isEmpty()) return;

        // Same message arrives twice when Forward succeeds on top of the DB
        // publish — dedupe on the origin-issued message id.
        String mid = fields.get("mid");
        if (mid != null && !delivered.add(mid)) return;
        if (delivered.size() > 1024) delivered.clear();

        String memberCsv = fields.get("members");
        String ch = channel;
        String lbl = label == null || label.isBlank() ? channel : label;
        String sn = senderName == null ? "?" : senderName;

        // Roster membership resolves here; STAFF resolves per-player below.
        final Set<UUID> targets = new HashSet<>();
        if (memberCsv != null && !memberCsv.isEmpty()) {
            for (String part : memberCsv.split(",")) {
                try { targets.add(UUID.fromString(part.trim())); } catch (IllegalArgumentException ignored) { }
            }
        }
        boolean staffResolve = memberCsv == null && "STAFF".equalsIgnoreCase(ch);

        // Each player is checked + messaged on their own entity thread —
        // hasPermission/sendMessage are region-bound on Folia (same pattern
        // PlotChatService.broadcast uses).
        for (Player online : Bukkit.getOnlinePlayers()) {
            Player target = online;
            plugin.runMain(target, () -> {
                boolean wants = targets.contains(target.getUniqueId())
                        || (staffResolve && (target.hasPermission(com.aegisguard.chat.PlotChatService.PERM_STAFF)
                                || target.hasPermission("aegis.admin.staffchat")
                                || target.isOp()));
                if (!wants || !target.isOnline()) return;
                String line = plugin.plotChat() != null
                        ? plugin.plotChat().formatRelayed(ch, lbl, sn, text, target)
                        : "&8[&b" + lbl + "&8] &f" + sn + "&7: &f" + text;
                target.sendMessage(line);
            });
        }
    }

    // ------------------------------------------------------------------
    // Cursor + pruning
    // ------------------------------------------------------------------

    private long maxEventId() throws Exception {
        try (var c = ((com.aegisguard.data.SQLDataStore) plugin.store()).getConnection();
             var s = c.createStatement();
             var rs = s.executeQuery("SELECT COALESCE(MAX(id),0) FROM aegis_network_events")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private void maybePrune() {
        long now = System.currentTimeMillis();
        if (now - lastPrune < 60_000L) return;
        lastPrune = now;
        try { net().store().pruneEvents(300_000L); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Tiny k=v; codec with \; escaping (matches settings-blob convention)
    // ------------------------------------------------------------------

    private String encode(Map<String, String> fields) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(e.getKey()).append('=').append(escape(e.getValue()));
        }
        return out.toString();
    }

    private Map<String, String> decode(String payload) {
        Map<String, String> out = new HashMap<>();
        if (payload == null) return out;
        StringBuilder cur = new StringBuilder();
        List<String> parts = new ArrayList<>();
        boolean esc = false;
        for (char c : payload.toCharArray()) {
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
}
