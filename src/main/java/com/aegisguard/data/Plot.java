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
    private int maxMembers = 2;

    // --- COSMETICS ---
    private String borderParticle;
    private String ambientParticle;
    private String entryEffect;

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
    private final List<MarketStall> stalls = new CopyOnWriteArrayList<>();

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
    private String warpName;
    private Material warpIcon;

    // --- GROUP / SHARED PLOT ---
    private boolean groupPlot;
    private double treasuryBalance;
    private UUID groupId;
    private String groupName;

    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2) {
        this.plotId = plotId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        setBounds(x1, z1, x2, z2);
        this.lastUpkeepPayment = System.currentTimeMillis();
    }

    public Plot(UUID plotId, UUID owner, String ownerName, String world, int x1, int z1, int x2, int z2, long lastUpkeepPayment) {
        this(plotId, owner, ownerName, world, x1, z1, x2, z2);
        this.lastUpkeepPayment = lastUpkeepPayment;
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

    public void internalSetOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        removeRole(owner);
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

    /** Legacy compatibility alias used by older listeners/GUI code. */
    public boolean contains(Location location) {
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
        if (isOwner(playerUUID) || SERVER_OWNER_UUID.equals(playerUUID)) return;

        if (role == null || role.equalsIgnoreCase("default") || role.equalsIgnoreCase("none")) {
            playerRoles.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase(Locale.ROOT));
            bannedPlayers.remove(playerUUID);
        }
    }

    public void removeRole(UUID playerUUID) {
        if (playerUUID == null) return;
        if (isOwner(playerUUID) || SERVER_OWNER_UUID.equals(playerUUID)) return;
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
        if (hasElevatedManagementAccess(online, plugin)) {
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

        if (hasElevatedManagementAccess(player, pl)) return true;
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

        if (isOwner(targetUUID) || SERVER_OWNER_UUID.equals(targetUUID)) return false;
        if (editor.getUniqueId().equals(targetUUID)) return false;

        if (hasElevatedManagementAccess(editor, pl)) return true;

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
     * - If plot flag build=false, the plot is explicitly public-build
     * - Otherwise require role permission (BUILD / BLOCK_PLACE / BLOCK_BREAK)
     */
    public boolean canBuild(@Nullable Player player, @Nullable AegisGuard plugin, @Nullable String permission) {
        if (player == null) return false;
        AegisGuard pl = (plugin != null) ? plugin : AegisGuard.getInstance();

        if (hasElevatedManagementAccess(player, pl)) return true;

        UUID uuid = player.getUniqueId();
        if (isOwner(uuid)) return true;
        if (isBanned(uuid)) return false;

        String role = getRole(uuid);
        TriState override = getRoleFlagState(role, "build");
        if (override == TriState.ALLOW) return true;
        if (override == TriState.DENY) return false;

        // "build" acts as a public-build override.
        // By default claims are protected, so only trusted roles can build unless a plot explicitly opens building up.
        if (!getFlag("build", true)) return true;

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

    public boolean isZoneRenter(@Nullable UUID playerUUID, @Nullable Location location) {
        if (playerUUID == null || location == null) return false;
        if (isBanned(playerUUID)) return false;

        Zone zone = getZoneAt(location);
        return zone != null && zone.isRentedBy(playerUUID);
    }

    public @Nullable Zone getRentedZoneAt(@Nullable Location location) {
        Zone zone = getZoneAt(location);
        return zone != null && zone.isRented() ? zone : null;
    }

    public @Nullable Zone getRentedZoneFor(@Nullable UUID playerUUID) {
        if (playerUUID == null) return null;
        for (Zone zone : zones) {
            if (zone != null && zone.isRentedBy(playerUUID)) {
                return zone;
            }
        }
        return null;
    }

    private boolean canUseRentedZone(@Nullable Player player, @Nullable Location location, @Nullable AegisGuard plugin, @Nullable String permission) {
        if (player == null || location == null) return false;
        Zone zone = getRentedZoneAt(location);
        if (zone == null) return false;

        AegisGuard pl = (plugin != null) ? plugin : AegisGuard.getInstance();
        if (canManage(player, pl)) return true;

        UUID uuid = player.getUniqueId();
        if (zone.isRentedBy(uuid)) return true;
        if (isBanned(uuid)) return false;

        String perm = permission == null ? "INTERACT" : permission.toUpperCase(Locale.ROOT);
        return switch (perm) {
            case "BLOCK_BREAK", "BLOCK_PLACE", "BUILD" -> zone.canGuestBuild(uuid);
            case "CONTAINERS" -> zone.canGuestUseContainers(uuid);
            case "VEHICLES" -> zone.canGuestUseVehicles(uuid);
            default -> zone.canGuestInteract(uuid);
        };
    }

    public boolean canBuildAt(@Nullable Player player, @Nullable Location location, @Nullable AegisGuard plugin, @Nullable String permission) {
        if (player == null) return false;
        Zone rentedZone = getRentedZoneAt(location);
        if (rentedZone != null) {
            return canUseRentedZone(player, location, plugin, permission);
        }
        if (canBuild(player, plugin, permission)) return true;
        return isZoneRenter(player.getUniqueId(), location);
    }

    public boolean canBuildAt(@Nullable Player player, @Nullable Location location, @Nullable String permission) {
        return canBuildAt(player, location, AegisGuard.getInstance(), permission);
    }

    public boolean canInteractAt(@Nullable Player player, @Nullable Location location, @Nullable AegisGuard plugin, @Nullable String permission) {
        if (player == null) return false;
        AegisGuard pl = (plugin != null) ? plugin : AegisGuard.getInstance();

        Zone rentedZone = getRentedZoneAt(location);
        if (rentedZone != null) {
            return canUseRentedZone(player, location, pl, permission);
        }

        if (canBuildAt(player, location, pl, permission)) return true;

        UUID uuid = player.getUniqueId();
        if (hasPermission(uuid, "INTERACT", pl)) return true;
        if (permission != null && !permission.isBlank() && hasPermission(uuid, permission, pl)) return true;

        return isZoneRenter(uuid, location);
    }

    public boolean canInteractAt(@Nullable Player player, @Nullable Location location, @Nullable String permission) {
        return canInteractAt(player, location, AegisGuard.getInstance(), permission);
    }

    // ---------------------------------------------------------------------
    // Server zone / warp
    // ---------------------------------------------------------------------

    public boolean isServerZone() {
        return SERVER_OWNER_UUID.equals(owner);
    }

    private boolean hasElevatedManagementAccess(@Nullable Player player, @Nullable AegisGuard plugin) {
        if (player == null || plugin == null) return false;
        if (plugin.isAdmin(player) || plugin.isBypassing(player) || player.hasPermission("aegis.bypass")) return true;
        if (hasAnyPermission(player, plugin, "staff_access.global_manage_permissions", List.of("aegis.admin.manage"))) {
            return true;
        }
        if (isServerZone() && hasAnyPermission(player, plugin, "staff_access.server_zone_manage_permissions",
                List.of("aegis.serverzone.manage", "aegis.staff.co_owner"))) {
            return true;
        }
        return isMarketManaged() && hasAnyPermission(player, plugin, "staff_access.market_plot_manage_permissions",
                List.of("aegis.market.manage", "aegis.staff.market_steward"));
    }

    private boolean isMarketManaged() {
        return isForSale() || isForRent() || isForAuction() || isServerWarp();
    }

    private boolean hasAnyPermission(@Nullable Player player, @Nullable AegisGuard plugin, String path, List<String> fallback) {
        if (player == null || plugin == null) return false;

        List<String> permissions = fallback;
        try {
            List<String> configured = plugin.getConfig().getStringList(path);
            if (configured != null && !configured.isEmpty()) {
                permissions = configured;
            }
        } catch (Throwable ignored) {}

        if (permissions == null || permissions.isEmpty()) return false;
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean isServerWarp() {
        return serverWarp;
    }

    public void setServerWarp(boolean serverWarp) {
        this.serverWarp = serverWarp;
    }

    public boolean isGroupPlot() {
        return groupPlot;
    }

    public void setGroupPlot(boolean groupPlot) {
        this.groupPlot = groupPlot;
        if (!groupPlot && treasuryBalance < 0.0) {
            treasuryBalance = 0.0;
        }
    }

    public double getTreasuryBalance() {
        return Math.max(0.0, treasuryBalance);
    }

    public void setTreasuryBalance(double treasuryBalance) {
        this.treasuryBalance = Math.max(0.0, treasuryBalance);
    }

    public void addTreasuryFunds(double amount) {
        if (amount <= 0.0) return;
        treasuryBalance = Math.max(0.0, treasuryBalance + amount);
    }

    public boolean withdrawTreasuryFunds(double amount) {
        if (amount <= 0.0) return true;
        if (treasuryBalance + 0.000001D < amount) return false;
        treasuryBalance = Math.max(0.0, treasuryBalance - amount);
        return true;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    public String getSpawnLocationString() {
        Location loc = spawnLocation;
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    public void setSpawnLocationFromString(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            this.spawnLocation = null;
            return;
        }
        try {
            String[] parts = serialized.split(",");
            if (parts.length < 4) {
                this.spawnLocation = null;
                return;
            }
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) {
                this.spawnLocation = null;
                return;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0F;
            this.spawnLocation = new Location(w, x, y, z, yaw, pitch);
        } catch (Exception ignored) {
            this.spawnLocation = null;
        }
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

    public String getEntryEffect() {
        return entryEffect;
    }

    public void setEntryEffect(String entryEffect) {
        this.entryEffect = entryEffect;
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

    public void setForRent(boolean forRent, double rentPrice) {
        this.forRent = forRent;
        if (forRent) {
            this.rentPrice = Math.max(0.0, rentPrice);
        } else {
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

    public void setRenter(UUID renter, long rentExpires) {
        this.currentRenter = renter;
        this.rentEndTime = Math.max(0L, rentExpires);
    }

    public long getRentExpires() {
        return rentEndTime;
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

    public void setXp(double xp) {
        this.xp = Math.max(0, (int) Math.round(xp));
    }

    public int getMaxMembers() {
        return Math.max(1, maxMembers);
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(1, maxMembers);
    }

    public void expand(int amount) {
        if (amount <= 0) return;
        this.x1 -= amount;
        this.z1 -= amount;
        this.x2 += amount;
        this.z2 += amount;
        normalizeBounds();
    }

    // ---------------------------------------------------------------------
    // Zoning
    // ---------------------------------------------------------------------

    public List<Zone> getZones() {
        return zones;
    }

    public void addZone(Zone zone) {
        if (zone == null) return;
        removeZoneByName(zone.getName());
        zones.add(zone);
    }

    public void removeZone(Zone zone) {
        if (zone == null) return;
        zones.remove(zone);
    }

    public void removeZoneByName(String zoneName) {
        if (zoneName == null || zoneName.isBlank()) return;
        zones.removeIf(zone -> zone != null && zoneName.equalsIgnoreCase(zone.getName()));
    }

    public Zone getZone(String zoneName) {
        if (zoneName == null || zoneName.isBlank()) return null;
        for (Zone zone : zones) {
            if (zone != null && zoneName.equalsIgnoreCase(zone.getName())) {
                return zone;
            }
        }
        return null;
    }

    public boolean hasZone(String zoneName) {
        return getZone(zoneName) != null;
    }

    public boolean isZoneNameAvailable(String zoneName) {
        return !hasZone(zoneName);
    }

    public String nextAvailableZoneName(String baseName) {
        String base = (baseName == null || baseName.isBlank()) ? "Zone" : baseName.trim();
        if (isZoneNameAvailable(base)) return base;

        int index = 2;
        while (!isZoneNameAvailable(base + "-" + index)) {
            index++;
        }
        return base + "-" + index;
    }

    public boolean containsZoneBounds(int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        return minX >= this.x1 && maxX <= this.x2
                && minZ >= this.z1 && maxZ <= this.z2;
    }

    public boolean overlapsZone(Zone candidate, @Nullable Zone ignore) {
        if (candidate == null) return false;
        for (Zone zone : zones) {
            if (zone == null || zone == ignore) continue;
            if (candidate.overlaps(zone)) return true;
        }
        return false;
    }

    public Zone getZoneAt(Location location) {
        if (location == null) return null;
        for (Zone zone : zones) {
            if (zone != null && zone.isInside(location)) {
                return zone;
            }
        }
        return null;
    }

    public boolean hasBrowsableZonesFor(@Nullable Player player) {
        UUID viewer = player == null ? null : player.getUniqueId();
        for (Zone zone : zones) {
            if (zone == null) continue;
            if (zone.isListedForRent() || zone.isRentedBy(viewer) || zone.isRented()) {
                return true;
            }
        }
        return false;
    }

    public List<MarketStall> getStalls() {
        return stalls;
    }

    public void addStall(MarketStall stall) {
        if (stall == null) return;
        removeStallByChest(stall.getChestLocation());
        removeStallBySign(stall.getSignLocation());
        stalls.add(stall);
    }

    public void removeStall(MarketStall stall) {
        if (stall == null) return;
        stalls.remove(stall);
    }

    public boolean removeStallByChest(@Nullable Location location) {
        if (location == null) return false;
        return stalls.removeIf(stall -> stall != null && stall.matchesChest(location));
    }

    public boolean removeStallBySign(@Nullable Location location) {
        if (location == null) return false;
        return stalls.removeIf(stall -> stall != null && stall.matchesSign(location));
    }

    public MarketStall getStallByKey(@Nullable String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return null;
        for (MarketStall stall : stalls) {
            if (stall != null && storageKey.equalsIgnoreCase(stall.getStorageKey())) {
                return stall;
            }
        }
        return null;
    }

    public MarketStall getStallAtChest(@Nullable Location location) {
        if (location == null) return null;
        for (MarketStall stall : stalls) {
            if (stall != null && stall.matchesChest(location)) {
                return stall;
            }
        }
        return null;
    }

    public MarketStall getStallAtSign(@Nullable Location location) {
        if (location == null) return null;
        for (MarketStall stall : stalls) {
            if (stall != null && stall.matchesSign(location)) {
                return stall;
            }
        }
        return null;
    }

    public boolean hasBrowsableStalls() {
        for (MarketStall stall : stalls) {
            if (stall != null && stall.isActive(this)) {
                return true;
            }
        }
        return false;
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

    public long getLastUpkeep() {
        return getLastUpkeepPayment();
    }

    public void setLastUpkeep(long lastUpkeepPayment) {
        setLastUpkeepPayment(lastUpkeepPayment);
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

    public void toggleLike(UUID uuid) {
        if (uuid == null) return;
        if (hasLiked(uuid)) removeLike(uuid);
        else addLike(uuid);
    }

    public void setServerWarp(boolean serverWarp, String warpName, Material warpIcon) {
        this.serverWarp = serverWarp;
        this.warpName = warpName;
        this.warpIcon = warpIcon;
    }

    public String getWarpName() {
        return warpName;
    }

    public Material getWarpIcon() {
        return warpIcon;
    }

    public String serializeFlags() {
        if (flags.isEmpty()) return "";
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                entries.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return String.join(";", entries);
    }

    public String serializeRoles() {
        if (playerRoles.isEmpty()) return "";
        List<String> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerRoles.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                entries.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return String.join(";", entries);
    }

    public void deserializeFlags(String serialized) {
        flags.clear();
        if (serialized == null || serialized.isBlank()) return;
        for (String entry : serialized.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                flags.put(parts[0].toLowerCase(Locale.ROOT), Boolean.parseBoolean(parts[1]));
            }
        }
    }

    public void deserializeRoles(String serialized) {
        playerRoles.clear();
        if (serialized == null || serialized.isBlank()) return;
        for (String entry : serialized.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            try {
                setRole(UUID.fromString(parts[0]), parts[1]);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public String serializeRoleFlags() {
        if (roleFlagStates.isEmpty()) return "";

        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, TriState>> roleEntry : roleFlagStates.entrySet()) {
            String role = roleEntry.getKey();
            Map<String, TriState> flagsForRole = roleEntry.getValue();
            if (role == null || flagsForRole == null || flagsForRole.isEmpty()) continue;

            for (Map.Entry<String, TriState> flagEntry : flagsForRole.entrySet()) {
                String flag = flagEntry.getKey();
                TriState state = flagEntry.getValue();
                if (flag == null || state == null || state == TriState.INHERIT) continue;
                entries.add(role + "|" + flag + "|" + state.name());
            }
        }
        return String.join(";", entries);
    }

    public void deserializeRoleFlags(String serialized) {
        roleFlagStates.clear();
        if (serialized == null || serialized.isBlank()) return;

        for (String entry : serialized.split(";")) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split("\\|", 3);
            if (parts.length != 3) continue;

            String role = parts[0].trim().toLowerCase(Locale.ROOT);
            String flag = parts[1].trim().toLowerCase(Locale.ROOT);
            if (role.isEmpty() || flag.isEmpty()) continue;

            try {
                TriState state = TriState.valueOf(parts[2].trim().toUpperCase(Locale.ROOT));
                if (state == TriState.INHERIT) continue;
                roleFlagStates.computeIfAbsent(role, k -> new ConcurrentHashMap<>()).put(flag, state);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
