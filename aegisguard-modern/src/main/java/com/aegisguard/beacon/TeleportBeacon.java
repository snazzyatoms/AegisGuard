package com.aegisguard.beacon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * One plot-scoped teleport pad. Links to at most one other beacon.
 * Access defaults closed: owners only until the creator opens it.
 */
public final class TeleportBeacon {

    public enum Purpose {
        PERSONAL, SHOP, MARKET, ARENA, DUNGEON, SPAWN, ALLIANCE, SERVER, AUCTION;

        public static Purpose parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) return PERSONAL;
            try {
                return Purpose.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return PERSONAL;
            }
        }
    }

    public enum Preset {
        PRIVATE, MEMBERS, ALLIANCE, PUBLIC
    }

    private final UUID id;
    private UUID plotId;
    private String worldName;
    private int x;
    private int y;
    private int z;
    private float yaw;
    private float pitch;
    private Material padMaterial = Material.LODESTONE;
    private String name = "Beacon";
    private Purpose purpose = Purpose.PERSONAL;
    private UUID linkedBeaconId;
    private int customModelData;

    private boolean enabled = true;
    private boolean owners = true;
    private boolean members;
    private boolean trusted;
    private boolean guests;
    private boolean alliance;
    private boolean publicAccess;
    private boolean staffOnly;
    private boolean requireConfirm = true;
    private boolean allowCombat;
    private double vaultCost;
    private long claimBlockCost;
    private int extraCooldownSeconds;
    private long createdAt = System.currentTimeMillis();

    public TeleportBeacon(UUID id) {
        this.id = id == null ? UUID.randomUUID() : id;
    }

    public UUID getId() { return id; }
    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void setBlock(Location loc) {
        if (loc == null) return;
        if (loc.getWorld() != null) this.worldName = loc.getWorld().getName();
        setCoordinates(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getYaw(), loc.getPitch());
    }

    public void setCoordinates(int x, int y, int z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setFacing(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public Material getPadMaterial() { return padMaterial; }
    public void setPadMaterial(Material padMaterial) {
        this.padMaterial = padMaterial == null ? Material.LODESTONE : padMaterial;
    }

    public String getName() { return name == null || name.isBlank() ? "Beacon" : name; }
    public void setName(String name) {
        if (name == null || name.isBlank()) return;
        this.name = name.trim().length() > 32 ? name.trim().substring(0, 32) : name.trim();
    }

    public Purpose getPurpose() { return purpose == null ? Purpose.PERSONAL : purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose == null ? Purpose.PERSONAL : purpose; }

    public UUID getLinkedBeaconId() { return linkedBeaconId; }
    public void setLinkedBeaconId(UUID linkedBeaconId) { this.linkedBeaconId = linkedBeaconId; }

    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int customModelData) { this.customModelData = Math.max(0, customModelData); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isOwners() { return owners; }
    public void setOwners(boolean owners) { this.owners = owners; }
    public boolean isMembers() { return members; }
    public void setMembers(boolean members) { this.members = members; }
    public boolean isTrusted() { return trusted; }
    public void setTrusted(boolean trusted) { this.trusted = trusted; }
    public boolean isGuests() { return guests; }
    public void setGuests(boolean guests) { this.guests = guests; }
    public boolean isAlliance() { return alliance; }
    public void setAlliance(boolean alliance) { this.alliance = alliance; }
    public boolean isPublicAccess() { return publicAccess; }
    public void setPublicAccess(boolean publicAccess) { this.publicAccess = publicAccess; }
    public boolean isStaffOnly() { return staffOnly; }
    public void setStaffOnly(boolean staffOnly) { this.staffOnly = staffOnly; }
    public boolean isRequireConfirm() { return requireConfirm; }
    public void setRequireConfirm(boolean requireConfirm) { this.requireConfirm = requireConfirm; }
    public boolean isAllowCombat() { return allowCombat; }
    public void setAllowCombat(boolean allowCombat) { this.allowCombat = allowCombat; }
    public double getVaultCost() { return vaultCost; }
    public void setVaultCost(double vaultCost) { this.vaultCost = Math.max(0.0D, vaultCost); }
    public long getClaimBlockCost() { return claimBlockCost; }
    public void setClaimBlockCost(long claimBlockCost) { this.claimBlockCost = Math.max(0L, claimBlockCost); }
    public int getExtraCooldownSeconds() { return extraCooldownSeconds; }
    public void setExtraCooldownSeconds(int extraCooldownSeconds) {
        this.extraCooldownSeconds = Math.max(0, extraCooldownSeconds);
    }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = Math.max(0L, createdAt); }

    public boolean isLinked() { return linkedBeaconId != null; }

    public Location toStandLocation() {
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D, yaw, pitch);
    }

    public Location toBlockLocation() {
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public boolean isAt(Location loc) {
        if (loc == null || loc.getWorld() == null || worldName == null) return false;
        return loc.getWorld().getName().equalsIgnoreCase(worldName)
                && loc.getBlockX() == x && loc.getBlockY() == y && loc.getBlockZ() == z;
    }

    public boolean isNear(Location loc, double radius) {
        Location stand = toStandLocation();
        if (stand == null || loc == null || loc.getWorld() == null) return false;
        if (!stand.getWorld().equals(loc.getWorld())) return false;
        double dx = loc.getX() - stand.getX();
        double dz = loc.getZ() - stand.getZ();
        double dy = loc.getY() - (y + 1.0D);
        return (dx * dx + dz * dz) <= (radius * radius) && Math.abs(dy) <= 2.25D;
    }

    public void applyPreset(Preset preset) {
        owners = true;
        members = false;
        trusted = false;
        guests = false;
        alliance = false;
        publicAccess = false;
        staffOnly = false;
        if (preset == null) return;
        switch (preset) {
            case PRIVATE -> { }
            case MEMBERS -> {
                members = true;
                trusted = true;
            }
            case ALLIANCE -> {
                members = true;
                trusted = true;
                alliance = true;
            }
            case PUBLIC -> {
                members = true;
                trusted = true;
                guests = true;
                alliance = true;
                publicAccess = true;
            }
        }
    }
}
