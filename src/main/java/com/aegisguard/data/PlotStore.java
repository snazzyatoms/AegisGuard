package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlotStore
 * - Multi-plot support (per player configurable via config)
 * - Stores: owner, bounds, trusted, flags
 * - Persists to plots.yml
 * - Optional: sweep & remove banned players' plots
 *
 * --- UPGRADE NOTES ---
 * - Implemented spatial hashing (plotsByChunk) for O(1) getPlotAt() lookups.
 * - Removed all synchronous save() calls and replaced with a 'dirty' flag for async auto-saving.
 * - Added 'synchronized' to methods to ensure thread-safety.
 * - Moved Bukkit.getOfflinePlayer() calls out of save() and into modifier methods.
 */
public class PlotStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration data;

    // --- UPGRADED DATA STRUCTURES ---
    // Map<OwnerUUID, List<Plot>>
    private final Map<UUID, List<Plot>> plotsByOwner = new ConcurrentHashMap<>();

    // Spatial Hash Map for O(1) lookups
    // Map<WorldName, Map<ChunkCoordString, Set<Plot>>>
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();

    // --- NEW ---
    private volatile boolean isDirty = false; // 'volatile' ensures visibility across threads

    // ... existing PlotStore constructor ...
    public PlotStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
        load();
        // Note: Banned plot removal is now handled asynchronously on startup in AegisGuard.java
    }

    /* -----------------------------
     * Data Structures
     * (Plot inner class)
     * ----------------------------- */
    public static class Plot {
        // ... all existing fields (plotId, owner, etc) ...
// ... existing code ...
        private final UUID plotId;
        private final UUID owner;
        private String ownerName;
// ... existing code ...
        private final String world;
        private final int x1, z1, x2, z2;
        private final Set<UUID> trusted = new HashSet<>();
// ... existing code ...
        private final Map<UUID, String> trustedNames = new HashMap<>();
        private final Map<String, Boolean> flags = new HashMap<>();

        // ... existing Plot constructor ...
        public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
            this.plotId = plotId;
// ... existing code ...
            this.ownerName = ownerName;
            this.world = world;
            this.x1 = Math.min(x1, x2);
// ... existing code ...
            this.z2 = Math.max(z1, z2);

            // Default protections ON (can be overridden later)
// ... existing code ...
            flags.put("farm", true);
        }

        // ... existing getters (getPlotId, getOwner, etc) ...
        public UUID getPlotId() { return plotId; }
// ... existing code ...
        public int getZ2() { return z2; }

        public Set<UUID> getTrusted() { return trusted; }
// ... existing code ...
        public Map<UUID, String> getTrustedNames() { return trustedNames; }

        // ... existing isInside() method ...
        public boolean isInside(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
// ... existing code ...
            if (!loc.getWorld().getName().equals(world)) return false;
            int x = loc.getBlockX();
// ... existing code ...
            int z = loc.getBlockZ();
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }

        // ... existing Flag methods ...
        public boolean getFlag(String key, boolean def) { return flags.getOrDefault(key, def); }
// ... existing code ...
        public Map<String, Boolean> getFlags() { return flags; }
    }

    /* -----------------------------
     * Load / Save
     * ----------------------------- */
    public synchronized void load() {
        try {
// ... existing code ...
        // ... existing code ...
        } catch (IOException ignored) {}
        this.data = YamlConfiguration.loadConfiguration(file);
        plotsByOwner.clear();
        plotsByChunk.clear(); // --- NEW ---

        if (data.isConfigurationSection("plots")) {
// ... existing code ...
            // ... existing code ...
            for (String ownerId : data.getConfigurationSection("plots").getKeys(false)) {
                UUID owner;
// ... existing code ...
                // ... existing code ...
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                String ownerPath = "plots." + ownerId;

                // Legacy single-plot format (plots.<owner>.*)
// ... existing code ...
                if (data.isSet(ownerPath + ".x1")) {
                    migrateLegacy(owner);
                    continue;
                }

                // Multi-plot format (plots.<owner>.<plotId>.*)
// ... existing code ...
                for (String plotIdStr : data.getConfigurationSection(ownerPath).getKeys(false)) {
// ... existing code ...
                    // ... existing code ...
                    try {
                        plotId = UUID.fromString(plotIdStr);
                    } catch (IllegalArgumentException ex) {
// ... existing code ...
                    }

                    String ownerName = data.getString(path + ".owner-name", "Unknown");
// ... existing code ...
                    // ... existing code ...
                    int z2 = data.getInt(path + ".z2");

                    if (world == null) continue; // skip corrupt entries

                    Plot plot = new Plot(plotId, owner, ownerName, world, x1, z1, x2, z2);

                    // Trusted
// ... existing code ...
                    if (data.isConfigurationSection(path + ".trusted")) {
                        for (String uuidStr : data.getConfigurationSection(path + ".trusted").getKeys(false)) {
// ... existing code ...
                            // ... existing code ...
                            try {
                                UUID t = UUID.fromString(uuidStr);
// ... existing code ...
                                // ... existing code ...
                                plot.getTrustedNames().put(t, tName);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }

                    // Flags
// ... existing code ...
                    if (data.isConfigurationSection(path + ".flags")) {
                        for (String flagKey : data.getConfigurationSection(path + ".flags").getKeys(false)) {
// ... existing code ...
                            // ... existing code ...
                            plot.setFlag(flagKey, val);
                        }
                    }

                    // --- MODIFIED ---
                    plotsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(plot);
                    indexPlot(plot); // --- NEW ---
                }
            }
        }

        // --- REMOVED ---
        // removeBannedPlots() is now called async from AegisGuard.java onEnable()
        // to avoid main-thread lag on startup.
    }

    // --- MODIFIED ---
    private synchronized void migrateLegacy(UUID owner) {
// ... existing code ...
        // ... existing code ...
        if (world == null) return;

        UUID plotId = UUID.randomUUID();
// ... existing code ...
        // ... existing code ...
        Plot plot = new Plot(plotId, owner, ownerName, world, x1, z1, x2, z2);

        // ... migrate legacy trusted (if present) ...
// ... existing code ...
        // ... existing code ...
        // ... migrate legacy flags (if present) ...
        if (data.isConfigurationSection(base + ".flags")) {
// ... existing code ...
            // ... existing code ...
        }

        // --- MODIFIED ---
        plotsByOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(plot);
        indexPlot(plot); // --- NEW ---
        data.set(base, null); // clear old
        isDirty = true; // --- NEW ---
        // save() removed, will be handled by auto-saver
    }

    // --- MODIFIED ---
    public synchronized void save() {
        data.set("plots", null); // clear
        for (Map.Entry<UUID, List<Plot>> entry : plotsByOwner.entrySet()) {
            UUID owner = entry.getKey();
// ... existing code ...
            if (list == null || list.isEmpty()) continue;

            for (Plot plot : list) {
                String path = "plots." + owner + "." + plot.getPlotId();

                // --- MODIFIED ---
                // Names are now set on creation/trust, not during save.
                // This removes Bukkit API calls from the save loop.
                data.set(path + ".owner-name", plot.getOwnerName());
                data.set(path + ".world", plot.getWorld());
// ... existing code ...
                data.set(path + ".z2", plot.getZ2());

                // --- MODIFIED ---
                // We just save the names we have.
                data.set(path + ".trusted", null); // Clear old before saving
                for (Map.Entry<UUID, String> tn : plot.getTrustedNames().entrySet()) {
                    data.set(path + ".trusted." + tn.getKey(), tn.getValue());
                }

                data.set(path + ".flags", null); // Clear old before saving
                for (Map.Entry<String, Boolean> flag : plot.getFlags().entrySet()) {
// ... existing code ...
                }
            }
        }
        try {
            data.save(file);
            isDirty = false; // --- NEW ---
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- RENAMED ---
    public void saveSync() {
        save();
    }

    // --- NEW ---
    public boolean isDirty() {
        return isDirty;
    }

    /* -----------------------------
     * Plot Indexing (NEW)
     * ----------------------------- */

    /**
     * NEW: Registers a plot with the chunk-based spatial hash for fast lookups.
     */
    private void indexPlot(Plot plot) {
        plotsByChunk.computeIfAbsent(plot.getWorld(), k -> new ConcurrentHashMap<>());

        int chunkX1 = plot.getX1() >> 4; // Bitshift divide by 16
        int chunkZ1 = plot.getZ1() >> 4;
        int chunkX2 = plot.getX2() >> 4;
        int chunkZ2 = plot.getZ2() >> 4;

        for (int x = chunkX1; x <= chunkX2; x++) {
            for (int z = chunkZ1; z <= chunkZ2; z++) {
                String chunkKey = x + ":" + z;
                plotsByChunk.get(plot.getWorld())
                        .computeIfAbsent(chunkKey, k -> new HashSet<>())
                        .add(plot);
            }
        }
    }

    /**
     * NEW: Removes a plot from the spatial hash.
     */
    private void deIndexPlot(Plot plot) {
        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(plot.getWorld());
        if (worldChunks == null) return;

        int chunkX1 = plot.getX1() >> 4;
        int chunkZ1 = plot.getZ1() >> 4;
        int chunkX2 = plot.getX2() >> 4;
        int chunkZ2 = plot.getZ2() >> 4;

        for (int x = chunkX1; x <= chunkX2; x++) {
            for (int z = chunkZ1; z <= chunkZ2; z++) {
                String chunkKey = x + ":" + z;
                Set<Plot> plotsInChunk = worldChunks.get(chunkKey);
                if (plotsInChunk != null) {
                    plotsInChunk.remove(plot);
                    if (plotsInChunk.isEmpty()) {
                        worldChunks.remove(chunkKey);
                    }
                }
            }
        }
    }

    /* -----------------------------
     * Plot Management
     * ----------------------------- */
    public List<Plot> getPlots(UUID owner) {
        List<Plot> list = plotsByOwner.get(owner);
// ... existing code ...
    }

    public Plot getPlot(UUID owner, UUID plotId) {
        return plotsByOwner.getOrDefault(owner, Collections.emptyList())
// ... existing code ...
    }

    // --- MODIFIED ---
    public synchronized void createPlot(UUID owner, Location c1, Location c2) {
        if (c1 == null || c2 == null || c1.getWorld() == null || c2.getWorld() == null) return;
// ... existing code ...

        boolean bypass = plugin.getConfig().getBoolean("admin.bypass_claim_limit", false);
// ... existing code ...
        boolean isOp = Bukkit.getOfflinePlayer(owner).isOp();
        List<Plot> owned = plotsByOwner.computeIfAbsent(owner, k -> new ArrayList<>());

        // Enforce claim limit unless admin bypass applies (bypass must be enabled AND player must be op)
// ... existing code ...
        // ... existing code ...
            if (max > 0 && owned.size() >= max) return;
        }

        // --- MODIFIED ---
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        String ownerName = op.getName() != null ? op.getName() : "Unknown"; // Get name ONCE on creation

        Plot plot = new Plot(
                UUID.randomUUID(),
                owner,
                ownerName,
                c1.getWorld().getName(),
// ... existing code ...
                c2.getBlockZ()
        );
        owned.add(plot);
        indexPlot(plot); // --- NEW ---
        isDirty = true; // --- NEW ---
        // save() removed
    }

    // --- MODIFIED ---
    public synchronized void removePlot(UUID owner, UUID plotId) {
        List<Plot> owned = plotsByOwner.get(owner);
        if (owned == null) return;

        // --- MODIFIED ---
        Plot toRemove = null;
        Iterator<Plot> iterator = owned.iterator();
        while (iterator.hasNext()) {
            Plot p = iterator.next();
            if (p.getPlotId().equals(plotId)) {
                toRemove = p;
                iterator.remove();
                break;
            }
        }

        if (toRemove != null) {
            deIndexPlot(toRemove); // --- NEW ---
            if (owned.isEmpty()) plotsByOwner.remove(owner);
            isDirty = true; // --- NEW ---
            // save() removed
        }
    }

    // --- MODIFIED ---
    public synchronized void removeAllPlots(UUID owner) {
        List<Plot> removedPlots = plotsByOwner.remove(owner);
        if (removedPlots != null) {
            for (Plot p : removedPlots) {
                deIndexPlot(p); // --- NEW ---
            }
            isDirty = true; // --- NEW ---
            // save() removed
        }
    }

    public boolean hasPlots(UUID owner) { return !getPlots(owner).isEmpty(); }

    // --- HEAVILY UPGRADED ---
    public Plot getPlotAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        Map<String, Set<Plot>> worldChunks = plotsByChunk.get(loc.getWorld().getName());
        if (worldChunks == null) return null; // No plots in this world

        String chunkKey = (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
        Set<Plot> plotsInChunk = worldChunks.get(chunkKey);
        if (plotsInChunk == null) return null; // No plots in this chunk

        // Now we only iterate over the 1-2 plots in this chunk, not 10,000
        for (Plot p : plotsInChunk) {
            if (p.isInside(loc)) return p;
        }
        return null;
    }

    public Set<UUID> owners() { return Collections.unmodifiableSet(plotsByOwner.keySet()); }

    /* -----------------------------
     * Trusted Management
     * ----------------------------- */
    // --- MODIFIED ---
    public synchronized void addTrusted(UUID owner, UUID plotId, UUID trusted) {
        Plot p = getPlot(owner, plotId);
        if (p != null) {
            // --- MODIFIED ---
            OfflinePlayer tp = Bukkit.getOfflinePlayer(trusted);
            String trustedName = tp.getName() != null ? tp.getName() : "Unknown"; // Get name ONCE
            p.getTrusted().add(trusted);
            p.getTrustedNames().put(trusted, trustedName);
            isDirty = true; // --- NEW ---
            // save() removed
        }
    }

    // --- MODIFIED ---
    public synchronized void removeTrusted(UUID owner, UUID plotId, UUID trusted) {
        Plot p = getPlot(owner, plotId);
        if (p != null) {
            p.getTrusted().remove(trusted);
            p.getTrustedNames().remove(trusted);
            isDirty = true; // --- NEW ---
            // save() removed
        }
    }

    public boolean isTrusted(UUID owner, UUID plotId, UUID trusted) {
// ... existing code ...
    }

    /* -----------------------------
     * Admin Helpers
     * ----------------------------- */
    // --- MODIFIED ---
    public synchronized void removeBannedPlots() {
        boolean broadcast = plugin.cfg().broadcastAdminActions();

        Set<UUID> toRemove = new HashSet<>();
        // --- REMOVED --- BanList nameBans = Bukkit.getBanList(BanList.Type.NAME);

        for (UUID owner : new HashSet<>(plotsByOwner.keySet())) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
            
            // --- MODIFIED ---
            // op.isBanned() is more reliable than checking by name
            if (op.isBanned()) {
                toRemove.add(owner);
                String name = op.getName() != null ? op.getName() : owner.toString();
                plugin.getLogger().info("[AegisGuard] Removed plots of banned player: " + name);

                Map<String, String> ph = new HashMap<>();
// ... existing code ...
                ph.put("PLAYER", name);

                if (broadcast) {
// ... existing code ...
                    // ... existing code ...
                } else {
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.hasPermission("aegis.admin"))
// ... existing code ...
                }
            }
        }

        for (UUID id : toRemove) {
            // --- MODIFIED ---
            // Use our method to ensure de-indexing
            removeAllPlots(id);
        }
        // isDirty is already set by removeAllPlots()
    }
}
