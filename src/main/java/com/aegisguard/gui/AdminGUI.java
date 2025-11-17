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
import java.util.concurrent.CompletableFuture; // --- NEW IMPORT ---

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
// ... existing code ...
        // ... (title logic) ...
        return "§b🛡 AegisGuard — Admin";
    }

    private ItemStack bg() {
// ... existing code ...
    }

    private boolean getBool(String path, boolean def) {
// ... existing code ...
    }

    /**
     * --- MODIFIED ---
     * Flips a boolean in the config and saves it ASYNCHRONOUSLY to prevent lag.
     */
    private boolean flipBoolAsync(String path, boolean def) {
        boolean cur = getBool(path, def);
        boolean next = !cur;
        plugin.getConfig().set(path, next);

        // --- IMPROVEMENT ---
        // Save the config to disk on an async thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.saveConfig();
        });
        return next;
    }

    public void open(Player player) {
// ... existing code ...
        // ... (permission checks) ...

        Inventory inv = Bukkit.createInventory(new AdminHolder(), 45, title(player));
        // background
// ... existing code ...
        // ... (background fill) ...

        // Read toggles
// ... existing code ...
        // ... (boolean checks) ...

        // Row 2 — core toggles
        // SLOT 10
        inv.setItem(10, GUIManager.icon(
// ... existing code ...
        // ... (autoRemove icon) ...
        ));

        // SLOT 12
        inv.setItem(12, GUIManager.icon(
// ... existing code ...
        // ... (bypass icon) ...
        ));

        // SLOT 14
        inv.setItem(14, GUIManager.icon(
// ... existing code ...
        // ... (broadcast icon) ...
        ));

        // Row 3 — admin power & sync
        // SLOT 19
        inv.setItem(19, GUIManager.icon(
// ... existing code ...
        // ... (unlimited icon) ...
        ));

        // SLOT 21
        inv.setItem(21, GUIManager.icon(
// ... existing code ...
        // ... (proxySync icon) ...
        ));

        // SLOT 23
        inv.setItem(23, GUIManager.icon(
// ... existing code ...
        // ... (perfMode icon) ...
        ));

        // Row 4 — tools & navigation
        // SLOT 28
        inv.setItem(28, GUIManager.icon(
// ... existing code ...
        // ... (Expansion Admin icon) ...
        ));

        // SLOT 30
        inv.setItem(30, GUIManager.icon(
// ... existing code ...
        // ... (Diagnostics icon) ...
        ));

        // SLOT 31
        inv.setItem(31, GUIManager.icon(
// ... existing code ...
        // ... (Reload icon) ...
        ));

        // SLOT 34
        inv.setItem(34, GUIManager.icon(
// ... existing code ...
        // ... (Back icon) ...
        ));

        // SLOT 40
        inv.setItem(40, GUIManager.icon(
// ... existing code ...
        // ... (Exit icon) ...
        ));

        player.openInventory(inv);
// ... existing code ...
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        // Hard guard: only handle if this is OUR menu
// ... existing code ...

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // --- CRITICAL RELIABILITY FIX ---
        // Switched from Material-based switch to Slot-based switch
        switch (e.getSlot()) {
            // Toggles
            case 10 -> { // TNT, GUNPOWDER
                boolean now = flipBoolAsync("admin.auto_remove_banned", false);
                plugin.msg().send(player, now ? "admin_auto_remove_enabled" : "admin_auto_remove_disabled");
                plugin.sounds().playMenuFlip(player);
                open(player);
            }
            case 12 -> { // NETHER_STAR, IRON_NUGGET
                boolean now = flipBoolAsync("admin.bypass_claim_limit", false);
                plugin.msg().send(player, now ? "admin_bypass_enabled" : "admin_bypass_disabled");
                plugin.sounds().playMenuFlip(player);
                open(player);
            }
            case 14 -> { // BEACON, LIGHT
                boolean now = flipBoolAsync("admin.broadcast_admin_actions", false);
                plugin.msg().send(player, now ? "admin_broadcast_enabled" : "admin_broadcast_disabled");
                plugin.sounds().playMenuFlip(player);
                open(player);
            }
            case 19 -> { // EMERALD_BLOCK, EMERALD
                boolean now = flipBoolAsync("admin.unlimited_plots", true);
                plugin.msg().send(player, now ? "admin_unlimited_enabled" : "admin_unlimited_disabled");
                plugin.sounds().playMenuFlip(player);
                open(player);
            }
            case 21 -> { // ENDER_EYE, ENDER_PEARL
                boolean now = flipBoolAsync("sync.proxy.enabled", false);
                plugin.msg().send(player, now ? "admin_proxy_sync_enabled" : "admin_proxy_sync_disabled");
                // (Later: kick off an initial handshake to SyncBridge here)
                plugin.sounds().playMenuFlip(player);
                open(player);
            }
            case 23 -> { // REDSTONE_BLOCK, REDSTONE
                boolean now = flipBoolAsync("performance.low_overhead_mode", false);
                plugin.msg().send(player, now ? "admin_perf_mode_enabled" : "admin_perf_mode_disabled");
                plugin.sounds().playMenuFlip(player);
                open(player);
            }

            // Expansion Admin (preview)
            case 28 -> { // AMETHYST_CLUSTER
                // We must use the plugin.gui().expansionAdmin() getter
                plugin.gui().expansionAdmin().open(player);
            }

            // Diagnostics
            case 30 -> { // COMPASS
                plugin.gui().openDiagnostics(player); // implement a simple DiagnosticsGUI
                plugin.sounds().playMenuFlip(player);
            }

            // --- CRITICAL LAG FIX ---
            case 31 -> { // REPEATER (Reload)
                plugin.msg().send(player, "admin_reloading");
                plugin.sounds().playMenuFlip(player);

                // Run ALL reload logic on an async thread to prevent freezing the server
                CompletableFuture.runAsync(() -> {
                    // 1. Reload config.yml (and sync new defaults)
                    plugin.cfg().reload();
                    // 2. Reload messages.yml
                    plugin.msg().reload();
                    // 3. Reload plots.yml (reads file, builds spatial hash)
                    plugin.store().load();
                    // 4. Reload expansion-requests.yml
                    plugin.getExpansionRequestManager().load();

                    // Send "complete" message and refresh GUI back on the main thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.msg().send(player, "admin_reload_complete");
                        open(player); // Refresh the GUI
                    });
                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Spigot's async executor
            }

            // Back / Exit
            case 34 -> { // ARROW
                plugin.gui().openMain(player);
                plugin.sounds().playMenuFlip(player);
            }
            case 40 -> { // BARRIER
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
            }
            default -> { /* ignore */ }
        }
    }
}
