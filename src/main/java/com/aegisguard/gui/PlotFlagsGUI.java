package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
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
 * - UPDATED: Biome Changer with Disclaimer & Admin Bypass.
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

        // --- 1. GLASS BORDER (Frame) ---
        int[] borderSlots = {0,1,2,3,4,5,6,7,8,  9,17,  18,26,  27,35,  36,44,  45,46,47,50,51,52,53};
        for (int i : borderSlots) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // --- 2. DANGER & COMBAT (Row 2) ---
        addFlagButton(player, inv, plot, 10, "pvp", Material.IRON_SWORD, "button_pvp", "pvp_toggle_lore");
        addFlagButton(player, inv, plot, 11, "tnt-damage", Material.TNT, "button_tnt", "tnt_toggle_lore");
        addFlagButton(player, inv, plot, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire", "fire_toggle_lore");
        // (Gap)
        addFlagButton(player, inv, plot, 14, "mobs", Material.ZOMBIE_HEAD, "button_mobs", "mob_toggle_lore");
        addFlagButton(player, inv, plot, 15, "entry", Material.OAK_FENCE_GATE, "button_entry", "entry_toggle_lore"); // Lockdown
        addFlagButton(player, inv, plot, 16, "safe_zone", Material.SHIELD, "button_safe", "safe_toggle_lore"); // Master Switch

        // --- 3. ENTITIES & MECHANICS (Row 3) ---
        addFlagButton(player, inv, plot, 19, "containers", Material.CHEST, "button_containers", "container_toggle_lore");
        addFlagButton(player, inv, plot, 20, "piston-use", Material.PISTON, "button_piston", "piston_toggle_lore");
        addFlagButton(player, inv, plot, 21, "farm", Material.WHEAT, "button_farm", "farm_toggle_lore");
        // (Gap)
        addFlagButton(player, inv, plot, 23, "pets", Material.BONE, "button_pets", "pet_toggle_lore");
        addFlagButton(player, inv, plot, 24, "entities", Material.ARMOR_STAND, "button_entity", "entity_toggle_lore");

        // --- 4. PREMIUM & EXTRAS (Row 4) ---
        // Flight
        boolean canFly = plot.getFlag("fly", false);
        double flyCost = plugin.cfg().getFlightCost();
        String costString = (flyCost > 0) ? plugin.vault().format(flyCost) : "Free";
        if (plugin.isAdmin(player)) costString = "§cAdmin Bypass";

        inv.setItem(30, GUIManager.icon(
            Material.FEATHER,
            canFly ? plugin.msg().get(player, "button_fly_on") : plugin.msg().get(player, "button_fly_off"),
            replacePlaceholder(plugin.msg().getList(player, "fly_toggle_lore"), "{COST}", costString)
        ));

        // Cosmetics
        inv.setItem(31, GUIManager.icon(
            Material.NETHER_STAR,
            GUIManager.safeText(plugin.msg().get(player, "button_cosmetics"), "§dCosmetics"),
            plugin.msg().getList(player, "cosmetics_lore")
        ));
        
        // Biome Changer (Slot 32 - Now with Disclaimer)
        inv.setItem(32, GUIManager.icon(
            Material.GRASS_BLOCK,
            GUIManager.safeText(plugin.msg().get(player, "biome_gui_title"), "§2Change Biome"),
            plugin.msg().getList(player, "biome_button_lore") // <-- Uses config message with disclaimer
        ));

        // --- 5. NAVIGATION ---
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
        // FIX: This allows OPs/Admins to edit any plot (including Server Zones)
        if (!plot.getOwner().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            return;
        }

        int slot = e.getSlot();

        switch (slot) {
            // Row 2 (Danger)
            case 10: toggleFlag(player, plot, "pvp"); break;
            case 11: toggleFlag(player, plot, "tnt-damage"); break;
            case 12: toggleFlag(player, plot, "fire-spread"); break;
            case 14: toggleFlag(player, plot, "mobs"); break;
            case 15: toggleFlag(player, plot, "entry"); break;
            case 16: // Safe Zone Logic
                plugin.protection().toggleSafeZone(plot, true);
                plugin.msg().send(player, plot.getFlag("safe_zone", true) ? "safe_zone_enabled" : "safe_zone_disabled");
                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;

            // Row 3 (Entities)
            case 19: toggleFlag(player, plot, "containers"); break;
            case 20: toggleFlag(player, plot, "piston-use"); break;
            case 21: toggleFlag(player, plot, "farm"); break;
            case 23: toggleFlag(player, plot, "pets"); break;
            case 24: toggleFlag(player, plot, "entities"); break;
            
            // Row 4 (Premium)
            case 30: toggleFlight(player, plot); break; // Flight
            
            case 31: // Cosmetics
                plugin.gui().cosmetics().open(player, plot); 
                plugin.effects().playMenuFlip(player); 
                break;
            
            case 32: // Biome Changer
                if (plugin.cfg().isBiomesEnabled()) {
                    plugin.gui().biomes().open(player, plot);
                    plugin.effects().playMenuFlip(player);
                } else {
                    player.sendMessage("§cBiome changing is disabled on this server.");
                    plugin.effects().playError(player);
                }
                break;

            // Navigation
            case 48: plugin.gui().openMain(player); plugin.effects().playMenuFlip(player); break;
            case 49: player.closeInventory(); plugin.effects().playMenuClose(player); break;
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
                CurrencyType type = CurrencyType.VAULT; 
                
                if (!plugin.eco().withdraw(player, cost, type)) {
                    plugin.msg().send(player, "need_vault", Map.of("AMOUNT", plugin.eco().format(cost, type)));
                    plugin.effects().playError(player);
                    return;
                }
                plugin.msg().send(player, "cost_deducted", Map.of("AMOUNT", plugin.eco().format(cost, type)));
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
