package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import com.aegisguard.arena.preset.LavaDungeonPreset;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Arena subcommand handler. Wired from AegisCommand as {@code handle(...)}.
 */
public final class ArenaCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final ArenaService service;

    public ArenaCommand(AegisGuard plugin, ArenaService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!service.isEnabled() && (args.length == 0 || !isAdminBypass(args[0]))) {
            sender.sendMessage("§cArena module is disabled.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/ag arena <list|join|leave|party|stats|spectate|…>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "list" -> handleList(sender);
            case "join", "start" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "party" -> handleParty(sender, args);
            case "stats" -> handleStats(sender, args);
            case "spectate" -> handleSpectate(sender, args);
            case "create" -> handleCreate(sender, args);
            case "bind", "setlobby", "setfloor" -> handleBind(sender, args, sub);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "enable", "disable" -> handleEnable(sender, args, sub.equals("enable"));
            case "preset" -> handlePreset(sender, args);
            case "abort" -> handleAbort(sender, args);
            case "recover" -> handleRecover(sender, args);
            case "cleanup" -> handleCleanup(sender, args);
            case "diag", "diagnostics" -> handleDiag(sender);
            case "rewards" -> handleRewards(sender, args);
            case "gui" -> handleGui(sender);
            default -> {
                sender.sendMessage("§cUnknown arena subcommand.");
                yield true;
            }
        };
    }

    public boolean handle(Player player, String[] args) {
        return handle((CommandSender) player, args);
    }

    private boolean isAdminBypass(String sub) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "diag", "diagnostics", "enable", "disable", "create", "abort",
                    "recover", "cleanup", "rewards", "preset", "bind", "setlobby",
                    "setfloor", "setspawn" -> true;
            default -> false;
        };
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sender.sendMessage("§cPlayers only.");
        return false;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("aegis.arena.admin")
                || (sender instanceof Player p && plugin.isAdmin(p))
                || sender.isOp()) {
            return true;
        }
        sender.sendMessage("§cNo permission.");
        return false;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage("§6Arenas:");
        for (ArenaDefinition def : service.allArenas()) {
            sender.sendMessage(" §7- §e" + def.getId()
                    + " §7(" + def.getDisplayName() + ")"
                    + (def.isEnabled() ? " §aenabled" : " §cdisabled")
                    + " §8active=" + service.countActiveRuns(def.getId()));
        }
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena join <arenaId>");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aegis.arena.use")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        String err = service.tryStart(player, args[1]);
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aArena run started.");
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run != null) {
            ArenaLeadershipRules.Decision d =
                    ArenaLeadershipRules.onDeliberateLeave(run, player.getUniqueId());
            service.eliminate(player, run, "leave");
            if (d.action == ArenaLeadershipRules.Action.TRANSFER && d.newLeaderId != null) {
                if (run.tryBeginLeadershipTransfer(d.reason)) {
                    run.completeLeadershipTransfer(d.newLeaderId);
                }
            } else if (d.action == ArenaLeadershipRules.Action.END_RUN || run.countFighting() == 0) {
                service.endRun(run, ArenaEndReason.FORFEIT);
            }
            sender.sendMessage("§eYou left the arena run.");
            return true;
        }
        service.leaveParty(player);
        sender.sendMessage("§eLeft party.");
        return true;
    }

    private boolean handleParty(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena party <invite|accept|deny|leave> [player]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "invite" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /ag arena party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }
                String err = service.invite(player, target);
                if (err != null) sender.sendMessage("§c" + err);
                else {
                    sender.sendMessage("§aInvited " + target.getName() + ".");
                    target.sendMessage("§e" + player.getName() + " invited you to an arena party. /ag arena party accept");
                }
            }
            case "accept" -> {
                String err = service.accept(player);
                if (err != null) sender.sendMessage("§c" + err);
                else sender.sendMessage("§aJoined party.");
            }
            case "deny", "decline" -> {
                service.decline(player);
                sender.sendMessage("§eInvite declined.");
            }
            case "leave" -> {
                service.leaveParty(player);
                sender.sendMessage("§eLeft party.");
            }
            default -> sender.sendMessage("§cUnknown party action.");
        }
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        String arenaId = args.length >= 2 ? args[1] : null;
        if (arenaId == null) {
            sender.sendMessage("§eUsage: /ag arena stats <arenaId>");
            return true;
        }
        ArenaDefinition def = service.getArena(arenaId);
        if (def == null) {
            sender.sendMessage("§cUnknown arena.");
            return true;
        }
        sender.sendMessage("§6Leaderboard — " + def.getDisplayName());
        List<ArenaLeaderboardRecord> top = service.leaderboard().top(
                ArenaLeaderboardRecord.Board.SOLO_SCORE, def.getId(), def.getMode(), 5);
        int i = 1;
        for (ArenaLeaderboardRecord r : top) {
            sender.sendMessage(" §7#" + (i++) + " wave=" + r.getWave() + " score=" + r.getScore()
                    + " time=" + r.getClearTimeMillis() + "ms");
        }
        if (top.isEmpty()) sender.sendMessage(" §8No records yet.");
        return true;
    }

    private boolean handleSpectate(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aegis.arena.spectate")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena spectate <arenaId>");
            return true;
        }
        ArenaDefinition def = service.getArena(args[1]);
        if (def == null) {
            sender.sendMessage("§cUnknown arena.");
            return true;
        }
        var loc = service.toLocation(def.getSpectatorSpawn() != null ? def.getSpectatorSpawn() : def.getExitSpawn());
        if (loc == null) {
            sender.sendMessage("§cNo spectator/exit spawn set.");
            return true;
        }
        service.teleportPlayerAllowed(player, loc);
        sender.sendMessage("§aSpectating " + def.getDisplayName() + ".");
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena create <id>");
            return true;
        }
        ArenaDefinition def = service.createArena(args[1]);
        sender.sendMessage("§aCreated arena §e" + def.getId());
        return true;
    }

    private boolean handleBind(CommandSender sender, String[] args, String sub) {
        if (!requireAdmin(sender) || !requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena " + sub + " <arenaId>");
            return true;
        }
        String arenaId = args[1];
        String err;
        if ("setfloor".equals(sub) || (args.length >= 3 && "floor".equalsIgnoreCase(args[2]))) {
            err = service.bindFloorFromPlayer(player, arenaId);
        } else if ("setlobby".equals(sub) || (args.length >= 3 && "lobby".equalsIgnoreCase(args[2]))) {
            err = service.bindLobbyFromPlayer(player, arenaId);
        } else if ("bind".equals(sub) && args.length >= 3) {
            if ("floor".equalsIgnoreCase(args[2])) err = service.bindFloorFromPlayer(player, arenaId);
            else err = service.bindLobbyFromPlayer(player, arenaId);
        } else {
            err = service.bindLobbyFromPlayer(player, arenaId);
        }
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aPlot bound.");
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !requirePlayer(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /ag arena setspawn <arenaId> <entry|exit|mob>");
            return true;
        }
        String err = service.setSpawn((Player) sender, args[1], args[2]);
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aSpawn set.");
        return true;
    }

    private boolean handleEnable(CommandSender sender, String[] args, boolean enable) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena " + (enable ? "enable" : "disable") + " <arenaId>");
            return true;
        }
        String err = service.setArenaEnabled(args[1], enable);
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aArena " + (enable ? "enabled." : "disabled."));
        return true;
    }

    private boolean handlePreset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 3 || !"lava_dungeon".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§cUsage: /ag arena preset lava_dungeon <arenaId>");
            return true;
        }
        ArenaDefinition def = service.applyLavaPreset(args[2]);
        sender.sendMessage("§aApplied " + LavaDungeonPreset.PRESET_ID + " to §e" + def.getId()
                + " §7(bind plots/spawns before enabling).");
        return true;
    }

    private boolean handleAbort(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena abort <player|runUuid>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        String err;
        if (target != null) {
            err = service.abortPlayerRun(target);
        } else {
            try {
                err = service.abortRun(UUID.fromString(args[1]));
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cPlayer or run UUID required.");
                return true;
            }
        }
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aRun aborted.");
        return true;
    }

    private boolean handleRecover(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena recover <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        String err = service.recoverPlayer(target);
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aRecovery applied.");
        return true;
    }

    private boolean handleCleanup(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena cleanup <arenaId>");
            return true;
        }
        String err = service.cleanupArena(args[1]);
        if (err != null) sender.sendMessage("§c" + err);
        else sender.sendMessage("§aCleanup complete.");
        return true;
    }

    private boolean handleDiag(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        for (String line : service.diagnostics().split("\n")) {
            sender.sendMessage("§7" + line);
        }
        return true;
    }

    private boolean handleRewards(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ag arena rewards <review|resolve> [entryId] [commit|cancel]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("review".equals(action)) {
            List<ArenaRewardEntry> list = service.rewardsReview();
            sender.sendMessage("§6Reward review (" + list.size() + "):");
            for (ArenaRewardEntry e : list) {
                sender.sendMessage(" §e" + e.getEntryId() + " §7" + e.getStatus()
                        + (e.getDetail() == null ? "" : " — " + e.getDetail()));
            }
            return true;
        }
        if ("resolve".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /ag arena rewards resolve <entryId> [commit|cancel]");
                return true;
            }
            boolean commit = args.length < 4 || !"cancel".equalsIgnoreCase(args[3]);
            String err = service.rewardsResolve(args[2], commit);
            if (err != null) sender.sendMessage("§c" + err);
            else sender.sendMessage("§aReward entry " + (commit ? "committed." : "cancelled."));
            return true;
        }
        sender.sendMessage("§cUnknown rewards action.");
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!requirePlayer(sender)) return true;
        // GUI opened by GUIManager when wired; provide a message fallback
        sender.sendMessage("§eOpen the Arena GUI from the player menu when wired.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList(
                    "list", "join", "start", "leave", "party", "stats", "spectate",
                    "create", "bind", "setlobby", "setfloor", "setspawn",
                    "enable", "disable", "preset", "abort", "recover", "cleanup",
                    "diag", "rewards", "gui"));
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "join", "start", "stats", "spectate", "enable", "disable",
                        "setlobby", "setfloor", "setspawn", "cleanup", "bind" ->
                        filter(args[1], service.allArenas().stream().map(ArenaDefinition::getId).collect(Collectors.toList()));
                case "party" -> filter(args[1], Arrays.asList("invite", "accept", "deny", "leave"));
                case "preset" -> filter(args[1], List.of("lava_dungeon"));
                case "rewards" -> filter(args[1], Arrays.asList("review", "resolve"));
                case "abort", "recover" -> null;
                default -> List.of();
            };
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("setspawn".equals(sub)) {
                return filter(args[2], Arrays.asList("entry", "exit", "mob"));
            }
            if ("party".equals(sub) && "invite".equalsIgnoreCase(args[1])) {
                return null; // player names
            }
            if ("preset".equals(sub)) {
                return filter(args[2], service.allArenas().stream().map(ArenaDefinition::getId).collect(Collectors.toList()));
            }
            if ("bind".equals(sub)) {
                return filter(args[2], Arrays.asList("lobby", "floor"));
            }
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
        return out;
    }
}
