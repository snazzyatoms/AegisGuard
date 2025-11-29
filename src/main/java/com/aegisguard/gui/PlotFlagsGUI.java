package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlotFlagsGUI
 * - Manages protection settings on a specific plot.
 * - FINAL FIX: Biome Changer access removed and logic standardized.
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

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        String title = GUIManager.safeText(plugin.msg().get(player, "plot_flags_title"), "§9Plot Flags");
        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot), 54, title);

        // --- 1. GLASS BORDER ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,50,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. DANGER (Row 2) ---
        addFlagButton(player, inv, plot, 10, "pvp", Material.IRON_SWORD, "button_pvp", "pvp_toggle_lore");
        addFlagButton(player, inv, plot, 11, "tnt-damage", Material.TNT, "button_tnt", "tnt_toggle_lore");
        addFlagButton(player, inv, plot, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire", "fire_toggle_lore");
        
        addFlagButton(player, inv, plot, 14, "mobs", Material.ZOMBIE_HEAD, "button_mobs", "mob_toggle_lore");
        addFlagButton(player, inv, plot, 15, "entry", Material.OAK_FENCE_GATE, "button_entry", "entry_toggle_lore"); // Lockdown
        addFlagButton(player, inv, plot, 16, "safe_zone", Material.SHIELD, "button_safe", "safe_toggle_lore"); // Admin Only usually

        // --- 3. MECHANICS (Row 3) ---
        addFlagButton(player, inv, plot, 19, "containers", Material.CHEST, "button_containers", "container_toggle_lore");
        addFlagButton(player, inv, plot, 20, "piston-use", Material.PISTON, "button_piston", "piston_toggle_lore");
        addFlagButton(player, inv, plot, 21, "farm", Material.WHEAT, "button_farm", "farm_toggle_lore");
        
        addFlagButton(player, inv, plot, 23, "pets", Material.BONE, "button_pets", "pet_toggle_lore");
        addFlagButton(player, inv, plot, 24, "entities", Material.ARMOR_STAND, "button_entity", "entity_toggle_lore");
        
        // Shop Interact (Paid Flag)
        double shopCost = plugin.cfg().getShopInteractCost();
        String shopCostStr = (shopCost > 0 && !plugin.isAdmin(player)) ? plugin.eco().format(shopCost, CurrencyType.VAULT) : "Free";
        addPaidFlagButton(player, inv, plot, 25, "shop-interact", Material.EMERALD, "button_shop", "shop_toggle_lore", shopCostStr);

        // --- 4. PREMIUM (Row 4) ---
        
        // Flight
        boolean canFly = plot.getFlag("fly", false);
        double flyCost = plugin.cfg().getFlightCost();
        String costString = (flyCost > 0 && !plugin.isAdmin(player)) ? plugin.eco().format(flyCost, CurrencyType.VAULT) : "Free";

        ItemStack flyIcon = GUIManager.createItem(
            Material.FEATHER,
            canFly ? plugin.msg().get(player, "button_fly_on") : plugin.msg().get(player, "button_fly_off"),
            replacePlaceholder(plugin.msg().getList(player, "fly_toggle_lore"), "{COST}", costString)
        );
        if (canFly) addGlow(flyIcon);
        inv.setItem(30, flyIcon);

        // Cosmetics
        inv.setItem(31, GUIManager.createItem(
            Material.NETHER_STAR,
            GUIManager.safeText(plugin.msg().get(player, "button_cosmetics"), "§dCosmetics"),
            plugin.msg().getList(player, "cosmetics_lore")
        ));
        
        // REMOVED: Biome Changer button (Slot 32)
        // This is where the old Biome button was. We remove it as requested.

        // --- 5. NAVIGATION ---
        inv.setItem(48, GUIManager.createItem(Material.ARROW, "§fBack", List.of("§7Return to dashboard.")));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, "§cExit", List.of("§7Close menu.")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) { player.closeInventory(); return; }

        if (!plot.getOwner().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            return;
        }

        switch (e.getSlot()) {
            // Danger
            case 10: toggleFlag(player, plot, "pvp"); break;
            case 11: toggleFlag(player, plot, "tnt-damage"); break;
            case 12: toggleFlag(player, plot, "fire-spread"); break;
            case 14: toggleFlag(player, plot, "mobs"); break;
            case 15: toggleFlag(player, plot, "entry"); break;
            case 16: // Safe Zone
                if (plugin.isAdmin(player)) {
                    plugin.protection().toggleSafeZone(plot, true);
                    plugin.effects().playMenuFlip(player);
                    open(player, plot);
                } else {
                    plugin.msg().send(player, "no_perm");
                }
                break;

            // Mechanics
            case 19: toggleFlag(player, plot, "containers"); break;
            case 20: toggleFlag(player, plot, "piston-use"); break;
            case 21: toggleFlag(player, plot, "farm"); break;
            case 23: toggleFlag(player, plot, "pets"); break;
            case 24: toggleFlag(player, plot, "entities"); break;
            
            // Paid Flags
            case 25: togglePaidFlag(player, plot, "shop-interact", plugin.cfg().getShopInteractCost()); break;
            case 30: togglePaidFlag(player, plot, "fly", plugin.cfg().getFlightCost()); break;
            
            // Sub-Menus
            case 31: plugin.gui().cosmetics().open(player, plot); break;
            // CASE 32 (Biome Changer) REMOVED
            
            // Nav
            case 48: plugin.gui().openMain(player); break;
            case 49: player.closeInventory(); break;
        }
    }

    private void toggleFlag(Player player, Plot plot, String flag) {
        boolean current = plot.getFlag(flag, true);
        plot.setFlag(flag, !current);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(player);
        open(player, plot);
    }

    private void togglePaidFlag(Player player, Plot plot, String flag, double cost) {
        boolean current = plot.getFlag(flag, false);
        
        // Only charge if turning ON
        if (!current && cost > 0 && !plugin.isAdmin(player)) {
            if (!plugin.eco().withdraw(player, cost, CurrencyType.VAULT)) {
                plugin.msg().send(player, "need_vault", Map.of("AMOUNT", plugin.eco().format(cost, CurrencyType.VAULT)));
                plugin.effects().playError(player);
                return;
            }
            plugin.msg().send(player, "cost_deducted", Map.of("AMOUNT", plugin.eco().format(cost, CurrencyType.VAULT)));
        }

        plot.setFlag(flag, !current);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(player);
        
        // Specific logic for flight
        if (flag.equals("fly") && plot.isInside(player.getLocation())) {
            player.setAllowFlight(!current);
            if (current) player.setFlying(false);
        }
        
        open(player, plot);
    }

    private void addFlagButton(Player p, Inventory inv, Plot plot, int slot, String flag, Material mat, String nameKey, String loreKey) {
        boolean state = plot.getFlag(flag, true);
        String name = plugin.msg().get(p, nameKey + (state ? "_on" : "_off"));
        if (name == null || name.isEmpty()) name = "§7" + flag + ": " + (state ? "§aON" : "§cOFF");
        
        List<String> lore = plugin.msg().getList(p, loreKey);
        ItemStack item = GUIManager.createItem(mat, name, lore);
        if (state) addGlow(item);
        
        inv.setItem(slot, item);
    }
    
    private void addPaidFlagButton(Player p, Inventory inv, Plot plot, int slot, String flag, Material mat, String nameKey, String loreKey, String costStr) {
        boolean state = plot.getFlag(flag, false);
        String name = plugin.msg().get(p, nameKey + (state ? "_on" : "_off"));
        
        List<String> lore = plugin.msg().getList(p, loreKey);
        lore = replacePlaceholder(lore, "{COST}", costStr);
        
        ItemStack item = GUIManager.createItem(mat, name, lore);
        if (state) addGlow(item);
        
        inv.setItem(slot, item);
    }
    
    private void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }
    
    private List<String> replacePlaceholder(List<String> list, String key, String value) {
        if (list == null) return new ArrayList<>();
        List<String> newList = new ArrayList<>();
        for (String s : list) newList.add(s.replace(key, value));
        return newList;
    }
}
