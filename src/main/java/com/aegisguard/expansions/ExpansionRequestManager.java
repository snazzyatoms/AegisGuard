package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExpansionRequestManager
 * - Handles the creation, approval, and denial of plot expansion requests.
 * - Manages data persistence to expansion-requests.yml.
 */
public class ExpansionRequestManager {

    private final AegisGuard plugin;
    private final Map<UUID, ExpansionRequest> activeRequests = new ConcurrentHashMap<>();

    // --- Persistence ---
    private final File file;
    private FileConfiguration data;
    private volatile boolean isDirty = false;

    public ExpansionRequestManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "expansion-requests.yml");
        // load() is called async from AegisGuard.java
    }
    
    // Getter for GUI
    public Collection<ExpansionRequest> getActiveRequests() {
        return Collections.unmodifiableCollection(activeRequests.values());
    }
    
    // Utility for GUI
    public ExpansionRequest getRequest(UUID requesterId) {
        return activeRequests.get(requesterId);
    }

    /* -----------------------------
     * Create Request
     * ----------------------------- */
    public boolean createRequest(Player requester, Plot plot, int newRadius) {
        if (plot == null) {
            plugin.msg().send(requester, "no_plot_here");
            return false;
        }

        // Only owners may expand
        if (!plot.getOwner().equals(requester.getUniqueId())) {
            plugin.msg().send(requester, "no_perm");
            return false;
        }

        // Check if this player already has a pending expansion
        if (hasActiveRequest(requester.getUniqueId())) {
            plugin.msg().send(requester, "expansion_exists");
            return false;
        }

        // Check per-world rules
        WorldRulesManager rules = plugin.worldRules();
        if (!rules.allowClaims(requester.getWorld())) {
            plugin.msg().send(requester, "admin-zone-no-claims");
            return false;
        }

        // Logic to handle plot-radius conflict. We assume plots are squares.
        int currentRadius = (plot.getX2() - plot.getX1()) / 2;
        if (newRadius <= currentRadius) {
            plugin.msg().send(requester, "expansion_radius_too_small", Map.of("RADIUS", String.valueOf(currentRadius)));
            return false;
        }

        // Calculate cost dynamically
        double cost = calculateCost(requester.getWorld().getName(), currentRadius, newRadius);

        // Create request record
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
        setDirty(true); // Mark for saving

        Map<String, String> placeholders = Map.of(
                "PLAYER", requester.getName(),
                "AMOUNT", String.format("%.2f", cost)
        );

        plugin.msg().send(requester, "expansion_submitted", placeholders);
        plugin.getLogger().info("[AegisGuard] Expansion request submitted by " + requester.getName() +
                " -> Radius: " + currentRadius + " -> " + newRadius);
        return true;
    }

    /* -----------------------------
     * Approve Request
     * ----------------------------- */
    public boolean approveRequest(ExpansionRequest req) {
        if (req == null) return false;

        OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());

        // Charge cost
        if (!chargePlayer(requester, req.getCost(), req.getWorldName())) {
            // Deny if payment fails
            denyRequest(req);
            // Notify admin if they are online
            Player admin = Bukkit.getPlayer(req.getPlotOwner()); 
            if (admin != null) {
                plugin.msg().send(admin, "expansion_payment_failed", Map.of("PLAYER", requester.getName() != null ? requester.getName() : "Unknown"));
            }
            return false;
        }

        // Apply expansion
        Plot oldPlot = plugin.store().getPlot(req.getPlotOwner(), req.getPlotId());
        
        if (oldPlot == null) {
            // Plot was deleted after request, refund and deny
            plugin.getLogger().warning("Plot " + req.getPlotId() + " not found for approval. Refunding player.");
            refundPlayer(requester, req.getCost(), req.getWorldName());
            denyRequest(req);
            return false;
        }

        // We must remove the old plot and create a new one
        if (!applyExpansion(oldPlot, req.getRequestedRadius())) {
            // Failed to expand (e.g., overlap), refund and deny
            plugin.getLogger().warning("Failed to apply expansion for " + req.getPlotId() + ". Refunding player.");
            refundPlayer(requester, req.getCost(), req.getWorldName());
            denyRequest(req);
            return false;
        }

        req.approve();
        activeRequests.remove(req.getRequester()); // Request is complete
        setDirty(true); // Save the removal

        // Notify requester (if online)
        if (requester.isOnline()) {
            plugin.msg().send(requester.getPlayer(), "expansion_approved", Map.of("PLAYER", "Admin"));
        }

        plugin.getLogger().info("[AegisGuard] Expansion approved for " + requester.getName() +
                " (" + req.getCurrentRadius() + " -> " + req.getRequestedRadius() + ")");
        return true;
    }

    /**
     * Helper method to replace the old plot with a new, larger one.
     */
    private boolean applyExpansion(Plot oldPlot, int newRadius) {
        World world = Bukkit.getWorld(oldPlot.getWorld());
        if (world == null) return false;

        // 1. Find center
        int cX = oldPlot.getX1() + (oldPlot.getX2() - oldPlot.getX1()) / 2;
        int cZ = oldPlot.getZ1() + (oldPlot.getZ2() - oldPlot.getZ1()) / 2;

        // 2. Define new corners
        Location c1 = new Location(world, cX - newRadius, 0, cZ - newRadius);
        Location c2 = new Location(world, cX + newRadius, 0, cZ + newRadius);
        
        // 3. Check for overlap
        if (plugin.store().isAreaOverlapping(oldPlot, oldPlot.getWorld(), c1.getBlockX(), c1.getBlockZ(), c2.getBlockX(), c2.getBlockZ())) {
            return false;
        }

        // 4. Remove old plot
        plugin.store().removePlot(oldPlot.getOwner(), oldPlot.getPlotId());

        // 5. Create new plot (with all data from old plot)
        Plot newPlot = new Plot(
            oldPlot.getPlotId(), // Keep same ID
            oldPlot.getOwner(),
            oldPlot.getOwnerName(),
            oldPlot.getWorld(),
            c1.getBlockX(), c1.getBlockZ(),
            c2.getBlockX(), c2.getBlockZ(),
            oldPlot.getLastUpkeepPayment()
        );
        
        // --- Restore all data ---
        oldPlot.getFlags().forEach(newPlot::setFlag);
        oldPlot.getPlayerRoles().forEach(newPlot::setRole);
        newPlot.setSpawnLocation(oldPlot.getSpawnLocation());
        newPlot.setWelcomeMessage(oldPlot.getWelcomeMessage());
        newPlot.setFarewellMessage(oldPlot.getFarewellMessage());
        
        plugin.store().addPlot(newPlot);
        return true;
    }


    /* -----------------------------
     * Deny Request
     * ----------------------------- */
    public boolean denyRequest(ExpansionRequest req) {
        if (req == null) return false;

        req.deny();

        OfflinePlayer target = Bukkit.getOfflinePlayer(req.getRequester());
        if (target.isOnline()) {
            plugin.msg().send(target.getPlayer(), "expansion_denied", Map.of("PLAYER", "Admin"));
        }

        plugin.getLogger().info("[AegisGuard] Expansion request denied for " + target.getName());
        activeRequests.remove(req.getRequester());
        setDirty(true); // Save the removal
        return true;
    }

    /* -----------------------------
     * Cost Logic
     * ----------------------------- */
    private double calculateCost(String world, int currentRadius, int newRadius) {
        double baseCost = plugin.cfg().raw().getDouble("expansions.cost.amount", 250.0);
        int delta = newRadius - currentRadius;
        return Math.max(baseCost * delta, 0);
    }

    private boolean chargePlayer(OfflinePlayer player, double amount, String worldName) {
        if (amount <= 0) return true;
        // Safe vault call
        if (plugin.cfg().useVault(player.getPlayer() != null ? player.getPlayer().getWorld() : null)) {
            return plugin.vault().charge(player, amount);
        } else {
            // Item-based payment
            if (!player.isOnline()) return false;
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return false;

            Material item = plugin.cfg().getWorldItemCostType(player.getPlayer().getWorld());
            int amountRequired = plugin.cfg().getWorldItemCostAmount(player.getPlayer().getWorld());
            ItemStack costItem = new ItemStack(item, amountRequired);
            
            if (!onlinePlayer.getInventory().containsAtLeast(costItem, amountRequired)) return false;
            onlinePlayer.getInventory().removeItem(costItem);
            return true;
        }
    }

    private void refundPlayer(OfflinePlayer player, double amount, String worldName) {
        if (amount <= 0) return;

        if (plugin.cfg().useVault(player.getPlayer() != null ? player.getPlayer().getWorld() : null)) {
            plugin.vault().give(player, amount);
        } else {
            if (!player.isOnline()) return;
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return;

            Material item = plugin.cfg().getWorldItemCostType(player.getPlayer().getWorld());
            int amountToGive = plugin.cfg().getWorldItemCostAmount(player.getPlayer().getWorld());
            ItemStack costItem = new ItemStack(item, amountToGive);

            onlinePlayer.getInventory().addItem(costItem).forEach((index, itemStack) -> {
                onlinePlayer.getWorld().dropItemNaturally(onlinePlayer.getLocation(), itemStack);
            });
        }
    }


    /* -----------------------------
     * Utilities
     * ----------------------------- */
    public boolean hasActiveRequest(UUID requesterId) {
        return activeRequests.containsKey(requesterId);
    }
    
    /* -----------------------------
     * Persistence
     * ----------------------------- */
    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    public void saveSync() {
        save();
    }

    public synchronized void load() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        data = YamlConfiguration.loadConfiguration(file);
        activeRequests.clear();

        if (data.isConfigurationSection("requests")) {
            for (String requesterIdStr : data.getConfigurationSection("requests").getKeys(false)) {
                try {
                    UUID requesterId = UUID.fromString(requesterIdStr);
                    String path = "requests." + requesterIdStr;

                    ExpansionRequest req = new ExpansionRequest(
                            requesterId,
                            UUID.fromString(data.getString(path + ".owner")),
                            UUID.fromString(data.getString(path + ".plotId")),
                            data.getString(path + ".world"),
                            data.getInt(path + ".currentRadius"),
                            data.getInt(path + ".requestedRadius"),
                            data.getDouble(path + ".cost")
                    );

                    String status = data.getString(path + ".status", "PENDING");
                    if (status.equals("APPROVED")) req.approve();
                    if (status.equals("DENIED")) req.deny();

                    if (req.isPending()) {
                        activeRequests.put(requesterId, req);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load expansion request for: " + requesterIdStr);
                }
            }
        }
    }

    public synchronized void save() {
        // FIX: Prevent NullPointerException if load() failed or didn't run yet
        if (data == null) return;

        data.set("requests", null);

        for (ExpansionRequest req : activeRequests.values()) {
            if (!req.isPending()) continue;

            String path = "requests." + req.getRequester().toString();
            data.set(path + ".owner", req.getPlotOwner().toString());
            data.set(path + ".plotId", req.getPlotId().toString());
            data.set(path + ".world", req.getWorldName());
            data.set(path + ".currentRadius", req.getCurrentRadius());
            data.set(path + ".requestedRadius", req.getRequestedRadius());
            data.set(path + ".cost", req.getCost());
            data.set(path + ".status", req.getStatus());
        }

        try {
            data.save(file);
            isDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
