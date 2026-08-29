package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.chat.HearthService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional Simple Voice Chat hook. When the {@code voicechat} plugin is present
 * and Hearth is on, each Hearth room becomes an isolated SVC group. AegisGuard
 * still runs without Simple Voice Chat. Player-made groups are left alone unless
 * {@code hearth.voicechat_override_player_groups} is true.
 *
 * SVC network callbacks never touch Bukkit player/world APIs. Those hops go
 * through {@code runSync} / {@code runEntity} so Folia region ownership holds.
 */
public final class HearthVoicechatHook implements VoicechatPlugin, Listener {

    public static final String PLUGIN_ID = "aegisguard";
    public static final String GROUP_PREFIX = "AG-Hearth";

    private final AegisGuard plugin;
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastVoiceRoom = new ConcurrentHashMap<>();
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
            lastVoiceRoom.clear();
        });
        registration.registerEvent(PlayerConnectedEvent.class, event -> {
            UUID id = event.getConnection().getPlayer().getUuid();
            plugin.runSync(() -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) refreshLater(player);
            });
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event ->
                lastVoiceRoom.remove(event.getPlayerUuid()));
        registration.registerEvent(RemoveGroupEvent.class, event -> {
            Group removed = event.getGroup();
            if (removed == null || removed.getId() == null) return;
            UUID id = removed.getId();
            groups.entrySet().removeIf(entry ->
                    entry.getValue() != null && id.equals(entry.getValue().getId()));
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
        if (connection == null) return;

        Group current = connection.getGroup();
        if (!isHookEnabled()) {
            if (current != null && isOurs(current)) connection.setGroup(null);
            lastVoiceRoom.remove(player.getUniqueId());
            return;
        }

        if (current != null && !isOurs(current) && !overridePlayerGroups()) {
            return;
        }

        HearthService hearth = plugin.hearth();
        HearthService.Room room = hearth == null ? null : hearth.roomOf(player);
        lastVoiceRoom.put(player.getUniqueId(), roomKey(room));
        if (room == null) {
            if (current != null && isOurs(current)) connection.setGroup(null);
            return;
        }

        Group target = groupFor(voice, room);
        if (target == null) return;
        if (current == null || current.getId() == null || !current.getId().equals(target.getId())) {
            connection.setGroup(target);
        }
    }

    private Group groupFor(VoicechatServerApi voice, HearthService.Room room) {
        String key = roomKey(room);
        Group existing = groups.get(key);
        if (existing != null) {
            if (existing.getId() != null && voice.getGroup(existing.getId()) != null) {
                return existing;
            }
            groups.remove(key, existing);
        }
        Group created = voice.groupBuilder()
                .setName(groupName(room))
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
        if (raw.length() <= 24) return raw;
        return raw.substring(0, 24);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastVoiceRoom.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) return;
        Player player = event.getPlayer();
        HearthService hearth = plugin.hearth();
        HearthService.Room next = hearth == null ? null : hearth.roomAt(to);
        String key = roomKey(next);
        if (key.equals(lastVoiceRoom.get(player.getUniqueId()))) return;
        lastVoiceRoom.put(player.getUniqueId(), key);
        refreshLater(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        lastVoiceRoom.remove(event.getPlayer().getUniqueId());
        refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        lastVoiceRoom.remove(event.getPlayer().getUniqueId());
        refreshLater(event.getPlayer());
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld());
    }
}
