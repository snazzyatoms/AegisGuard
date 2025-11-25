package com.aegisguard.data;

import org.bukkit.Location;
import java.util.UUID;

/**
 * Zone (Sub-Claim)
 * - Represents a rentable area inside a Plot.
 */
public class Zone {
    
    private final String name;
    private final Plot parent;
    
    // Bounds
    private int x1, y1, z1;
    private int x2, y2, z2;
    
    private double rentPrice;
    private UUID renter;
    private long rentExpiration;
    
    public Zone(Plot parent, String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.parent = parent;
        this.name = name;
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    public String getName() { return name; }
    public Plot getParent() { return parent; }
    
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getZ2() { return z2; }
    
    public double getRentPrice() { return rentPrice; }
    public void setRentPrice(double price) { this.rentPrice = price; }
    
    public boolean isRented() {
        return renter != null && System.currentTimeMillis() < rentExpiration;
    }
    
    public UUID getRenter() { return isRented() ? renter : null; }
    public long getRentExpiration() { return rentExpiration; }
    
    public void rentTo(UUID player, long durationMillis) {
        this.renter = player;
        this.rentExpiration = System.currentTimeMillis() + durationMillis;
    }
    
    public void evict() {
        this.renter = null;
        this.rentExpiration = 0;
    }
    
    public boolean isInside(Location loc) {
        if (!loc.getWorld().getName().equals(parent.getWorld())) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }
}
