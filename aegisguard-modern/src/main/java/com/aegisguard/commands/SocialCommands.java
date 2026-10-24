package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;  // ✅ NEW: Currency Type enum
import com.aegisguard.groups.PlotGroup;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.*;

final class SocialCommands extends CommandBase {

    SocialCommands(AegisGuard plugin) { super(plugin); }

    void handleGroup(Player p, String[] args) {
        if (plugin.groups() == null) {
            sendKey(p, "error_generic", "&cThat feature is not available right now.");
            return;
        }
        if (!plugin.getConfig().getBoolean("group_plots.enabled", true)) {
            sendKey(p, "feature_disabled", "&cThat feature is disabled on this server.");
            return;
        }

        if (args.length < 2) {
            sendKey(p, "group_help_create", "&e/ag group create <name> &7- create a player group");
            sendKey(p, "group_help_invite", "&e/ag group invite <player> &7- invite a nearby player");
            sendKey(p, "group_help_accept", "&e/ag group accept <name> &7- accept a pending invite");
            sendKey(p, "group_help_status", "&e/ag group status &7- view group and treasury status");
            sendKey(p, "group_help_claim", "&e/ag group claim &7- create the group plot from your selection");
            sendKey(p, "group_help_deposit", "&e/ag group deposit <amount> &7- add money to the group or plot treasury");
            sendKey(p, "group_help_leave", "&e/ag group leave &7- leave your current group");
            sendKey(p, "group_help_disband", "&e/ag group disband &7- disband your group before it owns a plot");
            return;
        }

        PlotGroup group = plugin.groups().getGroupForPlayer(p.getUniqueId());
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    sendKey(p, "group_usage_create", "&cUsage: /ag group create <name>");
                    return;
                }
                if (group != null) {
                    sendKey(p, "group_already_in_group", "&cYou are already in a group. Leave it before creating another.");
                    return;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                PlotGroup created = plugin.groups().createGroup(p.getUniqueId(), name);
                if (created == null) {
                    sendKey(p, "group_create_failed_name_taken", "&cThat group name is unavailable, or you are already grouped.");
                    return;
                }
                sendKey(p, "group_created", "&aCreated group &e{GROUP}&a. Invite players, build a treasury, then use &e/ag group claim&a.",
                        Map.of("GROUP", created.getName()));
            }
            case "status", "info" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                Plot linkedPlot = getLinkedGroupPlot(group);
                double treasury = linkedPlot != null ? linkedPlot.getTreasuryBalance() : group.getTreasuryBalance();
                sendKey(p, "group_status_header", "&6Group Status");
                sendKey(p, "group_status_name", "&7Group: &e{GROUP}", Map.of("GROUP", group.getName()));
                sendKey(p, "group_status_leader", "&7Leader: &e{PLAYER}",
                        Map.of("PLAYER", plugin.groups().getMemberName(group.getLeader())));
                sendKey(p, "group_status_members", "&7Members: &e{COUNT}", Map.of("COUNT", String.valueOf(group.size())));
                sendKey(p, "group_status_treasury", "&7Treasury: &e{AMOUNT}",
                        Map.of("AMOUNT", plugin.eco() != null && plugin.eco().isVaultEnabled()
                                ? plugin.eco().format(treasury, CurrencyType.VAULT)
                                : String.format(Locale.US, "%.2f", treasury)));
                sendKey(p, "group_status_linked_plot", "&7Linked Plot: {STATE}",
                        Map.of("STATE", linkedPlot == null ? "&cNone" : "&aYes"));
                sendKey(p, "group_status_eligible_members", "&7Starter-eligible members: &e{COUNT}",
                        Map.of("COUNT", String.valueOf(plugin.groups().getEligibleStarterMemberCount(group))));
                sendKey(p, "group_status_starter_area", "&7Free starter max area: &e{AREA}",
                        Map.of("AREA", String.valueOf(plugin.groups().getStarterMaxArea(group))));
            }
            case "invite" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_invite", "&cUsage: /ag group invite <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || !target.isOnline()) {
                    sendKey(p, "group_player_not_found", "&cThat player is not online.");
                    return;
                }
                if (target.getUniqueId().equals(p.getUniqueId())) {
                    sendKey(p, "group_cannot_invite_self", "&cYou are already the leader.");
                    return;
                }
                if (group.isMember(target.getUniqueId())) {
                    sendKey(p, "group_already_member", "&cThat player is already in your group.");
                    return;
                }
                if (plugin.groups().isInGroup(target.getUniqueId())) {
                    sendKey(p, "group_target_already_grouped", "&cThat player is already in another group.");
                    return;
                }
                double maxDistance = plugin.getConfig().getDouble("group_plots.invite_max_distance", 32.0D);
                if (!target.getWorld().equals(p.getWorld()) || target.getLocation().distanceSquared(p.getLocation()) > (maxDistance * maxDistance)) {
                    sendKey(p, "group_invite_too_far", "&cThat player must be nearby to receive a group invite.");
                    return;
                }
                plugin.groups().invitePlayer(group, target.getUniqueId());
                sendKey(p, "group_invite_sent", "&aInvited &e{PLAYER} &ato join &e{GROUP}&a.",
                        Map.of("PLAYER", target.getName(), "GROUP", group.getName()));
                sendKey(target, "group_invite_received",
                        "&e{PLAYER} &7invited you to join &e{GROUP}&7. Use &e/ag group accept {GROUP}",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName()));
            }
            case "accept" -> {
                if (group != null) {
                    sendKey(p, "group_already_in_group", "&cYou are already in a group. Leave it before joining another.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_accept", "&cUsage: /ag group accept <name>");
                    return;
                }
                PlotGroup invited = plugin.groups().getGroupByName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                if (invited == null || !invited.hasInvite(p.getUniqueId())) {
                    sendKey(p, "group_no_invite", "&cYou do not have a pending invite for that group.");
                    return;
                }
                if (!plugin.groups().acceptInvite(invited, p.getUniqueId())) {
                    sendKey(p, "group_accept_failed", "&cThat invite could not be accepted.");
                    return;
                }
                sendKey(p, "group_joined", "&aYou joined group &e{GROUP}&a.", Map.of("GROUP", invited.getName()));
                notifyGroup(
                        invited,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_member_joined",
                        "&a{PLAYER} joined the group &e{GROUP}&a.",
                        Map.of("PLAYER", p.getName(), "GROUP", invited.getName())
                );
            }
            case "leave" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_leader_cannot_leave", "&cThe leader cannot leave. Disband the group or keep leading it.");
                    return;
                }
                if (!plugin.groups().leaveGroup(group, p.getUniqueId())) {
                    sendKey(p, "group_leave_failed", "&cYou could not leave the group right now.");
                    return;
                }
                sendKey(p, "group_left", "&eYou left group &6{GROUP}&e.", Map.of("GROUP", group.getName()));
                notifyGroup(
                        group,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_member_left",
                        "&e{PLAYER} left the group &6{GROUP}&e.",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName())
                );
            }
            case "kick" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_kick", "&cUsage: /ag group kick <player>");
                    return;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (target.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_cannot_kick_leader", "&cYou cannot kick the group leader.");
                    return;
                }
                if (!group.isMember(target.getUniqueId())) {
                    sendKey(p, "group_member_not_found", "&cThat player is not in your group.");
                    return;
                }
                if (!plugin.groups().canRemoveMemberNow(group)) {
                    sendKey(p, "group_kick_locked", "&cStarter protection is active. You can remove members in about &e{MINUTES} &cminutes.",
                            Map.of("MINUTES", plugin.groups().describeStarterLockRemaining(group)));
                    return;
                }
                if (!plugin.groups().kickMember(group, target.getUniqueId())) {
                    sendKey(p, "group_kick_failed", "&cThat member could not be removed.");
                    return;
                }
                String targetName = target.getName() == null ? args[2] : target.getName();
                sendKey(p, "group_member_kicked", "&eRemoved &6{PLAYER} &efrom the group.",
                        Map.of("PLAYER", targetName));
                if (plugin.notifications() != null) {
                    plugin.notifications().notifyPlayer(
                            target.getUniqueId(),
                            "notify_group_title",
                            "&6Group Update",
                            "notify_group_member_removed",
                            "&cYou were removed from the group &6{GROUP}&c by &e{PLAYER}&c.",
                            Map.of("GROUP", group.getName(), "PLAYER", p.getName())
                    );
                }
                notifyGroup(
                        group,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_member_kicked",
                        "&e{TARGET} was removed from the group by &6{PLAYER}&e.",
                        Map.of("TARGET", targetName, "PLAYER", p.getName(), "GROUP", group.getName())
                );
            }
            case "deposit", "add" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (args.length < 3) {
                    sendKey(p, "group_usage_deposit", "&cUsage: /ag group deposit <amount>");
                    return;
                }
                if (plugin.eco() == null || !plugin.eco().isVaultReady()) {
                    sendKey(p, "group_vault_required", "&cVault economy is required for group treasury deposits.");
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException ex) {
                    sendKey(p, "group_amount_invalid", "&cAmount must be a valid number.");
                    return;
                }
                if (amount <= 0.0D) {
                    sendKey(p, "group_amount_positive", "&cAmount must be greater than 0.");
                    return;
                }
                if (!plugin.eco().withdraw(p, amount, CurrencyType.VAULT)) {
                    sendKey(p, "group_insufficient_funds", "&cYou do not have enough money for that deposit.");
                    return;
                }
                Plot linkedPlot = getLinkedGroupPlot(group);
                if (linkedPlot != null) {
                    linkedPlot.addTreasuryFunds(amount);
                    plugin.store().savePlot(linkedPlot);
                    String newBalance = plugin.eco().format(linkedPlot.getTreasuryBalance(), CurrencyType.VAULT);
                    sendKey(p, "group_deposit_success",
                            "&aDeposited &e{AMOUNT} &ainto the group treasury. New balance: &e{BALANCE}",
                            Map.of("AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance));
                    notifyGroup(
                            group,
                            null,
                            "notify_group_title",
                            "&6Group Update",
                            "notify_group_treasury_updated",
                            "&e{PLAYER} deposited &6{AMOUNT} &einto {GROUP}'s treasury. New balance: &6{BALANCE}&e.",
                            Map.of(
                                    "PLAYER", p.getName(),
                                    "AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance,
                                    "GROUP", group.getName()
                            )
                    );
                    notifyLowTreasuryIfNeeded(group, linkedPlot, null);
                } else {
                    group.addTreasuryFunds(amount);
                    plugin.groups().setDirty(true);
                    String newBalance = plugin.eco().format(group.getTreasuryBalance(), CurrencyType.VAULT);
                    sendKey(p, "group_deposit_success",
                            "&aDeposited &e{AMOUNT} &ainto the group treasury. New balance: &e{BALANCE}",
                            Map.of("AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance));
                    notifyGroup(
                            group,
                            null,
                            "notify_group_title",
                            "&6Group Update",
                            "notify_group_treasury_updated",
                            "&e{PLAYER} deposited &6{AMOUNT} &einto {GROUP}'s treasury. New balance: &6{BALANCE}&e.",
                            Map.of(
                                    "PLAYER", p.getName(),
                                    "AMOUNT", plugin.eco().format(amount, CurrencyType.VAULT),
                                    "BALANCE", newBalance,
                                    "GROUP", group.getName()
                            )
                    );
                    notifyLowTreasuryIfNeeded(group, null, null);
                }
            }
            case "claim" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (group.hasLinkedPlot()) {
                    sendKey(p, "group_plot_already_exists", "&cThis group already has a linked plot.");
                    return;
                }
                if (!plugin.selection().hasSelection(p)) {
                    sendKey(p, "must_select", "&c❌ You must select two corners with the Wand first.");
                    return;
                }

                long area = plugin.selection().getSelectionArea(p);
                int eligible = plugin.groups().getEligibleStarterMemberCount(group);
                int minMembers = Math.max(1, plugin.getConfig().getInt("group_plots.min_members_to_claim", 2));
                if (eligible < minMembers) {
                    sendKey(p, "group_not_enough_members",
                            "&cYour group needs at least &e{COUNT} &cstarter-eligible members before it can claim a group plot.",
                            Map.of("COUNT", String.valueOf(minMembers)));
                    return;
                }

                boolean freeStarter = plugin.groups().qualifiesForFreeStarterClaim(group, (int) area);
                double charged = 0.0D;
                if (!freeStarter) {
                    if (plugin.eco() == null || !plugin.eco().isVaultReady()) {
                        sendKey(p, "group_claim_requires_vault", "&cA Vault economy is required for paid group claims.");
                        return;
                    }
                    double cost = plugin.groups().getRequiredClaimCost((int) area);
                    if (group.getTreasuryBalance() + 0.000001D < cost) {
                        sendKey(p, "group_claim_not_enough_treasury",
                                "&cYour group treasury needs &e{AMOUNT} &cmore before this plot can be claimed.",
                                Map.of("AMOUNT", plugin.eco().format(cost - group.getTreasuryBalance(), CurrencyType.VAULT)));
                        return;
                    }
                    if (!group.withdrawTreasuryFunds(cost)) {
                        sendKey(p, "group_claim_not_enough_treasury", "&cYour group treasury cannot cover that claim yet.");
                        return;
                    }
                    charged = cost;
                    plugin.groups().setDirty(true);
                }

                Plot newPlot = plugin.selection().confirmGroupClaim(p, group);
                if (newPlot == null) {
                    if (charged > 0.0D) {
                        group.addTreasuryFunds(charged);
                        plugin.groups().setDirty(true);
                    }
                    return;
                }

                if (freeStarter) {
                    plugin.groups().markStarterClaimUsed(group);
                    sendKey(p, "group_free_claim_created",
                            "&aCreated the free starter plot for &e{GROUP}&a. Protected size used: &e{AREA}&a blocks.",
                            Map.of("GROUP", group.getName(), "AREA", String.valueOf(area)));
                } else {
                    sendKey(p, "group_paid_claim_created",
                            "&aCreated the group plot for &e{GROUP}&a using &6{AMOUNT}&a from the treasury.",
                            Map.of("GROUP", group.getName(), "AMOUNT", plugin.eco().format(charged, CurrencyType.VAULT)));
                }

                plugin.groups().attachPlot(group, newPlot);
                newPlot.setTreasuryBalance(group.getTreasuryBalance());
                group.setTreasuryBalance(0.0D);
                plugin.groups().setDirty(true);
                plugin.store().savePlot(newPlot);
                notifyGroup(
                        group,
                        null,
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_plot_created",
                        "&a{PLAYER} created the group plot for &e{GROUP}&a.",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName())
                );
            }
            case "disband", "disable", "off" -> {
                if (group == null) {
                    sendKey(p, "group_not_in_group", "&cYou are not currently in a group.");
                    return;
                }
                if (!p.getUniqueId().equals(group.getLeader())) {
                    sendKey(p, "group_only_leader", "&cOnly the group leader can do that.");
                    return;
                }
                if (group.hasLinkedPlot()) {
                    sendKey(p, "group_disband_plot_exists", "&cThis group already owns a plot. Unclaim or migrate it before disbanding.");
                    return;
                }
                notifyGroup(
                        group,
                        p.getUniqueId(),
                        "notify_group_title",
                        "&6Group Update",
                        "notify_group_disbanded",
                        "&c{PLAYER} disbanded the group &6{GROUP}&c.",
                        Map.of("PLAYER", p.getName(), "GROUP", group.getName())
                );
                plugin.groups().disbandGroup(group);
                sendKey(p, "group_disbanded", "&eDisbanded group &6{GROUP}&e.", Map.of("GROUP", group.getName()));
            }
            default -> sendKey(p, "group_usage", "&cUsage: /ag group <create|invite|accept|status|deposit|claim|leave|kick|disband>");
        }
    }

    void handleAlliance(Player p, String[] args) {
        if (plugin.alliances() == null || plugin.allianceService() == null) {
            sendKey(p, "error_generic", "&cThat feature is not available right now.");
            return;
        }
        if (!plugin.getConfig().getBoolean("alliance_access.enabled", true)) {
            sendKey(p, "alliance_disabled", "&cAlliance Access is disabled on this server.");
            return;
        }

        if (args.length < 2) {
            sendKey(p, "alliance_help_create", "&e/ag alliance create <name> &7- form a new alliance");
            sendKey(p, "alliance_help_invite", "&e/ag alliance invite <player> &7- invite a player (leader)");
            sendKey(p, "alliance_help_accept", "&e/ag alliance accept &7- accept a pending invite");
            sendKey(p, "alliance_help_leave", "&e/ag alliance leave &7- leave your alliance");
            sendKey(p, "alliance_help_disband", "&e/ag alliance disband &7- disband your alliance (leader)");
            sendKey(p, "alliance_help_menu", "&e/ag alliance menu &7- open Alliance Access");
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        com.aegisguard.alliance.Alliance current = plugin.alliances().getByPlayer(p.getUniqueId());

        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    sendKey(p, "alliance_usage_create", "&cUsage: /ag alliance create <name>");
                    return;
                }
                if (current != null) {
                    sendKey(p, "alliance_already_member", "&cYou are already in an alliance.");
                    return;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
                if (name.isEmpty() || name.length() > 32) {
                    sendKey(p, "alliance_invalid_name", "&cAlliance names must be 1-32 characters.");
                    return;
                }
                com.aegisguard.alliance.Alliance created = plugin.alliances().create(name, p.getUniqueId());
                if (created == null) {
                    sendKey(p, "alliance_already_member", "&cYou are already in an alliance.");
                    return;
                }
                if (plugin.audit() != null) {
                    plugin.audit().record(com.aegisguard.audit.AuditCategory.ALLIANCE, p,
                            created.getName(), "Created alliance");
                }
                sendKey(p, "alliance_created", "&aCreated alliance &e{NAME}&a. Invite friends, then join a plot from Alliance Access.",
                        Map.of("NAME", created.getName()));
            }
            case "invite" -> {
                if (args.length < 3) {
                    sendKey(p, "alliance_usage_invite", "&cUsage: /ag alliance invite <player>");
                    return;
                }
                if (current == null) {
                    sendKey(p, "alliance_not_member", "&cYou are not in an alliance.");
                    return;
                }
                if (!current.isLeader(p.getUniqueId())) {
                    sendKey(p, "alliance_not_leader", "&cOnly the alliance leader can invite players.");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sendKey(p, "player_not_found", "&cPlayer not found.");
                    return;
                }
                String err = plugin.alliances().invite(current, target.getUniqueId());
                if (err != null) {
                    sendKey(p, err, "&cCould not invite that player.");
                    return;
                }
                sendKey(p, "alliance_invite_sent", "&aInvited &e{PLAYER}&a to &e{NAME}&a.",
                        Map.of("PLAYER", target.getName(), "NAME", current.getName()));
                if (plugin.getNotificationManager() == null
                        || plugin.getNotificationManager().allowsCategory(target.getUniqueId(), "alliance")) {
                    sendKey(target, "alliance_invite_received",
                            "&e{PLAYER} &7invited you to alliance &e{NAME}&7. Use &e/ag alliance accept",
                            Map.of("PLAYER", p.getName(), "NAME", current.getName()));
                }
            }
            case "accept" -> {
                String err = plugin.alliances().accept(p.getUniqueId());
                if (err != null) {
                    sendKey(p, err, "&cCould not accept that invite.");
                    return;
                }
                com.aegisguard.alliance.Alliance joined = plugin.alliances().getByPlayer(p.getUniqueId());
                String allianceName = joined == null ? "Alliance" : joined.getName();
                if (plugin.audit() != null) {
                    plugin.audit().record(com.aegisguard.audit.AuditCategory.ALLIANCE, p,
                            allianceName, "Accepted alliance invite");
                }
                sendKey(p, "alliance_accepted", "&aYou joined alliance &e{NAME}&a.",
                        Map.of("NAME", allianceName));
            }
            case "leave" -> {
                if (current == null) {
                    sendKey(p, "alliance_not_member", "&cYou are not in an alliance.");
                    return;
                }
                String name = current.getName();
                String err = plugin.alliances().leave(p.getUniqueId());
                if (err != null) {
                    sendKey(p, err, "&cCould not leave the alliance.");
                    return;
                }
                if (plugin.audit() != null) {
                    plugin.audit().record(com.aegisguard.audit.AuditCategory.ALLIANCE, p, name, "Left alliance");
                }
                sendKey(p, "alliance_left", "&eYou left alliance &6{NAME}&e.", Map.of("NAME", name));
            }
            case "disband" -> {
                if (current == null) {
                    sendKey(p, "alliance_not_member", "&cYou are not in an alliance.");
                    return;
                }
                UUID id = current.getId();
                String name = current.getName();
                String err = plugin.alliances().disband(p.getUniqueId());
                if (err != null) {
                    sendKey(p, err, "&cCould not disband the alliance.");
                    return;
                }
                plugin.allianceService().clearPlotsForDisbandedAlliance(id, p);
                if (plugin.audit() != null) {
                    plugin.audit().record(com.aegisguard.audit.AuditCategory.ALLIANCE, p, name, "Disbanded alliance");
                }
                sendKey(p, "alliance_disbanded", "&eDisbanded alliance &6{NAME}&e. Joined plots lost alliance access.",
                        Map.of("NAME", name));
            }
            case "menu", "gui" -> plugin.gui().allianceAccess().open(p);
            case "status", "info" -> {
                if (current == null) {
                    sendKey(p, "alliance_not_member", "&cYou are not in an alliance.");
                    return;
                }
                sendKey(p, "alliance_status", "&6Alliance: &f{NAME} &7| Members: &f{COUNT} &7| Leader: &f{LEADER}",
                        Map.of(
                                "NAME", current.getName(),
                                "COUNT", String.valueOf(current.size()),
                                "LEADER", Optional.ofNullable(Bukkit.getOfflinePlayer(current.getLeaderId()).getName())
                                        .orElse("Unknown")
                        ));
            }
            default -> sendKey(p, "alliance_usage",
                    "&cUsage: /ag alliance <create|invite|accept|leave|disband|menu|status>");
        }
    }

    void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            sendKey(player, "transfer_usage", "&eUsage: /ag transfer <player>");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isOwner(player.getUniqueId())) {
            sendKey(player, "transfer_not_owner", "&cStand in a plot you own to transfer it.");
            return;
        }
        org.bukkit.OfflinePlayer recipient = Bukkit.getOfflinePlayer(args[1]);
        if (recipient.getUniqueId().equals(player.getUniqueId())) {
            sendKey(player, "transfer_self", "&cYou already own this plot.");
            return;
        }
        plugin.gui().transferConfirm().open(player, plot, recipient);
    }

    private Plot getLinkedGroupPlot(PlotGroup group) {
        if (group == null || group.getLinkedPlotId() == null) return null;
        return plugin.store().getAllPlots().stream()
                .filter(plot -> plot != null && group.getLinkedPlotId().equals(plot.getPlotId()))
                .findFirst()
                .orElse(null);
    }

}