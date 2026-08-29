package com.aegisguard.data;

import com.aegisguard.AegisGuard;
import com.aegisguard.flags.TriState;
import com.aegisguard.alliance.Alliance;
import com.aegisguard.alliance.AllianceAccess;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.guestpass.GuestPassMode;
import com.aegisguard.guestpass.GuestPassPreset;
import com.aegisguard.profile.PlotNotice;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
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

    /**
     * How this plot's public listings (visit / market / auction) let visitors arrive.
     * CLASSIC is the 1.3.0-style Safe Travel to the plot spawn; BEACON requires a public
     * arrival pad and fails closed when none is available.
     */
    public enum ArrivalMode {
        CLASSIC,
        BEACON;

        public static ArrivalMode parse(String raw) {
            if (raw == null || raw.isBlank()) return CLASSIC;
            try {
                return ArrivalMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CLASSIC;
            }
        }
    }

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
    // Plot-local display labels only; permission tokens still come from playerRoles / config roles.
    private final Map<UUID, String> roleNicknames = new ConcurrentHashMap<>();
    private final List<UUID> bannedPlayers = new CopyOnWriteArrayList<>();

    // Role flag overrides (role -> flag -> TriState)
    private final Map<String, Map<String, TriState>> roleFlagStates = new ConcurrentHashMap<>();

    // --- GUEST PASSES (Milestone 2) ---
    // Additive, time-limited access. Kept entirely separate from playerRoles so a pass never
    // grants management rights and never overwrites/removes permanent trust on expiry or revoke.
    private final Map<UUID, GuestPass> guestPasses = new ConcurrentHashMap<>();

    // --- EMERGENCY LOCKDOWN (Milestone 3) ---
    // Disabled by default. While active, hasElevatedManagementAccess/owner keep full access, but
    // every other player loses the configured "restricted" build/interact actions - even if their
    // permanent role or an active Guest Pass would otherwise allow them. Ownership, roles, and
    // Guest Passes themselves are never modified by a lockdown; it is purely a temporary gate.
    private volatile boolean lockdownActive = false;
    private volatile long lockdownActivatedAt = 0L;
    private volatile long lockdownExpiresAt = 0L; // 0 = until manually lifted
    private volatile String lockdownMode = "FULL"; // FULL or SOFT
    private volatile UUID lockdownActivatedBy;
    private volatile String lockdownActivatedByName = "Unknown";

    // --- TRAVEL ATLAS ARRIVAL MODE (1.4.0) ---
    // Owner choice for how public listings (visit / market / auction) let visitors land:
    //   CLASSIC - Safe Travel to plot spawn / listing point (1.3.0 style)
    //   BEACON  - visitors must land on a public arrival pad; fails closed if none exists
    // Existing plots default to CLASSIC so enabling 1.4 never suddenly gates a server on pads.
    private volatile ArrivalMode arrivalMode = ArrivalMode.CLASSIC;

    // --- REALM PROFILE NOTICEBOARD (Milestone 4) ---
    // Short, owner-moderated public notices (rules, event details, shop info, announcements).
    // Purely presentational - never affects permissions, ownership, or protection behavior.
    private final List<PlotNotice> noticeboard = new CopyOnWriteArrayList<>();

    // --- ALLIANCE ACCESS (Milestone 7) ---
    // Optional join to a player alliance plus opt-in toggles (all risky defaults OFF).
    // Alliance membership alone never grants manage/ownership/money/rental rights.
    private volatile UUID allianceId;
    private final AllianceAccess allianceAccess = new AllianceAccess();

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

    // --- HORIZON ASCENSION (endgame progression after Plot Level 30) ---
    private int horizonRank;
    private int horizonExpansionRank;
    private long horizonRenown;
    private String horizonClimate = "NATURAL";
    private String ascensionFocus = "UNCHOSEN";
    private long ascensionFocusChangedAt;

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
    /** Optional travel category: SPAWN, HUB, TOWN, EVENT, SHOP (null = uncategorized). */
    private String warpCategory;

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
    public Location getCenter(Plugin plugin) {
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
            roleNicknames.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase(Locale.ROOT));
            bannedPlayers.remove(playerUUID);
        }
    }

    public void removeRole(UUID playerUUID) {
        if (playerUUID == null) return;
        if (isOwner(playerUUID) || SERVER_OWNER_UUID.equals(playerUUID)) return;
        playerRoles.remove(playerUUID);
        roleNicknames.remove(playerUUID);
    }

    public Map<UUID, String> getRoleNicknames() {
        return roleNicknames;
    }

    public String getRoleNickname(UUID playerUUID) {
        if (playerUUID == null) return null;
        String nick = roleNicknames.get(playerUUID);
        return (nick == null || nick.isBlank()) ? null : nick;
    }

    public void setRoleNickname(UUID playerUUID, String nickname) {
        if (playerUUID == null) return;
        if (isOwner(playerUUID) || SERVER_OWNER_UUID.equals(playerUUID)) return;
        if (nickname == null || nickname.isBlank()) {
            roleNicknames.remove(playerUUID);
            return;
        }
        String cleaned = nickname.replaceAll("[\\u0000-\\u001F\\u007F§]", "").trim();
        if (cleaned.length() > 24) cleaned = cleaned.substring(0, 24);
        if (cleaned.isBlank()) {
            roleNicknames.remove(playerUUID);
            return;
        }
        roleNicknames.put(playerUUID, cleaned);
    }

    public void clearRoleNickname(UUID playerUUID) {
        if (playerUUID == null) return;
        roleNicknames.remove(playerUUID);
    }

    /** Count members that consume territory capacity (non-visitor assigned roles). */
    public int countTrustedMembers() {
        int count = 0;
        for (Map.Entry<UUID, String> entry : playerRoles.entrySet()) {
            if (entry.getKey() == null) continue;
            if (isOwner(entry.getKey()) || SERVER_OWNER_UUID.equals(entry.getKey())) continue;
            String role = entry.getValue();
            if (role == null || role.isBlank()) continue;
            if (role.equalsIgnoreCase("visitor") || role.equalsIgnoreCase("default") || role.equalsIgnoreCase("none")) {
                continue;
            }
            count++;
        }
        return count;
    }

    public boolean isAtMemberCapacity() {
        return countTrustedMembers() >= getMaxMembers();
    }

    public String serializeRoleNicknames() {
        if (roleNicknames.isEmpty()) return "";
        List<String> entries = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : roleNicknames.entrySet()) {
            if (entry.getKey() == null) continue;
            String label = entry.getValue();
            if (label == null || label.isBlank()) continue;
            String encoded = Base64.getEncoder().encodeToString(label.getBytes(StandardCharsets.UTF_8));
            entries.add(entry.getKey() + "|" + encoded);
        }
        return String.join("~", entries);
    }

    public void deserializeRoleNicknames(String serialized) {
        roleNicknames.clear();
        if (serialized == null || serialized.isBlank()) return;
        for (String entry : serialized.split("~")) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) continue;
            try {
                UUID id = UUID.fromString(parts[0]);
                String label = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                setRoleNickname(id, label);
            } catch (Exception ignored) {}
        }
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
        guestPasses.remove(playerUUID);
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
        if (isRentedBy(uuid)) return true;
        if (getActiveGuestPass(uuid) != null) return true;
        String role = getRole(uuid);
        return role != null && !role.equalsIgnoreCase("visitor");
    }

    public boolean hasPermission(UUID playerUUID, String permission, Plugin plugin) {
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (playerUUID == null || permission == null || pl == null) return false;

        // Owner always allowed
        if (owner.equals(playerUUID)) return true;

        // Banned never allowed
        if (isBanned(playerUUID)) return false;

        // Admin/bypass always allowed
        Player online = Bukkit.getPlayer(playerUUID);
        if (hasElevatedManagementAccess(online, pl)) {
            return true;
        }

        // A full-plot renter receives only the explicitly configured tenant permissions.
        // Management permissions are never implied by renting a plot.
        if (isRentedBy(playerUUID)) {
            String needle = permission.toUpperCase(Locale.ROOT);
            List<String> renterPerms = pl.cfg().raw().getStringList("full_plot_renting.renter_permissions");
            if (renterPerms == null || renterPerms.isEmpty()) {
                renterPerms = List.of("BUILD", "BLOCK_BREAK", "BLOCK_PLACE", "INTERACT",
                        "CONTAINERS", "REDSTONE", "ANIMALS", "VEHICLES");
            }
            for (String renterPerm : renterPerms) {
                if (renterPerm == null) continue;
                String normalized = renterPerm.toUpperCase(Locale.ROOT);
                if ("ALL".equals(normalized) || normalized.equals(needle)
                        || ("BUILD".equals(normalized)
                        && ("BLOCK_BREAK".equals(needle) || "BLOCK_PLACE".equals(needle)))) {
                    return true;
                }
            }
        }

        // Temporary Guest Passes (Milestone 2) grant additive, time-limited access on top of
        // whatever permanent role (if any) the player already has. A pass never overwrites or
        // removes permanent trust: if its tokens don't cover this permission, we simply fall
        // through to the normal role-based check below.
        GuestPass guestPass = getActiveGuestPass(playerUUID);
        if (guestPass != null && guestPass.hasPermission(permission)) {
            return true;
        }

        // Alliance Access (Milestone 7): only the opted-in toggles on THIS plot grant tokens,
        // and never MANAGE / MANAGE_MEMBERS.
        if (grantsAlliancePermission(playerUUID, permission, pl)) {
            return true;
        }

        String role = getRole(playerUUID);
        if (role == null) role = "visitor";

        // Per-role flag editor overrides (ROLE_FLAG_KEYS) beat catalog permissions.
        TriState roleFlag = resolvePermissionRoleFlag(role, permission);
        if (roleFlag == TriState.ALLOW) return true;
        if (roleFlag == TriState.DENY) return false;

        // Role permissions from config: roles.<role>.permissions
        List<String> perms = pl.cfg().raw().getStringList("roles." + role.toLowerCase(Locale.ROOT) + ".permissions");
        if (perms == null || perms.isEmpty()) return false;

        String needle = permission.toUpperCase(Locale.ROOT);
        for (String p : perms) {
            if (p == null) continue;
            String up = p.toUpperCase(Locale.ROOT);
            if ("ALL".equals(up) || up.equals(needle)) return true;
        }
        return false;
    }

    /**
     * Maps a permission token onto a ROLE_FLAG_KEYS override when one exists.
     * Returns INHERIT when the permission has no corresponding role-flag key.
     */
    public TriState resolvePermissionRoleFlag(String roleName, String permission) {
        if (roleName == null || permission == null) return TriState.INHERIT;
        String flagKey = permissionFlagKey(permission);
        if (flagKey == null) return TriState.INHERIT;
        return getRoleFlagState(roleName, flagKey);
    }

    /**
     * Explicit role-flag override for a protection flag key (entry, pvp, animals, ...).
     * null means inherit (no override); TRUE=allow; FALSE=deny.
     */
    public Boolean resolveRoleFlagOverride(UUID playerUUID, String flagKey) {
        if (playerUUID == null || flagKey == null) return null;
        if (isOwner(playerUUID)) return Boolean.TRUE;
        if (isBanned(playerUUID)) return Boolean.FALSE;
        String role = getRole(playerUUID);
        if (role == null) role = "visitor";
        TriState state = getRoleFlagState(role, flagKey);
        if (state == TriState.ALLOW) return Boolean.TRUE;
        if (state == TriState.DENY) return Boolean.FALSE;
        return null;
    }

    private static String permissionFlagKey(String permission) {
        if (permission == null || permission.isBlank()) return null;
        String needle = permission.toUpperCase(Locale.ROOT);
        return switch (needle) {
            case "BUILD", "BLOCK_BREAK", "BLOCK_PLACE" -> "build";
            case "CONTAINERS" -> "containers";
            case "ANIMALS" -> "animals";
            case "VEHICLES" -> "vehicles";
            case "FARM" -> "farm";
            case "REDSTONE" -> "redstone";
            case "DOORS" -> "doors";
            case "DECOR" -> "decor";
            case "INTERACT", "ENTRY" -> "entry";
            case "PVP" -> "pvp";
            case "MOBS" -> "mobs";
            case "PETS" -> "pets";
            case "ENTITIES" -> "entities";
            case "TNT" -> "tnt";
            case "FIRE" -> "fire";
            case "PISTON" -> "piston";
            case "SHOP" -> "shop";
            case "FLY" -> "fly";
            default -> null;
        };
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
    public boolean canManage(@Nullable Player player, @Nullable Plugin plugin) {
        if (player == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (isRestoreMaintenanceLocked(pl)) return false;

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
    public boolean canModifyMember(@Nullable Player editor, @Nullable UUID targetUUID, @Nullable Plugin plugin) {
        if (editor == null || targetUUID == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (isRestoreMaintenanceLocked(pl)) return false;

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
    public boolean canBuild(@Nullable Player player, @Nullable Plugin plugin, @Nullable String permission) {
        if (player == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (isRestoreMaintenanceLocked(pl)) return false;

        if (hasElevatedManagementAccess(player, pl)) return true;

        UUID uuid = player.getUniqueId();
        if (isOwner(uuid)) return true;
        if (isBanned(uuid)) return false;

        String perm = (permission == null || permission.isEmpty()) ? "BUILD" : permission.toUpperCase(Locale.ROOT);

        // Emergency Lockdown (Milestone 3): a hard, temporary override for everyone except the
        // owner and elevated staff (both already handled above). It beats role-flag overrides and
        // the "public build" plot flag - the whole point is a fast, reversible safety response -
        // but never restricts plain INTERACT, so leaving through a door is always possible.
        if (refreshLockdownExpiry() && isPermissionRestrictedByLockdown(perm, pl)) return false;

        String role = getRole(uuid);
        TriState override = getRoleFlagState(role, "build");
        if (override == TriState.INHERIT) {
            override = resolvePermissionRoleFlag(role, perm);
        }
        if (override == TriState.ALLOW) return true;
        if (override == TriState.DENY) return false;

        // "build" acts as a public-build override.
        // By default claims are protected, so only trusted roles can build unless a plot explicitly opens building up.
        if (!getFlag("build", true)) return true;

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

    private boolean canUseRentedZone(@Nullable Player player, @Nullable Location location, @Nullable Plugin plugin, @Nullable String permission) {
        if (player == null || location == null) return false;
        Zone zone = getRentedZoneAt(location);
        if (zone == null) return false;

        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
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

    public boolean canBuildAt(@Nullable Player player, @Nullable Location location, @Nullable Plugin plugin, @Nullable String permission) {
        if (player == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (isRestoreMaintenanceLocked(pl)) return false;
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

    public boolean canInteractAt(@Nullable Player player, @Nullable Location location, @Nullable Plugin plugin, @Nullable String permission) {
        if (player == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (isRestoreMaintenanceLocked(pl)) return false;

        Zone rentedZone = getRentedZoneAt(location);
        if (rentedZone != null) {
            return canUseRentedZone(player, location, pl, permission);
        }

        if (canBuildAt(player, location, pl, permission)) return true;

        UUID uuid = player.getUniqueId();
        String needle = (permission == null || permission.isBlank()) ? "INTERACT" : permission.toUpperCase(Locale.ROOT);

        // Lockdown is enforced in canBuild for owners/staff already short-circuiting above.
        // Without this gate, Guest Pass / role tokens (e.g. CONTAINERS) would still unlock
        // sensitive interact paths after canBuild denied them during an active lockdown.
        if (isPermissionRestrictedByLockdown(needle, pl)) {
            return false;
        }

        // A plain interaction (doors, buttons, levers - no specific gated action requested) only
        // needs the broad INTERACT token. Gated actions (CONTAINERS, FARM, VEHICLES, ...) must hold
        // that exact token themselves; holding INTERACT alone must never unlock them. Bug fix
        // (1.3.0): previously any INTERACT holder bypassed every gated check below, silently
        // granting container/farm/vehicle access to roles and Guest Passes that only had INTERACT.
        // Role-flag editor overrides for gated actions (containers/farm/vehicles/...).
        Boolean flagOverride = resolveRoleFlagOverride(uuid, permissionFlagKey(needle) == null
                ? needle.toLowerCase(Locale.ROOT)
                : permissionFlagKey(needle));
        if (flagOverride != null) {
            return flagOverride;
        }

        if ("INTERACT".equals(needle)) {
            if (hasPermission(uuid, "INTERACT", pl)) return true;
        } else if (hasPermission(uuid, needle, pl)) {
            return true;
        }

        return isZoneRenter(uuid, location);
    }

    private boolean isRestoreMaintenanceLocked(@Nullable AegisGuard plugin) {
        return plugin != null && plugin.getSnapshotManager() != null
                && plugin.getSnapshotManager().isRestoreLocked(plotId);
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

    private boolean hasElevatedManagementAccess(@Nullable Player player, @Nullable Plugin plugin) {
        AegisGuard aegis = (plugin instanceof AegisGuard ag) ? ag : AegisGuard.getInstance();
        if (player == null || aegis == null) return false;
        // Explicit emergency bypass only — not blanket isAdmin.
        if (aegis.isBypassing(player) || player.hasPermission("aegis.bypass")) return true;

        // Server zones: only configured server-zone manage perms (or trusted OP), not any admin.
        // Plot-role Steward still reaches canManage via MANAGE / MANAGE_MEMBERS below.
        if (isServerZone()) {
            if (hasAnyPermission(player, aegis, "staff_access.server_zone_manage_permissions",
                    List.of("aegis.serverzone.manage", "aegis.staff.co_owner"))) {
                return true;
            }
            try {
                if (aegis.getConfig().getBoolean("admin.trust_operators", true) && player.isOp()) {
                    return true;
                }
            } catch (Throwable ignored) {}
            return false;
        }

        if (aegis.isAdmin(player)) return true;
        if (hasAnyPermission(player, aegis, "staff_access.global_manage_permissions", List.of("aegis.admin.manage"))) {
            return true;
        }
        return isMarketManaged() && hasAnyPermission(player, aegis, "staff_access.market_plot_manage_permissions",
                List.of("aegis.market.manage", "aegis.staff.market_steward"));
    }

    private boolean isMarketManaged() {
        return isForSale() || isForRent() || isForAuction() || isServerWarp();
    }

    private boolean hasAnyPermission(@Nullable Player player, @Nullable Plugin plugin, String path, List<String> fallback) {
        AegisGuard aegis = (plugin instanceof AegisGuard ag) ? ag : AegisGuard.getInstance();
        if (player == null || aegis == null) return false;

        List<String> permissions = fallback;
        try {
            List<String> configured = aegis.getConfig().getStringList(path);
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

    public boolean hasActiveRental() {
        return currentRenter != null && rentEndTime > System.currentTimeMillis();
    }

    public boolean isRentedBy(@Nullable UUID playerUUID) {
        return playerUUID != null && playerUUID.equals(currentRenter) && hasActiveRental();
    }

    public void clearRenter() {
        currentRenter = null;
        rentEndTime = 0L;
    }

    public void clearPlayerAccess() {
        playerRoles.clear();
        bannedPlayers.clear();
        roleFlagStates.clear();
        guestPasses.clear();
    }

    // ---------------------------------------------------------------------
    // Guest Passes (Milestone 2 - Temporary Guest Passes)
    // ---------------------------------------------------------------------

    /** Live view of every stored pass (expired or not). Prefer {@link #getActiveGuestPasses()} for display. */
    public Map<UUID, GuestPass> getGuestPasses() {
        return guestPasses;
    }

    public GuestPass getGuestPass(UUID playerUUID) {
        return playerUUID == null ? null : guestPasses.get(playerUUID);
    }

    /** Returns the pass only if it exists and has not expired as of {@code now}. */
    public GuestPass getActiveGuestPass(UUID playerUUID, long now) {
        GuestPass pass = getGuestPass(playerUUID);
        if (pass == null || pass.isExpired(now)) return null;
        return pass;
    }

    public GuestPass getActiveGuestPass(UUID playerUUID) {
        return getActiveGuestPass(playerUUID, System.currentTimeMillis());
    }

    /** Issuing a new pass for a player replaces any previous pass they held on this plot. */
    public void addGuestPass(GuestPass pass) {
        if (pass == null) return;
        guestPasses.put(pass.getPlayerId(), pass);
    }

    public boolean revokeGuestPass(UUID playerUUID) {
        if (playerUUID == null) return false;
        return guestPasses.remove(playerUUID) != null;
    }

    /** Every currently active (non-expired) pass, for GUI listing. */
    public List<GuestPass> getActiveGuestPasses() {
        long now = System.currentTimeMillis();
        List<GuestPass> active = new ArrayList<>();
        for (GuestPass pass : guestPasses.values()) {
            if (pass != null && !pass.isExpired(now)) active.add(pass);
        }
        return active;
    }

    /**
     * Removes every expired pass and returns the ones that were removed, so a caller (the expiry
     * sweep task) can notify players and write audit entries. Never touches {@code playerRoles}.
     */
    public List<GuestPass> pruneExpiredGuestPasses(long now) {
        List<GuestPass> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, GuestPass>> it = guestPasses.entrySet().iterator();
        while (it.hasNext()) {
            GuestPass pass = it.next().getValue();
            if (pass == null || pass.isExpired(now)) {
                if (pass != null) expired.add(pass);
                it.remove();
            }
        }
        return expired;
    }

    /**
     * Entries are joined with {@code ~} (not {@code ;}) because this blob is itself embedded as
     * one {@code key=value} pair inside a {@code ;}-delimited settings blob by
     * {@code SQLDataStore.serializeSettings}; using {@code ;} here would corrupt that outer split
     * as soon as a plot had more than one active pass.
     */
    public String serializeGuestPasses() {
        return serializeGuestPasses(false);
    }

    /**
     * Snapshot capture: freeze playtime remaining as-of-now without mutating live session
     * counters, so rollback does not refund time already consumed in the current session.
     */
    public String serializeGuestPassesForSnapshot() {
        return serializeGuestPasses(true);
    }

    private String serializeGuestPasses(boolean freezePlaytimeForSnapshot) {
        if (guestPasses.isEmpty()) return "";
        long now = System.currentTimeMillis();
        List<String> entries = new ArrayList<>();
        for (GuestPass pass : guestPasses.values()) {
            if (pass == null) continue;
            String perms = String.join(",", pass.getPermissions());
            String issuer = pass.getIssuerId() == null ? "" : pass.getIssuerId().toString();
            long remaining = pass.getStoredRemainingMillis();
            long sessionStartedAt = pass.getSessionStartedAt();
            if (freezePlaytimeForSnapshot && pass.isActivePlaytime()) {
                remaining = pass.getStoredRemainingMillis() < 0L ? -1L : pass.getRemainingMillis(now);
                sessionStartedAt = 0L;
            }
            // Legacy fields (8) remain first for readability; mode/remaining/session append for
            // active-playtime support. Missing trailing fields deserialize as REAL_TIME.
            entries.add(String.join("|",
                    pass.getPlayerId().toString(),
                    pass.getPlayerName(),
                    pass.getPreset().name(),
                    perms,
                    issuer,
                    pass.getIssuerName(),
                    String.valueOf(pass.getIssuedAt()),
                    String.valueOf(pass.getExpiresAt()),
                    pass.getMode().name(),
                    String.valueOf(remaining),
                    String.valueOf(sessionStartedAt)
            ));
        }
        return String.join("~", entries);
    }

    public void deserializeGuestPasses(String serialized) {
        guestPasses.clear();
        if (serialized == null || serialized.isBlank()) return;

        for (String entry : serialized.split("~")) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 8) continue;

            try {
                UUID playerId = UUID.fromString(parts[0]);
                String playerName = parts[1];
                GuestPassPreset preset = GuestPassPreset.valueOf(parts[2].toUpperCase(Locale.ROOT));
                Set<String> perms = parts[3].isBlank()
                        ? Set.of()
                        : new HashSet<>(Arrays.asList(parts[3].split(",")));
                UUID issuerId = parts[4].isBlank() ? null : UUID.fromString(parts[4]);
                String issuerName = parts[5];
                long issuedAt = Long.parseLong(parts[6]);
                long expiresAt = Long.parseLong(parts[7]);
                GuestPassMode mode = parts.length >= 9
                        ? GuestPassMode.fromSerialized(parts[8])
                        : GuestPassMode.REAL_TIME;
                long remainingMillis = parts.length >= 10 ? Long.parseLong(parts[9]) : 0L;
                long sessionStartedAt = parts.length >= 11 ? Long.parseLong(parts[10]) : 0L;

                GuestPass pass = new GuestPass(playerId, playerName, preset, perms,
                        issuerId, issuerName, issuedAt, expiresAt, mode, remainingMillis, sessionStartedAt);
                // Never count server-down / crash gaps as active playtime.
                pass.pauseAfterLoad();
                guestPasses.put(playerId, pass);
            } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------------------
    // Travel Atlas arrival mode (1.4.0)
    // ---------------------------------------------------------------------

    /** Never null; defaults to CLASSIC for plots that predate the 1.4 arrival choice. */
    public ArrivalMode getArrivalMode() {
        return arrivalMode == null ? ArrivalMode.CLASSIC : arrivalMode;
    }

    public void setArrivalMode(ArrivalMode mode) {
        this.arrivalMode = mode == null ? ArrivalMode.CLASSIC : mode;
    }

    public boolean requiresBeaconArrival() {
        return getArrivalMode() == ArrivalMode.BEACON;
    }

    // ---------------------------------------------------------------------
    // Emergency Lockdown (Milestone 3)
    // ---------------------------------------------------------------------

    public boolean isLockdownActive() {
        refreshLockdownExpiry();
        return lockdownActive;
    }

    /** Raw flag for persistence/snapshots. Does not auto-lift expired timed lockdowns. */
    public boolean isLockdownFlagSet() {
        return lockdownActive;
    }

    public long getLockdownActivatedAt() {
        return lockdownActivatedAt;
    }

    public long getLockdownExpiresAt() {
        return lockdownExpiresAt;
    }

    public String getLockdownMode() {
        return lockdownMode == null || lockdownMode.isBlank() ? "FULL" : lockdownMode;
    }

    public boolean isSoftLockdown() {
        return "SOFT".equalsIgnoreCase(getLockdownMode());
    }

    public @Nullable UUID getLockdownActivatedBy() {
        return lockdownActivatedBy;
    }

    public String getLockdownActivatedByName() {
        return lockdownActivatedByName;
    }

    /**
     * Flips the lockdown switch. Never touches {@code owner}, {@code playerRoles}, or
     * {@code guestPasses} - it is a purely temporary access gate, fully reversible by calling this
     * again with {@code active=false}.
     */
    public void setLockdown(boolean active, @Nullable UUID actorId, @Nullable String actorName) {
        setLockdown(active, actorId, actorName, 0L, "FULL");
    }

    public void setLockdown(boolean active, @Nullable UUID actorId, @Nullable String actorName,
                            long expiresAt, @Nullable String mode) {
        this.lockdownActive = active;
        if (active) {
            this.lockdownActivatedAt = System.currentTimeMillis();
            this.lockdownExpiresAt = Math.max(0L, expiresAt);
            this.lockdownMode = (mode == null || mode.isBlank()) ? "FULL" : mode.trim().toUpperCase(Locale.ROOT);
            this.lockdownActivatedBy = actorId;
            this.lockdownActivatedByName = (actorName == null || actorName.isBlank()) ? "Unknown" : actorName;
        } else {
            this.lockdownActivatedAt = 0L;
            this.lockdownExpiresAt = 0L;
            this.lockdownMode = "FULL";
            this.lockdownActivatedBy = null;
            this.lockdownActivatedByName = "Unknown";
        }
    }

    /**
     * Restores a persisted lockdown state with its original activation timestamp, so "active for"
     * displays survive a server restart instead of resetting to "just now". Data-store loaders only.
     */
    public void restoreLockdown(boolean active, @Nullable UUID actorId, @Nullable String actorName, long activatedAt) {
        restoreLockdown(active, actorId, actorName, activatedAt, 0L, "FULL");
    }

    public void restoreLockdown(boolean active, @Nullable UUID actorId, @Nullable String actorName,
                                long activatedAt, long expiresAt, @Nullable String mode) {
        this.lockdownActive = active;
        this.lockdownActivatedAt = active ? activatedAt : 0L;
        this.lockdownExpiresAt = active ? Math.max(0L, expiresAt) : 0L;
        this.lockdownMode = active
                ? ((mode == null || mode.isBlank()) ? "FULL" : mode.trim().toUpperCase(Locale.ROOT))
                : "FULL";
        this.lockdownActivatedBy = active ? actorId : null;
        this.lockdownActivatedByName = active
                ? ((actorName == null || actorName.isBlank()) ? "Unknown" : actorName)
                : "Unknown";
    }

    /** Auto-lifts expired timed lockdowns. Returns whether lockdown is still active afterward. */
    public boolean refreshLockdownExpiry() {
        if (!lockdownActive) return false;
        if (lockdownExpiresAt > 0L && System.currentTimeMillis() >= lockdownExpiresAt) {
            setLockdown(false, null, null);
            return false;
        }
        return true;
    }

    /**
     * Whether {@code permission} is one of the configured "sensitive" tokens that Emergency
     * Lockdown restricts. {@code INTERACT} is a hard-coded exception and is never restrictable,
     * regardless of config - lockdown must never trap a player behind a door they could otherwise
     * open, only gate build/break/container style actions. Movement itself is never touched by
     * AegisGuard, so leaving is always possible.
     */
    public static boolean isLockdownRestrictable(@Nullable String permission, @Nullable AegisGuard pl) {
        if (permission == null || permission.isBlank()) return false;
        String needle = permission.trim().toUpperCase(Locale.ROOT);
        if ("INTERACT".equals(needle)) return false;

        List<String> configured = pl == null ? null
                : pl.getConfig().getStringList("lockdown.restricted_permissions");
        if (configured == null || configured.isEmpty()) {
            configured = List.of("BUILD", "BLOCK_BREAK", "BLOCK_PLACE", "CONTAINERS", "REDSTONE",
                    "ANIMALS", "VEHICLES", "FARM", "SHOP");
        }
        for (String token : configured) {
            if (token != null && needle.equals(token.trim().toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** Soft lockdown only gates build/break/containers; full uses the configured list. */
    public boolean isPermissionRestrictedByLockdown(@Nullable String permission, @Nullable AegisGuard pl) {
        if (!refreshLockdownExpiry()) return false;
        if (permission == null || permission.isBlank()) return false;
        String needle = permission.trim().toUpperCase(Locale.ROOT);
        if ("INTERACT".equals(needle)) return false;
        if (isSoftLockdown()) {
            return "BUILD".equals(needle) || "BLOCK_BREAK".equals(needle)
                    || "BLOCK_PLACE".equals(needle) || "CONTAINERS".equals(needle);
        }
        return isLockdownRestrictable(permission, pl);
    }

    // ---------------------------------------------------------------------
    // Alliance Access (Milestone 7)
    // ---------------------------------------------------------------------

    public UUID getAllianceId() {
        return allianceId;
    }

    public void setAllianceId(UUID allianceId) {
        this.allianceId = allianceId;
    }

    public AllianceAccess getAllianceAccess() {
        return allianceAccess;
    }

    public void clearAllianceAccess() {
        this.allianceId = null;
        this.allianceAccess.clear();
    }

    /**
     * Whether this plot's Alliance Entry toggle is opted in.
     * Membership alone is never enough — the toggle must be ON.
     */
    public boolean isAllianceEntryEnabled() {
        return allianceId != null && allianceAccess.isEnter();
    }

    /**
     * Whether this plot's Alliance Friendly PvP toggle is opted in.
     * Membership alone is never enough — the toggle must be ON.
     */
    public boolean isAllianceFriendlyPvpEnabled() {
        return allianceId != null && allianceAccess.isFriendlyPvp();
    }

    /**
     * Pure membership-aware entry grant used by plot-entry protection.
     * Defaults to denied: requires a joined alliance, Enter toggle ON, and membership.
     */
    public boolean allowsAllianceEntry(UUID playerUUID, Alliance alliance) {
        if (!isAllianceEntryEnabled() || playerUUID == null || alliance == null) return false;
        if (!alliance.getId().equals(allianceId)) return false;
        return alliance.isMember(playerUUID);
    }

    public boolean allowsAllianceEntry(UUID playerUUID, Plugin plugin) {
        if (!isAllianceEntryEnabled() || playerUUID == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (pl == null || pl.alliances() == null) return false;
        return allowsAllianceEntry(playerUUID, pl.alliances().get(allianceId));
    }

    /**
     * Pure membership-aware friendly-PvP grant used by plot-PvP damage protection.
     * Defaults to denied: requires a joined alliance, Friendly PvP toggle ON, and both players as members.
     */
    public boolean areAllianceAllies(UUID a, UUID b, Alliance alliance) {
        if (!isAllianceFriendlyPvpEnabled() || a == null || b == null || alliance == null) return false;
        if (!alliance.getId().equals(allianceId)) return false;
        return alliance.isMember(a) && alliance.isMember(b);
    }

    public boolean areAllianceAllies(UUID a, UUID b, Plugin plugin) {
        if (!isAllianceFriendlyPvpEnabled() || a == null || b == null) return false;
        AegisGuard pl = (plugin instanceof AegisGuard aegis) ? aegis : AegisGuard.getInstance();
        if (pl == null || pl.alliances() == null) return false;
        return areAllianceAllies(a, b, pl.alliances().get(allianceId));
    }

    private boolean grantsAlliancePermission(UUID playerUUID, String permission, AegisGuard pl) {
        if (allianceId == null || playerUUID == null || permission == null || pl == null) return false;
        String needle = permission.trim().toUpperCase(Locale.ROOT);
        if ("MANAGE".equals(needle) || "MANAGE_MEMBERS".equals(needle)) return false;
        if (!allianceAccess.grantsPermission(needle)) return false;
        return pl.allianceService() != null && pl.allianceService().isAllianceMember(this, playerUUID);
    }

    public String serializeAllianceAccess() {
        if (allianceId == null) return "";
        return allianceId + "|" + allianceAccess.serialize();
    }

    public void deserializeAllianceAccess(String serialized) {
        clearAllianceAccess();
        if (serialized == null || serialized.isBlank()) return;
        String[] parts = serialized.split("\\|", 2);
        try {
            allianceId = UUID.fromString(parts[0]);
            if (parts.length > 1) {
                AllianceAccess loaded = AllianceAccess.deserialize(parts[1]);
                allianceAccess.setEnter(loaded.isEnter());
                allianceAccess.setInteract(loaded.isInteract());
                allianceAccess.setContainers(loaded.isContainers());
                allianceAccess.setBuild(loaded.isBuild());
                allianceAccess.setAnimals(loaded.isAnimals());
                allianceAccess.setVehicles(loaded.isVehicles());
                allianceAccess.setFriendlyPvp(loaded.isFriendlyPvp());
            }
        } catch (Exception ignored) {
            clearAllianceAccess();
        }
    }

    // ---------------------------------------------------------------------
    // Realm Profile Noticeboard (Milestone 4 - Realm Profiles and Noticeboards)
    // ---------------------------------------------------------------------

    /** Chronological (oldest first) view of every posted notice. */
    public List<PlotNotice> getNoticeboard() {
        return List.copyOf(noticeboard);
    }

    public PlotNotice getNotice(UUID noticeId) {
        if (noticeId == null) return null;
        for (PlotNotice notice : noticeboard) {
            if (notice != null && noticeId.equals(notice.getId())) return notice;
        }
        return null;
    }

    /**
     * Posts a new notice, dropping the oldest entry first once the plot is already at
     * {@code maxEntries}. Moderation (explicit removal) is always a separate, deliberate call -
     * posting never silently edits an existing notice.
     */
    public void postNotice(PlotNotice notice, int maxEntries) {
        if (notice == null || maxEntries <= 0) return;
        while (noticeboard.size() >= maxEntries) {
            PlotNotice oldest = noticeboard.isEmpty() ? null : noticeboard.get(0);
            if (oldest == null || !noticeboard.remove(oldest)) break;
        }
        noticeboard.add(notice);
    }

    /** Removes a single notice by id. Returns {@code true} if a notice was actually removed. */
    public boolean removeNotice(UUID noticeId) {
        if (noticeId == null) return false;
        return noticeboard.removeIf(notice -> notice != null && noticeId.equals(notice.getId()));
    }

    public void clearNoticeboard() {
        noticeboard.clear();
    }

    /**
     * Entries are joined with {@code ~} for the same reason as
     * {@link #serializeGuestPasses()}. Each notice's free-form text is Base64-encoded so
     * owner-authored content can never break this delimiter scheme, even if it contains
     * {@code |}, {@code ~}, {@code ;}, or newlines.
     */
    public String serializeNoticeboard() {
        if (noticeboard.isEmpty()) return "";
        List<String> entries = new ArrayList<>();
        for (PlotNotice notice : noticeboard) {
            if (notice == null) continue;
            String authorId = notice.getAuthorId() == null ? "" : notice.getAuthorId().toString();
            String encodedText = Base64.getEncoder().encodeToString(notice.getText().getBytes(StandardCharsets.UTF_8));
            entries.add(String.join("|",
                    notice.getId().toString(),
                    authorId,
                    notice.getAuthorName(),
                    String.valueOf(notice.getCreatedAt()),
                    encodedText
            ));
        }
        return String.join("~", entries);
    }

    public void deserializeNoticeboard(String serialized) {
        noticeboard.clear();
        if (serialized == null || serialized.isBlank()) return;

        for (String entry : serialized.split("~")) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split("\\|", 5);
            if (parts.length != 5) continue;

            try {
                UUID id = UUID.fromString(parts[0]);
                UUID authorId = parts[1].isBlank() ? null : UUID.fromString(parts[1]);
                String authorName = parts[2];
                long createdAt = Long.parseLong(parts[3]);
                String text = new String(Base64.getDecoder().decode(parts[4]), StandardCharsets.UTF_8);
                noticeboard.add(new PlotNotice(id, authorId, authorName, createdAt, text));
            } catch (Exception ignored) {}
        }
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

    public int getHorizonRank() {
        return Math.max(0, Math.min(5, horizonRank));
    }

    public void setHorizonRank(int horizonRank) {
        this.horizonRank = Math.max(0, Math.min(5, horizonRank));
    }

    public int getHorizonExpansionRank() {
        return Math.max(0, Math.min(getHorizonRank(), horizonExpansionRank));
    }

    public void setHorizonExpansionRank(int horizonExpansionRank) {
        this.horizonExpansionRank = Math.max(0, Math.min(5, horizonExpansionRank));
    }

    public long getHorizonRenown() {
        return Math.max(0L, horizonRenown);
    }

    public void setHorizonRenown(long horizonRenown) {
        this.horizonRenown = Math.max(0L, horizonRenown);
    }

    public void addHorizonRenown(long amount) {
        if (amount <= 0L) return;
        horizonRenown = horizonRenown > Long.MAX_VALUE - amount ? Long.MAX_VALUE : horizonRenown + amount;
    }

    public String getHorizonClimate() {
        return horizonClimate == null || horizonClimate.isBlank() ? "NATURAL" : horizonClimate;
    }

    public void setHorizonClimate(String horizonClimate) {
        String normalized = horizonClimate == null ? "NATURAL" : horizonClimate.trim().toUpperCase(Locale.ROOT);
        this.horizonClimate = Set.of("NATURAL", "CLEAR", "RAIN", "SUNRISE", "SUNSET", "NIGHT")
                .contains(normalized) ? normalized : "NATURAL";
    }

    public String getAscensionFocus() {
        return ascensionFocus == null || ascensionFocus.isBlank() ? "UNCHOSEN" : ascensionFocus;
    }

    public void setAscensionFocus(String ascensionFocus) {
        this.ascensionFocus = ascensionFocus == null || ascensionFocus.isBlank()
                ? "UNCHOSEN" : ascensionFocus.trim().toUpperCase(Locale.ROOT);
    }

    public long getAscensionFocusChangedAt() {
        return Math.max(0L, ascensionFocusChangedAt);
    }

    public void setAscensionFocusChangedAt(long ascensionFocusChangedAt) {
        this.ascensionFocusChangedAt = Math.max(0L, ascensionFocusChangedAt);
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

    public String getWarpCategory() {
        return warpCategory;
    }

    public void setWarpCategory(String warpCategory) {
        if (warpCategory == null || warpCategory.isBlank()) {
            this.warpCategory = null;
            return;
        }
        this.warpCategory = warpCategory.trim().toUpperCase(java.util.Locale.ROOT);
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
