package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YMLDataStore (v1.2.1)
 * - Manages plot data using 'plots.yml'.
 * - Implements strict IDataStore contract for 1.2.1.
 * - Ensures data persistence with immediate saving on modification.
 */
public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration config;

    // --- CACHES ---
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    // Map<WorldName, Map<ChunkKey, Set<Plot>>> for fast spatial lookups
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();
    
    private volatile boolean isDirty = false;

    public YMLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    // ==============================================================
    // --- CORE I/O ---
    // ==============================================================

    @Override
    public void load() {
        plotsByOwner.clear();
        plotsByChunk.clear();

        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }

        config = YamlConfiguration.loadConfiguration(file);
        int count = 0;

        // Iterate through all Plot UUIDs in the file
        for (String key : config.getKeys(false)) {
            try {
                UUID plotId = UUID.fromString(key);
                ConfigurationSection sec = config.getConfigurationSection(key);

                // Basic Info
                UUID ownerId = UUID.fromString(sec.getString("owner"));
                String ownerName = sec.getString("owner-name", "Unknown");
                String worldName = sec.getString("world");
                
                if (Bukkit.getWorld(worldName) == null) continue; // Skip invalid worlds

                int x1 = sec.getInt("x1");
                int z1 = sec.getInt("z1");
                int x2 = sec.getInt("x2");
                int z2 = sec.getInt("z2");

                // Create Plot Object
                Plot plot = new Plot(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2);
                
                // Load Extra Data
                plot.setLevel(sec.getInt("level", 1));
                plot.setXp(sec.getDouble("xp", 0.0));
                plot.setLastUpkeep(sec.getLong("last-upkeep", System.currentTimeMillis()));
                
                // Load Visuals
                plot.setSpawnLocationFromString(sec.getString("spawn-location"));
                plot.setWelcomeMessage(sec.getString("welcome-message"));
                plot.setFarewellMessage(sec.getString("farewell-message"));
                plot.setDescription(sec.getString("description"));
                
                // Load Economy Status
                if (sec.getBoolean("market.is-for-sale")) {
                    plot.setForSale(true, sec.getDouble("market.sale-price"));
                }
                plot.setPlotStatus(sec.getString("plot-status", "ACTIVE"));

                // Load Flags
                if (sec.isConfigurationSection("flags")) {
                    ConfigurationSection flags = sec.getConfigurationSection("flags");
                    for (String f : flags.getKeys(false)) {
                        plot.setFlag(f, flags.getBoolean(f));
                    }
                }

                // Load Roles
                if (sec.isConfigurationSection("roles")) {
                    ConfigurationSection roles = sec.getConfigurationSection("roles");
                    for (String pUuid : roles.getKeys(false)) {
                        try {
                            plot.setRole(UUID.fromString(pUuid), roles.getString(pUuid));
                        } catch (Exception ignored) {}
                    }
                }

                // Cache it
                cachePlot(plot);
                count++;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load plot: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + count + " plots from YML.");
    }

    @Override
    public void save() {
        if (config == null) config = new YamlConfiguration();
        
        // Save all cached plots to the config object
        for (Plot plot : getAllPlots()) {
            writePlotToConfig(plot);
        }
        
        try {
            config.save(file);
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save plots.yml!");
            e.printStackTrace();
        }
    }

    @Override
    public void saveSync() {
        save();
    }
    
    @Override
    public void savePlot(Plot plot) {
        // Update the specific section in memory and save to disk immediately
        // This mirrors SQL's "instant save" behavior
        writePlotToConfig(plot);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void writePlotToConfig(Plot plot) {
        String key = plot.getPlotId().toString();
        ConfigurationSection sec = config.createSection(key);
        
        sec.set("owner", plot.getOwner().toString());
        sec.set("owner-name", plot.getOwnerName());
        sec.set("world", plot.getWorld());
        sec.set("x1", plot.getX1());
        sec.set("z1", plot.getZ1());
        sec.set("x2", plot.getX2());
        sec.set("z2", plot.getZ2());
        
        sec.set("level", plot.getLevel());
        sec.set("xp", plot.getXp());
        sec.set("last-upkeep", plot.getLastUpkeep());
        
        sec.set("spawn-location", plot.getSpawnLocationString());
        sec.set("welcome-message", plot.getWelcomeMessage());
        sec.set("farewell-message", plot.getFarewellMessage());
        sec.set("description", plot.getDescription());
        
        if (plot.isForSale()) {
            sec.set("market.is-for-sale", true);
            sec.set("market.sale-price", plot.getSalePrice());
        }
        sec.set("plot-status", plot.getPlotStatus());

        // Flags
        ConfigurationSection flags = sec.createSection("flags");
        for (Map.Entry<String, Boolean> entry : plot.getFlags().entrySet()) {
            flags.set(entry.getKey(), entry.getValue());
        }

        // Roles
        ConfigurationSection roles = sec.createSection("roles");
        for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
            roles.set(entry.getKey().toString(), entry.getValue());
        }
    }

    // ==============================================================
    // --- ACCESSORS ---
    // ==============================================================

    @Override
    public List<Plot> getPlots(UUID owner) {
        return plotsByOwner.getOrDefault(owner, new ArrayList<>());
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        List<Plot> userPlots = plotsByOwner.get(owner);
        if (userPlots == null) return null;
        return userPlots.stream().filter(p -> p.getPlotId().equals(plotId)).findFirst().orElse(null);
    }

    @Override
    public Collection<Plot> getAllPlots() {
        return plotsByOwner.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    @Override
    public Collection<Plot> getPlotsForSale() {
        return getAllPlots().stream().filter(Plot::isForSale).collect(Collectors.toList());
    }

    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream().filter(p -> "AUCTION".equals(p.getPlotStatus())).collect(Collectors.toList());
    }

    @Override
    public Plot getPlotAt(Location loc) {
        String world = loc.getWorld().getName();
        String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
        
        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null) return null;
        
        Set<Plot> candidates = worldMap.get(key);
        if (candidates == null) return null;
        
        for (Plot p : candidates) {
            if (p.isInside(loc)) return p;
        }
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Plot ignore, String world, int x1, int z1, int x2, int z2) {
        // Fast check against cache
        // (In a real scenario, we'd optimize this with the chunk map, 
        // but iterating all plots in world is acceptable for small-medium servers)
        for (Plot p : getAllPlots()) {
            if (!p.getWorld().equals(world)) continue;
            if (ignore != null && p.getPlotId().equals(ignore.getPlotId())) continue;

            // AABB Overlap Check
            if (x1 <= p.getX2() && x2 >= p.getX1() && z1 <= p.getZ2() && z2 >= p.getZ1()) {
                return true;
            }
        }
        return false;
    }

    // ==============================================================
    // --- MODIFICATION ---
    // ==============================================================

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        UUID id = UUID.randomUUID();
        String ownerName = Bukkit.getOfflinePlayer(owner).getName();
        
        int x1 = Math.min(c1.getBlockX(), c2.getBlockX());
        int x2 = Math.max(c1.getBlockX(), c2.getBlockX());
        int z1 = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int z2 = Math.max(c1.getBlockZ(), c2.getBlockZ());

        Plot plot = new Plot(id, owner, ownerName, c1.getWorld().getName(), x1, z1, x2, z2);
        
        // Add to cache and save
        addPlot(plot);
    }

    @Override
    public void addPlot(Plot plot) {
        cachePlot(plot);
        savePlot(plot);
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        List<Plot> list = plotsByOwner.get(owner);
        if (list != null) {
            Plot removed = null;
            for (Plot p : list) {
                if (p.getPlotId().equals(plotId)) {
                    removed = p;
                    break;
                }
            }
            if (removed != null) {
                list.remove(removed);
                deIndexPlot(removed);
                
                // Remove from file
                if (config != null) {
                    config.set(plotId.toString(), null);
                    try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
                }
            }
        }
    }

    @Override
    public void removeAllPlots(UUID owner) {
        List<Plot> list = plotsByOwner.remove(owner);
        if (list != null) {
            for (Plot p : list) {
                deIndexPlot(p);
                config.set(p.getPlotId().toString(), null);
            }
            try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    @Override
    public void addPlayerRole(Plot plot, UUID playerUUID, String role) {
        plot.setRole(playerUUID, role);
        savePlot(plot);
    }

    @Override
    public void removePlayerRole(Plot plot, UUID playerUUID) {
        plot.removeRole(playerUUID);
        savePlot(plot);
    }

    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        // Remove from old owner cache
        List<Plot> oldList = plotsByOwner.get(plot.getOwner());
        if (oldList != null) oldList.remove(plot);
        
        // Update Plot
        plot.setOwner(newOwner);
        plot.setOwnerName(newOwnerName);
        
        // Add to new owner cache
        plotsByOwner.computeIfAbsent(newOwner, k -> new ArrayList<>()).add(plot);
        
        savePlot(plot);
    }

    @Override
    public void removeBannedPlots() {
        for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }
    
    // --- Indexing Helpers ---

    private void cachePlot(Plot plot) {
        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
        
        String w = plot.getWorld();
        int minX = plot.getX1() >> 4; int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4; int maxZ = plot.getZ2() >> 4;
        
        Map<String, Set<Plot>> worldMap = plotsByChunk.computeIfAbsent(w, k -> new ConcurrentHashMap<>());
        
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                worldMap.computeIfAbsent(x + "," + z, k -> new HashSet<>()).add(plot);
            }
        }
    }
    
    private void deIndexPlot(Plot plot) {
        String w = plot.getWorld();
        Map<String, Set<Plot>> worldMap = plotsByChunk.get(w);
        if (worldMap == null) return;

        int minX = plot.getX1() >> 4; int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4; int maxZ = plot.getZ2() >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Set<Plot> set = worldMap.get(x + "," + z);
                if (set != null) set.remove(plot);
            }
        }
    }

    @Override public boolean isDirty() { return isDirty; }
    @Override public void setDirty(boolean dirty) { this.isDirty = dirty; }

    // No-ops for SQL features
    @Override public void logWildernessBlock(Location loc, String o, String n, UUID p) {}
    @Override public void revertWildernessBlocks(long t, int l) {}
}
