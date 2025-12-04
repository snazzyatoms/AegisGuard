package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AegisPAPIExpansion extends PlaceholderExpansion {

    private final AegisGuard plugin;

    public AegisPAPIExpansion(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "aegis";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SnazzyAtoms";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String identifier) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return null;
        Player player = offlinePlayer.getPlayer();
        if (player == null) return null;

        // --- Player-Specific Placeholders ---
        
        // %aegis_player_estate_count%
        if (identifier.equals("player_estate_count")) {
            return String.valueOf(plugin.getEstateManager().getEstates(player.getUniqueId()).size());
        }
        
        // %aegis_player_guild_name%
        if (identifier.equals("player_guild_name")) {
            Guild guild = plugin.getAllianceManager().getPlayerGuild(player.getUniqueId());
            return (guild != null) ? guild.getName() : "None";
        }
        
        // %aegis_player_guild_role%
        if (identifier.equals("player_guild_role")) {
            Guild guild = plugin.getAllianceManager().getPlayerGuild(player.getUniqueId());
            return (guild != null) ? guild.getMemberRole(player.getUniqueId()) : "None";
        }

        // --- Location-Based Placeholders (Standing In) ---
        
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        String wilderness = "Wilderness";

        // %aegis_estate_owner%
        if (identifier.equals("estate_owner")) {
            if (estate == null) return wilderness;
            return Bukkit.getOfflinePlayer(estate.getOwnerId()).getName();
        }

        // %aegis_estate_name%
        if (identifier.equals("estate_name")) {
            return estate != null ? estate.getName() : wilderness;
        }

        // %aegis_estate_role% (Your role in the current land)
        if (identifier.equals("estate_role")) {
            if (estate == null) return "N/A";
            return estate.getMemberRole(player.getUniqueId());
        }
        
        // %aegis_estate_level%
        if (identifier.equals("estate_level")) {
            return estate != null ? String.valueOf(estate.getLevel()) : "0";
        }
        
        // %aegis_estate_balance% (Ledger)
        if (identifier.equals("estate_balance")) {
            return estate != null ? String.format("%.2f", estate.getBalance()) : "0.00";
        }

        // %aegis_estate_flag_<flag>%
        if (identifier.startsWith("estate_flag_")) {
            if (estate == null) return "N/A";
            String flag = identifier.substring(12); // remove "estate_flag_"
            return estate.getFlag(flag) ? "Enabled" : "Disabled";
        }
        
        // %aegis_estate_sale_price%
        if (identifier.equals("estate_sale_price")) {
            if (estate == null || !estate.isForSale()) return "Not for Sale";
            return String.format("%.2f", estate.getSalePrice());
        }

        return null; 
    }
}
