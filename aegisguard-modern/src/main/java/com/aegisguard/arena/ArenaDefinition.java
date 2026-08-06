package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Staff-authored arena bound to server plots. Not a rental Zone.
 */
public final class ArenaDefinition {

    private final String id;
    private String displayName;
    private boolean enabled;
    private String presetId;
    private ArenaMode mode = ArenaMode.PVE_WAVES;
    private int maxActiveRuns = 1;
    private int minPlayers = 1;
    private int maxPlayers = 4;
    private boolean allowLateJoin;
    private ArenaInventoryPolicy inventoryPolicy = ArenaInventoryPolicy.SAVE_AND_RESTORE;
    private ArenaTotemPolicy totemPolicy = ArenaTotemPolicy.CONSUME_AND_ELIMINATE;
    private int maxActiveMobs = 48;
    private UUID lobbyPlotId;
    private UUID arenaPlotId;
    private UUID spectatorPlotId;
    private ArenaSpawnPoint entrySpawn;
    private ArenaSpawnPoint exitSpawn;
    private ArenaSpawnPoint spectatorSpawn;
    private final List<ArenaSpawnPoint> mobSpawns = new ArrayList<>();
    private final List<ArenaWaveSpec> waves = new ArrayList<>();
    private ArenaScalingTable scaling = ArenaScalingTable.lavaDungeonDefaults();
    private long rewardCooldownSeconds = 1800L;
    private int minWaveForPayout = 1;
    private double presenceMinRatio = 0.5D;
    private String configError;
    private boolean configValid;

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
     * MVP minimum: lobby plot, combat plot, entry spawn, exit spawn, ≥1 mob spawn, wave list, mob cap, policy.
     */
    public void revalidate() {
        List<String> errors = new ArrayList<>();
        if (lobbyPlotId == null) errors.add("missing lobby plot");
        if (arenaPlotId == null) errors.add("missing combat-floor plot");
        if (entrySpawn == null || !entrySpawn.hasWorldIdentity()) errors.add("missing entry spawn");
        if (exitSpawn == null || !exitSpawn.hasWorldIdentity()) errors.add("missing exit/lobby spawn");
        if (mobSpawns.isEmpty()) errors.add("missing mob spawn point");
        if (waves.isEmpty()) errors.add("missing wave list");
        if (maxActiveMobs < 1) errors.add("invalid mob cap");
        if (inventoryPolicy == null) errors.add("missing inventory policy");
        if (minPlayers > maxPlayers) errors.add("minPlayers > maxPlayers");
        this.configValid = errors.isEmpty();
        this.configError = configValid ? null : String.join("; ", errors);
        if (!configValid) {
            this.enabled = false;
        }
    }
}
