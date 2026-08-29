package com.aegisguard.chat;

import com.aegisguard.AegisGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlotChatListener implements Listener {

    private final AegisGuard plugin;

    public PlotChatListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        PlotChatService chat = plugin.plotChat();
        if (chat == null) return;
        Player player = event.getPlayer();
        if (!chat.interceptPublicChat(player)) return;
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.runMain(player, () -> chat.sendActive(player, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.plotChat() != null) plugin.plotChat().clearPlayer(event.getPlayer().getUniqueId());
    }
}
