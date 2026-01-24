package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.flags.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Plot (Data Class) - v1.2.6 (restored 1.2.5 API + 1.2.6 QoL)
 *
 * Goals of this restore:
 * - Bring back the 1.2.5 methods your GUIs/listeners expect (market, auction, cosmetics, zoning, spawn, entry title, etc.)
 * - Keep newer 1.2.6-friendly helpers (area calc, isInside alias, no-arg getCenter, canBuild/canManage helpers)
 * - Avoid breaking existing stored data by keeping the same field/method surface as 1.2.5
 */
public class Plot {

    public static final UUID SERVER_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // --- CORE ---
    private UUID plotId;
    private UUID owner;
    private String ownerName;
    private String world; // IMPORTANT: String world name (1.2.5/1.2.6 GUI expects this)

    // Bounds (2D)
    private int x1, z1, x2, z2;

    // --- CLAIM FLAGS ---
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    // --- MEMBERS & ROLES ---
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();
    private final List<UUID> bannedPlayers = new CopyOnWriteArrayList<>();

    // Role flag overrides (role -> flag -> TriState)
    private final Map<String, Map<String, TriState>> roleFlagStates = new ConcurrentHashMap<>();

    // --- PLOT META ---
    private String plotName;
    private String description;

    // --- GREETINGS (chat) ---
    private String welcomeMessage;
    private String farewellMessage;

    // --- GREETINGS (titles) ---
    private String entryTitle;
    private String entrySubtitle;

    // --- SPAWN ---
    private Location spawnLocation;

    // --- COSMETICS ---
    private String borderParticle;
    private String ambientParticle;

    // --- MARKET ---
    private boolean forSale;
    private double salePrice;

    // --- RENT ---
    private boolean forRent;
    private double rentPrice;
    private UUID currentRenter;
    private long rentEndTime;

    // --- AUCTION ---
    private boolean forAuction;
    private double auctionStartPrice;
    private double currentBid;
    private UUID currentBidder;
    private long auctionEndTime;

    // --- LEVELING ---
    private int level = 1;
    private int xp = 0;

    // --- ZONING ---
    private final List<Zone> zones = new CopyOnWriteArrayList<>();

    // --- BIOME ---
    private String customBiome;

    // --- UPKEEP / STATUS ---
    private long lastUpkeepPayment;
    private String plotStatus;

    // --- SOCIAL ---
    private int likes;
    private final Set<UUID> likedBy = ConcurrentHashMap.newKeySet();

    // --- SERVER WARP ---
    private boolean serverWarp;

    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
        this.plotId = plotId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        setBounds(x1, z1, x2, z2);
    }

    // ---------------------------------------------------------------------
    // Identity / World
    // ---------------------------------------------------------------------

    public UUID getPlotId() {
        return plotId;
    }

    /** 1.2.6+ compatibility alias */
    public UUID getId() {
        return plotId;
    }

    public void setPlotId(UUID plotId) {
        this.plotId = plotId;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    /** IMPORTANT: String world name (1.2.5/1.2.6 GUIs expect this to be String, not World). */
    public String getWorld() {
        return world;
    }

    /** 1.2.6+ readability alias */
    public String getWorldName() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public World getBukkitWorld() {
        return Bukkit.getWorld(world);
    }

    // ---------------------------------------------------------------------
    // Bounds / Geometry
    // ---------------------------------------------------------------------

    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }

    public void setX1(int x1) { this.x1 = x1; normalizeBounds(); }
    public void setZ1(int z1) { this.z1 = z1; normalizeBounds(); }
    public void setX2(int x2) { this.x2 = x2; normalizeBounds(); }
    public void setZ2(int z2) { this.z2 = z2; normalizeBounds(); }

    /** v1.2.6 convenience: set all bounds and normalize. */
    public void setBounds(int x1, int z1, int x2, int z2) {
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        normalizeBounds();
    }

    private void normalizeBounds() {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        this.x1 = minX;
        this.x2 = maxX;
        this.z1 = minZ;
        this.z2 = maxZ;
    }

    /** v1.2.6: Used for claim-block / sizing math. */
    public int getArea() {
        return (Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1);
    }

    public boolean isInPlot(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equalsIgnoreCase(world)) return false;

        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    /** v1.2.6 alias used by some newer code */
    public boolean isInside(Location location) {
        return isInPlot(location);
    }

    /** 1.2.6 compatibility: no-arg center. */
    public Location getCenter() {
        World w = getBukkitWorld();
        if (w == null) return null;

        int centerX = (x1 + x2) / 2;
        int centerZ = (z1 + z2) / 2;
        return new Location(w, centerX + 0.5, w.getHighestBlockYAt(centerX, centerZ) + 1.0, centerZ + 0.5);
    }

    /** Original 1.2.5 signature used by older GUIs */
    public Location getCenter(AegisGuard plugin) {
        return getCenter();
    }

    // ---------------------------------------------------------------------
    // Flags
    // ---------------------------------------------------------------------

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public boolean getFlag(String key, boolean defaultValue) {
        if (key == null) return defaultValue;
        return flags.getOrDefault(key.toLowerCase(Locale.ROOT), defaultValue);
    }

    public void setFlag(String key, boolean value) {
        if (key == null) return;
        flags.put(key.toLowerCase(Locale.ROOT), value);
    }

    // ---------------------------------------------------------------------
    // Roles / Permissions
    // ---------------------------------------------------------------------

    public Map<UUID, String> getPlayerRoles() {
        return playerRoles;
    }

    public String getRole(UUID playerUUID) {
        if (playerUUID == null) return "visitor";
        return playerRoles.getOrDefault(playerUUID, "visitor");
    }

    public void setRole(UUID playerUUID, String role) {
        if (playerUUID == null) return;

        if (role == null || role.equalsIgnoreCase("default") || role.equalsIgnoreCase("none")) {
            playerRoles.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase(Locale.ROOT));
            bannedPlayers.remove(playerUUID);
        }
    }

    public void removeRole(UUID playerUUID) {
        if (playerUUID == null) return;
        playerRoles.remove(playerUUID);
    }

    public boolean isOwner(UUID uuid) {
        return uuid != null && uuid.equals(owner);
    }

    public boolean isOwner(Player player) {
        return player != null && isOwner(player.getUniqueId());
    }

    public boolean isBanned(UUID uuid) {
        return uuid != null && bannedPlayers.contains(uuid);
    }

    public List<UUID> getBannedPlayers() {
        return bannedPlayers;
    }

    public void addBan(UUID playerUUID) {
        if (playerUUID == null) return;
        if (!bannedPlayers.contains(playerUUID)) bannedPlayers.add(playerUUID);
        playerRoles.remove(playerUUID);
    }

    public void removeBan(UUID playerUUID) {
        if (playerUUID == null) return;
        bannedPlayers.remove(playerUUID);
    }

    /** Legacy semantic: "trusted" means they have a non-visitor role and are not banned. */
    public boolean isTrusted(Player player) {
        if (player == null) return false;
        if (isOwner(player)) return true;
        UUID uuid = player.getUniqueId();
        if (isBanned(uuid)) return false;
        String role = getRole(uuid);
        return role != null && !role.equalsIgnoreCase("visitor");
    }

    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        if (playerUUID == null || permission == null || plugin == null) return false;

        // Owner always allowed
        if (owner.equals(playerUUID)) return true;

        // Banned never allowed
        if (isBanned(playerUUID)) return false;

        // Admin/bypass always allowed
        Player online = Bukkit.getPlayer(playerUUID);
        if (online != null && (plugin.isAdmin(online) || plugin.isBypassing(online) || online.hasPermission("aegis.bypass"))) {
            return true;
        }

        String role = getRole(playerUUID);
        if (role == null) role = "visitor";

        // Role permissions from config: roles.<role>.permissions
        List<String> perms = plugin.cfg().raw().getStringList("roles." + role.toLowerCase(Locale.ROOT) + ".permissions");
        if (perms == null || perms.isEmpty()) return false;

        String needle = permission.toUpperCase(Locale.ROOT);
        for (String p : perms) {
            if (p == null) continue;
            String up = p.toUpperCase(Locale.ROOT);
            if ("ALL".equals(up) || up.equals(needle)) return true;
        }
        return false;
    }

    // --- Role flag overrides ---

    public TriState getRoleFlagState(String roleName, String flagKey) {
        if (roleName == null || flagKey == null) return TriState.INHERIT;
        Map<String, TriState> perRole = roleFlagStates.get(roleName.toLowerCase(Locale.ROOT));
        if (perRole == null) return TriState.INHERIT;
        return perRole.getOrDefault(flagKey.toLowerCase(Locale.ROOT), TriState.INHERIT);
    }

    public void setRoleFlagState(String roleName, String flagKey, TriState state) {
        if (roleName == null || flagKey == null || state == null) return;

        String r = roleName.toLowerCase(Locale.ROOT);
        String f = flagKey.toLowerCase(Locale.ROOT);

        roleFlagStates.putIfAbsent(r, new ConcurrentHashMap<>());
        Map<String, TriState> perRole = roleFlagStates.get(r);

        if (state == TriState.INHERIT) {
            perRole.remove(f);
        } else {
            perRole.put(f, state);
        }
    }

    public Map<String, Map<String, TriState>> getRoleFlagStates() {
        return roleFlagStates;
    }

    // ---------------------------------------------------------------------
    // 1.2.6 QoL helpers: unified manage/build checks
    // ---------------------------------------------------------------------

    /** v1.2.6: Prefer this over raw owner checks for GUIs. */
    public boolean canManage(@Nullable Player player, @Nullable AegisGuard plugin) {
        if (player == null) return false;
        AegisGuard pl = (plugin != null) ? plugin : AegisGuard.getInstance();

        if (pl.isAdmin(player) || pl.isBypassing(player) || player.hasPermission("aegis.bypass")) return true;
        return hasPermission(player.getUniqueId(), "MANAGE", pl);
    }

    /** v1.2.6: Convenience overload */
    public boolean canManage(@Nullable Player player) {
        return canManage(player, AegisGuard.getInstance());
    }

    /**
     * v1.2.6: Member modification guard.
     * - Owner/admin can modify.
     * - Otherwise requires MANAGE_MEMBERS on the plot role.
     * - Prevent owner from removing themselves (plot orphan protection).
     */
    public boolean canModifyMember(@Nullable Player editor, @Nullable UUID targetUUID, @Nullable AegisGuard plugin) {
        if (editor == null || targetUUID == null) return false;
        AegisGuard pl = (plugin != null) ? plugin : AegisGuard.getInstance();

        if (pl.isAdmin(editor) || pl.isBypassing(editor) || editor.hasPermission("aegis.bypass")) return true;

        // Owners can manage members, but cannot remove themselves.
        if (isOwner(editor)) {
            return !editor.getUniqueId().equals(targetUUID);
        }

        // Role-based permission
        return hasPermission(editor.getUniqueId(), "MANAGE_MEMBERS", pl);
    }

    /** v1.2.6 convenience overload */
    public boolean canModifyMember(@Nullable Player editor, @Nullable UUID targetUUID) {
        return canModifyMember(editor, targetUUID, AegisGuard.getInstance());
    }

    /**
     * v1.2.6: Build check using flag + role override + role permission.
     * - Admin/bypass always allow
     * - Role flag override on "build" beats everything
     * - If plot flag build=true allow
     * - Else require role permission (BUILD / BLOCK_PLACE / BLOCK_BREAK)
     */
    public boolean canBuild(@Nullable Player player, @Nullable AegisGuard plugin, @Nullable String permission) {
        if (player == null) return false;
        AegisGuard pl = (plugin != null) ? plugin : AegisGuard.getInstance();

        if (pl.isAdmin(player) || pl.isBypassing(player) || player.hasPermission("aegis.bypass")) return true;

        UUID uuid = player.getUniqueId();
        if (isOwner(uuid)) return true;
        if (isBanned(uuid)) return false;

        String role = getRole(uuid);
        TriState override = getRoleFlagState(role, "build");
        if (override == TriState.ALLOW) return true;
        if (override == TriState.DENY) return false;

        if (getFlag("build", true)) return true;

        String perm = (permission == null || permission.isEmpty()) ? "BUILD" : permission.toUpperCase(Locale.ROOT);

        if ("BLOCK_BREAK".equals(perm) || "BLOCK_PLACE".equals(perm)) {
            return hasPermission(uuid, perm, pl) || hasPermission(uuid, "BUILD", pl);
        }

        return hasPermission(uuid, perm, pl);
    }

    /** v1.2.6 convenience overload */
    public boolean canBuild(@Nullable Player player, @Nullable String permission) {
        return canBuild(player, AegisGuard.getInstance(), permission);
    }

    // ---------------------------------------------------------------------
    // Server zone / warp
    // ---------------------------------------------------------------------

    public boolean isServerZone() {
        return SERVER_OWNER_UUID.equals(owner);
    }

    public boolean isServerWarp() {
        return serverWarp;
    }

    public void setServerWarp(boolean serverWarp) {
        this.serverWarp = serverWarp;
    }

    // ---------------------------------------------------------------------
    // Meta
    // ---------------------------------------------------------------------

    public String getPlotName() {
        return plotName;
    }

    public void setPlotName(String plotName) {
        this.plotName = plotName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Material getIconMaterial() {
        return Material.GRASS_BLOCK;
    }

    // ---------------------------------------------------------------------
    // Greetings (chat)
    // ---------------------------------------------------------------------

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public String getFarewellMessage() {
        return farewellMessage;
    }

    public void setFarewellMessage(String farewellMessage) {
        this.farewellMessage = farewellMessage;
    }

    // ---------------------------------------------------------------------
    // Entry titles (used by PlotGreetingListener + VisitGUI)
    // ---------------------------------------------------------------------

    public String getEntryTitle() {
        return entryTitle;
    }

    public void setEntryTitle(String entryTitle) {
        this.entryTitle = entryTitle;
    }

    public String getEntrySubtitle() {
        return entrySubtitle;
    }

    public void setEntrySubtitle(String entrySubtitle) {
        this.entrySubtitle = entrySubtitle;
    }

    // ---------------------------------------------------------------------
    // Spawn
    // ---------------------------------------------------------------------

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    // ---------------------------------------------------------------------
    // Cosmetics
    // ---------------------------------------------------------------------

    public String getBorderParticle() {
        return borderParticle;
    }

    public void setBorderParticle(String borderParticle) {
        this.borderParticle = borderParticle;
    }

    public String getAmbientParticle() {
        return ambientParticle;
    }

    public void setAmbientParticle(String ambientParticle) {
        this.ambientParticle = ambientParticle;
    }

    // ---------------------------------------------------------------------
    // Market (sale)
    // ---------------------------------------------------------------------

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
        if (!forSale) this.salePrice = 0.0;
    }

    /** 1.2.5+ signature: used by older market GUIs/commands */
    public void setForSale(boolean forSale, double salePrice) {
        this.forSale = forSale;
        this.salePrice = forSale ? Math.max(0.0, salePrice) : 0.0;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = Math.max(0.0, salePrice);
    }

    // ---------------------------------------------------------------------
    // Rent
    // ---------------------------------------------------------------------

    public boolean isForRent() {
        return forRent;
    }

    public void setForRent(boolean forRent) {
        this.forRent = forRent;
        if (!forRent) {
            this.rentPrice = 0.0;
            this.currentRenter = null;
            this.rentEndTime = 0;
        }
    }

    public double getRentPrice() {
        return rentPrice;
    }

    public void setRentPrice(double rentPrice) {
        this.rentPrice = Math.max(0.0, rentPrice);
    }

    public UUID getCurrentRenter() {
        return currentRenter;
    }

    public void setCurrentRenter(UUID currentRenter) {
        this.currentRenter = currentRenter;
    }

    public long getRentEndTime() {
        return rentEndTime;
    }

    public void setRentEndTime(long rentEndTime) {
        this.rentEndTime = rentEndTime;
    }

    // ---------------------------------------------------------------------
    // Auction
    // ---------------------------------------------------------------------

    public boolean isForAuction() {
        return forAuction;
    }

    public void setForAuction(boolean forAuction) {
        this.forAuction = forAuction;
        if (!forAuction) {
            this.auctionStartPrice = 0.0;
            this.currentBid = 0.0;
            this.currentBidder = null;
            this.auctionEndTime = 0;
        }
    }

    public double getAuctionStartPrice() {
        return auctionStartPrice;
    }

    public void setAuctionStartPrice(double auctionStartPrice) {
        this.auctionStartPrice = Math.max(0.0, auctionStartPrice);
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public UUID getCurrentBidder() {
        return currentBidder;
    }

    public void setCurrentBid(double currentBid, UUID currentBidder) {
        this.currentBid = Math.max(0.0, currentBid);
        this.currentBidder = currentBidder;
    }

    public long getAuctionEndTime() {
        return auctionEndTime;
    }

    public void setAuctionEndTime(long auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    // ---------------------------------------------------------------------
    // Leveling
    // ---------------------------------------------------------------------

    public int getLevel() {
        return Math.max(1, level);
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getXp() {
        return Math.max(0, xp);
    }

    public void setXp(int xp) {
        this.xp = Math.max(0, xp);
    }

    // ---------------------------------------------------------------------
    // Zoning
    // ---------------------------------------------------------------------

    public List<Zone> getZones() {
        return zones;
    }

    public void addZone(Zone zone) {
        if (zone == null) return;
        zones.add(zone);
    }

    public void removeZone(Zone zone) {
        if (zone == null) return;
        zones.remove(zone);
    }

    // ---------------------------------------------------------------------
    // Biome
    // ---------------------------------------------------------------------

    public String getCustomBiome() {
        return customBiome;
    }

    public void setCustomBiome(String customBiome) {
        this.customBiome = customBiome;
    }

    // ---------------------------------------------------------------------
    // Upkeep / Status
    // ---------------------------------------------------------------------

    public long getLastUpkeepPayment() {
        return lastUpkeepPayment;
    }

    public void setLastUpkeepPayment(long lastUpkeepPayment) {
        this.lastUpkeepPayment = lastUpkeepPayment;
    }

    public String getPlotStatus() {
        return plotStatus;
    }

    public void setPlotStatus(String plotStatus) {
        this.plotStatus = plotStatus;
    }

    // ---------------------------------------------------------------------
    // Social
    // ---------------------------------------------------------------------

    public int getLikes() {
        return Math.max(0, likes);
    }

    public void setLikes(int likes) {
        this.likes = Math.max(0, likes);
    }

    public Set<UUID> getLikedBy() {
        return likedBy;
    }

    public boolean hasLiked(UUID uuid) {
        return uuid != null && likedBy.contains(uuid);
    }

    public void addLike(UUID uuid) {
        if (uuid == null) return;
        if (likedBy.add(uuid)) likes++;
    }

    public void removeLike(UUID uuid) {
        if (uuid == null) return;
        if (likedBy.remove(uuid)) likes = Math.max(0, likes - 1);
    }
}
