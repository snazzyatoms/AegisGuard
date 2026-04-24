package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.hooks.DiscordWebhook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.Color; // --- ADDED IMPORT ---
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BannedPlayerListener
 * - Automatically deletes plots if the owner is banned.
 * - Handles both online bans (Quit) and offline bans (PreLogin).
 */
public class BannedPlayerListener implements Listener {

    private final AegisGuard plugin;

    public BannedPlayerListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Scenario 1: Player is banned while online and gets kicked/quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (e.getPlayer().isBanned()) {
            processBanWipe(e.getPlayer().getUniqueId(), e.getPlayer().getName());
        }
    }

    /**
     * Scenario 2: Player was banned via console while offline and tries to join.
     */
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {
            processBanWipe(e.getUniqueId(), e.getName());
        }
    }

    /**
     * Central logic to wipe plots safely.
     */
    private void processBanWipe(UUID uuid, String name) {
        if (!plugin.cfg().autoRemoveBannedPlots()) return;

        plugin.runGlobalAsync(() -> {
            List<Plot> plots = plugin.store().getPlots(uuid);
            
            if (plots != null && !plots.isEmpty()) {
                // Create copy to avoid concurrent modification exceptions during iteration
                List<Plot> toRemove = new ArrayList<>(plots);
                int count = toRemove.size();
                
                for (Plot plot : toRemove) {
                    // This call handles DB/YML deletion asynchronously
                    plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                }
                
                plugin.getLogger().warning("[AegisGuard] Banned Player Detected: " + name);
                plugin.getLogger().info("[AegisGuard] Auto-removed " + count + " plots belonging to " + name);

                // --- v1.1.2 Feature: Discord Logging ---
                if (plugin.getDiscord().isEnabled()) {
                    DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                        .setTitle("🚫 Banned Player Wipe")
                        .setColor(Color.RED) // FIXED: Use java.awt.Color object
                        .setDescription("Player **" + name + "** was detected as banned. Their land has been seized.")
                        .addField("Action", "All plots removed", true)
                        .addField("Count", String.valueOf(count), true)
                        .setFooter("AegisGuard Automation", null);
                    
                    plugin.getDiscord().send(embed);
                }
            }
        });
    }
}
