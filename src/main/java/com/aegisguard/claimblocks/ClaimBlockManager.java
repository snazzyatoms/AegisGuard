package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClaimBlockManager {

    private final AegisGuard plugin;
    private final Map<UUID, ClaimBlockData> cache = new HashMap<>();
    private final File file;
    private FileConfiguration data;

    // ✅ NEW: used-blocks cache to reduce repeated plot scans
    private final Map<UUID, Long> usedCache = new HashMap<>();
    private final Map<UUID, Long> usedCacheUntil = new HashMap<>();
    private static final long USED_CACHE_TTL_MS = 2000; // 2s is enough to stop spam rescans

    // ✅ NEW: throttle save spam
    private volatile boolean saveQueued = false;

    public ClaimBlockManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-blocks.yml");
        load();
    }

    // --- Core Math ---

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
        if (uuid == null) return 0;

        long now = System.currentTimeMillis();
        Long until = usedCacheUntil.get(uuid);
        if (until != null && until > now) {
            return usedCache.getOrDefault(uuid, 0L);
        }

        long used = 0;

        // ✅ Null-safe store access
        List<Plot> plots = null;
        try {
            plots = plugin.store().getPlots(uuid);
        } catch (Throwable ignored) {}

        if (plots != null) {
            for (Plot plot : plots) {
                if (plot == null) continue;
                try {
                    used += plot.getArea();
                } catch (Throwable ignored) {}
            }
        }

        // Update caches
        usedCache.put(uuid, used);
        usedCacheUntil.put(uuid, now + USED_CACHE_TTL_MS);

        // Update ClaimBlockData cache too (your existing behavior)
        try {
            getOrCreate(uuid).setUsedBlocksCache(used);
        } catch (Throwable ignored) {}

        return used;
    }

    /**
     * How many blocks can they still spend?
     */
    public long getAvailableBlocks(UUID uuid) {
        long available = getTotalBlocks(uuid) - getUsedBlocks(uuid);
        return Math.max(0, available);
    }

    /**
     * Check if player can afford a new claim of X size.
     */
    public boolean canAfford(UUID uuid, long areaNeeded) {
        if (uuid == null) return false;

        // Admins bypass
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null && p.hasPermission("aegis.admin.bypass-limits")) return true;

        return getAvailableBlocks(uuid) >= areaNeeded;
    }

    // --- Cache Control (NEW) ---

    /**
     * ✅ NEW: Invalidate used-block cache (call after claim/unclaim/merge/resize).
     */
    public void invalidateUsed(UUID uuid) {
        if (uuid == null) return;
        usedCache.remove(uuid);
        usedCacheUntil.remove(uuid);
    }

    /**
     * ✅ NEW: Force an immediate recompute of used blocks.
     */
    public long refreshUsed(UUID uuid) {
        invalidateUsed(uuid);
        return getUsedBlocks(uuid);
    }

    // --- Data Management ---

    public ClaimBlockData getOrCreate(UUID uuid) {
        if (uuid == null) throw new IllegalArgumentException("uuid cannot be null");
        if (!cache.containsKey(uuid)) {
            cache.put(uuid, new ClaimBlockData(uuid));
        }
        return cache.get(uuid);
    }

    public void setStarterClaimed(UUID uuid, boolean claimed) {
        ClaimBlockData data = getOrCreate(uuid);
        data.setClaimedStarter(claimed);
        saveAsync();
    }

    // Optional helpers (nice for tasks/shops later, won’t break anything)
    public void addEarnedBlocks(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData cbd = getOrCreate(uuid);
        cbd.setEarnedBlocks(cbd.getEarnedBlocks() + amount);
        saveAsync();
    }

    public void addBonusBlocks(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData cbd = getOrCreate(uuid);
        cbd.setBonusBlocks(cbd.getBonusBlocks() + amount);
        saveAsync();
    }

    public void addBoughtBlocks(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        ClaimBlockData cbd = getOrCreate(uuid);
        cbd.setBoughtBlocks(cbd.getBoughtBlocks() + amount);
        saveAsync();
    }

    // --- Persistence (YAML) ---

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
                } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        if (data == null) return;

        for (Map.Entry<UUID, ClaimBlockData> entry : cache.entrySet()) {
            UUID uuid = entry.getKey();
            ClaimBlockData cbd = entry.getValue();
            if (uuid == null || cbd == null) continue;

            String path = "players." + uuid;
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
        // ✅ NEW: coalesce spam into one async save
        if (saveQueued) return;
        saveQueued = true;

        plugin.runGlobalAsync(() -> {
            try {
                save();
            } finally {
                saveQueued = false;
            }
        });
    }

    public void shutdown() {
        save();
    }
}
