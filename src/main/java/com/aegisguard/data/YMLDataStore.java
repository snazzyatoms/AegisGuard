package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.flags.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * YMLDataStore (v1.2.2+)
 * - Manages plot data using 'plots.yml'.
 * - Implements strict IDataStore contract for 1.2.x.
 *
 * Upgrades in this version:
 *  ✅ getPlots(UUID) NEVER returns null (clean contract)
 *  ✅ Thread-safe owner cache (Set-backed) for Paper/Folia safety
 *  ✅ Fast overlap checks using chunk index (scales better on large servers)
 *  ✅ Defensive de-duplication by plotId
 */
public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration config;

    // --- CACHES ---
    // Thread-safe: avoids ConcurrentHashMap + ArrayList concurrency hazards
    private final Map<UUID, Set<Plot>> plotsByOwner = new ConcurrentHashMap<>();

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
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {}
        }

        config = YamlConfiguration.loadConfiguration(file);
        int count = 0;

        for (String key : config.getKeys(false)) {
            try {
                UUID plotId = UUID.fromString(key);
                ConfigurationSection sec = config.getConfigurationSection(key);
                if (sec == null) continue;

                UUID ownerId = UUID.fromString(sec.getString("owner"));
                String ownerName = sec.getString("owner-name", "Unknown");
                String worldName = sec.getString("world");
                if (worldName == null || worldName.isEmpty()) continue;

                // If world missing/unloaded, still load plot data (do not discard claims)
                // NOTE: getPlotAt will only work for loaded worlds, but data remains intact.

                int x1 = sec.getInt("x1");
                int z1 = sec.getInt("z1");
                int x2 = sec.getInt("x2");
                int z2 = sec.getInt("z2");

                Plot plot = new Plot(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2);

                // Progression
                plot.setLevel(sec.getInt("level", 1));
                plot.setXp(sec.getDouble("xp", 0.0));
                plot.setLastUpkeep(sec.getLong("last-upkeep", System.currentTimeMillis()));
                plot.setMaxMembers(sec.getInt("max-members", 2));

                // Visuals
                plot.setSpawnLocationFromString(sec.getString("spawn-location"));
                plot.setWelcomeMessage(sec.getString("welcome-message"));
                plot.setFarewellMessage(sec.getString("farewell-message"));
                plot.setEntryTitle(sec.getString("entry-title"));
                plot.setEntrySubtitle(sec.getString("entry-subtitle"));
                plot.setDescription(sec.getString("description"));
                plot.setCustomBiome(sec.getString("custom-biome"));

                // Economy & Market
                if (sec.isConfigurationSection("market")) {
                    ConfigurationSection market = sec.getConfigurationSection("market");
                    if (market != null) {
                        if (market.getBoolean("is-for-sale", false)) {
                            plot.setForSale(true, market.getDouble("sale-price", 0.0));
                        }
                        if (market.getBoolean("is-for-rent", false)) {
                            plot.setForRent(true, market.getDouble("rent-price", 0.0));
                        }
                        String renterStr = market.getString("current-renter");
                        if (renterStr != null && !renterStr.isEmpty()) {
                            try {
                                UUID renter = UUID.fromString(renterStr);
                                long expires = market.getLong("rent-expires", 0L);
                                plot.setRenter(renter, expires);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else {
                    if (sec.getBoolean("market.is-for-sale", false)) {
                        plot.setForSale(true, sec.getDouble("market.sale-price", 0.0));
                    }
                }

                plot.setPlotStatus(sec.getString("plot-status", "ACTIVE"));

                // Auction
                if (sec.isConfigurationSection("auction")) {
                    ConfigurationSection auction = sec.getConfigurationSection("auction");
                    if (auction != null) {
                        double bid = auction.getDouble("current-bid", 0.0);
                        String bidderStr = auction.getString("current-bidder");
                        UUID bidder = null;
                        if (bidderStr != null && !bidderStr.isEmpty()) {
                            try {
                                bidder = UUID.fromString(bidderStr);
                            } catch (IllegalArgumentException ignored) {}
                        }
                        plot.setCurrentBid(bid, bidder);
                    }
                }

                // Flags
                if (sec.isConfigurationSection("flags")) {
                    ConfigurationSection flags = sec.getConfigurationSection("flags");
                    if (flags != null) {
                        for (String f : flags.getKeys(false)) {
                            plot.setFlag(f, flags.getBoolean(f));
                        }
                    }
                }

                // Roles
                if (sec.isConfigurationSection("roles")) {
                    ConfigurationSection roles = sec.getConfigurationSection("roles");
                    if (roles != null) {
                        for (String pUuid : roles.getKeys(false)) {
                            try {
                                plot.setRole(UUID.fromString(pUuid), roles.getString(pUuid));
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // Role flag overrides
                String roleFlagsBlob = sec.getString("role-flags");
                if (roleFlagsBlob != null && !roleFlagsBlob.isEmpty()) {
                    plot.deserializeRoleFlags(roleFlagsBlob);
                }

                // Likes
                for (String uuidStr : sec.getStringList("liked-by")) {
                    try {
                        plot.toggleLike(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                // Bans
                for (String uuidStr : sec.getStringList("banned")) {
                    try {
                        plot.addBan(UUID.fromString(uuidStr));
                    } catch (IllegalArgumentException ignored) {}
                }

                // Cosmetics
                if (sec.isConfigurationSection("cosmetics")) {
                    ConfigurationSection cos = sec.getConfigurationSection("cosmetics");
                    if (cos != null) {
                        plot.setBorderParticle(cos.getString("border-particle"));
                        plot.setAmbientParticle(cos.getString("ambient-particle"));
                        plot.setEntryEffect(cos.getString("entry-effect"));
                    }
                }

                // Warp
                if (sec.isConfigurationSection("warp")) {
                    ConfigurationSection warp = sec.getConfigurationSection("warp");
                    if (warp != null) {
                        boolean isWarp = warp.getBoolean("is-server-warp", false);
                        String warpName = warp.getString("warp-name");
                        String iconName = warp.getString("warp-icon");
                        Material icon = null;
                        if (iconName != null && !iconName.isEmpty()) {
                            try {
                                icon = Material.valueOf(iconName);
                            } catch (IllegalArgumentException ignored) {}
                        }
                        plot.setServerWarp(isWarp, warpName, icon);
                    }
                }

                // Zones
                if (sec.isConfigurationSection("zones")) {
                    ConfigurationSection zonesSec = sec.getConfigurationSection("zones");
                    if (zonesSec != null) {
                        for (String zoneName : zonesSec.getKeys(false)) {
                            ConfigurationSection z = zonesSec.getConfigurationSection(zoneName);
                            if (z == null) continue;

                            int zx1 = z.getInt("x1");
                            int zy1 = z.getInt("y1");
                            int zz1 = z.getInt("z1");
                            int zx2 = z.getInt("x2");
                            int zy2 = z.getInt("y2");
                            int zz2 = z.getInt("z2");

                            Zone zone = new Zone(plot, zoneName, zx1, zy1, zz1, zx2, zy2, zz2);
                            zone.setRentPrice(z.getDouble("rent-price", 0.0));

                            String renterStr = z.getString("renter");
                            long exp = z.getLong("rent-expiration", 0L);
                            if (renterStr != null && !renterStr.isEmpty()) {
                                try {
                                    UUID renter = UUID.fromString(renterStr);
                                    long now = System.currentTimeMillis();
                                    if (exp > now) zone.rentTo(renter, exp - now);
                                } catch (IllegalArgumentException ignored) {}
                            }

                            plot.addZone(zone);
                        }
                    }
                }

                cachePlot(plot);
                count++;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load plot: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + count + " plots from YML.");
        isDirty = false;
    }

    @Override
    public void save() {
        if (config == null) config = YamlConfiguration.loadConfiguration(file);

        // Clear file content (so deleted plots don't linger)
        for (String k : new HashSet<>(config.getKeys(false))) {
            config.set(k, null);
        }

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
        if (plot == null) return;
        if (config == null) config = YamlConfiguration.loadConfiguration(file);

        writePlotToConfig(plot);
        try {
            config.save(file);
            isDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writePlotToConfig(Plot plot) {
        String key = plot.getPlotId().toString();

        // Replace section cleanly
        config.set(key, null);
        ConfigurationSection sec = config.createSection(key);

        // Core
        sec.set("owner", plot.getOwner().toString());
        sec.set("owner-name", plot.getOwnerName());
        sec.set("world", plot.getWorld());
        sec.set("x1", plot.getX1());
        sec.set("z1", plot.getZ1());
        sec.set("x2", plot.getX2());
        sec.set("z2", plot.getZ2());

        // Progression
        sec.set("level", plot.getLevel());
        sec.set("xp", plot.getXp());
        sec.set("last-upkeep", plot.getLastUpkeep());
        sec.set("max-members", plot.getMaxMembers());

        // Visuals
        sec.set("spawn-location", plot.getSpawnLocationString());
        sec.set("welcome-message", plot.getWelcomeMessage());
        sec.set("farewell-message", plot.getFarewellMessage());
        sec.set("entry-title", plot.getEntryTitle());
        sec.set("entry-subtitle", plot.getEntrySubtitle());
        sec.set("description", plot.getDescription());
        sec.set("custom-biome", plot.getCustomBiome());

        // Market
        ConfigurationSection market = sec.createSection("market");
        market.set("is-for-sale", plot.isForSale());
        market.set("sale-price", plot.getSalePrice());
        market.set("is-for-rent", plot.isForRent());
        market.set("rent-price", plot.getRentPrice());
        UUID renter = plot.getCurrentRenter();
        market.set("current-renter", renter != null ? renter.toString() : null);
        market.set("rent-expires", plot.getRentExpires());

        sec.set("plot-status", plot.getPlotStatus());

        // Auction
        ConfigurationSection auction = sec.createSection("auction");
        auction.set("current-bid", plot.getCurrentBid());
        UUID bidder = plot.getCurrentBidder();
        auction.set("current-bidder", bidder != null ? bidder.toString() : null);

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

        // Role flags blob
        String roleFlagsBlob = plot.serializeRoleFlags();
        sec.set("role-flags", roleFlagsBlob.isEmpty() ? null : roleFlagsBlob);

        // Likes
        List<String> liked = plot.getLikedBy().stream().map(UUID::toString).collect(Collectors.toList());
        sec.set("liked-by", liked.isEmpty() ? null : liked);

        // Bans
        List<String> banned = plot.getBannedPlayers().stream().map(UUID::toString).collect(Collectors.toList());
        sec.set("banned", banned.isEmpty() ? null : banned);

        // Cosmetics
        ConfigurationSection cos = sec.createSection("cosmetics");
        cos.set("border-particle", plot.getBorderParticle());
        cos.set("ambient-particle", plot.getAmbientParticle());
        cos.set("entry-effect", plot.getEntryEffect());

        // Warp
        ConfigurationSection warp = sec.createSection("warp");
        warp.set("is-server-warp", plot.isServerWarp());
        warp.set("warp-name", plot.getWarpName());
        warp.set("warp-icon", plot.getWarpIcon() != null ? plot.getWarpIcon().name() : null);

        // Zones
        ConfigurationSection zonesSec = sec.createSection("zones");
        for (Zone zone : plot.getZones()) {
            ConfigurationSection z = zonesSec.createSection(zone.getName());
            z.set("x1", zone.getX1());
            z.set("y1", zone.getY1());
            z.set("z1", zone.getZ1());
            z.set("x2", zone.getX2());
            z.set("y2", zone.getY2());
            z.set("z2", zone.getZ2());
            z.set("rent-price", zone.getRentPrice());
            UUID zr = zone.getRenter();
            z.set("renter", zr != null ? zr.toString() : null);
            z.set("rent-expiration", zone.getRentExpiration());
        }
    }

    // ==============================================================
    // --- ACCESSORS ---
    // ==============================================================

    /**
     * ✅ NEVER NULL contract:
     * Always returns a snapshot list (safe to iterate).
     */
    @Override
    public List<Plot> getPlots(UUID owner) {
        if (owner == null) return Collections.emptyList();
        Set<Plot> set = plotsByOwner.get(owner);
        if (set == null || set.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(set);
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        if (owner == null || plotId == null) return null;
        Set<Plot> set = plotsByOwner.get(owner);
        if (set == null || set.isEmpty()) return null;
        for (Plot p : set) {
            if (p != null && plotId.equals(p.getPlotId())) return p;
        }
        return null;
    }

    @Override
    public Collection<Plot> getAllPlots() {
        List<Plot> all = new ArrayList<>();
        for (Set<Plot> set : plotsByOwner.values()) {
            if (set != null && !set.isEmpty()) all.addAll(set);
        }
        return all;
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
        if (loc == null || loc.getWorld() == null) return null;

        String world = loc.getWorld().getName();
        String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);

        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null) return null;

        Set<Plot> candidates = worldMap.get(key);
        if (candidates == null || candidates.isEmpty()) return null;

        for (Plot p : candidates) {
            if (p != null && p.isInside(loc)) return p;
        }
        return null;
    }

    /**
     * ✅ Big-server friendly:
     * Uses chunk index instead of scanning every plot on the server.
     */
    @Override
    public boolean isAreaOverlapping(Plot ignore, String world, int x1, int z1, int x2, int z2) {
        if (world == null || world.isEmpty()) return false;

        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null || worldMap.isEmpty()) return false;

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int cMinX = minX >> 4;
        int cMaxX = maxX >> 4;
        int cMinZ = minZ >> 4;
        int cMaxZ = maxZ >> 4;

        // Deduplicate candidates
        Set<Plot> candidates = new HashSet<>();

        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                Set<Plot> set = worldMap.get(cx + "," + cz);
                if (set != null && !set.isEmpty()) candidates.addAll(set);
            }
        }

        for (Plot p : candidates) {
            if (p == null) continue;
            if (!world.equals(p.getWorld())) continue;
            if (ignore != null && ignore.getPlotId().equals(p.getPlotId())) continue;

            // AABB overlap
            if (minX <= p.getX2() && maxX >= p.getX1() && minZ <= p.getZ2() && maxZ >= p.getZ1()) {
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
        addPlot(plot);
    }

    @Override
    public void addPlot(Plot plot) {
        if (plot == null) return;
        isDirty = true;

        // Defensive: ensure no duplicate plotId exists anywhere
        removePlotByIdEverywhere(plot.getPlotId());

        cachePlot(plot);
        savePlot(plot);
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        if (owner == null || plotId == null) return;
        isDirty = true;

        Plot removed = null;
        Set<Plot> set = plotsByOwner.get(owner);

        if (set != null && !set.isEmpty()) {
            for (Plot p : set) {
                if (p != null && plotId.equals(p.getPlotId())) {
                    removed = p;
                    break;
                }
            }
            if (removed != null) {
                set.remove(removed);
            }
            if (set.isEmpty()) plotsByOwner.remove(owner);
        }

        if (removed != null) {
            deIndexPlot(removed);
        }

        if (config == null) config = YamlConfiguration.loadConfiguration(file);
        config.set(plotId.toString(), null);
        try {
            config.save(file);
            isDirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeAllPlots(UUID owner) {
        if (owner == null) return;
        isDirty = true;

        Set<Plot> set = plotsByOwner.remove(owner);
        if (set != null) {
            if (config == null) config = YamlConfiguration.loadConfiguration(file);

            for (Plot p : set) {
                if (p == null) continue;
                deIndexPlot(p);
                config.set(p.getPlotId().toString(), null);
            }

            try {
                config.save(file);
                isDirty = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void addPlayerRole(Plot plot, UUID playerUUID, String role) {
        if (plot == null || playerUUID == null) return;
        isDirty = true;
        plot.setRole(playerUUID, role);
        savePlot(plot);
    }

    @Override
    public void removePlayerRole(Plot plot, UUID playerUUID) {
        if (plot == null || playerUUID == null) return;
        isDirty = true;
        plot.removeRole(playerUUID);
        savePlot(plot);
    }

    /**
     * FIXED: Ownership transfer must reset plot internals (roles/bans/likes/role-flags/title/desc).
     * Also prevents duplicate cache entries.
     */
    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        if (plot == null || newOwner == null) return;
        isDirty = true;

        UUID oldOwner = plot.getOwner();
        if (oldOwner != null && oldOwner.equals(newOwner)) {
            plot.setOwnerName(newOwnerName);
            savePlot(plot);
            return;
        }

        // Remove from old owner cache
        if (oldOwner != null) {
            Set<Plot> oldSet = plotsByOwner.get(oldOwner);
            if (oldSet != null) {
                oldSet.remove(plot);
                if (oldSet.isEmpty()) plotsByOwner.remove(oldOwner);
            }
        }

        // Remove from chunk index
        deIndexPlot(plot);

        // Reset internals + swap owner/name
        plot.internalSetOwner(newOwner, newOwnerName);

        // Re-add cleanly (dedupe-by-id)
        removePlotByIdEverywhere(plot.getPlotId());
        cachePlot(plot);

        // Persist immediately
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
        // Owner index
        Set<Plot> ownerSet = plotsByOwner.computeIfAbsent(plot.getOwner(), k -> ConcurrentHashMap.newKeySet());
        ownerSet.add(plot);

        // Chunk index
        String w = plot.getWorld();
        int minX = plot.getX1() >> 4;
        int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxZ = plot.getZ2() >> 4;

        Map<String, Set<Plot>> worldMap = plotsByChunk.computeIfAbsent(w, k -> new ConcurrentHashMap<>());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                worldMap.computeIfAbsent(x + "," + z, k -> ConcurrentHashMap.newKeySet()).add(plot);
            }
        }
    }

    private void deIndexPlot(Plot plot) {
        String w = plot.getWorld();
        Map<String, Set<Plot>> worldMap = plotsByChunk.get(w);
        if (worldMap == null) return;

        int minX = plot.getX1() >> 4;
        int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxZ = plot.getZ2() >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String k = x + "," + z;
                Set<Plot> set = worldMap.get(k);
                if (set != null) {
                    set.remove(plot);
                    if (set.isEmpty()) worldMap.remove(k);
                }
            }
        }
        if (worldMap.isEmpty()) plotsByChunk.remove(w);
    }

    /**
     * Defensive de-duplication:
     * If an old Plot instance with the same plotId exists (different object),
     * remove it from caches/indexes before caching the new one.
     */
    private void removePlotByIdEverywhere(UUID plotId) {
        if (plotId == null) return;

        // Remove from owner sets
        for (Map.Entry<UUID, Set<Plot>> entry : plotsByOwner.entrySet()) {
            Set<Plot> set = entry.getValue();
            if (set == null || set.isEmpty()) continue;

            Plot found = null;
            for (Plot p : set) {
                if (p != null && plotId.equals(p.getPlotId())) {
                    found = p;
                    break;
                }
            }

            if (found != null) {
                set.remove(found);
                deIndexPlot(found);
            }
        }
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    // No-ops for SQL-specific features
    @Override public void logWildernessBlock(Location loc, String o, String n, UUID p) {}
    @Override public void revertWildernessBlocks(long t, int l) {}

    // ----------------------------------------
    // --- ROLE FLAG OVERRIDES (YML) ----------
    // ----------------------------------------

    @Override
    public TriState getRoleFlagState(Plot plot, String roleName, String flagKey) {
        if (plot == null) return TriState.INHERIT;
        return plot.getRoleFlagState(roleName, flagKey);
    }

    @Override
    public void setRoleFlagState(Plot plot, String roleName, String flagKey, TriState state) {
        if (plot == null) return;

        isDirty = true;
        plot.setRoleFlagState(roleName, flagKey, state == null ? TriState.INHERIT : state);
        savePlot(plot);
    }
}
