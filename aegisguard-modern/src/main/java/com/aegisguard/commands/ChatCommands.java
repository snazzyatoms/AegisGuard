package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.groups.PlotGroup;
import org.bukkit.entity.Player;
import java.util.*;

final class ChatCommands extends CommandBase {

    ChatCommands(AegisGuard plugin) { super(plugin); }

    void handlePlotChat(Player p, String[] args) {
        com.aegisguard.chat.PlotChatService chat = plugin.plotChat();
        if (chat == null) {
            sendKey(p, "plot_chat_disabled", "&cPlot Frequency is disabled on this server.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            com.aegisguard.chat.PlotChatService.Channel was = chat.activeChannel(p.getUniqueId());
            chat.turnOff(p);
            if (was == com.aegisguard.chat.PlotChatService.Channel.ALLIANCE) {
                sendKey(p, "alliance_chat_off", "&eAlliance radio off. Chat is public again.");
            } else if (was == com.aegisguard.chat.PlotChatService.Channel.GROUP) {
                sendKey(p, "group_chat_off", "&eGroup chat off. Chat is public again.");
            } else if (was == com.aegisguard.chat.PlotChatService.Channel.STAFF) {
                sendKey(p, "staff_chat_off", "&eStaff chat off. Chat is public again.");
            } else {
                sendKey(p, "plot_chat_off", "&eAegis Frequency off. Chat is public again.");
            }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("alliance")) {
            handleAllianceChat(p, args);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("group")) {
            handleGroupChat(p, args);
            return;
        }
        if (!p.hasPermission("aegis.chat")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (!chat.isEnabled()) {
            sendKey(p, "plot_chat_disabled", "&cPlot Frequency is disabled on this server.");
            return;
        }
        if (args.length >= 2) {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            switch (chat.send(p, message)) {
                case NEED_MEMBER -> sendKey(p, "plot_chat_need_member",
                        "&cStand in a plot you belong to before opening Frequency.");
                case NOT_MEMBER -> sendKey(p, "plot_chat_not_member",
                        "&cYou are not a member of that plot Frequency.");
                case EMPTY -> sendKey(p, "plot_chat_empty",
                        "&cSay something after /ag chat, or toggle with /ag chat.");
                default -> {
                }
            }
            return;
        }
        com.aegisguard.chat.PlotChatService.ToggleResult result = chat.toggle(p);
        com.aegisguard.data.Plot plot = chat.resolvePlot(p);
        String label = chat.plotLabel(plot);
        switch (result) {
            case ON -> sendKey(p, "plot_chat_on",
                    "&aAegis Frequency on for &f{PLOT}&a. Your chat stays on this plot.",
                    Map.of("PLOT", label));
            case SWITCHED -> sendKey(p, "plot_chat_switched",
                    "&aFrequency switched to &f{PLOT}&a.",
                    Map.of("PLOT", label));
            case OFF -> sendKey(p, "plot_chat_off", "&eAegis Frequency off. Chat is public again.");
            case NEED_MEMBER -> sendKey(p, "plot_chat_need_member",
                    "&cStand in a plot you belong to before opening Frequency.");
            default -> {
            }
        }
    }

    private void handleAllianceChat(Player p, String[] args) {
        com.aegisguard.chat.PlotChatService chat = plugin.plotChat();
        if (chat == null || !chat.isAllianceEnabled()) {
            sendKey(p, "alliance_chat_disabled", "&cAlliance radio is disabled on this server.");
            return;
        }
        if (!p.hasPermission("aegis.chat.alliance")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length >= 3 && (args[2].equalsIgnoreCase("name") || args[2].equalsIgnoreCase("rename"))) {
            if (args.length < 4) {
                sendKey(p, "alliance_chat_rename_usage",
                        "&eUsage: /ag chat alliance name <title>");
                return;
            }
            if (plugin.alliances() == null) {
                sendKey(p, "alliance_chat_not_member", "&cYou are not in an alliance.");
                return;
            }
            String title = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
            String error = plugin.alliances().setChatTitle(p.getUniqueId(), title);
            if (error == null) {
                com.aegisguard.alliance.Alliance alliance = plugin.alliances().getByPlayer(p.getUniqueId());
                String label = alliance == null ? title : alliance.getChatTitle();
                sendKey(p, "alliance_chat_renamed",
                        "&aAlliance radio is now called &f{ALLIANCE}&a.",
                        Map.of("ALLIANCE", label));
                return;
            }
            if ("alliance_chat_rename_denied".equals(error)) {
                sendKey(p, "alliance_chat_rename_denied",
                        "&cOnly the alliance leader can name the radio.");
            } else if ("alliance_chat_rename_usage".equals(error)) {
                sendKey(p, "alliance_chat_rename_usage",
                        "&eUsage: /ag chat alliance name <title>");
            } else {
                sendKey(p, "alliance_chat_not_member", "&cYou are not in an alliance.");
            }
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("off")) {
            chat.turnOffAlliance(p);
            sendKey(p, "alliance_chat_off", "&eAlliance radio off. Chat is public again.");
            return;
        }
        if (args.length >= 3) {
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
            switch (chat.sendAlliance(p, message)) {
                case NEED_MEMBER, NOT_MEMBER -> sendKey(p, "alliance_chat_not_member",
                        "&cYou are not in an alliance.");
                case EMPTY -> sendKey(p, "alliance_chat_empty",
                        "&cSay something after /ag chat alliance, or toggle with /ag chat alliance.");
                case DISABLED -> sendKey(p, "alliance_chat_disabled",
                        "&cAlliance radio is disabled on this server.");
                default -> {
                }
            }
            return;
        }
        com.aegisguard.alliance.Alliance alliance = plugin.alliances() == null
                ? null : plugin.alliances().getByPlayer(p.getUniqueId());
        String label = alliance == null ? "Alliance" : alliance.getChatTitle();
        switch (chat.toggleAlliance(p)) {
            case ON -> sendKey(p, "alliance_chat_on",
                    "&aAlliance radio on for &f{ALLIANCE}&a. Public chat stays with your alliance.",
                    Map.of("ALLIANCE", label));
            case OFF -> sendKey(p, "alliance_chat_off", "&eAlliance radio off. Chat is public again.");
            case NEED_MEMBER -> sendKey(p, "alliance_chat_need",
                    "&cJoin an alliance before opening alliance radio.");
            case DISABLED -> sendKey(p, "alliance_chat_disabled",
                    "&cAlliance radio is disabled on this server.");
            default -> {
            }
        }
    }

    private void handleGroupChat(Player p, String[] args) {
        com.aegisguard.chat.PlotChatService chat = plugin.plotChat();
        if (chat == null || !chat.isGroupEnabled()) {
            sendKey(p, "group_chat_disabled", "&cGroup chat is disabled on this server.");
            return;
        }
        if (!p.hasPermission("aegis.chat.group")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return;
        }
        if (args.length >= 3 && (args[2].equalsIgnoreCase("name") || args[2].equalsIgnoreCase("rename"))) {
            if (args.length < 4) {
                sendKey(p, "group_chat_rename_usage",
                        "&eUsage: /ag chat group name <title>");
                return;
            }
            if (plugin.groups() == null) {
                sendKey(p, "group_chat_not_member", "&cYou are not in a group.");
                return;
            }
            String title = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
            String error = plugin.groups().setChatTitle(p.getUniqueId(), title);
            if (error == null) {
                com.aegisguard.groups.PlotGroup group = plugin.groups().getGroupForPlayer(p.getUniqueId());
                String label = group == null ? title : chat.groupLabel(group);
                sendKey(p, "group_chat_renamed",
                        "&aGroup chat title is now &f{GROUP}&a.",
                        Map.of("GROUP", label));
                return;
            }
            if ("group_chat_rename_denied".equals(error)) {
                sendKey(p, "group_chat_rename_denied",
                        "&cOnly the group leader can rename group chat.");
            } else if ("group_chat_rename_usage".equals(error)) {
                sendKey(p, "group_chat_rename_usage",
                        "&eUsage: /ag chat group name <title>");
            } else {
                sendKey(p, "group_chat_not_member", "&cYou are not in a group.");
            }
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("off")) {
            chat.turnOffGroup(p);
            sendKey(p, "group_chat_off", "&eGroup chat off. Chat is public again.");
            return;
        }
        if (args.length >= 3) {
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
            switch (chat.sendGroup(p, message)) {
                case NEED_MEMBER, NOT_MEMBER -> sendKey(p, "group_chat_not_member",
                        "&cYou are not a member of that group.");
                case EMPTY -> sendKey(p, "group_chat_empty",
                        "&cSay something after /ag chat group, or toggle with /ag chat group.");
                case DISABLED -> sendKey(p, "group_chat_disabled",
                        "&cGroup chat is disabled on this server.");
                default -> {
                }
            }
            return;
        }
        com.aegisguard.groups.PlotGroup group = plugin.groups() == null
                ? null : plugin.groups().getGroupForPlayer(p.getUniqueId());
        String label = group == null ? "Group" : chat.groupLabel(group);
        switch (chat.toggleGroup(p)) {
            case ON -> sendKey(p, "group_chat_on",
                    "&aGroup chat on for &f{GROUP}&a. Your chat stays with your group.",
                    Map.of("GROUP", label));
            case OFF -> sendKey(p, "group_chat_off", "&eGroup chat off. Chat is public again.");
            case NEED_MEMBER -> sendKey(p, "group_chat_need",
                    "&cJoin a group before opening group chat.");
            case DISABLED -> sendKey(p, "group_chat_disabled",
                    "&cGroup chat is disabled on this server.");
            default -> {
            }
        }
    }

    void handleStaffChat(Player p, String[] args) {
        com.aegisguard.chat.PlotChatService chat = plugin.plotChat();
        if (chat == null || !chat.isStaffEnabled()) {
            sendKey(p, "staff_chat_disabled", "&cStaff chat is disabled on this server.");
            return;
        }
        if (!chat.canHearStaff(p)) {
            sendKey(p, "staff_chat_denied", "&cYou do not have permission to use staff chat.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            chat.turnOffStaff(p);
            sendKey(p, "staff_chat_off", "&eStaff chat off. Chat is public again.");
            return;
        }
        if (args.length >= 2) {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            switch (chat.sendStaff(p, message)) {
                case DENIED -> sendKey(p, "staff_chat_denied",
                        "&cYou do not have permission to use staff chat.");
                case EMPTY -> sendKey(p, "staff_chat_empty",
                        "&cSay something after /ag staff, or toggle with /ag staff.");
                case DISABLED -> sendKey(p, "staff_chat_disabled",
                        "&cStaff chat is disabled on this server.");
                default -> {
                }
            }
            return;
        }
        switch (chat.toggleStaff(p)) {
            case ON -> sendKey(p, "staff_chat_on",
                    "&aStaff chat on. Public chat stays with online staff.");
            case OFF -> sendKey(p, "staff_chat_off", "&eStaff chat off. Chat is public again.");
            case DENIED -> sendKey(p, "staff_chat_denied",
                    "&cYou do not have permission to use staff chat.");
            case DISABLED -> sendKey(p, "staff_chat_disabled",
                    "&cStaff chat is disabled on this server.");
            default -> {
            }
        }
    }

    void handleNotify(Player p, String[] args) {
        // Check if NotificationManager is available
        if (plugin.getNotificationManager() == null) {
            sendKey(p, "notify_unavailable", "&cNotification system unavailable.");
            plugin.effects().playError(p);
            return;
        }

        // No args: legacy behavior (toggle greetings)
        if (args.length == 1) {
            handleNotifyGreetings(p, args);
            return;
        }

        String subcommand = args[1].toLowerCase();

        switch (subcommand) {
            case "greetings", "greeting", "greet" -> handleNotifyGreetings(p, args);
            case "admin", "admins", "adminupdates" -> handleNotifyAdmin(p, args);
            case "all" -> handleNotifyAll(p, args);
            case "mode" -> handleNotifyMode(p, args);
            case "status", "settings", "info" -> handleNotifyStatus(p);
            default -> {
                sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify &8- &7Toggle greeting messages\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim enter/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings"
                );
            }
        }
    }

    private void handleNotifyGreetings(Player p, String[] args) {
        if (!canManageGreetingNotifications(p, true)) {
            plugin.effects().playError(p);
            return;
        }

        Boolean requested = parseBooleanArg(args.length >= 3 ? args[2] : null);
        if (args.length >= 3 && requested == null) {
            sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim entry/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings");
            plugin.effects().playError(p);
            return;
        }

        boolean newState;
        if (requested == null) {
            newState = plugin.getNotificationManager().toggleGreetings(p.getUniqueId());
        } else {
            var settings = plugin.getNotificationManager().getSettings(p.getUniqueId());
            settings.setGreetingsEnabled(requested);
            plugin.getNotificationManager().updateSettings(settings);
            newState = requested;
        }

        if (newState) {
            sendKey(p, "notify_greetings_enabled", "&aGreetings enabled. You will see claim enter/exit messages.");
            plugin.effects().playConfirm(p);
        } else {
            sendKey(p, "notify_greetings_disabled", "&cGreetings disabled. You will not see claim enter/exit messages.");
            plugin.effects().playError(p);
        }
    }

    private void handleNotifyAdmin(Player p, String[] args) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(p);
            return;
        }

        Boolean requested = parseBooleanArg(args.length >= 3 ? args[2] : null);
        if (args.length >= 3 && requested == null) {
            sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim entry/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings");
            plugin.effects().playError(p);
            return;
        }

        boolean newState;
        if (requested == null) {
            newState = plugin.getNotificationManager().toggleAdminUpdates(p.getUniqueId());
        } else {
            var settings = plugin.getNotificationManager().getSettings(p.getUniqueId());
            settings.setAdminUpdatesEnabled(requested);
            plugin.getNotificationManager().updateSettings(settings);
            newState = requested;
        }

        if (newState) {
            sendKey(p, "notify_admin_enabled", "&aAdmin notifications enabled.");
            plugin.effects().playConfirm(p);
        } else {
            sendKey(p, "notify_admin_disabled", "&cAdmin notifications disabled.");
            plugin.effects().playError(p);
        }
    }

    private void handleNotifyAll(Player p, String[] args) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(p);
            return;
        }

        if (!canManageGreetingNotifications(p, true)) {
            plugin.effects().playError(p);
            return;
        }

        Boolean requested = parseBooleanArg(args.length >= 3 ? args[2] : null);
        if (args.length >= 3 && requested == null) {
            sendKey(p, "notify_usage",
                    "&eNotification Commands:\n" +
                    "&7/aegis notify greetings [on|off] &8- &7Toggle claim entry/exit messages\n" +
                    "&7/aegis notify admin [on|off] &8- &7Toggle admin notifications\n" +
                    "&7/aegis notify all [on|off] &8- &7Toggle both notification groups\n" +
                    "&7/aegis notify mode <chat|actionbar|title> &8- &7Set notification style\n" +
                    "&7/aegis notify status &8- &7View current settings");
            plugin.effects().playError(p);
            return;
        }

        var settings = plugin.getNotificationManager().getSettings(p.getUniqueId());
        boolean newState = requested != null ? requested : !(settings.isGreetingsEnabled() && settings.isAdminUpdatesEnabled());
        settings.setGreetingsEnabled(newState);
        settings.setAdminUpdatesEnabled(newState);
        plugin.getNotificationManager().updateSettings(settings);

        if (newState) {
            sendKey(p, "notify_greetings_enabled", "&aGreetings enabled. You will see claim enter/exit messages.");
            sendKey(p, "notify_admin_enabled", "&aAdmin notifications enabled.");
            plugin.effects().playConfirm(p);
        } else {
            sendKey(p, "notify_greetings_disabled", "&cGreetings disabled. You will not see claim enter/exit messages.");
            sendKey(p, "notify_admin_disabled", "&cAdmin notifications disabled.");
            plugin.effects().playError(p);
        }
    }

    private void handleNotifyMode(Player p, String[] args) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            plugin.effects().playError(p);
            return;
        }

        if (args.length < 3) {
            sendKey(p, "notify_mode_usage",
                "&eUsage: &7/aegis notify mode <chat|actionbar|title>\n" +
                "&7Available modes:\n" +
                "&7- &bchat &8- &7Standard chat messages\n" +
                "&7- &bactionbar &8- &7Action bar notifications (above hotbar)\n" +
                "&7- &btitle &8- &7Title/subtitle notifications (center screen)"
            );
            return;
        }

        String modeStr = args[2].trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("ACTIONBAR".equals(modeStr)) modeStr = "ACTION_BAR";

        com.aegisguard.notify.NotificationMode mode = null;
        for (com.aegisguard.notify.NotificationMode candidate : com.aegisguard.notify.NotificationMode.values()) {
            if (candidate.name().equals(modeStr)) {
                mode = candidate;
                break;
            }
        }
        if (mode == null) {
            sendKey(p, "notify_mode_invalid",
                "&cInvalid mode: &7" + args[2] + "\n" +
                "&eValid modes: &7chat, actionbar, title"
            );
            plugin.effects().playError(p);
            return;
        }

        plugin.getNotificationManager().setMode(p.getUniqueId(), mode);
        sendKey(p, "notify_mode_set", "&aNotification mode set to: &b" + mode.getDisplayName());
        plugin.effects().playConfirm(p);
    }

    private void handleNotifyStatus(Player p) {
        com.aegisguard.notify.PlayerNotificationSettings settings =
            plugin.getNotificationManager().getSettings(p.getUniqueId());

        boolean effectiveGreetings = areGreetingNotificationsEffectivelyEnabled(p, settings);
        String greetingStatus = effectiveGreetings ? "&aEnabled" : "&cDisabled";
        String adminStatus = settings.isAdminUpdatesEnabled() ? "&aEnabled" : "&cDisabled";
        String mode = "&b" + resolveEffectiveMode(settings).getDisplayName();

        sendKey(p, "notify_status",
            "&e&l━━━━━━━━━ &6Notification Settings &e&l━━━━━━━━━\n" +
            "&7Greetings: {GREETINGS}\n" +
            "&7Admin Updates: {ADMIN}\n" +
            "&7Mode: {MODE}\n" +
            "&e&l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            Map.of(
                    "GREETINGS", greetingStatus,
                    "ADMIN", adminStatus,
                    "MODE", mode
            )
        );
    }

    private boolean canManageGreetingNotifications(Player p, boolean togglingState) {
        if (!p.hasPermission("aegis.notify")) {
            sendKey(p, "no_perm", "&cError: You do not have permission for this.");
            return false;
        }

        String base = "titles.claim_enter_exit";
        boolean enabled = plugin.getConfig().getBoolean(base + ".enabled", true);
        String mode = plugin.getConfig().getString(base + ".mode", "PER_PLAYER").toUpperCase(Locale.ROOT);
        boolean allowToggle = plugin.getConfig().getBoolean(base + ".allow_player_toggle", true);
        String requiredPermission = plugin.getConfig().getString(base + ".required_permission", "");
        String bypassPermission = plugin.getConfig().getString(base + ".bypass_permission", "aegis.notify.bypass");

        if (!enabled) {
            if (bypassPermission != null && !bypassPermission.isBlank() && p.hasPermission(bypassPermission)) {
                return true;
            }
            sendKey(p, "claim_enter_exit_notify_server_disabled",
                    "&cClaim enter/exit notifications are disabled server-wide.");
            return false;
        }

        if (requiredPermission != null && !requiredPermission.isBlank() && !p.hasPermission(requiredPermission)) {
            sendKey(p, "claim_enter_exit_notify_server_disabled",
                    "&cClaim enter/exit notifications are disabled server-wide.");
            return false;
        }

        if (togglingState && !"PER_PLAYER".equals(mode)) {
            sendKey(p, "claim_enter_exit_notify_toggle_not_allowed",
                    "&cThis server does not allow players to toggle claim notifications.");
            return false;
        }

        if (togglingState && !allowToggle) {
            sendKey(p, "claim_enter_exit_notify_toggle_not_allowed",
                    "&cThis server does not allow players to toggle claim notifications.");
            return false;
        }

        return true;
    }

    private boolean areGreetingNotificationsEffectivelyEnabled(Player p,
                                                               com.aegisguard.notify.PlayerNotificationSettings settings) {
        String base = "titles.claim_enter_exit";
        boolean enabled = plugin.getConfig().getBoolean(base + ".enabled", true);
        String mode = plugin.getConfig().getString(base + ".mode", "PER_PLAYER").toUpperCase(Locale.ROOT);
        String requiredPermission = plugin.getConfig().getString(base + ".required_permission", "");
        String bypassPermission = plugin.getConfig().getString(base + ".bypass_permission", "aegis.notify.bypass");

        if (!enabled) {
            return bypassPermission != null && !bypassPermission.isBlank() && p.hasPermission(bypassPermission);
        }

        if (requiredPermission != null && !requiredPermission.isBlank() && !p.hasPermission(requiredPermission)) {
            return false;
        }

        if ("FORCE_ON".equals(mode)) return true;
        if ("FORCE_OFF".equals(mode)) {
            return bypassPermission != null && !bypassPermission.isBlank() && p.hasPermission(bypassPermission);
        }

        return settings == null || settings.isGreetingsEnabled();
    }


    private com.aegisguard.notify.NotificationMode resolveEffectiveMode(
            com.aegisguard.notify.PlayerNotificationSettings settings
    ) {
        String configured = plugin.getConfig().getString("titles.claim_enter_exit.notification_location",
                plugin.getConfig().getString("titles.notification_location", "ACTION_BAR"));

        if (settings == null || settings.getMode() == null) {
            return com.aegisguard.notify.NotificationMode.fromString(configured);
        }

        return settings.getMode();
    }
}
