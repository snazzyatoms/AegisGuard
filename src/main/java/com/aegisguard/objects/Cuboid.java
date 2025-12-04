package com.aegisguard.objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Cuboid implements Iterable<Block> {

    private final String worldName;
    private final int xMin, xMax;
    private final int yMin, yMax;
    private final int zMin, zMax;

    /**
     * Construct a new Cuboid given two diagonal points.
     * It automatically figures out which is min and which is max.
     */
    public Cuboid(Location point1, Location point2) {
        if (!point1.getWorld().getName().equals(point2.getWorld().getName())) {
            throw new IllegalArgumentException("Cuboid points must be in the same world!");
        }
        this.worldName = point1.getWorld().getName();
        this.xMin = Math.min(point1.getBlockX(), point2.getBlockX());
        this.xMax = Math.max(point1.getBlockX(), point2.getBlockX());
        this.yMin = Math.min(point1.getBlockY(), point2.getBlockY());
        this.yMax = Math.max(point1.getBlockY(), point2.getBlockY());
        this.zMin = Math.min(point1.getBlockZ(), point2.getBlockZ());
        this.zMax = Math.max(point1.getBlockZ(), point2.getBlockZ());
    }

    /**
     * Check if a location is inside this Cuboid.
     */
    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(this.worldName)) return false;
        return loc.getBlockX() >= xMin && loc.getBlockX() <= xMax &&
               loc.getBlockY() >= yMin && loc.getBlockY() <= yMax &&
               loc.getBlockZ() >= zMin && loc.getBlockZ() <= zMax;
    }

    /**
     * Get the total number of blocks (Volume).
     */
    public long getVolume() {
        return (long) (getWidth() * getHeight() * getLength());
    }

    /**
     * Get the Area (Width * Length). Useful for calculating tax/upkeep.
     */
    public long getArea() {
        return (long) (getWidth() * getLength());
    }

    public int getWidth() { return xMax - xMin + 1; }
    public int getHeight() { return yMax - yMin + 1; }
    public int getLength() { return zMax - zMin + 1; }

    public Location getLowerNE() {
        return new Location(org.bukkit.Bukkit.getWorld(worldName), xMin, yMin, zMin);
    }

    public Location getUpperSW() {
        return new Location(org.bukkit.Bukkit.getWorld(worldName), xMax, yMax, zMax);
    }

    public Location getCenter() {
        int x1 = xMax + 1;
        int y1 = yMax + 1;
        int z1 = zMax + 1;
        return new Location(org.bukkit.Bukkit.getWorld(worldName), 
            xMin + (x1 - xMin) / 2.0, 
            yMin + (y1 - yMin) / 2.0, 
            zMin + (z1 - zMin) / 2.0);
    }

    public World getWorld() {
        return org.bukkit.Bukkit.getWorld(worldName);
    }

    @Override
    public Iterator<Block> iterator() {
        List<Block> blocks = new ArrayList<>();
        World world = getWorld();
        if (world != null) {
            for (int x = xMin; x <= xMax; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    for (int z = zMin; z <= zMax; z++) {
                        blocks.add(world.getBlockAt(x, y, z));
                    }
                }
            }
        }
        return blocks.iterator();
    }
}
