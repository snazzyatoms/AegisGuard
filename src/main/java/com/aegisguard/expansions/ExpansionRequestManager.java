package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * ExpansionRequestManager
 * - Handles land expansion requests.
 * - Fully localized (uses message keys / Codex-backed messaging).
 *
 * Persistence:
 *  requests.<requesterUUID>.owner
 *  requests.<requesterUUID>.plotId
 *  requests.<requesterUUID>.world
 *  requests.<requesterUUID>.currentRadius
 *  requests.<requesterUUID>.requestedRadius
 *  requests.<requesterUUID>.cost
 *  requests.<requesterUUID>.timestamp
 *  requests.<requesterUUID>.status
 *  requests.<requesterUUID>.decisionTimestamp
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

    /** True only if the stored request is still pending. */
    public boolean hasPendingRequest(UUID requesterId) {
        ExpansionRequest req = activeRequests.get(requesterId);
        if (req == null) return false;

        if (!req.isPending()) {
            // Just in case older files loaded non-pending states.
            activeRequests.remove(requesterId);
            setDirty(true);
            return false;
        }
        return true;
    }

    /* -----------------------------
     * REQUEST CREATION
     * ----------------------------- */
    public boolean createRequest(Player requester, Plot plot, int newRadius) {
        if (plot == null || !plot.getOwner().equals(requester.getUniqueId())) {
            plugin.msg().send(requester, "no_perm");
            return false;
        }

        // Only one pending request per requester
        if (hasPendingRequest(requester.getUniqueId())) {
            plugin.msg().send(requester, "expansion_exists");
            return false;
        }

        // 1) Size Check
        int currentRadius = Math.max(0, (plot.getX2() - plot.getX1()) / 2);
        if (newRadius <= currentRadius) {
            plugin.msg().send(requester, "expansion_invalid_size");
            return false;
        }

        // 2) Limit Check
        int maxRadius = plugin.cfg().raw().getInt("expansions.max_radius_global", 100);
        if (newRadius > maxRadius && !requester.hasPermission("aegis.admin.bypass-limits")) {
            plugin.msg().send(requester, "expansion_limit_reached", Map.of("LIMIT", String.valueOf(maxRadius)));
            return false;
        }

        // 3) Cost Check (area-based)
        double cost = calculateSmartCost(currentRadius, newRadius);
        CurrencyType type = CurrencyType.VAULT;

        if (!plugin.eco().has(requester, cost, type)) {
            plugin.msg().send(requester, "expansion_payment_failed");
            return false;
        }

        // 4) Overlap Check
        if (isOverlapping(plot, newRadius)) {
            plugin.msg().send(requester, "expansion_overlap_fail");
            return false;
        }

        // 5) Submit
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

        // Must still be pending to approve
        if (!req.isPending()) {
            activeRequests.remove(req.getRequester());
            setDirty(true);
            return false;
        }

        OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
        CurrencyType type = CurrencyType.VAULT;

        // 1) Charge Player
        Player p = requester.getPlayer();
        if (p != null) {
            if (!plugin.eco().withdraw(p, req.getCost(), type)) {
                // Payment failed: remove request, don't “deny” with admin messaging.
                removeRequest(req);
                plugin.msg().send(p, "expansion_payment_failed");
                return false;
            }
        } else {
            // Offline charge via Vault wrapper (if you have one)
            if (plugin.cfg().useVault()) {
                if (!plugin.vault().charge(requester, req.getCost())) {
                    removeRequest(req);
                    return false;
                }
            } else {
                // No offline charging supported
                removeRequest(req);
                return false;
            }
        }

        // 2) Get Plot
        Plot oldPlot = plugin.store().getPlot(req.getPlotOwner(), req.getPlotId());
        if (oldPlot == null) {
            refund(requester, p, req.getCost(), type);
            removeRequest(req);
            return false;
        }

        // 3) Apply Expansion (re-check overlap at approval time)
        if (!applyExpansion(oldPlot, req.getRequestedRadius())) {
            refund(requester, p, req.getCost(), type);

            // Keep semantics: denial due to overlap/rules (this *is* a real deny)
            denyRequest(req);
            plugin.getLogger().warning("Expansion approval failed (overlap or invalid bounds) for " + req.getRequester());
            return false;
        }

        // 4) Mark + remove active
        req.approve();
        activeRequests.remove(req.getRequester());
        setDirty(true);

        // 5) Notify (online only)
        if (p != null) {
            String actor = plugin.gui().tr(p, "expansion_actor_admin", "Admin");
            plugin.msg().send(p, "expansion_approved", Map.of("PLAYER", actor));
            plugin.effects().playConfirm(p);
        }
        return true;
    }

    public boolean denyRequest(ExpansionRequest req) {
        if (req == null) return false;

        req.deny();

        OfflinePlayer target = Bukkit.getOfflinePlayer(req.getRequester());
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                String actor = plugin.gui().tr(p, "expansion_actor_admin", "Admin");
                plugin.msg().send(p, "expansion_denied", Map.of("PLAYER", actor));
                plugin.effects().playError(p);
            }
        }

        activeRequests.remove(req.getRequester());
        setDirty(true);
        return true;
    }

    private void removeRequest(ExpansionRequest req) {
        activeRequests.remove(req.getRequester());
        setDirty(true);
    }

    private void refund(OfflinePlayer requester, Player onlinePlayer, double amount, CurrencyType type) {
        if (amount <= 0) return;

        if (onlinePlayer != null) {
            plugin.eco().deposit(onlinePlayer, amount, type);
            return;
        }

        if (plugin.cfg().useVault()) {
            plugin.vault().give(requester, amount);
        }
    }

    // --- LOGIC ---

    /**
     * Area-based cost:
     *  - radius r -> square side = (2r + 1)
     *  - area = side^2
     *  - cost_per_block applies to added area (delta blocks)
     */
    private double calculateSmartCost(int currentRadius, int newRadius) {
        double perBlock = plugin.cfg().raw().getDouble("expansions.cost_per_block", 10.0);
        double multiplier = plugin.cfg().raw().getDouble("expansions.cost_multiplier", 1.1);

        long oldSide = (2L * currentRadius) + 1L;
        long newSide = (2L * newRadius) + 1L;

        long oldArea = oldSide * oldSide;
        long newArea = newSide * newSide;

        long addedBlocks = Math.max(0L, newArea - oldArea);

        double totalCost = perBlock * addedBlocks;

        // Optional “rapid growth tax” if radius jump is large
        int radiusJump = Math.max(0, newRadius - currentRadius);
        if (radiusJump > 10) totalCost *= multiplier;

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

    /**
     * Applies expansion safely:
     * - Re-check overlap (excluding the plot itself)
     * - Only mutates store after the overlap check passes
     */
    private boolean applyExpansion(Plot oldPlot, int newRadius) {
        if (newRadius <= 0) return false;

        // Re-check overlap at approval time
        if (isOverlapping(oldPlot, newRadius)) return false;

        int cX = (oldPlot.getX1() + oldPlot.getX2()) / 2;
        int cZ = (oldPlot.getZ1() + oldPlot.getZ2()) / 2;

        int x1 = cX - newRadius;
        int z1 = cZ - newRadius;
        int x2 = cX + newRadius;
        int z2 = cZ + newRadius;

        // Replace in store
        plugin.store().removePlot(oldPlot.getOwner(), oldPlot.getPlotId());

        oldPlot.setX1(x1); oldPlot.setX2(x2);
        oldPlot.setZ1(z1); oldPlot.setZ2(z2);

        plugin.store().addPlot(oldPlot);
        plugin.store().setDirty(true);

        return true;
    }

    // --- PERSISTENCE ---

    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }

    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create expansion-requests.yml", e);
        }

        data = YamlConfiguration.loadConfiguration(file);
        activeRequests.clear();

        if (!data.isConfigurationSection("requests")) return;

        for (String key : data.getConfigurationSection("requests").getKeys(false)) {
            try {
                UUID reqId = UUID.fromString(key);
                String path = "requests." + key;

                UUID owner = UUID.fromString(Objects.requireNonNull(data.getString(path + ".owner")));
                UUID plotId = UUID.fromString(Objects.requireNonNull(data.getString(path + ".plotId")));
                String world = data.getString(path + ".world", "");

                int currentRadius = data.getInt(path + ".currentRadius");
                int requestedRadius = data.getInt(path + ".requestedRadius");
                double cost = data.getDouble(path + ".cost");

                long timestamp = data.getLong(path + ".timestamp", System.currentTimeMillis());
                long decisionTs = data.getLong(path + ".decisionTimestamp", 0L);

                // Backwards compatibility: older files won’t have status
                String statusStr = data.getString(path + ".status", "PENDING");
                ExpansionRequest.Status status;
                try {
                    status = ExpansionRequest.Status.valueOf(statusStr.toUpperCase(Locale.ROOT));
                } catch (Throwable ignored) {
                    status = ExpansionRequest.Status.PENDING;
                }

                ExpansionRequest req = new ExpansionRequest(
                        reqId,
                        owner,
                        plotId,
                        world,
                        currentRadius,
                        requestedRadius,
                        cost,
                        timestamp,
                        status,
                        decisionTs
                );

                // Only keep pending requests in the active map
                if (req.isPending()) {
                    activeRequests.put(reqId, req);
                }

            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid expansion request entry: " + key, ex);
            }
        }

        setDirty(false);
    }

    public synchronized void save() {
        if (data == null) return;

        data.set("requests", null);

        for (ExpansionRequest req : activeRequests.values()) {
            // Only persist pending requests (keeps file clean)
            if (!req.isPending()) continue;

            String path = "requests." + req.getRequester();

            data.set(path + ".owner", req.getPlotOwner().toString());
            data.set(path + ".plotId", req.getPlotId().toString());
            data.set(path + ".world", req.getWorldName());

            data.set(path + ".currentRadius", req.getCurrentRadius());
            data.set(path + ".requestedRadius", req.getRequestedRadius());
            data.set(path + ".cost", req.getCost());

            data.set(path + ".timestamp", req.getTimestamp());
            data.set(path + ".status", req.getStatus().name());
            data.set(path + ".decisionTimestamp", req.getDecisionTimestamp());
        }

        try {
            data.save(file);
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save expansion-requests.yml", e);
        }
    }
}
