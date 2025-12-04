package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.objects.Estate;
import com.yourname.aegisguard.objects.PetitionRequest;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PetitionManager {

    private final AegisGuard plugin;
    private final Map<UUID, PetitionRequest> activeRequests = new ConcurrentHashMap<>();
    private final File file;
    private FileConfiguration data;
    private volatile boolean isDirty = false;

    public PetitionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "petitions.yml");
        load();
    }

    // ... [Keep existing getters: getActiveRequests, getRequest, hasPendingRequest] ...

    /* -----------------------------
     * REQUEST CREATION (Updated for PTS)
     * ----------------------------- */
    public boolean createRequest(Player requester, Estate estate, int newRadius) {
        LanguageManager lang = plugin.getLanguageManager();

        // 1. Ownership Check
        if (estate == null || !estate.getOwnerId().equals(requester.getUniqueId())) {
            requester.sendMessage(lang.getMsg(requester, "no_permission"));
            return false;
        }

        // 2. Pending Check (Skip if auto-approve is on)
        boolean autoApprove = plugin.getConfig().getBoolean("expansions.auto_approve", false);
        
        if (!autoApprove && hasPendingRequest(requester.getUniqueId())) {
            requester.sendMessage(lang.getMsg(requester, "petition_exists"));
            return false;
        }

        // 3. Size Check
        int currentRadius = estate.getRegion().getWidth() / 2;
        if (newRadius <= currentRadius) {
            requester.sendMessage(lang.getMsg(requester, "petition_invalid_size"));
            return false;
        }

        // 4. Limit Check (Admins or PTS bypass)
        int maxRadius = plugin.getConfig().getInt("estates.max_radius", 100);
        if (newRadius > maxRadius && !requester.hasPermission("aegis.admin.bypass")) {
            requester.sendMessage(lang.getMsg(requester, "petition_limit_reached").replace("%max%", String.valueOf(maxRadius)));
            return false;
        }

        // 5. Cost Check
        double cost = calculateSmartCost(currentRadius, newRadius);
        if (!plugin.getEconomy().has(requester, cost)) {
            requester.sendMessage(lang.getMsg(requester, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
            return false;
        }

        // --- PTS: AUTO-APPROVE LOGIC ---
        if (autoApprove) {
            // 1. Take Money
            plugin.getEconomy().withdraw(requester, cost);
            
            // 2. Expand Immediately
            boolean success = plugin.getEstateManager().resizeEstate(estate, "ALL", newRadius - currentRadius);
            
            if (success) {
                requester.sendMessage("§a[PTS] §eExpansion Auto-Approved!");
                requester.sendMessage("§7Expanded to " + newRadius + " blocks radius.");
                // plugin.effects().playConfirm(requester);
                return true;
            } else {
                // Refund if overlap
                plugin.getEconomy().deposit(requester, cost);
                requester.sendMessage(lang.getMsg(requester, "claim_failed_overlap"));
                return false;
            }
        }
        // -------------------------------

        // Standard Logic (Create Ticket)
        PetitionRequest request = new PetitionRequest(
                requester.getUniqueId(), estate.getOwnerId(), estate.getId(),
                estate.getWorld().getName(), currentRadius, newRadius, cost
        );

        activeRequests.put(requester.getUniqueId(), request);
        setDirty(true);

        requester.sendMessage(lang.getMsg(requester, "petition_submitted")
            .replace("%cost%", String.valueOf(cost))
            .replace("%size%", newRadius + " blocks"));
            
        return true;
    }

    // ... [Keep existing approveRequest, denyRequest, calculateSmartCost, save/load methods] ...
    // (Copy them from your previous file, they don't need changing)
    
    // Ensure you keep this helper for the code above to work:
    private double calculateSmartCost(int currentRadius, int newRadius) {
        double baseCost = plugin.getConfig().getDouble("expansions.price_per_block", 10.0);
        return (newRadius - currentRadius) * baseCost;
    }
    
    // ... [Keep Persistence Logic] ...
}
