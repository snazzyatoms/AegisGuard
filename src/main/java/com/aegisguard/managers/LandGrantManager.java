package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class LandGrantManager {

    private final AegisGuard plugin;

    public LandGrantManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Entry Point: Guild Leader wants to expand.
     */
    public void processExpansion(Player leader, Estate estate, int amount, String direction) {
        double cost = calculateGrantCost(estate, amount);
        
        // 1. Check Guild
        // In v1.3.0, we need to ensure we are getting the guild correctly
        Guild guild = null;
        if (estate.isGuild()) {
            guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
        }

        if (guild == null) {
             leader.sendMessage(ChatColor.RED + "❌ This estate does not belong to a valid Guild.");
             return;
        }

        // 2. Check Treasury
        if (guild.getBalance() < cost) {
            leader.sendMessage(ChatColor.RED + "✖ The Guild Treasury is insufficient.");
            leader.sendMessage(ChatColor.RED + "Required: $" + String.format("%.2f", cost) + 
                             " | Current: $" + String.format("%.2f", guild.getBalance()));
            return;
        }

        // 3. Execute Transaction
        guild.withdraw(cost);
        
        // 4. Expand the Land
        boolean success = plugin.getEstateManager().resizeEstate(estate, direction, amount);
        
        if (success) {
            leader.sendMessage(ChatColor.GREEN + "✔ Land Grant Signed!");
            leader.sendMessage(ChatColor.GREEN + "   Expanded " + amount + " blocks " + direction);
            leader.sendMessage(ChatColor.YELLOW + "   Treasury Deducted: $" + String.format("%.2f", cost));
            leader.playSound(leader.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            // Refund if resize failed
            guild.deposit(cost);
            leader.sendMessage(ChatColor.RED + "✖ Expansion failed: Overlap detected. Funds refunded.");
        }
    }

    public double calculateGrantCost(Estate estate, int blocksAdded) {
        double basePrice = plugin.getConfig().getDouble("expansions.price_per_block", 10.0);
        long area = estate.getRegion().getArea();
        double multiplier = 1.0;
        
        if (area > 5000) {
            multiplier = plugin.getConfig().getDouble("expansions.scaling.large.multiplier", 2.0);
        } else if (area > 2000) {
            multiplier = plugin.getConfig().getDouble("expansions.scaling.medium.multiplier", 1.5);
        }

        return (blocksAdded * basePrice) * multiplier;
    }
}
