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

        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, "§c§lHigh Guardian Tools");
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // --- SETTINGS TOGGLES ---
        addToggle(inv, 10, "admin.auto_remove_banned", "Auto-Remove Banned", Material.TNT, false);
        addToggle(inv, 11, "admin.bypass_claim_limit", "Bypass Limits", Material.NETHER_STAR, false);
        addToggle(inv, 12, "admin.broadcast_admin_actions", "Broadcast Actions", Material.BEACON, false);
        addToggle(inv, 13, "admin.unlimited_plots", "Unlimited Plots", Material.EMERALD_BLOCK, true);
        addToggle(inv, 14, "sync.proxy.enabled", "Global Sync", Material.ENDER_EYE, false);
        addToggle(inv, 15, "performance.low_overhead_mode", "Performance Mode", Material.REDSTONE_BLOCK, false);

        // --- TOOLS ---
        inv.setItem(28, GUIManager.createItem(Material.AMETHYST_CLUSTER, "§dExpansion Admin", List.of("§7Review pending land requests.")));
        inv.setItem(29, GUIManager.createItem(Material.WRITABLE_BOOK, "§bGlobal Plot List", List.of("§7View/TP to any plot.")));
        inv.setItem(30, GUIManager.createItem(Material.COMPASS, "§bDiagnostics", List.of("§7View system stats.")));
        inv.setItem(31, GUIManager.createItem(Material.REPEATER, "§eReload Config", List.of("§7Reload all settings.")));

        // --- NAVIGATION ---
        inv.setItem(36, GUIManager.createItem(Material.ARROW, "§fBack to Menu", List.of("§7Return to main menu.")));
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, "§cExit", List.of("§7Close menu.")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        switch (e.getSlot()) {
            // Toggles
            case 10: flipBool("admin.auto_remove_banned", false); open(player); break;
            case 11: flipBool("admin.bypass_claim_limit", false); open(player); break;
            case 12: flipBool("admin.broadcast_admin_actions", false); open(player); break;
            case 13: flipBool("admin.unlimited_plots", true); open(player); break;
            case 14: flipBool("sync.proxy.enabled", false); open(player); break;
            case 15: flipBool("performance.low_overhead_mode", false); open(player); break;

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

    private void addToggle(Inventory inv, int slot, String path, String name, Material mat, boolean def) {
        boolean val = plugin.getConfig().getBoolean(path, def);
        String color = val ? "§a" : "§7";
        String status = val ? "ON" : "OFF";
        
        // FIX: Replaced invalid method call with Material enum logic
        Material icon = val ? mat : Material.GRAY_DYE; 

        inv.setItem(slot, GUIManager.createItem(icon, color + name + ": " + status, List.of("§7Click to toggle.")));
    }
    
    private void flipBool(String path, boolean def) {
        boolean current = plugin.getConfig().getBoolean(path, def);
        plugin.getConfig().set(path, !current);
        plugin.saveConfig();
        plugin.cfg().reload(); // Update cached values in AGConfig
    }
}
