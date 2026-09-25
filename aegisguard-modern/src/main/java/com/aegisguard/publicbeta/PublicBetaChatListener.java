package com.aegisguard.publicbeta;

import com.aegisguard.AegisGuard;
import com.aegisguard.publicbeta.PublicBetaWorldService.Role;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adds a cached, localized origin tag to ordinary Public Beta text chat. */
public final class PublicBetaChatListener implements Listener {

    private static final String ROOT = "public-beta-mode";
    private final AegisGuard plugin;
    private final Map<UUID, String> tags = new ConcurrentHashMap<>();

    public PublicBetaChatListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void refresh(Player player) {
        if (player == null || !enabled()) return;
        tags.put(player.getUniqueId(), tag(player, player.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled() || event.getTo() == null || event.getTo().getWorld() == null) return;
        tags.put(event.getPlayer().getUniqueId(), tag(event.getPlayer(), event.getTo().getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tags.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPublicChat(AsyncPlayerChatEvent event) {
        if (!enabled()) return;
        String tag = tags.get(event.getPlayer().getUniqueId());
        if (tag == null || tag.isBlank()) return;
        event.setFormat(tag + event.getFormat());
    }

    private boolean enabled() {
        return plugin.publicBeta() != null && plugin.publicBeta().isEnabled()
                && plugin.getConfig().getBoolean(ROOT + ".text-chat-world-tags.enabled", true);
    }

    private String tag(Player player, String worldName) {
        PublicBetaWorldService worlds = plugin.publicBetaWorlds();
        if (worlds == null || worldName == null) return "";
        if (worldName.equalsIgnoreCase(worlds.worldName(Role.WELCOME_HUB))) {
            return color(tr(player, "public_beta_chat_tag_hub", "&8[&bHub&8]&r "));
        }
        if (worldName.equalsIgnoreCase(worlds.worldName(Role.PLAY_WORLD))) {
            return color(tr(player, "public_beta_chat_tag_play", "&8[&aPlay&8]&r "));
        }
        if (worldName.equalsIgnoreCase(worlds.worldName(Role.TEST_LAB))) {
            return color(tr(player, "public_beta_chat_tag_lab", "&8[&dTest Lab&8]&r "));
        }
        return "";
    }

    private String tr(Player player, String key, String fallback) {
        String value = plugin.gui().tr(player, key, fallback);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
