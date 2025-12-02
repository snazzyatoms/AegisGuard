package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * AdminGUI
 * - Central control panel for server administrators.
 * - Fully localized for language switching.
 */
public class AdminGUI {

    private final AegisGuard plugin;

    public AdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class AdminHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (!plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        String title = GUIManager.safeText(plugin.msg().get(player, "admin_menu_title"), "§c§lHigh Guardian Tools");
        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, title);
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // --- SETTINGS TOGGLES ---
        // Each toggle uses dynamic keys: "button_admin_auto_remove", "admin_auto_remove_enabled", etc.
        addToggle(player, inv, 10, "admin.auto_remove_banned", "button_admin_auto_remove", "admin_auto_remove_lore", Material.TNT, false);
        addToggle(player, inv, 11, "admin.bypass_claim_limit", "button_admin_bypass_limit", "admin_bypass_limit_lore", Material.NETHER_STAR, false);
        addToggle(player, inv, 12, "admin.broadcast_admin_actions", "button_admin_broadcast", "admin_broadcast_lore", Material.BEACON, false);
        addToggle(player, inv, 13, "admin.unlimited_plots", "button_admin_unlimited", "admin_unlimited_lore", Material.EMERALD_BLOCK, true);
        addToggle(player, inv, 14, "sync.proxy.enabled", "button_admin_sync", "admin_sync_lore", Material.ENDER_EYE, false);
        addToggle(player, inv, 15, "performance.low_overhead_mode", "button_admin_perf", "admin_perf_lore", Material.REDSTONE_BLOCK, false);

        // --- TOOLS ---
        inv.setItem(28, GUIManager.createItem(Material.AMETHYST_CLUSTER, 
            plugin.msg().get(player, "button_view_requests_admin"), 
            plugin.msg().getList(player, "view_requests_admin_lore")));
            
        inv.setItem(29, GUIManager.createItem(Material.WRITABLE_BOOK, 
            plugin.msg().get(player, "admin_plot_list_title"), 
            List.of("§7View/TP to any plot."))); // Can add "admin_plot_list_lore" later if needed

        inv.setItem(30, GUIManager.createItem(Material.COMPASS, 
            plugin.msg().get(player, "button_admin_diagnostics", "§bDiagnostics"), 
            List.of("§7View system stats.")));

        inv.setItem(31, GUIManager.createItem(Material.REPEATER, 
            plugin.msg().get(player, "button_admin_reload", "§eReload Config"), 
            List.of("§7Reload all settings.")));

        // --- NAVIGATION ---
        inv.setItem(36, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back_menu"), 
            plugin.msg().getList(player, "back_menu_lore")));
            
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, 
            plugin.msg().get(player, "button_exit"), 
            plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        switch (e.getSlot()) {
            // Toggles
            case 10: flipBool(player, "admin.auto_remove_banned", "admin_auto_remove_enabled", "admin_auto_remove_disabled", false); open(player); break;
            case 11: flipBool(player, "admin.bypass_claim_limit", "admin_bypass_enabled", "admin_bypass_disabled", false); open(player); break;
            case 12: flipBool(player, "admin.broadcast_admin_actions", "admin_broadcast_enabled", "admin_broadcast_disabled", false); open(player); break;
            case 13: flipBool(player, "admin.unlimited_plots", null, null, true); open(player); break;
            case 14: flipBool(player, "sync.proxy.enabled", null, null, false); open(player); break;
            case 15: flipBool(player, "performance.low_overhead_mode", null, null, false); open(player); break;

            // Tools
            case 28: 
                plugin.gui().expansionAdmin().open(player); 
                plugin.effects().playMenuFlip(player);
                break;
                
            case 29:
                plugin.gui().plotList().open(player, 0); 
                plugin.effects().playMenuFlip(player);
                break;

            case 30: 
                plugin.gui().openDiagnostics(player); 
                plugin.effects().playMenuFlip(player);
                break;

            case 31: // Reload
                plugin.msg().send(player, "admin_reloading");
                plugin.runGlobalAsync(() -> {
                    plugin.cfg().reload();
                    plugin.msg().reload();
                    plugin.worldRules().reload();
                    plugin.store().load();
                    plugin.runMain(player, () -> {
                        plugin.msg().send(player, "admin_reload_complete");
                        plugin.effects().playConfirm(player);
                        open(player);
                    });
                });
                break;

            case 36: plugin.gui().openMain(player); break;
            case 44: player.closeInventory(); break;
        }
    }

    // --- HELPERS ---

    private void addToggle(Player p, Inventory inv, int slot, String path, String nameKey, String loreKey, Material mat, boolean def) {
        boolean val = plugin.getConfig().getBoolean(path, def);
        
        String name = plugin.msg().get(p, nameKey);
        if (name == null) name = "Setting"; // Fallback
        
        String status = val ? "§aON" : "§cOFF";
        Material icon = val ? mat : Material.GRAY_DYE; 

        inv.setItem(slot, GUIManager.createItem(icon, name + ": " + status, plugin.msg().getList(p, loreKey)));
    }
    
    private void flipBool(Player p, String path, String msgOn, String msgOff, boolean def) {
        boolean current = plugin.getConfig().getBoolean(path, def);
        boolean next = !current;
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.cfg().reload(); 
        
        // Optional feedback message if keys provided
        if (next && msgOn != null) plugin.msg().send(p, msgOn);
        if (!next && msgOff != null) plugin.msg().send(p, msgOff);
    }
}
