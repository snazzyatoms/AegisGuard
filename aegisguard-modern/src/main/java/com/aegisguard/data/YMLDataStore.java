package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotDeleteEvent;
import com.aegisguard.flags.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YMLDataStore (v1.2.4 hardened)
 * - Manages plot data using 'plots.yml'.
 * - Strict IDataStore contract for 1.2.x.
 *
 * HARDENING:
 *  ✅ getPlots(UUID) NEVER returns null
 *  ✅ Thread-safe owner cache (Set-backed)
 *  ✅ Chunk index for fast lookups
 *  ✅ Defensive de-duplication by plotId
 *  ✅ Atomic-ish disk writes (temp + move) to reduce corruption risk
 *  ✅ Transfer safety: old owner cannot retain privileges (role cleanup + cache purge)
 */
public class YMLDataStore implements IDataStore {

    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration config;

    private final Object ioLock = new Object();

    private final Map<UUID, Set<Plot>> plotsByOwner = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Set<Plot>>> plotsByChunk = new ConcurrentHashMap<>();

    private volatile boolean isDirty = false;

    public YMLDataStore(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    // ==============================================================
    // --- CORE I/O ---
    // ==============================================================

    @Override
    public void load() {
        synchronized (ioLock) {
            plotsByOwner.clear();
            plotsByChunk.clear();

            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException ignored) {}
            }

            config = YamlConfiguration.loadConfiguration(file);
            int count = 0;

            for (String key : config.getKeys(false)) {
                try {
                    UUID plotId = UUID.fromString(key);
                    ConfigurationSection sec = config.getConfigurationSection(key);
                    if (sec == null) continue;

                    String ownerStr = sec.getString("owner");
                    if (ownerStr == null || ownerStr.isEmpty()) continue;

                    UUID ownerId = UUID.fromString(ownerStr);
                    String ownerName = sec.getString("owner-name", "Unknown");
                    String worldName = sec.getString("world");
                    if (worldName == null || worldName.isEmpty()) continue;

                    int x1 = sec.getInt("x1");
                    int z1 = sec.getInt("z1");
                    int x2 = sec.getInt("x2");
                    int z2 = sec.getInt("z2");

                    Plot plot = new Plot(plotId, ownerId, ownerName, worldName, x1, z1, x2, z2);

                    plot.setLevel(sec.getInt("level", 1));
                    plot.setXp(sec.getDouble("xp", 0.0));
                    plot.setHorizonRank(sec.getInt("horizons.rank", 0));
                    plot.setHorizonExpansionRank(sec.getInt("horizons.expansion-rank", 0));
                    plot.setHorizonRenown(sec.getLong("horizons.renown", 0L));
                    plot.setHorizonClimate(sec.getString("horizons.climate", "NATURAL"));
                    plot.setAscensionFocus(sec.getString("ascension.focus", "UNCHOSEN"));
                    plot.setAscensionFocusChangedAt(sec.getLong("ascension.focus-changed-at", 0L));
                    plot.setLastUpkeep(sec.getLong("last-upkeep", System.currentTimeMillis()));
                    plot.setMaxMembers(sec.getInt("max-members", 2));

                    plot.setSpawnLocationFromString(sec.getString("spawn-location"));
                    plot.setWelcomeMessage(sec.getString("welcome-message"));
                    plot.setFarewellMessage(sec.getString("farewell-message"));
                    plot.setEntryTitle(sec.getString("entry-title"));
                    plot.setEntrySubtitle(sec.getString("entry-subtitle"));
                    plot.setDescription(sec.getString("description"));
                    plot.setCustomBiome(sec.getString("custom-biome"));

                    // Market
                    if (sec.isConfigurationSection("market")) {
                        ConfigurationSection market = sec.getConfigurationSection("market");
                        if (market != null) {
                            if (market.getBoolean("is-for-sale", false)) {
                                plot.setForSale(true, market.getDouble("sale-price", 0.0));
                            }
                            if (market.getBoolean("is-for-rent", false)) {
                                plot.setForRent(true, market.getDouble("rent-price", 0.0));
                            }
                            String renterStr = market.getString("current-renter");
                            if (renterStr != null && !renterStr.isEmpty()) {
                                try {
                                    UUID renter = UUID.fromString(renterStr);
                                    long expires = market.getLong("rent-expires", 0L);
                                    plot.setRenter(renter, expires);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    } else {
                        // legacy keys fallback
                        if (sec.getBoolean("market.is-for-sale", false)) {
                            plot.setForSale(true, sec.getDouble("market.sale-price", 0.0));
                        }
                    }

                    plot.setPlotStatus(sec.getString("plot-status", "ACTIVE"));

                    // Auction
                    if (sec.isConfigurationSection("auction")) {
                        ConfigurationSection auction = sec.getConfigurationSection("auction");
                        if (auction != null) {
                            double bid = auction.getDouble("current-bid", 0.0);
                            String bidderStr = auction.getString("current-bidder");
                            UUID bidder = null;
                            if (bidderStr != null && !bidderStr.isEmpty()) {
                                try { bidder = UUID.fromString(bidderStr); } catch (IllegalArgumentException ignored) {}
                            }
                            plot.setCurrentBid(bid, bidder);
                        }
                    }

                    // Flags
                    if (sec.isConfigurationSection("flags")) {
                        ConfigurationSection flags = sec.getConfigurationSection("flags");
                        if (flags != null) {
                            for (String f : flags.getKeys(false)) {
                                plot.setFlag(f, flags.getBoolean(f));
                            }
                        }
                    }

                    // Roles (skip owner entry if present)
                    if (sec.isConfigurationSection("roles")) {
                        ConfigurationSection roles = sec.getConfigurationSection("roles");
                        if (roles != null) {
                            for (String pUuid : roles.getKeys(false)) {
                                try {
                                    UUID u = UUID.fromString(pUuid);
                                    if (u.equals(ownerId)) continue; // owner should not be a role entry
                                    plot.setRole(u, roles.getString(pUuid));
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    if (sec.isConfigurationSection("role-nicknames")) {
                        ConfigurationSection nicks = sec.getConfigurationSection("role-nicknames");
                        if (nicks != null) {
                            for (String pUuid : nicks.getKeys(false)) {
                                try {
                                    UUID u = UUID.fromString(pUuid);
                                    if (u.equals(ownerId)) continue;
                                    plot.setRoleNickname(u, nicks.getString(pUuid));
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    String roleFlagsBlob = sec.getString("role-flags");
                    if (roleFlagsBlob != null && !roleFlagsBlob.isEmpty()) {
                        plot.deserializeRoleFlags(roleFlagsBlob);
                    }

                    String guestPassesBlob = sec.getString("guest-passes");
                    if (guestPassesBlob != null && !guestPassesBlob.isEmpty()) {
                        plot.deserializeGuestPasses(guestPassesBlob);
                    }

                    String noticeboardBlob = sec.getString("noticeboard");
                    if (noticeboardBlob != null && !noticeboardBlob.isEmpty()) {
                        plot.deserializeNoticeboard(noticeboardBlob);
                    }

                    String allianceBlob = sec.getString("alliance-access");
                    if (allianceBlob != null && !allianceBlob.isEmpty()) {
                        plot.deserializeAllianceAccess(allianceBlob);
                    }

                    if (sec.getBoolean("lockdown-active", false)) {
                        String actorStr = sec.getString("lockdown-activated-by", null);
                        UUID actorId = null;
                        try { if (actorStr != null && !actorStr.isBlank()) actorId = UUID.fromString(actorStr); }
                        catch (IllegalArgumentException ignored) { }
                        plot.restoreLockdown(true, actorId, sec.getString("lockdown-activated-by-name", "Unknown"),
                                sec.getLong("lockdown-activated-at", System.currentTimeMillis()),
                                sec.getLong("lockdown-expires-at", 0L),
                                sec.getString("lockdown-mode", "FULL"));
                    }

                    for (String uuidStr : sec.getStringList("liked-by")) {
                        try { plot.toggleLike(UUID.fromString(uuidStr)); }
                        catch (IllegalArgumentException ignored) {}
                    }

                    for (String uuidStr : sec.getStringList("banned")) {
                        try { plot.addBan(UUID.fromString(uuidStr)); }
                        catch (IllegalArgumentException ignored) {}
                    }

                    // Cosmetics
                    if (sec.isConfigurationSection("cosmetics")) {
                        ConfigurationSection cos = sec.getConfigurationSection("cosmetics");
                        if (cos != null) {
                            plot.setBorderParticle(cos.getString("border-particle"));
                            plot.setAmbientParticle(cos.getString("ambient-particle"));
                            plot.setEntryEffect(cos.getString("entry-effect"));
                        }
                    }

                    // Warp
                    if (sec.isConfigurationSection("warp")) {
                        ConfigurationSection warp = sec.getConfigurationSection("warp");
                        if (warp != null) {
                            boolean isWarp = warp.getBoolean("is-server-warp", false);
                            String warpName = warp.getString("warp-name");
                            String iconName = warp.getString("warp-icon");
                            Material icon = null;
                            if (iconName != null && !iconName.isEmpty()) {
                                try { icon = Material.valueOf(iconName); } catch (IllegalArgumentException ignored) {}
                            }
                            plot.setServerWarp(isWarp, warpName, icon);
                            plot.setWarpCategory(warp.getString("warp-category"));
                        }
                    }

                    if (sec.isConfigurationSection("group")) {
                        ConfigurationSection group = sec.getConfigurationSection("group");
                        if (group != null) {
                            plot.setGroupPlot(group.getBoolean("enabled", false));
                            plot.setTreasuryBalance(group.getDouble("treasury-balance", 0.0));
                            String groupId = group.getString("id");
                            if (groupId != null && !groupId.isBlank()) {
                                try { plot.setGroupId(UUID.fromString(groupId)); } catch (IllegalArgumentException ignored) {}
                            }
                            plot.setGroupName(group.getString("name"));
                        }
                    }

                    // Zones
                    if (sec.isConfigurationSection("zones")) {
                        ConfigurationSection zonesSec = sec.getConfigurationSection("zones");
                        if (zonesSec != null) {
                            for (String zoneName : zonesSec.getKeys(false)) {
                                ConfigurationSection z = zonesSec.getConfigurationSection(zoneName);
                                if (z == null) continue;

                                int zx1 = z.getInt("x1");
                                int zy1 = z.getInt("y1");
                                int zz1 = z.getInt("z1");
                                int zx2 = z.getInt("x2");
                                int zy2 = z.getInt("y2");
                                int zz2 = z.getInt("z2");

                                Zone zone = new Zone(plot, zoneName, zx1, zy1, zz1, zx2, zy2, zz2);
                                zone.setRentPrice(z.getDouble("rent-price", 0.0));
                                zone.setDeposit(z.getDouble("deposit", 0.0));

                                String renterStr = z.getString("renter");
                                long exp = z.getLong("rent-expiration", 0L);
                                if (renterStr != null && !renterStr.isEmpty()) {
                                    try {
                                        UUID renter = UUID.fromString(renterStr);
                                        long now = System.currentTimeMillis();
                                        if (exp > now) {
                                            zone.rentTo(renter, exp - now, z.getDouble("held-deposit", zone.getDeposit()));
                                        }
                                    } catch (IllegalArgumentException ignored) {}
                                }
                                if (zone.getRenter() == null) {
                                    zone.setHeldDeposit(z.getDouble("held-deposit", 0.0));
                                }

                                zone.setFlag("hotel_mode", z.getBoolean("flags.hotel-mode", false));
                                zone.setFlag("guest_visit", z.getBoolean("flags.guest-visit", true));
                                zone.setFlag("guest_interact", z.getBoolean("flags.guest-interact", true));
                                zone.setFlag("guest_containers", z.getBoolean("flags.guest-containers", true));
                                zone.setFlag("guest_build", z.getBoolean("flags.guest-build", false));

                                if (z.isList("guests")) {
                                    for (String guestStr : z.getStringList("guests")) {
                                        try { zone.addGuest(UUID.fromString(guestStr)); }
                                        catch (IllegalArgumentException ignored) {}
                                    }
                                }

                                String spawn = z.getString("spawn-location");
                                if (spawn != null && !spawn.isBlank()) {
                                    Location parsed = parseLocation(plot.getWorld(), spawn);
                                    if (parsed != null) {
                                        zone.setSpawnLocation(parsed);
                                    }
                                }

                                plot.addZone(zone);
                            }
                        }
                    }

                    if (sec.isConfigurationSection("stalls")) {
                        ConfigurationSection stallsSec = sec.getConfigurationSection("stalls");
                        if (stallsSec != null) {
                            for (String stallKey : stallsSec.getKeys(false)) {
                                ConfigurationSection stallSec = stallsSec.getConfigurationSection(stallKey);
                                if (stallSec == null) continue;

                                String stallOwner = stallSec.getString("owner");
                                if (stallOwner == null || stallOwner.isBlank()) continue;

                                try {
                                    MarketStall stall = new MarketStall(
                                            UUID.fromString(stallOwner),
                                            stallSec.getString("owner-name", "Unknown"),
                                            plot.getWorld(),
                                            stallSec.getInt("chest.x"),
                                            stallSec.getInt("chest.y"),
                                            stallSec.getInt("chest.z"),
                                            stallSec.getInt("sign.x"),
                                            stallSec.getInt("sign.y"),
                                            stallSec.getInt("sign.z"),
                                            stallSec.getString("title"),
                                            stallSec.getString("zone"),
                                            stallSec.getLong("created-at", System.currentTimeMillis())
                                    );
                                    if (stallSec.isConfigurationSection("listings")) {
                                        ConfigurationSection listingsSec = stallSec.getConfigurationSection("listings");
                                        if (listingsSec != null) {
                                            for (String slotKey : listingsSec.getKeys(false)) {
                                                try {
                                                    int slot = Integer.parseInt(slotKey);
                                                    ConfigurationSection listingSec = listingsSec.getConfigurationSection(slotKey);
                                                    if (listingSec == null) continue;

                                                    stall.setListing(slot, new MarketStall.StallListing(
                                                            listingSec.getDouble("price", 0.0D),
                                                            parseCurrencyType(listingSec.getString("currency")),
                                                            listingSec.getInt("bundle", 1)
                                                    ));
                                                } catch (NumberFormatException ignored) {}
                                            }
                                        }
                                    }
                                    plot.addStall(stall);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }

                    // Cache with dedupe safety
                    cachePlot(plot);
                    count++;

                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load plot: " + key);
                }
            }

            plugin.getLogger().info("Loaded " + count + " plots from YML.");
            isDirty = false;
        }
    }

    @Override
    public void save() {
        saveSync();
    }

    @Override
    public void saveSync() {
        synchronized (ioLock) {
            if (config == null) config = YamlConfiguration.loadConfiguration(file);

            for (String k : new HashSet<>(config.getKeys(false))) {
                config.set(k, null);
            }

            for (Plot plot : getAllPlots()) {
                writePlotToConfig(plot);
            }

            safeSaveConfigToDisk();
            isDirty = false;
        }
    }

    @Override
    public void savePlot(Plot plot) {
        savePlotSync(plot);
    }

    @Override
    public void savePlotSync(Plot plot) {
        if (plot == null) return;

        synchronized (ioLock) {
            if (config == null) config = YamlConfiguration.loadConfiguration(file);

            writePlotToConfig(plot);
            safeSaveConfigToDisk();
            isDirty = false;
        }
    }

    private void safeSaveConfigToDisk() {
        File tmp = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());

            tmp = new File(parent, file.getName() + ".tmp");
            config.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp.toPath()); } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw new IllegalStateException("Failed to durably save plot data to " + file, error);
        }
    }

    private void writePlotToConfig(Plot plot) {
        String key = plot.getPlotId().toString();

        config.set(key, null);
        ConfigurationSection sec = config.createSection(key);

        sec.set("owner", plot.getOwner().toString());
        sec.set("owner-name", plot.getOwnerName());
        sec.set("world", plot.getWorld());
        sec.set("x1", plot.getX1());
        sec.set("z1", plot.getZ1());
        sec.set("x2", plot.getX2());
        sec.set("z2", plot.getZ2());

        sec.set("level", plot.getLevel());
        sec.set("xp", plot.getXp());
        sec.set("horizons.rank", plot.getHorizonRank());
        sec.set("horizons.expansion-rank", plot.getHorizonExpansionRank());
        sec.set("horizons.renown", plot.getHorizonRenown());
        sec.set("horizons.climate", plot.getHorizonClimate());
        sec.set("ascension.focus", plot.getAscensionFocus());
        sec.set("ascension.focus-changed-at", plot.getAscensionFocusChangedAt());
        sec.set("last-upkeep", plot.getLastUpkeep());
        sec.set("max-members", plot.getMaxMembers());

        sec.set("spawn-location", plot.getSpawnLocationString());
        sec.set("welcome-message", plot.getWelcomeMessage());
        sec.set("farewell-message", plot.getFarewellMessage());
        sec.set("entry-title", plot.getEntryTitle());
        sec.set("entry-subtitle", plot.getEntrySubtitle());
        sec.set("description", plot.getDescription());
        sec.set("custom-biome", plot.getCustomBiome());

        ConfigurationSection market = sec.createSection("market");
        market.set("is-for-sale", plot.isForSale());
        market.set("sale-price", plot.getSalePrice());
        market.set("is-for-rent", plot.isForRent());
        market.set("rent-price", plot.getRentPrice());
        UUID renter = plot.getCurrentRenter();
        market.set("current-renter", renter != null ? renter.toString() : null);
        market.set("rent-expires", plot.getRentExpires());

        sec.set("plot-status", plot.getPlotStatus());

        ConfigurationSection auction = sec.createSection("auction");
        auction.set("current-bid", plot.getCurrentBid());
        UUID bidder = plot.getCurrentBidder();
        auction.set("current-bidder", bidder != null ? bidder.toString() : null);

        ConfigurationSection flags = sec.createSection("flags");
        for (Map.Entry<String, Boolean> entry : plot.getFlags().entrySet()) {
            flags.set(entry.getKey(), entry.getValue());
        }

        // Roles: skip owner entry if it somehow exists
        ConfigurationSection roles = sec.createSection("roles");
        UUID ownerId = plot.getOwner();
        for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
            if (entry.getKey() == null) continue;
            if (ownerId != null && ownerId.equals(entry.getKey())) continue;
            roles.set(entry.getKey().toString(), entry.getValue());
        }

        ConfigurationSection nicknames = sec.createSection("role-nicknames");
        for (Map.Entry<UUID, String> entry : plot.getRoleNicknames().entrySet()) {
            if (entry.getKey() == null) continue;
            if (ownerId != null && ownerId.equals(entry.getKey())) continue;
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;
            nicknames.set(entry.getKey().toString(), entry.getValue());
        }

        String roleFlagsBlob = plot.serializeRoleFlags();
        sec.set("role-flags", roleFlagsBlob.isEmpty() ? null : roleFlagsBlob);

        String guestPassesBlob = plot.serializeGuestPasses();
        sec.set("guest-passes", guestPassesBlob.isEmpty() ? null : guestPassesBlob);

        String noticeboardBlob = plot.serializeNoticeboard();
        sec.set("noticeboard", noticeboardBlob.isEmpty() ? null : noticeboardBlob);

        String allianceBlob = plot.serializeAllianceAccess();
        sec.set("alliance-access", allianceBlob.isEmpty() ? null : allianceBlob);

        if (plot.isLockdownFlagSet()) {
            sec.set("lockdown-active", true);
            sec.set("lockdown-activated-at", plot.getLockdownActivatedAt());
            sec.set("lockdown-expires-at", plot.getLockdownExpiresAt() > 0L ? plot.getLockdownExpiresAt() : null);
            sec.set("lockdown-mode", plot.getLockdownMode());
            sec.set("lockdown-activated-by", plot.getLockdownActivatedBy() == null ? null : plot.getLockdownActivatedBy().toString());
            sec.set("lockdown-activated-by-name", plot.getLockdownActivatedByName());
        } else {
            sec.set("lockdown-active", null);
            sec.set("lockdown-activated-at", null);
            sec.set("lockdown-expires-at", null);
            sec.set("lockdown-mode", null);
            sec.set("lockdown-activated-by", null);
            sec.set("lockdown-activated-by-name", null);
        }

        List<String> liked = plot.getLikedBy().stream().map(UUID::toString).collect(Collectors.toList());
        sec.set("liked-by", liked.isEmpty() ? null : liked);

        List<String> banned = plot.getBannedPlayers().stream().map(UUID::toString).collect(Collectors.toList());
        sec.set("banned", banned.isEmpty() ? null : banned);

        ConfigurationSection cos = sec.createSection("cosmetics");
        cos.set("border-particle", plot.getBorderParticle());
        cos.set("ambient-particle", plot.getAmbientParticle());
        cos.set("entry-effect", plot.getEntryEffect());

        ConfigurationSection warp = sec.createSection("warp");
        warp.set("is-server-warp", plot.isServerWarp());
        warp.set("warp-name", plot.getWarpName());
        warp.set("warp-icon", plot.getWarpIcon() != null ? plot.getWarpIcon().name() : null);
        warp.set("warp-category", plot.getWarpCategory());

        ConfigurationSection group = sec.createSection("group");
        group.set("enabled", plot.isGroupPlot());
        group.set("treasury-balance", plot.getTreasuryBalance());
        group.set("id", plot.getGroupId() == null ? null : plot.getGroupId().toString());
        group.set("name", plot.getGroupName());

        ConfigurationSection zonesSec = sec.createSection("zones");
        for (Zone zone : plot.getZones()) {
            ConfigurationSection z = zonesSec.createSection(zone.getName());
            z.set("x1", zone.getX1());
            z.set("y1", zone.getY1());
            z.set("z1", zone.getZ1());
            z.set("x2", zone.getX2());
            z.set("y2", zone.getY2());
            z.set("z2", zone.getZ2());
            z.set("rent-price", zone.getRentPrice());
            z.set("deposit", zone.getDeposit());
            z.set("held-deposit", zone.getHeldDeposit());
            UUID zr = zone.getRenter();
            z.set("renter", zr != null ? zr.toString() : null);
            z.set("rent-expiration", zone.getRentExpiration());
            z.set("spawn-location", zone.getSpawnLocation() == null ? null : serializeLocation(zone.getSpawnLocation()));
            z.set("guests", zone.getGuestAccess().keySet().stream().map(UUID::toString).collect(Collectors.toList()));

            ConfigurationSection zoneFlags = z.createSection("flags");
            zoneFlags.set("hotel-mode", zone.isHotelMode());
            zoneFlags.set("guest-visit", zone.getFlag("guest_visit", true));
            zoneFlags.set("guest-interact", zone.getFlag("guest_interact", true));
            zoneFlags.set("guest-containers", zone.getFlag("guest_containers", true));
            zoneFlags.set("guest-build", zone.getFlag("guest_build", false));
        }

        ConfigurationSection stallsSec = sec.createSection("stalls");
        for (MarketStall stall : plot.getStalls()) {
            if (stall == null) continue;
            ConfigurationSection stallSec = stallsSec.createSection(stall.getStorageKey());
            stallSec.set("owner", stall.getOwnerId() == null ? null : stall.getOwnerId().toString());
            stallSec.set("owner-name", stall.getOwnerName());
            stallSec.set("title", stall.getTitle());
            stallSec.set("zone", stall.getZoneName());
            stallSec.set("created-at", stall.getCreatedAt());
            stallSec.set("chest.x", stall.getChestX());
            stallSec.set("chest.y", stall.getChestY());
            stallSec.set("chest.z", stall.getChestZ());
            stallSec.set("sign.x", stall.getSignX());
            stallSec.set("sign.y", stall.getSignY());
            stallSec.set("sign.z", stall.getSignZ());
            ConfigurationSection listingsSec = stallSec.createSection("listings");
            for (Map.Entry<Integer, MarketStall.StallListing> entry : stall.getListings().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || !entry.getValue().isValid()) continue;
                ConfigurationSection listingSec = listingsSec.createSection(String.valueOf(entry.getKey()));
                listingSec.set("price", entry.getValue().getPrice());
                listingSec.set("currency", entry.getValue().getCurrency().name());
                listingSec.set("bundle", entry.getValue().getBundleAmount());
            }
        }
    }

    private com.aegisguard.economy.CurrencyType parseCurrencyType(String raw) {
        if (raw == null || raw.isBlank()) return com.aegisguard.economy.CurrencyType.VAULT;
        try {
            return com.aegisguard.economy.CurrencyType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return com.aegisguard.economy.CurrencyType.VAULT;
        }
    }

    private Location parseLocation(String worldName, String raw) {
        if (worldName == null || worldName.isBlank() || raw == null || raw.isBlank()) return null;
        String[] split = raw.split(",");
        if (split.length < 3) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(split[0]);
            double y = Double.parseDouble(split[1]);
            double z = Double.parseDouble(split[2]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String serializeLocation(Location location) {
        if (location == null) return null;
        return location.getX() + "," + location.getY() + "," + location.getZ();
    }

    // ==============================================================
    // --- ACCESSORS ---
    // ==============================================================

    @Override
    public List<Plot> getPlots(UUID owner) {
        if (owner == null) return Collections.emptyList();
        Set<Plot> set = plotsByOwner.get(owner);
        if (set == null || set.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(set);
    }

    @Override
    public Plot getPlot(UUID owner, UUID plotId) {
        if (owner == null || plotId == null) return null;
        Set<Plot> set = plotsByOwner.get(owner);
        if (set == null || set.isEmpty()) return null;
        for (Plot p : set) if (p != null && plotId.equals(p.getPlotId())) return p;
        return null;
    }

    @Override
    public Collection<Plot> getAllPlots() {
        Map<UUID, Plot> byId = new HashMap<>();
        for (Set<Plot> set : plotsByOwner.values()) {
            if (set == null || set.isEmpty()) continue;
            for (Plot p : set) if (p != null) byId.put(p.getPlotId(), p);
        }
        return byId.values();
    }

    @Override
    public Collection<Plot> getPlotsForSale() {
        return getAllPlots().stream().filter(Plot::isForSale).collect(Collectors.toList());
    }

    @Override
    public Collection<Plot> getPlotsForAuction() {
        return getAllPlots().stream().filter(p -> "AUCTION".equals(p.getPlotStatus())).collect(Collectors.toList());
    }

    @Override
    public Plot getPlotAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        String world = loc.getWorld().getName();
        String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);

        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null) return null;

        Set<Plot> candidates = worldMap.get(key);
        if (candidates == null || candidates.isEmpty()) return null;

        for (Plot p : candidates) if (p != null && p.isInside(loc)) return p;
        return null;
    }

    @Override
    public boolean isAreaOverlapping(Plot ignore, String world, int x1, int z1, int x2, int z2) {
        if (world == null || world.isEmpty()) return false;

        Map<String, Set<Plot>> worldMap = plotsByChunk.get(world);
        if (worldMap == null || worldMap.isEmpty()) return false;

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int cMinX = minX >> 4;
        int cMaxX = maxX >> 4;
        int cMinZ = minZ >> 4;
        int cMaxZ = maxZ >> 4;

        Set<Plot> candidates = new HashSet<>();
        for (int cx = cMinX; cx <= cMaxX; cx++) {
            for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                Set<Plot> set = worldMap.get(cx + "," + cz);
                if (set != null && !set.isEmpty()) candidates.addAll(set);
            }
        }

        for (Plot p : candidates) {
            if (p == null) continue;
            if (!world.equals(p.getWorld())) continue;
            if (ignore != null && ignore.getPlotId().equals(p.getPlotId())) continue;

            if (minX <= p.getX2() && maxX >= p.getX1() && minZ <= p.getZ2() && maxZ >= p.getZ1()) {
                return true;
            }
        }

        return false;
    }

    // ==============================================================
    // --- MODIFICATION ---
    // ==============================================================

    @Override
    public void createPlot(UUID owner, Location c1, Location c2) {
        if (owner == null || c1 == null || c2 == null || c1.getWorld() == null || c2.getWorld() == null) return;

        UUID id = UUID.randomUUID();
        String ownerName = Bukkit.getOfflinePlayer(owner).getName();

        int x1 = Math.min(c1.getBlockX(), c2.getBlockX());
        int x2 = Math.max(c1.getBlockX(), c2.getBlockX());
        int z1 = Math.min(c1.getBlockZ(), c2.getBlockZ());
        int z2 = Math.max(c1.getBlockZ(), c2.getBlockZ());

        Plot plot = new Plot(id, owner, ownerName, c1.getWorld().getName(), x1, z1, x2, z2);
        addPlot(plot);
    }

    @Override
    public void addPlot(Plot plot) {
        if (plot == null) return;
        isDirty = true;

        removePlotByIdEverywhere(plot.getPlotId());
        cachePlot(plot);
        savePlotSync(plot);
    }

    @Override
    public void reindexPlot(Plot plot) {
        if (plot == null) return;
        cachePlot(plot);
        isDirty = true;
    }

    @Override
    public void removePlot(UUID owner, UUID plotId) {
        if (plotId == null) return;
        isDirty = true;

        Plot removedPlot = getAllPlots().stream()
                .filter(plot -> plot != null && plotId.equals(plot.getPlotId()))
                .findFirst()
                .orElse(null);

        // Ghost-killer: remove plotId from ANY cached owner set + chunk index
        removePlotByIdEverywhere(plotId);

        if (removedPlot != null) {
            Bukkit.getPluginManager().callEvent(new PlotDeleteEvent(removedPlot));
        }

        synchronized (ioLock) {
            if (config == null) config = YamlConfiguration.loadConfiguration(file);
            config.set(plotId.toString(), null);
            safeSaveConfigToDisk();
            isDirty = false;
        }
    }

    @Override
    public void removeAllPlots(UUID owner) {
        if (owner == null) return;
        isDirty = true;

        Set<Plot> set = plotsByOwner.remove(owner);
        if (set != null) {
            synchronized (ioLock) {
                if (config == null) config = YamlConfiguration.loadConfiguration(file);

                for (Plot p : set) {
                    if (p == null) continue;
                    deIndexPlot(p);
                    Bukkit.getPluginManager().callEvent(new PlotDeleteEvent(p));
                    config.set(p.getPlotId().toString(), null);
                }

                safeSaveConfigToDisk();
                isDirty = false;
            }
        }
    }

    @Override
    public void addPlayerRole(Plot plot, UUID playerUUID, String role) {
        if (plot == null || playerUUID == null) return;
        isDirty = true;
        plot.setRole(playerUUID, role);
        savePlotSync(plot);
    }

    @Override
    public void removePlayerRole(Plot plot, UUID playerUUID) {
        if (plot == null || playerUUID == null) return;
        isDirty = true;
        plot.removeRole(playerUUID);
        savePlotSync(plot);
    }

    @Override
    public void changePlotOwner(Plot plot, UUID newOwner, String newOwnerName) {
        if (plot == null || newOwner == null) return;
        isDirty = true;

        UUID plotId = plot.getPlotId();
        UUID oldOwner = plot.getOwner();

        if (oldOwner != null && oldOwner.equals(newOwner)) {
            plot.setOwnerName(newOwnerName);
            savePlotSync(plot);
            return;
        }

        // Hard purge any cached copies first (prevents owner ghosts)
        removePlotByIdEverywhere(plotId);

        // Transfer owner
        plot.internalSetOwner(newOwner, newOwnerName);

        // Ensure old owner cannot retain permissions via a leftover role entry
        if (oldOwner != null) {
            try { plot.removeRole(oldOwner); } catch (Throwable ignored) {}
        }

        // Re-cache under new owner + re-index
        cachePlot(plot);

        savePlotSync(plot);
    }

    @Override
    public void updatePlotBounds(Plot plot, int x1, int z1, int x2, int z2) {
        if (plot == null) return;
        removePlotByIdEverywhere(plot.getPlotId());
        plot.setBounds(x1, z1, x2, z2);
        cachePlot(plot);
        savePlotSync(plot);
    }

    @Override
    public void removeBannedPlots() {
        for (OfflinePlayer p : Bukkit.getBannedPlayers()) {
            removeAllPlots(p.getUniqueId());
        }
    }

    private void cachePlot(Plot plot) {
        // Safety net: always dedupe by plotId before indexing
        removePlotByIdEverywhere(plot.getPlotId());

        Set<Plot> ownerSet = plotsByOwner.computeIfAbsent(plot.getOwner(), k -> ConcurrentHashMap.newKeySet());
        ownerSet.add(plot);

        String w = plot.getWorld();
        int minX = plot.getX1() >> 4;
        int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxZ = plot.getZ2() >> 4;

        Map<String, Set<Plot>> worldMap = plotsByChunk.computeIfAbsent(w, k -> new ConcurrentHashMap<>());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                worldMap.computeIfAbsent(x + "," + z, k -> ConcurrentHashMap.newKeySet()).add(plot);
            }
        }
    }

    private void deIndexPlot(Plot plot) {
        if (plot == null) return;

        String w = plot.getWorld();
        Map<String, Set<Plot>> worldMap = plotsByChunk.get(w);
        if (worldMap == null) return;

        int minX = plot.getX1() >> 4;
        int maxX = plot.getX2() >> 4;
        int minZ = plot.getZ1() >> 4;
        int maxZ = plot.getZ2() >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String k = x + "," + z;
                Set<Plot> set = worldMap.get(k);
                if (set != null) {
                    set.remove(plot);
                    if (set.isEmpty()) worldMap.remove(k);
                }
            }
        }
        if (worldMap.isEmpty()) plotsByChunk.remove(w);
    }

    private void removePlotByIdEverywhere(UUID plotId) {
        if (plotId == null) return;

        for (Map.Entry<UUID, Set<Plot>> entry : plotsByOwner.entrySet()) {
            Set<Plot> set = entry.getValue();
            if (set == null || set.isEmpty()) continue;

            Plot found = null;
            for (Plot p : set) {
                if (p != null && plotId.equals(p.getPlotId())) {
                    found = p;
                    break;
                }
            }

            if (found != null) {
                set.remove(found);
                deIndexPlot(found);
            }
        }
    }

    // ==============================================================
    // --- MISC / CONTRACT ---
    // ==============================================================

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
    }

    @Override public void logWildernessBlock(Location loc, String o, String n, UUID p) {}
    @Override public void revertWildernessBlocks(long t, int l) {}

    @Override
    public TriState getRoleFlagState(Plot plot, String roleName, String flagKey) {
        if (plot == null) return TriState.INHERIT;
        return plot.getRoleFlagState(roleName, flagKey);
    }

    @Override
    public void setRoleFlagState(Plot plot, String roleName, String flagKey, TriState state) {
        if (plot == null) return;
        isDirty = true;
        plot.setRoleFlagState(roleName, flagKey, state == null ? TriState.INHERIT : state);
        savePlotSync(plot);
    }

    @Override
    public void shutdown() {
        // YML is sync, so just force save.
        try { saveSync(); } catch (Throwable ignored) {}
    }
}
