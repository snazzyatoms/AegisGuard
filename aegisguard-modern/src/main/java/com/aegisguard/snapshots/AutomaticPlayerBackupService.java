package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.scheduler.AegisScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded automatic data backups for eligible player plots and server zones.
 * Selection is global, plot capture is region-owned, and hashing/persistence is asynchronous.
 */
public final class AutomaticPlayerBackupService {

    public enum BatchStatus {
        DISABLED, NOT_LOADED, ALREADY_RUNNING, PAUSED_LOAD, EMPTY, COMPLETED, SCHEDULER_REJECTED
    }

    public record BatchResult(BatchStatus status, int selected, int saved, int unchanged,
                              int skipped, int failed, double observedTps) { }

    private final AegisGuard plugin;
    private final SnapshotManager snapshots;
    private final AutomaticBackupStateStore stateStore;
    private final Map<UUID, AutomaticBackupState> states = new ConcurrentHashMap<>();
    private final Set<UUID> inFlightPlots = ConcurrentHashMap.newKeySet();
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicBoolean batchRunning = new AtomicBoolean();
    private volatile boolean loaded;
    private volatile boolean shuttingDown;

    AutomaticPlayerBackupService(AegisGuard plugin, SnapshotManager snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
        this.stateStore = new AutomaticBackupStateStore(
                new File(plugin.getDataFolder(), "automatic-player-backups.yml"));
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("snapshots.enabled", true)
                && plugin.getConfig().getBoolean("snapshots.automatic_player.enabled", false);
    }

    public boolean includesServerZones() {
        return isEnabled() && plugin.getConfig().getBoolean(
                "snapshots.automatic_player.include_server_zones", true);
    }

    public int intervalMinutes() {
        return clamp(plugin.getConfig().getInt("snapshots.automatic_player.interval_minutes", 5), 1, 1440);
    }

    public int batchSize() {
        return clamp(plugin.getConfig().getInt("snapshots.automatic_player.batch_size", 5), 1, 100);
    }

    public int inFlightCount() {
        return inFlightPlots.size();
    }

    public boolean isPlotInFlight(UUID plotId) {
        return plotId != null && inFlightPlots.contains(plotId);
    }

    void load() {
        states.clear();
        states.putAll(stateStore.load());
        loaded = true;
        plugin.getLogger().info("[Snapshots] Loaded automatic backup state for " + states.size() + " plot(s).");
    }

    public CompletableFuture<BatchResult> runBatch() {
        CompletableFuture<BatchResult> result = new CompletableFuture<>();
        if (plugin.scheduler() == null) {
            result.complete(new BatchResult(BatchStatus.SCHEDULER_REJECTED,
                    0, 0, 0, 0, 1, 20.0D));
            return result;
        }
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runGlobal(() -> {
            try {
                runBatchOnGlobal().whenComplete((batch, error) -> {
                    if (error != null) {
                        batchRunning.set(false);
                        result.completeExceptionally(error);
                    } else result.complete(batch);
                });
            } catch (Throwable error) {
                batchRunning.set(false);
                result.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) result.complete(new BatchResult(BatchStatus.SCHEDULER_REJECTED,
                0, 0, 0, 0, 1, 20.0D));
        return result;
    }

    private CompletableFuture<BatchResult> runBatchOnGlobal() {
        if (!isEnabled()) return CompletableFuture.completedFuture(
                new BatchResult(BatchStatus.DISABLED, 0, 0, 0, 0, 0, 20.0D));
        if (!loaded || shuttingDown) return CompletableFuture.completedFuture(
                new BatchResult(BatchStatus.NOT_LOADED, 0, 0, 0, 0, 0, 20.0D));
        if (!batchRunning.compareAndSet(false, true)) return CompletableFuture.completedFuture(
                new BatchResult(BatchStatus.ALREADY_RUNNING, 0, 0, 0, 0, 0, 20.0D));

        double tps = currentTps(Bukkit.getServer());
        double pauseBelow = Math.max(0.0D, Math.min(20.0D,
                plugin.getConfig().getDouble("snapshots.automatic_player.pause_below_tps", 18.0D)));
        if (tps >= 0.0D && tps < pauseBelow) {
            batchRunning.set(false);
            return CompletableFuture.completedFuture(
                    new BatchResult(BatchStatus.PAUSED_LOAD, 0, 0, 0, 0, 0, tps));
        }

        long now = System.currentTimeMillis();
        List<Plot> eligible = plugin.store().getAllPlots().stream()
                .filter(plot -> eligible(plot, now))
                .sorted(Comparator.comparing(plot -> plot.getPlotId().toString()))
                .toList();
        removeDeletedState(plugin.store().getAllPlots());
        if (eligible.isEmpty()) {
            batchRunning.set(false);
            return CompletableFuture.completedFuture(
                    new BatchResult(BatchStatus.EMPTY, 0, 0, 0, 0, 0, tps));
        }

        List<Plot> selected = selectRoundRobin(eligible, cursor.getAndAdd(batchSize()), batchSize());
        List<CompletableFuture<Evaluation>> jobs = new ArrayList<>();
        for (Plot plot : selected) jobs.add(evaluate(plot, now));
        CompletableFuture<BatchResult> result = new CompletableFuture<>();
        CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            int saved = 0;
            int unchanged = 0;
            int skipped = 0;
            int failed = 0;
            for (CompletableFuture<Evaluation> job : jobs) {
                Evaluation evaluation = job.getNow(Evaluation.FAILED);
                switch (evaluation) {
                    case SAVED -> saved++;
                    case UNCHANGED -> unchanged++;
                    case SKIPPED -> skipped++;
                    case FAILED -> failed++;
                }
            }
            int finalSaved = saved;
            int finalUnchanged = unchanged;
            int finalSkipped = skipped;
            int finalFailed = failed;
            persistStateAsync().whenComplete((savedState, stateError) -> {
                batchRunning.set(false);
                result.complete(new BatchResult(BatchStatus.COMPLETED, selected.size(), finalSaved,
                        finalUnchanged, finalSkipped, finalFailed + (stateError == null ? 0 : 1), tps));
            });
        });
        return result;
    }

    void shutdown() {
        shuttingDown = true;
    }

    private CompletableFuture<Evaluation> evaluate(Plot plot, long now) {
        UUID plotId = plot.getPlotId();
        AutomaticBackupState existing = states.get(plotId);
        long minimumMinutes = clamp(plugin.getConfig().getLong(
                "snapshots.automatic_player.minimum_backup_interval_minutes", 60L), 0L, 10080L);
        if (existing != null && minimumMinutes > 0
                && now - existing.lastBackupAt() < minimumMinutes * 60_000L) {
            existing.checked(now, "minimum interval");
            return CompletableFuture.completedFuture(Evaluation.SKIPPED);
        }
        if (snapshots.isRestoreLocked(plotId) || !inFlightPlots.add(plotId)) {
            return CompletableFuture.completedFuture(Evaluation.SKIPPED);
        }

        CompletableFuture<Evaluation> result = new CompletableFuture<>();
        World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) {
            inFlightPlots.remove(plotId);
            return CompletableFuture.completedFuture(Evaluation.SKIPPED);
        }
        Location target = new Location(world, plot.getX1(), world.getMinHeight(), plot.getZ1());
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAt(target, () -> {
            try {
            if (!plugin.scheduler().owns(target) || snapshots.isRestoreLocked(plotId)) {
                finish(plotId, result, Evaluation.SKIPPED);
                return;
            }
            boolean serverZone = plot.isServerZone();
            ClaimSnapshot.SnapshotType snapshotType = serverZone
                    ? ClaimSnapshot.SnapshotType.AUTOMATIC_SERVER_ZONE
                    : ClaimSnapshot.SnapshotType.AUTOMATIC_PLAYER;
            String reason = serverZone ? "Automatic server-zone backup" : "Automatic player-plot backup";
            ClaimSnapshot candidate = snapshots.captureSnapshot(plot, snapshotType, reason, null);
            boolean captureBuild = plugin.getConfig().getBoolean(
                    "snapshots.automatic_player.build_backup.enabled", false);
            AegisScheduler.DispatchResult hashDispatch = plugin.scheduler().runAsync(() -> {
                try {
                String dataFingerprint = snapshotFingerprint(candidate);
                // Keep player and server-zone histories distinct even if a conversion happens
                // without another plot-data field changing.
                String fingerprint = (serverZone ? "server-zone:" : "player:") + dataFingerprint;
                AutomaticBackupState state = states.get(plotId);
                boolean skipUnchanged = plugin.getConfig().getBoolean(
                        "snapshots.automatic_player.skip_unchanged", true);
                // Plot-data fingerprints are complete and reliable. Build content can change through
                // plugins or inventories without a block event, so build-enabled passes never skip.
                if (skipUnchanged && !captureBuild && state != null
                        && fingerprint.equals(state.fingerprint())) {
                    state.checked(System.currentTimeMillis(), "unchanged");
                    finish(plotId, result, Evaluation.UNCHANGED);
                    return;
                }
                scheduleCommit(plot, candidate, fingerprint, captureBuild, target, result);
                } catch (Throwable error) {
                    fail(plotId, result, error);
                }
            });
            if (!hashDispatch.accepted()) finish(plotId, result, Evaluation.FAILED);
            } catch (Throwable error) {
                fail(plotId, result, error);
            }
        });
        if (!dispatch.accepted()) finish(plotId, result, Evaluation.FAILED);
        return result;
    }

    private void scheduleCommit(Plot plot, ClaimSnapshot candidate, String fingerprint,
                                boolean captureBuild, Location target,
                                CompletableFuture<Evaluation> result) {
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAt(target, () -> {
            try {
            UUID plotId = plot.getPlotId();
            if (!plugin.scheduler().owns(target) || snapshots.isRestoreLocked(plotId)) {
                finish(plotId, result, Evaluation.SKIPPED);
                return;
            }
            Plot live = plugin.store().getPlotById(plotId);
            if (live == null || (captureBuild && !sameGeometry(live, candidate))) {
                finish(plotId, result, Evaluation.SKIPPED);
                return;
            }
            int retention = clamp(plugin.getConfig().getInt(
                    "snapshots.automatic_player.retention_per_plot", 5), 1, 100);
            long days = clamp(plugin.getConfig().getLong(
                    "snapshots.automatic_player.retention_days", 14L), 0L, 3650L);
            snapshots.createAutomaticSnapshot(plot, candidate, captureBuild, retention, days)
                    .whenComplete((saved, error) -> {
                        if (error != null || saved == null || !saved.dataSaved()) {
                            AutomaticBackupState state = states.computeIfAbsent(plotId,
                                    id -> new AutomaticBackupState(id, "", 0L, 0L, ""));
                            state.checked(System.currentTimeMillis(), error == null
                                    ? saved == null ? "no result" : saved.detail()
                                    : safeMessage(error));
                            finish(plotId, result, Evaluation.FAILED);
                            return;
                        }
                        AutomaticBackupState state = states.computeIfAbsent(plotId,
                                id -> new AutomaticBackupState(id, "", 0L, 0L, ""));
                        state.backedUp(fingerprint, System.currentTimeMillis(),
                                saved.buildResult().name());
                        finish(plotId, result, Evaluation.SAVED);
                    });
            } catch (Throwable error) {
                fail(plot.getPlotId(), result, error);
            }
        });
        if (!dispatch.accepted()) finish(plot.getPlotId(), result, Evaluation.FAILED);
    }

    private boolean eligible(Plot plot, long now) {
        if (plot == null || plot.getPlotId() == null) return false;
        if (plot.isServerZone()) {
            if (!includesServerZones()) return false;
            return worldEligible(plot);
        }
        if (plot.getOwner() == null) return false;
        if (!plugin.getConfig().getBoolean("snapshots.automatic_player.eligibility.include_group_plots", true)
                && plot.isGroupPlot()) return false;
        if (!worldEligible(plot)) return false;

        long inactiveDays = clamp(plugin.getConfig().getLong(
                "snapshots.automatic_player.eligibility.max_owner_inactive_days", 30L), 0L, 3650L);
        if (inactiveDays <= 0 || Bukkit.getPlayer(plot.getOwner()) != null) return true;
        OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
        long lastPlayed = owner.getLastPlayed();
        return lastPlayed <= 0L || now - lastPlayed <= inactiveDays * 86_400_000L;
    }

    private boolean worldEligible(Plot plot) {
        List<String> allow = lower(plugin.getConfig().getStringList(
                "snapshots.automatic_player.eligibility.world_allowlist"));
        List<String> deny = lower(plugin.getConfig().getStringList(
                "snapshots.automatic_player.eligibility.world_denylist"));
        String world = plot.getWorldName() == null ? "" : plot.getWorldName().toLowerCase(java.util.Locale.ROOT);
        if (!allow.isEmpty() && !allow.contains(world)) return false;
        return !deny.contains(world) && Bukkit.getWorld(plot.getWorldName()) != null;
    }

    private CompletableFuture<Void> persistStateAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                stateStore.save(states.values());
                future.complete(null);
            } catch (Exception error) {
                future.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) future.completeExceptionally(
                new IllegalStateException("Automatic backup state persistence was rejected"));
        return future;
    }

    private void removeDeletedState(Collection<Plot> plots) {
        Set<UUID> live = new HashSet<>();
        for (Plot plot : plots) if (plot != null && plot.getPlotId() != null) live.add(plot.getPlotId());
        states.keySet().removeIf(id -> !live.contains(id));
    }

    private void finish(UUID plotId, CompletableFuture<Evaluation> result, Evaluation evaluation) {
        inFlightPlots.remove(plotId);
        result.complete(evaluation);
    }

    private void fail(UUID plotId, CompletableFuture<Evaluation> result, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Automatic backup failed for plot " + plotId, error);
        finish(plotId, result, Evaluation.FAILED);
    }

    static <T> List<T> selectRoundRobin(List<T> source, int start, int limit) {
        if (source == null || source.isEmpty() || limit <= 0) return List.of();
        int count = Math.min(limit, source.size());
        int normalized = Math.floorMod(start, source.size());
        List<T> selected = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            selected.add(source.get((normalized + index) % source.size()));
        }
        return List.copyOf(selected);
    }

    static String snapshotFingerprint(ClaimSnapshot snapshot) {
        if (snapshot == null) return "";
        StringBuilder value = new StringBuilder(2048);
        append(value, snapshot.getPlotId());
        append(value, snapshot.getOwner());
        append(value, snapshot.getOwnerName());
        append(value, snapshot.getWorldName());
        append(value, snapshot.getX1()); append(value, snapshot.getZ1());
        append(value, snapshot.getX2()); append(value, snapshot.getZ2());
        append(value, snapshot.getPlotName()); append(value, snapshot.getDescription());
        append(value, snapshot.getWelcomeMessage()); append(value, snapshot.getFarewellMessage());
        append(value, snapshot.getEntryTitle()); append(value, snapshot.getEntrySubtitle());
        append(value, snapshot.getCustomBiome()); append(value, snapshot.getPlotStatus());
        append(value, snapshot.isServerWarp()); append(value, snapshot.isGroupPlot());
        append(value, snapshot.getTreasuryBalance()); append(value, snapshot.getGroupId());
        append(value, snapshot.getGroupName());
        snapshot.getFlags().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> { append(value, entry.getKey()); append(value, entry.getValue()); });
        snapshot.getMembers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> { append(value, entry.getKey()); append(value, entry.getValue()); });
        snapshot.getBannedPlayers().stream().sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> append(value, id));
        append(value, snapshot.getGuestPassesBlob()); append(value, snapshot.getNoticeboardBlob());
        append(value, snapshot.getAllianceAccessBlob()); append(value, snapshot.getRoleNicknamesBlob());
        append(value, snapshot.getRoleFlagsBlob()); append(value, snapshot.isLockdownActive());
        append(value, snapshot.getLockdownActivatedAt()); append(value, snapshot.getLockdownExpiresAt());
        append(value, snapshot.getLockdownMode()); append(value, snapshot.getLockdownActivatedBy());
        append(value, snapshot.getLockdownActivatedByName());
        append(value, snapshot.getExtendedStateBlob());
        append(value, snapshot.getTerritoryRentalStateBlob());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    static double currentTps(Object server) {
        if (server == null) return 20.0D;
        try {
            Object value = server.getClass().getMethod("getTPS").invoke(server);
            if (value instanceof double[] samples && samples.length > 0) {
                return Math.max(0.0D, Math.min(20.0D, samples[0]));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 20.0D;
    }

    static boolean sameGeometry(Plot plot, ClaimSnapshot snapshot) {
        return plot != null && snapshot != null
                && java.util.Objects.equals(plot.getWorldName(), snapshot.getWorldName())
                && plot.getX1() == snapshot.getX1() && plot.getZ1() == snapshot.getZ1()
                && plot.getX2() == snapshot.getX2() && plot.getZ2() == snapshot.getZ2();
    }

    private static void append(StringBuilder target, Object value) {
        String text = String.valueOf(value);
        target.append(text.length()).append(':').append(text).append('|');
    }

    private static List<String> lower(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(value -> !value.isEmpty()).toList();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        while (root != null && root.getCause() != null) root = root.getCause();
        return root == null || root.getMessage() == null ? "unknown error" : root.getMessage();
    }

    private enum Evaluation { SAVED, UNCHANGED, SKIPPED, FAILED }
}
