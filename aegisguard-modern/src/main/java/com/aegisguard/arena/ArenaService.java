package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import com.aegisguard.arena.preset.LavaDungeonPreset;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Main Arena module facade: definitions, parties, runs, inventory, rewards, boards, crash recovery.
 */
public final class ArenaService {

    private final AegisGuard plugin;
    private final ArenaKeys keys;
    private final ArenaInventoryService inventoryService;
    private final ArenaPersistenceQueue persistenceQueue;
    private final ArenaScheduler scheduler;
    private final ArenaRarityGate rarityGate = ArenaRarityGate.defaults();
    /** Placeholders for the most recent failure key returned by this service (cleared on take). */
    private final ThreadLocal<Map<String, String>> failVars = ThreadLocal.withInitial(HashMap::new);

    private final Map<String, ArenaDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaRun> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> runsByArenaId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToRun = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaParty> parties = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaPendingRecovery> pendingRecoveries = new ConcurrentHashMap<>();
    private final ArenaRewardLedger rewardLedger = new ArenaRewardLedger();
    private final ArenaLeaderboard leaderboard = new ArenaLeaderboard();
    private final Set<UUID> teleportAllow = ConcurrentHashMap.newKeySet();

    private final File arenasFile;
    private final File runsFile;
    private final File statsFile;
    private final File rewardsFile;
    private final File pendingFile;

    private volatile boolean dirty;
    private volatile boolean dirtyStats;
    private volatile boolean dirtyRewards;
    private volatile boolean dirtyPending;
    /** Folia without required schedulers → effective module off. */
    private volatile boolean schedulerCapabilityBlocked;

    public ArenaService(AegisGuard plugin) {
        this.plugin = plugin;
        this.keys = new ArenaKeys(plugin);
        this.inventoryService = new ArenaInventoryService(plugin);
        this.scheduler = new ArenaScheduler(plugin);
        this.persistenceQueue = new ArenaPersistenceQueue(
                plugin.getLogger(), plugin.getDataFolder(),
                Math.max(1, plugin.getConfig().getInt("arena.persistence.backup_count", 5)));
        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        this.runsFile = new File(plugin.getDataFolder(), "arena-runs.yml");
        this.statsFile = new File(plugin.getDataFolder(), "arena-stats.yml");
        this.rewardsFile = new File(plugin.getDataFolder(), "arena-rewards.yml");
        this.pendingFile = new File(plugin.getDataFolder(), "arena-pending-recovery.yml");
        probeSchedulerCapabilities();
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public AegisGuard plugin() { return plugin; }
    public ArenaKeys keys() { return keys; }
    public ArenaInventoryService inventory() { return inventoryService; }
    public ArenaPersistenceQueue persistence() { return persistenceQueue; }
    public ArenaScheduler scheduler() { return scheduler; }
    public ArenaRewardLedger rewards() { return rewardLedger; }
    public ArenaLeaderboard leaderboard() { return leaderboard; }
    public ArenaRarityGate rarityGate() { return rarityGate; }

    /** Consume placeholders attached to the last failure key returned by this thread. */
    public Map<String, String> takeFailVars() {
        Map<String, String> vars = failVars.get();
        failVars.set(new HashMap<>());
        if (vars == null || vars.isEmpty()) return Map.of();
        return Map.copyOf(vars);
    }

    private String fail(String key) {
        failVars.set(new HashMap<>());
        return key;
    }

    private String fail(String key, String k1, String v1) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1 == null ? "" : v1);
        failVars.set(m);
        return key;
    }

    private String fail(String key, String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>();
        m.put(k1, v1 == null ? "" : v1);
        m.put(k2, v2 == null ? "" : v2);
        failVars.set(m);
        return key;
    }

    public boolean isEnabled() {
        if (schedulerCapabilityBlocked) return false;
        return plugin.getConfig().getBoolean("arena.enabled", true);
    }

    private void probeSchedulerCapabilities() {
        if (scheduler.hasRequiredCapabilities()) {
            schedulerCapabilityBlocked = false;
            plugin.getLogger().info("[Arena] Scheduler path: " + scheduler.pathName()
                    + (scheduler.isFolia() ? " (entity/region/global/async)" : " (main-thread inline)"));
            return;
        }
        schedulerCapabilityBlocked = true;
        plugin.getLogger().severe("[Arena] Folia detected but required schedulers are unavailable; "
                + "arena.enabled is effective-off until Folia-safe APIs are present.");
    }

    public long disconnectGraceMillis() {
        return Math.max(5L, plugin.getConfig().getLong("arena.disconnect_grace_seconds", 60L)) * 1000L;
    }

    public int globalMaxActiveRuns() {
        return Math.max(1, plugin.getConfig().getInt("arena.limits.max_active_runs",
                plugin.getConfig().getInt("arena.max_concurrent_runs", 8)));
    }

    public int defaultMaxActiveRunsPerArena() {
        return Math.max(1, plugin.getConfig().getInt("arena.defaults.max_active_runs_per_arena", 1));
    }

    public int partyMaxSize() {
        return Math.max(1, plugin.getConfig().getInt("arena.party.default_max_players",
                plugin.getConfig().getInt("arena.party.max_size", 4)));
    }

    public int partyInviteExpireSeconds() {
        return Math.max(10, plugin.getConfig().getInt("arena.party.invite_expire_seconds", 60));
    }

    public int leaderboardTopN() {
        return Math.max(5, plugin.getConfig().getInt("arena.leaderboard.top_n", 10));
    }

    public boolean grantTeleportAllow(UUID playerId) {
        return playerId != null && teleportAllow.add(playerId);
    }

    public boolean consumeTeleportAllow(UUID playerId) {
        return playerId != null && teleportAllow.remove(playerId);
    }

    public boolean hasTeleportAllow(UUID playerId) {
        return playerId != null && teleportAllow.contains(playerId);
    }

    // ------------------------------------------------------------------
    // Load / save
    // ------------------------------------------------------------------

    public synchronized void load() {
        loadDefinitions();
        loadLeaderboard();
        loadRewards();
        loadPendingRecoveries();
        loadActiveRunsJournal();
        dirty = false;
        dirtyStats = false;
        dirtyRewards = false;
        dirtyPending = false;
    }

    public synchronized void save() {
        saveDefinitions();
        saveLeaderboard();
        saveRewards();
        savePendingRecoveries();
        saveActiveRunsJournal();
        dirty = false;
        dirtyStats = false;
        dirtyRewards = false;
        dirtyPending = false;
    }

    public boolean isDirty() {
        return dirty || dirtyStats || dirtyRewards || dirtyPending;
    }

    public void markDirty() { dirty = true; }

    private void loadDefinitions() {
        definitions.clear();
        if (!arenasFile.exists()) return;
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(arenasFile);
            ConfigurationSection root = data.getConfigurationSection("arenas");
            if (root == null) return;
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                ArenaDefinition def = deserializeDefinition(id, sec);
                def.revalidate();
                definitions.put(def.getId(), def);
            }
            plugin.getLogger().info("[Arena] Loaded " + definitions.size() + " arena definition(s).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to load arenas.yml", e);
        }
    }

    private void saveDefinitions() {
        try {
            YamlConfiguration out = new YamlConfiguration();
            for (ArenaDefinition def : definitions.values()) {
                String base = "arenas." + def.getId();
                serializeDefinition(out, base, def);
            }
            persistenceQueue.saveYamlAtomic(arenasFile, out, check -> {
                if (!check.contains("arenas") && !definitions.isEmpty()) {
                    throw new IllegalStateException("arenas.yml missing arenas root");
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to save arenas.yml", e);
        }
    }

    private void loadLeaderboard() {
        if (!statsFile.exists()) return;
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(statsFile);
            ConfigurationSection boards = data.getConfigurationSection("boards");
            if (boards == null) return;
            for (String key : boards.getKeys(false)) {
                List<?> raw = boards.getList(key);
                if (raw == null) continue;
                List<ArenaLeaderboardRecord> list = new ArrayList<>();
                for (Object o : raw) {
                    if (!(o instanceof Map<?, ?> map)) continue;
                    ArenaLeaderboardRecord rec = deserializeBoardRecord(map);
                    if (rec != null) list.add(rec);
                }
                leaderboard.putAll(key, list);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to load arena-stats.yml", e);
        }
    }

    private void saveLeaderboard() {
        try {
            YamlConfiguration out = new YamlConfiguration();
            for (Map.Entry<String, List<ArenaLeaderboardRecord>> e : leaderboard.asMap().entrySet()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (ArenaLeaderboardRecord r : e.getValue()) {
                    rows.add(serializeBoardRecord(r));
                }
                out.set("boards." + e.getKey(), rows);
            }
            persistenceQueue.saveYamlAtomic(statsFile, out, null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to save arena-stats.yml", e);
        }
    }

    private void loadRewards() {
        if (!rewardsFile.exists()) return;
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(rewardsFile);
            ConfigurationSection root = data.getConfigurationSection("entries");
            if (root == null) return;
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                UUID runId = parseUuid(sec.getString("runId"));
                UUID playerId = parseUuid(sec.getString("playerId"));
                String rewardKey = sec.getString("rewardKey", "default");
                if (runId == null || playerId == null) continue;
                ArenaRewardEntry entry = new ArenaRewardEntry(runId, playerId, rewardKey);
                try {
                    entry.setStatus(ArenaRewardStatus.valueOf(sec.getString("status", "PENDING")));
                } catch (IllegalArgumentException ignored) {
                    entry.setStatus(ArenaRewardStatus.NEEDS_REVIEW);
                }
                entry.setDetail(sec.getString("detail"));
                rewardLedger.put(entry);
            }
            rewardLedger.sanitizeAfterLoad();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to load arena-rewards.yml", e);
        }
    }

    private void saveRewards() {
        try {
            YamlConfiguration out = new YamlConfiguration();
            for (ArenaRewardEntry e : rewardLedger.all()) {
                String base = "entries." + e.getEntryId().replace(':', '_');
                out.set(base + ".entryId", e.getEntryId());
                out.set(base + ".runId", e.getRunId().toString());
                out.set(base + ".playerId", e.getPlayerId().toString());
                out.set(base + ".rewardKey", e.getRewardKey());
                out.set(base + ".status", e.getStatus().name());
                out.set(base + ".detail", e.getDetail());
                out.set(base + ".updatedAt", e.getUpdatedAt());
            }
            persistenceQueue.saveYamlAtomic(rewardsFile, out, null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to save arena-rewards.yml", e);
        }
    }

    private void loadPendingRecoveries() {
        pendingRecoveries.clear();
        if (!pendingFile.exists()) return;
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(pendingFile);
            ConfigurationSection root = data.getConfigurationSection("pending");
            if (root == null) return;
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                UUID playerId = parseUuid(sec.getString("playerId"));
                UUID runId = parseUuid(sec.getString("runId"));
                String path = sec.getString("snapshotPath");
                if (playerId == null || runId == null || path == null) continue;
                ArenaSpawnPoint lobby = readSpawn(sec.getConfigurationSection("lobby"));
                ArenaPendingRecovery rec = new ArenaPendingRecovery(playerId, runId, path, lobby);
                if ("COMPLETE".equalsIgnoreCase(sec.getString("status"))) {
                    rec.setStatus(ArenaPendingRecovery.Status.COMPLETE);
                }
                rec.setAppliedToken(sec.getString("appliedToken"));
                pendingRecoveries.put(playerId, rec);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to load arena-pending-recovery.yml", e);
        }
    }

    private void savePendingRecoveries() {
        try {
            YamlConfiguration out = new YamlConfiguration();
            for (ArenaPendingRecovery rec : pendingRecoveries.values()) {
                String base = "pending." + rec.getPlayerId();
                out.set(base + ".playerId", rec.getPlayerId().toString());
                out.set(base + ".runId", rec.getRunId().toString());
                out.set(base + ".snapshotPath", rec.getSnapshotPath());
                out.set(base + ".status", rec.getStatus().name());
                out.set(base + ".appliedToken", rec.getAppliedToken());
                writeSpawn(out, base + ".lobby", rec.getLobbyDestination());
            }
            persistenceQueue.saveYamlAtomic(pendingFile, out, null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to save arena-pending-recovery.yml", e);
        }
    }

    /** Active runs journal is mainly for crash recovery; cleared after cleanup. */
    private void loadActiveRunsJournal() {
        if (!runsFile.exists()) return;
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(runsFile);
            ConfigurationSection root = data.getConfigurationSection("runs");
            if (root == null) return;
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                UUID runId = parseUuid(sec.getString("runId", key));
                String arenaId = sec.getString("arenaId");
                if (runId == null || arenaId == null) continue;
                ArenaMode mode = ArenaMode.PVE_WAVES;
                try {
                    mode = ArenaMode.valueOf(sec.getString("mode", "PVE_WAVES"));
                } catch (IllegalArgumentException ignored) {}
                UUID leader = parseUuid(sec.getString("leaderId"));
                ArenaRun run = new ArenaRun(runId, arenaId, mode, leader);
                try {
                    run.setState(ArenaRunState.valueOf(sec.getString("state", "WAVE")));
                } catch (IllegalArgumentException ignored) {
                    run.setState(ArenaRunState.WAVE);
                }
                run.setWaveIndex(sec.getInt("waveIndex", 0));
                run.setStartedAt(sec.getLong("startedAt", System.currentTimeMillis()));
                ConfigurationSection parts = sec.getConfigurationSection("participants");
                if (parts != null) {
                    for (String pid : parts.getKeys(false)) {
                        UUID playerId = parseUuid(pid);
                        if (playerId == null) continue;
                        ArenaParticipant p = run.getOrCreate(playerId);
                        ConfigurationSection ps = parts.getConfigurationSection(pid);
                        if (ps == null) continue;
                        p.setSnapshotPath(ps.getString("snapshotPath"));
                        try {
                            p.setState(ParticipantState.valueOf(ps.getString("state", "FIGHTING")));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                activeRuns.put(runId, run);
                runsByArenaId.computeIfAbsent(arenaId, k -> ConcurrentHashMap.newKeySet()).add(runId);
                for (UUID pid : run.memberIds()) {
                    playerToRun.put(pid, runId);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to load arena-runs.yml", e);
        }
    }

    private void saveActiveRunsJournal() {
        try {
            YamlConfiguration out = new YamlConfiguration();
            for (ArenaRun run : activeRuns.values()) {
                if (run.getState() == ArenaRunState.CLOSED || run.isCleanupDone()) continue;
                String base = "runs." + run.getRunId();
                out.set(base + ".runId", run.getRunId().toString());
                out.set(base + ".arenaId", run.getArenaId());
                out.set(base + ".mode", run.getMode().name());
                out.set(base + ".state", run.getState().name());
                out.set(base + ".leaderId", run.getLeaderId() == null ? null : run.getLeaderId().toString());
                out.set(base + ".waveIndex", run.getWaveIndex());
                out.set(base + ".startedAt", run.getStartedAt());
                if (run.getEndReason() != null) out.set(base + ".endReason", run.getEndReason().name());
                for (ArenaParticipant p : run.getParticipants().values()) {
                    String pb = base + ".participants." + p.getPlayerId();
                    out.set(pb + ".state", p.getState().name());
                    out.set(pb + ".snapshotPath", p.getSnapshotPath());
                }
            }
            persistenceQueue.saveYamlAtomic(runsFile, out, null);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Failed to save arena-runs.yml", e);
        }
    }

    private void flushJournalAsync() {
        dirty = true;
        persistenceQueue.enqueue(this::saveActiveRunsJournal);
    }

    // ------------------------------------------------------------------
    // Definitions
    // ------------------------------------------------------------------

    public ArenaDefinition createArena(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        String key = id.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (definitions.containsKey(key)) {
            return definitions.get(key);
        }
        ArenaDefinition def = new ArenaDefinition(key);
        def.setMaxActiveRuns(defaultMaxActiveRunsPerArena());
        def.revalidate();
        definitions.put(def.getId(), def);
        dirty = true;
        saveDefinitions();
        return def;
    }

    public ArenaDefinition getArena(String id) {
        if (id == null) return null;
        return definitions.get(id.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    public Collection<ArenaDefinition> allArenas() {
        List<ArenaDefinition> list = new ArrayList<>(definitions.values());
        list.sort(Comparator.comparing(ArenaDefinition::getId));
        return list;
    }

    public void saveDefinition(ArenaDefinition def) {
        if (def == null) return;
        def.revalidate();
        definitions.put(def.getId(), def);
        dirty = true;
        saveDefinitions();
    }

    public ArenaDefinition applyLavaPreset(String arenaId) {
        ArenaDefinition existing = getArena(arenaId);
        if (existing != null) {
            LavaDungeonPreset.applyPresetWaves(existing);
            saveDefinition(existing);
            return existing;
        }
        ArenaDefinition created = LavaDungeonPreset.createDefinition(arenaId);
        definitions.put(created.getId(), created);
        dirty = true;
        saveDefinitions();
        return created;
    }

    // ------------------------------------------------------------------
    // Party
    // ------------------------------------------------------------------

    public ArenaParty getParty(UUID playerId) {
        return playerId == null ? null : parties.get(playerId);
    }

    public ArenaParty createParty(Player leader) {
        if (leader == null) return null;
        leaveParty(leader);
        ArenaParty party = new ArenaParty(leader.getUniqueId());
        parties.put(leader.getUniqueId(), party);
        return party;
    }

    public String invite(Player leader, Player target) {
        if (leader == null || target == null) return fail("arena_invalid_players");
        if (leader.getUniqueId().equals(target.getUniqueId())) return fail("arena_cannot_invite_self");
        ArenaParty party = parties.get(leader.getUniqueId());
        if (party == null) party = createParty(leader);
        if (!party.isLeader(leader.getUniqueId())) return fail("arena_party_leader_only_invite");
        if (parties.containsKey(target.getUniqueId()) && parties.get(target.getUniqueId()) != party) {
            return fail("arena_target_in_party", "PLAYER", target.getName());
        }
        if (playerToRun.containsKey(target.getUniqueId())) {
            return fail("arena_target_in_run", "PLAYER", target.getName());
        }
        int max = partyMaxSize();
        if (party.size() >= max) return fail("arena_party_full");
        party.invite(target.getUniqueId(), System.currentTimeMillis() + partyInviteExpireSeconds() * 1000L);
        return null;
    }

    public String accept(Player player) {
        if (player == null) return fail("arena_invalid_player");
        if (playerToRun.containsKey(player.getUniqueId())) return fail("arena_already_in_run");
        ArenaParty existing = parties.get(player.getUniqueId());
        if (existing != null) return fail("arena_already_in_party");
        for (ArenaParty party : new ArrayList<>(parties.values())) {
            party.purgeExpiredInvites();
            if (!party.hasInvite(player.getUniqueId())) continue;
            if (!party.acceptInvite(player.getUniqueId())) return fail("arena_invite_expired");
            parties.put(player.getUniqueId(), party);
            return null;
        }
        return fail("arena_no_pending_invite");
    }

    public String decline(Player player) {
        if (player == null) return fail("arena_invalid_player");
        for (ArenaParty party : parties.values()) {
            party.declineInvite(player.getUniqueId());
        }
        return null;
    }

    public String leaveParty(Player player) {
        if (player == null) return fail("arena_invalid_player");
        ArenaParty party = parties.get(player.getUniqueId());
        if (party == null) {
            parties.remove(player.getUniqueId());
            return null;
        }
        party.removeMember(player.getUniqueId());
        parties.remove(player.getUniqueId());
        if (party.size() == 0) {
            for (UUID m : new ArrayList<>(party.getMembers())) parties.remove(m);
        } else {
            // Keep remaining members mapped
            for (UUID m : party.getMembers()) parties.put(m, party);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Start
    // ------------------------------------------------------------------

    /**
     * @return null on success, otherwise a lang key (see {@link #takeFailVars()})
     */
    public String tryStart(Player leader, String arenaId) {
        if (!isEnabled()) return fail("arena_disabled");
        if (leader == null) return fail("arena_invalid_leader");
        ArenaDefinition def = getArena(arenaId);
        if (def == null) return fail("arena_unknown");
        if (!def.isEnabledFlag()) return fail("arena_arena_disabled_flag");
        def.revalidate();
        if (!def.isConfigValid()) {
            return fail("arena_not_ready", "DETAIL",
                    def.getConfigError() == null ? "" : def.getConfigError());
        }
        if (!def.isEnabled()) return fail("arena_not_enabled");

        if (countActiveRuns(def.getId()) >= def.getMaxActiveRuns()) {
            return def.getMaxActiveRuns() <= 1
                    ? fail("arena_busy")
                    : fail("arena_max_active_runs");
        }
        if (activeRuns.size() >= globalMaxActiveRuns()) {
            return fail("arena_global_run_limit");
        }

        ArenaParty party = parties.get(leader.getUniqueId());
        if (party == null) party = createParty(leader);
        if (!party.isLeader(leader.getUniqueId())) return fail("arena_party_leader_only_start");

        int size = party.size();
        if (size < def.getMinPlayers()) {
            return fail("arena_need_min_players", "MIN", String.valueOf(def.getMinPlayers()));
        }
        if (size > def.getMaxPlayers()) {
            return fail("arena_party_exceeds_max", "MAX", String.valueOf(def.getMaxPlayers()));
        }

        for (UUID mid : party.getMembers()) {
            if (playerToRun.containsKey(mid)) return fail("arena_member_in_run");
            Player online = Bukkit.getPlayer(mid);
            if (online == null || !online.isOnline()) return fail("arena_members_must_be_online");
        }

        UUID runId = UUID.randomUUID();
        ArenaRun run = new ArenaRun(runId, def.getId(), def.getMode(), leader.getUniqueId());
        run.setStartFighterCount(size);
        run.setLockedScale(def.getScaling().forPartySize(size));
        run.setWaveIndex(0);
        run.journal("start partySize=" + size);

        List<Player> onlineMembers = new ArrayList<>();
        for (UUID mid : party.getMembers()) {
            Player p = Bukkit.getPlayer(mid);
            if (p == null) return fail("arena_member_offline");
            onlineMembers.add(p);
            ArenaParticipant part = run.getOrCreate(mid);
            part.setState(ParticipantState.FIGHTING);
        }

        // Inventory snapshot then clear (SAVE_AND_RESTORE / ARENA_LOADOUT)
        ArenaInventoryPolicy policy = def.getInventoryPolicy();
        Location entry = toLocation(def.getEntrySpawn());
        if (scheduler.isFolia()) {
            return beginRunFolia(def, run, runId, onlineMembers, policy, entry);
        }

        if (policy.isProtectedInventory()) {
            for (Player p : onlineMembers) {
                try {
                    String path = inventoryService.saveSnapshotAtomic(p, runId, persistenceQueue);
                    ArenaParticipant part = run.getParticipant(p.getUniqueId());
                    part.setSnapshotPath(path);
                    inventoryService.clearPlayer(p);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Arena] Snapshot failed for " + p.getName(), e);
                    // Rollback any already-cleared players
                    for (Player restored : onlineMembers) {
                        ArenaParticipant rp = run.getParticipant(restored.getUniqueId());
                        if (rp != null && rp.getSnapshotPath() != null && !rp.isSnapshotRestored()) {
                            inventoryService.restoreFromFile(restored, rp.getSnapshotPath());
                            rp.setSnapshotRestored(true);
                        }
                    }
                    return fail("arena_snapshot_failed", "PLAYER", p.getName());
                }
            }
        }

        for (Player p : onlineMembers) {
            playerToRun.put(p.getUniqueId(), runId);
            teleportAllowed(p, entry);
        }

        activeRuns.put(runId, run);
        runsByArenaId.computeIfAbsent(def.getId(), k -> ConcurrentHashMap.newKeySet()).add(runId);
        run.setState(ArenaRunState.COUNTDOWN);
        run.setState(ArenaRunState.WAVE);
        spawnCurrentWave(run);
        notifyWave(run, def);
        flushJournalAsync();
        return null;
    }

    /**
     * Folia party start: snapshot/teleport each member on their entity scheduler without
     * waiting across regions from the caller's region thread.
     */
    private String beginRunFolia(ArenaDefinition def, ArenaRun run, UUID runId,
                                 List<Player> onlineMembers, ArenaInventoryPolicy policy, Location entry) {
        activeRuns.put(runId, run);
        runsByArenaId.computeIfAbsent(def.getId(), k -> ConcurrentHashMap.newKeySet()).add(runId);
        run.setState(ArenaRunState.COUNTDOWN);
        for (Player p : onlineMembers) {
            playerToRun.put(p.getUniqueId(), runId);
        }

        AtomicInteger remaining = new AtomicInteger(onlineMembers.size());
        AtomicBoolean failed = new AtomicBoolean(false);

        for (Player p : onlineMembers) {
            scheduler.runForEntity(p, () -> {
                if (failed.get() || run.isCleanupDone()) return;
                try {
                    if (policy.isProtectedInventory()) {
                        String path = inventoryService.saveSnapshotAtomic(p, runId, persistenceQueue);
                        ArenaParticipant part = run.getParticipant(p.getUniqueId());
                        if (part != null) {
                            part.setSnapshotPath(path);
                        }
                        inventoryService.clearPlayer(p);
                    }
                    teleportAllowedInline(p, entry);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Arena] Folia start failed for " + p.getName(), e);
                    if (failed.compareAndSet(false, true)) {
                        scheduler.runGlobal(() -> endRun(run, ArenaEndReason.ADMIN_ABORT));
                    }
                    return;
                }
                if (remaining.decrementAndGet() == 0 && !failed.get()) {
                    scheduler.runGlobal(() -> {
                        if (run.isCleanupDone() || failed.get()) return;
                        run.setState(ArenaRunState.WAVE);
                        spawnCurrentWave(run);
                        notifyWave(run, def);
                        flushJournalAsync();
                    });
                }
            });
        }
        flushJournalAsync();
        return null;
    }

    private void teleportAllowed(Player player, Location loc) {
        if (player == null || loc == null) return;
        scheduler.runForEntity(player, () -> teleportAllowedInline(player, loc));
    }

    /** Public teleport helper for commands/GUIs (allow-token + entity scheduler). */
    public void teleportPlayerAllowed(Player player, Location loc) {
        teleportAllowed(player, loc);
    }

    private void teleportAllowedInline(Player player, Location loc) {
        if (player == null || loc == null) return;
        grantTeleportAllow(player.getUniqueId());
        com.aegisguard.util.TeleportUtil.safeTeleport(plugin, player, loc)
                .whenComplete((ok, error) -> consumeTeleportAllow(player.getUniqueId()));
    }

    public int countActiveRuns(String arenaId) {
        if (arenaId == null) return 0;
        Set<UUID> set = runsByArenaId.get(arenaId.toLowerCase(Locale.ROOT));
        if (set == null) return 0;
        int n = 0;
        for (UUID id : set) {
            ArenaRun run = activeRuns.get(id);
            if (run != null && !run.isCleanupDone() && !run.getState().isTerminal()
                    && run.getState() != ArenaRunState.CLEANUP) {
                n++;
            }
        }
        return n;
    }

    public ArenaRun getRun(UUID runId) {
        return runId == null ? null : activeRuns.get(runId);
    }

    public ArenaRun getRunForPlayer(UUID playerId) {
        UUID runId = playerId == null ? null : playerToRun.get(playerId);
        return getRun(runId);
    }

    public Collection<ArenaRun> allActiveRuns() {
        return List.copyOf(activeRuns.values());
    }

    // ------------------------------------------------------------------
    // Waves
    // ------------------------------------------------------------------

    /**
     * Advance to the next wave index and return the rolled rarity for that wave (shared seed).
     */
    public ArenaRarity advanceWave(ArenaRun run) {
        if (run == null) return ArenaRarity.COMMON;
        ArenaDefinition def = getArena(run.getArenaId());
        int next = run.getWaveIndex() + 1;
        run.setWaveIndex(next);
        ArenaWaveSpec spec = currentWaveSpec(def, run);
        ArenaRarity max = spec == null ? ArenaRarity.COMMON : spec.maxRarity();
        int seed = (int) (run.getRunSeed() ^ (next * 31L));
        ArenaRarity rolled = rarityGate.roll(max, seed);
        run.journal("wave=" + next + " rarity=" + rolled);
        if (spec != null && spec.isBoss()) {
            run.setState(ArenaRunState.MILESTONE_BOSS);
        } else {
            run.setState(ArenaRunState.WAVE);
        }
        spawnCurrentWave(run);
        flushJournalAsync();
        notifyWave(run, def);
        return rolled;
    }

    private void notifyWave(ArenaRun run, ArenaDefinition def) {
        if (run == null) return;
        ArenaWaveSpec spec = currentWaveSpec(def, run);
        boolean boss = spec != null && spec.isBoss();
        String difficulty = run.getLockedScale() == null ? "1.0x"
                : String.format(Locale.ROOT, "%.2fx", run.getLockedScale().mobHealth);
        String wave = String.valueOf(run.getWaveIndex() + 1);
        for (ArenaParticipant part : run.getParticipants().values()) {
            if (part.getState() != ParticipantState.FIGHTING) continue;
            Player p = Bukkit.getPlayer(part.getPlayerId());
            if (p == null || !p.isOnline()) continue;
            if (boss) {
                plugin.msg().send(p, "arena_boss_wave", Map.of("WAVE", wave));
            } else {
                plugin.msg().send(p, "arena_wave_started", Map.of(
                        "WAVE", wave,
                        "DIFFICULTY", difficulty));
            }
        }
    }

    /**
     * Spawn the mobs for the run's current wave index. Idempotent per wave index.
     */
    public int spawnCurrentWave(ArenaRun run) {
        if (run == null || run.isCleanupDone()) return 0;
        if (run.getState().isTerminal() || run.getState() == ArenaRunState.CLEANUP) return 0;
        if (run.getSpawnedWaveIndex() >= run.getWaveIndex()) return 0;

        ArenaDefinition def = getArena(run.getArenaId());
        if (def == null) return 0;
        ArenaWaveSpec spec = currentWaveSpec(def, run);
        if (spec == null) {
            run.setSpawnedWaveIndex(run.getWaveIndex());
            return 0;
        }

        int desired = scaledMobCount(spec, run.getLockedScale());
        int globalCap = Math.max(1, plugin.getConfig().getInt("arena.limits.max_mobs_per_run", 64));
        int cap = Math.min(def.getMaxActiveMobs(), globalCap);
        int room = Math.max(0, cap - run.getActiveMobIds().size());
        int toSpawn = Math.min(desired, room);
        if (toSpawn <= 0) {
            run.setSpawnedWaveIndex(run.getWaveIndex());
            run.journal("spawn_skipped wave=" + run.getWaveIndex() + " room=0");
            return 0;
        }

        List<ArenaSpawnPoint> points = def.getMobSpawns();
        if (points.isEmpty()) {
            run.setSpawnedWaveIndex(run.getWaveIndex());
            run.journal("spawn_failed no_mob_spawns");
            return 0;
        }

        List<String> templates = spec.mobTemplateIds().isEmpty()
                ? List.of("zombie") : spec.mobTemplateIds();
        boolean boss = spec.isBoss();
        double healthMult = boss ? run.getLockedScale().bossHealth : run.getLockedScale().mobHealth;
        int spawned = 0;

        for (int i = 0; i < toSpawn; i++) {
            ArenaSpawnPoint point = points.get(i % points.size());
            Location loc = toLocation(point);
            if (loc == null || loc.getWorld() == null) continue;
            String template = templates.get(i % templates.size());
            EntityType type = resolveMobType(template);
            final int index = i;
            spawnAtLocation(loc, () -> {
                if (run.isCleanupDone() || run.getState().isTerminal()) return;
                try {
                    Entity entity = loc.getWorld().spawnEntity(loc, type);
                    if (!(entity instanceof LivingEntity living)) {
                        entity.remove();
                        return;
                    }
                    keys.tagEntity(living.getPersistentDataContainer(), run.getRunId(), run.getArenaId(), boss);
                    if (boss) {
                        living.setCustomName(plugin.msg().get("arena_mob_boss_name"));
                        living.setCustomNameVisible(true);
                    } else {
                        living.setCustomName(plugin.msg().get("arena_mob_name"));
                        living.setCustomNameVisible(false);
                    }
                    applyHealthScale(living, healthMult);
                    run.getActiveMobIds().add(living.getUniqueId());
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Arena] Failed to spawn wave mob #" + index + " for run " + run.getRunId(), t);
                }
            });
            spawned++;
        }

        run.setSpawnedWaveIndex(run.getWaveIndex());
        run.journal("spawned wave=" + run.getWaveIndex() + " count=" + spawned
                + (boss ? " boss=true" : ""));
        return spawned;
    }

    /** Package-visible for unit tests: apply party scale + boss count rules. */
    static int scaledMobCount(ArenaWaveSpec spec, ArenaScalingTable.Row scale) {
        if (spec == null) return 0;
        ArenaScalingTable.Row row = scale == null ? ArenaScalingTable.Row.identity() : scale;
        if (spec.isBoss()) {
            return Math.max(1, spec.mobCount());
        }
        return Math.max(1, (int) Math.ceil(spec.mobCount() * row.mobCount));
    }

    static EntityType resolveMobType(String template) {
        if (template == null || template.isBlank()) return EntityType.ZOMBIE;
        String key = template.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            EntityType type = EntityType.valueOf(key);
            if (type.isAlive()) return type;
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return EntityType.ZOMBIE;
    }

    private void spawnAtLocation(Location loc, Runnable task) {
        scheduler.runAtLocation(loc, task);
    }

    private static void applyHealthScale(LivingEntity living, double mult) {
        if (living == null || mult <= 0.0D || Math.abs(mult - 1.0D) < 0.001D) return;
        try {
            AttributeInstance max = living.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max == null) return;
            double next = Math.max(1.0D, max.getBaseValue() * mult);
            max.setBaseValue(next);
            living.setHealth(Math.min(next, max.getValue()));
        } catch (Throwable ignored) {
            // Attribute API variance across versions
        }
    }

    public ArenaWaveSpec currentWaveSpec(ArenaDefinition def, ArenaRun run) {
        if (def == null || run == null) return null;
        List<ArenaWaveSpec> waves = def.getWaves();
        if (waves.isEmpty()) return null;
        int idx = Math.min(run.getWaveIndex(), waves.size() - 1);
        return waves.get(Math.max(0, idx));
    }

    public boolean isFinalWaveCleared(ArenaRun run) {
        ArenaDefinition def = run == null ? null : getArena(run.getArenaId());
        if (def == null) return false;
        return run.getWaveIndex() >= def.getWaves().size() - 1 && run.getActiveMobIds().isEmpty();
    }

    // ------------------------------------------------------------------
    // Elimination / totem / disconnect
    // ------------------------------------------------------------------

    public ArenaTotemPolicy resolveTotemPolicy(ArenaDefinition def) {
        if (def == null) return ArenaTotemPolicy.CONSUME_AND_ELIMINATE;
        return def.getTotemPolicy() == null ? ArenaTotemPolicy.CONSUME_AND_ELIMINATE : def.getTotemPolicy();
    }

    /**
     * Idempotent elimination. Does not restore inventory (that happens at CLEANUP).
     */
    public void eliminate(Player player, ArenaRun run, String reason) {
        if (player == null || run == null) return;
        ArenaParticipant part = run.getParticipant(player.getUniqueId());
        if (part == null) return;
        if (part.isEliminatedHandled()) return;
        part.setEliminatedHandled(true);
        part.setState(ParticipantState.ELIMINATED);
        part.addElimination();
        scheduler.runForEntity(player, () -> {
            stripEffects(player);
            if (!"lethal_consume_totem".equals(reason) && !"leave".equals(reason)) {
                plugin.msg().send(player, "arena_eliminated");
            }
        });
        run.journal("eliminate " + player.getUniqueId() + " reason=" + reason);
        if (run.countFighting() == 0) {
            endRun(run, ArenaEndReason.WIPE);
        }
    }

    /**
     * Apply totem policy on lethal damage. Returns true if death path should be cancelled
     * (protected eliminate path).
     */
    public boolean handleTotemThenEliminate(Player player, ArenaRun run) {
        if (player == null || run == null) return false;
        ArenaDefinition def = getArena(run.getArenaId());
        ArenaInventoryPolicy inv = def == null ? ArenaInventoryPolicy.SAVE_AND_RESTORE : def.getInventoryPolicy();
        if (!inv.isProtectedInventory()) {
            // DROP_ON_DEATH: let vanilla death proceed; still mark eliminated if wipe tracking needed
            return false;
        }
        ArenaTotemPolicy policy = resolveTotemPolicy(def);
        boolean hasTotem = hasTotem(player);
        switch (policy) {
            case ALLOW -> {
                if (hasTotem) return false; // vanilla totem save
                eliminate(player, run, "lethal");
                return true;
            }
            case DISABLE -> {
                // Prevent totem; eliminate
                eliminate(player, run, "lethal_no_totem");
                return true;
            }
            case CONSUME_AND_ELIMINATE -> {
                if (hasTotem) {
                    consumeOneTotem(player);
                    plugin.msg().send(player, "arena_totem_consumed");
                }
                eliminate(player, run, "lethal_consume_totem");
                return true;
            }
            default -> {
                eliminate(player, run, "lethal");
                return true;
            }
        }
    }

    public void handleDisconnect(Player player) {
        if (player == null) return;
        ArenaRun run = getRunForPlayer(player.getUniqueId());
        if (run == null) return;
        ArenaParticipant part = run.getParticipant(player.getUniqueId());
        if (part == null) return;
        if (part.getState() == ParticipantState.ELIMINATED) return;
        part.setState(ParticipantState.DISCONNECTED);
        part.setDisconnectedSince(System.currentTimeMillis());
        run.journal("disconnect " + player.getUniqueId());

        if (player.getUniqueId().equals(run.getLeaderId())) {
            ArenaLeadershipRules.Decision d = ArenaLeadershipRules.onLeaderDisconnect(run, System.currentTimeMillis());
            run.journal("leadership " + d.action + " " + d.reason);
        }
        flushJournalAsync();
    }

    public void handleReconnect(Player player) {
        if (player == null) return;
        // Defer +1 tick so join/entity ownership is stable on Folia and Paper.
        scheduler.runForEntityLater(player, () -> handleReconnectBody(player), 1L);
    }

    private void handleReconnectBody(Player player) {
        if (player == null || !player.isOnline()) return;
        applyPendingRecovery(player);

        ArenaRun run = getRunForPlayer(player.getUniqueId());
        if (run == null) return;
        if (run.getState().isTerminal() || run.getState() == ArenaRunState.CLEANUP || run.isCleanupDone()) {
            return;
        }
        ArenaParticipant part = run.getParticipant(player.getUniqueId());
        if (part == null) return;

        long now = System.currentTimeMillis();
        long grace = disconnectGraceMillis();
        if (player.getUniqueId().equals(run.getReservedLeaderId())
                || player.getUniqueId().equals(run.getLeaderId())) {
            ArenaLeadershipRules.Decision d =
                    ArenaLeadershipRules.onLeaderReconnectDuringGrace(run, player.getUniqueId(), now, grace);
            if (d.action == ArenaLeadershipRules.Action.RESTORE_LEADER) {
                run.setLeaderId(player.getUniqueId());
                run.setReservedLeaderId(null);
                run.setLeaderDisconnectAt(0L);
                run.journal("leadership restored " + player.getUniqueId());
            }
        }

        if (part.getState() == ParticipantState.DISCONNECTED && run.getState().isActiveCombat()) {
            // Eligible fighter restore if grace still open and not eliminated
            if (part.getDisconnectedSince() > 0 && now - part.getDisconnectedSince() <= grace) {
                part.setState(ParticipantState.FIGHTING);
                part.setDisconnectedSince(0L);
                ArenaDefinition def = getArena(run.getArenaId());
                Location entry = def == null ? null : toLocation(def.getEntrySpawn());
                teleportAllowed(player, entry);
                run.journal("reconnect fighter " + player.getUniqueId());
            } else {
                part.setState(ParticipantState.SPECTATING);
                run.journal("reconnect spectator " + player.getUniqueId());
            }
        }
        flushJournalAsync();
    }

    /** Periodic maintenance: leadership grace, disconnect wipe, party invite purge. */
    public void tickRuns() {
        if (!isEnabled()) return;
        tickLeadership();
        tickDisconnectGrace();
        for (ArenaParty party : parties.values()) {
            party.purgeExpiredInvites();
        }
    }

    public void tickLeadership() {
        long now = System.currentTimeMillis();
        long grace = disconnectGraceMillis();
        for (ArenaRun run : activeRuns.values()) {
            if (run.isCleanupDone() || run.getState().isTerminal()) continue;
            if (run.getLeaderDisconnectAt() <= 0) continue;
            ArenaLeadershipRules.Decision d = ArenaLeadershipRules.onGraceExpired(run, now, grace);
            if (d.action == ArenaLeadershipRules.Action.KEEP_RESERVED) continue;
            if (d.action == ArenaLeadershipRules.Action.END_RUN) {
                endRun(run, ArenaEndReason.WIPE);
                continue;
            }
            if (d.action == ArenaLeadershipRules.Action.TRANSFER && d.newLeaderId != null) {
                if (run.tryBeginLeadershipTransfer(d.reason)) {
                    run.completeLeadershipTransfer(d.newLeaderId);
                    flushJournalAsync();
                }
            }
        }
    }

    /**
     * After disconnect grace, mark offline fighters eliminated and wipe if none remain.
     */
    public void tickDisconnectGrace() {
        long now = System.currentTimeMillis();
        long grace = disconnectGraceMillis();
        for (ArenaRun run : new ArrayList<>(activeRuns.values())) {
            if (run.isCleanupDone() || run.getState().isTerminal() || run.getState() == ArenaRunState.CLEANUP) {
                continue;
            }
            boolean changed = false;
            for (ArenaParticipant part : run.getParticipants().values()) {
                if (part.getState() != ParticipantState.DISCONNECTED) continue;
                if (part.getDisconnectedSince() <= 0L) continue;
                if (now - part.getDisconnectedSince() <= grace) continue;
                if (part.isEliminatedHandled()) {
                    part.setState(ParticipantState.ELIMINATED);
                    continue;
                }
                part.setEliminatedHandled(true);
                part.setState(ParticipantState.ELIMINATED);
                part.addElimination();
                run.journal("grace_expired eliminate " + part.getPlayerId());
                changed = true;
            }
            if (changed) {
                flushJournalAsync();
                if (run.countFighting() == 0) {
                    endRun(run, ArenaEndReason.WIPE);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // End / cleanup / crash
    // ------------------------------------------------------------------

    public void endRun(ArenaRun run, ArenaEndReason reason) {
        if (run == null) return;
        if (run.isCleanupDone()) return;
        if (!run.markCleanupDone()) return;

        ArenaEndReason end = reason == null ? ArenaEndReason.ADMIN_ABORT : reason;
        run.setEndReason(end);
        run.setEndedAt(System.currentTimeMillis());

        switch (end) {
            case CLEAR -> run.setState(ArenaRunState.CLEAR);
            case WIPE, FORFEIT, TIMEOUT -> run.setState(ArenaRunState.WIPE);
            case CRASH_RECOVERY -> run.setState(ArenaRunState.CRASH_RECOVERY);
            default -> run.setState(ArenaRunState.ABORT);
        }
        run.setState(ArenaRunState.CLEANUP);
        run.journal("end reason=" + end);

        ArenaDefinition def = getArena(run.getArenaId());

        // Notify online participants of end outcome
        String endKey = switch (end) {
            case CLEAR -> "arena_run_cleared";
            case WIPE, TIMEOUT -> "arena_run_wiped";
            case FORFEIT -> "arena_run_forfeit";
            case CRASH_RECOVERY -> "arena_recovery_notice";
            default -> "arena_run_aborted_player";
        };
        for (ArenaParticipant part : run.getParticipants().values()) {
            Player online = Bukkit.getPlayer(part.getPlayerId());
            if (online != null && online.isOnline()) {
                plugin.msg().send(online, endKey);
            }
        }

        // Despawn tracked mobs on each entity's scheduler
        despawnTrackedMobs(run);

        // Restore inventories / pending recovery on each player's entity scheduler
        AtomicInteger pendingEntityWork = new AtomicInteger(0);
        for (ArenaParticipant part : run.getParticipants().values()) {
            Player online = Bukkit.getPlayer(part.getPlayerId());
            if (online != null && online.isOnline()) {
                pendingEntityWork.incrementAndGet();
                scheduler.runForEntity(online, () -> {
                    try {
                        restoreParticipant(online, part, def);
                    } finally {
                        pendingEntityWork.decrementAndGet();
                    }
                });
            } else if (part.getSnapshotPath() != null && !part.isSnapshotRestored()) {
                ArenaSpawnPoint lobby = def == null ? null : def.getExitSpawn();
                ArenaPendingRecovery pending = new ArenaPendingRecovery(
                        part.getPlayerId(), run.getRunId(), part.getSnapshotPath(), lobby);
                pendingRecoveries.put(part.getPlayerId(), pending);
                dirtyPending = true;
            }
            playerToRun.remove(part.getPlayerId());
        }

        // Leaderboards: only CLEAR (invalid / abort / crash never enter)
        if (end == ArenaEndReason.CLEAR) {
            updateLeaderboards(run, def);
        }

        // Rewards: CLEAR or WIPE if min wave and not crash/abort
        if ((end == ArenaEndReason.CLEAR || end == ArenaEndReason.WIPE)
                && def != null
                && run.getDeepestWave() >= def.getMinWaveForPayout()) {
            issueRewards(run, def, end);
        }

        // Folia: wait briefly for entity restore tasks without holding locks (poll off region work).
        if (scheduler.isFolia() && pendingEntityWork.get() > 0) {
            scheduler.runGlobalLater(() -> finalizeClosedRun(run), 5L);
        } else {
            finalizeClosedRun(run);
        }
    }

    private void finalizeClosedRun(ArenaRun run) {
        if (run == null) return;
        run.setState(ArenaRunState.CLOSED);
        Set<UUID> byArena = runsByArenaId.get(run.getArenaId());
        if (byArena != null) byArena.remove(run.getRunId());
        activeRuns.remove(run.getRunId());
        dirty = true;
        dirtyStats = true;
        dirtyRewards = true;
        flushJournalAsync();
        persistenceQueue.enqueue(() -> {
            saveLeaderboard();
            saveRewards();
            savePendingRecoveries();
            saveActiveRunsJournal();
        });
    }

    public void recoverIncompleteRunsOnEnable() {
        List<ArenaRun> incomplete = new ArrayList<>(activeRuns.values());
        for (ArenaRun run : incomplete) {
            if (run.isCleanupDone() || run.getState() == ArenaRunState.CLOSED) continue;
            plugin.getLogger().warning("[Arena] Crash-recovering incomplete run " + run.getRunId());
            run.setState(ArenaRunState.CRASH_RECOVERY);
            // No rewards/boards — endRun with CRASH_RECOVERY skips those
            // Force cleanup path even if markCleanupDone was somehow set
            if (run.isCleanupDone()) {
                // already cleaned; just drop
                activeRuns.remove(run.getRunId());
                continue;
            }
            endRun(run, ArenaEndReason.CRASH_RECOVERY);
        }
    }

    private void restoreParticipant(Player player, ArenaParticipant part, ArenaDefinition def) {
        if (player == null || part == null) return;
        if (part.isSnapshotRestored()) {
            inventoryService.stripArenaTaggedItems(player, keys);
            return;
        }
        if (part.getSnapshotPath() != null) {
            inventoryService.restoreFromFile(player, part.getSnapshotPath());
            part.setSnapshotRestored(true);
        }
        inventoryService.stripArenaTaggedItems(player, keys);
        Location exit = def == null ? null : toLocation(def.getExitSpawn());
        teleportAllowedInline(player, exit);
    }

    private void despawnTrackedMobs(ArenaRun run) {
        if (run == null) return;
        for (UUID mobId : new ArrayList<>(run.getActiveMobIds())) {
            Entity entity = null;
            for (World world : Bukkit.getWorlds()) {
                entity = world.getEntity(mobId);
                if (entity != null) break;
            }
            if (entity != null && entity.isValid()) {
                Entity victim = entity;
                scheduler.runForEntity(victim, () -> {
                    if (victim.isValid()) victim.remove();
                });
            }
            run.getActiveMobIds().remove(mobId);
        }
    }

    public boolean applyPendingRecovery(Player player) {
        if (player == null) return false;
        ArenaPendingRecovery pending = pendingRecoveries.get(player.getUniqueId());
        if (pending == null || !pending.isPending()) return false;
        String token = pending.getRunId() + ":" + player.getUniqueId();
        if (!pending.tryComplete(token)) return false;
        inventoryService.restoreFromFile(player, pending.getSnapshotPath());
        inventoryService.stripArenaTaggedItems(player, keys);
        Location lobby = toLocation(pending.getLobbyDestination());
        teleportAllowedInline(player, lobby);
        dirtyPending = true;
        persistenceQueue.enqueue(this::savePendingRecoveries);
        plugin.getLogger().info("[Arena] Applied pending recovery for " + player.getName());
        plugin.msg().send(player, "arena_recovery_notice");
        return true;
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    public String abortRun(UUID runId) {
        ArenaRun run = getRun(runId);
        if (run == null) return fail("arena_no_active_run");
        endRun(run, ArenaEndReason.ADMIN_ABORT);
        return null;
    }

    public String abortPlayerRun(Player player) {
        ArenaRun run = getRunForPlayer(player.getUniqueId());
        if (run == null) return fail("arena_player_not_in_run");
        endRun(run, ArenaEndReason.ADMIN_ABORT);
        return null;
    }

    public String recoverPlayer(Player player) {
        if (player == null) return fail("arena_invalid_player");
        scheduler.runForEntity(player, () -> {
            if (applyPendingRecovery(player)) return;
            ArenaRun run = getRunForPlayer(player.getUniqueId());
            if (run != null) {
                ArenaParticipant part = run.getParticipant(player.getUniqueId());
                ArenaDefinition def = getArena(run.getArenaId());
                if (part != null) restoreParticipant(player, part, def);
            }
        });
        return null;
    }

    public String cleanupArena(String arenaId) {
        ArenaDefinition def = getArena(arenaId);
        if (def == null) return fail("arena_unknown");
        Set<UUID> set = runsByArenaId.get(def.getId());
        if (set != null) {
            for (UUID id : new ArrayList<>(set)) {
                ArenaRun run = activeRuns.get(id);
                if (run != null) endRun(run, ArenaEndReason.ADMIN_ABORT);
            }
        }
        return null;
    }

    public String setArenaEnabled(String arenaId, boolean enabled) {
        ArenaDefinition def = getArena(arenaId);
        if (def == null) return fail("arena_unknown");
        if (enabled) {
            def.revalidate();
            if (!def.isConfigValid()) {
                return fail("arena_cannot_enable", "DETAIL",
                        def.getConfigError() == null ? "" : def.getConfigError());
            }
        }
        def.setEnabled(enabled);
        saveDefinition(def);
        return null;
    }

    public String diagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("Arena module enabled=").append(isEnabled()).append('\n');
        sb.append("schedulerPath=").append(scheduler.pathName())
                .append(" capabilitiesOk=").append(scheduler.hasRequiredCapabilities())
                .append(" blocked=").append(schedulerCapabilityBlocked).append('\n');
        sb.append("definitions=").append(definitions.size()).append('\n');
        sb.append("activeRuns=").append(activeRuns.size())
                .append('/').append(globalMaxActiveRuns()).append('\n');
        sb.append("defaultMaxRunsPerArena=").append(defaultMaxActiveRunsPerArena()).append('\n');
        sb.append("parties=").append(parties.size()).append('\n');
        sb.append("pendingRecoveries=").append(pendingRecoveries.size()).append('\n');
        sb.append("rewardEntries=").append(rewardLedger.all().size()).append('\n');
        sb.append("needsReview=").append(rewardLedger.needsReview().size()).append('\n');
        for (ArenaRun run : activeRuns.values()) {
            sb.append(" run ").append(run.getRunId()).append(" arena=").append(run.getArenaId())
                    .append(" state=").append(run.getState())
                    .append(" fighters=").append(run.countFighting())
                    .append(" wave=").append(run.getDeepestWave())
                    .append('\n');
        }
        for (ArenaDefinition def : allArenas()) {
            sb.append(" arena ").append(def.getId())
                    .append(" enabled=").append(def.isEnabledFlag())
                    .append(" valid=").append(def.isConfigValid())
                    .append(" active=").append(countActiveRuns(def.getId()))
                    .append('\n');
            if (def.getConfigError() != null) {
                sb.append("  error=").append(def.getConfigError()).append('\n');
            }
        }
        return sb.toString();
    }

    public List<ArenaRewardEntry> rewardsReview() {
        return rewardLedger.needsReview();
    }

    public String rewardsResolve(String entryId, boolean commit) {
        ArenaRewardEntry entry = rewardLedger.get(entryId);
        if (entry == null) {
            // try underscore form
            for (ArenaRewardEntry e : rewardLedger.all()) {
                if (e.getEntryId().replace(':', '_').equals(entryId) || e.getEntryId().equals(entryId)) {
                    entry = e;
                    break;
                }
            }
        }
        if (entry == null) return fail("arena_unknown_reward_entry");
        if (commit) {
            if (!rewardLedger.beginProcessing(entry)) {
                return fail("arena_reward_bad_status", "STATUS",
                        entry.getStatus() == null ? "" : entry.getStatus().name());
            }
            Player online = Bukkit.getPlayer(entry.getPlayerId());
            if (online == null || !online.isOnline()) {
                rewardLedger.markFailed(entry, "Player offline for money payout");
                dirtyRewards = true;
                persistenceQueue.enqueue(this::saveRewards);
                return fail("arena_payout_player_offline");
            }
            ArenaRewardEntry target = entry;
            scheduler.runForEntity(online, () -> {
                try {
                    payoutEntry(online, target, 50.0D, 0);
                    rewardLedger.markCommitted(target);
                } catch (Exception e) {
                    rewardLedger.markFailed(target, e.getMessage());
                }
                dirtyRewards = true;
                persistenceQueue.enqueue(this::saveRewards);
            });
            return null;
        } else {
            entry.setStatus(ArenaRewardStatus.CANCELLED);
        }
        dirtyRewards = true;
        persistenceQueue.enqueue(this::saveRewards);
        return null;
    }

    // ------------------------------------------------------------------
    // Rewards / boards
    // ------------------------------------------------------------------

    private void issueRewards(ArenaRun run, ArenaDefinition def, ArenaEndReason end) {
        if (!plugin.getConfig().getBoolean("arena.rewards.enabled", true)) return;
        List<ArenaParticipant> eligible = new ArrayList<>();
        for (ArenaParticipant p : run.getParticipants().values()) {
            if (p.isRewardEligible()) eligible.add(p);
        }
        if (eligible.isEmpty()) return;

        // Hybrid: equal milestone pot + score bonus
        double milestonePot = end == ArenaEndReason.CLEAR ? 200.0D : 50.0D;
        milestonePot *= run.getLockedScale().rewardMultiplier;
        double equalShare = milestonePot / eligible.size();
        int totalScore = Math.max(1, run.getPartyScore());

        for (ArenaParticipant part : eligible) {
            String rewardKey = end == ArenaEndReason.CLEAR ? "clear" : "wipe_wave_" + run.getDeepestWave();
            if (rewardLedger.alreadyCommitted(run.getRunId(), part.getPlayerId(), rewardKey)) continue;
            ArenaRewardEntry entry = rewardLedger.getOrCreate(run.getRunId(), part.getPlayerId(), rewardKey);
            if (!rewardLedger.beginProcessing(entry)) continue;

            double scoreBonus = milestonePot * 0.25D * (part.getPersonalScore() / (double) totalScore);
            double amount = equalShare + scoreBonus;
            long blocks = (long) Math.max(0, amount / 10.0D);
            Player online = Bukkit.getPlayer(part.getPlayerId());
            if (online == null || !online.isOnline()) {
                rewardLedger.markFailed(entry, "Player offline for money payout");
                plugin.getLogger().warning("[Arena] Reward needs review (offline): " + part.getPlayerId());
                continue;
            }
            scheduler.runForEntity(online, () -> {
                try {
                    payoutEntry(online, entry, amount, blocks);
                    rewardLedger.markCommitted(entry);
                } catch (Exception e) {
                    rewardLedger.markFailed(entry, e.getMessage());
                    plugin.getLogger().log(Level.WARNING, "[Arena] Reward failed for " + part.getPlayerId(), e);
                }
            });
        }
        dirtyRewards = true;
    }

    private void payoutEntry(Player player, ArenaRewardEntry entry, double money, long claimBlocks) {
        if (player == null) {
            // Offline: leave for review if money was expected
            if (money > 0) {
                throw new IllegalStateException("Player offline for money payout");
            }
            return;
        }
        try {
            if (plugin.eco() != null && money > 0) {
                plugin.eco().deposit(player, money, CurrencyType.VAULT);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Economy deposit failed: " + t.getMessage(), t);
        }
        try {
            if (claimBlocks > 0 && plugin.getClaimBlockManager() != null) {
                plugin.getClaimBlockManager().addEarned(player.getUniqueId(), claimBlocks);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[Arena] Claim-block grant skipped", t);
        }
        entry.setDetail(String.format(Locale.ROOT, "money=%.2f blocks=%d", money, claimBlocks));
    }

    private void updateLeaderboards(ArenaRun run, ArenaDefinition def) {
        if (run == null) return;
        boolean solo = run.isSoloRun();
        List<UUID> members = run.memberIds();
        int score = run.getPartyScore();
        int wave = run.getDeepestWave();
        long time = run.durationMillis();
        int bosses = run.getBossesDefeated();
        ArenaMode mode = run.getMode();
        String arenaId = run.getArenaId();
        int top = leaderboardTopN();

        if (solo) {
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.SOLO_SCORE, arenaId, mode, "default",
                    1, members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.SOLO_WAVE, arenaId, mode, "default",
                    1, members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.SOLO_FASTEST, arenaId, mode, "default",
                    1, members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
        } else {
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.PARTY_SCORE, arenaId, mode, "default",
                    members.size(), members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.PARTY_WAVE, arenaId, mode, "default",
                    members.size(), members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.PARTY_FASTEST, arenaId, mode, "default",
                    members.size(), members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
            leaderboard.submit(new ArenaLeaderboardRecord(
                    ArenaLeaderboardRecord.Board.PARTY_BOSS_KILLS, arenaId, mode, "default",
                    members.size(), members, score, wave, time, bosses, run.getRunId(), "lifetime"), top);
            for (ArenaParticipant p : run.getParticipants().values()) {
                leaderboard.submit(new ArenaLeaderboardRecord(
                        ArenaLeaderboardRecord.Board.GROUP_INDIVIDUAL_KILLS, arenaId, mode, "default",
                        members.size(), List.of(p.getPlayerId()), p.getPersonalScore(), wave, time,
                        p.getBossKills(), run.getRunId(), "lifetime"), top);
                leaderboard.submit(new ArenaLeaderboardRecord(
                        ArenaLeaderboardRecord.Board.GROUP_INDIVIDUAL_BOSS_KILLS, arenaId, mode, "default",
                        members.size(), List.of(p.getPlayerId()), p.getPersonalScore(), wave, time,
                        p.getBossKills(), run.getRunId(), "lifetime"), top);
            }
        }
        dirtyStats = true;
    }

    // ------------------------------------------------------------------
    // Plot / spawn helpers (admin bind)
    // ------------------------------------------------------------------

    public String bindLobbyFromPlayer(Player player, String arenaId) {
        return bindPlot(player, arenaId, true);
    }

    public String bindFloorFromPlayer(Player player, String arenaId) {
        return bindPlot(player, arenaId, false);
    }

    private String bindPlot(Player player, String arenaId, boolean lobby) {
        if (player == null) return fail("arena_invalid_player");
        ArenaDefinition def = getArena(arenaId);
        if (def == null) return fail("arena_unknown");
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        if (plot == null) return fail("arena_stand_in_plot");
        if (lobby) def.setLobbyPlotId(plot.getPlotId());
        else def.setArenaPlotId(plot.getPlotId());
        saveDefinition(def);
        return null;
    }

    public String setSpawn(Player player, String arenaId, String kind) {
        if (player == null) return fail("arena_invalid_player");
        ArenaDefinition def = getArena(arenaId);
        if (def == null) return fail("arena_unknown");
        Location loc = player.getLocation();
        ArenaSpawnPoint sp = new ArenaSpawnPoint(
                loc.getWorld() == null ? null : loc.getWorld().getUID(),
                loc.getWorld() == null ? null : loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        String k = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        switch (k) {
            case "entry" -> def.setEntrySpawn(sp);
            case "exit", "lobby" -> def.setExitSpawn(sp);
            case "spectator" -> def.setSpectatorSpawn(sp);
            case "mob" -> def.getMobSpawns().add(sp);
            default -> {
                return fail("arena_unknown_spawn_kind");
            }
        }
        saveDefinition(def);
        return null;
    }

    public boolean isLocationInActiveArenaPlot(Location loc) {
        if (loc == null || plugin.store() == null) return false;
        Plot plot = plugin.store().getPlotAt(loc);
        if (plot == null) return false;
        UUID plotId = plot.getPlotId();
        for (ArenaRun run : activeRuns.values()) {
            if (!run.getState().isActiveCombat()) continue;
            ArenaDefinition def = getArena(run.getArenaId());
            if (def == null) continue;
            if (plotId.equals(def.getArenaPlotId())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Scoring helpers for listener
    // ------------------------------------------------------------------

    public void recordMobKill(ArenaRun run, Player killer, ArenaRarity rarity, boolean boss) {
        if (run == null) return;
        int base = boss ? ArenaScoreService.bossScore(false) : ArenaScoreService.killScore(rarity);
        int scored = ArenaScoreService.applyScoreMultiplier(base, run.getLockedScale().scoreMultiplier);
        run.addPartyKill();
        run.addPartyScore(scored);
        if (killer != null) {
            ArenaParticipant p = run.getParticipant(killer.getUniqueId());
            if (p != null) {
                p.addKill();
                p.addScore(scored);
                if (boss) p.addBossKill();
            }
        }
        if (boss) run.addBossDefeated();
    }

    // ------------------------------------------------------------------
    // Misc helpers
    // ------------------------------------------------------------------

    public Location toLocation(ArenaSpawnPoint sp) {
        if (sp == null || !sp.hasWorldIdentity()) return null;
        World world = null;
        if (sp.worldId() != null) {
            for (World w : Bukkit.getWorlds()) {
                if (sp.worldId().equals(w.getUID())) {
                    world = w;
                    break;
                }
            }
        }
        if (world == null && sp.worldName() != null) {
            world = Bukkit.getWorld(sp.worldName());
        }
        if (world == null) return null;
        return new Location(world, sp.x(), sp.y(), sp.z(), sp.yaw(), sp.pitch());
    }

    private static void stripEffects(Player player) {
        if (player == null) return;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        try {
            AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = max == null ? 20.0D : max.getValue();
            player.setHealth(Math.min(maxHp, Math.max(1.0D, player.getHealth())));
        } catch (Throwable ignored) {
            // Paper version variance
        }
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            // leave gamemode; spectator transition can be applied by caller later
        }
    }

    private static boolean hasTotem(Player player) {
        if (player == null) return false;
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();
        return (main != null && main.getType() == Material.TOTEM_OF_UNDYING)
                || (off != null && off.getType() == Material.TOTEM_OF_UNDYING);
    }

    private static void consumeOneTotem(Player player) {
        if (player == null) return;
        PlayerInventory inv = player.getInventory();
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) {
            off.setAmount(off.getAmount() - 1);
            if (off.getAmount() <= 0) inv.setItemInOffHand(null);
            return;
        }
        ItemStack main = inv.getItemInMainHand();
        if (main != null && main.getType() == Material.TOTEM_OF_UNDYING) {
            main.setAmount(main.getAmount() - 1);
            if (main.getAmount() <= 0) inv.setItemInMainHand(null);
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // YAML (de)serialization
    // ------------------------------------------------------------------

    private ArenaDefinition deserializeDefinition(String id, ConfigurationSection sec) {
        ArenaDefinition def = new ArenaDefinition(id);
        def.setDisplayName(sec.getString("displayName", id));
        def.setEnabled(sec.getBoolean("enabled", false));
        def.setPresetId(sec.getString("presetId"));
        try {
            def.setMode(ArenaMode.valueOf(sec.getString("mode", "PVE_WAVES")));
        } catch (IllegalArgumentException ignored) {}
        def.setMaxActiveRuns(sec.getInt("maxActiveRuns", 1));
        def.setMinPlayers(sec.getInt("minPlayers", 1));
        def.setMaxPlayers(sec.getInt("maxPlayers", 4));
        def.setAllowLateJoin(sec.getBoolean("allowLateJoin", false));
        def.setInventoryPolicy(ArenaInventoryPolicy.fromConfig(
                sec.getString("inventoryPolicy"), ArenaInventoryPolicy.SAVE_AND_RESTORE));
        def.setTotemPolicy(ArenaTotemPolicy.fromConfig(
                sec.getString("totemPolicy"), ArenaTotemPolicy.CONSUME_AND_ELIMINATE));
        def.setMaxActiveMobs(sec.getInt("maxActiveMobs", 48));
        def.setLobbyPlotId(parseUuid(sec.getString("lobbyPlotId")));
        def.setArenaPlotId(parseUuid(sec.getString("arenaPlotId")));
        def.setSpectatorPlotId(parseUuid(sec.getString("spectatorPlotId")));
        def.setEntrySpawn(readSpawn(sec.getConfigurationSection("entrySpawn")));
        def.setExitSpawn(readSpawn(sec.getConfigurationSection("exitSpawn")));
        def.setSpectatorSpawn(readSpawn(sec.getConfigurationSection("spectatorSpawn")));
        def.setRewardCooldownSeconds(sec.getLong("rewardCooldownSeconds", 1800L));
        def.setMinWaveForPayout(sec.getInt("minWaveForPayout", 1));
        def.setPresenceMinRatio(sec.getDouble("presenceMinRatio", 0.5D));

        List<?> mobs = sec.getList("mobSpawns");
        if (mobs != null) {
            for (Object o : mobs) {
                if (o instanceof Map<?, ?> map) {
                    ArenaSpawnPoint sp = readSpawnMap(map);
                    if (sp != null) def.getMobSpawns().add(sp);
                }
            }
        }
        ConfigurationSection waves = sec.getConfigurationSection("waves");
        if (waves != null) {
            for (String key : waves.getKeys(false)) {
                ConfigurationSection ws = waves.getConfigurationSection(key);
                if (ws == null) continue;
                def.getWaves().add(new ArenaWaveSpec(
                        ArenaWaveSpec.typeFrom(ws.getString("type")),
                        ws.getString("id", key),
                        ws.getInt("mobCount", 6),
                        ArenaRarity.fromConfig(ws.getString("maxRarity"), ArenaRarity.COMMON),
                        ws.getBoolean("finalBoss", false),
                        ws.getString("checkpointRewardKey"),
                        ws.getStringList("difficultyModifiers"),
                        ws.getString("unlockPhaseId"),
                        ws.getStringList("mobTemplateIds"),
                        ws.getLong("timeLimitMillis", 0L)));
            }
        }
        return def;
    }

    private void serializeDefinition(YamlConfiguration out, String base, ArenaDefinition def) {
        out.set(base + ".displayName", def.getDisplayName());
        out.set(base + ".enabled", def.isEnabledFlag());
        out.set(base + ".presetId", def.getPresetId());
        out.set(base + ".mode", def.getMode().name());
        out.set(base + ".maxActiveRuns", def.getMaxActiveRuns());
        out.set(base + ".minPlayers", def.getMinPlayers());
        out.set(base + ".maxPlayers", def.getMaxPlayers());
        out.set(base + ".allowLateJoin", def.isAllowLateJoin());
        out.set(base + ".inventoryPolicy", def.getInventoryPolicy().name());
        out.set(base + ".totemPolicy", def.getTotemPolicy().name());
        out.set(base + ".maxActiveMobs", def.getMaxActiveMobs());
        out.set(base + ".lobbyPlotId", def.getLobbyPlotId() == null ? null : def.getLobbyPlotId().toString());
        out.set(base + ".arenaPlotId", def.getArenaPlotId() == null ? null : def.getArenaPlotId().toString());
        out.set(base + ".spectatorPlotId", def.getSpectatorPlotId() == null ? null : def.getSpectatorPlotId().toString());
        writeSpawn(out, base + ".entrySpawn", def.getEntrySpawn());
        writeSpawn(out, base + ".exitSpawn", def.getExitSpawn());
        writeSpawn(out, base + ".spectatorSpawn", def.getSpectatorSpawn());
        out.set(base + ".rewardCooldownSeconds", def.getRewardCooldownSeconds());
        out.set(base + ".minWaveForPayout", def.getMinWaveForPayout());
        out.set(base + ".presenceMinRatio", def.getPresenceMinRatio());

        List<Map<String, Object>> mobs = new ArrayList<>();
        for (ArenaSpawnPoint sp : def.getMobSpawns()) {
            mobs.add(spawnToMap(sp));
        }
        out.set(base + ".mobSpawns", mobs);

        int i = 0;
        for (ArenaWaveSpec w : def.getWaves()) {
            String wb = base + ".waves." + (i++);
            out.set(wb + ".type", w.type().name());
            out.set(wb + ".id", w.id());
            out.set(wb + ".mobCount", w.mobCount());
            out.set(wb + ".maxRarity", w.maxRarity().name());
            out.set(wb + ".finalBoss", w.finalBoss());
            out.set(wb + ".checkpointRewardKey", w.checkpointRewardKey());
            out.set(wb + ".difficultyModifiers", w.difficultyModifiers());
            out.set(wb + ".unlockPhaseId", w.unlockPhaseId());
            out.set(wb + ".mobTemplateIds", w.mobTemplateIds());
            out.set(wb + ".timeLimitMillis", w.timeLimitMillis());
        }
    }

    private ArenaSpawnPoint readSpawn(ConfigurationSection sec) {
        if (sec == null) return null;
        return new ArenaSpawnPoint(
                parseUuid(sec.getString("worldId")),
                sec.getString("worldName"),
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    private ArenaSpawnPoint readSpawnMap(Map<?, ?> map) {
        if (map == null) return null;
        Object wid = map.get("worldId");
        Object wn = map.get("worldName");
        return new ArenaSpawnPoint(
                wid == null ? null : parseUuid(String.valueOf(wid)),
                wn == null ? null : String.valueOf(wn),
                asDouble(map.get("x")), asDouble(map.get("y")), asDouble(map.get("z")),
                (float) asDouble(map.get("yaw")), (float) asDouble(map.get("pitch")));
    }

    private void writeSpawn(YamlConfiguration out, String base, ArenaSpawnPoint sp) {
        if (sp == null) return;
        out.set(base + ".worldId", sp.worldId() == null ? null : sp.worldId().toString());
        out.set(base + ".worldName", sp.worldName());
        out.set(base + ".x", sp.x());
        out.set(base + ".y", sp.y());
        out.set(base + ".z", sp.z());
        out.set(base + ".yaw", sp.yaw());
        out.set(base + ".pitch", sp.pitch());
    }

    private Map<String, Object> spawnToMap(ArenaSpawnPoint sp) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (sp == null) return map;
        map.put("worldId", sp.worldId() == null ? null : sp.worldId().toString());
        map.put("worldName", sp.worldName());
        map.put("x", sp.x());
        map.put("y", sp.y());
        map.put("z", sp.z());
        map.put("yaw", sp.yaw());
        map.put("pitch", sp.pitch());
        return map;
    }

    private ArenaLeaderboardRecord deserializeBoardRecord(Map<?, ?> map) {
        try {
            ArenaLeaderboardRecord.Board board =
                    ArenaLeaderboardRecord.Board.valueOf(String.valueOf(map.get("board")));
            String arenaId = String.valueOf(map.get("arenaId"));
            ArenaMode mode = ArenaMode.PVE_WAVES;
            try {
                mode = ArenaMode.valueOf(String.valueOf(map.get("mode")));
            } catch (Exception ignored) {}
            List<UUID> members = new ArrayList<>();
            Object rawMembers = map.get("members");
            if (rawMembers instanceof List<?> list) {
                for (Object o : list) {
                    UUID u = parseUuid(String.valueOf(o));
                    if (u != null) members.add(u);
                }
            }
            Object difficulty = map.get("difficulty");
            Object seasonId = map.get("seasonId");
            return new ArenaLeaderboardRecord(
                    board, arenaId, mode,
                    difficulty == null ? "default" : String.valueOf(difficulty),
                    asInt(map.get("partySize"), 1),
                    members,
                    asInt(map.get("score"), 0),
                    asInt(map.get("wave"), 0),
                    (long) asDouble(map.get("clearTimeMillis")),
                    asInt(map.get("bossKills"), 0),
                    parseUuid(map.get("runId") == null ? null : String.valueOf(map.get("runId"))),
                    seasonId == null ? "lifetime" : String.valueOf(seasonId));
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> serializeBoardRecord(ArenaLeaderboardRecord r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("board", r.getBoard().name());
        map.put("arenaId", r.getArenaId());
        map.put("mode", r.getMode().name());
        map.put("difficulty", r.getDifficultyLabel());
        map.put("partySize", r.getPartySize());
        List<String> members = new ArrayList<>();
        for (UUID u : r.getMembers()) members.add(u.toString());
        map.put("members", members);
        map.put("score", r.getScore());
        map.put("wave", r.getWave());
        map.put("clearTimeMillis", r.getClearTimeMillis());
        map.put("bossKills", r.getBossKills());
        map.put("runId", r.getRunId() == null ? null : r.getRunId().toString());
        map.put("seasonId", r.getSeasonId());
        map.put("recordedAt", r.getRecordedAt());
        return map;
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return 0.0D;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0.0D;
        }
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    public void shutdown() {
        try {
            save();
        } finally {
            persistenceQueue.close();
        }
    }
}
