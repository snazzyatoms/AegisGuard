package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot; 
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlotFlagsGUI
 * - Manages flags on a specific plot.
 */
public class PlotFlagsGUI {

    private final AegisGuard plugin;

    public PlotFlagsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotFlagsHolder implements InventoryHolder {
        private final Plot plot;
        public PlotFlagsHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    /* -----------------------------
     * Open Flags Menu
     * ----------------------------- */
    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        String title = GUIManager.safeText(plugin.msg().get(player, "plot_flags_title"), "§9Plot Flags");
        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot), 54, title);

        // Fill background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // --- STANDARD FLAGS (Row 1-2) ---
        addFlagButton(player, inv, plot, 10, "pvp", Material.IRON_SWORD, "button_pvp", "pvp_toggle_lore");
        addFlagButton(player, inv, plot, 11, "mobs", Material.ZOMBIE_HEAD, "button_mobs", "mob_toggle_lore");
        addFlagButton(player, inv, plot, 12, "containers", Material.CHEST, "button_containers", "container_toggle_lore");
        addFlagButton(player, inv, plot, 13, "tnt-damage", Material.TNT, "button_tnt", "tnt_toggle_lore");
        addFlagButton(player, inv, plot, 14, "fire-spread", Material.FLINT_AND_STEEL, "button_fire", "fire_toggle_lore");
        addFlagButton(player, inv, plot, 15, "piston-use", Material.PISTON, "button_piston", "piston_toggle_lore");
        
        addFlagButton(player, inv, plot, 19, "pets", Material.BONE, "button_pets", "pet_toggle_lore");
        addFlagButton(player, inv, plot, 20, "entities", Material.ARMOR_STAND, "button_entity", "entity_toggle_lore");
        addFlagButton(player, inv, plot, 21, "farm", Material.WHEAT, "button_farm", "farm_toggle_lore");

        // --- NEW: Entry Control (Slot 22) ---
        addFlagButton(player, inv, plot, 22, "entry", Material.OAK_FENCE_GATE, "button_entry", "entry_toggle_lore");
        
        // --- SAFE ZONE (Slot 17) ---
        // Keep existing safe zone logic but moved to standard button gen
        addFlagButton(player, inv, plot, 17, "safe_zone", Material.SHIELD, "button_safe", "safe_toggle_lore");


        // --- NEW: Flight (Slot 31 - Center) ---
        boolean canFly = plot.getFlag("fly", false);
        double flyCost = plugin.cfg().getFlightCost();
        String costString = (flyCost > 0) ? plugin.vault().format(flyCost) : "Free";
        
        // Admin Bypass check for lore
        if (plugin.isAdmin(player)) costString = "§cAdmin Bypass";

        inv.setItem(31, GUIManager.icon(
            Material.FEATHER,
            canFly ? plugin.msg().get(player, "button_fly_on") : plugin.msg().get(player, "button_fly_off"),
            replacePlaceholder(plugin.msg().getList(player, "fly_toggle_lore"), "{COST}", costString)
        ));

        // --- COSMETICS LINK (Slot 34) ---
        inv.setItem(34, GUIManager.icon(
            Material.NETHER_STAR,
            GUIManager.safeText(plugin.msg().get(player, "button_cosmetics"), "§dCosmetics"),
            plugin.msg().getList(player, "cosmetics_lore")
        ));

        // Navigation
        inv.setItem(48, GUIManager.icon(Material.ARROW, GUIManager.safeText(plugin.msg().get(player, "button_back"), "Back"), null));
        inv.setItem(49, GUIManager.icon(Material.BARRIER, GUIManager.safeText(plugin.msg().get(player, "button_exit"), "Exit"), null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /* -----------------------------
     * Handle Clicks
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) { player.closeInventory(); return; }

        // Security: Only Owner OR Admin can edit flags
        if (!plot.getOwner().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            return;
        }

        int slot = e.getSlot();

        switch (slot) {
            case 10: toggleFlag(player, plot, "pvp"); break;
            case 11: toggleFlag(player, plot, "mobs"); break;
            case 12: toggleFlag(player, plot, "containers"); break;
            case 13: toggleFlag(player, plot, "tnt-damage"); break;
            case 14: toggleFlag(player, plot, "fire-spread"); break;
            case 15: toggleFlag(player, plot, "piston-use"); break;
            case 19: toggleFlag(player, plot, "pets"); break;
            case 20: toggleFlag(player, plot, "entities"); break;
            case 21: toggleFlag(player, plot, "farm"); break;
            case 22: toggleFlag(player, plot, "entry"); break; // New Entry Logic
            
            case 17: // Safe Zone Special Logic
                plugin.protection().toggleSafeZone(plot, true);
                plugin.msg().send(player, plot.getFlag("safe_zone", true) ? "safe_zone_enabled" : "safe_zone_disabled");
                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;
            
            case 31: // FLIGHT TOGGLE
                toggleFlight(player, plot);
                break;

            case 34: // Cosmetics Menu
                plugin.gui().cosmetics().open(player, plot);
                plugin.effects().playMenuFlip(player);
                break;

            case 48: // Back
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;
            case 49: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
        }
    }

    private void toggleFlag(Player player, Plot plot, String flag) {
        boolean current = plot.getFlag(flag, true);
        plot.setFlag(flag, !current);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(player);
        open(player, plot); // Refresh
    }

    private void toggleFlight(Player player, Plot plot) {
        boolean current = plot.getFlag("fly", false);
        
        // If turning ON, check cost
        if (!current) {
            double cost = plugin.cfg().getFlightCost();
            // Charge if: Cost > 0 AND Player is NOT Admin
            if (cost > 0 && !plugin.isAdmin(player)) {
                if (!plugin.vault().charge(player, cost)) {
                    plugin.msg().send(player, "need_vault", Map.of("AMOUNT", plugin.vault().format(cost)));
                    plugin.effects().playError(player);
                    return;
                }
                plugin.msg().send(player, "cost_deducted", Map.of("AMOUNT", plugin.vault().format(cost)));
            }
        }

        plot.setFlag("fly", !current);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(player);
        
        // Instant update if inside
        if (plot.isInside(player.getLocation())) {
            player.setAllowFlight(!current);
            if (current) player.setFlying(false); 
            player.sendMessage(plugin.msg().get(player, !current ? "flight_enabled" : "flight_disabled"));
        }
        
        open(player, plot);
    }

    // Helper to generate button icons
    private void addFlagButton(Player p, Inventory inv, Plot plot, int slot, String flag, Material mat, String nameKey, String loreKey) {
        boolean state = plot.getFlag(flag, true);
        String name = plugin.msg().get(p, nameKey + (state ? "_on" : "_off"));
        if (name == null) name = "§7" + flag;
        
        List<String> lore = plugin.msg().getList(p, loreKey);
        if (lore == null) lore = new ArrayList<>();
        
        inv.setItem(slot, GUIManager.icon(mat, name, lore));
    }
    
    private List<String> replacePlaceholder(List<String> list, String key, String value) {
        if (list == null) return new ArrayList<>();
        List<String> newList = new ArrayList<>();
        for (String s : list) newList.add(s.replace(key, value));
        return newList;
    }
}
