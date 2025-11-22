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

    private ItemStack bg() { return GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null); }

    private boolean getBool(String path, boolean def) { return plugin.getConfig().getBoolean(path, def); }

    private void flipBoolAsync(String path, boolean def) {
        boolean next = !getBool(path, def);
        plugin.getConfig().set(path, next);
        plugin.runGlobalAsync(() -> plugin.saveConfig());
    }

    public void open(Player player) {
        if (!plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, "§c§lHigh Guardian Tools");
        
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg());

        boolean autoRemove = getBool("admin.auto_remove_banned", false);
        boolean bypass     = getBool("admin.bypass_claim_limit", false);
        boolean broadcast  = getBool("admin.broadcast_admin_actions", false);
        boolean unlimited  = getBool("admin.unlimited_plots", true);
        boolean proxySync  = getBool("sync.proxy.enabled", false);
        boolean perfMode   = getBool("performance.low_overhead_mode", false);

        inv.setItem(10, GUIManager.icon(autoRemove ? Material.TNT : Material.GUNPOWDER, autoRemove ? "§aAuto-Remove Banned: ON" : "§7Auto-Remove Banned: OFF", plugin.msg().getList(player, "admin_auto_remove_lore")));
        inv.setItem(12, GUIManager.icon(bypass ? Material.NETHER_STAR : Material.IRON_NUGGET, bypass ? "§aBypass Limits: ON" : "§7Bypass Limits: OFF", plugin.msg().getList(player, "admin_bypass_limit_lore")));
        inv.setItem(14, GUIManager.icon(broadcast ? Material.BEACON : Material.LIGHT, broadcast ? "§aBroadcast Actions: ON" : "§7Broadcast Actions: OFF", plugin.msg().getList(player, "admin_broadcast_lore")));

        inv.setItem(19, GUIManager.icon(unlimited ? Material.EMERALD_BLOCK : Material.EMERALD, unlimited ? "§aUnlimited Plots: ON" : "§7Unlimited Plots: OFF", List.of("§7Admins can create unlimited plots.")));
        inv.setItem(21, GUIManager.icon(proxySync ? Material.ENDER_EYE : Material.ENDER_PEARL, proxySync ? "§aGlobal Sync: ON" : "§7Global Sync: OFF", List.of("§7Sync data across network.")));
        inv.setItem(23, GUIManager.icon(perfMode ? Material.REDSTONE_BLOCK : Material.REDSTONE, perfMode ? "§aPerformance Mode: ON" : "§7Performance Mode: OFF", List.of("§7Disable cosmetics for speed.")));

        // --- TOOLS ---
        inv.setItem(28, GUIManager.icon(Material.AMETHYST_CLUSTER, "§dExpansion Admin", List.of("§7Review pending land requests.")));
        inv.setItem(30, GUIManager.icon(Material.COMPASS, "§bDiagnostics", List.of("§7View system stats.")));
        inv.setItem(31, GUIManager.icon(Material.REPEATER, "§eReload Config", List.of("§7Reload all settings.")));

        inv.setItem(34, GUIManager.icon(Material.ARROW, "§fBack to Menu", List.of("§7Return to main menu.")));
        inv.setItem(40, GUIManager.icon(Material.BARRIER, "§cExit", List.of("§7Close menu.")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        switch (e.getSlot()) {
            case 10: flipBoolAsync("admin.auto_remove_banned", false); open(player); break;
            case 12: flipBoolAsync("admin.bypass_claim_limit", false); open(player); break;
            case 14: flipBoolAsync("admin.broadcast_admin_actions", false); open(player); break;
            case 19: flipBoolAsync("admin.unlimited_plots", true); open(player); break;
            case 21: flipBoolAsync("sync.proxy.enabled", false); open(player); break;
            case 23: flipBoolAsync("performance.low_overhead_mode", false); open(player); break;

            // --- FIX: NOW OPENS THE ACTUAL MENUS ---
            case 28: 
                plugin.gui().expansionAdmin().open(player); 
                plugin.effects().playMenuFlip(player);
                break;

            case 30: 
                plugin.gui().openDiagnostics(player); 
                plugin.effects().playMenuFlip(player);
                break;

            case 31: 
                plugin.msg().send(player, "admin_reloading");
                plugin.runGlobalAsync(() -> {
                    plugin.cfg().reload();
                    plugin.msg().reload();
                    plugin.worldRules().reload();
                    plugin.store().load();
                    plugin.runMain(player, () -> {
                        plugin.msg().send(player, "admin_reload_complete");
                        open(player);
                    });
                });
                break;

            case 34: plugin.gui().openMain(player); plugin.effects().playMenuFlip(player); break;
            case 40: player.closeInventory(); plugin.effects().playMenuClose(player); break;
        }
    }
}
