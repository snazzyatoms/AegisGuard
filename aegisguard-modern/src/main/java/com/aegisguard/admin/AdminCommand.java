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
            "rentals", "discover", "activity", "snapshot", "restore", "audit"
    };

    private static final String[] MIGRATE_ACTIONS = {
            "list", "preview", "import", "help"
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
                sender.sendMessage(ChatColor.GREEN + "AegisGuard reload complete.");
                plugin.getLogger().info("AegisGuard was reloaded from the server console.");
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Players only.");
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
            case "snapshot" -> handleSnapshot(player, args);
            case "restore" -> handleRestore(player, args);
            case "convert" -> handleConvert(player, args);
            case "blocks" -> handleBlocks(player, args);
            case "merge" -> handleServerMerge(player, args);
            case "rentals" -> handleAdminRentals(player, args);
            case "discover" -> handleAdminDiscover(player, args);
            case "activity" -> handleAdminActivity(player);
            case "audit" -> handleAudit(player);
            default -> player.sendMessage(ChatColor.YELLOW + "Usage: /aegisadmin <" + String.join("|", SUB_COMMANDS) + ">");
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

        if ((args[0].equalsIgnoreCase("snapshot") || args[0].equalsIgnoreCase("restore")) && args.length == 2) {
            return StringUtil.copyPartialMatches(args[1], List.of("here", "current"), new ArrayList<>());
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
                    "&6[Admin] &e" + player.getName() + " &7reloaded AegisGuard."
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
        player.sendMessage(ChatColor.GOLD + "Bypass Mode: " + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
        if (plugin.getNotificationManager() != null) {
            plugin.getNotificationManager().notifyAdmins(
                    "aegis.admin",
                    "&6[Admin] &e" + player.getName() + " &7set bypass mode to "
                            + (enabled ? "&aENABLED" : "&cDISABLED") + "&7."
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
            player.sendMessage(ChatColor.RED + "The audit ledger is unavailable.");
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
                player.sendMessage(ChatColor.YELLOW + "You already have an Aegis claim wand or Sentinel's Scepter.");
                return;
            }

            player.getInventory().addItem(createServerWand(player));
            plugin.selection().setPlayerWand(player, "server_claim_wand");
            player.sendMessage(ChatColor.GREEN + "You received the Sentinel's Scepter.");
            player.sendMessage(ChatColor.GRAY + "Select two corners, then use " + ChatColor.AQUA + "/agadmin claim" + ChatColor.GRAY + ".");
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("migration")) {
            if (plugin.gui().migration() != null) {
                plugin.gui().migration().giveMigrationWand(player);
            } else {
                player.sendMessage(ChatColor.RED + "Migration wand is unavailable.");
            }
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /aegisadmin wand <server|migration>");
    }

    private void handleServerClaim(Player player) {
        if (!player.hasPermission("aegis.serverzone.manage") && !player.hasPermission("aegis.admin.manage")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        if (!plugin.selection().hasSelection(player)) {
            player.sendMessage(ChatColor.RED + "You need to select two corners first with the Sentinel's Scepter.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.AQUA + "/agadmin wand server" + ChatColor.GRAY + " to get it.");
            return;
        }

        plugin.selection().setPlayerWand(player, "server_claim_wand");
        plugin.selection().confirmClaim(player, true);
    }

    private void handleServerManage(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isServerZone()) {
            player.sendMessage(ChatColor.RED + "Stand inside the server zone you want to manage.");
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
                    ChatColor.GRAY + "A tool of absolute authority.",
                    " ",
                    ChatColor.YELLOW + "Right-Click: " + ChatColor.WHITE + "Select Pos 1",
                    ChatColor.YELLOW + "Left-Click: " + ChatColor.WHITE + "Select Pos 2",
                    " ",
                    ChatColor.RED + "Creates server zones directly."
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
                player.sendMessage(ChatColor.RED + "Migration wizard is unavailable.");
            }
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("help")) {
            player.sendMessage(ChatColor.GOLD + "AegisGuard Migration");
            player.sendMessage(ChatColor.YELLOW + "/agadmin migrate");
            player.sendMessage(ChatColor.GRAY + "Open the migration wizard.");
            player.sendMessage(ChatColor.YELLOW + "/agadmin migrate list");
            player.sendMessage(ChatColor.YELLOW + "/agadmin migrate preview <source> [options]");
            player.sendMessage(ChatColor.YELLOW + "/agadmin migrate import <source> [options]");
            player.sendMessage(ChatColor.YELLOW + "/agadmin snapshot here [reason]");
            player.sendMessage(ChatColor.YELLOW + "/agadmin restore here");
            return;
        }

        if (action.equals("list")) {
            List<SourcePlugin> available = migrationManager.getAvailableSources();
            if (available.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "No supported migration sources were detected.");
                return;
            }
            player.sendMessage(ChatColor.GOLD + "Detected migration sources:");
            for (SourcePlugin source : available) {
                player.sendMessage(ChatColor.GRAY + " - " + source.getDisplayName());
            }
            return;
        }

        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin migrate <preview|import> <source>");
            return;
        }

        SourcePlugin source = SourcePlugin.fromString(args[2]);
        if (source == null) {
            player.sendMessage(ChatColor.RED + "Unknown source. Use griefprevention/gp, griefdefender/gd, or lands.");
            return;
        }

        MigrationOptions options = parseOptions(args, 3);
        if (action.equals("preview")) {
            migrationManager.previewMigration(player, source, options)
                    .whenComplete((result, error) -> plugin.runMain(player, () -> {
                        if (error != null) {
                            player.sendMessage(ChatColor.RED + "Migration preview failed: " + safeMessage(error));
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
                            player.sendMessage(ChatColor.RED + "Migration failed: " + safeMessage(error));
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

        player.sendMessage(ChatColor.YELLOW + "Unknown migrate action. Use list, preview, import, or help.");
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
            player.sendMessage(ChatColor.RED + "Snapshot system is unavailable.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Stand inside a plot to create a recovery snapshot.");
            return;
        }
        if (!plot.canManage(player, plugin)) {
            player.sendMessage(ChatColor.RED + "You cannot create a recovery snapshot for this plot.");
            return;
        }

        String reason = args.length > 2
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : "Manual admin recovery snapshot by " + player.getName();
        ClaimSnapshot snapshot = plugin.getSnapshotManager().createSnapshot(
                plot,
                ClaimSnapshot.SnapshotType.MANUAL,
                reason,
                player.getUniqueId()
        );
        player.sendMessage(ChatColor.GREEN + "Created recovery snapshot: " + snapshot.getSnapshotId());
    }

    private void handleRestore(Player player, String[] args) {
        if (plugin.getSnapshotManager() == null) {
            player.sendMessage(ChatColor.RED + "Snapshot system is unavailable.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Stand inside the plot you want to restore.");
            return;
        }
        if (!plot.canManage(player, plugin)) {
            player.sendMessage(ChatColor.RED + "You cannot restore this plot.");
            return;
        }

        ClaimSnapshot latest = plugin.getSnapshotManager().getLatestSnapshotForPlot(plot.getPlotId());
        if (latest == null) {
            player.sendMessage(ChatColor.YELLOW + "No snapshots were found for this plot.");
            return;
        }

        player.sendMessage(ChatColor.AQUA + "[AegisGuard] " + ChatColor.GRAY + "Restoring latest snapshot...");
        UUID restoredPlotId = plot.getPlotId();
        plugin.runGlobalAsync(() -> {
            boolean restored = plugin.getSnapshotManager().rollback(latest.getSnapshotId());
            plugin.runMain(player, () -> {
                if (restored) {
                    player.sendMessage(ChatColor.GREEN + "Plot restored from snapshot " + latest.getSnapshotId());
                } else {
                    player.sendMessage(ChatColor.RED + "Plot restore failed. Check the logs or doctor report.");
                }
                if (plugin.audit() != null) {
                    plugin.audit().record(AuditCategory.SNAPSHOT_RESTORE, player, restoredPlotId.toString(),
                            "Restored plot from snapshot " + latest.getSnapshotId() + (restored ? "" : " (failed)"));
                }
            });
        });
    }

    private void handleConvert(Player player, String[] args) {
        if (!player.hasPermission("aegis.convert") && !player.hasPermission("aegis.admin.manage")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Stand inside the player plot you want to convert.");
            return;
        }
        if (plot.isServerZone()) {
            player.sendMessage(ChatColor.YELLOW + "This plot is already a server zone.");
            return;
        }
        if (plot.isGroupPlot() || plot.hasActiveRental() || plot.isForAuction()
                || plot.getZones().stream().anyMatch(zone -> zone.isRented() || zone.isListedForRent())) {
            player.sendMessage(ChatColor.RED + "Group, rented, or auction plots must be resolved before conversion.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.GOLD + "This permanently transfers the plot to the server and clears player access.");
            player.sendMessage(ChatColor.YELLOW + "Run /agadmin convert confirm to continue. A recovery snapshot will be created.");
            return;
        }

        UUID previousOwner = plot.getOwner();
        if (plugin.snapshots() != null) {
            plugin.snapshots().createSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL,
                    "Before server-zone conversion by " + player.getName(), player.getUniqueId());
        }
        plot.setForSale(false, 0);
        plot.setForRent(false, 0);
        plot.setForAuction(false);
        plot.clearPlayerAccess();
        plot.getZones().forEach(Zone::clearGuests);
        plugin.store().changePlotOwner(plot, Plot.SERVER_OWNER_UUID, "Server");
        plugin.store().savePlotSync(plot);
        plugin.claimBlocks().invalidateOwnerCache(previousOwner);
        if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.territoryLife().log(plot.getPlotId(), player.getUniqueId(), "SERVER_ZONE_CONVERT",
                "Player territory converted into a server zone.");
        audit(player, "converted plot " + plot.getPlotId() + " from owner " + previousOwner + " into a server zone");
        player.sendMessage(ChatColor.GREEN + "Plot converted into a server zone. Recovery snapshot created.");
    }

    private void handleBlocks(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin blocks <get|add|remove|set> <player> [amount] [reason]");
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
            player.sendMessage(ChatColor.GOLD + targetName + ChatColor.GRAY + " ClaimBlocks: total="
                    + plugin.claimBlocks().getTotalBlocks(targetId) + ", used="
                    + plugin.claimBlocks().getUsedBlocks(targetId) + ", available="
                    + plugin.claimBlocks().getAvailableBlocks(targetId));
            return;
        }
        if (!List.of("add", "remove", "set").contains(action) || args.length < 4) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin blocks <add|remove|set> <player> <amount> [reason]");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException ignored) {
            player.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return;
        }
        long maximum = Math.max(1L, plugin.getConfig().getLong("admin.max_claimblock_adjustment", 1_000_000_000L));
        if (amount < 0L || amount > maximum) {
            player.sendMessage(ChatColor.RED + "Amount must be between 0 and " + maximum + ".");
            return;
        }

        long newBalance = switch (action) {
            case "add" -> plugin.claimBlocks().adjustAvailableBlocks(targetId, amount);
            case "remove" -> plugin.claimBlocks().adjustAvailableBlocks(targetId, -amount);
            default -> plugin.claimBlocks().setAvailableBlocks(targetId, amount);
        };
        String reason = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "No reason supplied";
        audit(player, action + " ClaimBlocks for " + targetName + " by " + amount + " (available=" + newBalance + ", reason=" + reason + ")");
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.CLAIM_BLOCK_ADJUST, player, targetName,
                    action + " ClaimBlocks by " + amount + " (available=" + newBalance + ", reason=" + reason + ")");
        }
        player.sendMessage(ChatColor.GREEN + "Updated " + targetName + " to " + newBalance + " available ClaimBlocks.");
    }

    private void handleServerMerge(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.merge") && !player.hasPermission("aegis.serverzone.manage")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin merge <north|south|east|west> confirm");
            return;
        }
        String direction = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("north", "south", "east", "west").contains(direction)) {
            player.sendMessage(ChatColor.RED + "Direction must be north, south, east, or west.");
            return;
        }
        Plot current = plugin.store().getPlotAt(player.getLocation());
        if (current == null || !current.isServerZone()) {
            player.sendMessage(ChatColor.RED + "Stand inside the server zone that should remain after the merge.");
            return;
        }

        Plot adjacent = findMergeCandidate(current, direction);
        if (adjacent == null) {
            player.sendMessage(ChatColor.RED + "No perfectly aligned adjacent server zone exists in that direction.");
            return;
        }
        if (current.hasActiveRental() || adjacent.hasActiveRental() || current.isForSale() || adjacent.isForSale()
                || current.isForAuction() || adjacent.isForAuction()) {
            player.sendMessage(ChatColor.RED + "Market or rental state must be cleared before merging server zones.");
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
            player.sendMessage(ChatColor.GREEN + "Server zones merged. Recovery snapshots were created.");
        } catch (Throwable error) {
            current.getZones().removeAll(transferredZones);
            adjacent.getZones().addAll(transferredZones);
            plugin.store().updatePlotBounds(current, oldX1, oldZ1, oldX2, oldZ2);
            plugin.store().addPlot(adjacent);
            plugin.store().savePlotSync(adjacent);
            player.sendMessage(ChatColor.RED + "Merge failed and was rolled back: " + safeMessage(error));
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
            player.sendMessage(ChatColor.GREEN + "Delivered " + settled + " pending settlement(s). Remaining: "
                    + plugin.territoryLife().settlements().size());
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("cancel") || !args[3].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin rentals cancel <plot-id> confirm");
            player.sendMessage(ChatColor.GRAY + "Or: /agadmin rentals retry-settlements");
            return;
        }
        Plot plot;
        UUID plotId;
        if (args[2].equalsIgnoreCase("here")) {
            plot = plugin.store().getPlotAt(player.getLocation());
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Stand inside the rented plot or provide its UUID.");
                return;
            }
            plotId = plot.getPlotId();
        } else {
            try { plotId = UUID.fromString(args[2]); }
            catch (IllegalArgumentException error) {
                player.sendMessage(ChatColor.RED + "Plot ID must be a valid UUID, or use 'here'.");
                return;
            }
            plot = plugin.store().getAllPlots().stream()
                    .filter(candidate -> candidate != null && plotId.equals(candidate.getPlotId())).findFirst().orElse(null);
        }
        TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plotId);
        if (plot == null || contract == null) {
            player.sendMessage(ChatColor.RED + "No active rental contract exists for that plot.");
            return;
        }
        plugin.territoryLife().removeContract(plotId);
        plugin.territoryLife().refundDeposit(contract, "Deposit after admin rental cancellation");
        plot.clearRenter();
        plugin.store().savePlotSync(plot);
        plugin.territoryLife().queueNotice(contract.ownerId(), "&eAn administrator ended rental contract &f" + plotId + "&e.");
        plugin.territoryLife().queueNotice(contract.renterId(), "&eAn administrator ended rental contract &f" + plotId
                + "&e. Your deposit was refunded or queued.");
        plugin.territoryLife().log(plotId, player.getUniqueId(), "ADMIN_RENTAL_CANCEL", "Contract cancelled by administrator.");
        audit(player, "cancelled rental contract for plot " + plotId);
        player.sendMessage(ChatColor.GREEN + "Rental contract cancelled safely.");
    }

    private void handleAdminDiscover(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.discovery")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Stand inside the plot you want to update.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin discover <feature|unfeature|show|hide>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "feature" -> plugin.territoryLife().setFeatured(plot.getPlotId(), true);
            case "unfeature" -> plugin.territoryLife().setFeatured(plot.getPlotId(), false);
            case "show" -> plugin.territoryLife().setVisible(plot.getPlotId(), true);
            case "hide" -> plugin.territoryLife().setVisible(plot.getPlotId(), false);
            default -> {
                player.sendMessage(ChatColor.YELLOW + "Usage: /agadmin discover <feature|unfeature|show|hide>");
                return;
            }
        }
        plugin.territoryLife().log(plot.getPlotId(), player.getUniqueId(), "ADMIN_DISCOVERY",
                "Discovery state changed: " + action + ".");
        player.sendMessage(ChatColor.GREEN + "Discovery state updated: " + action + ".");
    }

    private void handleAdminActivity(Player player) {
        if (!player.hasPermission("aegis.admin.activity")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Stand inside a plot to view its activity.");
            return;
        }
        List<TerritoryLifeService.ActivityEntry> entries = plugin.territoryLife().activity(plot.getPlotId(), 20);
        player.sendMessage(ChatColor.GOLD + "Territory Activity: " + plot.getPlotId());
        if (entries.isEmpty()) player.sendMessage(ChatColor.GRAY + "No activity recorded.");
        for (TerritoryLifeService.ActivityEntry entry : entries) {
            player.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + entry.type()
                    + ChatColor.GRAY + " " + entry.details());
        }
    }

    private void audit(Player actor, String action) {
        plugin.getLogger().info("[Admin Audit] " + actor.getName() + " " + action + ".");
        if (plugin.notifications() != null) {
            plugin.notifications().notifyAdmins("aegis.admin", "&6[Admin] &e" + actor.getName() + " &7" + action + ".");
        }
    }

    private void sendLocalized(Player player, String key, String fallback) {
        sendLocalized(player, key, fallback, Map.of());
    }

    private void sendLocalized(Player player, String key, String fallback, Map<String, String> placeholders) {
        String message = plugin.gui().tr(player, key, fallback, placeholders);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "unknown error" : throwable.getMessage();
        return (message == null || message.isBlank()) ? "unknown error" : message;
    }
}
