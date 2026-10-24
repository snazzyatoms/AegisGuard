package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.stream.Collectors;

final class RealmCommands extends CommandBase {

    RealmCommands(AegisGuard plugin) { super(plugin); }

    void handleRename(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "usage_rename", "&cUsage: /ag rename <n>");
            return;
        }

        String name = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        name = ChatColor.translateAlternateColorCodes('&', name);

        if (name.length() > 32) {
            sendKey(p, "rename_too_long", "&cName is too long (Max {MAX} chars).",
                    Map.of("MAX", "32"));
            return;
        }

        String oldName = getReadablePlotName(plot);
        plot.setEntryTitle(name);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "plot_renamed", "&a✔ Plot renamed to: &r{NAME}",
                Map.of("NAME", name));
        notifyPlot(
                plot,
                p.getUniqueId(),
                "notify_claim_title",
                "&bClaim Update",
                "notify_plot_renamed",
                "&e{PLAYER} renamed {OLD_NAME} &eto &b{NEW_NAME}&e.",
                Map.of(
                        "PLAYER", p.getName(),
                        "OLD_NAME", oldName,
                        "NEW_NAME", name
                )
        );
        plugin.effects().playConfirm(p);
    }

    void handleSetDesc(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "usage_setdesc", "&cUsage: /ag setdesc <Description>");
            return;
        }

        String desc = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        desc = ChatColor.translateAlternateColorCodes('&', desc);

        plot.setDescription(desc);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "plot_desc_updated", "&a✔ Plot description updated.");
        plugin.effects().playConfirm(p);
    }

    void handleNotice(Player p, String[] args) {
        if (!plugin.getConfig().getBoolean("realm_profiles.enabled", true)
                || !plugin.getConfig().getBoolean("realm_profiles.noticeboard.enabled", true)) {
            sendKey(p, "noticeboard_disabled", "&cThe noticeboard is disabled on this server.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }

        if (args.length < 2) {
            sendMsg(p, "&e/ag notice add <text> &7| &e/ag notice remove <#> &7| &e/ag notice list");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        if (action.equals("list")) {
            plugin.gui().realmProfile().openNoticeboard(p, plot);
            return;
        }

        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        if (action.equals("add")) {
            if (args.length < 3) {
                sendKey(p, "usage_notice_add", "&cUsage: /ag notice add <text>");
                return;
            }
            String text = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
            text = ChatColor.translateAlternateColorCodes('&', text);

            int maxLength = Math.max(1, plugin.getConfig().getInt("realm_profiles.noticeboard.max_length", 200));
            if (text.length() > maxLength) {
                sendKey(p, "notice_too_long", "&cThat notice is too long (Max {MAX} chars).",
                        Map.of("MAX", String.valueOf(maxLength)));
                return;
            }

            int maxEntries = Math.max(1, plugin.getConfig().getInt("realm_profiles.noticeboard.max_entries", 8));
            plot.postNotice(com.aegisguard.profile.PlotNotice.post(p.getUniqueId(), p.getName(), text), maxEntries);
            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);
            plugin.territoryLife().logKey(plot.getPlotId(), p.getUniqueId(), "NOTICE_POSTED",
                    "activity_detail_notice_posted", "Posted a noticeboard notice.", Map.of());

            sendKey(p, "notice_posted", "&a✔ Notice posted to this plot's noticeboard.");
            plugin.effects().playConfirm(p);
            return;
        }

        if (action.equals("remove")) {
            if (args.length < 3) {
                sendKey(p, "usage_notice_remove", "&cUsage: /ag notice remove <#>");
                return;
            }
            List<com.aegisguard.profile.PlotNotice> notices = plot.getNoticeboard();
            int index;
            try {
                index = Integer.parseInt(args[2]) - 1;
            } catch (NumberFormatException ex) {
                sendKey(p, "usage_notice_remove", "&cUsage: /ag notice remove <#>");
                return;
            }
            if (index < 0 || index >= notices.size()) {
                sendKey(p, "notice_invalid_index", "&cThat is not a valid notice number. Use /ag notice list to see them.");
                return;
            }

            plot.removeNotice(notices.get(index).getId());
            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);
            plugin.territoryLife().logKey(plot.getPlotId(), p.getUniqueId(), "NOTICE_REMOVED",
                    "activity_detail_notice_removed", "Removed a noticeboard notice.", Map.of());

            sendKey(p, "notice_removed", "&e✔ Notice removed.");
            plugin.effects().playConfirm(p);
            return;
        }

        sendMsg(p, "&e/ag notice add <text> &7| &e/ag notice remove <#> &7| &e/ag notice list");
    }

    void handleWelcomeFarewell(Player p, String[] args, boolean isWelcome) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        if (args.length < 2) {
            if (isWelcome) plot.setWelcomeMessage(null);
            else plot.setFarewellMessage(null);

            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);

            if (isWelcome) sendKey(p, "welcome-cleared", "&e✔ Welcome message cleared.");
            else sendKey(p, "farewell-cleared", "&e✔ Farewell message cleared.");
            return;
        }

        String msg = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        if (isWelcome) plot.setWelcomeMessage(msg);
        else plot.setFarewellMessage(msg);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        if (isWelcome) sendKey(p, "welcome-set", "&a✔ Welcome message updated.");
        else sendKey(p, "farewell-set", "&a✔ Farewell message updated.");
    }

    void handleArrival(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "arrival_status",
                    "&7Public arrival for this plot: &f{MODE}&7. Use &e/ag arrival classic|beacon&7.",
                    Map.of("MODE", plot.getArrivalMode().name().toLowerCase(Locale.ROOT)));
            return;
        }
        Plot.ArrivalMode mode;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "classic", "spawn", "off" -> mode = Plot.ArrivalMode.CLASSIC;
            case "beacon", "pad", "on" -> mode = Plot.ArrivalMode.BEACON;
            default -> {
                sendKey(p, "arrival_usage", "&cUsage: /ag arrival <classic|beacon>");
                return;
            }
        }
        plot.setArrivalMode(mode);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        sendKey(p, "arrival_set", "&a✔ Public arrival set to &f{MODE}&a for this plot.",
                Map.of("MODE", mode.name().toLowerCase(Locale.ROOT)));
        if (plugin.effects() != null) plugin.effects().playConfirm(p);
    }

    void handleRoles(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!plot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "roles_usage", "&eUsage: /ag roles <lock|unlock|undo> [player]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("undo")) {
            if (!plot.undoLastRoleChange()) {
                sendKey(p, "roles_undo_empty", "&cNo recent role change to undo on this plot.");
                plugin.effects().playError(p);
                return;
            }
            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);
            if (plugin.audit() != null) {
                plugin.audit().record(com.aegisguard.audit.AuditCategory.ROLE_CHANGE, p,
                        plot.getPlotName(), "Undid last role change");
            }
            sendKey(p, "roles_undo_ok", "&aUndid the last role change on this plot.");
            plugin.effects().playConfirm(p);
            return;
        }
        if (!action.equals("lock") && !action.equals("unlock")) {
            sendKey(p, "roles_usage", "&eUsage: /ag roles <lock|unlock|undo> [player]");
            return;
        }
        if (args.length < 3) {
            sendKey(p, "roles_usage", "&eUsage: /ag roles <lock|unlock|undo> [player]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        UUID targetId = target.getUniqueId();
        boolean changed = action.equals("lock") ? plot.lockMember(targetId) : plot.unlockMember(targetId);
        if (!changed) {
            sendKey(p, "roles_lock_failed", "&cCould not {ACTION} that member.",
                    Map.of("ACTION", action));
            plugin.effects().playError(p);
            return;
        }
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        if (plugin.audit() != null) {
            String name = target.getName() == null ? targetId.toString() : target.getName();
            plugin.audit().record(com.aegisguard.audit.AuditCategory.ROLE_CHANGE, p,
                    plot.getPlotName(), action + " " + name);
        }
        sendKey(p, "roles_lock_ok", "&aMember {PLAYER} is now {ACTION}.",
                Map.of("PLAYER", target.getName() == null ? args[2] : target.getName(),
                        "ACTION", action + "ed"));
        plugin.effects().playConfirm(p);
    }

    void handleKick(Player p, String[] args) {
        if (args.length < 2) {
            sendKey(p, "kick_usage", "&cUsage: /ag kick <player>");
            return;
        }

        Plot kPlot = plugin.store().getPlotAt(p.getLocation());
        if (kPlot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (!kPlot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        Player kTarget = Bukkit.getPlayer(args[1]);
        if (kTarget == null) {
            sendKey(p, "player_not_found", "&cPlayer not found.");
            return;
        }
        if (!kPlot.isInside(kTarget.getLocation())) {
            sendKey(p, "player_not_in_plot", "&cPlayer is not in your plot.");
            return;
        }
        if (kTarget.getUniqueId().equals(p.getUniqueId())) {
            sendKey(p, "cannot_kick_self", "&cYou cannot kick yourself.");
            return;
        }
        if (kPlot.isOwner(kTarget.getUniqueId()) || Plot.SERVER_OWNER_UUID.equals(kTarget.getUniqueId())) {
            sendKey(p, "cannot_kick_owner", "&cYou cannot kick the plot owner.");
            return;
        }
        if (kTarget.hasPermission("aegis.admin.bypass") || kTarget.isOp()) {
            sendKey(p, "cannot_kick_operator", "&cYou cannot kick a Server Operator.");
            return;
        }

        Location spawn = kTarget.getWorld().getSpawnLocation();
        plugin.safeTravel().travel(kTarget, spawn, com.aegisguard.travel.SafeTravelService.Kind.SPAWN, false);

        // Target gets message in THEIR language.
        sendKey(kTarget, "kicked_target", "§cYou were kicked from {OWNER}'s plot.",
                Map.of("OWNER", kPlot.getOwnerName()));

        // Sender gets message in THEIR language.
        sendKey(p, "kicked_sender", "&eKicked {PLAYER}",
                Map.of("PLAYER", kTarget.getName()));
    }

    void handleBan(Player p, String[] args) {
        if (args.length < 2) {
            sendKey(p, "ban_usage", "&cUsage: /ag ban <player>");
            return;
        }

        Plot bPlot = plugin.store().getPlotAt(p.getLocation());
        if (bPlot == null) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (!bPlot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        OfflinePlayer bTarget = Bukkit.getOfflinePlayer(args[1]);
        if (bTarget.getUniqueId() != null && bTarget.getUniqueId().equals(p.getUniqueId())) {
            sendKey(p, "ban_self_error", "&cCannot ban yourself.");
            return;
        }
        if (bTarget.getUniqueId() != null
                && (bPlot.isOwner(bTarget.getUniqueId()) || Plot.SERVER_OWNER_UUID.equals(bTarget.getUniqueId()))) {
            sendKey(p, "ban_owner_error", "&cCannot ban the plot owner.");
            return;
        }

        bPlot.addBan(bTarget.getUniqueId());

        plugin.store().savePlot(bPlot);
        plugin.store().setDirty(true);

        sendKey(p, "ban_success", "&cBanned {PLAYER}",
                Map.of("PLAYER", (bTarget.getName() == null ? args[1] : bTarget.getName())));

        if (bTarget.isOnline()) {
            Player online = bTarget.getPlayer();
            if (online != null && bPlot.isInside(online.getLocation())) {
                Location spawn = online.getWorld().getSpawnLocation();
                plugin.safeTravel().travel(online, spawn, com.aegisguard.travel.SafeTravelService.Kind.SPAWN, false);

                // Target gets message in THEIR language.
                sendKey(online, "ban_target", "§4You have been BANNED from this plot.");
            }
        }
    }

    void handleUnban(Player p, String[] args) {
        if (args.length < 2) {
            sendKey(p, "unban_usage", "&cUsage: /ag unban <player>");
            return;
        }

        Plot uPlot = plugin.store().getPlotAt(p.getLocation());
        if (uPlot == null) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (!uPlot.canManage(p, plugin)) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }

        OfflinePlayer uTarget = Bukkit.getOfflinePlayer(args[1]);
        uPlot.removeBan(uTarget.getUniqueId());

        plugin.store().savePlot(uPlot);
        plugin.store().setDirty(true);

        sendKey(p, "unban_success", "&aUnbanned {PLAYER}",
                Map.of("PLAYER", (uTarget.getName() == null ? args[1] : uTarget.getName())));
    }

    void handleHeir(Player player, String[] args) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isOwner(player.getUniqueId())) {
            sendKey(player, "heir_not_owner", "&cStand in a plot you own to set an heir.");
            return;
        }
        if (args.length < 2) {
            String name = plot.getHeir() == null ? "none" : Bukkit.getOfflinePlayer(plot.getHeir()).getName();
            sendKey(player, "heir_status", "&7Heir: &f{PLAYER}",
                    Map.of("PLAYER", name == null ? (plot.getHeir() == null ? "none" : plot.getHeir().toString()) : name));
            return;
        }
        if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("none")) {
            plot.setHeir(null);
            plugin.store().savePlot(plot);
            sendKey(player, "heir_cleared", "&aHeir cleared.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendKey(player, "heir_self", "&cYou cannot name yourself as heir.");
            return;
        }
        plot.setHeir(target.getUniqueId());
        plot.lockMember(target.getUniqueId());
        plugin.store().savePlot(plot);
        sendKey(player, "heir_set", "&aHeir set to &f{PLAYER}&a.",
                Map.of("PLAYER", target.getName() == null ? args[1] : target.getName()));
    }

    void handleSuccession(Player player, String[] args) {
        if (plugin.succession() == null || !plugin.succession().enabled()) {
            sendKey(player, "succession_disabled", "&cSuccession is disabled.");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendKey(player, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "menu";
        switch (action) {
            case "assume" -> {
                if (plugin.succession().assume(player, plot)) {
                    sendKey(player, "stewardship_assume_ok", "&aYou are now the owner of this plot.");
                } else {
                    sendKey(player, "succession_assume_denied",
                            "&cYou cannot assume this plot. You must be the heir and the owner must be inactive.");
                    plugin.effects().playError(player);
                }
            }
            case "rollback" -> {
                if (plugin.succession().rollback(player, plot)) {
                    sendKey(player, "stewardship_rollback_ok", "&aOwnership transfer was rolled back.");
                } else {
                    sendKey(player, "succession_rollback_denied",
                            "&cNo recent transfer is available to roll back.");
                    plugin.effects().playError(player);
                }
            }
            default -> plugin.gui().stewardship().open(player, plot);
        }
    }

}