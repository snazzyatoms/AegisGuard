package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration config;
    private boolean isDirty = false;

    public YMLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "estates.yml");
    }

    // ------------------------------------------------------------------------
    // Core lifecycle
    // ------------------------------------------------------------------------

    @Override
    public void load() {
        ensureConfigLoaded();

        int count = 0;
        for (String idStr : config.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idStr);
                ConfigurationSection sec = config.getConfigurationSection(idStr);
                if (sec == null) {
                    plugin.getLogger().warning("Skipping estate '" + idStr + "': section is null.");
                    continue;
                }

                String name = sec.getString("name", "Unnamed Estate");
                String ownerStr = sec.getString("owner");
                UUID owner = (ownerStr != null && !ownerStr.isEmpty()) ? UUID.fromString(ownerStr) : null;
                boolean isGuild = sec.getBoolean("is_guild", false);

                String worldName = sec.getString("world");
                if (worldName == null || Bukkit.getWorld(worldName) == null) {
                    plugin.getLogger().warning("Skipping estate '" + idStr + "': world '" + worldName + "' is missing.");
                    continue;
                }
                World world = Bukkit.getWorld(worldName);

                int x1 = sec.getInt("region.x1");
                int x2 = sec.getInt("region.x2");
                int y1 = sec.getInt("region.y1");
                int y2 = sec.getInt("region.y2");
                int z1 = sec.getInt("region.z1");
                int z2 = sec.getInt("region.z2");

                Location l1 = new Location(world, x1, y1, z1);
                Location l2 = new Location(world, x2, y2, z2);
                Cuboid region = new Cuboid(l1, l2);

                Estate estate = new Estate(id, name, owner, isGuild, world, region);

                // --- Extended fields (v1.3.0) ---
                estate.setLevel(sec.getInt("level", estate.getLevel()));

                double storedBalance = sec.getDouble("balance", 0.0);
                if (storedBalance != 0.0) {
                    // Estate starts at 0, so deposit the stored balance.
                    estate.deposit(storedBalance);
                }

                String description = sec.getString("description", "");
                estate.setDescription(description);

                // Spawn location
                if (sec.isConfigurationSection("spawn")) {
                    ConfigurationSection spawnSec = sec.getConfigurationSection("spawn");
                    if (spawnSec != null) {
                        double sx = spawnSec.getDouble("x", region.getCenter().getX());
                        double sy = spawnSec.getDouble("y", region.getCenter().getY());
                        double sz = spawnSec.getDouble("z", region.getCenter().getZ());
                        float syaw = (float) spawnSec.getDouble("yaw", 0.0D);
                        float spitch = (float) spawnSec.getDouble("pitch", 0.0D);
                        Location spawnLoc = new Location(world, sx, sy, sz, syaw, spitch);
                        estate.setSpawnLocation(spawnLoc);
                    }
                }

                // Economy flags
                boolean forSale = sec.getBoolean("for_sale", false);
                double salePrice = sec.getDouble("sale_price", 0.0);
                estate.setForSale(forSale, (int) salePrice);

                boolean forRent = sec.getBoolean("for_rent", false);
                estate.setForRent(forRent);
                // rent_price is saved & loaded here but Estate currently has no setter for the price itself.
                // Once you add setRentPrice(double), you can wire it in:
                // double rentPrice = sec.getDouble("rent_price", 0.0); estate.setRentPrice(rentPrice);

                // Visuals
                String borderParticle = sec.getString("border_particle", estate.getBorderParticle());
                estate.setBorderParticle(borderParticle);

                String biome = sec.getString("custom_biome", null);
                if (biome != null && !biome.isEmpty()) {
                    estate.setCustomBiome(biome);
                }

                // Flags
                if (sec.isConfigurationSection("flags")) {
                    ConfigurationSection flags = sec.getConfigurationSection("flags");
                    for (String f : flags.getKeys(false)) {
                        estate.setFlag(f, flags.getBoolean(f));
                    }
                }

                // Members
                if (sec.isConfigurationSection("members")) {
                    ConfigurationSection mems = sec.getConfigurationSection("members");
                    for (String mUuid : mems.getKeys(false)) {
                        String role = mems.getString(mUuid, "member");
                        estate.setMember(UUID.fromString(mUuid), role);
                    }
                }

                // Zones: can be wired later when Zone has a stable schema.

                plugin.getEstateManager().registerEstateFromLoad(estate);
                count++;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load estate '" + idStr + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + count + " estates from estates.yml (YML datastore).");
        isDirty = false;
    }

    @Override
    public void save() {
        ensureConfigLoaded();

        // Rebuild from in-memory view so deletions are honored.
        FileConfiguration newConfig = new YamlConfiguration();

        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            String id = estate.getId().toString();
            ConfigurationSection sec = newConfig.createSection(id);

            sec.set("name", estate.getName());
            sec.set("owner", estate.getOwnerId() != null ? estate.getOwnerId().toString() : "");
            sec.set("is_guild", estate.isGuild());
            sec.set("world", estate.getWorld().getName());

            Cuboid r = estate.getRegion();
            Location lower = r.getLowerNE();
            Location upper = r.getUpperSW();

            sec.set("region.x1", lower.getBlockX());
            sec.set("region.y1", lower.getBlockY());
            sec.set("region.z1", lower.getBlockZ());
            sec.set("region.x2", upper.getBlockX());
            sec.set("region.y2", upper.getBlockY());
            sec.set("region.z2", upper.getBlockZ());

            // Extended fields
            sec.set("level", estate.getLevel());
            sec.set("balance", estate.getBalance());
            sec.set("description", estate.getDescription());

            // Spawn
            Location spawn = estate.getSpawnLocation() != null ? estate.getSpawnLocation() : estate.getCenter();
            ConfigurationSection spawnSec = sec.createSection("spawn");
            spawnSec.set("x", spawn.getX());
            spawnSec.set("y", spawn.getY());
            spawnSec.set("z", spawn.getZ());
            spawnSec.set("yaw", spawn.getYaw());
            spawnSec.set("pitch", spawn.getPitch());

            // Economy
            sec.set("for_sale", estate.isForSale());
            sec.set("sale_price", estate.getSalePrice());
            sec.set("for_rent", estate.isForRent());
            sec.set("rent_price", estate.getRentPrice());

            // Visuals
            sec.set("border_particle", estate.getBorderParticle());
            sec.set("custom_biome", estate.getCustomBiome());

            // Flags
            ConfigurationSection flags = sec.createSection("flags");
            estate.getFlags().forEach(flags::set);

            // Members
            ConfigurationSection mems = sec.createSection("members");
            for (UUID uid : estate.getAllMembers()) {
                mems.set(uid.toString(), estate.getMemberRole(uid));
            }

            // Zones: reserved for future use.
        }

        try {
            newConfig.save(file);
            this.config = newConfig;
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save estates.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------------

    @Override
    public Estate getEstateAt(Location loc) {
        if (loc == null || plugin.getEstateManager() == null) return null;
        for (Estate e : plugin.getEstateManager().getAllEstates()) {
            if (e.getRegion() != null && e.getRegion().contains(loc)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void saveEstate(Estate estate) {
        if (estate == null) return;
        isDirty = true;
        // Rock-solid: persist immediately; autosaver + saveSync add extra safety.
        save();
    }

    @Override
    public void updateEstateOwner(Estate estate, UUID newOwner, boolean isGuild) {
        // Ownership changes should already be applied to the Estate object by EstateManager.
        // Datastore's job is just persistence.
        if (estate == null) return;
        saveEstate(estate);
    }

    @Override
    public void removeEstate(UUID id) {
        ensureConfigLoaded();
        if (id == null) return;

        // Remove from in-memory manager
        plugin.getEstateManager().removeEstate(id);

        // Remove from file
        config.set(id.toString(), null);
        isDirty = true;
        save();
    }

    /**
     * Legacy alias for older code that still calls deleteEstate.
     * Not part of IDataStore, so no @Override here.
     */
    public void deleteEstate(UUID id) {
        removeEstate(id);
    }

    @Override
    public boolean isAreaOverlapping(String world, int x1, int z1, int x2, int z2, UUID excludeId) {
        ensureConfigLoaded();
        if (config == null) return false;

        for (String key : config.getKeys(false)) {
            if (excludeId != null && key.equals(excludeId.toString())) continue;

            String w = config.getString(key + ".world");
            if (w == null || !w.equals(world)) continue;

            int ex1 = config.getInt(key + ".region.x1");
            int ex2 = config.getInt(key + ".region.x2");
            int ez1 = config.getInt(key + ".region.z1");
            int ez2 = config.getInt(key + ".region.z2");

            if (x1 <= ex2 && x2 >= ex1 && z1 <= ez2 && z2 >= ez1) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Dirty tracking & sync
    // ------------------------------------------------------------------------

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    @Override
    public void saveSync() {
        save();
    }

    @Override
    public void revertWildernessBlocks(long timestamp, int limit) {
        // YML datastore does not track wilderness history; SQL datastore will.
    }

    @Override
    public void logWildernessBlock(Location loc, String oldMat, String newMat, UUID playerUUID) {
        // No-op for YML implementation.
    }

    // ------------------------------------------------------------------------
    // Bulk operations & flags (banned, sale, auction)
    // ------------------------------------------------------------------------

    @Override
    public void removeAllPlots(UUID owner) {
        if (owner == null) return;
        ensureConfigLoaded();

        List<String> toRemove = new ArrayList<>();
        for (String key : config.getKeys(false)) {
            String ownerStr = config.getString(key + ".owner");
            if (ownerStr != null && ownerStr.equals(owner.toString())) {
                toRemove.add(key);
            }
        }

        for (String key : toRemove) {
            config.set(key, null);
        }

        if (!toRemove.isEmpty()) {
            isDirty = true;
            save();
        }
    }

    @Override
    public List<Estate> getEstates(UUID owner) {
        if (owner == null) return Collections.emptyList();
        return plugin.getEstateManager().getAllEstates().stream()
                .filter(e -> e.getOwnerId() != null && e.getOwnerId().equals(owner))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Estate> getAllEstates() {
        return plugin.getEstateManager().getAllEstates();
    }

    @Override
    public Collection<Estate> getEstatesForSale() {
        return plugin.getEstateManager().getAllEstates().stream()
                .filter(Estate::isForSale)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Estate> getEstatesForAuction() {
        // Wire auction support via a flag: estate.flag["auction"] == true
        return plugin.getEstateManager().getAllEstates().stream()
                .filter(e -> e.getFlag("auction"))
                .collect(Collectors.toList());
    }

    @Override
    public Estate getEstate(UUID owner, UUID plotId) {
        if (plotId == null) return null;
        return plugin.getEstateManager().getEstate(plotId);
    }

    @Override
    public void createEstate(UUID owner, Location c1, Location c2) {
        // Optional convenience hook: currently you likely call EstateManager#createEstate directly.
        // Left empty to avoid changing behavior.
    }

    @Override
    public void addEstate(Estate estate) {
        saveEstate(estate);
    }

    @Override
    public void addPlayerRole(Estate estate, UUID uuid, String role) {
        if (estate == null || uuid == null) return;
        estate.setMember(uuid, role);
        saveEstate(estate);
    }

    @Override
    public void removePlayerRole(Estate estate, UUID uuid) {
        if (estate == null || uuid == null) return;
        estate.removeMember(uuid);
        saveEstate(estate);
    }

    @Override
    public void removeBannedEstates() {
        // Uses a "banned" flag on the estate to identify which ones to purge.
        ensureConfigLoaded();

        List<UUID> toRemove = plugin.getEstateManager().getAllEstates().stream()
                .filter(e -> e.getFlag("banned"))
                .map(Estate::getId)
                .collect(Collectors.toList());

        for (UUID id : toRemove) {
            plugin.getEstateManager().removeEstate(id);
            config.set(id.toString(), null);
        }

        if (!toRemove.isEmpty()) {
            isDirty = true;
            save();
        }
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    private void ensureConfigLoaded() {
        if (config != null) return;

        if (!file.exists()) {
            config = new YamlConfiguration();
            return;
        }

        config = YamlConfiguration.loadConfiguration(file);
    }
}
