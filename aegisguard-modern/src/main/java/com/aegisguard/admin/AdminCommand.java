package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.config.ConfigMigrationService;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.migration.MigrationManager.MigrationOptions;
import com.aegisguard.migration.MigrationManager.SourcePlugin;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.snapshots.ClaimSnapshot;
import com.aegisguard.snapshots.RestoreOperation;
import com.aegisguard.snapshots.RestorePreview;
import com.aegisguard.snapshots.RestoreScope;
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final MigrationManager migrationManager;

    private static final String[] SUB_COMMANDS = {
            "reload", "bypass", "menu", "manage", "convert", "wand", "claim", "blocks", "merge", "migrate", "doctor",
            "health", "rentals", "discover", "activity", "snapshot", "restore", "audit", "season", "skill", "transition", "upgrade", "v130", "v140",
            "staffchat", "sc",
            "help"
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
                plugin.console().info("log_admin_console_reload",
                        "AegisGuard was reloaded from the server console.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("health")) {
                StaffHealthCheck.report(plugin, sender);
                return true;
            }
            if (args.length > 0 && isTransitionSubcommand(args[0])) {
                handleTransition(sender);
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
            case "season" -> handleAdminSeason(player, args);
            case "skill" -> handleAdminSkill(player, args);
            case "activity" -> handleAdminActivity(player);
            case "audit" -> handleAudit(player);
            case "transition", "upgrade", "v130", "v140" -> handleTransition(player);
            case "staffchat", "sc" -> handleStaffChat(player, args);
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
                return StringUtil.copyPartialMatches(args[1], List.of("here", "current", "operation"), new ArrayList<>());
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("operation") && plugin.getSnapshotManager() != null) {
                    List<String> ids = plugin.getSnapshotManager().getRestoreOperations().stream()
                            .filter(operation -> operation.status() == RestoreOperation.Status.PARTIAL
                                    || operation.status() == RestoreOperation.Status.PAUSED_REVIEW)
                            .map(operation -> operation.operationId().toString()).toList();
                    return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
                }
                return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("operation")) {
                return StringUtil.copyPartialMatches(args[3], List.of("retry", "release"), new ArrayList<>());
            }
            return StringUtil.copyPartialMatches(args[args.length - 1],
                    List.of("all", "data", "build", "flags", "members", "bans", "guestpasses",
                            "alliance", "lockdown", "noticeboard", "identity"), new ArrayList<>());
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
        if ((args[0].equalsIgnoreCase("staffchat") || args[0].equalsIgnoreCase("sc")) && args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], List.of("off"), new ArrayList<>());
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

    private void handleStaffChat(Player player, String[] args) {
        com.aegisguard.chat.PlotChatService chat = plugin.plotChat();
        if (chat == null || !chat.isStaffEnabled()) {
            sendLocalized(player, "staff_chat_disabled", "&cStaff chat is disabled on this server.");
            return;
        }
        if (!player.hasPermission("aegis.admin.staffchat") && !plugin.isAdmin(player)) {
            sendLocalized(player, "staff_chat_denied", "&cYou do not have permission to use staff chat.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            chat.turnOffStaff(player);
            sendLocalized(player, "staff_chat_off", "&eStaff chat off. Chat is public again.");
            return;
        }
        if (args.length >= 2) {
            String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
            switch (chat.sendStaff(player, message)) {
                case DENIED -> sendLocalized(player, "staff_chat_denied",
                        "&cYou do not have permission to use staff chat.");
                case EMPTY -> sendLocalized(player, "staff_chat_empty",
                        "&cSay something after /ag staff, or toggle with /ag staff.");
                case DISABLED -> sendLocalized(player, "staff_chat_disabled",
                        "&cStaff chat is disabled on this server.");
                default -> {
                }
            }
            return;
        }
        switch (chat.toggleStaff(player)) {
            case ON -> sendLocalized(player, "staff_chat_on",
                    "&aStaff chat on. Public chat stays with online staff.");
            case OFF -> sendLocalized(player, "staff_chat_off", "&eStaff chat off. Chat is public again.");
            case DENIED -> sendLocalized(player, "staff_chat_denied",
                    "&cYou do not have permission to use staff chat.");
            case DISABLED -> sendLocalized(player, "staff_chat_disabled",
                    "&cStaff chat is disabled on this server.");
            default -> {
            }
        }
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
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    plugin.gui().tr(player, "server_zone_manage_denied",
                            "&cYou need server-zone manage permission or the Steward role to change these settings.")));
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

        if (args.length >= 4 && "operation".equalsIgnoreCase(args[1])) {
            handleRestoreOperation(player, args[2], args[3]);
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
        EnumSet<RestoreScope> scopes = parseRestoreScopes(args);
        if (!confirmed) {
            plugin.getSnapshotManager().previewAsync(latest.getSnapshotId(), scopes).whenComplete((preview, error) ->
                    plugin.runMain(player, () -> sendRestorePreview(player, latest, preview, error, scopes)));
            return;
        }

        sendLocalized(player, "admin_restore_running", "&b[AegisGuard] &7Restoring latest snapshot...");
        UUID snapshotId = latest.getSnapshotId();
        plugin.getSnapshotManager().restoreAsync(snapshotId, player.getUniqueId(), scopes).whenComplete((result, error) -> {
            plugin.runMain(player, () -> {
                boolean restored = error == null && result != null && result.dataRestored();
                if (restored) {
                    if (result.status() == com.aegisguard.snapshots.SnapshotManager.RestoreStatus.PARTIAL
                            || result.status() == com.aegisguard.snapshots.SnapshotManager.RestoreStatus.BUILD_PARTIALLY_QUEUED
                            || result.status() == com.aegisguard.snapshots.SnapshotManager.RestoreStatus.BUILD_UNAVAILABLE) {
                        sendLocalized(player, "admin_restore_partial",
                                "&6Restore is partial and the plot remains maintenance-locked. Review the operation details.");
                    } else {
                        sendLocalized(player, "admin_restore_success",
                                "&aPlot restored from snapshot &f{ID}",
                                Map.of("ID", String.valueOf(snapshotId)));
                    }
                } else {
                    sendLocalized(player, "admin_restore_failed",
                            "&cPlot restore failed. Check the logs or doctor report.");
                }
            });
        });
    }

    private void sendRestorePreview(Player player, ClaimSnapshot snapshot, RestorePreview preview,
                                    Throwable error, Set<RestoreScope> scopes) {
        if (error != null || preview == null) {
            sendLocalized(player, "admin_restore_failed", "&cCould not prepare the restore preview.");
            return;
        }
        sendLocalized(player, "admin_restore_prompt",
                "&eLatest snapshot: &f{TYPE} &7| &f{REASON} &7| age &f{AGE}",
                Map.of("TYPE", snapshot.getType().name(),
                        "REASON", snapshot.getReason() == null || snapshot.getReason().isBlank()
                                ? plugin.gui().tr(player, "admin_restore_no_reason", "No reason recorded")
                                : snapshot.getReason(), "AGE", formatAgeMillis(snapshot.getAgeMillis())));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Owner: &f" + preview.currentOwnerName() + " &8-> &f" + preview.snapshotOwnerName()
                        + " &7| World: &f" + preview.worldName()
                        + " &7| Bounds: &f" + preview.x1() + "," + preview.z1() + " to "
                        + preview.x2() + "," + preview.z2()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Selection: &f" + scopes + " &7| Chunks: &f" + preview.estimatedChunks()
                        + " &7| Build backup: &f" + (preview.buildBackupPresent()
                        ? preview.buildBackupBytes() + " bytes / " + preview.buildBackupFiles() + " file(s)"
                        : "none")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Integrity: &f" + preview.buildIntegrity() + " &7| Compatible: &f"
                        + preview.buildCompatible() + " &7| Destination safe: &f"
                        + preview.buildDestinationSafe() + " &7| Format: &f" + preview.buildFormat()
                        + " &7| Integration: &f" + preview.buildIntegration() + " "
                        + preview.buildIntegrationVersion()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Build checksum: &f" + preview.buildChecksum()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e" + preview.preflightMessage()));
        if (preview.ready()) {
            sendLocalized(player, "admin_restore_confirm_hint",
                    "&cRun &e/agadmin restore here confirm [all|data|build|flags|members|bans|guestpasses|alliance|lockdown|noticeboard|identity] &cto continue.");
        } else {
            player.sendMessage(ChatColor.RED + "The selected restore cannot be confirmed until preflight passes. "
                    + "Use a data-only scope if the build backup is unavailable.");
        }
    }

    private EnumSet<RestoreScope> parseRestoreScopes(String[] args) {
        EnumSet<RestoreScope> scopes = EnumSet.noneOf(RestoreScope.class);
        for (String raw : args) {
            if (raw == null) continue;
            for (String token : raw.toLowerCase(Locale.ROOT).split(",")) {
                switch (token) {
                    case "all", "full" -> scopes.addAll(EnumSet.of(RestoreScope.FULL_DATA, RestoreScope.BUILD));
                    case "data" -> scopes.add(RestoreScope.FULL_DATA);
                    case "build", "builds" -> scopes.add(RestoreScope.BUILD);
                    case "flags" -> scopes.add(RestoreScope.FLAGS);
                    case "members", "roles" -> scopes.add(RestoreScope.MEMBERS_AND_ROLES);
                    case "bans" -> scopes.add(RestoreScope.BANS);
                    case "guestpasses", "passes" -> scopes.add(RestoreScope.GUEST_PASSES);
                    case "alliance" -> scopes.add(RestoreScope.ALLIANCE_ACCESS);
                    case "lockdown" -> scopes.add(RestoreScope.LOCKDOWN);
                    case "noticeboard", "notices" -> scopes.add(RestoreScope.NOTICEBOARD);
                    case "identity", "bounds", "settings" -> scopes.add(RestoreScope.IDENTITY_AND_BOUNDS);
                    case "plotsettings", "travel", "cosmetics" -> scopes.add(RestoreScope.PLOT_SETTINGS);
                    case "economy", "market", "rentals" -> scopes.add(RestoreScope.ECONOMY);
                    case "progression", "horizons" -> scopes.add(RestoreScope.PROGRESSION);
                    case "social", "likes" -> scopes.add(RestoreScope.SOCIAL);
                    case "zones", "stalls" -> scopes.add(RestoreScope.ZONES_AND_STALLS);
                    default -> { }
                }
            }
        }
        return scopes.isEmpty() ? EnumSet.of(RestoreScope.FULL_DATA, RestoreScope.BUILD) : scopes;
    }

    private void handleRestoreOperation(Player player, String operationRaw, String action) {
        UUID operationId;
        try {
            operationId = UUID.fromString(operationRaw);
        } catch (IllegalArgumentException error) {
            player.sendMessage(ChatColor.RED + "Invalid restore operation ID.");
            return;
        }
        RestoreOperation operation = plugin.getSnapshotManager().getRestoreOperation(operationId);
        if (operation == null) {
            player.sendMessage(ChatColor.RED + "Restore operation not found.");
            return;
        }
        if ("release".equalsIgnoreCase(action)) {
            plugin.getSnapshotManager().releaseRestoreLockAsync(operationId).whenComplete((released, error) ->
                    plugin.runMain(player, () -> player.sendMessage(error == null && Boolean.TRUE.equals(released)
                            ? ChatColor.GREEN + "Maintenance lock durably released after staff review."
                            : ChatColor.RED + "That operation could not be durably released.")));
            return;
        }
        if ("retry".equalsIgnoreCase(action)) {
            player.sendMessage(ChatColor.AQUA + "Retrying restore operation as a new transaction...");
            plugin.getSnapshotManager().retryRestore(operationId, player.getUniqueId()).whenComplete((result, error) ->
                    plugin.runMain(player, () -> player.sendMessage(error == null && result != null && result.complete()
                            ? ChatColor.GREEN + "Restore retry completed."
                            : ChatColor.GOLD + "Restore retry needs review: "
                                    + (result == null ? safeMessage(error) : result.detail()))));
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Use retry or release.");
    }

    private void sendAdminHelp(Player player) {
        sendLocalized(player, "admin_help_header", "&6AegisGuard Staff Commands");
        sendLocalized(player, "admin_help_menu", "&e/agadmin &7| &e/agadmin menu &8- Staff Command Center");
        sendLocalized(player, "admin_help_doctor", "&e/agadmin doctor [scan|report|repair confirm] &8- Territory Doctor");
        sendLocalized(player, "admin_help_health", "&e/agadmin health &8- Quick health check");
        sendLocalized(player, "admin_help_migrate", "&e/agadmin migrate &8- Claim migration wizard");
        sendLocalized(player, "admin_help_migrate_storage", "&e/agadmin migrate storage &8- YML ↔ SQL storage");
        sendLocalized(player, "admin_help_snapshot", "&e/agadmin snapshot here [reason] &8- Manual recovery snapshot");
        sendLocalized(player, "admin_help_restore", "&e/agadmin restore here [confirm] [scope] &8- Preview/restore latest snapshot");
        sendLocalized(player, "admin_help_audit", "&e/agadmin audit &8- Staff Audit Ledger");
        sendLocalized(player, "admin_help_rentals", "&e/agadmin rentals <cancel|retry-settlements> ...");
        sendLocalized(player, "admin_help_bypass", "&e/agadmin bypass &8- Toggle personal protection bypass");
        sendLocalized(player, "admin_help_reload", "&e/agadmin reload &8- Reload AegisGuard");
        sendLocalized(player, "admin_help_transition",
                "&e/agadmin transition &8- 1.2.7 / 1.3.x → 1.4.0 upgrade status");
        sendLocalized(player, "admin_help_season", "&e/agadmin season &8- Staff season featured plots and routes");
        sendLocalized(player, "admin_help_skill", "&e/agadmin skill fly <player> [seconds] &8- Temporary flight skill");
        sendLocalized(player, "admin_help_staffchat", "&e/agadmin staffchat &8- Toggle staff radio");
        sendLocalized(player, "admin_help_more", "&7Also: wand, claim, manage, convert, blocks, merge, discover, activity");
    }

    private static boolean isTransitionSubcommand(String sub) {
        return sub != null && (sub.equalsIgnoreCase("transition")
                || sub.equalsIgnoreCase("upgrade")
                || sub.equalsIgnoreCase("v130")
                || sub.equalsIgnoreCase("v140"));
    }

    private void handleTransition(CommandSender sender) {
        int schema = plugin.getConfig().getInt("config_schema", plugin.getConfig().getInt("config-version", 0));
        int target = ConfigMigrationService.CURRENT_SCHEMA;
        sendLocalized(sender, "admin_transition_schema",
                "&7Config schema: &f{CURRENT} &7/ 1.4.0 target &f{TARGET}.",
                Map.of("CURRENT", String.valueOf(schema), "TARGET", String.valueOf(target)));
        sendLocalized(sender, "admin_transition_plots",
                "&7Plots load as-is. Claim records are not rewritten.");

        if (schema >= target) {
            sendLocalized(sender, "admin_transition_already",
                    "&aAlready on 1.4.0; nothing to convert.");
            sendLocalized(sender, "admin_transition_doctor_optional",
                    "&7Doctor is optional. Use &e/agadmin doctor scan &7only if something looks wrong.");
            return;
        }

        plugin.reloadAegisGuard(true);
        int after = plugin.getConfig().getInt("config_schema", plugin.getConfig().getInt("config-version", 0));
        sendLocalized(sender, "admin_transition_ran",
                "&aConfig and language merge re-ran. Schema is now &f{SCHEMA}&a.",
                Map.of("SCHEMA", String.valueOf(after)));

        ConfigMigrationService migration = plugin.configMigration();
        File backup = migration == null ? null : migration.backup();
        File report = migration == null ? null : migration.lastReport();
        sendLocalized(sender, "admin_transition_backup",
                "&7Config backup: &f{PATH}",
                Map.of("PATH", backup != null ? backup.getAbsolutePath() : "none"));
        sendLocalized(sender, "admin_transition_report",
                "&7Migration report: &f{PATH}",
                Map.of("PATH", report != null ? report.getAbsolutePath()
                        : "plugins/AegisGuard/reports/config-migration-*.txt"));
        sendLocalized(sender, "admin_transition_doctor_optional",
                "&7Doctor is optional. Use &e/agadmin doctor scan &7only if something looks wrong.");
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
            plugin.territoryLife().logKey(current.getPlotId(), player.getUniqueId(), "SERVER_ZONE_MERGE",
                    "activity_detail_server_zone_merge",
                    "Merged adjacent server zone " + adjacent.getPlotId() + ".",
                    Map.of("PLOT", String.valueOf(adjacent.getPlotId())));
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
        plugin.territoryLife().logKey(plotId, player.getUniqueId(), "ADMIN_RENTAL_CANCEL",
                "activity_detail_admin_rental_cancel", "Contract cancelled by administrator.", Map.of());
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
        plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "ADMIN_DISCOVERY",
                "activity_detail_admin_discovery",
                "Discovery state changed: " + action + ".",
                Map.of("ACTION", action));
        sendLocalized(player, "admin_discover_updated",
                "&aDiscovery state updated: {ACTION}.", Map.of("ACTION", action));
    }

    private void handleAdminSeason(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.season") && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        if (plugin.seasons() == null || !plugin.seasons().isEnabled()) {
            sendLocalized(player, "season_disabled", "&cStaff seasons are disabled.");
            return;
        }
        if (args.length < 2) {
            plugin.gui().seasons().open(player);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "title" -> {
                if (args.length < 3) {
                    sendLocalized(player, "season_title_usage", "&eUsage: /agadmin season title <name>");
                    return;
                }
                plugin.seasons().setTitle(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                sendLocalized(player, "season_title_set", "&aSeason title updated.");
            }
            case "desc", "description" -> {
                if (args.length < 3) {
                    sendLocalized(player, "season_desc_usage", "&eUsage: /agadmin season desc <text>");
                    return;
                }
                plugin.seasons().setDescription(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                sendLocalized(player, "season_desc_set", "&aSeason description updated.");
            }
            case "feature" -> {
                Plot plot = plugin.store().getPlotAt(player.getLocation());
                if (plot == null) {
                    sendLocalized(player, "season_need_plot", "&cStand inside the plot you want to feature.");
                    return;
                }
                if (!plugin.seasons().featurePlot(plot.getPlotId())) {
                    sendLocalized(player, "season_plot_limit", "&cSeason plot limit reached.");
                    return;
                }
                plugin.territoryLife().setFeatured(plot.getPlotId(), true);
                sendLocalized(player, "season_plot_featured", "&aThis plot is featured for the current season.");
            }
            case "unfeature" -> {
                Plot plot = plugin.store().getPlotAt(player.getLocation());
                if (plot == null) {
                    sendLocalized(player, "season_need_plot", "&cStand inside the plot you want to feature.");
                    return;
                }
                plugin.seasons().unfeaturePlot(plot.getPlotId());
                sendLocalized(player, "season_plot_unfeatured", "&aThis plot is no longer a season feature.");
            }
            case "route" -> {
                if (args.length < 3) {
                    sendLocalized(player, "season_route_usage", "&eUsage: /agadmin season route <name>");
                    return;
                }
                UUID routeId = plugin.seasons().resolveRoute(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                if (routeId == null) {
                    sendLocalized(player, "season_route_missing", "&cNo matching route.");
                    return;
                }
                if (!plugin.seasons().featureRoute(routeId)) {
                    sendLocalized(player, "season_route_limit", "&cSeason route limit reached.");
                    return;
                }
                sendLocalized(player, "season_route_featured", "&aThat route is featured for the current season.");
            }
            case "unroute" -> {
                if (args.length < 3) {
                    sendLocalized(player, "season_route_usage", "&eUsage: /agadmin season unroute <name>");
                    return;
                }
                UUID routeId = plugin.seasons().resolveRoute(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                if (routeId == null) {
                    sendLocalized(player, "season_route_missing", "&cNo matching route.");
                    return;
                }
                plugin.seasons().unfeatureRoute(routeId);
                sendLocalized(player, "season_route_unfeatured", "&aThat route is no longer a season feature.");
            }
            case "clear" -> {
                plugin.seasons().clear();
                sendLocalized(player, "season_cleared", "&aStaff season cleared.");
            }
            default -> plugin.gui().seasons().open(player);
        }
    }

    private void handleAdminSkill(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.skill") && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        if (plugin.flightSkills() == null || !plugin.flightSkills().isEnabled()) {
            sendLocalized(player, "flight_skill_disabled", "&cFlight skills are disabled.");
            return;
        }
        if (args.length < 3) {
            sendLocalized(player, "flight_skill_usage",
                    "&eUsage: /agadmin skill fly <player> [seconds] &7or &e/agadmin skill clear <player>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sendLocalized(player, "player_not_found", "&cPlayer not found.");
            return;
        }
        if (action.equals("clear") || action.equals("revoke")) {
            plugin.flightSkills().clear(target.getUniqueId());
            plugin.flightSkills().refreshLater(target);
            sendLocalized(player, "flight_skill_cleared", "&aCleared flight skill for {PLAYER}.",
                    Map.of("PLAYER", target.getName()));
            return;
        }
        if (!action.equals("fly") && !action.equals("flight")) {
            sendLocalized(player, "flight_skill_usage",
                    "&eUsage: /agadmin skill fly <player> [seconds] &7or &e/agadmin skill clear <player>");
            return;
        }
        int seconds = plugin.flightSkills().defaultSeconds();
        if (args.length >= 4) {
            try {
                seconds = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                sendLocalized(player, "flight_skill_usage",
                        "&eUsage: /agadmin skill fly <player> [seconds] &7or &e/agadmin skill clear <player>");
                return;
            }
        }
        plugin.flightSkills().grant(target.getUniqueId(), seconds);
        plugin.flightSkills().refreshLater(target);
        sendLocalized(player, "flight_skill_granted",
                "&aGranted flight skill to {PLAYER} for {SECONDS}s.",
                Map.of("PLAYER", target.getName(), "SECONDS", String.valueOf(seconds)));
        target.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                plugin.gui().tr(target, "flight_skill_received",
                        "&aA staff member granted you a temporary flight skill.")));
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
                    Map.of(
                            "TYPE", com.aegisguard.territory.ActivityText.resolveTypeLabel(plugin, player, entry.type()),
                            "DETAILS", com.aegisguard.territory.ActivityText.resolveDetails(
                                    plugin, player, entry.details() == null ? "" : entry.details())));
        }
    }

    private void audit(Player actor, String action) {
        plugin.console().info("log_admin_audit",
                "[Admin Audit] {PLAYER} {ACTION}.",
                "PLAYER", actor.getName(),
                "ACTION", action == null ? "" : action);
        if (plugin.notifications() != null) {
            // Permission node "aegis.admin" must remain an ASCII LuckPerms identifier.
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
