package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.chat.HearthService;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional Simple Voice Chat hook. Proximity uses ordinary positional voice
 * with no Aegis group. Global, Current World, and (when enabled) Hearth rooms
 * become isolated SVC groups. AegisGuard still runs without Simple Voice Chat.
 * Player-made groups are left alone unless override is on.
 *
 * SVC network callbacks never touch Bukkit player/world APIs. Those hops go
 * through {@code runSync} / {@code runEntity} so Folia region ownership holds.
 */
public final class HearthVoicechatHook implements VoicechatPlugin, Listener {

    public static final String PLUGIN_ID = "aegisguard";
    public static final String GROUP_PREFIX = "AG-Hearth";

    private final AegisGuard plugin;
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastVoiceTarget = new ConcurrentHashMap<>();
    private volatile VoicechatServerApi api;

    public HearthVoicechatHook(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isHookEnabled() {
        return plugin.getConfig().getBoolean("hearth.voicechat", true);
    }

    public boolean overridePlayerGroups() {
        return plugin.getConfig().getBoolean("hearth.voicechat_override_player_groups", false);
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoiceStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            api = null;
            groups.clear();
            lastVoiceTarget.clear();
        });
        registration.registerEvent(PlayerConnectedEvent.class, event -> {
            UUID id = event.getConnection().getPlayer().getUuid();
            plugin.runSync(() -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) refreshLater(player);
            });
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event ->
                lastVoiceTarget.remove(event.getPlayerUuid()));
        registration.registerEvent(JoinGroupEvent.class, event -> scheduleGroupRefresh(event.getConnection()));
        registration.registerEvent(LeaveGroupEvent.class, event -> scheduleGroupRefresh(event.getConnection()));
        registration.registerEvent(RemoveGroupEvent.class, event -> {
            Group removed = event.getGroup();
            if (removed == null || removed.getId() == null) return;
            UUID id = removed.getId();
            groups.entrySet().removeIf(entry ->
                    entry.getValue() != null && id.equals(entry.getValue().getId()));
        });
    }

    private void scheduleGroupRefresh(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) return;
        UUID id = connection.getPlayer().getUuid();
        plugin.runSync(() -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) refreshLater(player);
        });
    }

    private void onVoiceStarted(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();
        plugin.runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshLater(player);
            }
        });
    }

    public void refreshLater(Player player) {
        if (player == null) return;
        plugin.runEntity(player, () -> refresh(player));
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        VoicechatServerApi voice = api;
        if (voice == null) return;
        VoicechatConnection connection = voice.getConnectionOf(player.getUniqueId());
        Group current = connection == null ? null : connection.getGroup();
        if (!isHookEnabled()) {
            if (connection != null && current != null && isOurs(current)) connection.setGroup(null);
            lastVoiceTarget.remove(player.getUniqueId());
            pruneUnusedGroups(voice);
            return;
        }

        VoiceTarget target = resolveTarget(player);
        lastVoiceTarget.put(player.getUniqueId(), target.key());
        if (connection == null) {
            pruneUnusedGroups(voice);
            return;
        }

        if (current != null && !isOurs(current) && !overridePlayerGroups()) {
            pruneUnusedGroups(voice);
            return;
        }

        if (target.kind() == TargetKind.PROXIMITY) {
            if (current != null && isOurs(current)) connection.setGroup(null);
            pruneUnusedGroups(voice);
            return;
        }

        Group targetGroup = groupFor(voice, target);
        if (targetGroup == null) {
            pruneUnusedGroups(voice);
            return;
        }
        if (current == null || current.getId() == null || !current.getId().equals(targetGroup.getId())) {
            connection.setGroup(targetGroup);
        }
        pruneUnusedGroups(voice);
    }

    private void pruneUnusedGroups(VoicechatServerApi voice) {
        if (voice == null) return;
        Set<String> live = new HashSet<>(lastVoiceTarget.values());
        groups.entrySet().removeIf(entry -> {
            if (entry.getKey() != null && live.contains(entry.getKey())) return false;
            Group group = entry.getValue();
            if (group != null && group.getId() != null) {
                voice.removeGroup(group.getId());
            }
            return true;
        });
    }

    private VoiceTarget resolveTarget(Player player) {
        return resolveTarget(player, player.getLocation());
    }

    private VoiceTarget resolveTarget(Player player, Location location) {
        if (plugin.getConfig().getBoolean("hearth.voicechat", true)) {
            HearthService hearth = plugin.hearth();
            HearthService.Room room = hearth == null ? null : hearth.roomAt(location);
            if (room != null) {
                return new VoiceTarget(TargetKind.HEARTH, "hearth:" + roomKey(room), groupName(room));
            }
        }
        return VoiceTarget.proximity();
    }

    private Group groupFor(VoicechatServerApi voice, VoiceTarget target) {
        String key = target.key();
        Group existing = groups.get(key);
        if (existing != null) {
            if (existing.getId() != null && voice.getGroup(existing.getId()) != null) {
                return existing;
            }
            groups.remove(key, existing);
        }
        Group created = voice.groupBuilder()
                .setName(target.name())
                .setPersistent(false)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();
        Group raced = groups.putIfAbsent(key, created);
        if (raced != null && raced != created) {
            if (created.getId() != null) voice.removeGroup(created.getId());
            if (raced.getId() != null && voice.getGroup(raced.getId()) != null) return raced;
            groups.remove(key, raced);
            Group retry = groups.putIfAbsent(key, created);
            return retry == null ? created : retry;
        }
        return created;
    }

    public static boolean isOurs(Group group) {
        if (group == null) return false;
        String name = group.getName();
        return name != null && name.startsWith(GROUP_PREFIX);
    }

    public static String roomKey(HearthService.Room room) {
        if (room == null) return "";
        return room.plotId() + ":" + room.zoneName();
    }

    public static String groupName(HearthService.Room room) {
        String zone = room == null || room.zoneName().isBlank() ? "yard" : room.zoneName();
        String raw = GROUP_PREFIX + " " + zone;
        return bounded(raw);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastVoiceTarget.remove(event.getPlayer().getUniqueId());
        pruneUnusedGroups(api);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) return;
        Player player = event.getPlayer();
        String key = resolveTarget(player, to).key();
        if (key.equals(lastVoiceTarget.get(player.getUniqueId()))) return;
        lastVoiceTarget.put(player.getUniqueId(), key);
        refreshLater(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        lastVoiceTarget.remove(event.getPlayer().getUniqueId());
        refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        lastVoiceTarget.remove(event.getPlayer().getUniqueId());
        refreshLater(event.getPlayer());
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld());
    }

    private static String shortWorld(String world) {
        if (world == null || world.isBlank()) return "World";
        String cleaned = world.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.isBlank() ? "World" : cleaned;
    }

    private static String bounded(String value) {
        if (value == null) return GROUP_PREFIX;
        return value.length() <= 24 ? value : value.substring(0, 24);
    }

    private enum TargetKind { PROXIMITY, HEARTH }

    private record VoiceTarget(TargetKind kind, String key, String name) {
        private static VoiceTarget proximity() {
            return new VoiceTarget(TargetKind.PROXIMITY, "proximity", "");
        }
    }
}
