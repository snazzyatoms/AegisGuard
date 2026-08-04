package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.hooks.DiscordWebhook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                List<Plot> toRemove = new ArrayList<>(plots);
                int count = toRemove.size();

                for (Plot plot : toRemove) {
                    plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                }

                plugin.console().warning("log_banned_player_detected",
                        "[AegisGuard] Banned Player Detected: {PLAYER}",
                        "PLAYER", name == null ? "" : name);
                plugin.console().info("log_banned_plots_removed",
                        "[AegisGuard] Auto-removed {COUNT} plots belonging to {PLAYER}",
                        "COUNT", String.valueOf(count),
                        "PLAYER", name == null ? "" : name);

                if (plugin.getDiscord().isEnabled()) {
                    DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                        .setTitle(plugin.console().plain("discord_ban_wipe_title", "Banned Player Wipe"))
                        .setColor(Color.RED)
                        .setDescription(plugin.console().plain(
                                "discord_ban_wipe_description",
                                "Player **{PLAYER}** was detected as banned. Their land has been seized.",
                                Map.of("PLAYER", name == null ? "" : name)))
                        .addField(
                                plugin.console().plain("discord_ban_wipe_action_name", "Action"),
                                plugin.console().plain("discord_ban_wipe_action_value", "All plots removed"),
                                true)
                        .addField(
                                plugin.console().plain("discord_ban_wipe_count_name", "Count"),
                                String.valueOf(count),
                                true)
                        .setFooter(plugin.console().plain("discord_ban_wipe_footer", "AegisGuard Automation"), null);

                    plugin.getDiscord().send(embed);
                }
            }
        });
    }
}
