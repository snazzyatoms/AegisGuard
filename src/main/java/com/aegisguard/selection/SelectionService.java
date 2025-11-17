package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey; // --- NEW IMPORT ---
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType; // --- NEW IMPORT ---

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SelectionService
 * ... (existing comments) ...
 *
 * --- UPGRADE NOTES ---
 * - CRITICAL LAG FIX: Removed the redundant setFlag() calls and the
 * 'flushSync()' call from confirmClaim(). PlotStore constructor
 * and createPlot() already handle this.
 * - ARCHITECTURE FIX: All plugin.getConfig() calls have been
 * replaced with plugin.cfg() calls to use the config wrapper.
 * - RELIABILITY FIX: Wand now uses a PersistentDataContainer (NBT tag)
 * instead of a display name to be 100% reliable.
 * - CLEANUP: Updated plugin.sounds() to plugin.effects()
 */
public class SelectionService implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, Location> corner1 = new HashMap<>();
    private final Map<UUID, Location> corner2 = new HashMap<>();

    // --- NEW ---
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

        // --- RELIABILITY FIX ---
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
                    java.util.Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            plugin.effects().playMenuFlip(p); // --- MODIFIED ---
            e.setCancelled(true);
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            corner2.put(p.getUniqueId(), loc);
            plugin.msg().send(p, "corner2_set",
                    java.util.Map.of("X", String.valueOf(loc.getBlockX()), "Z", String.valueOf(loc.getBlockZ())));
            plugin.effects().playMenuFlip(p); // --- MODIFIED ---
            e.setCancelled(true);
        }
    }

    /* -----------------------------
     * Confirm Claim
     * ----------------------------- */
    public void confirmClaim(Player p) {
        UUID id = p.getUniqueId();
        String worldName = p.getWorld().getName();

        // --- ARCHITECTURE FIX ---
        // Use the AGConfig wrapper (plugin.cfg()) not plugin.getConfig()
        int maxClaims = plugin.cfg().getMaxClaimsPerPlayer(); // Simpler lookup

        int currentClaims = plugin.store().getPlots(id).size();
        if (currentClaims >= maxClaims && maxClaims > 0) {
            plugin.msg().send(p, "max_claims_reached", java.util.Map.of("AMOUNT", String.valueOf(maxClaims)));
            plugin.effects().playError(p); // --- MODIFIED ---
            return;
        }

        if (!corner1.containsKey(id) || !corner2.containsKey(id)) {
            plugin.msg().send(p, "must_select");
            plugin.effects().playError(p); // --- MODIFIED ---
            return;
        }

        // --- ARCHITECTURE FIX ---
        boolean useVault = plugin.cfg().useVault();
        double cost = plugin.cfg().getClaimCost();
        String itemType = plugin.cfg().getItemCostType();
        int itemAmount = plugin.cfg().getItemCostAmount();
        // (Note: Your per-world config logic was good, but this example uses
        // the global defaults from AGConfig. You can expand AGConfig to
        // handle per-world overrides if needed.)

        if (useVault && cost > 0 && !plugin.vault().charge(p, cost)) {
            plugin.msg().send(p, "need_vault", java.util.Map.of("AMOUNT", plugin.vault().format(cost)));
            plugin.effects().playError(p); // --- MODIFIED ---
            return;
        } else if (!useVault && itemAmount > 0) {
            Material mat = Material.matchMaterial(itemType);
            if (mat != null) {
                if (!p.getInventory().containsAtLeast(new ItemStack(mat), itemAmount)) {
                    plugin.msg().send(p, "need_items",
                            java.util.Map.of("AMOUNT", String.valueOf(itemAmount), "ITEM", itemType));
                    plugin.effects().playError(p); // --- MODIFIED ---
                    return;
                }
                p.getInventory().removeItem(new ItemStack(mat, itemAmount));
            }
        }

        Location c1 = corner1.get(id);
        Location c2 = corner2.get(id);

        // Save claim (This is now async-safe and sets all defaults)
        plugin.store().createPlot(id, c1, c2);

        // --- CRITICAL LAG FIX ---
        // The entire block below is REMOVED.
        // 1. The Plot constructor already sets these flags.
        // 2. createPlot() already sets the 'isDirty' flag.
        // 3. flushSync() causes massive server lag.
        /*
        PlotStore.Plot newPlot = plugin.store().getPlotAt(c1);
        if (newPlot != null) {
            newPlot.setFlag("safe_zone", true);
            ...
            plugin.store().flushSync(); // <--- REMOVED
        }
        */

        plugin.msg().send(p, "plot_created");
        plugin.msg().send(p, "safe_zone_enabled"); // tell the player explicitly

        if (useVault && cost > 0) {
            plugin.msg().send(p, "cost_deducted", java.util.Map.of("AMOUNT", plugin.vault().format(cost)));
        } else if (!useVault && itemAmount > 0) {
            plugin.msg().send(p, "items_deducted",
                    java.util.Map.of("AMOUNT", String.valueOf(itemAmount), "ITEM", itemType));
        }

        plugin.effects().playClaimSuccess(p); // --- MODIFIED ---

        if (plugin.cfg().lightningOnClaim()) { // --- ARCHITECTURE FIX ---
            p.getWorld().strikeLightningEffect(c1);
        }
    }

    /* -----------------------------
     * Unclaim (only plot you’re standing in)
     * ----------------------------- */
    public void unclaimHere(Player p) {
        UUID id = p.getUniqueId();
        PlotStore.Plot plot = plugin.store().getPlotAt(p.getLocation());

        if (plot == null || !plot.getOwner().equals(id)) {
            plugin.msg().send(p, "no_plot_here");
            plugin.effects().playError(p); // --- MODIFIED ---
            return;
        }

        // String worldName = p.getWorld().getName(); // Not needed if using global cfg

        // --- ARCHITECTURE FIX ---
        boolean refundEnabled = plugin.cfg().refundOnUnclaim();
        int refundPercent = plugin.cfg().getRefundPercent();
        boolean useVault = plugin.cfg().useVault();
        double vaultCost = plugin.cfg().getClaimCost();
        String itemType = plugin.cfg().getItemCostType();
        int itemAmount = plugin.cfg().getItemCostAmount();

        if (refundEnabled && refundPercent > 0) {
            if (useVault && vaultCost > 0) {
                double refundAmount = (vaultCost * refundPercent) / 100.0;
                plugin.vault().give(p, refundAmount); // give() supports OfflinePlayer
                plugin.msg().send(p, "vault_refund",
                        java.util.Map.of("AMOUNT", plugin.vault().format(refundAmount), "PERCENT", String.valueOf(refundPercent)));
            } else {
                Material mat = Material.matchMaterial(itemType);
                if (mat != null && itemAmount > 0) {
                    int refundCount = (int) Math.floor((itemAmount * refundPercent) / 100.0);
                    if (refundCount > 0) {
                        p.getInventory().addItem(new ItemStack(mat, refundCount));
                        plugin.msg().send(p, "item_refund",
                                java.util.Map.of("AMOUNT", String.valueOf(refundCount),
                                        "ITEM", itemType, "PERCENT", String.valueOf(refundPercent)));
                    }
                }
            }
        }

        // This is now async-safe
        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
        plugin.msg().send(p, "plot_unclaimed");

        plugin.effects().playUnclaim(p); // --- MODIFIED ---
    }
}
