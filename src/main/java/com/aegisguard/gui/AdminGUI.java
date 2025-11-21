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

/**
 * AdminGUI
 * Fixed for compilation errors and cleaned up.
 */
public class AdminGUI {

    private final AegisGuard plugin;

    public AdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // --- FIX: Must be PUBLIC so GUIListener can access it ---
    public static class AdminHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private String title(Player player) {
        String raw = plugin.msg().get("admin_menu_title"); // Removed player arg if not needed, or keep if your MsgUtil supports it
        if (raw != null && !raw.contains("[Missing")) return raw;
        return "§b🛡 AegisGuard — Admin";
    }

    private ItemStack bg() {
        return GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }

    private boolean getBool(String path, boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }

    /**
     * Flips a boolean in the config and saves it ASYNCHRONOUSLY
     * Folia-Safe.
     */
    private boolean flipBoolAsync(String path, boolean def) {
        boolean cur = getBool(path, def);
        boolean next = !cur;
        plugin.getConfig().set(path, next);

        // Save the config to disk on an async thread
        plugin.runGlobalAsync(() -> {
            plugin.saveConfig();
        });
        return next;
    }

    public void open(Player player) {
        if (!player.hasPermission("aegis.admin")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, title(player));
        
        ItemStack bg = bg();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        // Read toggles
        boolean autoRemove = getBool("admin.auto_remove_banned", false);
        boolean bypass     = getBool("admin.bypass_claim_limit", false);
        boolean broadcast  = getBool("admin.broadcast_admin_actions", false);
        boolean unlimited  = getBool("admin.unlimited_plots", true);
        boolean proxySync  = getBool("sync.proxy.enabled", false);
        boolean perfMode   = getBool("performance.low_overhead_mode", false);

        // Row 2 — core toggles
        inv.setItem(10, GUIManager.icon(
                autoRemove ? Material.TNT : Material.GUNPOWDER,
                autoRemove ? "§aAuto-Remove Banned: §aON" : "§7Auto-Remove Banned: §cOFF",
                plugin.msg().getList(player, "admin_auto_remove_lore")
        ));

        inv.setItem(12, GUIManager.icon(
                bypass ? Material.NETHER_STAR : Material.IRON_NUGGET,
                bypass ? "§aBypass Claim Limit (OP): §aON" : "§7Bypass Claim Limit (OP): §cOFF",
                plugin.msg().getList(player, "admin_bypass_limit_lore")
        ));

        inv.setItem(14, GUIManager.icon(
                broadcast ? Material.BEACON : Material.LIGHT,
                broadcast ? "§aBroadcast Admin Actions: §aON" : "§7Broadcast Admin Actions: §cOFF",
                plugin.msg().getList(player, "admin_broadcast_lore")
        ));

        // Row 3 — admin power & sync
        inv.setItem(19, GUIManager.icon(
                unlimited ? Material.EMERALD_BLOCK : Material.EMERALD,
                unlimited ? "§aUnlimited Plots (Admin): §aON" : "§7Unlimited Plots (Admin): §cOFF",
                List.of("§7Admins can create unlimited plots/claims.")
        ));

        inv.setItem(21, GUIManager.icon(
                proxySync ? Material.ENDER_EYE : Material.ENDER_PEARL,
                proxySync ? "§aGlobal Sync (Proxy): §aON" : "§7Global Sync (Proxy): §cOFF",
                List.of("§7Enable Bungee/proxy sync for claims/flags.", "§8(Requires SyncBridge setup)")
        ));

        inv.setItem(23, GUIManager.icon(
                perfMode ? Material.REDSTONE_BLOCK : Material.REDSTONE,
                perfMode ? "§aPerformance Mode: §aON" : "§7Performance Mode: §cOFF",
                List.of("§7Disables non-essential cosmetics.", "§7Great for large servers.")
        ));

        // Row 4 — tools & navigation
        inv.setItem(28, GUIManager.icon(
                Material.AMETHYST_CLUSTER,
                "§dExpansion Admin",
                List.of("§7Open Expansion admin preview.")
        ));

        inv.setItem(30, GUIManager.icon(
                Material.COMPASS,
                "§bDiagnostics",
                List.of("§7Show TPS, listener counts, last sync time,", "§7and adapter statuses.")
        ));

        inv.setItem(31, GUIManager.icon(
                Material.REPEATER,
                "§eReload Config",
                List.of("§7Reload all config files", "§7and data from storage.")
        ));

        inv.setItem(34, GUIManager.icon(
                Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")
        ));

        inv.setItem(40, GUIManager.icon(
                Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // --- FIX: Switched to standard 'case:' syntax for compatibility ---
        switch (e.getSlot()) {
            // Toggles
            case 10:
                boolean ar = flipBoolAsync("admin.auto_remove_banned", false);
                plugin.msg().send(player, ar ? "admin_auto_remove_enabled" : "admin_auto_remove_disabled");
                plugin.effects().playMenuFlip(player);
                open(player);
                break;

            case 12:
                boolean by = flipBoolAsync("admin.bypass_claim_limit", false);
                plugin.msg().send(player, by ? "admin_bypass_enabled" : "admin_bypass_disabled");
                plugin.effects().playMenuFlip(player);
                open(player);
                break;

            case 14:
                boolean br = flipBoolAsync("admin.broadcast_admin_actions", false);
                plugin.msg().send(player, br ? "admin_broadcast_enabled" : "admin_broadcast_disabled");
                plugin.effects().playMenuFlip(player);
                open(player);
                break;

            case 19:
                boolean un = flipBoolAsync("admin.unlimited_plots", true);
                plugin.msg().send(player, un ? "admin_unlimited_enabled" : "admin_unlimited_disabled");
                plugin.effects().playMenuFlip(player);
                open(player);
                break;

            case 21:
                boolean ps = flipBoolAsync("sync.proxy.enabled", false);
                plugin.msg().send(player, ps ? "admin_proxy_sync_enabled" : "admin_proxy_sync_disabled");
                plugin.effects().playMenuFlip(player);
                open(player);
                break;

            case 23:
                boolean pm = flipBoolAsync("performance.low_overhead_mode", false);
                plugin.msg().send(player, pm ? "admin_perf_mode_enabled" : "admin_perf_mode_disabled");
                plugin.effects().playMenuFlip(player);
                open(player);
                break;

            // Tools
            case 28: // Expansion
                // Ensure your GUIManager has this method, or comment this out
                // plugin.gui().expansionAdmin().open(player);
                player.sendMessage("§cFeature pending GUIManager update."); 
                break;

            case 30: // Diagnostics
                // plugin.gui().openDiagnostics(player);
                player.sendMessage("§cFeature pending GUIManager update.");
                plugin.effects().playMenuFlip(player);
                break;

            case 31: // Reload
                plugin.msg().send(player, "admin_reloading");
                plugin.effects().playMenuFlip(player);

                plugin.runGlobalAsync(() -> {
                    // 1. Reload config
                    plugin.cfg().reload();
                    // 2. Reload managers
                    plugin.msg().reload();
                    plugin.effects().reload();
                    plugin.worldRules().reload();
                    // 3. Reload data
                    plugin.msg().loadPlayerPreferences();
                    plugin.store().load();
                    plugin.getExpansionRequestManager().load();

                    // Finish on main thread
                    plugin.runMain(player, () -> {
                        plugin.msg().send(player, "admin_reload_complete");
                        open(player);
                    });
                });
                break;

            // Navigation
            case 34: // Back
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;

            case 40: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;

            default:
                break;
        }
    }
}
