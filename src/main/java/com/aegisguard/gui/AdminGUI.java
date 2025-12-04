package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminGUI
 * - Central control panel for server administrators.
 * - Manages v1.3.0 Modules and Settings.
 */
public class AdminGUI {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    public AdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "admin_action");
    }

    public static class AdminHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (!plugin.isAdmin(player)) {
            plugin.getLanguageManager().getMsg(player, "no_permission");
            return;
        }

        LanguageManager lang = plugin.getLanguageManager();
        // Title: "Admin Control Panel"
        String title = lang.getGui("title_admin"); 
        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, title);

        // Background Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // --- MODULE TOGGLES (Row 2) ---
        // 1. Private Estates
        addToggle(inv, 10, "modules.private_estates", 
            "&bPrivate Estates", Material.OAK_DOOR, "toggle_private");

        // 2. Guild System
        addToggle(inv, 11, "modules.guild_system", 
            "&6Guild System", Material.GOLDEN_HELMET, "toggle_guilds");

        // 3. Economy Engine
        addToggle(inv, 12, "modules.economy_engine", 
            "&eEconomy Engine", Material.GOLD_INGOT, "toggle_economy");

        // 4. Asset Liquidation
        addToggle(inv, 13, "modules.asset_liquidation", 
            "&aLiquidation Chute", Material.HOPPER, "toggle_liquidation");

        // 5. Progression System
        addToggle(inv, 14, "modules.progression_system", 
            "&dProgression System", Material.EXPERIENCE_BOTTLE, "toggle_progression");

        // --- TOOLS (Row 4) ---
        
        // Reload Config
        inv.setItem(29, createActionItem(Material.REDSTONE_BLOCK, 
            "&c&lReload Plugin", 
            List.of("&7Reloads Config, Lang, and Roles."), 
            "reload_plugin"));

        // Admin Wand
        inv.setItem(31, createActionItem(Material.BLAZE_ROD, 
            "&6Get Sentinel Wand", 
            List.of("&7Create Server Zones."), 
            "get_wand"));

        // Manage Petitions
        inv.setItem(33, createActionItem(Material.WRITABLE_BOOK, 
            "&bView Petitions", 
            List.of("&7Manage pending requests."), 
            "view_petitions"));

        // --- NAVIGATION ---
        inv.setItem(36, createActionItem(Material.ARROW, lang.getGui("button_back"), null, "back"));
        inv.setItem(44, createActionItem(Material.BARRIER, lang.getGui("button_close"), null, "close"));

        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        ItemMeta meta = e.getCurrentItem().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) return;

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        switch (action) {
            // --- TOGGLES ---
            case "toggle_private":
                flipBool("modules.private_estates"); open(player); break;
            case "toggle_guilds":
                flipBool("modules.guild_system"); open(player); break;
            case "toggle_economy":
                flipBool("modules.economy_engine"); open(player); break;
            case "toggle_liquidation":
                flipBool("modules.asset_liquidation"); open(player); break;
            case "toggle_progression":
                flipBool("modules.progression_system"); open(player); break;

            // --- ACTIONS ---
            case "reload_plugin":
                player.sendMessage("§eReloading AegisGuard v1.3.0...");
                plugin.cfg().reload();
                plugin.getLanguageManager().loadAllLocales();
                plugin.getRoleManager().loadAllRoles();
                player.sendMessage("§a✔ Configuration Reloaded.");
                player.closeInventory();
                break;

            case "get_wand":
                player.performCommand("agadmin wand");
                player.closeInventory();
                break;

            case "view_petitions":
                // Open Petition Admin GUI
                plugin.getGuiManager().petitionAdmin().open(player);
                break;

            case "back":
                // plugin.getGuiManager().openMainMenu(player);
                break;
                
            case "close":
                player.closeInventory();
                break;
        }
    }

    // --- HELPERS ---

    private void addToggle(Inventory inv, int slot, String configPath, String name, Material mat, String actionId) {
        boolean enabled = plugin.getConfig().getBoolean(configPath, true);
        
        String status = enabled ? "&a&lENABLED" : "&c&lDISABLED";
        Material icon = enabled ? mat : Material.GRAY_DYE;
        
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        lore.add("&7Status: " + status);
        lore.add(" ");
        lore.add("&eClick to Toggle");

        ItemStack item = createActionItem(icon, name, lore, actionId);
        
        // Add Glow if enabled
        if (enabled) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        
        inv.setItem(slot, item);
    }

    private void flipBool(String path) {
        boolean current = plugin.getConfig().getBoolean(path);
        plugin.getConfig().set(path, !current);
        plugin.saveConfig();
        plugin.cfg().reload();
    }

    private ItemStack createActionItem(Material mat, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
        
        if (lore != null) {
            List<String> colorLore = new ArrayList<>();
            for (String l : lore) colorLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', l));
            meta.setLore(colorLore);
        }
        
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
}
