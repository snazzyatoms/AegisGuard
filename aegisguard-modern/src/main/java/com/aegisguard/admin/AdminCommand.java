package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.migration.MigrationManager.MigrationOptions;
import com.aegisguard.migration.MigrationManager.SourcePlugin;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.snapshots.ClaimSnapshot;
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final MigrationManager migrationManager;

    private static final String[] SUB_COMMANDS = {
            "reload", "bypass", "menu", "manage", "convert", "wand", "claim", "blocks", "merge", "migrate", "doctor",
            "health", "rentals", "discover", "activity", "snapshot", "restore", "audit", "help"
    };

    private static final String[] MIGRATE_ACTIONS = {
            "list", "preview", "import", "help", "storage", "backend"
    };

    private static final String[] MIGRATE_SOURCES = {
            "griefprevention", "gp", "griefdefender", "gd", "lands"
    };

    private static final String[] MIGRATE_OPTIONS = {
            "--force", "--no-trusted", "--no-flags", "--world="
    };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
        this.migrationManager = plugin.migration() != null ? plugin.migration() : new MigrationManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAegisGuard(true);
                sendLocalized(sender, "admin_console_reload_complete", "&aAegisGuard reload complete.");
                plugin.getLogger().info("AegisGuard was reloaded from the server console.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("health")) {
                StaffHealthCheck.report(plugin, sender);
                return true;
            }
            sendLocalized(sender, "players_only", "&cError: This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("aegis.admin")) {
            plugin.msg().send(player, "no_perm");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().admin().open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(player);
            case "bypass" -> handleBypass(player);
            case "menu" -> plugin.gui().admin().open(player);
            case "manage" -> handleServerManage(player);
            case "wand" -> handleWand(player, args);
            case "claim" -> handleServerClaim(player);
            case "migrate" -> handleMigrate(player, args);
            case "doctor" -> runDoctor(player, args);
            case "health" -> StaffHealthCheck.report(plugin, player);
            case "snapshot" -> handleSnapshot(player, args);
            case "restore" -> handleRestore(player, args);
            case "convert" -> handleConvert(player, args);
            case "blocks" -> handleBlocks(player, args);
            case "merge" -> handleServerMerge(player, args);
            case "rentals" -> handleAdminRentals(player, args);
            case "discover" -> handleAdminDiscover(player, args);
            case "activity" -> handleAdminActivity(player);
            case "audit" -> handleAudit(player);
            case "help" -> sendAdminHelp(player);
            default -> sendAdminHelp(player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), new ArrayList<>());
        }

        if (args[0].equalsIgnoreCase("wand") && args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], List.of("server", "migration"), new ArrayList<>());
        }

        if (args[0].equalsIgnoreCase("migrate")) {
            if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList(MIGRATE_ACTIONS), new ArrayList<>());
            }
            if (args.length == 3) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList(MIGRATE_SOURCES), new ArrayList<>());
            }
            if (args.length >= 4) {
                return StringUtil.copyPartialMatches(args[args.length - 1], Arrays.asList(MIGRATE_OPTIONS), new ArrayList<>());
            }
        }

        if (args[0].equalsIgnoreCase("snapshot") && args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], List.of("here", "current"), new ArrayList<>());
        }
        if (args[0].equalsIgnoreCase("restore")) {
            if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], List.of("here", "current", "confirm"), new ArrayList<>());
            }
            if (args.length == 3) {
                return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
            }
        }

        if (args[0].equalsIgnoreCase("convert") && args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], List.of("confirm"), new ArrayList<>());
        }
        if (args[0].equalsIgnoreCase("merge")) {
            if (args.length == 2) return StringUtil.copyPartialMatches(args[1], List.of("north", "south", "east", "west"), new ArrayList<>());
            if (args.length == 3) return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
        }
        if (args[0].equalsIgnoreCase("blocks")) {
            if (args.length == 2) return StringUtil.copyPartialMatches(args[1], List.of("get", "add", "remove", "set"), new ArrayList<>());
            if (args.length == 3) {
                List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
            }
            if (args.length == 4 && !args[1].equalsIgnoreCase("get")) {
                return StringUtil.copyPartialMatches(args[3], List.of("100", "500", "1000"), new ArrayList<>());
            }
        }
        if (args[0].equalsIgnoreCase("doctor")) {
            if (args.length == 2) return StringUtil.copyPartialMatches(args[1], List.of("report", "scan", "repair"), new ArrayList<>());
            if (args.length == 3 && args[1].equalsIgnoreCase("repair")) {
                return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
            }
        }
        if (args[0].equalsIgnoreCase("rentals")) {
            if (args.length == 2) return StringUtil.copyPartialMatches(args[1], List.of("cancel", "retry-settlements"), new ArrayList<>());
            if (args.length == 3 && args[1].equalsIgnoreCase("cancel")) {
                return StringUtil.copyPartialMatches(args[2], List.of("here"), new ArrayList<>());
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("cancel")) {
                return StringUtil.copyPartialMatches(args[3], List.of("confirm"), new ArrayList<>());
            }
        }
        if (args[0].equalsIgnoreCase("discover") && args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], List.of("feature", "unfeature", "show", "hide"), new ArrayList<>());
        }

        return Collections.emptyList();
    }

    private void handleReload(Player player) {
        plugin.reloadAegisGuard(true);
        plugin.msg().send(player, "reload_success");
        if (plugin.getNotificationManager() != null) {
            plugin.getNotificationManager().notifyAdmins(
                    "aegis.admin",
                    "admin_notify_reloaded",
                    "&6[Admin] &e{PLAYER} &7reloaded AegisGuard.",
                    Map.of("PLAYER", player.getName())
            );
        }
        plugin.effects().playConfirm(player);
    }

    private void handleBypass(Player player) {
        if (!player.hasPermission("aegis.admin.bypass")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        boolean enabled = plugin.toggleBypass(player);
        String state = enabled
                ? plugin.gui().tr(player, "toggle_on", "&aON")
                : plugin.gui().tr(player, "toggle_off", "&cOFF");
        sendLocalized(player, "admin_bypass_mode", "&6Bypass Mode: {STATE}", Map.of("STATE", state));
        if (plugin.getNotificationManager() != null) {
            plugin.getNotificationManager().notifyAdmins(
                    "aegis.admin",
                    "admin_notify_bypass",
                    "&6[Admin] &e{PLAYER} &7set bypass mode to {STATE}&7.",
                    Map.of("PLAYER", player.getName(), "STATE", state)
            );
        }
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.ADMIN_BYPASS, player, null,
                    "Set bypass mode to " + (enabled ? "ENABLED" : "DISABLED"));
        }
        plugin.effects().playConfirm(player);
    }

    private void handleAudit(Player player) {
        if (!player.hasPermission("aegis.admin.audit")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        if (plugin.gui().audit() == null) {
            sendLocalized(player, "admin_audit_unavailable", "&cThe audit ledger is unavailable.");
            return;
        }
        plugin.gui().audit().open(player);
    }

    private void handleWand(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.wand")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        if (args.length == 1 || (args.length >= 2 && args[1].equalsIgnoreCase("server"))) {
            if (playerHasClaimWand(player)) {
                sendLocalized(player, "admin_wand_already_owned",
                        "&eYou already have an Aegis claim wand or Sentinel's Scepter.");
                return;
            }

            player.getInventory().addItem(createServerWand(player));
            plugin.selection().setPlayerWand(player, "server_claim_wand");
            sendLocalized(player, "admin_wand_received", "&aYou received the Sentinel's Scepter.");
            sendLocalized(player, "admin_wand_usage_hint",
                    "&7Select two corners, then use &b/agadmin claim&7.");
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("migration")) {
            if (plugin.gui().migration() != null) {
                plugin.gui().migration().giveMigrationWand(player);
            } else {
                sendLocalized(player, "admin_migration_wand_unavailable", "&cMigration wand is unavailable.");
            }
            return;
        }

        sendLocalized(player, "admin_wand_usage", "&eUsage: /aegisadmin wand <server|migration>");
    }

    private void handleServerClaim(Player player) {
        if (!player.hasPermission("aegis.serverzone.manage") && !player.hasPermission("aegis.admin.manage")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        if (!plugin.selection().hasSelection(player)) {
            sendLocalized(player, "admin_claim_need_selection",
                    "&cYou need to select two corners first with the Sentinel's Scepter.");
            sendLocalized(player, "admin_claim_get_wand_hint",
                    "&7Use &b/agadmin wand server &7to get it.");
            return;
        }

        plugin.selection().setPlayerWand(player, "server_claim_wand");
        plugin.selection().confirmClaim(player, true);
    }

    private void handleServerManage(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isServerZone()) {
            sendLocalized(player, "admin_manage_need_server_zone",
                    "&cStand inside the server zone you want to manage.");
            return;
        }
        if (!plot.canManage(player, plugin)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        plugin.gui().openMain(player);
    }

    private ItemStack createServerWand(Player player) {
        ItemStack rod = new ItemStack(plugin.cfg().getAdminWandMaterial());
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return rod;

        meta.setDisplayName(plugin.cfg().getAdminWandName());

        List<String> lore = plugin.cfg().getAdminWandLore();
        if (lore == null || lore.isEmpty()) {
            lore = List.of(
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.gui().tr(player, "admin_wand_lore_authority", "&7A tool of absolute authority.")),
                    " ",
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.gui().tr(player, "admin_wand_lore_pos1", "&eRight-Click: &fSelect Pos 1")),
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.gui().tr(player, "admin_wand_lore_pos2", "&eLeft-Click: &fSelect Pos 2")),
                    " ",
                    ChatColor.translateAlternateColorCodes('&',
                            plugin.gui().tr(player, "admin_wand_lore_creates", "&cCreates server zones directly."))
            );
        }
        lore = new ArrayList<>(lore);
        lore.add(" ");
        lore.add(ChatColor.translateAlternateColorCodes('&', plugin.gui().tr(player, "admin_wand_doctor_hint",
                "&bSneak + Right-Click: &fOpen Doctor Tools")));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        meta.getPersistentDataContainer().set(
                SelectionService.SERVER_WAND_KEY,
                PersistentDataType.BYTE,
                (byte) 1
        );

        NamespacedKey idKey = new NamespacedKey(plugin, "wand_id");
        meta.getPersistentDataContainer().set(
                idKey,
                PersistentDataType.STRING,
                UUID.randomUUID().toString()
        );

        rod.setItemMeta(meta);
        return rod;
    }

    private boolean playerHasClaimWand(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer().has(SelectionService.WAND_KEY, PersistentDataType.BYTE)
                    || item.getItemMeta().getPersistentDataContainer().has(SelectionService.SERVER_WAND_KEY, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    private void handleMigrate(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.migrate")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        if (args.length == 1) {
            if (plugin.gui().migration() != null) {
                plugin.gui().migration().open(player);
            } else {
                sendLocalized(player, "migration_unavailable", "&cMigration wizard is unavailable.");
            }
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("storage") || action.equals("backend")) {
            if (plugin.gui().storageMigrate() != null) plugin.gui().storageMigrate().open(player);
            else sendLocalized(player, "storage_migrate_unavailable", "&cStorage migration is unavailable.");
            return;
        }
        if (action.equals("help")) {
            sendLocalized(player, "admin_migrate_help_header", "&6AegisGuard Migration");
            sendLocalized(player, "admin_migrate_help_wizard", "&e/agadmin migrate &7- open claim migration wizard");
            sendLocalized(player, "admin_migrate_help_storage", "&e/agadmin migrate storage &7- YML ↔ SQL storage migrate");
            sendLocalized(player, "admin_migrate_help_list", "&e/agadmin migrate list");
            sendLocalized(player, "admin_migrate_help_preview", "&e/agadmin migrate preview <source> [options]");
            sendLocalized(player, "admin_migrate_help_import", "&e/agadmin migrate import <source> [options]");
            sendLocalized(player, "admin_migrate_help_snapshot", "&e/agadmin snapshot here [reason]");
            sendLocalized(player, "admin_migrate_help_restore", "&e/agadmin restore here confirm");
            return;
        }

        if (action.equals("list")) {
            List<SourcePlugin> available = migrationManager.getAvailableSources();
            if (available.isEmpty()) {
                sendLocalized(player, "admin_migrate_no_sources",
                        "&eNo supported migration sources were detected.");
                return;
            }
            sendLocalized(player, "admin_migrate_sources_header", "&6Detected migration sources:");
            for (SourcePlugin source : available) {
                sendLocalized(player, "admin_migrate_source_line", "&7 - {SOURCE}",
                        Map.of("SOURCE", source.getDisplayName()));
            }
            return;
        }

        if (args.length < 3) {
            sendLocalized(player, "admin_migrate_usage",
                    "&eUsage: /agadmin migrate <preview|import> <source>");
            return;
        }

        SourcePlugin source = SourcePlugin.fromString(args[2]);
        if (source == null) {
            sendLocalized(player, "admin_migrate_unknown_source",
                    "&cUnknown source. Use griefprevention/gp, griefdefender/gd, or lands.");
            return;
        }

        MigrationOptions options = parseOptions(args, 3);
        if (action.equals("preview")) {
            migrationManager.previewMigration(player, source, options)
                    .whenComplete((result, error) -> plugin.runMain(player, () -> {
                        if (error != null) {
                            sendLocalized(player, "admin_migrate_preview_failed",
                                    "&cMigration preview failed: {ERROR}",
                                    Map.of("ERROR", safeMessage(error)));
                        } else if (plugin.gui().migration() != null) {
                            plugin.gui().migration().openPreview(player, source, result);
                        }
                    }));
            return;
        }

        if (action.equals("import")) {
            migrationManager.startMigration(player, source, options)
                    .whenComplete((result, error) -> plugin.runMain(player, () -> {
                        if (error != null) {
                            sendLocalized(player, "admin_migrate_failed",
                                    "&cMigration failed: {ERROR}",
                                    Map.of("ERROR", safeMessage(error)));
                        } else {
                            if (plugin.gui().migration() != null) {
                                plugin.gui().migration().openPreview(player, source, result);
                            }
                            if (plugin.audit() != null) {
                                plugin.audit().record(AuditCategory.MIGRATION, player, source.getDisplayName(),
                                        "Imported claims from " + source.getDisplayName());
                            }
                        }
                    }));
            return;
        }

        sendLocalized(player, "admin_migrate_unknown_action",
                "&eUnknown migrate action. Use list, preview, import, or help.");
    }

    private MigrationOptions parseOptions(String[] args, int startIndex) {
        MigrationOptions options = new MigrationOptions();
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--force")) options.forceOverlap = true;
            else if (arg.equalsIgnoreCase("--no-trusted")) options.importTrusted = false;
            else if (arg.equalsIgnoreCase("--no-flags")) options.importFlags = false;
            else if (arg.toLowerCase(Locale.ROOT).startsWith("--world=")) {
                options.worldFilter = arg.substring("--world=".length());
            }
        }
        return options;
    }

    private void runDoctor(Player player, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "report";
        if (action.equals("scan")) {
            sendLocalized(player, "doctor_scan_running", "&bScanning territory consistency...");
            plugin.runSync(() -> {
                DoctorRepairService.ScanResult result = DoctorRepairService.scan(plugin);
                plugin.runMain(player, () -> {
                    sendLocalized(player, "doctor_scan_summary",
                            "&6Doctor scan: &f{PLOTS} &7plots, &e{ISSUES} &7issue(s), &c{CRITICAL} &7critical.",
                            Map.of("PLOTS", String.valueOf(result.plotsScanned()),
                                    "ISSUES", String.valueOf(result.issues().size()),
                                    "CRITICAL", String.valueOf(result.criticalCount())));
                    result.issues().stream().limit(8).forEach(issue -> sendLocalized(player,
                            "doctor_scan_issue", "&e{SEVERITY} &f{CODE}",
                            Map.of("SEVERITY", issue.severity().name(), "CODE", issue.code())));
                    if (result.issues().size() > 8) {
                        sendLocalized(player, "doctor_scan_more",
                                "&7Run /agadmin doctor report for all {COUNT} issues.",
                                Map.of("COUNT", String.valueOf(result.issues().size())));
                    }
                });
            });
            return;
        }
        if (action.equals("repair")) {
            if (!player.hasPermission("aegis.admin.doctor.repair")) {
                sendLocalized(player, "doctor_repair_no_permission", "&cYou cannot run automatic Doctor repairs.");
                return;
            }
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                sendLocalized(player, "doctor_repair_prompt",
                        "&eRun /agadmin doctor scan first, then /agadmin doctor repair confirm.");
                sendLocalized(player, "doctor_repair_scope",
                        "&7Doctor only repairs deterministic state; overlaps and missing worlds are never guessed.");
                return;
            }
            sendLocalized(player, "doctor_repair_running", "&eApplying deterministic Doctor repairs...");
            plugin.runSync(() -> {
                DoctorRepairService.RepairResult result = DoctorRepairService.repair(plugin);
                plugin.runMain(player, () -> {
                    sendLocalized(player, "doctor_repair_complete",
                            "&aDoctor repaired {PLOTS} plot(s); {REMAINING} issue(s) remain.",
                            Map.of("PLOTS", String.valueOf(result.repairedPlots()),
                                    "REMAINING", String.valueOf(result.after().issues().size())));
                    audit(player, "ran Doctor repair (plots=" + result.repairedPlots() + ", remaining=" + result.after().issues().size() + ")");
                    if (plugin.audit() != null) {
                        plugin.audit().record(AuditCategory.DOCTOR_REPAIR, player, null,
                                "Repaired " + result.repairedPlots() + " plot(s); "
                                        + result.after().issues().size() + " issue(s) remain.");
                    }
                });
            });
            return;
        }

        sendLocalized(player, "doctor_report_running", "&bGenerating Doctor report...");
        plugin.runSync(() -> {
            try {
                Path report = AdminDiagnostics.writeReport(plugin);
                plugin.runMain(player, () -> sendLocalized(player, "doctor_report_saved",
                        "&aDoctor report saved: &f{FILE}", Map.of("FILE", report.getFileName().toString())));
            } catch (Exception e) {
                plugin.runMain(player, () -> sendLocalized(player, "doctor_report_failed",
                        "&cDoctor report failed: {ERROR}", Map.of("ERROR", safeMessage(e))));
            }
        });
    }

    private void handleSnapshot(Player player, String[] args) {
        if (plugin.getSnapshotManager() == null) {
            sendLocalized(player, "snapshots_unavailable", "&cSnapshot system is unavailable.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendLocalized(player, "admin_snapshot_need_plot",
                    "&cStand inside a plot to create a recovery snapshot.");
            return;
        }
        if (!plot.canManage(player, plugin)) {
            sendLocalized(player, "admin_snapshot_no_perm",
                    "&cYou cannot create a recovery snapshot for this plot.");
            return;
        }

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : plugin.gui().tr(player, "admin_snapshot_reason_default",
                        "Manual admin recovery snapshot by {PLAYER}",
                        Map.of("PLAYER", player.getName()));
        ClaimSnapshot snapshot = plugin.getSnapshotManager().createSnapshot(
                plot,
                ClaimSnapshot.SnapshotType.MANUAL,
                reason,
                player.getUniqueId()
        );
        sendLocalized(player, "admin_snapshot_created",
                "&aCreated recovery snapshot: {ID}",
                Map.of("ID", String.valueOf(snapshot.getSnapshotId())));
    }

    private void handleRestore(Player player, String[] args) {
        if (plugin.getSnapshotManager() == null) {
            sendLocalized(player, "snapshots_disabled", "&cSnapshot system is unavailable.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendLocalized(player, "admin_restore_need_plot", "&cStand inside the plot you want to restore.");
            return;
        }
        if (!plot.canManage(player, plugin)) {
            sendLocalized(player, "admin_restore_no_perm", "&cYou cannot restore this plot.");
            return;
        }

        ClaimSnapshot latest = plugin.getSnapshotManager().getLatestSnapshotForPlot(plot.getPlotId());
        if (latest == null) {
            sendLocalized(player, "admin_restore_none", "&eNo snapshots were found for this plot.");
            return;
        }

        boolean confirmed = false;
        for (String arg : args) {
            if (arg != null && arg.equalsIgnoreCase("confirm")) {
                confirmed = true;
                break;
            }
        }
        if (!confirmed) {
            sendLocalized(player, "admin_restore_prompt",
                    "&eLatest snapshot: &f{TYPE} &7| &f{REASON} &7| age &f{AGE}",
                    Map.of(
                            "TYPE", latest.getType().name(),
                            "REASON", latest.getReason() == null || latest.getReason().isBlank()
                                    ? plugin.gui().tr(player, "admin_restore_no_reason", "No reason recorded")
                                    : latest.getReason(),
                            "AGE", formatAgeMillis(latest.getAgeMillis())
                    ));
            sendLocalized(player, "admin_restore_confirm_hint",
                    "&cThis overwrites the live claim. Run &e/agadmin restore here confirm &cto continue.");
            return;
        }

        sendLocalized(player, "admin_restore_running", "&b[AegisGuard] &7Restoring latest snapshot...");
        UUID restoredPlotId = plot.getPlotId();
        UUID snapshotId = latest.getSnapshotId();
        plugin.runGlobalAsync(() -> {
            boolean restored = plugin.getSnapshotManager().rollback(snapshotId);
            plugin.runMain(player, () -> {
                if (restored) {
                    sendLocalized(player, "admin_restore_success",
                            "&aPlot restored from snapshot &f{ID}",
                            Map.of("ID", String.valueOf(snapshotId)));
                } else {
                    sendLocalized(player, "admin_restore_failed",
                            "&cPlot restore failed. Check the logs or doctor report.");
                }
                if (plugin.audit() != null) {
                    plugin.audit().record(AuditCategory.SNAPSHOT_RESTORE, player, restoredPlotId.toString(),
                            "Restored plot from snapshot " + snapshotId + (restored ? "" : " (failed)"));
                }
            });
        });
    }

    private void sendAdminHelp(Player player) {
        sendLocalized(player, "admin_help_header", "&6AegisGuard Staff Commands");
        sendLocalized(player, "admin_help_menu", "&e/agadmin &7| &e/agadmin menu &8- Staff Command Center");
        sendLocalized(player, "admin_help_doctor", "&e/agadmin doctor [scan|report|repair confirm] &8- Territory Doctor");
        sendLocalized(player, "admin_help_health", "&e/agadmin health &8- Quick health check");
        sendLocalized(player, "admin_help_migrate", "&e/agadmin migrate &8- Claim migration wizard");
        sendLocalized(player, "admin_help_migrate_storage", "&e/agadmin migrate storage &8- YML ↔ SQL storage");
        sendLocalized(player, "admin_help_snapshot", "&e/agadmin snapshot here [reason] &8- Manual recovery snapshot");
        sendLocalized(player, "admin_help_restore", "&e/agadmin restore here confirm &8- Restore latest snapshot");
        sendLocalized(player, "admin_help_audit", "&e/agadmin audit &8- Staff Audit Ledger");
        sendLocalized(player, "admin_help_rentals", "&e/agadmin rentals <cancel|retry-settlements> ...");
        sendLocalized(player, "admin_help_bypass", "&e/agadmin bypass &8- Toggle personal protection bypass");
        sendLocalized(player, "admin_help_reload", "&e/agadmin reload &8- Reload AegisGuard");
        sendLocalized(player, "admin_help_more", "&7Also: wand, claim, manage, convert, blocks, merge, discover, activity");
    }

    private static String formatAgeMillis(long ageMillis) {
        long seconds = Math.max(0L, ageMillis / 1000L);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60L;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 48) return hours + "h";
        return (hours / 24L) + "d";
    }

    private void handleConvert(Player player, String[] args) {
        var convertGui = plugin.gui().convertToServer();
        if (!convertGui.hasConvertPermission(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        // Default path: open Staff convert GUI (type picker + confirm).
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            convertGui.openFromStanding(player);
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendLocalized(player, "convert_blocker_no_plot",
                    "&cStand inside the player plot you want to convert.");
            return;
        }
        if (!convertGui.executeConvert(player, plot, com.aegisguard.gui.ConvertToServerGUI.ConvertTarget.PLAIN)) {
            return;
        }
        audit(player, "converted plot " + plot.getPlotId() + " into a server zone via /agadmin convert confirm");
    }

    private void handleBlocks(Player player, String[] args) {
        if (args.length < 3) {
            sendLocalized(player, "admin_blocks_usage",
                    "&eUsage: /agadmin blocks <get|add|remove|set> <player> [amount] [reason]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        boolean viewOnly = action.equals("get");
        String permission = viewOnly ? "aegis.admin.blocks.view" : "aegis.admin.blocks.manage";
        if (!player.hasPermission(permission)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() == null ? args[2] : target.getName();
        if (viewOnly) {
            sendLocalized(player, "admin_blocks_view",
                    "&6{PLAYER} &7ClaimBlocks: total={TOTAL}, used={USED}, available={AVAILABLE}",
                    Map.of(
                            "PLAYER", targetName,
                            "TOTAL", String.valueOf(plugin.claimBlocks().getTotalBlocks(targetId)),
                            "USED", String.valueOf(plugin.claimBlocks().getUsedBlocks(targetId)),
                            "AVAILABLE", String.valueOf(plugin.claimBlocks().getAvailableBlocks(targetId))
                    ));
            return;
        }
        if (!List.of("add", "remove", "set").contains(action) || args.length < 4) {
            sendLocalized(player, "admin_blocks_usage_mutate",
                    "&eUsage: /agadmin blocks <add|remove|set> <player> <amount> [reason]");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException ignored) {
            sendLocalized(player, "admin_blocks_amount_invalid", "&cAmount must be a whole number.");
            return;
        }
        long maximum = Math.max(1L, plugin.getConfig().getLong("admin.max_claimblock_adjustment", 1_000_000_000L));
        if (amount < 0L || amount > maximum) {
            sendLocalized(player, "admin_blocks_amount_range",
                    "&cAmount must be between 0 and {MAX}.",
                    Map.of("MAX", String.valueOf(maximum)));
            return;
        }

        long newBalance = switch (action) {
            case "add" -> plugin.claimBlocks().adjustAvailableBlocks(targetId, amount);
            case "remove" -> plugin.claimBlocks().adjustAvailableBlocks(targetId, -amount);
            default -> plugin.claimBlocks().setAvailableBlocks(targetId, amount);
        };
        String reason = args.length > 4
                ? String.join(" ", Arrays.copyOfRange(args, 4, args.length))
                : plugin.gui().tr(player, "admin_blocks_reason_default", "No reason supplied");
        audit(player, action + " ClaimBlocks for " + targetName + " by " + amount + " (available=" + newBalance + ", reason=" + reason + ")");
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.CLAIM_BLOCK_ADJUST, player, targetName,
                    action + " ClaimBlocks by " + amount + " (available=" + newBalance + ", reason=" + reason + ")");
        }
        sendLocalized(player, "admin_blocks_updated",
                "&aUpdated {PLAYER} to {AMOUNT} available ClaimBlocks.",
                Map.of("PLAYER", targetName, "AMOUNT", String.valueOf(newBalance)));
    }

    private void handleServerMerge(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.merge") && !player.hasPermission("aegis.serverzone.manage")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sendLocalized(player, "admin_merge_usage",
                    "&eUsage: /agadmin merge <north|south|east|west> confirm");
            return;
        }
        String direction = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("north", "south", "east", "west").contains(direction)) {
            sendLocalized(player, "admin_merge_bad_direction",
                    "&cDirection must be north, south, east, or west.");
            return;
        }
        Plot current = plugin.store().getPlotAt(player.getLocation());
        if (current == null || !current.isServerZone()) {
            sendLocalized(player, "admin_merge_need_server_zone",
                    "&cStand inside the server zone that should remain after the merge.");
            return;
        }

        Plot adjacent = findMergeCandidate(current, direction);
        if (adjacent == null) {
            sendLocalized(player, "admin_merge_no_adjacent",
                    "&cNo perfectly aligned adjacent server zone exists in that direction.");
            return;
        }
        if (current.hasActiveRental() || adjacent.hasActiveRental() || current.isForSale() || adjacent.isForSale()
                || current.isForAuction() || adjacent.isForAuction()) {
            sendLocalized(player, "admin_merge_market_blocker",
                    "&cMarket or rental state must be cleared before merging server zones.");
            return;
        }

        int oldX1 = current.getX1(), oldZ1 = current.getZ1(), oldX2 = current.getX2(), oldZ2 = current.getZ2();
        List<Zone> transferredZones = new ArrayList<>(adjacent.getZones());
        if (plugin.snapshots() != null) {
            plugin.snapshots().createSnapshot(current, ClaimSnapshot.SnapshotType.PRE_MERGE,
                    "Before server-zone merge by " + player.getName(), player.getUniqueId());
            plugin.snapshots().createSnapshot(adjacent, ClaimSnapshot.SnapshotType.PRE_MERGE,
                    "Before merge into " + current.getPlotId(), player.getUniqueId());
        }
        try {
            int x1 = Math.min(current.getX1(), adjacent.getX1());
            int z1 = Math.min(current.getZ1(), adjacent.getZ1());
            int x2 = Math.max(current.getX2(), adjacent.getX2());
            int z2 = Math.max(current.getZ2(), adjacent.getZ2());
            adjacent.getZones().clear();
            current.getZones().addAll(transferredZones);
            plugin.store().removePlot(adjacent.getOwner(), adjacent.getPlotId());
            plugin.store().updatePlotBounds(current, x1, z1, x2, z2);
            if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
            plugin.territoryLife().log(current.getPlotId(), player.getUniqueId(), "SERVER_ZONE_MERGE",
                    "Merged adjacent server zone " + adjacent.getPlotId() + ".");
            audit(player, "merged server zone " + adjacent.getPlotId() + " into " + current.getPlotId());
            sendLocalized(player, "admin_merge_success",
                    "&aServer zones merged. Recovery snapshots were created.");
        } catch (Throwable error) {
            current.getZones().removeAll(transferredZones);
            adjacent.getZones().addAll(transferredZones);
            plugin.store().updatePlotBounds(current, oldX1, oldZ1, oldX2, oldZ2);
            plugin.store().addPlot(adjacent);
            plugin.store().savePlotSync(adjacent);
            sendLocalized(player, "admin_merge_failed",
                    "&cMerge failed and was rolled back: {ERROR}",
                    Map.of("ERROR", safeMessage(error)));
        }
    }

    private Plot findMergeCandidate(Plot current, String direction) {
        return plugin.store().getAllPlots().stream()
                .filter(candidate -> candidate != null && candidate.isServerZone())
                .filter(candidate -> !candidate.getPlotId().equals(current.getPlotId()))
                .filter(candidate -> candidate.getWorld().equalsIgnoreCase(current.getWorld()))
                .filter(candidate -> switch (direction) {
                    case "north" -> candidate.getZ2() + 1 == current.getZ1()
                            && candidate.getX1() == current.getX1() && candidate.getX2() == current.getX2();
                    case "south" -> current.getZ2() + 1 == candidate.getZ1()
                            && candidate.getX1() == current.getX1() && candidate.getX2() == current.getX2();
                    case "east" -> current.getX2() + 1 == candidate.getX1()
                            && candidate.getZ1() == current.getZ1() && candidate.getZ2() == current.getZ2();
                    case "west" -> candidate.getX2() + 1 == current.getX1()
                            && candidate.getZ1() == current.getZ1() && candidate.getZ2() == current.getZ2();
                    default -> false;
                })
                .findFirst()
                .orElse(null);
    }

    private void handleAdminRentals(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.rentals")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("retry-settlements")) {
            int settled = plugin.territoryLife().retrySettlements();
            sendLocalized(player, "admin_rentals_settlements_done",
                    "&aDelivered {SETTLED} pending settlement(s). Remaining: {REMAINING}",
                    Map.of("SETTLED", String.valueOf(settled),
                            "REMAINING", String.valueOf(plugin.territoryLife().settlements().size())));
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("cancel") || !args[3].equalsIgnoreCase("confirm")) {
            sendLocalized(player, "admin_rentals_usage",
                    "&eUsage: /agadmin rentals cancel <plot-id> confirm");
            sendLocalized(player, "admin_rentals_usage_or",
                    "&7Or: /agadmin rentals retry-settlements");
            return;
        }
        Plot plot;
        UUID plotId;
        if (args[2].equalsIgnoreCase("here")) {
            plot = plugin.store().getPlotAt(player.getLocation());
            if (plot == null) {
                sendLocalized(player, "admin_rentals_need_plot",
                        "&cStand inside the rented plot or provide its UUID.");
                return;
            }
            plotId = plot.getPlotId();
        } else {
            try { plotId = UUID.fromString(args[2]); }
            catch (IllegalArgumentException error) {
                sendLocalized(player, "admin_rentals_bad_plot_id",
                        "&cPlot ID must be a valid UUID, or use 'here'.");
                return;
            }
            plot = plugin.store().getAllPlots().stream()
                    .filter(candidate -> candidate != null && plotId.equals(candidate.getPlotId())).findFirst().orElse(null);
        }
        TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plotId);
        if (plot == null || contract == null) {
            sendLocalized(player, "admin_rentals_no_contract",
                    "&cNo active rental contract exists for that plot.");
            return;
        }
        plugin.territoryLife().removeContract(plotId);
        plugin.territoryLife().refundDeposit(contract, "Deposit after admin rental cancellation");
        plot.clearRenter();
        plugin.store().savePlotSync(plot);
        plugin.territoryLife().queueNoticeKey(contract.ownerId(), "admin_rental_ended_owner",
                "&eAn administrator ended rental contract &f{PLOT}&e.",
                Map.of("PLOT", String.valueOf(plotId)));
        plugin.territoryLife().queueNoticeKey(contract.renterId(), "admin_rental_ended_renter",
                "&eAn administrator ended rental contract &f{PLOT}&e. Your deposit was refunded or queued.",
                Map.of("PLOT", String.valueOf(plotId)));
        plugin.territoryLife().log(plotId, player.getUniqueId(), "ADMIN_RENTAL_CANCEL", "Contract cancelled by administrator.");
        audit(player, "cancelled rental contract for plot " + plotId);
        sendLocalized(player, "admin_rentals_cancelled", "&aRental contract cancelled safely.");
    }

    private void handleAdminDiscover(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.discovery")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendLocalized(player, "admin_discover_need_plot",
                    "&cStand inside the plot you want to update.");
            return;
        }
        if (args.length < 2) {
            sendLocalized(player, "admin_discover_usage",
                    "&eUsage: /agadmin discover <feature|unfeature|show|hide>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "feature" -> plugin.territoryLife().setFeatured(plot.getPlotId(), true);
            case "unfeature" -> plugin.territoryLife().setFeatured(plot.getPlotId(), false);
            case "show" -> plugin.territoryLife().setVisible(plot.getPlotId(), true);
            case "hide" -> plugin.territoryLife().setVisible(plot.getPlotId(), false);
            default -> {
                sendLocalized(player, "admin_discover_usage",
                        "&eUsage: /agadmin discover <feature|unfeature|show|hide>");
                return;
            }
        }
        plugin.territoryLife().log(plot.getPlotId(), player.getUniqueId(), "ADMIN_DISCOVERY",
                "Discovery state changed: " + action + ".");
        sendLocalized(player, "admin_discover_updated",
                "&aDiscovery state updated: {ACTION}.", Map.of("ACTION", action));
    }

    private void handleAdminActivity(Player player) {
        if (!player.hasPermission("aegis.admin.activity")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendLocalized(player, "admin_activity_need_plot",
                    "&cStand inside a plot to view its activity.");
            return;
        }
        List<TerritoryLifeService.ActivityEntry> entries = plugin.territoryLife().activity(plot.getPlotId(), 20);
        sendLocalized(player, "admin_activity_header",
                "&6Territory Activity: {PLOT}", Map.of("PLOT", String.valueOf(plot.getPlotId())));
        if (entries.isEmpty()) {
            sendLocalized(player, "admin_activity_empty", "&7No activity recorded.");
        }
        for (TerritoryLifeService.ActivityEntry entry : entries) {
            sendLocalized(player, "admin_activity_line",
                    "&8- &e{TYPE} &7{DETAILS}",
                    Map.of("TYPE", entry.type(), "DETAILS", entry.details() == null ? "" : entry.details()));
        }
    }

    private void audit(Player actor, String action) {
        plugin.getLogger().info("[Admin Audit] " + actor.getName() + " " + action + ".");
        if (plugin.notifications() != null) {
            plugin.notifications().notifyAdmins(
                    "aegis.admin",
                    "admin_notify_action",
                    "&6[Admin] &e{PLAYER} &7{ACTION}.",
                    Map.of("PLAYER", actor.getName(), "ACTION", action));
        }
    }

    private void sendLocalized(CommandSender sender, String key, String fallback) {
        sendLocalized(sender, key, fallback, Map.of());
    }

    private void sendLocalized(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        String message;
        if (sender instanceof Player player) {
            message = plugin.gui().tr(player, key, fallback, placeholders);
        } else if (plugin.codex() != null) {
            try {
                message = plugin.codex().tr(key, placeholders == null ? Map.of() : placeholders);
            } catch (Throwable ignored) {
                message = null;
            }
            if (message == null || message.isBlank() || message.equals(key)) {
                message = fallback;
                if (placeholders != null) {
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        message = message.replace("{" + entry.getKey() + "}",
                                entry.getValue() == null ? "" : entry.getValue());
                    }
                }
            }
        } else {
            message = fallback;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace("{" + entry.getKey() + "}",
                            entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "unknown error" : throwable.getMessage();
        return (message == null || message.isBlank()) ? "unknown error" : message;
    }
}
