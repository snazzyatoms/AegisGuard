package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

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

        // ✅ Title fix: translate & colors + safe fallback + clamp length
        String rawTitle = (plugin.codex() != null)
                ? plugin.codex().tr(player, "admin_menu_title")
                : null;

        String title = formatTitle(rawTitle, "&c&l⚔ High Guardian Tools ⚔");
        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // --- SETTINGS TOGGLES ---
        addToggle(player, inv, 10, "admin.auto_remove_banned", "button_admin_auto_remove", "admin_auto_remove_lore", Material.TNT, false);
        addToggle(player, inv, 11, "admin.bypass_claim_limit", "button_admin_bypass_limit", "admin_bypass_limit_lore", Material.NETHER_STAR, false);
        addToggle(player, inv, 12, "admin.broadcast_admin_actions", "button_admin_broadcast", "admin_broadcast_lore", Material.BEACON, false);
        addToggle(player, inv, 13, "admin.unlimited_plots", "button_admin_unlimited", "admin_unlimited_lore", Material.EMERALD_BLOCK, true);
        addToggle(player, inv, 14, "sync.proxy.enabled", "button_admin_sync", "admin_sync_lore", Material.ENDER_EYE, false);
        addToggle(player, inv, 15, "performance.low_overhead_mode", "button_admin_perf", "admin_perf_lore", Material.REDSTONE_BLOCK, false);

        // --- TOOLS ---
        inv.setItem(28, GUIManager.createItem(
                Material.AMETHYST_CLUSTER,
                plugin.codex().tr(player, "button_view_requests_admin"),
                plugin.codex().list(player, "view_requests_admin_lore")
        ));

        inv.setItem(29, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.codex().tr(player, "admin_plot_list_title"),
                List.of("§7View/TP to any plot.")
        ));

        String diagName = GUIManager.safeText(
                plugin.codex().tr(player, "button_admin_diagnostics"),
                "§bDiagnostics"
        );
        inv.setItem(30, GUIManager.createItem(
                Material.COMPASS,
                diagName,
                List.of("§7View system stats.")
        ));

        String reloadName = GUIManager.safeText(
                plugin.codex().tr(player, "button_admin_reload"),
                "§eReload Config"
        );
        inv.setItem(31, GUIManager.createItem(
                Material.REPEATER,
                reloadName,
                List.of("§7Reload all settings.")
        ));

        // --- NAVIGATION ---
        inv.setItem(36, GUIManager.createItem(
                Material.ARROW,
                plugin.codex().tr(player, "button_back_menu"),
                plugin.codex().list(player, "back_menu_lore")
        ));

        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                plugin.codex().tr(player, "button_exit"),
                plugin.codex().list(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        switch (e.getSlot()) {
            case 10:
                flipBool(player, "admin.auto_remove_banned", "admin_auto_remove_enabled", "admin_auto_remove_disabled", false);
                open(player);
                break;
            case 11:
                flipBool(player, "admin.bypass_claim_limit", "admin_bypass_enabled", "admin_bypass_disabled", false);
                open(player);
                break;
            case 12:
                flipBool(player, "admin.broadcast_admin_actions", "admin_broadcast_enabled", "admin_broadcast_disabled", false);
                open(player);
                break;
            case 13:
                flipBool(player, "admin.unlimited_plots", null, null, true);
                open(player);
                break;
            case 14:
                flipBool(player, "sync.proxy.enabled", null, null, false);
                open(player);
                break;
            case 15:
                flipBool(player, "performance.low_overhead_mode", null, null, false);
                open(player);
                break;

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

            case 31:
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

            case 36:
                plugin.gui().openMain(player);
                break;
            case 44:
                player.closeInventory();
                break;
        }
    }

    // --- HELPERS ---

    private void addToggle(Player p, Inventory inv, int slot, String path, String nameKey, String loreKey, Material mat, boolean def) {
        boolean val = plugin.getConfig().getBoolean(path, def);

        String name = plugin.codex().tr(p, nameKey);
        if (name == null) name = "Setting";

        String status = val ? "§aON" : "§cOFF";
        Material icon = val ? mat : Material.GRAY_DYE;

        inv.setItem(slot, GUIManager.createItem(
                icon,
                name + ": " + status,
                plugin.codex().list(p, loreKey)
        ));
    }

    private void flipBool(Player p, String path, String msgOn, String msgOff, boolean def) {
        boolean current = plugin.getConfig().getBoolean(path, def);
        boolean next = !current;
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.cfg().reload();

        if (next && msgOn != null) plugin.msg().send(p, msgOn);
        if (!next && msgOff != null) plugin.msg().send(p, msgOff);
    }

    // ✅ Central title cleanup for THIS GUI (we'll reuse this pattern in each GUI file)
    private String formatTitle(String raw, String fallback) {
        String t = GUIManager.safeText(raw, fallback);
        t = ChatColor.translateAlternateColorCodes('&', t);

        // Vanilla inventory titles are short; clamp to avoid IllegalArgumentException
        if (t.length() > 32) t = t.substring(0, 32);

        // If we clipped right after a section sign, remove it
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);

        return t;
    }
}
