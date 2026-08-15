package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.util.TeleportUtil;
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

    private static final int SIZE = 54;

    // Policy row (10-16) — expansion QUEUE ↔ INSTANT must not collide with tools.
    private static final int SLOT_TOGGLE_AUTO_REMOVE = 10;
    private static final int SLOT_TOGGLE_BYPASS_LIMIT = 11;
    private static final int SLOT_TOGGLE_BROADCAST   = 12;
    private static final int SLOT_TOGGLE_UNLIMITED   = 13;
    private static final int SLOT_TOGGLE_PROXY_SYNC  = 14;
    private static final int SLOT_TOGGLE_LOW_OVERHEAD= 15;
    private static final int SLOT_TOGGLE_EXPANSION_MODE = 16;

    private static final int SLOT_SECTION_TERRITORY = 18;
    private static final int SLOT_TOOL_CONVERT        = 19;
    private static final int SLOT_TOOL_PLOT_LIST     = 20;
    private static final int SLOT_TOOL_REQUESTS      = 21;
    private static final int SLOT_TOOL_INSTANT_APPROVALS = 22;
    private static final int SLOT_TOOL_SET_SPAWN      = 23;
    private static final int SLOT_TOOL_WORLD_CONTROLS = 24;

    private static final int SLOT_SECTION_RECOVERY = 27;
    private static final int SLOT_TOOL_SNAPSHOTS     = 28;
    private static final int SLOT_TOOL_SNAPSHOT_SCHEDULE = 29;
    private static final int SLOT_TOOL_AUDIT_LEDGER   = 30;
    private static final int SLOT_TOOL_DIAGNOSTICS   = 31;
    private static final int SLOT_TOOL_MIGRATION      = 32;

    private static final int SLOT_SECTION_MODULES = 36;
    private static final int SLOT_TOOL_ROUTES         = 37;
    private static final int SLOT_TOOL_ARENA          = 38;
    private static final int SLOT_TOOL_REFRESH_LANG  = 39;
    private static final int SLOT_TOOL_RELOAD_ALL    = 40;

    private static final int SLOT_NAV_BACK = 48;
    private static final int SLOT_NAV_EXIT = 49;

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
        int[] borderSlots = {
                0, 1, 2, 3, 5, 6, 7, 8,
                45, 46, 47, 50, 51, 52, 53
        };
        for (int slot : borderSlots) inv.setItem(slot, filler);

        inv.setItem(4, GUIManager.createItem(
                Material.ENCHANTED_GOLDEN_APPLE,
                plugin.gui().tr(player, "staff_command_center_name", "&c&lGuardian Command Center"),
                plugin.gui().trList(player, "staff_command_center_lore", List.of(
                        "&7Staff hub for server policy, territory ops,",
                        "&7recovery, migration, and diagnostics.",
                        " ",
                        "&7Hover any control for what it does,",
                        "&7when to use it, and caution notes.",
                        " ",
                        "&8Every control below is permission-checked."
                ))
        ));

        addSectionFrame(player, inv, Material.YELLOW_STAINED_GLASS_PANE,
                "staff_policy_section_name", "&eOperational Policy",
                "staff_policy_section_lore",
                List.of(
                        "&7This row: server-wide operating policy.",
                        "&7Toggle only during maintenance windows",
                        "&7when you understand the side effects."
                ),
                9, 17);
        addSectionFrame(player, inv, Material.LIME_STAINED_GLASS_PANE,
                "staff_territory_section_name", "&aTerritory",
                "staff_territory_section_lore",
                List.of(
                        "&7Convert plots, browse claims,",
                        "&7review expansions, and set spawn."
                ),
                18, 19, 20, 21, 22, 23, 24, 25, 26);
        addSectionFrame(player, inv, Material.MAGENTA_STAINED_GLASS_PANE,
                "staff_recovery_section_name", "&dRecovery",
                "staff_recovery_section_lore",
                List.of(
                        "&7Claim-data snapshots, audit, doctor,",
                        "&7and migration. Snapshots do not save builds."
                ),
                27, 28, 29, 30, 31, 32, 33, 34, 35);
        addSectionFrame(player, inv, Material.CYAN_STAINED_GLASS_PANE,
                "staff_toolbelt_section_name", "&bGuardian Toolbelt",
                "staff_modules_section_lore",
                List.of("&7Routes, Arena, language refresh, and reload."),
                36, 37, 38, 39, 40, 41, 42, 43, 44);

        // --- SETTINGS TOGGLES ---
        addToggle(player, inv, SLOT_TOGGLE_AUTO_REMOVE,
                "admin.auto_remove_banned",
                "button_admin_auto_remove", "admin_auto_remove_lore",
                Material.TNT, false,
                List.of(
                        "&7What: auto-clean claims owned by banned players.",
                        "&7When: you want banned accounts cleared over time.",
                        " ",
                        "&cCaution: removes territory after the ban cleanup path.",
                        "&eClick to toggle."
                ),
                "toggle_auto_remove_banned"
        );
        addToggle(player, inv, SLOT_TOGGLE_BYPASS_LIMIT,
                "admin.bypass_claim_limit",
                "button_admin_bypass_limit", "admin_bypass_limit_lore",
                Material.NETHER_STAR, false,
                List.of(
                        "&7What: let staff ignore normal claim limits.",
                        "&7When: building staff zones or repairing oversized claims.",
                        " ",
                        "&eClick to toggle."
                ),
                "toggle_bypass_claim_limit"
        );
        addToggle(player, inv, SLOT_TOGGLE_BROADCAST,
                "admin.broadcast_admin_actions",
                "button_admin_broadcast", "admin_broadcast_lore",
                Material.BEACON, false,
                List.of(
                        "&7What: announce major staff actions to the audience.",
                        "&7When: you want transparency for restores/migrations.",
                        " ",
                        "&eClick to toggle."
                ),
                "toggle_broadcast_admin_actions"
        );
        addToggle(player, inv, SLOT_TOGGLE_UNLIMITED,
                "admin.unlimited_plots",
                "button_admin_unlimited", "admin_unlimited_lore",
                Material.EMERALD_BLOCK, true,
                List.of(
                        "&7What: remove normal plot-count limits for admins.",
                        "&7When: large staff builds or recovery work.",
                        " ",
                        "&eClick to toggle."
                ),
                "toggle_unlimited_plots"
        );
        addToggle(player, inv, SLOT_TOGGLE_PROXY_SYNC,
                "sync.proxy.enabled",
                "button_admin_sync", "admin_sync_lore",
                Material.ENDER_EYE, false,
                List.of(
                        "&7What: sync supported data across proxy servers.",
                        "&7When: multi-server networks with shared claims.",
                        " ",
                        "&cRequires a correctly configured proxy sync setup.",
                        "&eClick to toggle."
                ),
                "toggle_proxy_sync"
        );
        addToggle(player, inv, SLOT_TOGGLE_LOW_OVERHEAD,
                "performance.low_overhead_mode",
                "button_admin_perf", "admin_perf_lore",
                Material.REDSTONE_BLOCK, false,
                List.of(
                        "&7What: favor lighter background work.",
                        "&7When: busy events or large player counts.",
                        " ",
                        "&8Some cosmetics/background polish may reduce.",
                        "&eClick to toggle."
                ),
                "toggle_low_overhead_mode"
        );
        addExpansionModeToggle(player, inv);

        // --- TOOLS ---
        ItemStack requests = GUIManager.createItem(
                Material.AMETHYST_CLUSTER,
                plugin.gui().tr(player, "button_view_requests_admin", "&cReview Requests"),
                plugin.gui().trList(player, "view_requests_admin_lore", List.of(
                        "&7What: approve or deny expansion petitions.",
                        "&7When: the expansion queue has pending requests.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(requests, "open_requests");
        inv.setItem(SLOT_TOOL_REQUESTS, requests);

        ItemStack instantApprovals = GUIManager.createItem(
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                plugin.gui().tr(player, "button_view_instant_approvals", "&bInstant Approvals"),
                plugin.gui().trList(player, "view_instant_approvals_lore", List.of(
                        "&7What: browse auto-approved expansion history.",
                        "&7When: auditing Instant Mode or unattended queue approvals.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(instantApprovals, "open_instant_approvals");
        inv.setItem(SLOT_TOOL_INSTANT_APPROVALS, instantApprovals);

        ItemStack plotList = GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.gui().tr(player, "admin_plot_list_title", "&bPlot List"),
                plugin.gui().trList(player, "admin_plot_list_lore", List.of(
                        "&7What: browse tracked plots and teleport.",
                        "&7When: investigating a claim or helping a player.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(plotList, "open_plot_list");
        inv.setItem(SLOT_TOOL_PLOT_LIST, plotList);

        ItemStack diagnostics = GUIManager.createItem(
                Material.COMPASS,
                plugin.gui().tr(player, "button_admin_diagnostics", "&bDiagnostics"),
                plugin.gui().trList(player, "admin_diagnostics_lore", List.of(
                        "&7What: Territory Doctor — scan, repair,",
                        "&7reports, settlements, delinquents, storage migrate.",
                        "&7When: claims, rentals, or hooks look inconsistent.",
                        " ",
                        "&eClick to open."
                ))
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
                        List.of(
                                "&7What: reload config, data views, and menus.",
                                "&7When: after editing config.yml on disk.",
                                " ",
                                "&cDoes not replace a full server restart for every change.",
                                "&eClick to reload."
                        )
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
                        List.of(
                                "&7What: reload menu and message language packs.",
                                "&7When: after editing files under lang/.",
                                " ",
                                "&eClick to refresh."
                        )
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
                            "&7What: browse claim-data recovery points.",
                            "&7Creates copies of flags, members, and bounds.",
                            "&7Does not save world blocks or builds.",
                            " ",
                            "&cRollback overwrites the live claim — confirm carefully.",
                            "&eClick to open.",
                            " ",
                            "&7Saves plot records: owner, flags,",
                            "&7members, and bounds. Not world blocks.",
                            "&eFull plot backups (builds) come later."
                    ))
            );
            tagAction(snapshots, "open_snapshots");
            inv.setItem(SLOT_TOOL_SNAPSHOTS, snapshots);
        } else {
            ItemStack snapshotsDisabled = GUIManager.createItem(
                    Material.GRAY_DYE,
                    plugin.gui().tr(player, "button_admin_snapshots_disabled", "&8📸 Snapshots Disabled"),
                    plugin.gui().trList(player, "admin_snapshots_disabled_lore", List.of(
                            "&7Claim snapshots are disabled in config.",
                            "&7Set snapshots.enabled to true, then reload."
                    ))
            );
            tagAction(snapshotsDisabled, "snapshots_disabled");
            inv.setItem(SLOT_TOOL_SNAPSHOTS, snapshotsDisabled);
        }

        addSnapshotScheduleButton(player, inv);

        ItemStack worldControls = GUIManager.createItem(
                Material.LECTERN,
                plugin.gui().tr(player, "button_admin_world_controls", "&b🌍 World Controls"),
                plugin.gui().trList(player, "admin_world_controls_lore", List.of(
                        "&7What: per-world claim defaults and live game rules.",
                        "&7When: locking a world, tuning PvP/mobs, or spawn policy.",
                        " ",
                        "&cLive game-rule toggles apply immediately.",
                        "&eClick to open."
                ))
        );
        tagAction(worldControls, "open_world_controls");
        inv.setItem(SLOT_TOOL_WORLD_CONTROLS, worldControls);

        ItemStack migration = GUIManager.createItem(
                Material.BLAZE_ROD,
                plugin.gui().tr(player, "button_admin_migration", "&6Migration Wizard"),
                plugin.gui().trList(player, "admin_migration_lore", List.of(
                        "&7What: dry-run then import GriefPrevention,",
                        "&7GriefDefender, or Lands claims into AegisGuard.",
                        "&7When: switching protection plugins.",
                        " ",
                        "&cLive import writes claims — preview first.",
                        "&eClick to open."
                ))
        );
        tagAction(migration, "open_migration");
        inv.setItem(SLOT_TOOL_MIGRATION, migration);

        if (player.hasPermission("aegis.admin.routes") || plugin.isAdmin(player)) {
            ItemStack routes = GUIManager.createItem(
                    Material.FILLED_MAP,
                    plugin.gui().tr(player, "button_admin_routes", "&aRoute Editor"),
                    plugin.gui().trList(player, "admin_routes_lore", List.of(
                            "&7What: create named exploration routes",
                            "&7with ordered checkpoints players can browse.",
                            "&7When: building guided tours or trails.",
                            " ",
                            "&eClick to open."
                    ))
            );
            tagAction(routes, "open_routes");
            inv.setItem(SLOT_TOOL_ROUTES, routes);
        }

        if (player.hasPermission("aegis.arena.admin") || player.hasPermission("aegis.arena.steward") || plugin.isAdmin(player)) {
            ItemStack arena = GUIManager.createItem(
                    Material.DIAMOND_SWORD,
                    plugin.gui().tr(player, "button_admin_arena", "&cArena Admin"),
                    plugin.gui().trList(player, "admin_arena_lore", List.of(
                            "&7What: configure dungeon arenas, abort runs,",
                            "&7and review reward ledger entries.",
                            "&7When: building Lava Dungeon or party PvE.",
                            " ",
                            "&eClick to open."
                    ))
            );
            tagAction(arena, "open_arena");
            inv.setItem(SLOT_TOOL_ARENA, arena);
        }

        if (player.hasPermission("aegis.admin.audit")) {
            ItemStack auditLedger = GUIManager.createItem(
                    Material.WRITTEN_BOOK,
                    plugin.gui().tr(player, "button_admin_audit", "&eStaff Audit Ledger"),
                    plugin.gui().trList(player, "admin_audit_lore", List.of(
                            "&7What: review staff restores, Doctor repairs,",
                            "&7migrations, bypass toggles, and ClaimBlock edits.",
                            "&7When: accountability checks or incident review.",
                            " ",
                            "&eClick to open."
                    ))
            );
            tagAction(auditLedger, "open_audit");
            inv.setItem(SLOT_TOOL_AUDIT_LEDGER, auditLedger);
        }

        ItemStack setSpawn = GUIManager.createItem(
                Material.RESPAWN_ANCHOR,
                plugin.gui().tr(player, "button_admin_set_spawn", "&aSet Current Plot as Spawn"),
                plugin.gui().trList(player, "admin_set_spawn_lore", List.of(
                        "&7What: set this staff plot's safe spot as public Spawn.",
                        "&7When: standing inside a server/staff plot.",
                        " ",
                        "&7A recovery snapshot may be taken first.",
                        "&8Does not change ownership.",
                        "&eClick to set."
                ))
        );
        tagAction(setSpawn, "set_current_plot_spawn");
        inv.setItem(SLOT_TOOL_SET_SPAWN, setSpawn);

        ItemStack convert = GUIManager.createItem(
                Material.STRUCTURE_BLOCK,
                plugin.gui().tr(player, "button_admin_convert_server", "&cConvert Plot → Server Plot"),
                plugin.gui().trList(player, "admin_convert_server_lore", List.of(
                        "&7What: convert the personal plot you are standing in",
                        "&7into a server zone (Spawn, Hub, Town, Event,",
                        "&7Shop, Arena, or plain server land).",
                        "&7When: a finished personal build should become",
                        "&7public staff territory without rebuilding.",
                        " ",
                        "&cOwnership moves to the server.",
                        "&cRoles and Guest Passes are cleared.",
                        "&7Confirms before changing anything.",
                        "&eClick to open."
                ))
        );
        tagAction(convert, "open_convert_server");
        inv.setItem(SLOT_TOOL_CONVERT, convert);

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
            case "toggle_expansion_approval_mode" -> { GUIManager.playClick(player); cycleExpansionApprovalMode(player); }

            // --- Tools ---
            case "open_requests" -> { plugin.gui().expansionAdmin().open(player); plugin.effects().playMenuFlip(player); }
            case "open_instant_approvals" -> {
                plugin.gui().expansionInstantApprovals().open(player);
                plugin.effects().playMenuFlip(player);
            }
            case "open_plot_list" -> { plugin.gui().plotList().open(player, 0); plugin.effects().playMenuFlip(player); }
            case "open_diagnostics" -> { plugin.gui().doctor().open(player); plugin.effects().playMenuFlip(player); }

            case "reload_all" -> handleReloadAll(player);
            case "refresh_lang" -> handleRefreshLang(player);

            case "open_snapshots" -> {
                if (plugin.getSnapshotManager() != null) {
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
            case "cycle_snapshot_schedule" -> {
                GUIManager.playClick(player);
                cycleSnapshotSchedule(player);
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
            case "open_routes" -> {
                if ((player.hasPermission("aegis.admin.routes") || plugin.isAdmin(player))
                        && plugin.gui().routeAdmin() != null) {
                    plugin.gui().routeAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.msg().send(player, "no_perm");
                    plugin.effects().playError(player);
                }
            }
            case "open_arena" -> {
                if ((player.hasPermission("aegis.arena.admin")
                        || player.hasPermission("aegis.arena.steward")
                        || plugin.isAdmin(player))
                        && plugin.gui().arenaAdmin() != null) {
                    plugin.gui().arenaAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.msg().send(player, "no_perm");
                    plugin.effects().playError(player);
                }
            }

            case "open_audit" -> {
                if (player.hasPermission("aegis.admin.audit") && plugin.gui().audit() != null) {
                    plugin.gui().audit().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.msg().send(player, "no_perm");
                    plugin.effects().playError(player);
                }
            }

            case "set_current_plot_spawn" -> setCurrentPlotAsSpawn(player);
            case "open_convert_server" -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().convertToServer().openFromStanding(player);
            }

            // --- Navigation ---
            case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); }
            case "back_main" -> plugin.gui().openMain(player);

            default -> {
                // Unknown action: ignore safely
            }
        }
    }

    private void setCurrentPlotAsSpawn(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isServerZone()) {
            sendKey(player, "spawn_requires_staff_plot", "&cStand inside a staff/server plot to set Spawn.");
            plugin.effects().playError(player);
            return;
        }

        var safeLocation = plugin.safeTravel() != null
                ? plugin.safeTravel().findSafeDestination(player.getLocation())
                : TeleportUtil.findSafeDestination(player.getLocation());
        if (safeLocation == null) {
            sendKey(player, "spawn_unsafe_location", "&cThis location is not safe for player travel.");
            plugin.effects().playError(player);
            return;
        }

        if (plugin.getSnapshotManager() != null
                && plugin.getConfig().getBoolean("snapshots.auto_snapshot.before_staff_destination", true)) {
            try {
                plugin.getSnapshotManager().createSnapshot(plot,
                        com.aegisguard.snapshots.ClaimSnapshot.SnapshotType.PRE_STAFF_DESTINATION,
                        "Before setting public Spawn destination", player.getUniqueId());
            } catch (Throwable ignored) {}
        }

        plot.setSpawnLocation(safeLocation);
        plot.setServerWarp(true, "Spawn", Material.BEACON);
        if (plot.getWarpCategory() == null || plot.getWarpCategory().isBlank()) {
            plot.setWarpCategory("SPAWN");
        }
        plugin.store().savePlot(plot);
        sendKey(player, "spawn_destination_set", "&aThis staff plot is now the public Spawn destination.");
        plugin.effects().playConfirm(player);
        open(player);
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

        Material icon = mat;

        List<String> lore = plugin.gui().trList(
                p,
                loreKey,
                fallbackLore
        );

        ItemStack it = GUIManager.createItem(icon, display, lore);
        tagAction(it, actionKey);
        inv.setItem(slot, it);
    }

    private void addExpansionModeToggle(Player p, Inventory inv) {
        boolean instant = isExpansionInstantMode();
        String modeLabel = plugin.gui().tr(
                p,
                instant ? "admin_expansion_mode_instant" : "admin_expansion_mode_queue",
                instant ? "&bInstant" : "&6Queue"
        );
        String display = plugin.gui().tr(
                p,
                "button_admin_expansion_mode",
                "&eExpansion Approval: {MODE}",
                Map.of("MODE", modeLabel)
        );
        List<String> lore = plugin.gui().trList(
                p,
                instant ? "admin_expansion_mode_lore_instant" : "admin_expansion_mode_lore_queue",
                instant
                        ? List.of(
                        "&7Current: &bInstant",
                        "&7Valid expansion requests auto-approve",
                        "&7and are written to audit history.",
                        " ",
                        "&7Use Instant when staff is away or on",
                        "&7small trusted servers that want self-serve growth.",
                        " ",
                        "&cCaution: borders grow without staff review.",
                        "&eClick to switch to Queue."
                )
                        : List.of(
                        "&7Current: &6Queue",
                        "&7Players submit expansion requests;",
                        "&7staff must approve or deny each one.",
                        " ",
                        "&7Default for most servers — keeps growth reviewed.",
                        " ",
                        "&7Click to switch to Instant (auto-approve).",
                        "&eClick to cycle mode."
                )
        );
        Material icon = instant ? Material.LIGHTNING_ROD : Material.HOPPER;
        ItemStack it = GUIManager.createItem(icon, display, lore);
        tagAction(it, "toggle_expansion_approval_mode");
        inv.setItem(SLOT_TOGGLE_EXPANSION_MODE, it);
    }

    private boolean isExpansionInstantMode() {
        if (plugin.getExpansionRequestManager() != null) {
            return plugin.getExpansionRequestManager().getApprovalMode()
                    == com.aegisguard.expansions.ExpansionRequestManager.ApprovalMode.INSTANT;
        }
        String nested = plugin.getConfig().getString("expansions.approval.mode", "");
        String raw = (nested != null && !nested.isBlank())
                ? nested
                : plugin.getConfig().getString("expansions.approval_mode", "QUEUE");
        if (raw == null) return false;
        String s = raw.trim().toUpperCase();
        return s.equals("INSTANT") || s.equals("AUTO") || s.equals("AUTO_APPROVE") || s.equals("AUTOAPPROVE");
    }

    /**
     * Cycle expansions.approval_mode between QUEUE and INSTANT, persist to config.yml,
     * and keep the preferred nested key in sync so runtime reads update immediately.
     */
    private void cycleExpansionApprovalMode(Player p) {
        boolean nextInstant = !isExpansionInstantMode();
        String next = nextInstant ? "INSTANT" : "QUEUE";

        // Documented key + preferred nested key (ExpansionRequestManager reads either live).
        plugin.getConfig().set("expansions.approval_mode", next);
        plugin.getConfig().set("expansions.approval.mode", next);
        if (plugin.cfg() != null && plugin.cfg().raw() != null && plugin.cfg().raw() != plugin.getConfig()) {
            plugin.cfg().raw().set("expansions.approval_mode", next);
            plugin.cfg().raw().set("expansions.approval.mode", next);
        }

        plugin.runGlobalAsync(() -> {
            try {
                plugin.saveConfig();
            } catch (Throwable t) {
                plugin.getLogger().warning("[AdminGUI] Failed to save expansion approval mode: " + t.getMessage());
            }

            plugin.runMain(p, () -> {
                try {
                    if (plugin.cfg() != null) {
                        tryInvokeNoArg(plugin.cfg(), "reload", "load", "refresh", "reloadAll", "reloadConfig");
                    }
                } catch (Throwable ignored) {}

                // Ensure both keys survive reload (preferred path must not lag the documented key).
                plugin.getConfig().set("expansions.approval_mode", next);
                plugin.getConfig().set("expansions.approval.mode", next);

                sendKey(
                        p,
                        nextInstant ? "admin_expansion_mode_set_instant" : "admin_expansion_mode_set_queue",
                        nextInstant
                                ? "&bExpansion approval set to Instant (auto-approve)."
                                : "&6Expansion approval set to Queue (staff review)."
                );
                plugin.effects().playConfirm(p);
                open(p);
            });
        });
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

    private void addSectionFrame(Player player, Inventory inv, Material material,
                                 String titleKey, String titleFallback,
                                 String loreKey, List<String> loreFallback,
                                 int... slots) {
        String title = plugin.gui().tr(player, titleKey, titleFallback);
        List<String> lore = plugin.gui().trList(player, loreKey, loreFallback);
        for (int slot : slots) {
            ItemStack marker = GUIManager.createItem(material, title, lore);
            tagAction(marker, "section_marker");
            inv.setItem(slot, marker);
        }
    }

    private void addSnapshotScheduleButton(Player p, Inventory inv) {
        boolean enabled = plugin.getConfig().getBoolean("snapshots.scheduled.enabled", false)
                && plugin.getConfig().getBoolean("snapshots.enabled", true);
        int minutes = Math.max(1, plugin.getConfig().getInt("snapshots.scheduled.interval_minutes", 360));
        String interval = enabled
                ? plugin.gui().tr(p, "admin_snapshot_schedule_interval", "&aEvery {MINUTES} min",
                Map.of("MINUTES", String.valueOf(minutes)))
                : plugin.gui().tr(p, "admin_snapshot_schedule_off", "&cOff");
        String display = plugin.gui().tr(p, "button_admin_snapshot_schedule",
                "&dAuto Snapshots: {STATE}", Map.of("STATE", interval));
        List<String> lore = plugin.gui().trList(p, enabled
                        ? "admin_snapshot_schedule_lore_on"
                        : "admin_snapshot_schedule_lore_off",
                enabled
                        ? List.of(
                        "&7Takes plot-data snapshots of server zones",
                        "&7on a timer. Does not copy world builds.",
                        " ",
                        "&eClick to cycle Off / 15m / 1h / 6h / 24h.")
                        : List.of(
                        "&7Automatic server-zone snapshots are off.",
                        "&7Does not copy world builds.",
                        " ",
                        "&eClick to cycle Off / 15m / 1h / 6h / 24h."));
        ItemStack it = GUIManager.createItem(Material.CLOCK, display, lore);
        tagAction(it, "cycle_snapshot_schedule");
        inv.setItem(SLOT_TOOL_SNAPSHOT_SCHEDULE, it);
    }

    private void cycleSnapshotSchedule(Player p) {
        if (plugin.getSnapshotManager() == null) {
            sendKey(p, "snapshots_disabled", "&cSnapshots are disabled.");
            plugin.effects().playError(p);
            return;
        }
        int next = plugin.getSnapshotManager().cycleScheduledInterval();
        plugin.runGlobalAsync(() -> {
            try {
                plugin.saveConfig();
            } catch (Throwable t) {
                plugin.getLogger().warning("[AdminGUI] Failed to save snapshot schedule: " + t.getMessage());
            }
            plugin.runMain(p, () -> {
                plugin.restartScheduledSnapshotTask();
                String msg = next <= 0
                        ? plugin.gui().tr(p, "admin_snapshot_schedule_set_off", "&cAutomatic snapshots disabled.")
                        : plugin.gui().tr(p, "admin_snapshot_schedule_set",
                        "&aAutomatic snapshots every {MINUTES} minutes.",
                        Map.of("MINUTES", String.valueOf(next)));
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                plugin.effects().playConfirm(p);
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
