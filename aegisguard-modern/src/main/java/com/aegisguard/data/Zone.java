package com.aegisguard.data; // Changed from .objects to .data

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Zone (Sub-Claim) - v1.2.2
 * - Represents a 3D rentable area inside a Plot.
 * - Lives in 'data' package now.
 */
public class Zone {
    
    private final String name;
    private final Plot parent;
    
    // 3D Bounds (Zones are usually rooms, so we keep Y)
    private int x1, y1, z1;
    private int x2, y2, z2;
    
    // Rent Data
    private double rentPrice;
    /** Listing deposit required when a new renter starts (refundable). */
    private double deposit;
    /** Deposit currently held for the active renter. */
    private double heldDeposit;
    private UUID renter;
    private long rentExpiration;
    private final Map<UUID, String> guestAccess = new ConcurrentHashMap<>();
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();
    private Location spawnLocation;
    
    public Zone(Plot parent, String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.parent = parent;
        this.name = name;
        // Normalize coordinates immediately
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    // --- Core Identity ---
    public String getName() { return name; }
    public Plot getParent() { return parent; }
    
    // --- Bounds ---
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getZ2() { return z2; }
    
    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    // --- Rent Logic ---
    public double getRentPrice() { return rentPrice; }
    public void setRentPrice(double price) { this.rentPrice = price; }

    public double getDeposit() { return deposit; }
    public void setDeposit(double deposit) {
        this.deposit = Double.isFinite(deposit) ? Math.max(0.0D, deposit) : 0.0D;
    }

    public double getHeldDeposit() { return heldDeposit; }
    public void setHeldDeposit(double heldDeposit) {
        this.heldDeposit = Double.isFinite(heldDeposit) ? Math.max(0.0D, heldDeposit) : 0.0D;
    }
    public void clearHeldDeposit() { this.heldDeposit = 0.0D; }

    /** Returns and clears the held deposit so callers can refund or queue settlement. */
    public double takeHeldDeposit() {
        double held = heldDeposit;
        heldDeposit = 0.0D;
        return held;
    }

    public boolean isListedForRent() {
        return rentPrice > 0.0D;
    }
    
    public boolean isRented() {
        if (renter == null) return false;
        if (System.currentTimeMillis() > rentExpiration) {
            evict();
            return false;
        }
        return true;
    }
    
    public UUID getRenter() { 
        return isRented() ? renter : null; 
    }

    /** Persistence/snapshot accessor that never expires or mutates the live rental. */
    public UUID getStoredRenter() {
        return renter;
    }
    
    public long getRentExpiration() { return rentExpiration; }

    public long getRemainingRentMillis() {
        if (!isRented()) return 0L;
        return Math.max(0L, rentExpiration - System.currentTimeMillis());
    }

    public void rentTo(UUID player, long durationMillis) {
        rentTo(player, durationMillis, this.deposit);
    }

    public void rentTo(UUID player, long durationMillis, double depositToHold) {
        this.renter = player;
        this.rentExpiration = System.currentTimeMillis() + durationMillis;
        setHeldDeposit(depositToHold);
    }

    public void extendRent(long durationMillis) {
        if (durationMillis <= 0L) return;

        long base = isRented() ? rentExpiration : System.currentTimeMillis();
        this.rentExpiration = base + durationMillis;
    }

    /**
     * Direct setter used by persistence layers to restore exact state.
     */
    public void setRentState(UUID renter, long rentExpiration) {
        this.renter = renter;
        this.rentExpiration = rentExpiration;
    }
    
    public void evict() {
        this.renter = null;
        this.rentExpiration = 0;
        // heldDeposit is left for takeHeldDeposit() refund paths; do not silently drop it
        this.guestAccess.clear();
        this.spawnLocation = null;
    }

    public boolean isRentedBy(UUID playerId) {
        return playerId != null && isRented() && playerId.equals(renter);
    }

    public Map<UUID, String> getGuestAccess() {
        return guestAccess;
    }

    public void addGuest(UUID playerId) {
        if (playerId == null) return;
        guestAccess.put(playerId, "guest");
    }

    public void removeGuest(UUID playerId) {
        if (playerId == null) return;
        guestAccess.remove(playerId);
    }

    public boolean hasGuest(UUID playerId) {
        return playerId != null && guestAccess.containsKey(playerId);
    }

    public void clearGuests() {
        guestAccess.clear();
    }

    public boolean getFlag(String key, boolean defaultValue) {
        if (key == null || key.isBlank()) return defaultValue;
        return flags.getOrDefault(key.toLowerCase(), defaultValue);
    }

    public void setFlag(String key, boolean value) {
        if (key == null || key.isBlank()) return;
        flags.put(key.toLowerCase(), value);
    }

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public boolean isHotelMode() {
        return getFlag("hotel_mode", false);
    }

    public boolean canGuestVisit(UUID playerId) {
        if (playerId == null || !isHotelMode()) return false;
        if (!hasGuest(playerId)) return false;
        return getFlag("guest_visit", true);
    }

    public boolean canGuestInteract(UUID playerId) {
        if (playerId == null || !isHotelMode()) return false;
        if (!hasGuest(playerId)) return false;
        return getFlag("guest_interact", true);
    }

    public boolean canGuestUseContainers(UUID playerId) {
        if (playerId == null || !isHotelMode()) return false;
        if (!hasGuest(playerId)) return false;
        return getFlag("guest_containers", true);
    }

    public boolean canGuestBuild(UUID playerId) {
        if (playerId == null || !isHotelMode()) return false;
        if (!hasGuest(playerId)) return false;
        return getFlag("guest_build", false);
    }

    public boolean canGuestUseVehicles(UUID playerId) {
        if (canGuestBuild(playerId)) return true;
        return canGuestInteract(playerId);
    }

    public Location getSpawnLocation() {
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    public void setSpawnLocation(Location spawnLocation) {
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            this.spawnLocation = null;
            return;
        }
        if (!spawnLocation.getWorld().getName().equalsIgnoreCase(parent.getWorld())) {
            return;
        }
        if (!isInside(spawnLocation)) {
            return;
        }
        this.spawnLocation = spawnLocation.clone();
    }

    public void clearSpawnLocation() {
        this.spawnLocation = null;
    }

    public Location getTeleportLocation() {
        Location spawn = getSpawnLocation();
        return spawn != null ? spawn : getCenter();
    }

    public int getWidth() {
        return (x2 - x1) + 1;
    }

    public int getHeight() {
        return (y2 - y1) + 1;
    }

    public int getDepth() {
        return (z2 - z1) + 1;
    }

    public int getFootprintArea() {
        return getWidth() * getDepth();
    }

    public int getVolume() {
        return getWidth() * getHeight() * getDepth();
    }

    // --- Utilities ---
    
    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(parent.getWorld())) return false;
        
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        return x >= x1 && x <= x2 && 
               y >= y1 && y <= y2 && 
               z >= z1 && z <= z2;
    }
    
    public Location getCenter() {
        World w = Bukkit.getWorld(parent.getWorld());
        if (w == null) return null;
        double cX = (x1 + x2) / 2.0 + 0.5;
        double cY = (y1 + y2) / 2.0; 
        double cZ = (z1 + z2) / 2.0 + 0.5;
        return new Location(w, cX, cY, cZ);
    }
    
    public String getRemainingTimeFormatted() {
        if (!isRented()) return "Available";
        long diff = rentExpiration - System.currentTimeMillis();
        if (diff <= 0) return "Expired";
        
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff) % 24;
        
        if (days > 0) return days + "d " + hours + "h";
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;
        return hours + "h " + minutes + "m";
    }

    public boolean overlaps(Zone other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!Objects.equals(parent, other.parent)) return false;

        return x1 <= other.x2 && x2 >= other.x1
                && y1 <= other.y2 && y2 >= other.y1
                && z1 <= other.z2 && z2 >= other.z1;
    }

    public boolean isWithinParentBounds() {
        return x1 >= parent.getX1()
                && x2 <= parent.getX2()
                && z1 >= parent.getZ1()
                && z2 <= parent.getZ2();
    }

    // --- Equality (Prevents Duplicates in Lists) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Zone zone = (Zone) o;
        return name.equals(zone.name) && parent.equals(zone.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, parent);
    }
}
