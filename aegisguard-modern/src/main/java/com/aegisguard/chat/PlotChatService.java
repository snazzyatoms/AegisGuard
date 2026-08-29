package com.aegisguard.chat;

import com.aegisguard.AegisGuard;
import com.aegisguard.alliance.Alliance;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import com.aegisguard.groups.PlotGroup;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same-server intercept chat: one channel at a time (plot Frequency, alliance, group, or staff).
 * Cross-server sync is still a 2.0 network feature and is not implemented here.
 */
public final class PlotChatService {

    public static final String PERM_PLOT = "aegis.chat";
    public static final String PERM_ALLIANCE = "aegis.chat.alliance";
    public static final String PERM_GROUP = "aegis.chat.group";
    public static final String PERM_STAFF = "aegis.admin.staffchat";

    private static final int TITLE_MAX = 32;

    private final AegisGuard plugin;
    private final Map<UUID, UUID> tunedPlotByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Channel> channelByPlayer = new ConcurrentHashMap<>();

    public PlotChatService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        try {
            return plugin.modules().on(Modules.Id.PLOT_CHAT);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public boolean isAllianceChatEnabled() {
        return plugin.getConfig().getBoolean("alliance_chat.enabled", true);
    }

    public boolean isAllianceEnabled() {
        return isAllianceChatEnabled();
    }

    public boolean isGroupChatEnabled() {
        return plugin.getConfig().getBoolean("group_chat.enabled", true);
    }

    public boolean isGroupEnabled() {
        return isGroupChatEnabled();
    }

    public boolean isStaffChatEnabled() {
        return plugin.getConfig().getBoolean("staff_chat.enabled", true);
    }

    public boolean isStaffEnabled() {
        return isStaffChatEnabled();
    }

    public Channel activeChannel(UUID playerId) {
        if (playerId == null) return Channel.NONE;
        Channel channel = channelByPlayer.get(playerId);
        if (channel != null) return channel;
        return tunedPlotByPlayer.containsKey(playerId) ? Channel.PLOT : Channel.NONE;
    }

    public boolean isTuned(UUID playerId) {
        return activeChannel(playerId) == Channel.PLOT;
    }

    public UUID tunedPlotId(UUID playerId) {
        return playerId == null ? null : tunedPlotByPlayer.get(playerId);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        tunedPlotByPlayer.remove(playerId);
        channelByPlayer.remove(playerId);
    }

    public void clearAll() {
        tunedPlotByPlayer.clear();
        channelByPlayer.clear();
    }

    public ToggleResult toggle(Player player) {
        if (player == null) return ToggleResult.NEED_MEMBER;
        UUID current = tunedPlotByPlayer.get(player.getUniqueId());
        Plot here = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        if (here == null || !isFrequencyMember(here, player.getUniqueId())) {
            if (current != null || activeChannel(player.getUniqueId()) == Channel.PLOT) {
                setChannel(player.getUniqueId(), Channel.NONE);
                return ToggleResult.OFF;
            }
            return ToggleResult.NEED_MEMBER;
        }
        if (here.getPlotId().equals(current) && activeChannel(player.getUniqueId()) == Channel.PLOT) {
            setChannel(player.getUniqueId(), Channel.NONE);
            return ToggleResult.OFF;
        }
        boolean switched = current != null && activeChannel(player.getUniqueId()) == Channel.PLOT;
        setChannel(player.getUniqueId(), Channel.PLOT);
        tunedPlotByPlayer.put(player.getUniqueId(), here.getPlotId());
        return switched ? ToggleResult.SWITCHED : ToggleResult.ON;
    }

    public void turnOff(Player player) {
        if (player != null) clearPlayer(player.getUniqueId());
    }

    public void turnOffAlliance(Player player) {
        turnOff(player, Channel.ALLIANCE);
    }

    public void turnOffGroup(Player player) {
        turnOff(player, Channel.GROUP);
    }

    public void turnOffStaff(Player player) {
        turnOff(player, Channel.STAFF);
    }

    public void turnOff(Player player, Channel channel) {
        if (player == null || channel == null || channel == Channel.NONE) return;
        if (channel == Channel.PLOT) {
            turnOff(player);
            return;
        }
        if (activeChannel(player.getUniqueId()) == channel) {
            channelByPlayer.remove(player.getUniqueId());
        }
    }

    public ToggleResult toggleAlliance(Player player) {
        if (!isAllianceChatEnabled()) return ToggleResult.DISABLED;
        if (player == null) return ToggleResult.NEED_MEMBER;
        Alliance alliance = resolveAlliance(player);
        if (alliance == null) {
            if (activeChannel(player.getUniqueId()) == Channel.ALLIANCE) {
                setChannel(player.getUniqueId(), Channel.NONE);
                return ToggleResult.OFF;
            }
            return ToggleResult.NEED_MEMBER;
        }
        if (activeChannel(player.getUniqueId()) == Channel.ALLIANCE) {
            setChannel(player.getUniqueId(), Channel.NONE);
            return ToggleResult.OFF;
        }
        setChannel(player.getUniqueId(), Channel.ALLIANCE);
        return ToggleResult.ON;
    }

    public ToggleResult toggleGroup(Player player) {
        if (!isGroupChatEnabled()) return ToggleResult.DISABLED;
        if (player == null) return ToggleResult.NEED_MEMBER;
        PlotGroup group = resolveGroup(player);
        if (group == null) {
            if (activeChannel(player.getUniqueId()) == Channel.GROUP) {
                setChannel(player.getUniqueId(), Channel.NONE);
                return ToggleResult.OFF;
            }
            return ToggleResult.NEED_MEMBER;
        }
        if (activeChannel(player.getUniqueId()) == Channel.GROUP) {
            setChannel(player.getUniqueId(), Channel.NONE);
            return ToggleResult.OFF;
        }
        setChannel(player.getUniqueId(), Channel.GROUP);
        return ToggleResult.ON;
    }

    public ToggleResult toggleStaff(Player player) {
        if (!isStaffChatEnabled()) return ToggleResult.DISABLED;
        if (player == null || !canStaffChat(player)) return ToggleResult.DENIED;
        if (activeChannel(player.getUniqueId()) == Channel.STAFF) {
            setChannel(player.getUniqueId(), Channel.NONE);
            return ToggleResult.OFF;
        }
        setChannel(player.getUniqueId(), Channel.STAFF);
        return ToggleResult.ON;
    }

    public SendResult send(Player speaker, String rawMessage) {
        return sendPlot(speaker, rawMessage);
    }

    public SendResult sendActive(Player speaker, String rawMessage) {
        if (speaker == null) return SendResult.NEED_MEMBER;
        return switch (activeChannel(speaker.getUniqueId())) {
            case ALLIANCE -> sendAlliance(speaker, rawMessage);
            case GROUP -> sendGroup(speaker, rawMessage);
            case STAFF -> sendStaff(speaker, rawMessage);
            case PLOT, NONE -> sendPlot(speaker, rawMessage);
        };
    }

    public SendResult sendPlot(Player speaker, String rawMessage) {
        if (speaker == null) return SendResult.NEED_MEMBER;
        String message = clip(rawMessage);
        if (message.isEmpty()) return SendResult.EMPTY;

        Plot plot = resolvePlot(speaker);
        if (plot == null) return SendResult.NEED_MEMBER;
        if (!isFrequencyMember(plot, speaker.getUniqueId())) {
            tunedPlotByPlayer.remove(speaker.getUniqueId());
            if (activeChannel(speaker.getUniqueId()) == Channel.PLOT) {
                channelByPlayer.remove(speaker.getUniqueId());
            }
            return SendResult.NOT_MEMBER;
        }

        String line = format(speaker, plot, message);
        return broadcast(speaker, line, frequencyMemberIds(plot),
                "plot_chat_no_listeners", "&eNobody else is online on this Frequency.");
    }

    public SendResult sendAlliance(Player speaker, String rawMessage) {
        if (!isAllianceChatEnabled()) return SendResult.DISABLED;
        if (speaker == null) return SendResult.NEED_MEMBER;
        String message = clip(rawMessage);
        if (message.isEmpty()) return SendResult.EMPTY;
        Alliance alliance = resolveAlliance(speaker);
        if (alliance == null) {
            if (activeChannel(speaker.getUniqueId()) == Channel.ALLIANCE) {
                setChannel(speaker.getUniqueId(), Channel.NONE);
            }
            return SendResult.NEED_MEMBER;
        }
        if (!alliance.isMember(speaker.getUniqueId())) {
            setChannel(speaker.getUniqueId(), Channel.NONE);
            return SendResult.NOT_MEMBER;
        }
        String line = formatAlliance(speaker, alliance, message);
        return broadcast(speaker, line, alliance.getMemberIds(),
                "alliance_chat_no_listeners", "&eNobody else in your alliance is online.");
    }

    public SendResult sendGroup(Player speaker, String rawMessage) {
        if (!isGroupChatEnabled()) return SendResult.DISABLED;
        if (speaker == null) return SendResult.NEED_MEMBER;
        String message = clip(rawMessage);
        if (message.isEmpty()) return SendResult.EMPTY;
        PlotGroup group = resolveGroup(speaker);
        if (group == null) {
            if (activeChannel(speaker.getUniqueId()) == Channel.GROUP) {
                setChannel(speaker.getUniqueId(), Channel.NONE);
            }
            return SendResult.NEED_MEMBER;
        }
        if (!group.isMember(speaker.getUniqueId())) {
            setChannel(speaker.getUniqueId(), Channel.NONE);
            return SendResult.NOT_MEMBER;
        }
        String line = formatGroup(speaker, group, message);
        return broadcast(speaker, line, group.getMemberIds(),
                "group_chat_no_listeners", "&eNobody else in your group is online.");
    }

    public SendResult sendStaff(Player speaker, String rawMessage) {
        if (!isStaffChatEnabled()) return SendResult.DISABLED;
        if (speaker == null) return SendResult.DENIED;
        if (!canStaffChat(speaker)) {
            if (activeChannel(speaker.getUniqueId()) == Channel.STAFF) {
                setChannel(speaker.getUniqueId(), Channel.NONE);
            }
            return SendResult.DENIED;
        }
        String message = clip(rawMessage);
        if (message.isEmpty()) return SendResult.EMPTY;
        String line = formatStaff(speaker, message);
        Set<UUID> staff = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (canStaffChat(online)) staff.add(online.getUniqueId());
        }
        return broadcast(speaker, line, staff,
                "staff_chat_no_listeners", "&eNo other staff are online.");
    }

    public RenameResult renameAlliance(Player player, String rawTitle) {
        Alliance alliance = resolveAlliance(player);
        if (alliance == null || plugin.alliances() == null) return RenameResult.NEED;
        if (!alliance.isLeader(player.getUniqueId())) return RenameResult.DENIED;
        String title = sanitizeTitle(rawTitle);
        if (title.isEmpty()) return RenameResult.EMPTY;
        plugin.alliances().setChatTitle(alliance, title);
        return RenameResult.OK;
    }

    public RenameResult renameGroup(Player player, String rawTitle) {
        PlotGroup group = resolveGroup(player);
        if (group == null || plugin.groups() == null) return RenameResult.NEED;
        if (!player.getUniqueId().equals(group.getLeader())) return RenameResult.DENIED;
        String title = sanitizeTitle(rawTitle);
        if (title.isEmpty()) return RenameResult.EMPTY;
        plugin.groups().setChatTitle(group, title);
        return RenameResult.OK;
    }

    public boolean interceptPublicChat(Player speaker) {
        if (speaker == null) return false;
        Channel channel = activeChannel(speaker.getUniqueId());
        return switch (channel) {
            case PLOT -> isEnabled();
            case ALLIANCE -> {
                if (!isAllianceChatEnabled() || resolveAlliance(speaker) == null) {
                    setChannel(speaker.getUniqueId(), Channel.NONE);
                    yield false;
                }
                yield true;
            }
            case GROUP -> {
                if (!isGroupChatEnabled() || resolveGroup(speaker) == null) {
                    setChannel(speaker.getUniqueId(), Channel.NONE);
                    yield false;
                }
                yield true;
            }
            case STAFF -> {
                if (!isStaffChatEnabled() || !canStaffChat(speaker)) {
                    setChannel(speaker.getUniqueId(), Channel.NONE);
                    yield false;
                }
                yield true;
            }
            case NONE -> false;
        };
    }

    public boolean canStaffChat(Player player) {
        if (player == null) return false;
        try {
            if (player.hasPermission(PERM_STAFF)) return true;
        } catch (Throwable ignored) {}
        return plugin.isAdmin(player);
    }

    public boolean canHearStaff(Player player) {
        return canStaffChat(player);
    }

    public Plot resolvePlot(Player speaker) {
        if (speaker == null || plugin.store() == null) return null;
        UUID bound = tunedPlotByPlayer.get(speaker.getUniqueId());
        if (bound != null) {
            for (Plot plot : plugin.store().getAllPlots()) {
                if (plot != null && bound.equals(plot.getPlotId())) return plot;
            }
            tunedPlotByPlayer.remove(speaker.getUniqueId());
        }
        Plot here = plugin.store().getPlotAt(speaker.getLocation());
        if (here != null && isFrequencyMember(here, speaker.getUniqueId())) return here;
        return null;
    }

    public Alliance resolveAlliance(Player speaker) {
        if (speaker == null || plugin.alliances() == null) return null;
        return plugin.alliances().getByPlayer(speaker.getUniqueId());
    }

    public PlotGroup resolveGroup(Player speaker) {
        if (speaker == null || plugin.groups() == null) return null;
        return plugin.groups().getGroupForPlayer(speaker.getUniqueId());
    }

    public String plotLabel(Plot plot) {
        if (plot == null) return "Plot";
        String name = plot.getPlotName();
        if (name == null || name.isBlank()) name = plot.getOwnerName();
        if (name == null || name.isBlank()) return "Plot";
        return strip(name);
    }

    public String allianceLabel(Alliance alliance) {
        if (alliance == null) return "Alliance";
        String title = alliance.getChatTitle();
        if (title == null || title.isBlank()) title = alliance.getName();
        if (title == null || title.isBlank()) return "Alliance";
        return strip(title);
    }

    public String groupLabel(PlotGroup group) {
        if (group == null) return "Group";
        String title = group.getChatTitle();
        if (title == null || title.isBlank()) title = group.getName();
        if (title == null || title.isBlank()) return "Group";
        return strip(title);
    }

    public String format(Player speaker, Plot plot, String message) {
        String template = translate(speaker, "plot_chat_format",
                "&8[&b{PLOT}&8] &f{PLAYER}&7: &f{MESSAGE}");
        String filled = template
                .replace("{PLOT}", plotLabel(plot))
                .replace("{PLAYER}", speaker.getName())
                .replace("{MESSAGE}", message);
        return ChatColor.translateAlternateColorCodes('&', filled);
    }

    public String formatAlliance(Player speaker, Alliance alliance, String message) {
        String template = translate(speaker, "alliance_chat_format",
                "&8[&d{ALLIANCE}&8] &f{PLAYER}&7: &f{MESSAGE}");
        String filled = template
                .replace("{ALLIANCE}", allianceLabel(alliance))
                .replace("{PLAYER}", speaker.getName())
                .replace("{MESSAGE}", message);
        return ChatColor.translateAlternateColorCodes('&', filled);
    }

    public String formatGroup(Player speaker, PlotGroup group, String message) {
        String template = translate(speaker, "group_chat_format",
                "&8[&a{GROUP}&8] &f{PLAYER}&7: &f{MESSAGE}");
        String filled = template
                .replace("{GROUP}", groupLabel(group))
                .replace("{PLAYER}", speaker.getName())
                .replace("{MESSAGE}", message);
        return ChatColor.translateAlternateColorCodes('&', filled);
    }

    public String formatStaff(Player speaker, String message) {
        String template = translate(speaker, "staff_chat_format",
                "&8[&cStaff&8] &f{PLAYER}&7: &f{MESSAGE}");
        String filled = template
                .replace("{PLAYER}", speaker.getName())
                .replace("{MESSAGE}", message);
        return ChatColor.translateAlternateColorCodes('&', filled);
    }

    public static boolean isFrequencyMember(Plot plot, UUID playerId) {
        if (plot == null || playerId == null) return false;
        if (plot.isBanned(playerId)) return false;
        if (plot.isOwner(playerId)) return true;
        if (plot.isRentedBy(playerId)) return true;
        String role = plot.getRole(playerId);
        if (role == null) return false;
        String normalized = role.toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !"visitor".equals(normalized)
                && !"default".equals(normalized)
                && !"none".equals(normalized);
    }

    public static Set<UUID> frequencyMemberIds(Plot plot) {
        Set<UUID> ids = new HashSet<>();
        if (plot == null) return ids;
        if (plot.getOwner() != null) ids.add(plot.getOwner());
        if (plot.getPlayerRoles() != null) {
            for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
                if (isFrequencyMember(plot, entry.getKey())) ids.add(entry.getKey());
            }
        }
        UUID renter = plot.getCurrentRenter();
        if (plot.isRentedBy(renter)) ids.add(renter);
        return ids;
    }

    private SendResult broadcast(Player speaker, String line, Set<UUID> members, String emptyKey, String emptyFallback) {
        int others = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || members == null || !members.contains(online.getUniqueId())) continue;
            Player target = online;
            plugin.runMain(target, () -> target.sendMessage(line));
            if (!target.getUniqueId().equals(speaker.getUniqueId())) others++;
        }
        if (others == 0) {
            plugin.runMain(speaker, () -> speaker.sendMessage(color(translate(speaker, emptyKey, emptyFallback))));
        }
        return others == 0 ? SendResult.NO_LISTENERS : SendResult.SENT;
    }

    private void setChannel(UUID playerId, Channel channel) {
        if (playerId == null) return;
        if (channel == null || channel == Channel.NONE) {
            channelByPlayer.remove(playerId);
            tunedPlotByPlayer.remove(playerId);
            return;
        }
        channelByPlayer.put(playerId, channel);
        if (channel != Channel.PLOT) tunedPlotByPlayer.remove(playerId);
    }

    private String clip(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) return "";
        int max = Math.max(16, plugin.getConfig().getInt("plot_chat.max_message_length", 256));
        return message.length() > max ? message.substring(0, max) : message;
    }

    public static String sanitizeTitle(String rawTitle) {
        if (rawTitle == null) return "";
        String title = strip(rawTitle).trim();
        if (title.length() > TITLE_MAX) title = title.substring(0, TITLE_MAX);
        return title;
    }

    private String translate(Player player, String key, String fallback) {
        try {
            if (plugin.codex() != null) {
                String value = plugin.codex().tr(player, key, Map.of());
                if (value != null && !value.isBlank() && !value.equalsIgnoreCase(key)) return value;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static String strip(String text) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text == null ? "" : text));
    }

    public enum Channel { NONE, PLOT, ALLIANCE, GROUP, STAFF }

    public enum ToggleResult { ON, OFF, SWITCHED, NEED_MEMBER, DENIED, DISABLED }

    public enum SendResult { SENT, NO_LISTENERS, NEED_MEMBER, NOT_MEMBER, EMPTY, DENIED, DISABLED }

    public enum RenameResult { OK, DENIED, NEED, EMPTY }
}
