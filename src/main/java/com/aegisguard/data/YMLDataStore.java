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
 * YMLDataStore
 * - This is your old PlotStore, refactored to implement IDataStore.
 * - It manages plot data storage using plots.yml.
 * - This is the "Ultimate" version, supporting all features.
 */
public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration data;

    // --- In-Memory Cache ---
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
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                String ownerPath = "plots." + ownerId;

                // Legacy single-plot format (plots.<owner>.*)
                if (data.isSet(ownerPath + ".x1")) {
                    migrateLegacy(owner);
                    continue;
                }

                // Multi-plot format (plots.<owner>.<plotId>.*)
                for (String plotIdStr : data.getConfigurationSection(ownerPath).getKeys(false)) {
                    String path = ownerPath + "." + plotIdStr;
                    UUID plotId;
                    try {
                        plotId = UUID.fromString(plotIdStr);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }

                    String ownerName = data.getString(path + ".owner-name", "Unknown");
                    String world = data.getString(path + ".world");
                    int x1 = data.getInt(path + ".x1");
                    int z1 = data.getInt(path + ".z1");
                    int x2 = data.getInt(path + ".x2");
                    int z2 = data.getInt(path + ".z2");
                    long lastUpkeep = data.getLong(path + ".last-upkeep", System.currentTimeMillis());

                    if (world == null) continue; // skip corrupt entries

                    Plot plot = new Plot(plotId, owner, ownerName, world, x1, z1, x2, z2, lastUpkeep);

                    // --- Load ALL Ultimate Fields ---
                    plot.setSpawnLocationFromString(data.getString(path + ".spawn-location"));
                    plot.setWelcomeMessage(data.getString(path + ".welcome-message"));
                    plot.setFarewellMessage(data.getString(path + ".farewell-message"));
                    
                    plot.setForSale(data.getBoolean(path + ".market.is-for-sale", false), data.getDouble(path + ".market.sale-price", 0.0));
                    plot.setForRent(data.getBoolean(path + ".market.is-for-rent", false), data.getDouble(path + ".market.rent-price", 0.0));
                    plot.setRenter(data.getString(path + ".market.renter-uuid") != null ? UUID.fromString(data.getString(path + ".market.renter-uuid")) : null, data.getLong(path + ".market.rent-expires", 0L));

                    plot.setPlotStatus(data.getString(path + ".plot-status", "ACTIVE"));
                    plot.setCurrentBid(data.getDouble(path + ".auction.current-bid", 0.0),
                                   data.getString(path + ".auction.current-bidder") != null ? UUID.fromString(data.getString(path + ".auction.current-bidder")) : null);

                    plot.setBorderParticle(data.getString(path + ".cosmetics.border-particle"));
                    plot.setAmbientParticle(data.getString(path + ".cosmetics.ambient-particle"));
                    plot.setEntryEffect(data.getString(path + ".cosmetics.entry-effect"));


                    // --- MIGRATION & LOADING LOGIC ---
                    if (data.isConfigurationSection(path + ".trusted")) {
                        // This is an OLD plot file. Migrate trusted users to "member".
                        for (String uuidStr : data.getConfigurationSection(path + ".trusted").getKeys(false)) {
                            try {
                                UUID t = UUID.fromString(uuidStr);
                                plot.setRole(t, "member"); // Auto-migrate to "member"
                            } catch (IllegalArgumentException ignored) {}
                        }
                    } else if (data.isConfigurationSection(path + ".roles")) {
                        // This is a NEW plot file. Load roles.
                        for (String uuidStr : data.getConfigurationSection(path + ".roles").getKeys(false)) {
                            try {
                                UUID t = UUID.fromString(uuidStr);
                                String role = data.getString(path + ".roles." + uuidStr, "guest");
                                plot.setRole(t, role);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    
                    // Flags
                    if (data.isConfigurationSection(path + ".flags")) {
                        for (String flagKey : data.getConfigurationSection(path + ".flags").getKeys(false)) {
                            boolean val = data.getBoolean(path + ".flags." + flagKey, true);
                            plot.setFlag(flagKey, val);
                        }
                    }

                    plotsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(plot);
                    indexPlot(plot);
                }
            }
        }
        
        // If we migrated any legacy plots, mark for save
        if (data.isSet("plots." + plotsByOwner.keySet().stream().findFirst().map(UUID::toString).orElse("") + ".trusted")) {
            isDirty = true;
        }
    }

    private synchronized void migrateLegacy(UUID owner) {
        String base = "plots." + owner;
        String ownerName = data.getString(base + ".owner-name", "Unknown");
        String world = data.getString(base + ".world");
        int x1 = data.getInt(base + ".x1");
        int z1 = data.getInt(base + ".z1");
        int x2 = data.getInt(base + ".x2");
        int z2 = data.getInt(base + ".z2");

        if (world == null) return;

        UUID plotId = UUID.randomUUID();
        Plot plot = new Plot(plotId, owner, ownerName, world, x1, z1, x2, z2);

        // --- MODIFIED: Migrate old trusted to "member" role ---
        if (data.isConfigurationSection(base + ".trusted")) {
            for (String uuidStr : data.getConfigurationSection(base + ".trusted").getKeys(false)) {
                try {
                    UUID t = UUID.fromString(uuidStr);
                    plot.setRole(t, "member"); // Auto-migrate to "member"
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        // migrate legacy flags (if present)
        if (data.isConfigurationSection(base + ".flags")) {
            for (String flagKey : data.getConfigurationSection(base + ".flags").getKeys(false)) {
                boolean val = data.getBoolean(base + ".flags." + flagKey, true);
                plot.setFlag(flagKey, val);
            }
        }

        plotsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(plot);
        indexPlot(plot);
        data.set(base, null); // clear old
        isDirty = true;
    }

    @Override
    public synchronized void save() {
        data.set("plots", null); // clear
        for (Map.Entry<UUID, List<Plot>> entry : plotsByOwner.entrySet()) {
            UUID owner = entry.getKey();
            List<Plot> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            for (Plot plot : list) {
                String path = "plots." + owner + "." + plot.getPlotId();

                // Update owner name on save
                OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
                plot.setOwnerName(op.getName() != null ? op.getName() : "Unknown");

                data.set(path + ".owner-name", plot.getOwnerName());
                data.set(path + ".world", plot.getWorld());
                data.set(path + ".x1", plot.getX1());
                data.set(path + ".z1", plot.getZ1());
                data.set(path + ".x2", plot.getX2());
                data.set(path + ".z2", plot.getZ2());
                data.set(path + ".last-upkeep", plot.getLastUpkeepPayment());

                // --- Save Welcome/TP ---
                data.set(path + ".spawn-location", plot.getSpawnLocationString());
                data.set(path + ".welcome-message", plot.getWelcomeMessage());
                data.set(path + ".farewell-message", plot.getFarewellMessage());
                
                // --- Save Marketplace ---
                data.set(path + ".market.is-for-sale", plot.isForSale() ? true : null); // null = delete if false
                data.set(path + ".market.sale-price", plot.isForSale() ? plot.getSalePrice() : null);
                data.set(path + ".market.is-for-rent", plot.isForRent() ? true : null);
                data.set(path + ".market.rent-price", plot.isForRent() ? plot.getRentPrice() : null);
                data.set(path + ".market.renter-uuid", plot.getCurrentRenter() != null ? plot.getCurrentRenter().toString() : null);
                data.set(path + ".market.rent-expires", plot.getCurrentRenter() != null ? plot.getRentExpires() : null);
                
                // --- Save Status ---
                data.set(path + ".plot-status", plot.getPlotStatus().equals("ACTIVE") ? null : plot.getPlotStatus());
                
                // --- Save Auction ---
                data.set(path + ".auction.current-bid", plot.getCurrentBid() > 0 ? plot.getCurrentBid() : null);
                data.set(path + ".auction.current-bidder", plot.getCurrentBidder() != null ? plot.getCurrentBidder().toString() : null);
                
                // --- Save Cosmetics ---
                data.set(path + ".cosmetics.border-particle", plot.getBorderParticle());
                data.set(path + ".cosmetics.ambient-particle", plot.getAmbientParticle());
                data.set(path + ".cosmetics.entry-effect", plot.getEntryEffect());

                // --- MODIFIED: Save Roles ---
                data.set(path + ".trusted", null); // Clear the old "trusted" section
                data.set(path + ".roles", null);   // Clear roles before saving
                for (Map.Entry<UUID, String> roleEntry : plot.getPlayerRoles().entrySet()) {
                    // Don't save the owner, they are implied
                    if (roleEntry.getKey().equals(owner)) continue;
                    data.set(path + ".roles." + roleEntry.getKey().toString(), roleEntry.getValue());
                }

                // --- MODIFIED: Save Flags ---
                data.set(path + ".flags", null); // Clear flags before saving
                for (Map.Entry<String, Boolean> flag : plot.getFlags().entrySet()) {
                    data.set(path + ".flags." + flag.getKey(), flag.getValue());
                }
            }
        }
        try {
            data.save(file);
            isDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveSync() {
        save();
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    // --- Caching/Indexing Methods ---

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
                if (plots.isEmpty()) {
                    worldChunks.remove(chunkKey);
                }
            }
        }
    }
    
    private Set<String> getChunksInArea(String world, int x1, int z1, int x2, int z2) {
        Set<String> keys = new HashSet<>();
        int cX1 = x1 >> 4;
        int cZ1 = z1 >> 4;
        int cX2 = x2 >> 4;
        int cZ2 = z2 >> 4;
        for (int x = cX1; x <= cX2; x++) {
            for (int z = cZ1; z <= cZ2; z++) {
                keys.add(world + ";" + x + ";" + z);
            }
        }
        return keys;
    }
    
    // --- Plot Management API ---

    @Override
    public List<Plot> getPlots(UUID owner) {
        return plotsByOwner.getOrDefault(owner, Collections.emptyList());
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        return getPlots(owner).stream()
                .filter(p -> p.getPlotId().equals(plotId))
                .findFirst().orElse(null);
    }

    @Override
    public Collection<Plot> getAllPlots() {
        return plotsByOwner.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plot> getPlotsForSale() {
        return getAllPlots().stream()
                .filter(plot -> plot.isForSale() && "ACTIVE".equals(plot.getPlotStatus()))
                .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream()
                .filter(plot -> "AUCTION".equals(plot.getPlotStatus()))
                .collect(Collectors.toList());
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
            if (plot.isInside(loc)) {
                return plot;
            }
        }
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Plot plotToIgnore, String world, int x1, int z1, int x2, int z2) {
        Set<String> chunks = getChunksInArea(world, x1, z1, x2, z2);
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(world);
        if (worldChunks == null) return false;

        Set<Plot> plotsToTest = new HashSet<>();
        for (String chunkKey : chunks) {
            plotsToTest.addAll(worldChunks.getOrDefault(chunkKey, Collections.emptySet()));
        }

        if (plotToIgnore != null) {
            plotsToTest.remove(plotToIgnore);
        }
        
        if (plotsToTest.isEmpty()) return false;

        for (Plot existingPlot : plotsToTest) {
            boolean overlaps = !(x1 > existingPlot.getX2() || x2 < existingPlot.getX1() ||
                                 z1 > existingPlot.getZ2() || z2 < existingPlot.getZ1());
            if (overlaps) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        Plot plot = new Plot(
            UUID.randomUUID(),
            owner,
            op.getName() != null ? op.getName() : "Unknown",
            c1.getWorld().getName(),
            c1.getBlockX(), c1.getBlockZ(),
            c2.getBlockX(), c2.getBlockZ()
        );
        addPlot(plot); // Use the unified add method
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
        for(Plot p : owned) {
            if(p.getPlotId().equals(plotId)) {
                toRemove = p;
                break;
            }
        }
        
        if (toRemove != null) {
            owned.remove(toRemove);
            if (owned.isEmpty()) {
                plotsByOwner.remove(owner);
            }
            deIndexPlot(toRemove);
            isDirty = true;
        }
    }

    @Override
    public void removeAllPlots(UUID owner) {
        List<Plot> owned = plotsByOwner.remove(owner);
        if (owned != null && !owned.isEmpty()) {
            for (Plot plot : owned) {
                deIndexPlot(plot);
            }
            isDirty = true;
        }
    }
    
    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        UUID oldOwner = plot.getOwner();
        
        // 1. Remove from old owner's list
        List<Plot> oldList = plotsByOwner.get(oldOwner);
        if (oldList != null) {
            oldList.remove(plot);
            if (oldList.isEmpty()) {
                plotsByOwner.remove(oldOwner);
            }
        }
        
        // 2. Update plot object internals
        plot.internalSetOwner(newOwner, newOwnerName);
        
        // 3. Add to new owner's list
        plotsByOwner.computeIfAbsent(newOwner, k -> new ArrayList<>()).add(plot);
        
        // 4. Mark for save
        isDirty = true;
    }

    /* -----------------------------
     * Role Management API
     * ----------------------------- */
     
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

    /* -----------------------------
     * Admin Helpers
     * ----------------------------- */
     
    @Override
    public void removeBannedPlots() {
        for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }
    
    // --- NEW: Wilderness Revert Methods (STUBS) ---
    
    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        // Not supported in YML mode.
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        // Not supported in YML mode.
    }
}
