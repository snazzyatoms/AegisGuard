package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI Expansion for AegisGuard.
 * This class handles all placeholder requests.
 */
public class AegisPAPIExpansion extends PlaceholderExpansion {

    private final AegisGuard plugin;

    public AegisPAPIExpansion(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "aegis"; // The main identifier (e.g., %aegis_...%)
    }

    @Override
    public @NotNull String getAuthor() {
        return "SnazzyAtoms";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * This makes the placeholders persist even if the plugin is reloaded.
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * This is the core logic that handles placeholder requests.
     */
    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        if (offlinePlayer == null) {
            return null;
        }
        // Wallet data is available without an active Player instance.
        if (identifier.equals("claimblocks")) {
            return plugin.getClaimBlockManager() == null ? "0"
                    : String.valueOf(plugin.getClaimBlockManager().getAvailableBlocks(offlinePlayer.getUniqueId()));
        }
        if (!offlinePlayer.isOnline()) return null;
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return null;
        }

        // --- Player-Specific Placeholders ---
        // %aegis_player_plot_count%
        if (identifier.equals("player_plot_count")) {
            return String.valueOf(plugin.store().getPlots(player.getUniqueId()).size());
        }

        // --- Location-Based Placeholders ---
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        String wilderness = "Wilderness"; // Default if not in a plot

        // %aegis_plot_owner%
        if (identifier.equals("plot_owner")) {
            return plot != null ? plot.getOwnerName() : wilderness;
        }

        // %aegis_plot_role%
        if (identifier.equals("plot_role")) {
            if (plot == null) return "N/A";
            return plot.getRole(player.getUniqueId());
        }

        // %aegis_plot_flag_<flagname>%
        if (identifier.startsWith("plot_flag_")) {
            if (plot == null) return "N/A";
            String flag = identifier.substring(10); // Get the part after "plot_flag_"
            boolean enabled = plugin.protection().isFlagEnabled(plot, flag);
            return enabled ? "Enabled" : "Disabled";
        }
        
        // %aegis_plot_status%
        if (identifier.equals("plot_status")) {
            if (plot == null) return wilderness;
            return plot.getPlotStatus();
        }

        if (identifier.equals("realm_name") || identifier.equals("plot_name")) {
            if (plot == null) return wilderness;
            String name = plot.getEntryTitle();
            if (name == null || name.isBlank()) name = plot.getPlotName();
            return name == null || name.isBlank() ? wilderness : name;
        }

        if (identifier.equals("alliance")) {
            if (plot == null || plot.getAllianceId() == null || plugin.alliances() == null) return "None";
            var alliance = plugin.alliances().get(plot.getAllianceId());
            return alliance == null ? "None" : alliance.getName();
        }

        if (identifier.equals("rental_remaining")) {
            if (plot == null || !plot.isRentedBy(player.getUniqueId())) return "0";
            return String.valueOf(Math.max(0L, (plot.getRentEndTime() - System.currentTimeMillis()) / 1000L));
        }

        if (identifier.equals("lockdown")) {
            return plot != null && plot.isLockdownActive() ? "true" : "false";
        }
        
        // %aegis_plot_sale_price%
        if (identifier.equals("plot_sale_price")) {
            if (plot == null || !plot.isForSale()) return "Not for Sale";
            return plugin.vault().format(plot.getSalePrice());
        }

        return null; // Invalid placeholder
    }
}
