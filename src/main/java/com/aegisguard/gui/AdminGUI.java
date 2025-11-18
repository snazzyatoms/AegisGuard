package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.expansions.ExpansionRequestAdminGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * AdminGUI
 * --- UPGRADE NOTES ---
 * - PERMISSION FIX: Now correctly uses "aegis.admin"
 * - FOLIA FIX: All async tasks now use Folia-safe schedulers.
 * - SOUND FIX: Now uses plugin.effects()
 * - RELIABILITY FIX: Switched to slot-based clicks.
 */
public class AdminGUI {

    private final AegisGuard plugin;

    public AdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /** Tag holder so click handler only reacts to this GUI */
    private static class AdminHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private String title(Player player) {
        String raw = plugin.msg().get(player, "admin_menu_title");
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
     * --- FOLIA-SAFE ---
     */
    private boolean flipBoolAsync(String path, boolean def) {
        boolean cur = getBool(path, def);
        boolean next = !cur;
        plugin.getConfig().set(path, next);

        // --- FOLIA FIX ---
        // Save the config to disk on an async thread
        plugin.runGlobalAsync(() -> {
            plugin.saveConfig();
        });
        return next;
    }

    public void open(Player player) {
        // --- PERMISSION FIX ---
        if (!player.hasPermission("aegis.admin")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, title(player));
        // background
        var bg = bg();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        // Read toggles
        boolean autoRemove = getBool("admin.auto_remove_banned", false);
        boolean bypass     = getBool("admin.bypass_claim_limit", false);
        boolean broadcast  = getBool("admin.broadcast_admin_actions", false);
        boolean unlimited  = getBool("admin.unlimited_plots", true); // new: admin can create unlimited plots
        boolean proxySync  = getBool("sync.proxy.enabled", false);   // new: bungee/proxy sync toggle
        boolean perfMode   = getBool("performance.low_overhead_mode", false); // optional: trim cosmetics

        // Row 2 — core toggles
        // SLOT 10
        inv.setItem(10, GUIManager.icon(
                autoRemove ? Material.TNT : Material.GUNPOWDER,
                autoRemove ? "§aAuto-Remove Banned: §aON" : "§7Auto-Remove Banned: §cOFF",
                plugin.msg().getList(player, "admin_auto_remove_lore")
        ));

        // SLOT 12
        inv.setItem(12, GUIManager.icon(
                bypass ? Material.NETHER_STAR : Material.IRON_NUGGET,
                bypass ? "§aBypass Claim Limit (OP): §aON" : "§7Bypass Claim Limit (OP): §cOFF",
                plugin.msg().getList(player, "admin_bypass_limit_lore")
        ));

        // SLOT 14
        inv.setItem(14, GUIManager.icon(
                broadcast ? Material.BEACON : Material.LIGHT,
                broadcast ? "§aBroadcast Admin Actions: §aON" : "§7Broadcast Admin Actions: §cOFF",
                plugin.msg().getList(player, "admin_broadcast_lore")
        ));

        // Row 3 — admin power & sync
        // SLOT 19
        inv.setItem(19, GUIManager.icon(
                unlimited ? Material.EMERALD_BLOCK : Material.EMERALD,
                unlimited ? "§aUnlimited Plots (Admin): §aON" : "§7Unlimited Plots (Admin): §cOFF",
                List.of("§7Admins can create unlimited plots/claims.")
        ));

        // SLOT 21
        inv.setItem(21, GUIManager.icon(
                proxySync ? Material.ENDER_EYE : Material.ENDER_PEARL,
                proxySync ? "§aGlobal Sync (Proxy): §aON" : "§7Global Sync (Proxy): §cOFF",
                List.of(
                        "§7Enable Bungee/proxy sync for claims/flags.",
                        "§8(Requires SyncBridge setup; see config)"
                )
        ));

        // SLOT 23
        inv.setItem(23, GUIManager.icon(
                perfMode ? Material.REDSTONE_BLOCK : Material.REDSTONE,
                perfMode ? "§aPerformance Mode: §aON" : "§7Performance Mode: §cOFF",
                List.of(
                        "§7Disables non-essential cosmetics for speed.",
                        "§7Great for large servers or heavy plugin stacks."
                )
        ));

        // Row 4 — tools & navigation
        // SLOT 28
        inv.setItem(28, GUIManager.icon(
                Material.AMETHYST_CLUSTER,
                "§dExpansion Admin",
                List.of("§7Open Expansion admin preview.",
                        "§8(Community build — full workflow later)")
        ));

        // SLOT 30
        inv.setItem(30, GUIManager.icon(
                Material.COMPASS,
                "§bDiagnostics",
                List.of(
                        "§7Show TPS, listener counts, last sync time,",
                        "§7and adapter statuses (Vault/Dynmap/PAPI)."
                )
        ));

        // SLOT 31
        inv.setItem(31, GUIManager.icon(
                Material.REPEATER,
                "§eReload Config",
                List.of("§7Reload all config files",
                        "§7and data from storage.")
        ));

        // SLOT 34
        inv.setItem(34, GUIManager.icon(
                Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")
        ));

        // SLOT 40
        inv.setItem(40, GUIManager.icon(
                Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player); // --- SOUND FIX ---
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        // Hard guard: only handle if this is OUR menu
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // --- RELIABILITY FIX ---
        // Switched from Material-based switch to Slot-based switch
        switch (e.getSlot()) {
            // Toggles
            case 10 -> { // Auto-Remove
                boolean now = flipBoolAsync("admin.auto_remove_banned", false);
                plugin.msg().send(player, now ? "admin_auto_remove_enabled" : "admin_auto_remove_disabled");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player);
            }
            case 12 -> { // Bypass Limit
                boolean now = flipBoolAsync("admin.bypass_claim_limit", false);
                plugin.msg().send(player, now ? "admin_bypass_enabled" : "admin_bypass_disabled");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player);
            }
            case 14 -> { // Broadcast
                boolean now = flipBoolAsync("admin.broadcast_admin_actions", false);
                plugin.msg().send(player, now ? "admin_broadcast_enabled" : "admin_broadcast_disabled");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player);
            }
            case 19 -> { // Unlimited Plots
                boolean now = flipBoolAsync("admin.unlimited_plots", true);
                plugin.msg().send(player, now ? "admin_unlimited_enabled" : "admin_unlimited_disabled");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player);
            }
            case 21 -> { // Proxy Sync
                boolean now = flipBoolAsync("sync.proxy.enabled", false);
                plugin.msg().send(player, now ? "admin_proxy_sync_enabled" : "admin_proxy_sync_disabled");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player);
            }
            case 23 -> { // Performance Mode
                boolean now = flipBoolAsync("performance.low_overhead_mode", false);
                plugin.msg().send(player, now ? "admin_perf_mode_enabled" : "admin_perf_mode_disabled");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player);
            }

            // Expansion Admin (preview)
            case 28 -> { // AMETHYST_CLUSTER
                plugin.gui().expansionAdmin().open(player);
            }

            // Diagnostics
            case 30 -> { // COMPASS
                plugin.gui().openDiagnostics(player);
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
            }

            // --- CRITICAL LAG FIX (FOLIA-SAFE) ---
            case 31 -> { // REPEATER (Reload)
                plugin.msg().send(player, "admin_reloading");
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---

                // Run ALL reload logic on an async thread
                plugin.runGlobalAsync(() -> {
                    // 1. Reload config.yml (and sync new defaults)
                    plugin.cfg().reload();
                    // 2. Reload all managers that read from config.yml
                    plugin.msg().reload();
                    plugin.effects().reload();
                    plugin.worldRules().reload();
                    // 3. Reload data files
                    plugin.msg().loadPlayerPreferences();
                    plugin.store().load();
                    plugin.getExpansionRequestManager().load();

                    // Send "complete" message and refresh GUI back on the main thread
                    plugin.runMain(player, () -> {
                        plugin.msg().send(player, "admin_reload_complete");
                        open(player); // Refresh the GUI
                    });
                });
            }

            // Back / Exit
            case 34 -> { // ARROW
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
            }
            case 40 -> { // BARRIER
                player.closeInventory();
                plugin.effects().playMenuClose(player); // --- SOUND FIX ---
            }
            default -> { /* ignore */ }
        }
    }
}
