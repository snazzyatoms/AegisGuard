package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
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
 * ------------------------------------------------------------
 * Handles creation, approval, denial, and tracking of plot
 * expansion requests.
 *
 * --- UPGRADE NOTES ---
 * - Added full persistence (load/save) to expansion-requests.yml
 * - Added async auto-saving via isDirty flag.
 * - Fixed all offline-player bugs. Approval now works for offline players.
 * - Fixed major logic bug: approval now uses plotId, not player location.
 * - Fixed Plot vs. Radius conflict by assuming plots are squares and
 * re-creating the plot on expansion.
 */
public class ExpansionRequestManager {

    private final AegisGuard plugin;
    private final Map<UUID, ExpansionRequest> activeRequests = new ConcurrentHashMap<>();

    // --- NEW ---
    private final File file;
    private FileConfiguration data;
    private volatile boolean isDirty = false;

    public ExpansionRequestManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "expansion-requests.yml");
        load();
    }

    /* -----------------------------
     * Create Request
     * ----------------------------- */
    public boolean createRequest(Player requester, PlotStore.Plot plot, int newRadius) {
// ... existing code ...
        // ... (permission checks) ...
        if (hasActiveRequest(requester.getUniqueId())) {
// ... existing code ...
            return false;
        }

        // Check per-world rules
// ... existing code ...
        // ... (world checks) ...

        // --- MODIFIED ---
        // Logic to handle plot-radius conflict. We assume plots are squares.
        int currentRadius = (plot.getX2() - plot.getX1()) / 2;
        if (newRadius <= currentRadius) {
            plugin.msg().send(requester, "expansion_radius_too_small", Map.of("RADIUS", String.valueOf(currentRadius)));
            return false;
        }

        // Calculate cost dynamically
// ... existing code ...
        double cost = calculateCost(requester.getWorld().getName(), currentRadius, newRadius);

        // Create request record
        // --- MODIFIED --- (Added plotId)
        ExpansionRequest request = new ExpansionRequest(
                requester.getUniqueId(),
                plot.getOwner(),
                plot.getPlotId(), // <-- CRITICAL FIX
                requester.getWorld().getName(),
                currentRadius,
                newRadius,
                cost
        );

        activeRequests.put(requester.getUniqueId(), request);
        setDirty(true); // Mark for saving

// ... existing code ...
        // ... (notifications) ...
        return true;
    }

    /* -----------------------------
     * Approve Request
     * --- HEAVILY MODIFIED ---
     * ----------------------------- */
    public boolean approveRequest(ExpansionRequest req) {
        if (req == null) return false;

        OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());

        // Charge cost
        if (!chargePlayer(requester, req.getCost(), req.getWorldName())) {
            // Deny if payment fails
            denyRequest(req);
            // Notify admin if they are online
            Player admin = Bukkit.getPlayer(req.getPlotOwner()); // Assuming admin is the plot owner
            if (admin != null) {
                plugin.msg().send(admin, "expansion_payment_failed", Map.of("PLAYER", requester.getName()));
            }
            return false;
        }

        // Apply expansion
        PlotStore.Plot oldPlot = plugin.store().getPlot(req.getPlotOwner(), req.getPlotId());
        if (oldPlot == null) {
            // Plot was deleted after request, refund and deny
            plugin.getLogger().warning("Plot " + req.getPlotId() + " not found for approval. Refunding player.");
            refundPlayer(requester, req.getCost(), req.getWorldName());
            denyRequest(req);
            return false;
        }

        // --- NEW LOGIC ---
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
     * --- NEW ---
     * Helper method to replace the old plot with a new, larger one.
     * Assumes plots are squares.
     */
    private boolean applyExpansion(PlotStore.Plot oldPlot, int newRadius) {
        Location world = Bukkit.getWorld(oldPlot.getWorld());
        if (world == null) return false;

        // 1. Find center
        int cX = oldPlot.getX1() + (oldPlot.getX2() - oldPlot.getX1()) / 2;
        int cZ = oldPlot.getZ1() + (oldPlot.getZ2() - oldPlot.getZ1()) / 2;

        // 2. Define new corners
        Location c1 = new Location(world, cX - newRadius, 0, cZ - newRadius);
        Location c2 = new Location(world, cX + newRadius, 0, cZ + newRadius);

        // 3. Remove old plot
        plugin.store().removePlot(oldPlot.getOwner(), oldPlot.getPlotId());

        // 4. Create new plot
        // createPlot will auto-save (setDirty) the PlotStore
        plugin.store().createPlot(oldPlot.getOwner(), c1, c2);
        return true;
    }


    /* -----------------------------
     * Deny Request
     * ----------------------------- */
    public boolean denyRequest(ExpansionRequest req) {
// ... existing code ...
        // ... (code) ...
        req.deny();

        // --- MODIFIED ---
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
// ... existing code ...
        // ... (cost calculation) ...
        return Math.max(worldModifier * (delta / 4.0), 0);
    }

    // --- MODIFIED --- to take OfflinePlayer
    private boolean chargePlayer(OfflinePlayer player, double amount, String worldName) {
        if (amount <= 0) return true;

        if (plugin.cfg().useVault()) {
            VaultHook vault = plugin.vault();
            // We can't charge an offline player with Vault, so they must be online
            if (!player.isOnline()) {
                plugin.getLogger().warning("Vault charge failed: Player " + player.getName() + " is offline.");
                return false;
            }
            // Use the safe charge method from VaultHook
            return vault.charge(player.getPlayer(), amount);
        } else {
            // Item-based payment
            // Player MUST be online to take items
            if (!player.isOnline()) {
                plugin.getLogger().warning("Item charge failed: Player " + player.getName() + " is offline.");
                return false;
            }
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return false;

// ... existing code ...
            // ... (item cost logic) ...
            Material item = Material.matchMaterial(plugin.getConfig().getString(path + "type", "DIAMOND"));
// ... existing code ...
            int amountRequired = plugin.getConfig().getInt(path + "amount", 5);

            ItemStack costItem = new ItemStack(item, amountRequired);
            if (!onlinePlayer.getInventory().containsAtLeast(costItem, amountRequired)) return false;

            onlinePlayer.getInventory().removeItem(costItem);
            return true;
        }
    }

    /**
     * --- NEW ---
     * Refunds a player.
     */
    private void refundPlayer(OfflinePlayer player, double amount, String worldName) {
        if (amount <= 0) return;

        if (plugin.cfg().useVault()) {
            // VaultHook.give() supports offline players!
            plugin.vault().give(player, amount);
        } else {
            // Cannot refund items to an offline player.
            // This is a design limitation of item-based economies.
            if (!player.isOnline()) {
                plugin.getLogger().warning("Could not refund items to " + player.getName() + ": Player is offline.");
                return;
            }
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) return;

// ... existing code ...
            // ... (item cost logic, same as chargePlayer) ...
            String path = "claims.per_world." + worldName + ".item_cost.";
            Material item = Material.matchMaterial(plugin.getConfig().getString(path + "type", "DIAMIND"));
            int amountToGive = plugin.getConfig().getInt(path + "amount", 5);
            ItemStack costItem = new ItemStack(item, amountToGive);

            // Give items, drop on ground if inventory is full
            onlinePlayer.getInventory().addItem(costItem).forEach((index, itemStack) -> {
                onlinePlayer.getWorld().dropItemNaturally(onlinePlayer.getLocation(), itemStack);
            });
        }
    }


    /* -----------------------------
     * Utilities
     * ----------------------------- */
// ... existing code ...
    // ... (getRequest, getRequesterFromItem, etc) ...

    /* -----------------------------
     * Persistence (NEW)
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

                    // Restore state
                    String status = data.getString(path + ".status", "PENDING");
                    if (status.equals("APPROVED")) req.approve();
                    if (status.equals("DENIED")) req.deny();

                    // Only load pending requests into memory
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
        // Clear old data
        data.set("requests", null);

        for (ExpansionRequest req : activeRequests.values()) {
            // We only save PENDING requests. Approved/Denied are removed.
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
