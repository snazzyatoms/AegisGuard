package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Staff-authored arena bound to server plots. Not a rental Zone.
 *
 * <p>All mutable fields are volatile and the three spawn/floor/wave lists are
 * copy-on-write: admin commands mutate a definition on the admin's region
 * thread while run ticks, lobby joiners, and the matchmaking queue read it
 * from other region threads (Folia) — without safe publication a tick could
 * iterate a list mid-edit or never see a flag/mode change.
 */
public final class ArenaDefinition {

    private final String id;
    private volatile String displayName;
    private volatile boolean enabled;
    private volatile String presetId;
    private volatile ArenaMode mode = ArenaMode.PVE_WAVES;
    private volatile int maxActiveRuns = 1;
    private volatile int minPlayers = 1;
    private volatile int maxPlayers = 4;
    private volatile boolean allowLateJoin;
    private volatile ArenaInventoryPolicy inventoryPolicy = ArenaInventoryPolicy.SAVE_AND_RESTORE;
    private volatile ArenaTotemPolicy totemPolicy = ArenaTotemPolicy.CONSUME_AND_ELIMINATE;
    private volatile int maxActiveMobs = 48;
    private volatile UUID lobbyPlotId;
    private volatile UUID arenaPlotId;
    private volatile UUID spectatorPlotId;
    private volatile ArenaSpawnPoint entrySpawn;
    private volatile ArenaSpawnPoint exitSpawn;
    private volatile ArenaSpawnPoint spectatorSpawn;
    private final List<ArenaSpawnPoint> mobSpawns = new CopyOnWriteArrayList<>();
    private final List<ArenaFloor> floors = new CopyOnWriteArrayList<>();
    private final List<ArenaWaveSpec> waves = new CopyOnWriteArrayList<>();
    private volatile ArenaScalingTable scaling = ArenaScalingTable.lavaDungeonDefaults();
    private volatile long rewardCooldownSeconds = 1800L;
    private volatile int minWaveForPayout = 1;
    private volatile double presenceMinRatio = 0.5D;
    private volatile String configError;
    private volatile boolean configValid;

    public ArenaDefinition(String id) {
        this.id = Objects.requireNonNull(id, "id").toLowerCase().replace(' ', '_');
        this.displayName = id;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null ? id : displayName; }

    public boolean isEnabled() { return enabled && configValid; }
    public boolean isEnabledFlag() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getPresetId() { return presetId; }
    public void setPresetId(String presetId) { this.presetId = presetId; }

    public ArenaMode getMode() { return mode; }
    public void setMode(ArenaMode mode) { this.mode = mode == null ? ArenaMode.PVE_WAVES : mode; }

    public int getMaxActiveRuns() { return Math.max(1, maxActiveRuns); }
    public void setMaxActiveRuns(int maxActiveRuns) { this.maxActiveRuns = Math.max(1, maxActiveRuns); }

    public int getMinPlayers() { return Math.max(1, minPlayers); }
    public void setMinPlayers(int minPlayers) { this.minPlayers = Math.max(1, minPlayers); }

    public int getMaxPlayers() { return Math.max(getMinPlayers(), maxPlayers); }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = Math.max(1, maxPlayers); }

    public boolean isAllowLateJoin() { return allowLateJoin; }
    public void setAllowLateJoin(boolean allowLateJoin) { this.allowLateJoin = allowLateJoin; }

    public ArenaInventoryPolicy getInventoryPolicy() { return inventoryPolicy; }
    public void setInventoryPolicy(ArenaInventoryPolicy inventoryPolicy) {
        this.inventoryPolicy = inventoryPolicy == null ? ArenaInventoryPolicy.SAVE_AND_RESTORE : inventoryPolicy;
    }

    public ArenaTotemPolicy getTotemPolicy() { return totemPolicy; }
    public void setTotemPolicy(ArenaTotemPolicy totemPolicy) {
        this.totemPolicy = totemPolicy == null ? ArenaTotemPolicy.CONSUME_AND_ELIMINATE : totemPolicy;
    }

    public int getMaxActiveMobs() { return Math.max(1, maxActiveMobs); }
    public void setMaxActiveMobs(int maxActiveMobs) { this.maxActiveMobs = Math.max(1, maxActiveMobs); }

    public UUID getLobbyPlotId() { return lobbyPlotId; }
    public void setLobbyPlotId(UUID lobbyPlotId) { this.lobbyPlotId = lobbyPlotId; }
    public UUID getArenaPlotId() { return arenaPlotId; }
    public void setArenaPlotId(UUID arenaPlotId) { this.arenaPlotId = arenaPlotId; }
    public UUID getSpectatorPlotId() { return spectatorPlotId; }
    public void setSpectatorPlotId(UUID spectatorPlotId) { this.spectatorPlotId = spectatorPlotId; }

    public ArenaSpawnPoint getEntrySpawn() { return entrySpawn; }
    public void setEntrySpawn(ArenaSpawnPoint entrySpawn) { this.entrySpawn = entrySpawn; }
    public ArenaSpawnPoint getExitSpawn() { return exitSpawn; }
    public void setExitSpawn(ArenaSpawnPoint exitSpawn) { this.exitSpawn = exitSpawn; }
    public ArenaSpawnPoint getSpectatorSpawn() { return spectatorSpawn; }
    public void setSpectatorSpawn(ArenaSpawnPoint spectatorSpawn) { this.spectatorSpawn = spectatorSpawn; }

    public List<ArenaSpawnPoint> getMobSpawns() { return mobSpawns; }

    /** Instancing state for the primary floor — variants carry theirs on the ArenaFloor. */
    private String primarySchematic;
    private String primaryCaptureWorld;
    private int primaryCaptureMinX, primaryCaptureMinY, primaryCaptureMinZ;
    private int primaryCaptureSizeX, primaryCaptureSizeY, primaryCaptureSizeZ;

    /** Extra floor variants beyond the primary (flat fields). */
    public List<ArenaFloor> getFloors() { return floors; }

    /** Total selectable floors: the primary one plus any variants. */
    public int getFloorCount() { return 1 + floors.size(); }

    /**
     * Floor view at index 0 = primary (synthesized from flat fields);
     * indices 1..n = the extra variants in insertion order.
     */
    public ArenaFloor floorAt(int index) {
        if (index <= 0) return primaryFloor();
        if (index - 1 < floors.size()) return floors.get(index - 1);
        return primaryFloor();
    }

    /**
     * The flat-field primary floor expressed as an ArenaFloor. Synthesized per
     * call so a running game never shares mutable spawn state with admin
     * edits; schematic/capture fields come from the flat primary-* keys.
     */
    public ArenaFloor primaryFloor() {
        ArenaFloor f = new ArenaFloor(displayName);
        f.setFloorPlotId(arenaPlotId);
        f.setEntrySpawn(entrySpawn);
        f.getMobSpawns().addAll(mobSpawns);
        f.setWeight(1);
        f.setSchematic(primarySchematic);
        if (primaryCaptureWorld != null) {
            f.setCaptureBounds(primaryCaptureWorld,
                    primaryCaptureMinX, primaryCaptureMinY, primaryCaptureMinZ,
                    primaryCaptureSizeX, primaryCaptureSizeY, primaryCaptureSizeZ);
        }
        return f;
    }

    /**
     * Stamp schematic/capture on the primary floor (index 0 in {@code map}
     * commands). Variants carry these on the ArenaFloor itself.
     */
    public void setPrimarySchematic(String schematic, String captureWorld,
                                    int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        this.primarySchematic = schematic == null || schematic.isBlank() ? null : schematic;
        this.primaryCaptureWorld = captureWorld == null || captureWorld.isBlank() ? null : captureWorld;
        this.primaryCaptureMinX = minX;
        this.primaryCaptureMinY = minY;
        this.primaryCaptureMinZ = minZ;
        this.primaryCaptureSizeX = Math.max(0, sizeX);
        this.primaryCaptureSizeY = Math.max(0, sizeY);
        this.primaryCaptureSizeZ = Math.max(0, sizeZ);
    }

    public String getPrimarySchematic() { return primarySchematic; }

    /** Extra-floor index of the plot the location stands in, or -1. */
    public int extraFloorIndexForPlot(UUID plotId) {
        if (plotId == null) return -1;
        for (int i = 0; i < floors.size(); i++) {
            if (plotId.equals(floors.get(i).getFloorPlotId())) return i;
        }
        return -1;
    }

    /** True when the plot is any combat floor of this arena. */
    public boolean isFloorPlot(UUID plotId) {
        if (plotId == null) return false;
        if (plotId.equals(arenaPlotId)) return true;
        return extraFloorIndexForPlot(plotId) >= 0;
    }

    /**
     * Pick a usable floor for a new run. Uniform pick over usable variants —
     * the primary always qualifies once the arena validates. Never null after
     * {@link #revalidate()} passes (it requires the primary to be usable).
     */
    public ArenaFloor pickFloor(java.util.Random random) {
        List<ArenaFloor> usable = new ArrayList<>();
        ArenaFloor primary = primaryFloor();
        if (primary.isUsable()) usable.add(primary);
        for (ArenaFloor f : floors) if (f.isUsable()) usable.add(f);
        if (usable.isEmpty()) return primary;
        if (random == null) random = new java.util.Random();
        int total = 0;
        for (ArenaFloor f : usable) total += f.getWeight();
        int roll = random.nextInt(total);
        for (ArenaFloor f : usable) {
            roll -= f.getWeight();
            if (roll < 0) return f;
        }
        return usable.get(usable.size() - 1);
    }

    public List<ArenaWaveSpec> getWaves() { return waves; }

    public ArenaScalingTable getScaling() { return scaling; }
    public void setScaling(ArenaScalingTable scaling) {
        this.scaling = scaling == null ? ArenaScalingTable.lavaDungeonDefaults() : scaling;
    }

    public long getRewardCooldownSeconds() { return rewardCooldownSeconds; }
    public void setRewardCooldownSeconds(long rewardCooldownSeconds) {
        this.rewardCooldownSeconds = Math.max(0L, rewardCooldownSeconds);
    }

    public int getMinWaveForPayout() { return minWaveForPayout; }
    public void setMinWaveForPayout(int minWaveForPayout) { this.minWaveForPayout = Math.max(0, minWaveForPayout); }

    public double getPresenceMinRatio() { return presenceMinRatio; }
    public void setPresenceMinRatio(double presenceMinRatio) {
        this.presenceMinRatio = Math.max(0.0D, Math.min(1.0D, presenceMinRatio));
    }

    public String getConfigError() { return configError; }
    public boolean isConfigValid() { return configValid; }

    /**
     * Structural validation. Shared requirements: lobby plot, combat plot,
     * entry spawn, exit spawn. PvE additionally needs mob spawns + wave list
     * (PVE_BOSS needs a boss wave); PvP needs ≥2 players and DUEL exactly 2.
     * The {@code pvpAllowed} flag is config-level (arena.pvp.enabled).
     */
    public void revalidate() {
        revalidate(true);
    }

    public void revalidate(boolean pvpAllowed) {
        List<String> errors = new ArrayList<>();
        if (lobbyPlotId == null) errors.add("missing lobby plot");
        if (arenaPlotId == null) errors.add("missing combat-floor plot");
        if (entrySpawn == null || !entrySpawn.hasWorldIdentity()) errors.add("missing entry spawn");
        if (exitSpawn == null || !exitSpawn.hasWorldIdentity()) errors.add("missing exit/lobby spawn");
        if (maxActiveMobs < 1) errors.add("invalid mob cap");
        if (inventoryPolicy == null) errors.add("missing inventory policy");
        if (minPlayers > maxPlayers) errors.add("minPlayers > maxPlayers");
        if (mode != null && mode.isPvp()) {
            if (!pvpAllowed) errors.add("pvp mode requires arena.pvp.enabled");
            if (minPlayers < 2) errors.add("pvp requires min_players >= 2");
            if (mode == ArenaMode.PVP_DUEL && maxPlayers != 2) {
                errors.add("duel requires exactly 2 players");
            }
        } else {
            if (mobSpawns.isEmpty()) errors.add("missing mob spawn point");
            if (waves.isEmpty()) errors.add("missing wave list");
            if (mode == ArenaMode.PVE_BOSS
                    && waves.stream().noneMatch(w -> w.isBoss() || w.finalBoss())) {
                errors.add("pve_boss requires a boss wave");
            }
        }
        this.configValid = errors.isEmpty();
        this.configError = configValid ? null : String.join("; ", errors);
        if (!configValid) {
            this.enabled = false;
        }
    }
}
