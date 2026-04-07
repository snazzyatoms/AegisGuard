package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.migration.MigrationManager.MigrationOptions;
import com.aegisguard.migration.MigrationManager.SourcePlugin;
import com.aegisguard.snapshots.ClaimSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final MigrationManager migrationManager;

    private static final String[] SUB_COMMANDS = {
            "reload", "bypass", "menu", "convert", "wand", "blocks", "migrate", "doctor", "snapshot", "restore"
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
            case "wand" -> handleWand(player, args);
            case "migrate" -> handleMigrate(player, args);
            case "doctor" -> runDoctor(player);
            case "snapshot" -> handleSnapshot(player, args);
            case "restore" -> handleRestore(player, args);
            case "convert", "blocks" -> player.sendMessage(ChatColor.YELLOW + "That admin subcommand is not wired into this trimmed 1.2.6 command pass yet.");
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
            return StringUtil.copyPartialMatches(args[1], List.of("migration"), new ArrayList<>());
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
        plugin.effects().playConfirm(player);
    }

    private void handleWand(Player player, String[] args) {
        if (!player.hasPermission("aegis.admin.wand")) {
            plugin.msg().send(player, "no_perm");
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

        player.sendMessage(ChatColor.YELLOW + "Usage: /aegisadmin wand migration");
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
                        } else if (plugin.gui().migration() != null) {
                            plugin.gui().migration().openPreview(player, source, result);
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

    private void runDoctor(Player player) {
        player.sendMessage(ChatColor.AQUA + "[AegisGuard] " + ChatColor.GRAY + "Generating doctor report...");
        plugin.runGlobalAsync(() -> {
            try {
                Path report = AdminDiagnostics.writeReport(plugin);
                plugin.runMain(player, () -> player.sendMessage(ChatColor.GREEN + "Doctor report saved: " + report.getFileName()));
            } catch (Exception e) {
                plugin.runMain(player, () -> player.sendMessage(ChatColor.RED + "Doctor report failed: " + e.getMessage()));
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
        plugin.runGlobalAsync(() -> {
            boolean restored = plugin.getSnapshotManager().rollback(latest.getSnapshotId());
            plugin.runMain(player, () -> {
                if (restored) {
                    player.sendMessage(ChatColor.GREEN + "Plot restored from snapshot " + latest.getSnapshotId());
                } else {
                    player.sendMessage(ChatColor.RED + "Plot restore failed. Check the logs or doctor report.");
                }
            });
        });
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "unknown error" : throwable.getMessage();
        return (message == null || message.isBlank()) ? "unknown error" : message;
    }
}
