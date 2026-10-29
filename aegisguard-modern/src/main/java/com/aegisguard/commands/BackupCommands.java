package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import com.aegisguard.snapshots.ClaimSnapshot;
import com.aegisguard.snapshots.RestoreScope;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ag backup — player self-backups. An owner snapshots the plot they are
 * standing on (a data restore point; build copies ride the existing
 * snapshots.build_backup opt-in), browses the history, and rolls back through
 * the same durable restore pipeline staff use. Cooldown, per-plot cap, and an
 * optional Vault cost keep storage honest.
 */
final class BackupCommands extends CommandBase {

    private static final String SELF_REASON = "Self-backup";
    private static final long CONFIRM_MS = 30_000L;

    private static final class PendingRestore {
        final UUID snapshotId;
        final long expiresAt;
        PendingRestore(UUID snapshotId) {
            this.snapshotId = snapshotId;
            this.expiresAt = System.currentTimeMillis() + CONFIRM_MS;
        }
    }

    private final Map<UUID, Long> lastBackupAt = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRestore> pending = new ConcurrentHashMap<>();

    BackupCommands(AegisGuard plugin) { super(plugin); }

    private boolean enabled() {
        boolean on = plugin.getConfig().getBoolean("self_backup.enabled", true);
        try {
            on = on && plugin.modules().on(Modules.Id.SELF_BACKUP);
        } catch (Throwable ignored) { }
        return on;
    }

    void handleBackup(Player p, String[] args) {
        if (!enabled()) {
            sendKey(p, "backup_disabled", "&cSelf-backups are disabled on this server.");
            return;
        }
        if (!p.hasPermission("aegis.backup") && !plugin.isAdmin(p)) {
            sendKey(p, "backup_denied", "&cYou don't have permission to use self-backups.");
            return;
        }
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotAt(p.getLocation());
        if (plot == null || (!plot.isOwner(p.getUniqueId()) && !plugin.isAdmin(p))) {
            sendKey(p, "backup_stand_on_own_plot",
                    "&cStand on a plot you own to manage its backups.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "list", "l", "history" -> list(p, plot);
            case "restore", "revert", "undo" -> restore(p, plot, args);
            case "create", "new", "save" -> create(p, plot,
                    args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "");
            case "" -> {
                if (plugin.gui() != null && plugin.gui().backup() != null) {
                    plugin.gui().backup().open(p, plot, 0);
                } else {
                    create(p, plot, "");
                }
            }
            default -> create(p, plot, "");
        }
    }

    // ------------------------------------------------------------ create

    private void create(Player p, Plot plot) {
        create(p, plot, "");
    }

    /** Player labels are stripped of formatting codes and capped so they stay readable in the GUI. */
    private String sanitizeLabel(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[§&][0-9A-FK-ORXa-fk-orx]", "")
                .replaceAll("\\p{Cntrl}", "").trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private void create(Player p, Plot plot, String rawLabel) {
        String label = sanitizeLabel(rawLabel);
        long cooldownMs = Math.max(0L,
                plugin.getConfig().getLong("self_backup.cooldown_minutes", 30L)) * 60_000L;
        long last = lastBackupAt.getOrDefault(p.getUniqueId(), 0L);
        long wait = last + cooldownMs - System.currentTimeMillis();
        if (cooldownMs > 0L && wait > 0L) {
            sendKey(p, "backup_cooldown", "&eNext self-backup in &f{MINUTES}&e minute(s).",
                    Map.of("MINUTES", String.valueOf(Math.max(1L, wait / 60_000L))));
            return;
        }
        double cost = Math.max(0.0D, plugin.getConfig().getDouble("self_backup.cost", 0.0D));
        if (cost > 0.0D && plugin.vault() != null && plugin.vault().isEnabled()) {
            if (!plugin.vault().has(p, cost) || !plugin.vault().charge(p, cost)) {
                sendKey(p, "backup_cannot_afford", "&cA self-backup costs &f{AMOUNT}&c.",
                        Map.of("AMOUNT", String.format(Locale.US, "%.2f", cost)));
                return;
            }
        }
        String reason = label.isEmpty() ? SELF_REASON : SELF_REASON + ": " + label;
        sendKey(p, "backup_working", "&7Snapshotting your plot…");
        plugin.snapshots().createSnapshotDurableAsync(plot, ClaimSnapshot.SnapshotType.MANUAL,
                        reason, p.getUniqueId())
                .whenComplete((snapshot, error) -> plugin.runMain(p, () -> {
                    if (error != null || snapshot == null) {
                        sendKey(p, "backup_failed", "&cThe backup could not be saved.");
                        return;
                    }
                    lastBackupAt.put(p.getUniqueId(), System.currentTimeMillis());
                    pruneSelfBackups(plot);
                    sendKey(p, "backup_created", "&aBackup saved" + (label.isEmpty() ? ""
                            : "&a — &f" + label) + "&a. &f/ag backup list&a shows your restore points.");
                }));
    }

    /** Drop oldest self-tagged snapshots beyond the per-plot cap (never touches staff/system snapshots). */
    private void pruneSelfBackups(Plot plot) {
        int max = Math.max(1, plugin.getConfig().getInt("self_backup.max_per_plot", 5));
        List<ClaimSnapshot> mine = new ArrayList<>();
        for (ClaimSnapshot snap : plugin.snapshots().getSnapshotsForPlot(plot.getPlotId())) {
            String reason = snap == null ? null : snap.getReason();
            if (reason != null && (reason.equals(SELF_REASON)
                    || reason.startsWith(SELF_REASON + ":"))) mine.add(snap);
        }
        if (mine.size() <= max) return;
        mine.sort(Comparator.comparingLong(ClaimSnapshot::getTimestamp));
        for (int i = 0; i < mine.size() - max; i++) {
            plugin.snapshots().deleteSnapshot(mine.get(i).getSnapshotId());
        }
    }

    // -------------------------------------------------------------- list

    private void list(Player p, Plot plot) {
        List<ClaimSnapshot> snapshots = new ArrayList<>(
                plugin.snapshots().getSnapshotsForPlot(plot.getPlotId()));
        if (snapshots.isEmpty()) {
            sendKey(p, "backup_list_empty",
                    "&7No restore points yet — run &f/ag backup&7 to save one.");
            return;
        }
        snapshots.sort(Comparator.comparingLong(ClaimSnapshot::getTimestamp).reversed());
        String name = plot.getPlotName();
        if (name == null || name.isBlank()) name = "plot";
        sendKey(p, "backup_list_header", "&bRestore points for &f{PLOT}&b:",
                Map.of("PLOT", name));
        SimpleDateFormat fmt = new SimpleDateFormat("MMM d, HH:mm", Locale.ROOT);
        int shown = 0;
        for (ClaimSnapshot snap : snapshots) {
            if (snap == null || ++shown > 8) break;
            String id = snap.getSnapshotId().toString().substring(0, 8);
            String type = snap.getType() == null ? "manual"
                    : snap.getType().name().toLowerCase(Locale.ROOT);
            String reason = snap.getReason() == null || snap.getReason().isBlank()
                    ? "" : " — " + snap.getReason();
            sendMsg(p, "  &8[&e" + id + "&8] &f" + fmt.format(new Date(snap.getTimestamp()))
                    + " &7(" + type + reason + "&7)");
        }
        sendKey(p, "backup_list_hint",
                "&7Roll back: &f/ag backup restore <id>&7 — settings, roles, and members return.");
    }

    // ----------------------------------------------------------- restore

    private void restore(Player p, Plot plot, String[] args) {
        if (!"self".equalsIgnoreCase(
                plugin.getConfig().getString("self_backup.restore_mode", "self"))) {
            sendKey(p, "backup_restore_staff_only",
                    "&cRestores here are staff-approved — ask an admin to roll this plot back.");
            return;
        }
        if (args.length < 3) {
            sendKey(p, "backup_restore_usage",
                    "&eUsage: &f/ag backup restore <id>&e — find ids with &f/ag backup list&e.");
            return;
        }
        ClaimSnapshot target = findSnapshot(plot, args[2]);
        if (target == null) {
            sendKey(p, "backup_restore_not_found",
                    "&cNo restore point with that id. Check &f/ag backup list&c.");
            return;
        }
        PendingRestore wait = pending.get(p.getUniqueId());
        if (wait != null && wait.snapshotId.equals(target.getSnapshotId())
                && wait.expiresAt > System.currentTimeMillis()) {
            pending.remove(p.getUniqueId());
            runRestore(p, target);
            return;
        }
        pending.put(p.getUniqueId(), new PendingRestore(target.getSnapshotId()));
        sendKey(p, "backup_restore_confirm",
                "&eThis rolls your plot back to &f{TIME}&e. Run &f/ag backup restore {ID}&e again within 30s to confirm.",
                Map.of("TIME", new SimpleDateFormat("MMM d, HH:mm", Locale.ROOT)
                                .format(new Date(target.getTimestamp())),
                        "ID", args[2]));
    }

    private void runRestore(Player p, ClaimSnapshot target) {
        EnumSet<RestoreScope> scopes = EnumSet.of(RestoreScope.FULL_DATA);
        if (plugin.getConfig().getBoolean("self_backup.block_copies", false)) {
            scopes.add(RestoreScope.BUILD);
        }
        sendKey(p, "backup_restoring", "&7Restoring your plot from the snapshot…");
        plugin.snapshots().restoreAsync(target.getSnapshotId(), p.getUniqueId(), scopes)
                .whenComplete((result, error) -> plugin.runMain(p, () -> {
                    if (error != null || result == null || !result.dataRestored()) {
                        sendKey(p, "backup_restore_failed",
                                "&cThe restore did not complete — staff can check the Audit Ledger.");
                        return;
                    }
                    sendKey(p, "backup_restored",
                            "&aYour plot is back the way it was.");
                }));
    }

    /** Snapshot lookup by full id or the 8-char prefix shown in the list. */
    private ClaimSnapshot findSnapshot(Plot plot, String raw) {
        if (raw == null || raw.isBlank()) return null;
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (ClaimSnapshot snap : plugin.snapshots().getSnapshotsForPlot(plot.getPlotId())) {
            if (snap == null || snap.getSnapshotId() == null) continue;
            String id = snap.getSnapshotId().toString().toLowerCase(Locale.ROOT);
            if (id.equals(needle) || id.startsWith(needle)) return snap;
        }
        return null;
    }
}
