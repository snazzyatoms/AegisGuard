package com.aegisguard.groups;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Party sidebar: a per-player scoreboard that appears while you're grouped —
 * party name on top, then each member with a live health read, the leader
 * crowned, offline members greyed. Works for general parties and arena-run
 * parties alike (via {@link PartyService#viewFor}).
 *
 * <p>All scoreboard mutations hop to the owning entity thread on Folia; the
 * refresh loop only collects UUIDs globally and dispatches per player. Boards
 * a player already had are remembered and restored when the party ends, so we
 * never clobber another plugin's sidebar.
 */
public final class PartySidebarService {

    private static final String OBJECTIVE = "aegis_party";

    private final AegisGuard plugin;
    private final PartyService parties;
    private final Map<UUID, Scoreboard> ourBoards = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> previousBoards = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    /** Compact mode: names only — no health/distance suffix. */
    private final Set<UUID> compact = ConcurrentHashMap.newKeySet();
    private volatile Object ticker;

    public PartySidebarService(AegisGuard plugin, PartyService parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    public boolean sidebarEnabled() {
        return plugin.getConfig().getBoolean("party.sidebar.enabled", true);
    }

    public boolean toggle(Player player) {
        if (player == null) return true;
        boolean nowHidden = !hidden.remove(player.getUniqueId());
        if (nowHidden) {
            hidden.add(player.getUniqueId());
            clearBoard(player);
        }
        return !nowHidden;
    }

    public boolean isHidden(UUID playerId) { return hidden.contains(playerId); }

    /** true = names-only lines; false = full detail (health, distance). */
    public boolean setCompact(Player player, boolean on) {
        if (player == null) return false;
        if (on) compact.add(player.getUniqueId()); else compact.remove(player.getUniqueId());
        return on;
    }

    public boolean isCompact(UUID playerId) { return compact.contains(playerId); }

    private boolean showHealth() {
        return plugin.getConfig().getBoolean("party.sidebar.show_health", true);
    }

    private boolean showDistance() {
        return plugin.getConfig().getBoolean("party.sidebar.show_distance", true);
    }

    private boolean showOffline() {
        return plugin.getConfig().getBoolean("party.sidebar.show_offline", true);
    }

    /** Kick a refresh pass (call after membership changes). */
    public void requestRefresh() { refresh(); }

    /** Start the health/name refresh loop. Safe to call once at enable. */
    public void start() {
        if (!sidebarEnabled() || ticker != null) return;
        long period = Math.max(10L, plugin.getConfig().getLong("party.sidebar.refresh_ticks", 20L));
        ticker = plugin.scheduler().runGlobalRepeating(this::refresh, period, period);
    }

    /** Stop the loop and clear every board we set (plugin disable). */
    public void shutdown() {
        if (ticker != null) {
            try { plugin.scheduler().cancel(ticker); } catch (Throwable ignored) { }
            ticker = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) clearBoard(p);
        ourBoards.clear();
        previousBoards.clear();
        hidden.clear();
        compact.clear();
    }

    // ------------------------------------------------------------------

    private void refresh() {
        if (!sidebarEnabled()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) continue;
            PartyService.PartyView view = hidden.contains(viewer.getUniqueId())
                    ? null
                    : parties.viewFor(viewer.getUniqueId());
            if (view == null) {
                if (ourBoards.containsKey(viewer.getUniqueId())) clearBoard(viewer);
                continue;
            }
            List<String> lines = renderLines(viewer, view);
            Player target = viewer;
            plugin.scheduler().runEntity(target, () -> applyBoard(target, view.title(), lines), () -> { });
        }
    }

    /** Build the sidebar rows: "★ Name ❤20 35m" style, leader first. */
    private List<String> renderLines(Player viewer, PartyService.PartyView view) {
        int max = Math.max(4, plugin.getConfig().getInt("party.sidebar.max_lines", 12));
        List<UUID> ordered = new ArrayList<>(view.memberIds());
        ordered.sort((a, b) -> {
            if (a.equals(view.leaderId())) return -1;
            if (b.equals(view.leaderId())) return 1;
            return 0;
        });
        List<String> lines = new ArrayList<>();
        lines.add("§8────────────");
        int shown = 0;
        int hiddenOffline = 0;
        for (UUID id : ordered) {
            if (shown >= max - 1) break;
            Player member = Bukkit.getPlayer(id);
            if (!showOffline() && !id.equals(viewer.getUniqueId())
                    && (member == null || !member.isOnline())) {
                hiddenOffline++;
                continue;
            }
            lines.add(memberLine(viewer, member, id, view.leaderId()));
            shown++;
        }
        int overflow = ordered.size() - shown - hiddenOffline;
        if (overflow > 0) lines.add("§7+" + overflow + " more…");
        if (hiddenOffline > 0) lines.add("§8+" + hiddenOffline + " offline");
        return lines;
    }

    private String memberLine(Player viewer, Player p, UUID id, UUID leaderId) {
        boolean leader = id.equals(leaderId);
        boolean minimal = compact.contains(viewer.getUniqueId());
        if (p == null || !p.isOnline()) {
            // Remote member on another backend: name @server, not "offline".
            PartyService.RemoteMember remote = plugin.parties() == null
                    ? null : plugin.parties().remoteStatus(id);
            if (remote != null && remote.online()) {
                return "§b" + (leader ? "★ " : "● ") + remote.name() + " §8@" + remote.server();
            }
            String name = remote != null ? remote.name()
                    : p != null ? p.getName() : Bukkit.getOfflinePlayer(id).getName();
            return "§7" + (leader ? "★ " : "○ ") + (name == null ? "?" : name) + " §8offline";
        }
        String nameColor = leader ? "§6" : "§f";
        String suffix = "";
        if (!minimal) {
            if (showHealth()) {
                int hp = (int) Math.ceil(Math.max(0.0D, Math.min(p.getHealth(), 20.0D)));
                String hpColor = hp >= 15 ? "§a" : hp >= 8 ? "§e" : "§c";
                suffix += " " + hpColor + "❤" + hp;
            }
            if (showDistance() && !id.equals(viewer.getUniqueId()) && !parties.isHidden(id)) {
                try {
                    org.bukkit.Location target = parties.positionOf(id);
                    if (target != null && target.getWorld() != null
                            && target.getWorld().equals(viewer.getWorld())) {
                        int dist = (int) Math.round(viewer.getLocation().distance(target));
                        suffix += " §8" + dist + "m";
                    }
                } catch (Throwable ignored) { }
            }
        }
        return nameColor + (leader ? "★ " : "● ") + p.getName() + suffix;
    }

    /** Applies (or updates) the sidebar on the viewer's entity thread. */
    private void applyBoard(Player viewer, String title, List<String> lines) {
        if (viewer == null || !viewer.isOnline() || hidden.contains(viewer.getUniqueId())) return;
        Scoreboard board = ourBoards.get(viewer.getUniqueId());
        if (board == null || viewer.getScoreboard() != board) {
            // First takeover (or we lost the slot): remember what they had.
            Scoreboard current = viewer.getScoreboard();
            if (current != null && current != ourBoards.get(viewer.getUniqueId())) {
                previousBoards.putIfAbsent(viewer.getUniqueId(), current);
            }
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            ourBoards.put(viewer.getUniqueId(), board);
            viewer.setScoreboard(board);
        }
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (!obj.getDisplayName().equals(title)) {
            obj.setDisplayName(title);
        }
        // Remove stale entries, then lay out current lines bottom-up.
        for (String entry : board.getEntries()) {
            if (!lines.contains(entry)) board.resetScores(entry);
        }
        int score = lines.size();
        for (String line : lines) {
            obj.getScore(line).setScore(score--);
        }
    }

    /** Returns the player's remembered board (or the main one) and forgets them. */
    private void clearBoard(Player viewer) {
        if (viewer == null) return;
        Scoreboard ours = ourBoards.remove(viewer.getUniqueId());
        Scoreboard restore = previousBoards.remove(viewer.getUniqueId());
        if (ours == null) return;
        Player target = viewer;
        plugin.scheduler().runEntity(target, () -> {
            if (!target.isOnline() || target.getScoreboard() != ours) return;
            target.setScoreboard(restore != null
                    ? restore
                    : Bukkit.getScoreboardManager().getMainScoreboard());
        }, () -> { });
    }
}
