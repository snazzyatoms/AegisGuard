package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YMLDataStore
 * - Manages plot data using plots.yml.
 * - Updated for v1.1.1 (Biomes, Descriptions, Identity).
 */
public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration data;

    // Caches
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();
    private volatile boolean isDirty = false;

    public YMLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    @Override
    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException ignored) {}

        this.data = YamlConfiguration.loadConfiguration(file);
        plotsByOwner.clear();
        plotsByChunk.clear();

        if (data.isConfigurationSection("plots")) {
            for (String ownerId : data.getConfigurationSection("plots").getKeys(false)) {
                UUID owner;
                try {
                    owner = UUID.fromString(ownerId);
                } catch (IllegalArgumentException ex) { continue; }

                String ownerPath = "plots." + ownerId;

                // Legacy Check
                if (data.isSet(ownerPath + ".x1")) {
                    migrateLegacy(owner);
                    continue;
                }

                // Standard Format
                for (String plotIdStr : data.getConfigurationSection(ownerPath).getKeys(false)) {
                    String path = ownerPath + "." + plotIdStr;
                    UUID plotId;
                    try { plotId = UUID.fromString(plotIdStr); } catch (IllegalArgumentException ex) { continue; }

                    String ownerName = data.getString(path + ".owner-name", "Unknown");
                    String world = data.getString(path + ".world");
                    int x1 = data.getInt(path + ".x1");
                    int z1 = data.getInt(path + ".z1");
                    int x2 = data.getInt(path + ".x2");
                    int z2 = data.getInt(path + ".z2");
                    long lastUpkeep = data.getLong(path + ".last-upkeep", System.currentTimeMillis());

                    if (world == null) continue;

                    Plot plot = new Plot(plotId, owner, ownerName, world, x1, z1, x2, z2, lastUpkeep);

                    // --- Core Identity (v1.1.1 Updated) ---
                    plot.setSpawnLocationFromString(data.getString(path + ".spawn-location"));
                    plot.setWelcomeMessage(data.getString(path + ".welcome-message"));
                    plot.setFarewellMessage(data.getString(path + ".farewell-message"));
                    plot.setEntryTitle(data.getString(path + ".entry-title"));
                    plot.setEntrySubtitle(data.getString(path + ".entry-subtitle"));
                    plot.setDescription(data.getString(path + ".description"));
                    plot.setCustomBiome(data.getString(path + ".biome"));

                    // --- Leveling ---
                    plot.setLevel(data.getInt(path + ".level", 1));
                    plot.setXp(data.getDouble(path + ".xp", 0.0));

                    // --- Marketplace ---
                    plot.setForSale(data.getBoolean(path + ".market.is-for-sale", false), data.getDouble(path + ".market.sale-price", 0.0));
                    plot.setForRent(data.getBoolean(path + ".market.is-for-rent", false), data.getDouble(path + ".market.rent-price", 0.0));
                    
                    String renterUUID = data.getString(path + ".market.renter-uuid");
                    if (renterUUID != null) {
                        try {
                            plot.setRenter(UUID.fromString(renterUUID), data.getLong(path + ".market.rent-expires", 0L));
                        } catch (Exception ignored) {}
                    }

                    plot.setPlotStatus(data.getString(path + ".plot-status", "ACTIVE"));
                    plot.setCurrentBid(data.getDouble(path + ".auction.current-bid", 0.0),
                                     data.getString(path + ".auction.current-bidder") != null ? UUID.fromString(data.getString(path + ".auction.current-bidder")) : null);

                    // --- Cosmetics ---
                    plot.setBorderParticle(data.getString(path + ".cosmetics.border-particle"));
                    plot.setAmbientParticle(data.getString(path + ".cosmetics.ambient-particle"));
                    plot.setEntryEffect(data.getString(path + ".cosmetics.entry-effect"));
                    
                    // --- Server Warps ---
                    if (data.getBoolean(path + ".is-server-warp", false)) {
                        plot.setServerWarp(true, data.getString(path + ".warp-name"), org.bukkit.Material.matchMaterial(data.getString(path + ".warp-icon", "BEACON")));
                    }
                    
                    // --- Zones ---
                    if (data.isConfigurationSection(path + ".zones")) {
                        for (String zoneName : data.getConfigurationSection(path + ".zones").getKeys(false)) {
                            String zPath = path + ".zones." + zoneName;
                            int zx1 = data.getInt(zPath + ".x1");
                            int zy1 = data.getInt(zPath + ".y1");
                            int zz1 = data.getInt(zPath + ".z1");
                            int zx2 = data.getInt(zPath + ".x2");
                            int zy2 = data.getInt(zPath + ".y2");
                            int zz2 = data.getInt(zPath + ".z2");
                            
                            Zone zone = new Zone(plot, zoneName, zx1, zy1, zz1, zx2, zy2, zz2);
                            zone.setRentPrice(data.getDouble(zPath + ".rent-price", 0.0));
                            
                            String zRenter = data.getString(zPath + ".renter");
                            if (zRenter != null) {
                                try {
                                    zone.rentTo(UUID.fromString(zRenter), data.getLong(zPath + ".rent-expires", 0) - System.currentTimeMillis());
                                } catch (Exception ignored) {}
                            }
                            plot.addZone(zone);
                        }
                    }

                    // --- Roles ---
                    if (data.isConfigurationSection(path + ".roles")) {
                        for (String uuidStr : data.getConfigurationSection(path + ".roles").getKeys(false)) {
                            try {
                                UUID t = UUID.fromString(uuidStr);
                                String role = data.getString(path + ".roles." + uuidStr, "guest");
                                plot.setRole(t, role);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    
                    // --- Flags ---
                    if (data.isConfigurationSection(path + ".flags")) {
                        for (String flagKey : data.getConfigurationSection(path + ".flags").getKeys(false)) {
                            boolean val = data.getBoolean(path + ".flags." + flagKey, true);
                            plot.setFlag(flagKey, val);
                        }
                    }

                    addPlot(plot);
                }
            }
        }
    }

    private synchronized void migrateLegacy(UUID owner) {
        // Legacy support stub
    }

    @Override
    public synchronized void save() {
        data.set("plots", null); // Wipe and rewrite (safest for YML)
        
        for (Map.Entry<UUID, List<Plot>> entry : plotsByOwner.entrySet()) {
            UUID owner = entry.getKey();
            for (Plot plot : entry.getValue()) {
                String path = "plots." + owner + "." + plot.getPlotId();

                data.set(path + ".owner-name", plot.getOwnerName());
                data.set(path + ".world", plot.getWorld());
                data.set(path + ".x1", plot.getX1());
                data.set(path + ".z1", plot.getZ1());
                data.set(path + ".x2", plot.getX2());
                data.set(path + ".z2", plot.getZ2());
                data.set(path + ".last-upkeep", plot.getLastUpkeepPayment());

                data.set(path + ".spawn-location", plot.getSpawnLocationString());
                data.set(path + ".welcome-message", plot.getWelcomeMessage());
                data.set(path + ".farewell-message", plot.getFarewellMessage());
                data.set(path + ".entry-title", plot.getEntryTitle());
                data.set(path + ".entry-subtitle", plot.getEntrySubtitle());
                data.set(path + ".description", plot.getDescription());
                data.set(path + ".biome", plot.getCustomBiome());
                
                data.set(path + ".level", plot.getLevel());
                data.set(path + ".xp", plot.getXp());
                
                // Market
                if (plot.isForSale()) {
                    data.set(path + ".market.is-for-sale", true);
                    data.set(path + ".market.sale-price", plot.getSalePrice());
                }
                if (plot.isForRent()) {
                    data.set(path + ".market.is-for-rent", true);
                    data.set(path + ".market.rent-price", plot.getRentPrice());
                }
                if (plot.getCurrentRenter() != null) {
                    data.set(path + ".market.renter-uuid", plot.getCurrentRenter().toString());
                    data.set(path + ".market.rent-expires", plot.getRentExpires());
                }
                
                if (!"ACTIVE".equals(plot.getPlotStatus())) data.set(path + ".plot-status", plot.getPlotStatus());
                if (plot.getCurrentBid() > 0) {
                    data.set(path + ".auction.current-bid", plot.getCurrentBid());
                    data.set(path + ".auction.current-bidder", plot.getCurrentBidder() != null ? plot.getCurrentBidder().toString() : null);
                }
                
                if (plot.getBorderParticle() != null) data.set(path + ".cosmetics.border-particle", plot.getBorderParticle());
                if (plot.getAmbientParticle() != null) data.set(path + ".cosmetics.ambient-particle", plot.getAmbientParticle());
                if (plot.getEntryEffect() != null) data.set(path + ".cosmetics.entry-effect", plot.getEntryEffect());
                
                if (plot.isServerWarp()) {
                    data.set(path + ".is-server-warp", true);
                    data.set(path + ".warp-name", plot.getWarpName());
                    data.set(path + ".warp-icon", plot.getWarpIcon().name());
                }
                
                // Zones
                if (!plot.getZones().isEmpty()) {
                    for (Zone zone : plot.getZones()) {
                        String zPath = path + ".zones." + zone.getName();
                        data.set(zPath + ".x1", zone.getX1());
                        data.set(zPath + ".y1", zone.getY1());
                        data.set(zPath + ".z1", zone.getZ1());
                        data.set(zPath + ".x2", zone.getX2());
                        data.set(zPath + ".y2", zone.getY2());
                        data.set(zPath + ".z2", zone.getZ2());
                        data.set(zPath + ".rent-price", zone.getRentPrice());
                        if (zone.getRenter() != null) {
                            data.set(zPath + ".renter", zone.getRenter().toString());
                            data.set(zPath + ".rent-expires", zone.getRentExpiration());
                        }
                    }
                }

                // Roles (Skip Owner)
                for (Map.Entry<UUID, String> roleEntry : plot.getPlayerRoles().entrySet()) {
                    if (roleEntry.getKey().equals(owner)) continue;
                    data.set(path + ".roles." + roleEntry.getKey().toString(), roleEntry.getValue());
                }

                // Flags
                for (Map.Entry<String, Boolean> flag : plot.getFlags().entrySet()) {
                    data.set(path + ".flags." + flag.getKey(), flag.getValue());
                }
            }
        }
        try { data.save(file); isDirty = false; } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void saveSync() { save(); }

    @Override
    public boolean isDirty() { return isDirty; }

    @Override
    public void setDirty(boolean dirty) { this.isDirty = dirty; }

    // --- Indexing Helpers (Chunk Lookups) ---
    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + ";" + (loc.getBlockX() >> 4) + ";" + (loc.getBlockZ() >> 4);
    }

    private void indexPlot(Plot plot) {
        Map<String, Set<Plot>> worldChunks = plotsByChunk.computeIfAbsent(plot.getWorld(), k -> new ConcurrentHashMap<>());
        for (String chunkKey : getChunksInArea(plot.getWorld(), plot.getX1(), plot.getZ1(), plot.getX2(), plot.getZ2())) {
            worldChunks.computeIfAbsent(chunkKey, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(plot);
        }
    }
    
    private void deIndexPlot(Plot plot) {
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(plot.getWorld());
        if (worldChunks == null) return;
        for (String chunkKey : getChunksInArea(plot.getWorld(), plot.getX1(), plot.getZ1(), plot.getX2(), plot.getZ2())) {
            Set<Plot> plots = worldChunks.get(chunkKey);
            if (plots != null) {
                plots.remove(plot);
                if (plots.isEmpty()) worldChunks.remove(chunkKey);
            }
        }
    }
    
    private Set<String> getChunksInArea(String world, int x1, int z1, int x2, int z2) {
        Set<String> keys = new HashSet<>();
        int cX1 = x1 >> 4; int cZ1 = z1 >> 4;
        int cX2 = x2 >> 4; int cZ2 = z2 >> 4;
        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                keys.add(world + ";" + x + ";" + z);
            }
        }
        return keys;
    }
    
    // --- Plot Management API ---

    @Override
    public List<Plot> getPlots(UUID owner) { return plotsByOwner.getOrDefault(owner, Collections.emptyList()); }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        return getPlots(owner).stream().filter(p -> p.getPlotId().equals(plotId)).findFirst().orElse(null);
    }

    @Override
    public Collection<Plot> getAllPlots() {
        return plotsByOwner.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plot> getPlotsForSale() {
        return getAllPlots().stream().filter(plot -> plot.isForSale() && "ACTIVE".equals(plot.getPlotStatus())).collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream().filter(plot -> "AUCTION".equals(plot.getPlotStatus())).collect(Collectors.toList());
    }

    @Override
    public Plot getPlotAt(Location loc) {
        String worldName = loc.getWorld().getName();
        String chunkKey = getChunkKey(loc);
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(worldName);
        if (worldChunks == null) return null;
        Set<Plot> plotsInChunk = worldChunks.get(chunkKey);
        if (plotsInChunk == null) return null;
        for (Plot plot : plotsInChunk) {
            if (plot.isInside(loc)) return plot;
        }
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Object plotToIgnore, String world, int x1, int z1, int x2, int z2) {
        Set<String> chunks = getChunksInArea(world, x1, z1, x2, z2);
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(world);
        if (worldChunks == null) return false;
        
        Set<Plot> plotsToTest = new HashSet<>();
        for (String chunkKey : chunks) {
            plotsToTest.addAll(worldChunks.getOrDefault(chunkKey, Collections.emptySet()));
        }
        
        // Use Object for signature compat, but check ID if it's a plot
        UUID ignoreId = null;
        if (plotToIgnore instanceof Plot) ignoreId = ((Plot) plotToIgnore).getPlotId();
        else if (plotToIgnore instanceof UUID) ignoreId = (UUID) plotToIgnore;

        for (Plot existingPlot : plotsToTest) {
            if (ignoreId != null && existingPlot.getPlotId().equals(ignoreId)) continue;
            
            boolean overlaps = !(x1 > existingPlot.getX2() || x2 < existingPlot.getX1() || z1 > existingPlot.getZ2() || z2 < existingPlot.getZ1());
            if (overlaps) return true;
        }
        return false;
    }

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        Plot plot = new Plot(
            UUID.randomUUID(), owner, op.getName() != null ? op.getName() : "Unknown",
            c1.getWorld().getName(), c1.getBlockX(), c1.getBlockZ(), c2.getBlockX(), c2.getBlockZ()
        );
        addPlot(plot);
    }

    @Override
    public void addPlot(Plot plot) {
        plotsByOwner.computeIfAbsent(plot.getOwner(), k -> new ArrayList<>()).add(plot);
        indexPlot(plot);
        isDirty = true;
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        List<Plot> owned = plotsByOwner.get(owner);
        if (owned == null) return;
        Plot toRemove = null;
        for(Plot p : owned) { if(p.getPlotId().equals(plotId)) { toRemove = p; break; } }
        if (toRemove != null) {
            owned.remove(toRemove);
            if (owned.isEmpty()) plotsByOwner.remove(owner);
            deIndexPlot(toRemove);
            isDirty = true;
        }
    }

    @Override
    public void removeAllPlots(UUID owner) {
        List<Plot> owned = plotsByOwner.remove(owner);
        if (owned != null && !owned.isEmpty()) {
            for (Plot plot : owned) deIndexPlot(plot);
            isDirty = true;
        }
    }
    
    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        UUID oldOwner = plot.getOwner();
        List<Plot> oldList = plotsByOwner.get(oldOwner);
        if (oldList != null) {
            oldList.remove(plot);
            if (oldList.isEmpty()) plotsByOwner.remove(oldOwner);
        }
        plot.internalSetOwner(newOwner, newOwnerName);
        plotsByOwner.computeIfAbsent(newOwner, k -> new ArrayList<>()).add(plot);
        isDirty = true;
    }

    @Override
    public void addPlayerRole(Plot plot, UUID playerUUID, String role) {
        plot.setRole(playerUUID, role);
        isDirty = true;
    }

    @Override
    public void removePlayerRole(Plot plot, UUID playerUUID) {
        plot.removeRole(playerUUID);
        isDirty = true;
    }

    @Override
    public void removeBannedPlots() {
        for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }
    
    // Stubs
    @Override public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {}
    @Override public void revertWildernessBlocks(long timestamp, int limit) {}
}
