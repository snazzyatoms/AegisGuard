package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.economy.CurrencyType;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
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
 * - Manages protection settings for an Estate.
 * - Updated for v1.3.0 Estate System.
 */
public class PlotFlagsGUI {

    private final AegisGuard plugin;

    public PlotFlagsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotFlagsHolder implements InventoryHolder {
        private final Estate estate;
        public PlotFlagsHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        
        if (estate == null) {
            player.sendMessage(lang.getMsg(player, "no_plot_here"));
            return;
        }

        // Title: "Estate Settings"
        String title = lang.getGui("title_flags"); 
        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(estate), 54, title);

        // --- 1. GLASS BORDER ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,50,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. DANGER (Row 2) ---
        addFlagButton(player, inv, estate, 10, "pvp", Material.IRON_SWORD, "button_pvp", "pvp_toggle_lore");
        addFlagButton(player, inv, estate, 11, "tnt-damage", Material.TNT, "button_tnt", "tnt_toggle_lore");
        addFlagButton(player, inv, estate, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire", "fire_toggle_lore");
        
        addFlagButton(player, inv, estate, 14, "mobs", Material.ZOMBIE_HEAD, "button_mobs", "mob_toggle_lore");
        addFlagButton(player, inv, estate, 15, "entry", Material.OAK_FENCE_GATE, "button_entry", "entry_toggle_lore"); // Lockdown
        
        // Safe Zone (Admin Only Toggle)
        if (plugin.isAdmin(player)) {
            addFlagButton(player, inv, estate, 16, "safe_zone", Material.SHIELD, "button_safe", "safe_toggle_lore"); 
        }

        // --- 3. MECHANICS (Row 3) ---
        addFlagButton(player, inv, estate, 19, "containers", Material.CHEST, "button_containers", "container_toggle_lore");
        addFlagButton(player, inv, estate, 20, "piston-use", Material.PISTON, "button_piston", "piston_toggle_lore");
        addFlagButton(player, inv, estate, 21, "farm", Material.WHEAT, "button_farm", "farm_toggle_lore");
        
        addFlagButton(player, inv, estate, 23, "pets", Material.BONE, "button_pets", "pet_toggle_lore");
        addFlagButton(player, inv, estate, 24, "entities", Material.ARMOR_STAND, "button_entity", "entity_toggle_lore");
        
        // Shop Interact (Paid Flag)
        double shopCost = plugin.getConfig().getDouble("economy.flag_costs.shop-interact", 500.0);
        String shopCostStr = (shopCost > 0 && !plugin.isAdmin(player)) ? plugin.getEconomy().format(shopCost, CurrencyType.VAULT) : "Free";
        addPaidFlagButton(player, inv, estate, 25, "shop-interact", Material.EMERALD, "button_shop", "shop_toggle_lore", shopCostStr);

        // --- 4. PREMIUM (Row 4) ---
        
        // Flight
        boolean canFly = estate.getFlag("fly");
        double flyCost = plugin.getConfig().getDouble("economy.flag_costs.fly", 5000.0);
        String costString = (flyCost > 0 && !plugin.isAdmin(player)) ? plugin.getEconomy().format(flyCost, CurrencyType.VAULT) : "Free";

        ItemStack flyIcon = GUIManager.createItem(
            Material.FEATHER,
            lang.getMsg(player, "button_fly_" + (canFly ? "on" : "off")),
            replacePlaceholder(lang.getMsgList(player, "fly_toggle_lore"), "{COST}", costString)
        );
        if (canFly) addGlow(flyIcon);
        inv.setItem(30, flyIcon);

        // Cosmetics Link
        inv.setItem(31, GUIManager.createItem(
            Material.NETHER_STAR,
            lang.getGui("button_cosmetics"),
            lang.getMsgList(player, "cosmetics_lore")
        ));
        
        // --- 5. NAVIGATION ---
        inv.setItem(48, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));

        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Estate estate = holder.getEstate();
        if (estate == null) { player.closeInventory(); return; }

        // Permission Check
        if (!estate.getOwnerId().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
            player.closeInventory();
            return;
        }

        switch (e.getSlot()) {
            // Danger
            case 10: toggleFlag(player, estate, "pvp"); break;
            case 11: toggleFlag(player, estate, "tnt-damage"); break;
            case 12: toggleFlag(player, estate, "fire-spread"); break;
            case 14: toggleFlag(player, estate, "mobs"); break;
            case 15: toggleFlag(player, estate, "entry"); break;
            case 16: // Safe Zone
                if (plugin.isAdmin(player)) {
                    estate.setFlag("safe_zone", !estate.getFlag("safe_zone"));
                    // plugin.getEstateManager().saveEstate(estate);
                    open(player, estate);
                }
                break;

            // Mechanics
            case 19: toggleFlag(player, estate, "containers"); break;
            case 20: toggleFlag(player, estate, "piston-use"); break;
            case 21: toggleFlag(player, estate, "farm"); break;
            case 23: toggleFlag(player, estate, "pets"); break;
            case 24: toggleFlag(player, estate, "entities"); break;
            
            // Paid Flags
            case 25: togglePaidFlag(player, estate, "shop-interact", plugin.getConfig().getDouble("economy.flag_costs.shop-interact", 500.0)); break;
            case 30: togglePaidFlag(player, estate, "fly", plugin.getConfig().getDouble("economy.flag_costs.fly", 5000.0)); break;
            
            // Sub-Menus
            case 31: // Cosmetics
                plugin.getGuiManager().cosmetics().open(player, estate); // Make sure you add cosmetics() getter to GuiManager
                break;
            
            // Nav
            case 48: plugin.getGuiManager().openGuardianCodex(player); break;
            case 49: player.closeInventory(); break;
        }
    }
    
    // --- Helpers ---
    private void toggleFlag(Player player, Estate estate, String flag) {
        boolean current = estate.getFlag(flag);
        estate.setFlag(flag, !current);
        // plugin.getEstateManager().saveEstate(estate);
        
        String status = !current ? "§aON" : "§cOFF";
        // plugin.getLanguageManager().send(player, "flag_toggled", Map.of("FLAG", flag.toUpperCase(), "STATUS", status));
        
        // plugin.effects().playConfirm(player);
        open(player, estate);
    }

    private void togglePaidFlag(Player player, Estate estate, String flag, double cost) {
        boolean current = estate.getFlag(flag);
        
        if (!current && cost > 0 && !plugin.isAdmin(player)) {
            if (!plugin.getEconomy().withdraw(player, cost)) {
                player.sendMessage(plugin.getLanguageManager().getMsg(player, "claim_failed_money"));
                return;
            }
            player.sendMessage("§aPaid " + plugin.getEconomy().format(cost, CurrencyType.VAULT));
        }

        estate.setFlag(flag, !current);
        // plugin.getEstateManager().saveEstate(estate);
        
        if (flag.equals("fly") && estate.getRegion().contains(player.getLocation())) {
            player.setAllowFlight(!current);
            if (current) player.setFlying(false);
        }
        
        open(player, estate);
    }
    
    private void addFlagButton(Player p, Inventory inv, Estate estate, int slot, String flag, Material mat, String nameKey, String loreKey) {
        boolean state = estate.getFlag(flag);
        String name = plugin.getLanguageManager().getMsg(p, nameKey + (state ? "_on" : "_off"));
        if (name.contains("Missing")) name = "§7" + flag + ": " + (state ? "§aON" : "§cOFF");
        
        List<String> lore = plugin.getLanguageManager().getMsgList(p, loreKey);
        ItemStack item = GUIManager.createItem(mat, name, lore);
        if (state) addGlow(item);
        
        inv.setItem(slot, item);
    }
    
    private void addPaidFlagButton(Player p, Inventory inv, Estate estate, int slot, String flag, Material mat, String nameKey, String loreKey, String costStr) {
        boolean state = estate.getFlag(flag);
        String name = plugin.getLanguageManager().getMsg(p, nameKey + (state ? "_on" : "_off"));
        if (name.contains("Missing")) name = "§7" + flag + ": " + (state ? "§aON" : "§cOFF");
        
        List<String> lore = plugin.getLanguageManager().getMsgList(p, loreKey);
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
