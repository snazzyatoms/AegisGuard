package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimBlockManager {

    private final AegisGuard plugin;

    // Thread-safe map because saveAsync() can iterate while the main thread updates values
    private final Map<UUID, ClaimBlockData> cache = new ConcurrentHashMap<>();

    private final File file;
    private FileConfiguration data;

    public ClaimBlockManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-blocks.yml");
        load();
    }

    // --------------------------------------------------
    // Feature Toggle
    // --------------------------------------------------

    /**
     * Master switch for claim block economy.
     * If disabled, claims should not be blocked by budgets.
     */
    public boolean isEnabled() {
        return plugin.cfg().raw().getBoolean("claim_blocks.enabled", true);
    }

    // --------------------------------------------------
    // Core Math
    // --------------------------------------------------

    /**
     * How many blocks does this player have in TOTAL (Wallet Size)?
     * Formula: Starter_Config + Earned + Bonus + Bought
     */
    public long getTotalBlocks(UUID uuid) {
        ClaimBlockData user = getOrCreate(uuid);
        long starter = plugin.cfg().raw().getLong("claim_blocks.starting_blocks", 1000); // Default 1000
        return starter + user.getEarnedBlocks() + user.getBonusBlocks() + user.getBoughtBlocks();
    }

    /**
     * How many blocks are currently sitting on the ground as Plots?
     */
    public long getUsedBlocks(UUID uuid) {
        long used = 0;

        // Null-safe: some DataStores may return null for "no plots"
        List<Plot> plots = plugin.store().getPlots(uuid);
        if (plots != null) {
            for (Plot plot : plots) {
                if (plot == null) continue;
                used += plot.getArea();
            }
        }

        // Update cache
        getOrCreate(uuid).setUsedBlocksCache(used);
        return used;
    }

    /**
     * How many blocks can they still spend?
     */
    public long getAvailableBlocks(UUID uuid) {
        long available = getTotalBlocks(uuid) - getUsedBlocks(uuid);
        return Math.max(0L, available);
    }

    /**
     * Check if player can afford a new claim of X size.
     */
    public boolean canAfford(UUID uuid, long areaNeeded) {
        // If system is disabled, do not restrict claims
        if (!isEnabled()) return true;

        // Admins bypass
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null && p.hasPermission("aegis.admin.bypass-limits")) return true;

        return getAvailableBlocks(uuid) >= areaNeeded;
    }

    // --------------------------------------------------
    // Data Management
    // --------------------------------------------------

    public ClaimBlockData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, ClaimBlockData::new);
    }

    public void setStarterClaimed(UUID uuid, boolean claimed) {
        ClaimBlockData data = getOrCreate(uuid);
        data.setClaimedStarter(claimed);
        saveAsync();
    }

    // --------------------------------------------------
    // Persistence (YAML)
    // --------------------------------------------------

    public void load() {
        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        data = YamlConfiguration.loadConfiguration(file);

        if (data.contains("players") && data.getConfigurationSection("players") != null) {
            for (String key : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ClaimBlockData cbd = new ClaimBlockData(uuid);
                    String path = "players." + key;

                    cbd.setEarnedBlocks(data.getLong(path + ".earned", 0));
                    cbd.setBonusBlocks(data.getLong(path + ".bonus", 0));
                    cbd.setBoughtBlocks(data.getLong(path + ".bought", 0));
                    cbd.setClaimedStarter(data.getBoolean(path + ".starter_claimed", false));

                    cache.put(uuid, cbd);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void save() {
        if (data == null) return;

        // Snapshot to avoid async iteration issues
        for (Map.Entry<UUID, ClaimBlockData> entry : Map.copyOf(cache).entrySet()) {
            String path = "players." + entry.getKey().toString();
            ClaimBlockData cbd = entry.getValue();
            if (cbd == null) continue;

            data.set(path + ".earned", cbd.getEarnedBlocks());
            data.set(path + ".bonus", cbd.getBonusBlocks());
            data.set(path + ".bought", cbd.getBoughtBlocks());
            data.set(path + ".starter_claimed", cbd.hasClaimedStarter());
        }

        try {
            data.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAsync() {
        // Use plugin's Folia-safe async runner
        plugin.runGlobalAsync(this::save);
    }

    public void shutdown() {
        save();
    }
}
