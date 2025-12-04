package com.yourname.aegisguard.listeners;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.hooks.DiscordWebhook;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BannedPlayerListener
 * - Automatically deletes Estates if the owner is banned.
 * - Handles both online bans (Quit) and offline bans (PreLogin).
 * - Updated for v1.3.0 Estate System.
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
     * Central logic to wipe estates safely.
     */
    private void processBanWipe(UUID uuid, String name) {
        if (!plugin.cfg().autoRemoveBannedPlots()) return;

        plugin.runGlobalAsync(() -> {
            // v1.3.0: Use EstateManager to fetch data
            List<Estate> estates = plugin.getEstateManager().getEstates(uuid);
            
            if (estates != null && !estates.isEmpty()) {
                // Create copy to avoid concurrent modification exceptions during iteration
                List<Estate> toRemove = new ArrayList<>(estates);
                int count = toRemove.size();
                
                for (Estate estate : toRemove) {
                    // v1.3.0: Use EstateManager to delete (Handles Cache + DB)
                    plugin.getEstateManager().deleteEstate(estate.getId());
                }
                
                plugin.getLogger().warning("[AegisGuard] Banned Player Detected: " + name);
                plugin.getLogger().info("[AegisGuard] Auto-removed " + count + " estates belonging to " + name);

                // --- Discord Logging ---
                if (plugin.getDiscord().isEnabled()) {
                    DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                        .setTitle("🚫 Banned Player Wipe")
                        .setColor(Color.RED)
                        .setDescription("Player **" + name + "** was detected as banned. Their land has been seized.")
                        .addField("Action", "All Estates removed", true)
                        .addField("Count", String.valueOf(count), true)
                        .setFooter("AegisGuard Automation", null);
                    
                    plugin.getDiscord().send(embed);
                }
            }
        });
    }
}
