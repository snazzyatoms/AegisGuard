package com.aegisguard.objects;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Estate {

    private final UUID plotId;
    private String name;
    private UUID ownerId;
    private boolean isGuild;
    private final World world;
    private final Cuboid region;
    
    // Flags: e.g. "pvp": true, "mobs": false
    private final Map<String, Boolean> flags = new HashMap<>();
    
    // Members: UUID -> Role Name (e.g. "member", "visitor")
    private final Map<UUID, String> members = new HashMap<>();

    public Estate(UUID plotId, String name, UUID ownerId, boolean isGuild, World world, Cuboid region) {
        this.plotId = plotId;
        this.name = name;
        this.ownerId = ownerId;
        this.isGuild = isGuild;
        this.world = world;
        this.region = region;
        
        // Default Flags
        flags.put("pvp", false);
        flags.put("mobs", false);
        flags.put("build", false);
        flags.put("interact", false);
        flags.put("fire-spread", false);
        flags.put("tnt-damage", false);
    }

    // --- GETTERS ---
    public UUID getId() { return plotId; }
    public UUID getPlotId() { return plotId; } // Alias for compatibility
    
    public String getName() { return name; }
    public String getDisplayName() { return name; } // Alias for compatibility

    public UUID getOwnerId() { return ownerId; }
    public boolean isGuild() { return isGuild; }
    public World getWorld() { return world; }
    public Cuboid getRegion() { return region; }

    // --- FIXED: Added getFlags() ---
    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public boolean getFlag(String flag) {
        return flags.getOrDefault(flag.toLowerCase(), false);
    }

    public void setFlag(String flag, boolean value) {
        flags.put(flag.toLowerCase(), value);
    }

    // --- Members ---
    public void setMember(UUID uuid, String role) {
        members.put(uuid, role);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid) || (ownerId != null && ownerId.equals(uuid));
    }
    
    public String getMemberRole(UUID uuid) {
        return members.get(uuid);
    }

    public Set<UUID> getAllMembers() {
        return members.keySet();
    }

    // --- Helpers ---
    public boolean isInside(Location loc) {
        return region.contains(loc);
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
