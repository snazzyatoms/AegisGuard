package com.aegisguard.objects;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Estate {

    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final UUID plotId;
    private String name;
    private UUID ownerId;
    private boolean isGuild;
    private final World world;
    private Cuboid region; // was final, now mutable
    
    // --- Extended Features ---
    private int level = 1;
    private double balance = 0.0;
    private String description = "";
    private Location spawnLocation;
    
    // --- Economy ---
    private boolean forSale = false;
    private boolean forRent = false;
    private double salePrice = 0.0;
    private double rentPrice = 0.0;

    // --- Visuals ---
    private String borderParticle = "FLAME"; // Default
    private String customBiome = null;

    // --- Sub-Zones ---
    // Assuming you have a Zone class. If not, this is a placeholder list to prevent errors.
    private final List<Zone> zones = new ArrayList<>();

    // --- Flags & Members ---
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();
    private final Map<UUID, String> members = new ConcurrentHashMap<>();

    public Estate(UUID plotId, String name, UUID ownerId, boolean isGuild, World world, Cuboid region) {
        this.plotId = plotId;
        this.name = name;
        this.ownerId = ownerId;
        this.isGuild = isGuild;
        this.world = world;
        this.region = region;
        this.spawnLocation = getCenter(); // Default spawn
        
        // Defaults
        flags.put("pvp", false);
        flags.put("mobs", false);
        flags.put("build", false);
        flags.put("interact", false);
    }

    // --- CORE GETTERS ---
    public UUID getId() { return plotId; }
    public UUID getPlotId() { return plotId; } 
    public String getName() { return name; }
    public String getDisplayName() { return name; }
    public UUID getOwnerId() { return ownerId; }
    public boolean isGuild() { return isGuild; }
    public World getWorld() { return world; }
    public Cuboid getRegion() { return region; }

    // Allow EstateManager / other systems to resize or move the plot region.
    public void setRegion(Cuboid region) {
        if (region == null) return;
        this.region = region;
        // Keep spawn inside the estate by default
        this.spawnLocation = region.getCenter();
    }

    // --- HELPER METHODS ---
    public Location getCenter() {
        return region.getCenter();
    }
    
    public boolean isServerZone() {
        return isGuild ? false : (ownerId == null || ownerId.equals(SERVER_UUID));
    }
    
    public boolean isInside(Location loc) {
        return region.contains(loc);
    }

    public void setName(String name) { this.name = name; }

    // --- LEVELING ---
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    // --- ECONOMY ---
    public double getBalance() { return balance; }
    public void deposit(double amount) { this.balance += amount; }
    public void withdraw(double amount) { this.balance -= amount; } // Still void; caller shouldn't use it as boolean

    public boolean isForSale() { return forSale; }
    public void setForSale(boolean forSale, int price) { 
        this.forSale = forSale; 
        this.salePrice = price; 
    }
    public double getSalePrice() { return salePrice; }

    public boolean isForRent() { return forRent; }
    public void setForRent(boolean forRent) { this.forRent = forRent; }
    public double getRentPrice() { return rentPrice; }

    // --- DESCRIPTION ---
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // --- SPAWN ---
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }

    // --- VISUALS ---
    public String getBorderParticle() { return borderParticle; }
    public void setBorderParticle(String particle) { this.borderParticle = particle; }
    
    public String getCustomBiome() { return customBiome; }
    public void setCustomBiome(String biome) { this.customBiome = biome; }

    // --- ZONES ---
    public List<Zone> getZones() { return zones; }
    public void addZone(Zone zone) { zones.add(zone); }
    public void removeZone(Zone zone) { zones.remove(zone); }

    // --- FLAGS ---
    public Map<String, Boolean> getFlags() { return flags; }
    public boolean getFlag(String flag) { return flags.getOrDefault(flag.toLowerCase(), false); }
    public void setFlag(String flag, boolean value) { flags.put(flag.toLowerCase(), value); }

    // --- MEMBERS ---
    public void setMember(UUID uuid, String role) { members.put(uuid, role); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid) || (ownerId != null && ownerId.equals(uuid)); }
    public String getMemberRole(UUID uuid) { return members.get(uuid); }
    public Set<UUID> getAllMembers() { return members.keySet(); }
}
