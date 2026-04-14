package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MarketStall - read-only storefront metadata for chest/barrel-backed local stalls.
 */
public class MarketStall {

    private final UUID ownerId;
    private final String ownerName;
    private final String world;
    private final int chestX;
    private final int chestY;
    private final int chestZ;
    private final int signX;
    private final int signY;
    private final int signZ;
    private final long createdAt;
    private final Map<Integer, StallListing> listings = new ConcurrentHashMap<>();

    private String title;
    private String zoneName;

    public MarketStall(
            UUID ownerId,
            String ownerName,
            String world,
            int chestX,
            int chestY,
            int chestZ,
            int signX,
            int signY,
            int signZ,
            String title,
            String zoneName,
            long createdAt
    ) {
        this.ownerId = ownerId;
        this.ownerName = ownerName == null ? "Unknown" : ownerName;
        this.world = world;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.title = title == null || title.isBlank() ? this.ownerName + "'s Stall" : title.trim();
        this.zoneName = zoneName == null || zoneName.isBlank() ? null : zoneName.trim();
        this.createdAt = Math.max(0L, createdAt);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getWorld() {
        return world;
    }

    public int getChestX() {
        return chestX;
    }

    public int getChestY() {
        return chestY;
    }

    public int getChestZ() {
        return chestZ;
    }

    public int getSignX() {
        return signX;
    }

    public int getSignY() {
        return signY;
    }

    public int getSignZ() {
        return signZ;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName == null || zoneName.isBlank() ? null : zoneName.trim();
    }

    public String getStorageKey() {
        return chestX + "_" + chestY + "_" + chestZ;
    }

    public boolean hasZoneBinding() {
        return zoneName != null && !zoneName.isBlank();
    }

    public boolean matchesChest(@Nullable Location location) {
        return sameBlock(location, chestX, chestY, chestZ);
    }

    public boolean matchesSign(@Nullable Location location) {
        return sameBlock(location, signX, signY, signZ);
    }

    public @Nullable Location getChestLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;
        return new Location(bukkitWorld, chestX, chestY, chestZ);
    }

    public @Nullable Location getSignLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return null;
        return new Location(bukkitWorld, signX, signY, signZ);
    }

    public boolean isActive(@Nullable Plot plot) {
        if (plot == null) return false;
        if (!hasZoneBinding()) return true;

        Zone zone = plot.getZone(zoneName);
        return zone != null && zone.isRentedBy(ownerId);
    }

    public boolean canStock(@Nullable Player player, @Nullable Plot plot, @Nullable AegisGuard plugin) {
        if (player == null || plot == null) return false;
        if (plot.canManage(player, plugin)) return true;
        if (ownerId == null || !ownerId.equals(player.getUniqueId())) return false;
        return !hasZoneBinding() || isActive(plot);
    }

    public Map<Integer, StallListing> getListings() {
        return listings;
    }

    public @Nullable StallListing getListing(int slot) {
        return listings.get(slot);
    }

    public void setListing(int slot, StallListing listing) {
        if (slot < 0 || slot > 26 || listing == null) return;
        listings.put(slot, listing);
    }

    public void removeListing(int slot) {
        if (slot < 0 || slot > 26) return;
        listings.remove(slot);
    }

    public void clearInvalidListings() {
        listings.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey() < 0 || entry.getKey() > 26 || entry.getValue() == null);
    }

    private boolean sameBlock(@Nullable Location location, int x, int y, int z) {
        if (location == null || location.getWorld() == null) return false;
        return Objects.equals(location.getWorld().getName(), world)
                && location.getBlockX() == x
                && location.getBlockY() == y
                && location.getBlockZ() == z;
    }

    public static final class StallListing {
        private double price;
        private CurrencyType currency;
        private int bundleAmount;

        public StallListing(double price, @Nullable CurrencyType currency, int bundleAmount) {
            this.price = Math.max(0.0D, price);
            this.currency = currency == null ? CurrencyType.VAULT : currency;
            this.bundleAmount = Math.max(1, bundleAmount);
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = Math.max(0.0D, price);
        }

        public CurrencyType getCurrency() {
            return currency;
        }

        public void setCurrency(@Nullable CurrencyType currency) {
            this.currency = currency == null ? CurrencyType.VAULT : currency;
        }

        public int getBundleAmount() {
            return bundleAmount;
        }

        public void setBundleAmount(int bundleAmount) {
            this.bundleAmount = Math.max(1, bundleAmount);
        }

        public boolean isValid() {
            return price > 0.0D && bundleAmount > 0 && (currency == CurrencyType.VAULT || currency == CurrencyType.CLAIM_BLOCKS);
        }
    }
}
