package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.PetitionRequest;
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

    public Collection<PetitionRequest> getActiveRequests() {
        return Collections.unmodifiableCollection(activeRequests.values());
    }

    public PetitionRequest getRequest(UUID requesterId) {
        return activeRequests.get(requesterId);
    }
    
    public boolean hasPendingRequest(UUID requesterId) {
        return activeRequests.containsKey(requesterId);
    }

    public boolean createRequest(Player requester, Estate estate, int newRadius) {
        LanguageManager lang = plugin.getLanguageManager();
        if (estate == null || !estate.getOwnerId().equals(requester.getUniqueId())) {
            requester.sendMessage(lang.getMsg(requester, "no_permission"));
            return false;
        }
        if (hasPendingRequest(requester.getUniqueId())) {
            requester.sendMessage(lang.getMsg(requester, "petition_exists"));
            return false;
        }
        // Auto-Approve check for PTS
        boolean autoApprove = plugin.getConfig().getBoolean("expansions.auto_approve", false);
        int currentRadius = estate.getRegion().getWidth() / 2;
        double cost = calculateSmartCost(currentRadius, newRadius);

        if (autoApprove) {
            if (!plugin.getEconomy().withdraw(requester, cost)) {
                 requester.sendMessage(lang.getMsg(requester, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
                 return false;
            }
            plugin.getEstateManager().resizeEstate(estate, "ALL", newRadius - currentRadius);
            requester.sendMessage("§a[PTS] Expansion Auto-Approved!");
            return true;
        }

        PetitionRequest request = new PetitionRequest(requester.getUniqueId(), estate.getOwnerId(), estate.getId(), estate.getWorld().getName(), currentRadius, newRadius, cost);
        activeRequests.put(requester.getUniqueId(), request);
        setDirty(true);
        requester.sendMessage(lang.getMsg(requester, "petition_submitted").replace("%cost%", String.valueOf(cost)));
        return true;
    }

    public boolean approveRequest(PetitionRequest req, Player admin) {
        if (req == null) return false;
        Estate estate = plugin.getEstateManager().getEstate(req.getEstateId());
        if (estate == null) { activeRequests.remove(req.getRequester()); return false; }
        
        // Logic to expand
        boolean success = plugin.getEstateManager().resizeEstate(estate, "ALL", req.getRequestedRadius() - req.getCurrentRadius());
        if (success) {
            req.approve();
            activeRequests.remove(req.getRequester());
            setDirty(true);
            return true;
        }
        return false;
    }

    public void denyRequest(PetitionRequest req, Player admin) {
        if (req == null) return;
        req.deny();
        activeRequests.remove(req.getRequester());
        setDirty(true);
    }

    private double calculateSmartCost(int current, int target) {
        return (target - current) * plugin.getConfig().getDouble("expansions.price_per_block", 10.0);
    }

    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }

    public synchronized void load() {
        // Load logic... (Simplified for brevity, ensure you have the load logic)
    }
    public synchronized void save() {
        // Save logic...
        isDirty = false;
    }
}
