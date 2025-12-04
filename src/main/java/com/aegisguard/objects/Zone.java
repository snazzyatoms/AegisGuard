package com.yourname.aegisguard.objects; // Moved to objects package to match Estate

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Zone (Sub-Claim)
 * - Represents a 3D rentable area inside an Estate.
 * - Updated for v1.3.0 (Estate Integration).
 */
public class Zone {

    private final String name;
    private final Estate parent; // Changed from Plot to Estate
    
    // 3D Bounds
    private int x1, y1, z1;
    private int x2, y2, z2;
    
    // Rent Data
    private double rentPrice;
    private UUID renter;
    private long rentExpiration;
    
    public Zone(Estate parent, String name, int x1, int y1, int z1, int x2, int y2, int z2) {
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
    public Estate getParent() { return parent; }
    
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
    
    public boolean isRented() {
        if (renter == null) return false;
        if (System.currentTimeMillis() > rentExpiration) {
            // Expired implicitly
            return false;
        }
        return true;
    }
    
    public UUID getRenter() { 
        return isRented() ? renter : null; 
    }
    
    public long getRentExpiration() { return rentExpiration; }
    
    public void rentTo(UUID player, long durationMillis) {
        this.renter = player;
        this.rentExpiration = System.currentTimeMillis() + durationMillis;
    }
    
    public void evict() {
        this.renter = null;
        this.rentExpiration = 0;
    }
    
    // --- Utilities ---
    
    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        // v1.3.0 Update: Use Estate's world object directly
        if (!loc.getWorld().equals(parent.getWorld())) return false;
        
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        return x >= x1 && x <= x2 && 
               y >= y1 && y <= y2 && 
               z >= z1 && z <= z2;
    }
    
    public Location getCenter() {
        World w = parent.getWorld();
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
