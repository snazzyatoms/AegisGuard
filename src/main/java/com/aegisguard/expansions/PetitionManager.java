package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.objects.Estate;
import com.yourname.aegisguard.objects.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PetitionManager (Formerly ExpansionRequestManager)
 * - Handles "Ask an Admin" expansion requests for PRIVATE ESTATES.
 * - Saves requests to 'petitions.yml'.
 */
public class PetitionManager {

    private final AegisGuard plugin;
    // Map: PlayerUUID -> Request Object
    private final Map<UUID, PetitionRequest> activeRequests = new ConcurrentHashMap<>();
    private final File file;
    private FileConfiguration data;
    private volatile boolean isDirty = false;

    public PetitionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "petitions.yml"); // Renamed file
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

    /* -----------------------------
     * REQUEST CREATION (The Application)
     * ----------------------------- */
    public boolean createRequest(Player requester, Estate estate, int newRadius) {
        LanguageManager lang = plugin.getLanguageManager();

        // 1. Ownership Check
        if (estate == null || !estate.getOwnerId().equals(requester.getUniqueId())) {
            requester.sendMessage(lang.getMsg(requester, "no_permission"));
            return false;
        }

        // 2. Pending Check
        if (hasPendingRequest(requester.getUniqueId())) {
            requester.sendMessage(lang.getMsg(requester, "petition_exists")); // "You already have a pending petition."
            return false;
        }

        // 3. Size Check (Must be bigger than current)
        int currentRadius = estate.getRegion().getWidth() / 2;
        if (newRadius <= currentRadius) {
            requester.sendMessage(lang.getMsg(requester, "petition_invalid_size"));
            return false;
        }

        // 4. Global Limit Check
        int maxRadius = plugin.getConfig().getInt("estates.max_radius", 100);
        if (newRadius > maxRadius && !requester.hasPermission("aegis.admin.bypass")) {
            requester.sendMessage(lang.getMsg(requester, "petition_limit_reached")
                .replace("%max%", String.valueOf(maxRadius)));
            return false;
        }

        // 5. Cost Check (If Private Expansion costs money)
        double cost = calculateSmartCost(currentRadius, newRadius);
        if (!plugin.getEconomy().has(requester, cost)) {
            requester.sendMessage(lang.getMsg(requester, "claim_failed_money")
                .replace("%cost%", String.valueOf(cost)));
            return false;
        }

        // 6. Overlap Check (Simulation)
        // Note: We check this again on approval, but good to check now.
        if (isOverlapping(estate, newRadius)) {
            requester.sendMessage(lang.getMsg(requester, "claim_failed_overlap"));
            return false;
        }

        // 7. Submit the Petition
        PetitionRequest request = new PetitionRequest(
                requester.getUniqueId(),
                estate.getId(),
                estate.getWorld().getName(),
                currentRadius,
                newRadius,
                cost
        );

        activeRequests.put(requester.getUniqueId(), request);
        setDirty(true);

        requester.sendMessage(lang.getMsg(requester, "petition_submitted")
            .replace("%cost%", String.valueOf(cost))
            .replace("%size%", newRadius + " blocks"));
            
        // Notify Admins
        // Bukkit.broadcast("§8[§bAegis§8] §eNew Expansion Petition from " + requester.getName(), "aegis.admin");
        
        return true;
    }

    /* -----------------------------
     * APPROVE / DENY (Admin Action)
     * ----------------------------- */
    public boolean approveRequest(PetitionRequest req, Player admin) {
        if (req == null) return false;

        OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
        Estate estate = plugin.getEstateManager().getEstate(req.getEstateId());
        
        if (estate == null) {
            activeRequests.remove(req.getRequester());
            return false; // Estate deleted?
        }

        // 1. Charge Player (Final check)
        if (req.getCost() > 0) {
            if (!plugin.getEconomy().withdraw(requester, req.getCost())) {
                if (admin != null) admin.sendMessage("§cPlayer cannot afford this anymore.");
                return false;
            }
        }

        // 2. Expand Land
        boolean success = plugin.getEstateManager().resizeEstate(estate, "ALL", req.getRequestedRadius() - req.getCurrentRadius());
        
        if (!success) {
            // Refund if resize failed
            if (req.getCost() > 0) plugin.getEconomy().deposit(requester, req.getCost());
            if (admin != null) admin.sendMessage("§cResize failed (Overlap?). Petition kept open.");
            return false;
        }

        // 3. Finalize
        activeRequests.remove(req.getRequester());
        setDirty(true);

        if (requester.isOnline()) {
            plugin.getLanguageManager().sendTitle(requester.getPlayer(), "petition_approved_title", "");
            requester.getPlayer().sendMessage("§aYour petition was approved!");
        }
        return true;
    }

    public void denyRequest(PetitionRequest req, Player admin) {
        activeRequests.remove(req.getRequester());
        setDirty(true);
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(req.getRequester());
        if (target.isOnline()) {
            target.getPlayer().sendMessage("§cYour petition was denied by " + (admin != null ? admin.getName() : "Server"));
        }
    }

    // --- LOGIC ---

    private double calculateSmartCost(int currentRadius, int newRadius) {
        double baseCost = plugin.getConfig().getDouble("expansions.price_per_block", 10.0);
        int blocksAdded = newRadius - currentRadius;
        // Simple linear cost for private estates
        return blocksAdded * baseCost;
    }

    private boolean isOverlapping(Estate estate, int newRadius) {
        // Logic to simulate resize and check for collisions
        // Hook into EstateManager.simulateResize()
        return false; // Placeholder
    }

    // --- PERSISTENCE (petitions.yml) ---

    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    
    public void saveSync() { save(); }

    public synchronized void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            data = YamlConfiguration.loadConfiguration(file);
            activeRequests.clear();

            if (data.isConfigurationSection("requests")) {
                for (String key : data.getConfigurationSection("requests").getKeys(false)) {
                    // Load logic here (same as your old code, just mapping to PetitionRequest)
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public synchronized void save() {
        if (data == null) return;
        data.set("requests", null);
        
        for (PetitionRequest req : activeRequests.values()) {
            String path = "requests." + req.getRequester().toString();
            data.set(path + ".estateId", req.getEstateId().toString());
            data.set(path + ".radius", req.getRequestedRadius());
            data.set(path + ".cost", req.getCost());
        }
        
        try { data.save(file); isDirty = false; } catch (IOException e) { e.printStackTrace(); }
    }
    
    // --- Data Class ---
    public static class PetitionRequest {
        private final UUID requester;
        private final UUID estateId;
        private final String world;
        private final int currentRadius;
        private final int requestedRadius;
        private final double cost;

        public PetitionRequest(UUID requester, UUID estateId, String world, int currentRadius, int requestedRadius, double cost) {
            this.requester = requester;
            this.estateId = estateId;
            this.world = world;
            this.currentRadius = currentRadius;
            this.requestedRadius = requestedRadius;
            this.cost = cost;
        }
        // Getters...
        public UUID getRequester() { return requester; }
        public UUID getEstateId() { return estateId; }
        public int getCurrentRadius() { return currentRadius; }
        public int getRequestedRadius() { return requestedRadius; }
        public double getCost() { return cost; }
    }
}
