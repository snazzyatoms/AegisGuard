package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * AdminGUI
 * - Central control panel for server administrators.
 * - Fully localized for language switching (title + item names + lore + toggle labels).
 *
 * ✅ Fixed/Improved:
 * - Hard admin guard in click handler.
 * - Toggle format now uses placeholder-aware translation (no manual replace chains).
 * - Reload All Settings prefers plugin.reloadAegisGuard(true) when available.
 * - Folia-safe scheduling pattern preserved.
 *
 * ✅ NEW:
 * - Reload/Refresh items self-tag with PDC:
 *     aegis_action = reload_all / refresh_lang
 *   so GUIListener strict reload detection can identify them reliably.
 *
 * ✅ 2025-12:
 * - Reload/Refresh buttons now prefer NEW language keys:
 *     button_reload_all_settings / reload_all_settings_lore
 *     button_refresh_language_packs / refresh_language_packs_lore
 *   with backward-compatible fallback to older keys.
 *
 * ✅ NEW: Snapshot Admin GUI button (slot 33)
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
            sendKey(player, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "admin_menu_title", "&c&l⚔ High Guardian Tools ⚔");
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
                plugin.gui().tr(player, "button_view_requests_admin", "&cReview Requests"),
                plugin.gui().trList(player, "view_requests_admin_lore", List.of("&7Approve or deny expansion requests."))
        ));

        inv.setItem(29, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.gui().tr(player, "admin_plot_list_title", "&bPlot List"),
                plugin.gui().trList(player, "admin_plot_list_lore", List.of("&7View/TP to any plot."))
        ));

        inv.setItem(30, GUIManager.createItem(
                Material.COMPASS,
                plugin.gui().tr(player, "button_admin_diagnostics", "&bDiagnostics"),
                plugin.gui().trList(player, "admin_diagnostics_lore", List.of("&7View system stats."))
        ));

        // Slot 31: Reload All Settings (tagged)
        // Prefer NEW keys, fallback to legacy keys, then fallback text.
        ItemStack reloadAll = GUIManager.createItem(
                Material.REDSTONE,
                plugin.gui().tr(
                        player,
                        "button_reload_all_settings",
                        plugin.gui().tr(player, "button_admin_reload_all",
                                plugin.gui().tr(player, "button_admin_reload", "&eReload All Settings"))
                ),
                plugin.gui().trList(
                        player,
                        "reload_all_settings_lore",
                        plugin.gui().trList(player, "admin_reload_all_lore",
                                plugin.gui().trList(player, "admin_reload_lore", List.of("&7Reload all settings.")))
                )
        );
        tagAction(reloadAll, "reload_all");
        inv.setItem(31, reloadAll);

        // Slot 32: Refresh Language Packs (Codex only) (tagged)
        // Prefer NEW keys, fallback to legacy keys, then fallback lore list.
        ItemStack refreshLang = GUIManager.createItem(
                Material.RECOVERY_COMPASS,
                plugin.gui().tr(
                        player,
                        "button_refresh_language_packs",
                        plugin.gui().tr(player, "button_admin_refresh_lang", "&aRefresh Language Packs")
                ),
                plugin.gui().trList(
                        player,
                        "refresh_language_packs_lore",
                        plugin.gui().trList(player, "admin_refresh_lang_lore",
                                List.of("&7Reloads the language bundles.", "&7Use after editing lang files.", " ", "&eClick to refresh"))
                )
        );
        tagAction(refreshLang, "refresh_lang");
        inv.setItem(32, refreshLang);

        // ✅ NEW: Slot 33: Claim Snapshots (Rollback System)
        boolean snapshotsEnabled = plugin.getSnapshotManager() != null 
                && plugin.cfg().raw().getBoolean("snapshots.enabled", true);
        
        if (snapshotsEnabled) {
            inv.setItem(33, GUIManager.createItem(
                    Material.SPYGLASS,
                    plugin.gui().tr(player, "button_admin_snapshots", "&d📸 Claim Snapshots"),
                    plugin.gui().trList(player, "admin_snapshots_lore", List.of(
                            "&7View and manage claim snapshots.",
                            "&7Rollback plots to previous states.",
                            " ",
                            "&eClick to open snapshot browser."
                    ))
            ));
        } else {
            inv.setItem(33, GUIManager.createItem(
                    Material.GRAY_DYE,
                    plugin.gui().tr(player, "button_admin_snapshots_disabled", "&8📸 Snapshots Disabled"),
                    plugin.gui().trList(player, "admin_snapshots_disabled_lore", List.of(
                            "&7Snapshot system is disabled.",
                            "&7Enable in config.yml under 'snapshots.enabled'"
                    ))
            ));
        }

        // --- NAVIGATION ---
        inv.setItem(36, GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back_menu", "&fReturn to Menu"),
                plugin.gui().trList(player, "back_menu_lore", List.of("&7Return to the main dashboard."))
        ));

        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;

        e.setCancelled(true);

        // ✅ Hard guard: only admins can interact with this GUI
        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Optional: ignore filler clicks silently
        if (item.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        switch (e.getSlot()) {
            case 10 -> { // Auto-Remove Banned
                GUIManager.playClick(player);
                toggleAndReopen(player, "admin.auto_remove_banned", false, 
                    "admin_auto_remove_enabled", "admin_auto_remove_disabled");
            }
            case 11 -> { // Bypass Claim Limit
                GUIManager.playClick(player);
                toggleAndReopen(player, "admin.bypass_claim_limit", false,
                    "admin_bypass_enabled", "admin_bypass_disabled");
            }
            case 12 -> { // Broadcast Admin Actions
                GUIManager.playClick(player);
                toggleAndReopen(player, "admin.broadcast_admin_actions", false,
                    "admin_broadcast_enabled", "admin_broadcast_disabled");
            }
            case 13 -> { // Unlimited Plots
                GUIManager.playClick(player);
                toggleAndReopen(player, "admin.unlimited_plots", true,
                    null, null);
            }
            case 14 -> { // Proxy Sync
                GUIManager.playClick(player);
                toggleAndReopen(player, "sync.proxy.enabled", false,
                    null, null);
            }
            case 15 -> { // Low Overhead Mode
                GUIManager.playClick(player);
                toggleAndReopen(player, "performance.low_overhead_mode", false,
                    null, null);
            }

            case 28 -> { plugin.gui().expansionAdmin().open(player); plugin.effects().playMenuFlip(player); }
            case 29 -> { plugin.gui().plotList().open(player, 0); plugin.effects().playMenuFlip(player); }
            case 30 -> { plugin.gui().openDiagnostics(player); plugin.effects().playMenuFlip(player); }

            case 31 -> { // Reload ALL settings (central hook preferred)
                plugin.effects().playMenuFlip(player);
                sendKey(player, "admin_reloading", "&eReloading AegisGuard settings...");

                plugin.runGlobalAsync(() -> {
                    try {
                        plugin.runMainGlobal(() -> {
                            try {
                                // ✅ Prefer your central reload pipeline if present
                                tryInvokeReloadAegisGuard(true);
                            } catch (Throwable t) {
                                // Fallback if reloadAegisGuard isn't present for some reason
                                try { plugin.reloadConfig(); } catch (Throwable ex) {
                                    plugin.getLogger().warning("[AdminGUI] reloadConfig failed: " + ex.getMessage());
                                }
                                tryInvokeNoArg(plugin.cfg(), "reload", "load", "refresh", "reloadAll", "reloadConfig");
                                tryInvokeNoArg(plugin.worldRules(), "reload", "load", "refresh");
                                try {
                                    if (plugin.codex() != null) plugin.codex().reload();
                                } catch (Throwable ex) {
                                    plugin.getLogger().warning("[AdminGUI] Codex reload failed: " + ex.getMessage());
                                }
                            }
                        });
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[AdminGUI] runMainGlobal reload block failed: " + t.getMessage());
                    }

                    // Store load can be heavy; keep async
                    try {
                        if (plugin.store() != null) plugin.store().load();
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[AdminGUI] store.load failed: " + t.getMessage());
                    }

                    plugin.runMain(player, () -> {
                        sendKey(player, "admin_reload_complete", "&aReload complete.");
                        plugin.effects().playConfirm(player);
                        open(player);
                    });
                });
            }

            case 32 -> { // Refresh ONLY language packs (Codex)
                plugin.effects().playMenuFlip(player);
                sendKey(player, "admin_refreshing_lang", "&aRefreshing language packs...");

                plugin.runGlobalAsync(() -> {
                    try {
                        plugin.runMainGlobal(() -> {
                            try {
                                if (plugin.codex() != null) plugin.codex().reload();
                            } catch (Throwable t) {
                                plugin.getLogger().warning("[AdminGUI] Codex refresh failed: " + t.getMessage());
                            }
                        });
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[AdminGUI] runMainGlobal refresh block failed: " + t.getMessage());
                    }

                    plugin.runMain(player, () -> {
                        sendKey(player, "admin_refresh_lang_complete", "&aLanguage packs refreshed.");
                        plugin.effects().playConfirm(player);
                        open(player);
                    });
                });
            }

            // ✅ NEW: Slot 33 - Open Snapshot Admin GUI
            case 33 -> {
                if (plugin.getSnapshotManager() != null && plugin.gui().snapshotAdmin() != null) {
                    plugin.gui().openSnapshotAdmin(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    sendKey(player, "snapshots_disabled", "&cSnapshots are disabled.");
                    plugin.effects().playError(player);
                }
            }

            case 36 -> plugin.gui().openMain(player);
            case 44 -> player.closeInventory();
        }
    }

    // --- HELPERS ---

    /**
     * Tags an item so GUIListener strict reload detection can identify it without guessing.
     * Key: aegis_action
     * Values: reload / refresh / reload_all / refresh_lang / reload_settings
     */
    private void tagAction(ItemStack item, String action) {
        if (item == null || action == null || action.isBlank()) return;

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            NamespacedKey key = new NamespacedKey(plugin, "aegis_action");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action.trim().toLowerCase());
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private void addToggle(Player p, Inventory inv, int slot, String path, String nameKey, String loreKey, Material mat, boolean def) {
        boolean val = plugin.getConfig().getBoolean(path, def);

        String name = plugin.gui().tr(p, nameKey, "&eSetting");

        String status = plugin.gui().tr(
                p,
                val ? "toggle_on" : "toggle_off",
                val ? "&aON" : "&cOFF"
        );

        // ✅ Placeholder-aware toggle format (no manual replace chain)
        String display = plugin.gui().tr(
                p,
                "admin_toggle_format",
                "{NAME}: {STATE}",
                Map.of("NAME", name, "STATE", status)
        );

        Material icon = val ? mat : Material.GRAY_DYE;

        List<String> lore = plugin.gui().trList(
                p,
                loreKey,
                plugin.gui().trList(p, "admin_toggle_default_lore", List.of("&7Toggle this setting."))
        );

        inv.setItem(slot, GUIManager.createItem(icon, display, lore));
    }

    /**
     * Toggle a boolean config value and reopen the menu AFTER saving.
     * This ensures the GUI shows the updated state.
     */
    private void toggleAndReopen(Player p, String path, boolean def, String msgOn, String msgOff) {
        // Get current value
        boolean current = plugin.getConfig().getBoolean(path, def);
        boolean next = !current;
        
        // Set new value in memory
        plugin.getConfig().set(path, next);
        
        // Save synchronously to ensure it's written before GUI reopens
        try {
            plugin.saveConfig();
        } catch (Throwable t) {
            plugin.getLogger().warning("[AdminGUI] Failed to save config: " + t.getMessage());
        }
        
        // Reload the config wrapper if it exists
        try {
            if (plugin.cfg() != null) {
                tryInvokeNoArg(plugin.cfg(), "reload", "load", "refresh", "reloadAll", "reloadConfig");
            }
        } catch (Throwable ignored) {}
        
        // Send confirmation message
        if (next && msgOn != null) {
            sendKey(p, msgOn, "&aSetting enabled.");
        }
        if (!next && msgOff != null) {
            sendKey(p, msgOff, "&cSetting disabled.");
        }
        
        // NOW reopen the menu - config is saved and reloaded
        open(p);
    }

    private void sendKey(Player p, String key, String fallback) {
        // GUI gateway already returns colorized output, but this keeps legacy behavior safe.
        String msg = fallback;
        try {
            if (plugin.codex() != null) {
                String tr = plugin.codex().tr(p, key);
                if (tr != null && !tr.isBlank() && !tr.equalsIgnoreCase(key)) msg = tr;
            }
        } catch (Throwable ignored) {}

        p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void tryInvokeReloadAegisGuard(boolean refreshMenus) {
        // If your plugin has reloadAegisGuard(boolean), call it.
        // If not, this will throw and we fall back.
        try {
            Method m = plugin.getClass().getMethod("reloadAegisGuard", boolean.class);
            m.setAccessible(true);
            m.invoke(plugin, refreshMenus);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void tryInvokeNoArg(Object target, String... methodNames) {
        if (target == null || methodNames == null) return;
        for (String name : methodNames) {
            if (name == null || name.isBlank()) continue;
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                m.invoke(target);
                return;
            } catch (Throwable ignored) {}
        }
    }
}
