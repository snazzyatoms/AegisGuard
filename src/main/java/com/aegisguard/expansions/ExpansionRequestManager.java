package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.snapshots.ClaimSnapshot;
import com.aegisguard.snapshots.SnapshotManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ExpansionRequestManager
 * - Handles land expansion requests.
 * - Supports "QUEUE" mode (admin approval) and "INSTANT" mode (auto-approval).
 * - Optional audit/history: records approvals/denials (including AUTO approvals).
 * - Integrates with SnapshotManager to create PRE_EXPANSION snapshots before applying changes.
 *
 * Persistence:
 *  requests.<requesterUUID>.owner
 *  requests.<requesterUUID>.plotId
 *  requests.<requesterUUID>.world
 *  requests.<requesterUUID>.currentRadius
 *  requests.<requesterUUID>.requestedRadius
 *  requests.<requesterUUID>.cost
 *  requests.<requesterUUID>.timestamp
 *  requests.<requesterUUID>.status
 *  requests.<requesterUUID>.decisionTimestamp
 *
 *  history.<safeKey>.requester
 *  history.<safeKey>.owner
 *  history.<safeKey>.plotId
 *  history.<safeKey>.world
 *  history.<safeKey>.currentRadius
 *  history.<safeKey>.requestedRadius
 *  history.<safeKey>.cost
 *  history.<safeKey>.timestamp
 *  history.<safeKey>.status
 *  history.<safeKey>.decisionTimestamp
 *  history.<safeKey>.actorType
 *  history.<safeKey>.actor
 *  history.<safeKey>.note
 */
public class ExpansionRequestManager {

    public enum ApprovalMode { QUEUE, INSTANT }
    public enum ActorType { ADMIN, AUTO, SYSTEM, UNKNOWN }

    public static final class DecisionRecord {
        private final String key;
        private final UUID requester;
        private final UUID plotOwner;
        private final UUID plotId;
        private final String worldName;
        private final int currentRadius;
        private final int requestedRadius;
        private final double cost;
        private final long timestamp;
        private final long decisionTimestamp;
        private final ExpansionRequest.Status status;
        private final ActorType actorType;
        private final UUID actor;
        private final String note;

        public DecisionRecord(String key,
                              UUID requester,
                              UUID plotOwner,
                              UUID plotId,
                              String worldName,
                              int currentRadius,
                              int requestedRadius,
                              double cost,
                              long timestamp,
                              long decisionTimestamp,
                              ExpansionRequest.Status status,
                              ActorType actorType,
                              UUID actor,
                              String note) {
            this.key = key;
            this.requester = requester;
            this.plotOwner = plotOwner;
            this.plotId = plotId;
            this.worldName = worldName == null ? "" : worldName;
            this.currentRadius = currentRadius;
            this.requestedRadius = requestedRadius;
            this.cost = cost;
            this.timestamp = timestamp;
            this.decisionTimestamp = decisionTimestamp;
            this.status = status == null ? ExpansionRequest.Status.PENDING : status;
            this.actorType = actorType == null ? ActorType.UNKNOWN : actorType;
            this.actor = actor;
            this.note = note == null ? "" : note;
        }

        public String getKey() { return key; }
        public UUID getRequester() { return requester; }
        public UUID getPlotOwner() { return plotOwner; }
        public UUID getPlotId() { return plotId; }
        public String getWorldName() { return worldName; }
        public int getCurrentRadius() { return currentRadius; }
        public int getRequestedRadius() { return requestedRadius; }
        public double getCost() { return cost; }
        public long getTimestamp() { return timestamp; }
        public long getDecisionTimestamp() { return decisionTimestamp; }
        public ExpansionRequest.Status getStatus() { return status; }
        public ActorType getActorType() { return actorType; }
        public UUID getActor() { return actor; }
        public String getNote() { return note; }
    }

    private final AegisGuard plugin;

    // Pending-only map (QUEUE mode uses this)
    private final Map<UUID, ExpansionRequest> activeRequests = new ConcurrentHashMap<>();

    // Recent decisions (APPROVED/DENIED), for audit and "approved by AUTO" visibility
    private final Deque<DecisionRecord> history = new ConcurrentLinkedDeque<>();

    private final File file;
    private FileConfiguration data;

    private volatile boolean isDirty = false;

    public ExpansionRequestManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "expansion-requests.yml");
    }

    /** Pending requests only. */
    public Collection<ExpansionRequest> getActiveRequests() {
        return Collections.unmodifiableCollection(activeRequests.values());
    }

    /** Pending request only (if present). */
    public ExpansionRequest getRequest(UUID requesterId) {
        return activeRequests.get(requesterId);
    }

    /** Recent decisions (newest first). Useful for admin GUIs / audit views. */
    public List<DecisionRecord> getRecentDecisions() {
        List<DecisionRecord> out = new ArrayList<>(history);
        out.sort(Comparator.comparingLong(DecisionRecord::getDecisionTimestamp).reversed());
        return out;
    }

    /** True only if the stored request is still pending. */
    public boolean hasPendingRequest(UUID requesterId) {
        ExpansionRequest req = activeRequests.get(requesterId);
        if (req == null) return false;

        if (!req.isPending()) {
            // Defensive: older files loaded non-pending states.
            activeRequests.remove(requesterId);
            setDirty(true);
            return false;
        }
        return true;
    }

    /* -----------------------------
     * SNAPSHOT INTEGRATION
     * ----------------------------- */

    /**
     * Get the SnapshotManager instance.
     * @return SnapshotManager or null if not available
     */
    private SnapshotManager getSnapshotManager() {
        return plugin.getSnapshotManager();
    }

    /**
     * Check if snapshot creation is enabled for expansions.
     */
    private boolean isSnapshotEnabled() {
        return plugin.cfg().raw().getBoolean("expansions.snapshots.enabled", true);
    }

    /**
     * Create a PRE_EXPANSION snapshot before applying an expansion.
     * @param plot The plot being expanded
     * @param currentRadius Current radius
     * @param newRadius Requested new radius
     * @param triggeredBy UUID of the actor (admin or null for AUTO)
     * @return The created snapshot, or null if snapshots are disabled or failed
     */
    private ClaimSnapshot createExpansionSnapshot(Plot plot, int currentRadius, int newRadius, UUID triggeredBy) {
        if (!isSnapshotEnabled()) return null;

        SnapshotManager snapshotManager = getSnapshotManager();
        if (snapshotManager == null) {
            plugin.getLogger().warning("[Expansions] SnapshotManager not available, skipping snapshot creation");
            return null;
        }

        String reason = "Before expansion: radius " + currentRadius + " -> " + newRadius;

        try {
            ClaimSnapshot snapshot = snapshotManager.createSnapshot(
                    plot,
                    ClaimSnapshot.SnapshotType.PRE_EXPANSION,
                    reason,
                    triggeredBy
            );

            plugin.getLogger().info("[Expansions] Created PRE_EXPANSION snapshot " + snapshot.getSnapshotId() +
                    " for plot " + plot.getPlotId());

            return snapshot;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Expansions] Failed to create snapshot for plot " + plot.getPlotId(), e);
            return null;
        }
    }

    /**
     * Get all PRE_EXPANSION snapshots for a specific plot.
     * @param plotId The plot UUID
     * @return List of expansion snapshots, newest first
     */
    public List<ClaimSnapshot> getExpansionSnapshots(UUID plotId) {
        SnapshotManager snapshotManager = getSnapshotManager();
        if (snapshotManager == null) return Collections.emptyList();

        return snapshotManager.getSnapshotsForPlot(plotId).stream()
                .filter(s -> s.getType() == ClaimSnapshot.SnapshotType.PRE_EXPANSION)
                .collect(Collectors.toList());
    }

    /**
     * Get all PRE_EXPANSION snapshots across all plots.
     * @return List of all expansion snapshots, newest first
     */
    public List<ClaimSnapshot> getAllExpansionSnapshots() {
        SnapshotManager snapshotManager = getSnapshotManager();
        if (snapshotManager == null) return Collections.emptyList();

        return snapshotManager.getAllSnapshots().stream()
                .filter(s -> s.getType() == ClaimSnapshot.SnapshotType.PRE_EXPANSION)
                .collect(Collectors.toList());
    }

    /**
     * Rollback an expansion using a snapshot.
     * @param snapshotId The snapshot to rollback to
     * @return true if rollback succeeded
     */
    public boolean rollbackExpansion(UUID snapshotId) {
        SnapshotManager snapshotManager = getSnapshotManager();
        if (snapshotManager == null) {
            plugin.getLogger().warning("[Expansions] Cannot rollback: SnapshotManager not available");
            return false;
        }

        ClaimSnapshot snapshot = snapshotManager.getSnapshot(snapshotId);
        if (snapshot == null) {
            plugin.getLogger().warning("[Expansions] Cannot rollback: snapshot " + snapshotId + " not found");
            return false;
        }

        if (snapshot.getType() != ClaimSnapshot.SnapshotType.PRE_EXPANSION) {
            plugin.getLogger().warning("[Expansions] Cannot rollback: snapshot " + snapshotId + " is not a PRE_EXPANSION snapshot");
            return false;
        }

        return snapshotManager.rollback(snapshotId);
    }

    /**
     * Get the most recent expansion snapshot for a plot.
     * @param plotId The plot UUID
     * @return The most recent PRE_EXPANSION snapshot, or null if none
     */
    public ClaimSnapshot getLatestExpansionSnapshot(UUID plotId) {
        List<ClaimSnapshot> snapshots = getExpansionSnapshots(plotId);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /* -----------------------------
     * REQUEST CREATION
     * ----------------------------- */
    public boolean createRequest(Player requester, Plot plot, int newRadius) {
        if (plot == null || !plot.getOwner().equals(requester.getUniqueId())) {
            plugin.msg().send(requester, "no_perm");
            return false;
        }

        // Only one pending request per requester (Queue Mode)
        if (getApprovalMode() == ApprovalMode.QUEUE && hasPendingRequest(requester.getUniqueId())) {
            plugin.msg().send(requester, "expansion_exists");
            return false;
        }

        // 1) Size Check
        int currentRadius = Math.max(0, (plot.getX2() - plot.getX1()) / 2);
        if (newRadius <= currentRadius) {
            plugin.msg().send(requester, "expansion_invalid_size");
            return false;
        }

        // 2) Limit Check
        int maxRadius = plugin.cfg().raw().getInt("expansions.max_radius_global", 100);
        if (newRadius > maxRadius && !requester.hasPermission("aegis.admin.bypass-limits")) {
            plugin.msg().send(requester, "expansion_limit_reached", Map.of("LIMIT", String.valueOf(maxRadius)));
            return false;
        }

        // 3) Cost Check (area-based)
        double cost = calculateSmartCost(currentRadius, newRadius);
        CurrencyType type = CurrencyType.VAULT;

        if (!plugin.eco().has(requester, cost, type)) {
            plugin.msg().send(requester, "expansion_payment_failed");
            return false;
        }

        // 4) Overlap Check
        if (isOverlapping(plot, newRadius)) {
            plugin.msg().send(requester, "expansion_overlap_fail");
            return false;
        }

        // 5) Branch: QUEUE vs INSTANT
        if (getApprovalMode() == ApprovalMode.INSTANT) {
            return processInstantApproval(requester, plot, currentRadius, newRadius, cost, type);
        }

        // QUEUE Mode: Submit pending request
        ExpansionRequest request = new ExpansionRequest(
                requester.getUniqueId(),
                plot.getOwner(),
                plot.getPlotId(),
                requester.getWorld().getName(),
                currentRadius,
                newRadius,
                cost
        );

        activeRequests.put(requester.getUniqueId(), request);
        setDirty(true);

        Map<String, String> placeholders = Map.of(
                "PLAYER", requester.getName(),
                "AMOUNT", plugin.eco().format(cost, type),
                "SIZE", newRadius + " blocks"
        );

        plugin.msg().send(requester, "expansion_submitted", placeholders);
        return true;
    }

    private boolean processInstantApproval(Player requester,
                                          Plot plot,
                                          int currentRadius,
                                          int newRadius,
                                          double cost,
                                          CurrencyType type) {

        // Charge now (instant mode)
        if (!plugin.eco().withdraw(requester, cost, type)) {
            plugin.msg().send(requester, "expansion_payment_failed");
            return false;
        }

        // Apply expansion (re-check overlap one last time)
        Plot oldPlot = plugin.store().getPlot(plot.getOwner(), plot.getPlotId());
        if (oldPlot == null) {
            refund(Bukkit.getOfflinePlayer(requester.getUniqueId()), requester, cost, type);
            plugin.msg().send(requester, "transaction_failed"); // falls back if missing
            return false;
        }

        // Create PRE_EXPANSION snapshot before applying changes (AUTO = null triggeredBy)
        createExpansionSnapshot(oldPlot, currentRadius, newRadius, null);

        if (!applyExpansion(oldPlot, newRadius)) {
            refund(Bukkit.getOfflinePlayer(requester.getUniqueId()), requester, cost, type);
            plugin.msg().send(requester, "expansion_overlap_fail");
            return false;
        }

        // Build a request object for audit (not stored as pending)
        ExpansionRequest req = new ExpansionRequest(
                requester.getUniqueId(),
                plot.getOwner(),
                plot.getPlotId(),
                requester.getWorld().getName(),
                currentRadius,
                newRadius,
                cost
        );
        req.approve(); // status + decision timestamp

        // Notify player: approved by AUTO
        String actor = plugin.gui().tr(requester, "expansion_actor_auto", "Auto");
        plugin.msg().send(requester, "expansion_approved", Map.of("PLAYER", actor));
        plugin.effects().playConfirm(requester);

        // Audit trail
        if (isAuditEnabled()) {
            recordDecision(req, ActorType.AUTO, null, "Instant Mode");
        }

        // Optional admin notice
        notifyAdminsAutoApproved(requester, req);

        return true;
    }

    /* -----------------------------
     * APPROVE / DENY
     * ----------------------------- */

    /** Backwards compatible: admin GUI may call without actor. */
    public boolean approveRequest(ExpansionRequest req) {
        return approveRequest(req, null);
    }

    /** Preferred: pass the admin UUID for clean audit. */
    public boolean approveRequest(ExpansionRequest req, UUID adminActor) {
        if (req == null) return false;

        // Must still be pending to approve
        if (!req.isPending()) {
            activeRequests.remove(req.getRequester());
            setDirty(true);
            return false;
        }

        OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
        CurrencyType type = CurrencyType.VAULT;

        // 1) Charge Player
        Player p = requester.getPlayer();
        if (p != null) {
            if (!plugin.eco().withdraw(p, req.getCost(), type)) {
                removeRequest(req);
                plugin.msg().send(p, "expansion_payment_failed");
                return false;
            }
        } else {
            // Offline charge via Vault wrapper (if you have one)
            if (plugin.cfg().useVault()) {
                if (!plugin.vault().charge(requester, req.getCost())) {
                    removeRequest(req);
                    return false;
                }
            } else {
                removeRequest(req);
                return false;
            }
        }

        // 2) Get Plot
        Plot oldPlot = plugin.store().getPlot(req.getPlotOwner(), req.getPlotId());
        if (oldPlot == null) {
            refund(requester, p, req.getCost(), type);
            removeRequest(req);
            return false;
        }

        // 3) Create PRE_EXPANSION snapshot before applying changes
        createExpansionSnapshot(oldPlot, req.getCurrentRadius(), req.getRequestedRadius(), adminActor);

        // 4) Apply Expansion (re-check overlap at approval time)
        if (!applyExpansion(oldPlot, req.getRequestedRadius())) {
            refund(requester, p, req.getCost(), type);

            // Keep semantics: denial due to overlap/rules
            denyRequest(req, adminActor);
            plugin.getLogger().warning("Expansion approval failed (overlap or invalid bounds) for " + req.getRequester());
            return false;
        }

        // 5) Mark + remove active
        req.approve();
        activeRequests.remove(req.getRequester());
        setDirty(true);

        // 6) Notify (online only)
        if (p != null) {
            String actor = plugin.gui().tr(p, "expansion_actor_admin", "Admin");
            plugin.msg().send(p, "expansion_approved", Map.of("PLAYER", actor));
            plugin.effects().playConfirm(p);
        }

        // 7) Audit
        if (isAuditEnabled()) {
            recordDecision(req, ActorType.ADMIN, adminActor, "");
        }

        return true;
    }

    /** Backwards compatible: admin GUI may call without actor. */
    public boolean denyRequest(ExpansionRequest req) {
        return denyRequest(req, null);
    }

    /** Preferred: pass the admin UUID for clean audit. */
    public boolean denyRequest(ExpansionRequest req, UUID adminActor) {
        if (req == null) return false;

        req.deny();

        OfflinePlayer target = Bukkit.getOfflinePlayer(req.getRequester());
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null) {
                String actor = plugin.gui().tr(p, "expansion_actor_admin", "Admin");
                plugin.msg().send(p, "expansion_denied", Map.of("PLAYER", actor));
                plugin.effects().playError(p);
            }
        }

        activeRequests.remove(req.getRequester());
        setDirty(true);

        // Audit
        if (isAuditEnabled()) {
            recordDecision(req, ActorType.ADMIN, adminActor, "");
        }

        return true;
    }

    private void removeRequest(ExpansionRequest req) {
        activeRequests.remove(req.getRequester());
        setDirty(true);
    }

    private void refund(OfflinePlayer requester, Player onlinePlayer, double amount, CurrencyType type) {
        if (amount <= 0) return;

        if (onlinePlayer != null) {
            plugin.eco().deposit(onlinePlayer, amount, type);
            return;
        }

        if (plugin.cfg().useVault()) {
            plugin.vault().give(requester, amount);
        }
    }

    // --- LOGIC ---

    /**
     * Area-based cost:
     *  - radius r -> square side = (2r + 1)
     *  - area = side^2
     *  - cost_per_block applies to added area (delta blocks)
     */
    private double calculateSmartCost(int currentRadius, int newRadius) {
        double perBlock = plugin.cfg().raw().getDouble("expansions.cost_per_block", 10.0);
        double multiplier = plugin.cfg().raw().getDouble("expansions.cost_multiplier", 1.1);

        long oldSide = (2L * currentRadius) + 1L;
        long newSide = (2L * newRadius) + 1L;

        long oldArea = oldSide * oldSide;
        long newArea = newSide * newSide;

        long addedBlocks = Math.max(0L, newArea - oldArea);

        double totalCost = perBlock * addedBlocks;

        // Optional "rapid growth tax" if radius jump is large
        int radiusJump = Math.max(0, newRadius - currentRadius);
        if (radiusJump > 10) totalCost *= multiplier;

        return Math.round(totalCost * 100.0) / 100.0;
    }

    private boolean isOverlapping(Plot oldPlot, int newRadius) {
        int cX = (oldPlot.getX1() + oldPlot.getX2()) / 2;
        int cZ = (oldPlot.getZ1() + oldPlot.getZ2()) / 2;

        int buffer = plugin.cfg().raw().getInt("expansions.buffer_zone", 5);
        int r = newRadius + buffer;

        int x1 = cX - r;
        int z1 = cZ - r;
        int x2 = cX + r;
        int z2 = cZ + r;

        return plugin.store().isAreaOverlapping(oldPlot, oldPlot.getWorld(), x1, z1, x2, z2);
    }

    /**
     * Applies expansion safely:
     * - Re-check overlap (excluding the plot itself)
     * - Only mutates store after the overlap check passes
     */
    private boolean applyExpansion(Plot oldPlot, int newRadius) {
        if (newRadius <= 0) return false;

        // Re-check overlap at approval time
        if (isOverlapping(oldPlot, newRadius)) return false;

        int cX = (oldPlot.getX1() + oldPlot.getX2()) / 2;
        int cZ = (oldPlot.getZ1() + oldPlot.getZ2()) / 2;

        int x1 = cX - newRadius;
        int z1 = cZ - newRadius;
        int x2 = cX + newRadius;
        int z2 = cZ + newRadius;

        // Replace in store
        plugin.store().removePlot(oldPlot.getOwner(), oldPlot.getPlotId());

        oldPlot.setX1(x1); oldPlot.setX2(x2);
        oldPlot.setZ1(z1); oldPlot.setZ2(z2);

        plugin.store().addPlot(oldPlot);
        plugin.store().setDirty(true);

        return true;
    }

    // --- MODE + AUDIT CONFIG ---

    private ApprovalMode getApprovalMode() {
        FileConfiguration c = plugin.cfg().raw();

        // Preferred: expansions.approval.mode
        String mode = c.getString("expansions.approval.mode", "").trim();
        if (!mode.isEmpty()) {
            try {
                return ApprovalMode.valueOf(mode.toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) { }
        }

        // Legacy fallbacks (in case you used older naming)
        String mode2 = c.getString("expansions.approval_mode", "").trim();
        if (!mode2.isEmpty()) {
            try {
                return ApprovalMode.valueOf(mode2.toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) { }
        }

        boolean legacyInstant =
                c.getBoolean("expansions.instant_mode", false)
                        || c.getBoolean("expansions.instant_mode.enabled", false)
                        || c.getBoolean("expansions.auto_approve", false)
                        || c.getBoolean("expansions.auto_approve.enabled", false);

        return legacyInstant ? ApprovalMode.INSTANT : ApprovalMode.QUEUE;
    }

    private boolean isAuditEnabled() {
        FileConfiguration c = plugin.cfg().raw();
        if (c.contains("expansions.audit.enabled")) {
            return c.getBoolean("expansions.audit.enabled", true);
        }
        // Sensible default: if INSTANT mode is on and audit isn't defined, enable it.
        return getApprovalMode() == ApprovalMode.INSTANT;
    }

    private int auditMaxEntries() {
        return Math.max(25, plugin.cfg().raw().getInt("expansions.audit.max_entries", 250));
    }

    private long auditKeepMinutes() {
        return Math.max(0L, plugin.cfg().raw().getLong("expansions.audit.keep_recent_minutes", 10080L));
    }

    private boolean auditSaveToFile() {
        return plugin.cfg().raw().getBoolean("expansions.audit.save_to_file", true);
    }

    private boolean auditLogToConsole() {
        return plugin.cfg().raw().getBoolean("expansions.audit.log_to_console", true);
    }

    private boolean notifyAdminsOnAuto() {
        return plugin.cfg().raw().getBoolean("expansions.approval.notify_admins", true);
    }

    private String notifyPermission() {
        return plugin.cfg().raw().getString("expansions.approval.notify_permission", "aegis.admin");
    }

    private void recordDecision(ExpansionRequest req, ActorType actorType, UUID actor, String note) {
        if (req == null) return;

        pruneHistory();

        String key = safeHistoryKey(req.getRequester(), req.getPlotId(), req.getTimestamp());
        DecisionRecord record = new DecisionRecord(
                key,
                req.getRequester(),
                req.getPlotOwner(),
                req.getPlotId(),
                req.getWorldName(),
                req.getCurrentRadius(),
                req.getRequestedRadius(),
                req.getCost(),
                req.getTimestamp(),
                req.getDecisionTimestamp() > 0 ? req.getDecisionTimestamp() : System.currentTimeMillis(),
                req.getStatus(),
                actorType,
                actor,
                note
        );

        history.addLast(record);

        // enforce size cap
        while (history.size() > auditMaxEntries()) {
            history.pollFirst();
        }

        setDirty(true);

        if (auditLogToConsole()) {
            plugin.getLogger().info("[Expansions] " + actorType + " " + record.getStatus()
                    + " requester=" + record.getRequester()
                    + " plotId=" + record.getPlotId()
                    + " world=" + record.getWorldName()
                    + " radius " + record.getCurrentRadius() + " -> " + record.getRequestedRadius()
                    + " cost=" + record.getCost()
                    + (note == null || note.isBlank() ? "" : " note=" + note));
        }
    }

    private void pruneHistory() {
        if (!isAuditEnabled()) return;

        long keepMin = auditKeepMinutes();
        if (keepMin <= 0) return;

        long cutoff = System.currentTimeMillis() - (keepMin * 60_000L);

        // remove old entries (oldest first)
        while (true) {
            DecisionRecord first = history.peekFirst();
            if (first == null) break;
            long ts = first.getDecisionTimestamp() > 0 ? first.getDecisionTimestamp() : first.getTimestamp();
            if (ts >= cutoff) break;
            history.pollFirst();
        }

        // enforce hard cap too
        while (history.size() > auditMaxEntries()) {
            history.pollFirst();
        }
    }

    private void notifyAdminsAutoApproved(Player requester, ExpansionRequest req) {
        if (!notifyAdminsOnAuto()) return;

        String perm = notifyPermission();
        String requesterName = requester.getName() == null ? "Unknown" : requester.getName();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            if (!online.hasPermission(perm) && !plugin.isAdmin(online)) continue;

            // Suggested key (add later), fallback included:
            plugin.msg().send(online,
                    "expansion_auto_admin_notice",
                    Map.of(
                            "PLAYER", requesterName,
                            "WORLD", req.getWorldName() == null ? "" : req.getWorldName(),
                            "CUR", String.valueOf(req.getCurrentRadius()),
                            "REQ", String.valueOf(req.getRequestedRadius())
                    )
            );
        }
    }

    private String safeHistoryKey(UUID requester, UUID plotId, long timestamp) {
        // YAML-safe key: no colons
        return String.valueOf(requester) + "_" + String.valueOf(plotId) + "_" + timestamp;
    }

    // --- PERSISTENCE ---

    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }

    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create expansion-requests.yml", e);
        }

        data = YamlConfiguration.loadConfiguration(file);
        activeRequests.clear();
        history.clear();

        // Pending requests
        if (data.isConfigurationSection("requests")) {
            for (String key : data.getConfigurationSection("requests").getKeys(false)) {
                try {
                    UUID reqId = UUID.fromString(key);
                    String path = "requests." + key;

                    UUID owner = UUID.fromString(Objects.requireNonNull(data.getString(path + ".owner")));
                    UUID plotId = UUID.fromString(Objects.requireNonNull(data.getString(path + ".plotId")));
                    String world = data.getString(path + ".world", "");

                    int currentRadius = data.getInt(path + ".currentRadius");
                    int requestedRadius = data.getInt(path + ".requestedRadius");
                    double cost = data.getDouble(path + ".cost");

                    long timestamp = data.getLong(path + ".timestamp", System.currentTimeMillis());
                    long decisionTs = data.getLong(path + ".decisionTimestamp", 0L);

                    // Backwards compatibility: older files won't have status
                    String statusStr = data.getString(path + ".status", "PENDING");
                    ExpansionRequest.Status status;
                    try {
                        status = ExpansionRequest.Status.valueOf(statusStr.toUpperCase(Locale.ROOT));
                    } catch (Throwable ignored) {
                        status = ExpansionRequest.Status.PENDING;
                    }

                    ExpansionRequest req = new ExpansionRequest(
                            reqId,
                            owner,
                            plotId,
                            world,
                            currentRadius,
                            requestedRadius,
                            cost,
                            timestamp,
                            status,
                            decisionTs
                    );

                    // Only keep pending requests in the active map
                    if (req.isPending()) {
                        activeRequests.put(reqId, req);
                    }

                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Skipping invalid expansion request entry: " + key, ex);
                }
            }
        }

        // Decision history (optional)
        if (data.isConfigurationSection("history")) {
            for (String hKey : data.getConfigurationSection("history").getKeys(false)) {
                try {
                    String path = "history." + hKey;

                    UUID requester = UUID.fromString(Objects.requireNonNull(data.getString(path + ".requester")));
                    UUID owner = UUID.fromString(Objects.requireNonNull(data.getString(path + ".owner")));
                    UUID plotId = UUID.fromString(Objects.requireNonNull(data.getString(path + ".plotId")));

                    String world = data.getString(path + ".world", "");
                    int currentRadius = data.getInt(path + ".currentRadius");
                    int requestedRadius = data.getInt(path + ".requestedRadius");
                    double cost = data.getDouble(path + ".cost");

                    long timestamp = data.getLong(path + ".timestamp", System.currentTimeMillis());
                    long decisionTs = data.getLong(path + ".decisionTimestamp", timestamp);

                    String statusStr = data.getString(path + ".status", "PENDING");
                    ExpansionRequest.Status status;
                    try {
                        status = ExpansionRequest.Status.valueOf(statusStr.toUpperCase(Locale.ROOT));
                    } catch (Throwable ignored) {
                        status = ExpansionRequest.Status.PENDING;
                    }

                    String actorTypeStr = data.getString(path + ".actorType", "UNKNOWN");
                    ActorType actorType;
                    try {
                        actorType = ActorType.valueOf(actorTypeStr.toUpperCase(Locale.ROOT));
                    } catch (Throwable ignored) {
                        actorType = ActorType.UNKNOWN;
                    }

                    UUID actor = null;
                    String actorStr = data.getString(path + ".actor", "");
                    if (actorStr != null && !actorStr.isBlank()) {
                        try { actor = UUID.fromString(actorStr); } catch (Throwable ignored) { }
                    }

                    String note = data.getString(path + ".note", "");

                    DecisionRecord rec = new DecisionRecord(
                            hKey,
                            requester,
                            owner,
                            plotId,
                            world,
                            currentRadius,
                            requestedRadius,
                            cost,
                            timestamp,
                            decisionTs,
                            status,
                            actorType,
                            actor,
                            note
                    );

                    history.addLast(rec);

                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Skipping invalid expansion history entry: " + hKey, ex);
                }
            }
        }

        // prune + cap
        if (isAuditEnabled()) {
            pruneHistory();
        } else {
            history.clear();
        }

        setDirty(false);
    }

    public synchronized void save() {
        if (data == null) return;

        data.set("requests", null);

        // Persist pending requests only
        for (ExpansionRequest req : activeRequests.values()) {
            if (!req.isPending()) continue;

            String path = "requests." + req.getRequester();

            data.set(path + ".owner", req.getPlotOwner().toString());
            data.set(path + ".plotId", req.getPlotId().toString());
            data.set(path + ".world", req.getWorldName());

            data.set(path + ".currentRadius", req.getCurrentRadius());
            data.set(path + ".requestedRadius", req.getRequestedRadius());
            data.set(path + ".cost", req.getCost());

            data.set(path + ".timestamp", req.getTimestamp());
            data.set(path + ".status", req.getStatus().name());
            data.set(path + ".decisionTimestamp", req.getDecisionTimestamp());
        }

        // Persist audit history (optional)
        if (isAuditEnabled() && auditSaveToFile()) {
            data.set("history", null);

            // Write oldest -> newest
            for (DecisionRecord rec : history) {
                String path = "history." + rec.getKey();

                data.set(path + ".requester", rec.getRequester().toString());
                data.set(path + ".owner", rec.getPlotOwner().toString());
                data.set(path + ".plotId", rec.getPlotId().toString());
                data.set(path + ".world", rec.getWorldName());

                data.set(path + ".currentRadius", rec.getCurrentRadius());
                data.set(path + ".requestedRadius", rec.getRequestedRadius());
                data.set(path + ".cost", rec.getCost());

                data.set(path + ".timestamp", rec.getTimestamp());
                data.set(path + ".status", rec.getStatus().name());
                data.set(path + ".decisionTimestamp", rec.getDecisionTimestamp());

                data.set(path + ".actorType", rec.getActorType().name());
                data.set(path + ".actor", rec.getActor() == null ? "" : rec.getActor().toString());
                data.set(path + ".note", rec.getNote());
            }
        } else {
            // Keep file clean if audit disabled
            data.set("history", null);
        }

        try {
            data.save(file);
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save expansion-requests.yml", e);
        }
    }
}
