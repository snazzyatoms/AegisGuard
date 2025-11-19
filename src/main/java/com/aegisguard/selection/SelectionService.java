package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SelectionService
 * - Handles Aegis Scepter interactions, claiming, unclaiming, and resizing plots.
 *
 * --- FINAL VERSION ---
 * - Handles all commands: /ag claim, /ag unclaim, /ag resize, /ag setspawn, /ag home.
 * - Uses the secure PersistentDataContainer (NBT tag) for the wand.
 * - Lag-free: Removes all obsolete, lag-inducing manual flag setting.
 * - Server Zones: Contains claimServerZone logic.
 */
public class SelectionService implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, Location> corner1 = new HashMap<>();
    private final Map<UUID, Location> corner2 = new HashMap<>();

    // Public key so AegisCommand can add this tag and this class can check it.
    public static NamespacedKey WAND_KEY;

    public SelectionService(AegisGuard plugin) {
        this.plugin = plugin;
        WAND_KEY = new NamespacedKey(plugin, "aegis_wand");
    }

    /* -----------------------------
     * Handle Wand Clicks
     * ----------------------------- */
    @EventHandler
    public void onSelect(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.LIGHTNING_ROD) return;

        // Identify our wand by a hidden tag (PDC), not its display name.
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE)) {
            return;
        }

        Location loc = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : null;
        if (loc == null) return;

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            corner1.put(p.getUniqueId(), loc);
            plugin.msg().send(p, "corner1_set",
                    Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            plugin.effects().playMenuFlip(p);
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            corner2.put(p.getUniqueId(), loc);
            plugin.msg().send(p, "corner2_set",
                    Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            plugin.effects().playMenuFlip(p);
            e.setCancelled(true);
        }
    }

    /* -----------------------------
     * Claiming & Unclaiming Logic
     * ----------------------------- */

    public void confirmClaim(Player p) {
        UUID id = p.getUniqueId();
        
        // 1. Check Selections
        if (!corner1.containsKey(id) || !corner2.containsKey(id)) {
            plugin.msg().send(p, "must_select");
            plugin.effects().playError(p);
            return;
        }

        Location c1 = corner1.get(id);
        Location c2 = corner2.get(id);
        
        if (!c1.getWorld().equals(c2.getWorld())) {
            // Fallback message if key missing: "Worlds must match!"
            p.sendMessage("§cError: Corner worlds do not match."); 
            plugin.effects().playError(p);
            return;
        }
        
        if (plugin.store().isAreaOverlapping(null, c1.getWorld().getName(), c1.getBlockX(), c1.getBlockZ(), c2.getBlockX(), c2.getBlockZ())) {
            plugin.msg().send(p, "resize-fail-overlap"); // Reuse overlap message
            plugin.effects().playError(p);
            return;
        }
        
        // 2. Check Limits (Admin Bypass)
        int maxClaims = plugin.cfg().getWorldMaxClaims(p.getWorld());
        int currentClaims = plugin.store().getPlots(id).size();
        if (currentClaims >= maxClaims && maxClaims > 0 && !p.hasPermission("aegis.admin.bypass_claim_limit")) {
            plugin.msg().send(p, "max_claims_reached", Map.of("AMOUNT", String.valueOf(maxClaims)));
            plugin.effects().playError(p);
            return;
        }

        // 3. Economy Check
        if (!processPayment(p)) {
            return;
        }

        // 4. Create Plot
        plugin.store().createPlot(id, c1, c2);

        // 5. Cleanup & Feedback
        corner1.remove(id);
        corner2.remove(id);
        
        plugin.msg().send(p, "plot_created");
        plugin.msg().send(p, "safe_zone_enabled");
        plugin.effects().playClaimSuccess(p);

        if (plugin.cfg().lightningOnClaim()) {
            p.getWorld().strikeLightningEffect(c1);
        }
    }

    public void unclaimHere(Player p) {
        UUID id = p.getUniqueId();
        Plot plot = plugin.store().getPlotAt(p.getLocation());

        if (plot == null || !plot.getOwner().equals(id)) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        
        if (plot.isServerZone()) {
             // Admin zone protection message
             p.sendMessage("§cYou cannot unclaim a Server Zone with this command.");
             plugin.effects().playError(p);
             return;
        }
        
        // Refund logic
        double refundAmount = calculateRefund(p.getWorld());
        if (refundAmount > 0) {
            plugin.vault().give(p, refundAmount); 
            plugin.msg().send(p, "vault_refund",
                    Map.of("AMOUNT", plugin.vault().format(refundAmount), "PERCENT", String.valueOf(plugin.cfg().getRefundPercent(p.getWorld()))));
        }

        // Remove plot (async-safe)
        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
        plugin.msg().send(p, "plot_unclaimed");
        plugin.effects().playUnclaim(p);
    }
    
    /* -----------------------------
     * Resizing Logic
     * ----------------------------- */
    
    public void resizePlot(Player p, String direction, int amount) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        
        // 1. Calculate new coordinates
        int x1 = plot.getX1();
        int z1 = plot.getZ1();
        int x2 = plot.getX2();
        int z2 = plot.getZ2();
        
        // Apply the resize delta
        switch (direction.toLowerCase()) {
            case "north" -> z1 -= amount;
            case "south" -> z2 += amount;
            case "east" -> x2 += amount;
            case "west" -> x1 -= amount;
        }
        
        int newArea = (x2 - x1 + 1) * (z2 - z1 + 1);
        int maxArea = plugin.cfg().getWorldMaxArea(p.getWorld());
        
        // 2. Check Limits & Overlap
        if (newArea > maxArea && !p.hasPermission("aegis.admin.bypass_claim_limit")) {
            plugin.msg().send(p, "resize-fail-max-area", Map.of("AMOUNT", String.valueOf(maxArea)));
            plugin.effects().playError(p);
            return;
        }
        if (plugin.store().isAreaOverlapping(plot, plot.getWorld(), x1, z1, x2, z2)) {
            plugin.msg().send(p, "resize-fail-overlap");
            plugin.effects().playError(p);
            return;
        }
        
        // 3. Process Payment (only charge for net expansion)
        // Simple calculation: cost per block * amount of new blocks (width * amount)
        // This is an approximation. For exact area math:
        int oldArea = (plot.getX2() - plot.getX1() + 1) * (plot.getZ2() - plot.getZ1() + 1);
        int addedArea = newArea - oldArea;
        double cost = addedArea * plugin.cfg().getResizeCostPerBlock();
        
        if (cost > 0 && plugin.cfg().useVault(p.getWorld())) {
            if (!plugin.vault().charge(p, cost)) {
                plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(cost)));
                plugin.effects().playError(p);
                return;
            }
        }
        
        // 4. Create new plot object and add to store (removes old)
        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
        
        Plot newPlot = new Plot(
            plot.getPlotId(),
            plot.getOwner(),
            plot.getOwnerName(),
            plot.getWorld(),
            x1, z1, x2, z2,
            plot.getLastUpkeepPayment()
        );
        // Copy all flags, roles, and ultimate features to the new plot object
        plot.getFlags().forEach(newPlot::setFlag);
        plot.getPlayerRoles().forEach(newPlot::setRole);
        
        newPlot.setSpawnLocation(plot.getSpawnLocation());
        newPlot.setWelcomeMessage(plot.getWelcomeMessage());
        newPlot.setFarewellMessage(plot.getFarewellMessage());
        
        newPlot.setForSale(plot.isForSale(), plot.getSalePrice());
        newPlot.setForRent(plot.isForRent(), plot.getRentPrice());
        newPlot.setRenter(plot.getCurrentRenter(), plot.getRentExpires());
        
        newPlot.setPlotStatus(plot.getPlotStatus());
        newPlot.setCurrentBid(plot.getCurrentBid(), plot.getCurrentBidder());
        
        newPlot.setBorderParticle(plot.getBorderParticle());
        newPlot.setAmbientParticle(plot.getAmbientParticle());
        newPlot.setEntryEffect(plot.getEntryEffect());
        
        plugin.store().addPlot(newPlot);
        
        // 5. Feedback
        plugin.msg().send(p, "resize-success", Map.of("DIRECTION", direction, "AMOUNT", String.valueOf(amount)));
        if (cost > 0) {
            plugin.msg().send(p, "cost_deducted", Map.of("AMOUNT", plugin.vault().format(cost)));
        }
        plugin.effects().playConfirm(p);
    }
    
    /* -----------------------------
     * Admin/Utility Methods
     * ----------------------------- */
     
    public boolean hasBothCorners(UUID playerID) {
        return corner1.containsKey(playerID) && corner2.containsKey(playerID);
    }
    
    public void claimServerZone(Player admin) {
        UUID id = admin.getUniqueId();
        
        // We use the selection service's stored corners
        Location c1 = corner1.get(id);
        Location c2 = corner2.get(id);
        
        // Create a plot owned by the special server UUID
        Plot plot = new Plot(
            UUID.randomUUID(),
            Plot.SERVER_OWNER_UUID,
            "Server Zone: " + admin.getName(), // Owner name for logging
            c1.getWorld().getName(),
            c1.getBlockX(), c1.getBlockZ(),
            c2.getBlockX(), c2.getBlockZ()
        );
        
        // Default Server Zone flags are set in Plot constructor. 
        // Admin must use /ag admin flags to configure further.
        // Force them off initially for safety/customization:
        plot.setFlag("safe_zone", false); 
        plot.setFlag("build", false);     
        plot.setFlag("pvp", false);       
        plot.setFlag("containers", false);
        plot.setFlag("interact", true);   
        
        plugin.store().addPlot(plot);
        
        // Clear selection
        corner1.remove(id);
        corner2.remove(id);
    }
    
    /* -----------------------------
     * Welcome/TP Commands
     * ----------------------------- */
    
    public void setPlotSpawn(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }

        if (!plot.isInside(p.getLocation())) {
             plugin.msg().send(p, "home-fail-outside");
             plugin.effects().playError(p);
             return;
        }

        plot.setSpawnLocation(p.getLocation());
        plugin.store().setDirty(true);
        plugin.msg().send(p, "home-set-success");
        plugin.effects().playConfirm(p);
    }
    
    public void teleportToHome(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            // Try to find *any* plot they own if they aren't standing in one
            // (Simplified: just error for now, could check getPlots(p.getUniqueId()))
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        
        if (plot.getSpawnLocation() == null) {
            plugin.msg().send(p, "home-fail-no-spawn");
            plugin.effects().playError(p);
            return;
        }
        
        p.teleport(plot.getSpawnLocation());
        plugin.effects().playConfirm(p);
    }
    
    public void setWelcomeMessage(Player p, String message) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        
        plot.setWelcomeMessage(message);
        plugin.store().setDirty(true);
        plugin.msg().send(p, message == null ? "welcome-cleared" : "welcome-set");
        plugin.effects().playMenuFlip(p);
    }
    
    public void setFarewellMessage(Player p, String message) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.getOwner().equals(p.getUniqueId())) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p);
            return;
        }
        
        plot.setFarewellMessage(message);
        plugin.store().setDirty(true);
        plugin.msg().send(p, message == null ? "farewell-cleared" : "farewell-set");
        plugin.effects().playMenuFlip(p);
    }

    /* -----------------------------
     * Payment Helpers
     * ----------------------------- */
     
    private boolean processPayment(Player p) {
        // This logic handles payment for initial claims.
        boolean useVault = plugin.cfg().useVault(p.getWorld());
        double cost = plugin.cfg().getWorldClaimCost(p.getWorld());
        Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
        int itemAmount = plugin.cfg().getWorldItemCostAmount(p.getWorld());

        if (useVault && cost > 0) {
            if (!plugin.vault().charge(p, cost)) {
                plugin.msg().send(p, "need_vault", Map.of("AMOUNT", plugin.vault().format(cost)));
                plugin.effects().playError(p);
                return false;
            }
        } else if (!useVault && itemAmount > 0) {
            if (mat != null) {
                if (!p.getInventory().containsAtLeast(new ItemStack(mat), itemAmount)) {
                    plugin.msg().send(p, "need_items", Map.of("AMOUNT", String.valueOf(itemAmount), "ITEM", mat.toString()));
                    plugin.effects().playError(p);
                    return false;
                }
                p.getInventory().removeItem(new ItemStack(mat, itemAmount));
            }
        }
        return true;
    }
    
    private double calculateRefund(org.bukkit.World world) {
        double initialCost = plugin.cfg().getWorldClaimCost(world);
        int percent = plugin.cfg().getRefundPercent(world);
        return (initialCost * percent) / 100.0;
    }
}
