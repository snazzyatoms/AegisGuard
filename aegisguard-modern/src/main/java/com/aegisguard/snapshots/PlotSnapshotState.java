package com.aegisguard.snapshots;

import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned snapshot payload for Plot fields that pre-1.3.5 snapshots did not capture. */
final class PlotSnapshotState {
    static final int SCHEMA = 1;

    private PlotSnapshotState() { }

    static String capture(Plot plot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
        yaml.set("settings.max_members", plot.getMaxMembers());
        yaml.set("settings.spawn", plot.getSpawnLocationString());
        yaml.set("settings.border_particle", plot.getBorderParticle());
        yaml.set("settings.ambient_particle", plot.getAmbientParticle());
        yaml.set("settings.entry_effect", plot.getEntryEffect());
        yaml.set("settings.warp_name", plot.getWarpName());
        yaml.set("settings.warp_icon", plot.getWarpIcon() == null ? null : plot.getWarpIcon().name());
        yaml.set("settings.warp_category", plot.getWarpCategory());

        yaml.set("economy.for_sale", plot.isForSale());
        yaml.set("economy.sale_price", plot.getSalePrice());
        yaml.set("economy.for_rent", plot.isForRent());
        yaml.set("economy.rent_price", plot.getRentPrice());
        yaml.set("economy.renter", string(plot.getCurrentRenter()));
        yaml.set("economy.rent_expires", plot.getRentExpires());
        yaml.set("economy.for_auction", plot.isForAuction());
        yaml.set("economy.auction_start", plot.getAuctionStartPrice());
        yaml.set("economy.current_bid", plot.getCurrentBid());
        yaml.set("economy.current_bidder", string(plot.getCurrentBidder()));
        yaml.set("economy.auction_end", plot.getAuctionEndTime());

        yaml.set("progression.level", plot.getLevel());
        yaml.set("progression.xp", plot.getXp());
        yaml.set("progression.horizon_rank", plot.getHorizonRank());
        yaml.set("progression.expansion_rank", plot.getHorizonExpansionRank());
        yaml.set("progression.renown", plot.getHorizonRenown());
        yaml.set("progression.climate", plot.getHorizonClimate());
        yaml.set("progression.focus", plot.getAscensionFocus());
        yaml.set("progression.focus_changed_at", plot.getAscensionFocusChangedAt());
        yaml.set("progression.last_upkeep", plot.getLastUpkeepPayment());

        yaml.set("social.likes", plot.getLikes());
        yaml.set("social.liked_by", plot.getLikedBy().stream().filter(java.util.Objects::nonNull)
                .map(UUID::toString).sorted().toList());

        int zoneIndex = 0;
        for (Zone zone : plot.getZones()) {
            if (zone == null) continue;
            String path = "zones." + zoneIndex++;
            yaml.set(path + ".name", zone.getName());
            yaml.set(path + ".bounds", List.of(zone.getX1(), zone.getY1(), zone.getZ1(),
                    zone.getX2(), zone.getY2(), zone.getZ2()));
            yaml.set(path + ".rent_price", zone.getRentPrice());
            yaml.set(path + ".deposit", zone.getDeposit());
            yaml.set(path + ".held_deposit", zone.getHeldDeposit());
            yaml.set(path + ".renter", string(zone.getStoredRenter()));
            yaml.set(path + ".rent_expiration", zone.getRentExpiration());
            yaml.set(path + ".guests", zone.getGuestAccess().keySet().stream()
                    .filter(java.util.Objects::nonNull).map(UUID::toString).sorted().toList());
            yaml.set(path + ".flags", new java.util.TreeMap<>(zone.getFlags()));
            Location spawn = zone.getSpawnLocation();
            if (spawn != null) {
                yaml.set(path + ".spawn", List.of(spawn.getX(), spawn.getY(), spawn.getZ(),
                        (double) spawn.getYaw(), (double) spawn.getPitch()));
            }
        }

        int stallIndex = 0;
        for (MarketStall stall : plot.getStalls()) {
            if (stall == null || stall.getOwnerId() == null) continue;
            String path = "stalls." + stallIndex++;
            yaml.set(path + ".owner", stall.getOwnerId().toString());
            yaml.set(path + ".owner_name", stall.getOwnerName());
            yaml.set(path + ".world", stall.getWorld());
            yaml.set(path + ".chest", List.of(stall.getChestX(), stall.getChestY(), stall.getChestZ()));
            yaml.set(path + ".sign", List.of(stall.getSignX(), stall.getSignY(), stall.getSignZ()));
            yaml.set(path + ".title", stall.getTitle());
            yaml.set(path + ".zone", stall.getZoneName());
            yaml.set(path + ".created_at", stall.getCreatedAt());
            for (Map.Entry<Integer, MarketStall.StallListing> entry : new java.util.TreeMap<>(stall.getListings()).entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                String listing = path + ".listings." + entry.getKey();
                yaml.set(listing + ".price", entry.getValue().getPrice());
                yaml.set(listing + ".currency", entry.getValue().getCurrency().name());
                yaml.set(listing + ".bundle", entry.getValue().getBundleAmount());
            }
        }
        return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    static void restore(Plot plot, String encoded, Set<RestoreScope> requestedScopes) {
        if (plot == null || encoded == null || encoded.isBlank()) return;
        YamlConfiguration yaml = decode(encoded);
        EnumSet<RestoreScope> scopes = RestoreScope.normalize(requestedScopes);
        if (scopes.contains(RestoreScope.PLOT_SETTINGS)) restoreSettings(plot, yaml);
        if (scopes.contains(RestoreScope.ECONOMY)) restoreEconomy(plot, yaml);
        if (scopes.contains(RestoreScope.PROGRESSION)) restoreProgression(plot, yaml);
        if (scopes.contains(RestoreScope.SOCIAL)) restoreSocial(plot, yaml);
        if (scopes.contains(RestoreScope.ZONES_AND_STALLS)) restoreZonesAndStalls(plot, yaml);
    }

    static void validate(String encoded) {
        if (encoded != null && !encoded.isBlank()) decode(encoded);
    }

    private static YamlConfiguration decode(String encoded) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            yaml.load(new StringReader(decoded));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid versioned plot snapshot state", error);
        }
        int schema = yaml.getInt("schema", 0);
        if (schema < 1 || schema > SCHEMA) {
            throw new IllegalArgumentException("Unsupported plot snapshot state schema " + schema);
        }
        return yaml;
    }

    private static void restoreSettings(Plot plot, YamlConfiguration yaml) {
        plot.setMaxMembers(yaml.getInt("settings.max_members", plot.getMaxMembers()));
        plot.setSpawnLocationFromString(yaml.getString("settings.spawn"));
        plot.setBorderParticle(yaml.getString("settings.border_particle"));
        plot.setAmbientParticle(yaml.getString("settings.ambient_particle"));
        plot.setEntryEffect(yaml.getString("settings.entry_effect"));
        Material icon = material(yaml.getString("settings.warp_icon"));
        plot.setServerWarp(plot.isServerWarp(), yaml.getString("settings.warp_name"), icon);
        plot.setWarpCategory(yaml.getString("settings.warp_category"));
    }

    private static void restoreEconomy(Plot plot, YamlConfiguration yaml) {
        plot.setForSale(yaml.getBoolean("economy.for_sale", false), yaml.getDouble("economy.sale_price", 0D));
        plot.setForRent(yaml.getBoolean("economy.for_rent", false), yaml.getDouble("economy.rent_price", 0D));
        plot.setRenter(uuid(yaml.getString("economy.renter")), yaml.getLong("economy.rent_expires", 0L));
        boolean auction = yaml.getBoolean("economy.for_auction", false);
        plot.setForAuction(auction);
        if (auction) {
            plot.setAuctionStartPrice(yaml.getDouble("economy.auction_start", 0D));
            plot.setCurrentBid(yaml.getDouble("economy.current_bid", 0D), uuid(yaml.getString("economy.current_bidder")));
            plot.setAuctionEndTime(yaml.getLong("economy.auction_end", 0L));
        }
    }

    private static void restoreProgression(Plot plot, YamlConfiguration yaml) {
        plot.setLevel(yaml.getInt("progression.level", 1));
        plot.setXp(yaml.getInt("progression.xp", 0));
        plot.setHorizonRank(yaml.getInt("progression.horizon_rank", 0));
        plot.setHorizonExpansionRank(yaml.getInt("progression.expansion_rank", 0));
        plot.setHorizonRenown(yaml.getLong("progression.renown", 0L));
        plot.setHorizonClimate(yaml.getString("progression.climate", "NATURAL"));
        plot.setAscensionFocus(yaml.getString("progression.focus", "UNCHOSEN"));
        plot.setAscensionFocusChangedAt(yaml.getLong("progression.focus_changed_at", 0L));
        plot.setLastUpkeepPayment(yaml.getLong("progression.last_upkeep", 0L));
    }

    private static void restoreSocial(Plot plot, YamlConfiguration yaml) {
        plot.getLikedBy().clear();
        for (String raw : yaml.getStringList("social.liked_by")) {
            UUID id = uuid(raw);
            if (id != null) plot.getLikedBy().add(id);
        }
        plot.setLikes(yaml.getInt("social.likes", plot.getLikedBy().size()));
    }

    private static void restoreZonesAndStalls(Plot plot, YamlConfiguration yaml) {
        plot.getZones().clear();
        ConfigurationSection zones = yaml.getConfigurationSection("zones");
        if (zones != null) for (String key : orderedKeys(zones)) {
            ConfigurationSection section = zones.getConfigurationSection(key);
            if (section == null) continue;
            List<Integer> b = section.getIntegerList("bounds");
            if (b.size() != 6) continue;
            Zone zone = new Zone(plot, section.getString("name", "zone-" + key),
                    b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5));
            zone.setRentPrice(section.getDouble("rent_price", 0D));
            zone.setDeposit(section.getDouble("deposit", 0D));
            zone.setHeldDeposit(section.getDouble("held_deposit", 0D));
            zone.setRentState(uuid(section.getString("renter")), section.getLong("rent_expiration", 0L));
            for (String raw : section.getStringList("guests")) {
                UUID id = uuid(raw);
                if (id != null) zone.addGuest(id);
            }
            ConfigurationSection flags = section.getConfigurationSection("flags");
            if (flags != null) for (String flag : flags.getKeys(false)) zone.setFlag(flag, flags.getBoolean(flag));
            List<Double> spawn = section.getDoubleList("spawn");
            if (spawn.size() >= 3) {
                World world = Bukkit.getWorld(plot.getWorld());
                if (world == null) throw new IllegalStateException(
                        "Snapshot zone world is not loaded: " + plot.getWorld());
                float yaw = spawn.size() > 3 ? spawn.get(3).floatValue() : 0F;
                float pitch = spawn.size() > 4 ? spawn.get(4).floatValue() : 0F;
                zone.setSpawnLocation(new Location(world, spawn.get(0), spawn.get(1), spawn.get(2), yaw, pitch));
            }
            plot.addZone(zone);
        }

        plot.getStalls().clear();
        ConfigurationSection stalls = yaml.getConfigurationSection("stalls");
        if (stalls != null) for (String key : orderedKeys(stalls)) {
            ConfigurationSection section = stalls.getConfigurationSection(key);
            if (section == null) continue;
            UUID owner = uuid(section.getString("owner"));
            List<Integer> chest = section.getIntegerList("chest");
            List<Integer> sign = section.getIntegerList("sign");
            if (owner == null || chest.size() != 3 || sign.size() != 3) continue;
            MarketStall stall = new MarketStall(owner, section.getString("owner_name"),
                    section.getString("world", plot.getWorld()), chest.get(0), chest.get(1), chest.get(2),
                    sign.get(0), sign.get(1), sign.get(2), section.getString("title"),
                    section.getString("zone"), section.getLong("created_at", 0L));
            ConfigurationSection listings = section.getConfigurationSection("listings");
            if (listings != null) for (String slotKey : listings.getKeys(false)) {
                try {
                    ConfigurationSection listing = listings.getConfigurationSection(slotKey);
                    if (listing == null) continue;
                    CurrencyType currency = CurrencyType.valueOf(listing.getString("currency", "VAULT"));
                    stall.setListing(Integer.parseInt(slotKey), new MarketStall.StallListing(
                            listing.getDouble("price", 0D), currency, listing.getInt("bundle", 1)));
                } catch (IllegalArgumentException ignored) { }
            }
            // Snapshot state was validated when captured; avoid world/chunk lookups while decoding.
            plot.getStalls().add(stall);
        }
    }

    private static List<String> orderedKeys(ConfigurationSection section) {
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> Integer.compare(integer(a), integer(b)));
        return keys;
    }

    private static int integer(String raw) {
        try { return Integer.parseInt(raw); } catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }

    private static UUID uuid(String raw) {
        try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static Material material(String raw) {
        try { return raw == null || raw.isBlank() ? null : Material.valueOf(raw); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String string(UUID value) {
        return value == null ? null : value.toString();
    }
}
