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
 * AdminGUI (1.2.6 QoL pass)
 *
 * Goals:
 * - Keep 1.2.5 structure + layout intact, but make interactions more robust & less confusing.
 * - Keep navigation consistent with a clear return path and an explicit close button.
 * - Fix/avoid "back breaks after paging" class of issues by tagging ALL actionable items with PDC (aegis_action)
 *   and routing clicks by action instead of slot number when possible.
 * - Provide direct access to localized per-world claim and protection controls.
 * - Folia-safe click handling: only respond to top-inventory clicks, cancel all interactions, async file IO.
 *
 * Notes:
 * - This file now tags every clickable button: toggles, tools, nav, and disabled states.
 * - If your GUIListener already reads aegis_action for reload detection, it can now use the same key universally.
 */
public class AdminGUI {

    private final AegisGuard plugin;

    private static final int SIZE = 45;

    // --- Slots (kept mostly the same as 1.2.5 structure) ---
    private static final int SLOT_TOGGLE_AUTO_REMOVE = 10;
    private static final int SLOT_TOGGLE_BYPASS_LIMIT = 11;
    private static final int SLOT_TOGGLE_BROADCAST   = 12;
    private static final int SLOT_TOGGLE_UNLIMITED   = 13;
    private static final int SLOT_TOGGLE_PROXY_SYNC  = 14;
    private static final int SLOT_TOGGLE_LOW_OVERHEAD= 15;

    private static final int SLOT_TOOL_REQUESTS      = 28;
    private static final int SLOT_TOOL_PLOT_LIST     = 29;
    private static final int SLOT_TOOL_DIAGNOSTICS   = 30;
    private static final int SLOT_TOOL_RELOAD_ALL    = 31;
    private static final int SLOT_TOOL_REFRESH_LANG  = 32;
    private static final int SLOT_TOOL_SNAPSHOTS     = 33;

    // 1.2.6: World Controls entry + future stub (new, but still “tools row”)
    private static final int SLOT_TOOL_WORLD_CONTROLS = 34;
    private static final int SLOT_TOOL_MIGRATION      = 35;

    private static final int SLOT_NAV_EXIT = 40;
    private static final int SLOT_NAV_BACK = 44;

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
        Inventory inv = Bukkit.createInventory(new AdminHolder(), SIZE, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(4, GUIManager.createItem(
                Material.ENCHANTED_GOLDEN_APPLE,
                plugin.gui().tr(player, "staff_command_center_name", "&c&lGuardian Command Center"),
                plugin.gui().trList(player, "staff_command_center_lore", List.of(
                        "&7Server policy, territory operations,",
                        "&7recovery, migration, and diagnostics.",
                        " ",
                        "&8Every control below is permission-checked."
                ))
        ));

        inv.setItem(20, GUIManager.createItem(
                Material.REPEATER,
                plugin.gui().tr(player, "staff_policy_section_name", "&eOperational Policy"),
                plugin.gui().trList(player, "staff_policy_section_lore", List.of(
                        "&7The upper row controls server-wide",
                        "&7AegisGuard operating policy."
                ))
        ));
        inv.setItem(24, GUIManager.createItem(
                Material.ENDER_CHEST,
                plugin.gui().tr(player, "staff_toolbelt_section_name", "&bGuardian Toolbelt"),
                plugin.gui().trList(player, "staff_toolbelt_section_lore", List.of(
                        "&7The lower row opens review, recovery,",
                        "&7world, migration, and maintenance tools."
                ))
        ));

        // --- SETTINGS TOGGLES ---
        addToggle(player, inv, SLOT_TOGGLE_AUTO_REMOVE,
                "admin.auto_remove_banned",
                "button_admin_auto_remove", "admin_auto_remove_lore",
                Material.TNT, false,
                List.of(
                        "&7Automatically clean up claims",
                        "&7owned by banned players."
                ),
                "toggle_auto_remove_banned"
        );
        addToggle(player, inv, SLOT_TOGGLE_BYPASS_LIMIT,
                "admin.bypass_claim_limit",
                "button_admin_bypass_limit", "admin_bypass_limit_lore",
                Material.NETHER_STAR, false,
                List.of(
                        "&7Let staff create or manage",
                        "&7claims beyond normal limits."
                ),
                "toggle_bypass_claim_limit"
        );
        addToggle(player, inv, SLOT_TOGGLE_BROADCAST,
                "admin.broadcast_admin_actions",
                "button_admin_broadcast", "admin_broadcast_lore",
                Material.BEACON, false,
                List.of(
                        "&7Broadcast major admin actions",
                        "&7to the configured audience."
                ),
                "toggle_broadcast_admin_actions"
        );
        addToggle(player, inv, SLOT_TOGGLE_UNLIMITED,
                "admin.unlimited_plots",
                "button_admin_unlimited", "admin_unlimited_lore",
                Material.EMERALD_BLOCK, true,
                List.of(
                        "&7Remove normal plot limits",
                        "&7for administrators."
                ),
                "toggle_unlimited_plots"
        );
        addToggle(player, inv, SLOT_TOGGLE_PROXY_SYNC,
                "sync.proxy.enabled",
                "button_admin_sync", "admin_sync_lore",
                Material.ENDER_EYE, false,
                List.of(
                        "&7Sync supported data across",
                        "&7proxy-connected servers."
                ),
                "toggle_proxy_sync"
        );
        addToggle(player, inv, SLOT_TOGGLE_LOW_OVERHEAD,
                "performance.low_overhead_mode",
                "button_admin_perf", "admin_perf_lore",
                Material.REDSTONE_BLOCK, false,
                List.of(
                        "&7Favor lighter background work",
                        "&7for busy or larger servers."
                ),
                "toggle_low_overhead_mode"
        );

        // --- TOOLS ---
        ItemStack requests = GUIManager.createItem(
                Material.AMETHYST_CLUSTER,
                plugin.gui().tr(player, "button_view_requests_admin", "&cReview Requests"),
                plugin.gui().trList(player, "view_requests_admin_lore", List.of("&7Approve or deny expansion requests."))
        );
        tagAction(requests, "open_requests");
        inv.setItem(SLOT_TOOL_REQUESTS, requests);

        ItemStack plotList = GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.gui().tr(player, "admin_plot_list_title", "&bPlot List"),
                plugin.gui().trList(player, "admin_plot_list_lore", List.of("&7View/TP to any plot."))
        );
        tagAction(plotList, "open_plot_list");
        inv.setItem(SLOT_TOOL_PLOT_LIST, plotList);

        ItemStack diagnostics = GUIManager.createItem(
                Material.COMPASS,
                plugin.gui().tr(player, "button_admin_diagnostics", "&bDiagnostics"),
                plugin.gui().trList(player, "admin_diagnostics_lore", List.of("&7View system stats."))
        );
        tagAction(diagnostics, "open_diagnostics");
        inv.setItem(SLOT_TOOL_DIAGNOSTICS, diagnostics);

        // Reload All Settings (tagged)
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
        inv.setItem(SLOT_TOOL_RELOAD_ALL, reloadAll);

        // Refresh Language Packs (Codex only) (tagged)
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
        inv.setItem(SLOT_TOOL_REFRESH_LANG, refreshLang);

        // Snapshots (enabled/disabled) (tagged either way)
        boolean snapshotsEnabled = plugin.getSnapshotManager() != null
                && plugin.cfg().raw().getBoolean("snapshots.enabled", true);

        if (snapshotsEnabled) {
            ItemStack snapshots = GUIManager.createItem(
                    Material.SPYGLASS,
                    plugin.gui().tr(player, "button_admin_snapshots", "&d📸 Claim Snapshots"),
                    plugin.gui().trList(player, "admin_snapshots_lore", List.of(
                            "&7View and manage claim snapshots.",
                            "&7Rollback plots to previous states.",
                            " ",
                            "&eClick to open snapshot browser."
                    ))
            );
            tagAction(snapshots, "open_snapshots");
            inv.setItem(SLOT_TOOL_SNAPSHOTS, snapshots);
        } else {
            ItemStack snapshotsDisabled = GUIManager.createItem(
                    Material.GRAY_DYE,
                    plugin.gui().tr(player, "button_admin_snapshots_disabled", "&8📸 Snapshots Disabled"),
                    plugin.gui().trList(player, "admin_snapshots_disabled_lore", List.of(
                            "&7Snapshot system is disabled.",
                            "&7Enable in config.yml under 'snapshots.enabled'"
                    ))
            );
            tagAction(snapshotsDisabled, "snapshots_disabled");
            inv.setItem(SLOT_TOOL_SNAPSHOTS, snapshotsDisabled);
        }

        ItemStack worldControls = GUIManager.createItem(
                Material.LECTERN,
                plugin.gui().tr(player, "button_admin_world_controls", "&b🌍 World Controls"),
                plugin.gui().trList(player, "admin_world_controls_lore", List.of(
                        "&7Manage claiming and default plot",
                        "&7protection rules for every world.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(worldControls, "open_world_controls");
        inv.setItem(SLOT_TOOL_WORLD_CONTROLS, worldControls);

        ItemStack migration = GUIManager.createItem(
                Material.BLAZE_ROD,
                plugin.gui().tr(player, "button_admin_migration", "&6Migration Wizard"),
                plugin.gui().trList(player, "admin_migration_lore", List.of(
                        "&7Preview and import supported",
                        "&7external protection plugins.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(migration, "open_migration");
        inv.setItem(SLOT_TOOL_MIGRATION, migration);

        ItemStack close = GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        );
        tagAction(close, "close_menu");
        inv.setItem(SLOT_NAV_EXIT, close);

        // --- NAVIGATION ---
        ItemStack back = GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back_menu", "&fReturn to Menu"),
                plugin.gui().trList(player, "back_menu_lore", List.of("&7Return to the main dashboard."))
        );
        tagAction(back, "back_main");
        inv.setItem(SLOT_NAV_BACK, back);

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        // Only respond when the TOP inventory is ours.
        if (!(e.getView().getTopInventory().getHolder() instanceof AdminHolder)) return;

        // Cancel everything (prevents item stealing / shift-moves).
        e.setCancelled(true);

        // Ignore clicks in the player's own inventory (QoL: no accidental moves / spam).
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        // Hard guard: only admins can interact
        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Silently ignore filler (and any untagged items)
        String action = getAction(item);
        if (action == null || action.isBlank()) return;

        switch (action) {
            // --- Toggles ---
            case "toggle_auto_remove_banned" -> { GUIManager.playClick(player); toggleAndReopen(player, "admin.auto_remove_banned", false); }
            case "toggle_bypass_claim_limit" -> { GUIManager.playClick(player); toggleAndReopen(player, "admin.bypass_claim_limit", false); }
            case "toggle_broadcast_admin_actions" -> { GUIManager.playClick(player); toggleAndReopen(player, "admin.broadcast_admin_actions", false); }
            case "toggle_unlimited_plots" -> { GUIManager.playClick(player); toggleAndReopen(player, "admin.unlimited_plots", true); }
            case "toggle_proxy_sync" -> { GUIManager.playClick(player); toggleAndReopen(player, "sync.proxy.enabled", false); }
            case "toggle_low_overhead_mode" -> { GUIManager.playClick(player); toggleAndReopen(player, "performance.low_overhead_mode", false); }

            // --- Tools ---
            case "open_requests" -> { plugin.gui().expansionAdmin().open(player); plugin.effects().playMenuFlip(player); }
            case "open_plot_list" -> { plugin.gui().plotList().open(player, 0); plugin.effects().playMenuFlip(player); }
            case "open_diagnostics" -> { plugin.gui().doctor().open(player); plugin.effects().playMenuFlip(player); }

            case "reload_all" -> handleReloadAll(player);
            case "refresh_lang" -> handleRefreshLang(player);

            case "open_snapshots" -> {
                if (plugin.getSnapshotManager() != null && plugin.gui().snapshotAdmin() != null) {
                    plugin.gui().openSnapshotAdmin(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    sendKey(player, "snapshots_disabled", "&cSnapshots are disabled.");
                    plugin.effects().playError(player);
                }
            }
            case "snapshots_disabled" -> {
                sendKey(player, "snapshots_disabled", "&cSnapshots are disabled.");
                plugin.effects().playError(player);
            }

            case "open_world_controls" -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().worldControls().open(player);
            }
            case "open_migration" -> {
                if (plugin.gui().migration() != null) {
                    plugin.gui().migration().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    sendKey(player, "migration_unavailable", "&cMigration wizard is unavailable.");
                    plugin.effects().playError(player);
                }
            }

            // --- Navigation ---
            case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); }
            case "back_main" -> plugin.gui().openMain(player);

            default -> {
                // Unknown action: ignore safely
            }
        }
    }

    // --- 1.2.6 QoL: safer reload handlers split out for clarity ---

    private void handleReloadAll(Player player) {
        plugin.effects().playMenuFlip(player);
        sendKey(player, "admin_reloading", "&eReloading AegisGuard settings...");

        plugin.runGlobalAsync(() -> {
            try {
                plugin.runMainGlobal(() -> {
                    try {
                        // Prefer central reload pipeline if present
                        tryInvokeReloadAegisGuard(true);
                    } catch (Throwable t) {
                        // Fallback
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

    private void handleRefreshLang(Player player) {
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

    // --- HELPERS ---

    /**
     * Tag an item so GUIListener (and this class) can identify it without guessing slots.
     * Key: aegis_action
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

    private String getAction(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            NamespacedKey key = new NamespacedKey(plugin, "aegis_action");
            return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void addToggle(Player p, Inventory inv, int slot, String path, String nameKey, String loreKey,
                           Material mat, boolean def, List<String> fallbackLore, String actionKey) {

        boolean val = plugin.getConfig().getBoolean(path, def);

        String name = plugin.gui().tr(p, nameKey, "&eSetting");
        String status = plugin.gui().tr(p, val ? "toggle_on" : "toggle_off", val ? "&aON" : "&cOFF");

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
                fallbackLore
        );

        ItemStack it = GUIManager.createItem(icon, display, lore);
        tagAction(it, actionKey);
        inv.setItem(slot, it);
    }

    /**
     * Toggle a boolean config value and reopen AFTER saving.
     * 1.2.6 QoL:
     * - Save config async (file IO), then reopen on main thread.
     * - Reload cfg wrapper (if present) on main thread.
     */
    private void toggleAndReopen(Player p, String path, boolean def) {
        boolean current = plugin.getConfig().getBoolean(path, def);
        boolean next = !current;

        plugin.getConfig().set(path, next);

        plugin.runGlobalAsync(() -> {
            try {
                plugin.saveConfig();
            } catch (Throwable t) {
                plugin.getLogger().warning("[AdminGUI] Failed to save config: " + t.getMessage());
            }

            plugin.runMain(p, () -> {
                // Reload wrapper if it exists
                try {
                    if (plugin.cfg() != null) {
                        tryInvokeNoArg(plugin.cfg(), "reload", "load", "refresh", "reloadAll", "reloadConfig");
                    }
                } catch (Throwable ignored) {}

                // Generic feedback (works for ALL toggles)
                String msg = next ? "&aSetting enabled." : "&cSetting disabled.";
                sendKey(p, next ? "admin_setting_enabled" : "admin_setting_disabled", msg);

                open(p);
            });
        });
    }

    private void sendKey(Player p, String key, String fallback) {
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
