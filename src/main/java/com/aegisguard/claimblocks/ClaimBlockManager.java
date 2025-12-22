package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimBlockManager {

    private final AegisGuard plugin;

    private final Map<UUID, ClaimBlockData> cache = new ConcurrentHashMap<>();

    private final File file;
    private FileConfiguration data;

    private final Object ioLock = new Object();

    public ClaimBlockManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-blocks.yml");
        load();
    }

    // --- Core Math ---

    public long getTotalBlocks(UUID uuid) {
        ClaimBlockData user = getOrCreate(uuid);
        long starter = plugin.cfg().raw().getLong("claim_blocks.starting_blocks", 1000);
        return starter + user.getEarnedBlocks() + user.getBonusBlocks() + user.getBoughtBlocks();
    }

    public long getUsedBlocks(UUID uuid) {
        long used = 0;
        for (Plot plot : plugin.store().getPlots(uuid)) {
            if (plot != null) used += plot.getArea();
        }
        getOrCreate(uuid).setUsedBlocksCache(used);
        return used;
    }

    public long getSpentBlocks(UUID uuid) {
        return getOrCreate(uuid).getSpentBlocks();
    }

    public long getAvailableBlocks(UUID uuid) {
        long available = getTotalBlocks(uuid) - getUsedBlocks(uuid) - getSpentBlocks(uuid);
        return Math.max(0, available);
    }

    public boolean canAfford(UUID uuid, long amount) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null && p.hasPermission("aegis.admin.bypass-limits")) return true;
        return getAvailableBlocks(uuid) >= amount;
    }

    // --- Spending for upgrades/features ---

    public boolean spend(UUID uuid, long amount) {
        if (uuid == null) return false;
        if (amount <= 0) return true;
        if (plugin.cfg() == null || !plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)) return false;

        if (!canAfford(uuid, amount)) return false;

        getOrCreate(uuid).addSpentBlocks(amount);
        saveAsync();
        return true;
    }

    public void refund(UUID uuid, long amount) {
        if (uuid == null) return;
        if (amount <= 0) return;

        getOrCreate(uuid).removeSpentBlocks(amount);
        saveAsync();
    }

    // --- Safer Mutators (always persisted) ---

    public void addEarned(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        getOrCreate(uuid).addEarnedBlocks(amount);
        saveAsync();
    }

    public void addBonus(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        getOrCreate(uuid).addBonusBlocks(amount);
        saveAsync();
    }

    public void addBought(UUID uuid, long amount) {
        if (uuid == null || amount <= 0) return;
        getOrCreate(uuid).addBoughtBlocks(amount);
        saveAsync();
    }

    public ClaimBlockData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, ClaimBlockData::new);
    }

    public void setStarterClaimed(UUID uuid, boolean claimed) {
        ClaimBlockData d = getOrCreate(uuid);
        d.setClaimedStarter(claimed);
        saveAsync();
    }

    // --- Persistence (YAML) ---

    public void load() {
        synchronized (ioLock) {
            if (!file.exists()) {
                try { file.getParentFile().mkdirs(); file.createNewFile(); }
                catch (IOException e) { e.printStackTrace(); }
            }

            data = YamlConfiguration.loadConfiguration(file);

            if (data.contains("players")) {
                for (String key : Objects.requireNonNull(data.getConfigurationSection("players")).getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        ClaimBlockData cbd = new ClaimBlockData(uuid);
                        String path = "players." + key;

                        cbd.setEarnedBlocks(data.getLong(path + ".earned", 0));
                        cbd.setBonusBlocks(data.getLong(path + ".bonus", 0));
                        cbd.setBoughtBlocks(data.getLong(path + ".bought", 0));
                        cbd.setSpentBlocks(data.getLong(path + ".spent", 0)); // ✅ NEW
                        cbd.setClaimedStarter(data.getBoolean(path + ".starter_claimed", false));

                        cache.put(uuid, cbd);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (data == null) data = YamlConfiguration.loadConfiguration(file);

            Map<UUID, ClaimBlockData> snap = new HashMap<>(cache);

            for (Map.Entry<UUID, ClaimBlockData> entry : snap.entrySet()) {
                String path = "players." + entry.getKey();
                ClaimBlockData cbd = entry.getValue();

                data.set(path + ".earned", cbd.getEarnedBlocks());
                data.set(path + ".bonus", cbd.getBonusBlocks());
                data.set(path + ".bought", cbd.getBoughtBlocks());
                data.set(path + ".spent", cbd.getSpentBlocks()); // ✅ NEW
                data.set(path + ".starter_claimed", cbd.hasClaimedStarter());
            }

            try { data.save(file); }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void shutdown() {
        save();
    }
}
