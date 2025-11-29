package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExpansionRequestManager
 * - Handles land expansion requests.
 * - Fully localized.
 */
public class ExpansionRequestManager {

    private final AegisGuard plugin;
    private final Map<UUID, ExpansionRequest> activeRequests = new ConcurrentHashMap<>();
    private final File file;
    private FileConfiguration data;
    private volatile boolean isDirty = false;

    public ExpansionRequestManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "expansion-requests.yml");
    }

    public Collection<ExpansionRequest> getActiveRequests() {
        return Collections.unmodifiableCollection(activeRequests.values());
    }

    public ExpansionRequest getRequest(UUID requesterId) {
        return activeRequests.get(requesterId);
    }
    
    public boolean hasPendingRequest(UUID requesterId) {
        return activeRequests.containsKey(requesterId);
    }

    /* -----------------------------
     * REQUEST CREATION
     * ----------------------------- */
    public boolean createRequest(Player requester, Plot plot, int newRadius) {
        if (plot == null || !plot.getOwner().equals(requester.getUniqueId())) {
            plugin.msg().send(requester, "no_perm");
            return false;
        }

        if (hasPendingRequest(requester.getUniqueId())) {
            plugin.msg().send(requester, "expansion_exists"); 
            return false;
        }

        // 1. Size Check
        int currentRadius = (plot.getX2() - plot.getX1()) / 2;
        if (newRadius <= currentRadius) {
            plugin.msg().send(requester, "expansion_invalid_size"); // "New size must be larger."
            return false;
        }

        // 2. Limit Check
        int maxRadius = plugin.cfg().raw().getInt("expansions.max_radius_global", 100);
        if (newRadius > maxRadius && !requester.hasPermission("aegis.admin.bypass-limits")) {
            plugin.msg().send(requester, "expansion_limit_reached", Map.of("LIMIT", String.valueOf(maxRadius)));
            return false;
        }

        // 3. Cost Check
        double cost = calculateSmartCost(currentRadius, newRadius);
        CurrencyType type = CurrencyType.VAULT; 
        
        if (!plugin.eco().has(requester, cost, type)) {
            plugin.msg().send(requester, "expansion_payment_failed"); // Reusing or creating "insufficient_funds"
            return false;
        }

        // 4. Overlap Check
        if (isOverlapping(plot, newRadius)) {
            plugin.msg().send(requester, "expansion_overlap_fail");
            return false;
        }

        // 5. Submit
        ExpansionRequest request = new ExpansionRequest(
                requester.getUniqueId(),
                plot.getOwner(),
                plot.getPlotId(),
                requester.getWorld().getName(),
                currentRadius,
                newRadius,
                cost
        );

        activeRequests.put(requester.getUniqueId(), request);
        setDirty(true);

        Map<String, String> placeholders = Map.of(
                "PLAYER", requester.getName(),
                "AMOUNT", plugin.eco().format(cost, type),
                "SIZE", newRadius + " blocks"
        );

        plugin.msg().send(requester, "expansion_submitted", placeholders);
        return true;
    }

    /* -----------------------------
     * APPROVE / DENY
     * ----------------------------- */
    public boolean approveRequest(ExpansionRequest req) {
        if (req == null) return false;

        OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
        CurrencyType type = CurrencyType.VAULT;

        // 1. Charge Player
        Player p = requester.getPlayer();
        if (p != null) {
             if (!plugin.eco().withdraw(p, req.getCost(), type)) {
                 denyRequest(req);
                 plugin.msg().send(p, "expansion_payment_failed");
                 return false;
             }
        } else {
             // Offline charge via Vault
             if (plugin.cfg().useVault()) {
                 if (!plugin.vault().charge(requester, req.getCost())) {
                     denyRequest(req);
                     return false;
                 }
             }
        }

        // 2. Get Plot
        Plot oldPlot = plugin.store().getPlot(req.getPlotOwner(), req.getPlotId());
        if (oldPlot == null) {
            if (p != null) plugin.eco().deposit(p, req.getCost(), type);
            else if (plugin.cfg().useVault()) plugin.vault().give(requester, req.getCost());
            
            denyRequest(req);
            return false;
        }

        // 3. Apply Expansion
        if (!applyExpansion(oldPlot, req.getRequestedRadius())) {
            if (p != null) plugin.eco().deposit(p, req.getCost(), type);
            else if (plugin.cfg().useVault()) plugin.vault().give(requester, req.getCost());
            
            denyRequest(req);
            plugin.getLogger().warning("Expansion failed due to overlap during approval.");
            return false;
        }

        req.approve();
        activeRequests.remove(req.getRequester());
        setDirty(true);

        if (p != null) {
            plugin.msg().send(p, "expansion_approved", Map.of("PLAYER", "Admin"));
            plugin.effects().playConfirm(p);
        }
        return true;
    }

    public boolean denyRequest(ExpansionRequest req) {
        if (req == null) return false;
        req.deny();
        
        OfflinePlayer target = Bukkit.getOfflinePlayer(req.getRequester());
        if (target.isOnline()) {
            plugin.msg().send(target.getPlayer(), "expansion_denied", Map.of("PLAYER", "Admin"));
            plugin.effects().playError(target.getPlayer());
        }
        
        activeRequests.remove(req.getRequester());
        setDirty(true);
        return true;
    }

    // --- LOGIC ---

    private double calculateSmartCost(int currentRadius, int newRadius) {
        double baseCost = plugin.cfg().raw().getDouble("expansions.cost_per_block", 10.0);
        double multiplier = plugin.cfg().raw().getDouble("expansions.cost_multiplier", 1.1); 
        int blocksAdded = newRadius - currentRadius;
        
        double totalCost = baseCost * blocksAdded;
        if (blocksAdded > 10) totalCost *= multiplier; // Tax for rapid growth
        
        return Math.round(totalCost * 100.0) / 100.0;
    }

    private boolean isOverlapping(Plot oldPlot, int newRadius) {
        int cX = (oldPlot.getX1() + oldPlot.getX2()) / 2;
        int cZ = (oldPlot.getZ1() + oldPlot.getZ2()) / 2;
        
        int buffer = plugin.cfg().raw().getInt("expansions.buffer_zone", 5);
        int r = newRadius + buffer;

        int x1 = cX - r; 
        int z1 = cZ - r;
        int x2 = cX + r; 
        int z2 = cZ + r;

        return plugin.store().isAreaOverlapping(oldPlot, oldPlot.getWorld(), x1, z1, x2, z2);
    }
    
    private boolean applyExpansion(Plot oldPlot, int newRadius) {
        int cX = (oldPlot.getX1() + oldPlot.getX2()) / 2;
        int cZ = (oldPlot.getZ1() + oldPlot.getZ2()) / 2;
        
        int x1 = cX - newRadius;
        int z1 = cZ - newRadius;
        int x2 = cX + newRadius;
        int z2 = cZ + newRadius;
        
        plugin.store().removePlot(oldPlot.getOwner(), oldPlot.getPlotId());
        
        oldPlot.setX1(x1); oldPlot.setX2(x2);
        oldPlot.setZ1(z1); oldPlot.setZ2(z2);
        
        plugin.store().addPlot(oldPlot);
        return true;
    }

    // --- PERSISTENCE ---

    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }

    public synchronized void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) { e.printStackTrace(); }
        
        data = YamlConfiguration.loadConfiguration(file);
        activeRequests.clear();

        if (data.isConfigurationSection("requests")) {
            for (String key : data.getConfigurationSection("requests").getKeys(false)) {
                try {
                    UUID reqId = UUID.fromString(key);
                    String path = "requests." + key;
                    
                    ExpansionRequest req = new ExpansionRequest(
                        reqId,
                        UUID.fromString(data.getString(path + ".owner")),
                        UUID.fromString(data.getString(path + ".plotId")),
                        data.getString(path + ".world"),
                        data.getInt(path + ".currentRadius"),
                        data.getInt(path + ".requestedRadius"),
                        data.getDouble(path + ".cost")
                    );
                    activeRequests.put(reqId, req);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void save() {
        if (data == null) return;
        data.set("requests", null);
        
        for (ExpansionRequest req : activeRequests.values()) {
            String path = "requests." + req.getRequester().toString();
            data.set(path + ".owner", req.getPlotOwner().toString());
            data.set(path + ".plotId", req.getPlotId().toString());
            data.set(path + ".world", req.getWorldName());
            data.set(path + ".currentRadius", req.getCurrentRadius());
            data.set(path + ".requestedRadius", req.getRequestedRadius());
            data.set(path + ".cost", req.getCost());
        }
        
        try { data.save(file); isDirty = false; } catch (IOException e) { e.printStackTrace(); }
    }
}
