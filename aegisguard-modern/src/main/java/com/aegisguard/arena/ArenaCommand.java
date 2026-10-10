package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import com.aegisguard.arena.preset.LavaDungeonPreset;
import com.aegisguard.data.Plot;
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
import java.util.Map;
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
            msg(sender, "arena_disabled");
            return true;
        }
        if (args.length == 0) {
            msg(sender, "arena_usage");
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
                msg(sender, "arena_unknown_subcommand");
                yield true;
            }
        };
    }

    public boolean handle(Player player, String[] args) {
        return handle((CommandSender) player, args);
    }

    private void msg(CommandSender sender, String key) {
        plugin.msg().send(sender, key);
    }

    private void msg(CommandSender sender, String key, Map<String, String> vars) {
        plugin.msg().send(sender, key, vars);
    }

    private void sendFail(CommandSender sender, String key) {
        if (key == null) return;
        Map<String, String> vars = service.takeFailVars();
        if (vars.isEmpty()) msg(sender, key);
        else msg(sender, key, vars);
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
        msg(sender, "arena_players_only");
        return false;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("aegis.arena.admin")
                || (sender instanceof Player p && plugin.isAdmin(p))
                || sender.isOp()) {
            return true;
        }
        msg(sender, "arena_no_permission");
        return false;
    }

    private boolean handleList(CommandSender sender) {
        msg(sender, "arena_list_header");
        boolean any = false;
        for (ArenaDefinition def : service.allArenas()) {
            any = true;
            String enabledLabel = sender instanceof Player p
                    ? plugin.msg().get(p, "arena_list_enabled")
                    : plugin.msg().get("arena_list_enabled");
            String disabledLabel = sender instanceof Player p
                    ? plugin.msg().get(p, "arena_list_disabled")
                    : plugin.msg().get("arena_list_disabled");
            msg(sender, "arena_list_entry", Map.of(
                    "ID", def.getId(),
                    "NAME", def.getDisplayName(),
                    "STATUS", def.isEnabled() ? enabledLabel : disabledLabel,
                    "ACTIVE", String.valueOf(service.countActiveRuns(def.getId()))));
        }
        if (!any) msg(sender, "arena_empty_none");

        // Remote arenas advertised by other backends — registry read is SQL,
        // so this block streams in asynchronously after the local list.
        var net = plugin.network();
        if (net != null && net.isNetworked() && net.store() != null) {
            plugin.scheduler().runAsync(() -> {
                List<com.aegisguard.network.NetworkModels.RemoteArena> remotes =
                        net.store().remoteArenas(net.serverName());
                if (remotes == null || remotes.isEmpty()) return;
                String onlineLabel = sender instanceof Player p
                        ? plugin.msg().get(p, "arena_list_enabled")
                        : plugin.msg().get("arena_list_enabled");
                String offlineLabel = sender instanceof Player p
                        ? plugin.msg().get(p, "arena_list_disabled")
                        : plugin.msg().get("arena_list_disabled");
                msg(sender, "arena_list_remote_header");
                for (com.aegisguard.network.NetworkModels.RemoteArena ra : remotes) {
                    if (ra == null || ra.arenaId() == null) continue;
                    boolean online = net.isServerOnline(ra.serverName());
                    msg(sender, "arena_list_remote_entry", Map.of(
                            "ID", ra.arenaId(),
                            "NAME", ra.label(),
                            "SERVER", ra.serverName() == null ? "?" : ra.serverName(),
                            "STATUS", online ? onlineLabel : offlineLabel));
                }
            });
        }
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        if (args.length < 2) {
            msg(sender, "arena_usage_join");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aegis.arena.use")) {
            msg(sender, "arena_no_permission");
            return true;
        }
        String target = args[1];

        // Re-running join against your own open lobby = "start now".
        ArenaRun mine = service.getRunForPlayer(player.getUniqueId());
        if (mine != null && mine.isInLobby() && mine.getArenaId().equalsIgnoreCase(target)) {
            if (service.startLobbyNow(player)) {
                msg(sender, "arena_lobby_started_early");
            } else {
                msg(sender, "arena_not_leader");
            }
            return true;
        }

        // Optional explicit host: /ag arena join <server>:<arena>
        String remoteServer = null;
        String arenaId = target;
        int sep = target.indexOf(':');
        if (sep > 0) {
            remoteServer = target.substring(0, sep);
            arenaId = target.substring(sep + 1);
        }

        if (remoteServer == null) {
            // An open lobby on this backend wins over starting a fresh run.
            ArenaRun lobby = service.findOpenLobby(arenaId);
            if (lobby != null) {
                String err = service.joinLobbyRun(player, lobby, null);
                if (err != null) sendFail(sender, err);
                return true;
            }
            if (service.getArena(arenaId) != null) {
                String err = service.tryStart(player, arenaId);
                if (err != null) sendFail(sender, err);
                else msg(sender, "arena_run_started");
                return true;
            }
        }
        return joinRemote(player, remoteServer, arenaId);
    }

    /**
     * Route a join to an arena hosted on another backend: resolve the host from
     * the network registry (async — it's SQL), then write an ARENA_JOIN arrival
     * and hop. {@code serverHint} comes from the {@code server:arena} syntax.
     */
    private boolean joinRemote(Player player, String serverHint, String arenaId) {
        var net = plugin.network();
        var travel = plugin.networkTravel();
        if (net == null || !net.isNetworked() || travel == null || net.store() == null) {
            msg(player, "arena_unknown");
            return true;
        }
        String localName = net.serverName();
        // Explicitly self-targeted (join thisserver:id) → treat as local.
        if (serverHint != null && localName != null && serverHint.equalsIgnoreCase(localName)) {
            String err = service.tryStart(player, arenaId);
            if (err != null) sendFail(player, err);
            else msg(player, "arena_run_started");
            return true;
        }
        plugin.scheduler().runAsync(() -> {
            List<com.aegisguard.network.NetworkModels.RemoteArena> remotes =
                    net.store().remoteArenas(localName);
            com.aegisguard.network.NetworkModels.RemoteArena match = null;
            for (com.aegisguard.network.NetworkModels.RemoteArena ra : remotes) {
                if (ra == null || ra.arenaId() == null) continue;
                if (!ra.arenaId().equalsIgnoreCase(arenaId)) continue;
                if (serverHint != null && !serverHint.equalsIgnoreCase(ra.serverName())) continue;
                if (match == null || net.isServerOnline(ra.serverName())) match = ra;
            }
            final com.aegisguard.network.NetworkModels.RemoteArena chosen = match;
            plugin.scheduler().runEntity(player, () -> {
                if (chosen == null) {
                    msg(player, "arena_unknown");
                    return;
                }
                if (!net.isServerOnline(chosen.serverName())) {
                    msg(player, "arena_remote_offline",
                            Map.of("SERVER", chosen.serverName() == null ? "?" : chosen.serverName()));
                    return;
                }
                travel.sendToArenaJoin(player, chosen.serverName(), chosen.arenaId());
            }, null);
        });
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
            msg(sender, "arena_left_run");
            return true;
        }
        service.leaveParty(player);
        msg(sender, "arena_left_party");
        return true;
    }

    private boolean handleParty(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 2) {
            msg(sender, "arena_usage_party");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "invite" -> {
                if (args.length < 3) {
                    msg(sender, "arena_usage_party_invite");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    msg(sender, "arena_player_not_found");
                    return true;
                }
                String err = service.invite(player, target);
                if (err != null) sendFail(sender, err);
                else {
                    msg(sender, "arena_invited", Map.of("PLAYER", target.getName()));
                    msg(target, "arena_invite_received", Map.of("PLAYER", player.getName()));
                }
            }
            case "accept" -> {
                String err = service.accept(player);
                if (err != null) sendFail(sender, err);
                else msg(sender, "arena_joined_party");
            }
            case "deny", "decline" -> {
                service.decline(player);
                msg(sender, "arena_invite_declined");
            }
            case "leave" -> {
                service.leaveParty(player);
                msg(sender, "arena_left_party");
            }
            default -> msg(sender, "arena_unknown_party_action");
        }
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        String arenaId = args.length >= 2 ? args[1] : null;
        if (arenaId == null) {
            msg(sender, "arena_usage_stats");
            return true;
        }
        ArenaDefinition def = service.getArena(arenaId);
        if (def == null) {
            msg(sender, "arena_unknown");
            return true;
        }
        msg(sender, "arena_leaderboard_header", Map.of("NAME", def.getDisplayName()));
        List<ArenaLeaderboardRecord> top = service.leaderboard().top(
                ArenaLeaderboardRecord.Board.SOLO_SCORE, def.getId(), def.getMode(), 5);
        int i = 1;
        for (ArenaLeaderboardRecord r : top) {
            msg(sender, "arena_leaderboard_entry", Map.of(
                    "RANK", String.valueOf(i++),
                    "WAVE", String.valueOf(r.getWave()),
                    "SCORE", String.valueOf(r.getScore()),
                    "TIME", String.valueOf(r.getClearTimeMillis())));
        }
        if (top.isEmpty()) msg(sender, "arena_leaderboard_empty");
        return true;
    }

    private boolean handleSpectate(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (!player.hasPermission("aegis.arena.spectate")) {
            msg(sender, "arena_no_permission");
            return true;
        }
        if (args.length < 2) {
            msg(sender, "arena_usage_spectate");
            return true;
        }
        ArenaDefinition def = service.getArena(args[1]);
        if (def == null) {
            msg(sender, "arena_unknown");
            return true;
        }
        var loc = service.toLocation(def.getSpectatorSpawn() != null ? def.getSpectatorSpawn() : def.getExitSpawn());
        if (loc == null) {
            msg(sender, "arena_no_spectator_spawn");
            return true;
        }
        service.teleportPlayerAllowed(player, loc);
        msg(sender, "arena_spectating", Map.of("NAME", def.getDisplayName()));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            msg(sender, "arena_usage_create");
            return true;
        }
        ArenaDefinition def = service.createArena(args[1]);
        msg(sender, "arena_created", Map.of("ID", def.getId()));

        // Dedicated arena world convenience: standing in it on unclaimed ground
        // auto-claims a server zone and pre-binds lobby/floor + entry/exit.
        if (sender instanceof Player player && plugin.arenaWorld() != null) {
            Plot zone = plugin.arenaWorld().autoZone(player);
            if (zone != null) {
                String bindErr = service.bindLobbyFromPlayer(player, def.getId());
                if (bindErr == null) bindErr = service.bindFloorFromPlayer(player, def.getId());
                if (bindErr == null) bindErr = service.setSpawn(player, def.getId(), "entry");
                if (bindErr == null) bindErr = service.setSpawn(player, def.getId(), "exit");
                if (bindErr != null) sendFail(sender, bindErr);
                else msg(sender, "arena_auto_zoned",
                        Map.of("ID", def.getId(), "WORLD", plugin.arenaWorld().worldName()));
            }
        }
        return true;
    }

    private boolean handleBind(CommandSender sender, String[] args, String sub) {
        if (!requireAdmin(sender) || !requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (args.length < 2) {
            msg(sender, "arena_usage_bind", Map.of("SUB", sub));
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
        if (err != null) sendFail(sender, err);
        else msg(sender, "arena_plot_bound");
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !requirePlayer(sender)) return true;
        if (args.length < 3) {
            msg(sender, "arena_usage_setspawn");
            return true;
        }
        String err = service.setSpawn((Player) sender, args[1], args[2]);
        if (err != null) sendFail(sender, err);
        else msg(sender, "arena_spawn_set");
        return true;
    }

    private boolean handleEnable(CommandSender sender, String[] args, boolean enable) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            msg(sender, enable ? "arena_usage_enable" : "arena_usage_disable");
            return true;
        }
        String err = service.setArenaEnabled(args[1], enable);
        if (err != null) sendFail(sender, err);
        else msg(sender, enable ? "arena_enabled_ok" : "arena_disabled_ok");
        return true;
    }

    private boolean handlePreset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 3 || !"lava_dungeon".equalsIgnoreCase(args[1])) {
            msg(sender, "arena_usage_preset");
            return true;
        }
        ArenaDefinition def = service.applyLavaPreset(args[2]);
        msg(sender, "arena_preset_applied", Map.of(
                "PRESET", LavaDungeonPreset.PRESET_ID,
                "ID", def.getId()));
        return true;
    }

    private boolean handleAbort(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            msg(sender, "arena_usage_abort");
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
                msg(sender, "arena_abort_target_required");
                return true;
            }
        }
        if (err != null) sendFail(sender, err);
        else msg(sender, "arena_run_aborted");
        return true;
    }

    private boolean handleRecover(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            msg(sender, "arena_usage_recover");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "arena_player_not_online");
            return true;
        }
        String err = service.recoverPlayer(target);
        if (err != null) sendFail(sender, err);
        else msg(sender, "arena_recovery_applied");
        return true;
    }

    private boolean handleCleanup(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            msg(sender, "arena_usage_cleanup");
            return true;
        }
        String err = service.cleanupArena(args[1]);
        if (err != null) sendFail(sender, err);
        else msg(sender, "arena_cleanup_complete");
        return true;
    }

    private boolean handleDiag(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        msg(sender, "arena_diag_header");
        for (String line : service.diagnostics().split("\n")) {
            sender.sendMessage("§7" + line);
        }
        return true;
    }

    private boolean handleRewards(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2) {
            msg(sender, "arena_usage_rewards");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("review".equals(action)) {
            List<ArenaRewardEntry> list = service.rewardsReview();
            msg(sender, "arena_reward_review_header", Map.of("COUNT", String.valueOf(list.size())));
            for (ArenaRewardEntry e : list) {
                String detail = e.getDetail() == null ? "" : " — " + e.getDetail();
                msg(sender, "arena_reward_review_entry", Map.of(
                        "ID", e.getEntryId(),
                        "STATUS", String.valueOf(e.getStatus()),
                        "DETAIL", detail));
            }
            return true;
        }
        if ("resolve".equals(action)) {
            if (args.length < 3) {
                msg(sender, "arena_usage_rewards_resolve");
                return true;
            }
            boolean commit = args.length < 4 || !"cancel".equalsIgnoreCase(args[3]);
            String err = service.rewardsResolve(args[2], commit);
            if (err != null) sendFail(sender, err);
            else msg(sender, commit ? "arena_reward_committed" : "arena_reward_cancelled");
            return true;
        }
        msg(sender, "arena_unknown_rewards_action");
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!requirePlayer(sender)) return true;
        Player player = (Player) sender;
        if (plugin.gui() != null && plugin.gui().arena() != null) {
            plugin.gui().arena().open(player);
        } else {
            msg(sender, "arena_gui_hint");
        }
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
