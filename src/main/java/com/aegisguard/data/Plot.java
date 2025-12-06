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
import java.util.stream.Collectors;

/**
 * Plot (Data Class) - v1.2.2+
 * - Represents a land claim.
 * - 2D (X/Z only) to ensure Bedrock-to-Sky protection.
 * - Server Zone identification.
 * - Thread-friendly collections for Folia / async-safe data access.
 *
 * NOTE:
 *  - This class itself is thread-friendly for reads/writes on its collections.
 *  - Any Bukkit / world calls (Bukkit.getWorld, getHighestBlockYAt, etc.)
 *    must be invoked from a valid Paper/Folia context (global or region task).
 */
public class Plot {

    // Special UUID for server-owned plots (Admin Zones/Spawn)
    public static final UUID SERVER_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // Default Flag State
    private static final Map<String, Boolean> DEFAULT_FLAGS = Map.ofEntries(
            Map.entry("pvp", false),
            Map.entry("containers", true),
            Map.entry("mobs", false), // false by default: safer claims
            Map.entry("pets", true),
            Map.entry("entities", true),
            Map.entry("farm", true),
            Map.entry("tnt-damage", false),
            Map.entry("fire-spread", false),
            Map.entry("piston-use", false),
            Map.entry("build", true),
            Map.entry("interact", true),
            Map.entry("fly", false),
            Map.entry("entry", true),
            Map.entry("safe_zone", false),
            Map.entry("hunger", true),
            Map.entry("sleep", true)
    );

    // --- Core Identity ---
    private final UUID plotId;
    private UUID owner;
    private String ownerName;
    private final String world;

    // 2D Coordinates (Defines the "Column" of protection)
    // We ONLY store X and Z. Y is irrelevant for infinite height.
    private int x1, z1, x2, z2;

    // --- Data Containers ---

    // Claim flags (per-plot)
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    // Player -> role name ("owner", "member", etc.)
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();

    // Per-role flag overrides: roleName -> (flagKey -> TriState)
    // Example: "member" -> { "pvp" -> ALLOW, "entry" -> DENY }
    private final Map<String, Map<String, TriState>> roleFlagStates = new ConcurrentHashMap<>();

    // Banned players
    private final Set<UUID> bannedPlayers = ConcurrentHashMap.newKeySet();

    // Sub-zones within this plot
    private final List<Zone> zones = new CopyOnWriteArrayList<>();

    // Likes
    private final Set<UUID> likedBy = ConcurrentHashMap.newKeySet();

    // --- Progression ---
    private int level = 1;
    private double xp = 0;
    private int maxMembers = 2;

    // --- Economy & Upkeep ---
    private long lastUpkeepPayment;
    private boolean isForSale;
    private double salePrice;
    private boolean isForRent;
    private double rentPrice;
    private UUID currentRenter;
    private long rentExpires;

    // --- Auction ---
    private String plotStatus = "ACTIVE";
    private double currentBid;
    private UUID currentBidder;

    // --- Visuals ---
    private Location spawnLocation;
    private String welcomeMessage;
    private String farewellMessage;
    private String entryTitle;
    private String entrySubtitle;
    private String description;
    private String customBiome;

    // --- Cosmetics ---
    private String borderParticle;
    private String ambientParticle;
    private String entryEffect;

    // --- Server Warps ---
    private boolean isServerWarp;
    private String warpName;
    private Material warpIcon;

    // --- CONSTRUCTORS ---

    public Plot(UUID plotId, UUID owner, String ownerName, String world,
                int x1, int z1, int x2, int z2, long lastUpkeepPayment) {
        this.plotId = plotId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;

        // Math.min/max ensures x1/z1 is always the "Lower Left" corner
        // This is critical for accurate 2D collision checks
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);

        this.lastUpkeepPayment = lastUpkeepPayment;

        if (!isServerZone()) {
            this.playerRoles.put(owner, "owner");
        }
        this.flags.putAll(DEFAULT_FLAGS);
    }

    public Plot(UUID plotId, UUID owner, String ownerName, String world,
                int x1, int z1, int x2, int z2) {
        this(plotId, owner, ownerName, world, x1, z1, x2, z2, System.currentTimeMillis());
    }

    // --- CORE LOGIC (SKY TO BEDROCK) ---

    public boolean isInside(Location loc) {
        return contains(loc);
    }

    /**
     * Checks if a location is inside the Plot.
     * IGNORES Y-LEVEL completely to provide Bedrock-to-Sky protection.
     *
     * NOTE (Folia/Paper):
     * This method assumes it is called from a valid region/global context.
     */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        // World check first
        if (!loc.getWorld().getName().equals(world)) return false;

        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        // Simple 2D Bounding Box Check (Y is ignored)
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    public boolean isServerZone() {
        return SERVER_OWNER_UUID.equals(owner);
    }

    /**
     * Get approximate center of the plot.
     * Caller must ensure this is run on a valid Bukkit thread
     * (global tick or region task) for this world's region.
     */
    public Location getCenter(@Nullable AegisGuard plugin) {
        World w = Bukkit.getWorld(this.world);
        if (w == null) return null;

        double cX = (x1 + x2) / 2.0 + 0.5;
        double cZ = (z1 + z2) / 2.0 + 0.5;

        // Calculate Y based on highest block so the center isn't in the void
        int y = w.getHighestBlockYAt((int) cX, (int) cZ) + 1;

        return new Location(w, cX, y, cZ);
    }

    // --- LEVELING / SIZE ---

    public void expand(int amount) {
        this.x1 -= amount;
        this.z1 -= amount;
        this.x2 += amount;
        this.z2 += amount;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int max) {
        this.maxMembers = max;
    }

    public boolean isOwner(Player player) {
        return player.getUniqueId().equals(owner);
    }

    public boolean isTrusted(Player player) {
        return playerRoles.containsKey(player.getUniqueId()) && !isBanned(player.getUniqueId());
    }

    // --- PERMISSIONS SYSTEM (ROLE PERMISSIONS FROM CONFIG) ---

    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        // Server Zones: Always deny unless admin bypass is checked externally
        if (isServerZone()) return false;

        if (owner.equals(playerUUID)) return true;
        if (isBanned(playerUUID)) return false;

        // Rent Logic
        if (currentRenter != null && currentRenter.equals(playerUUID)) {
            if (System.currentTimeMillis() < rentExpires) {
                // Safely fetch permissions set from config
                Set<String> perms = new HashSet<>();
                List<String> configPerms = plugin.cfg().raw().getStringList("roles.member.permissions"); // Fallback to member role for renters
                if (configPerms != null) perms.addAll(configPerms);

                return perms.contains(permission.toUpperCase()) || perms.contains("ALL");
            } else {
                this.currentRenter = null;
                this.rentExpires = 0;
            }
        }

        String role = getRole(playerUUID);
        Set<String> permissions = new HashSet<>();
        List<String> rolePerms = plugin.cfg().raw().getStringList("roles." + role + ".permissions");
        if (rolePerms != null) permissions.addAll(rolePerms);

        return permissions.contains(permission.toUpperCase()) || permissions.contains("ALL");
    }

    public String getRole(UUID playerUUID) {
        return playerRoles.getOrDefault(playerUUID, "visitor");
    }

    public void setRole(UUID playerUUID, String role) {
        if (role == null || role.equalsIgnoreCase("default") || role.equalsIgnoreCase("none")) {
            playerRoles.remove(playerUUID);
        } else {
            playerRoles.put(playerUUID, role.toLowerCase());
            bannedPlayers.remove(playerUUID);
        }
    }

    public Map<UUID, String> getPlayerRoles() {
        return playerRoles;
    }

    public void removeRole(UUID playerUUID) {
        playerRoles.remove(playerUUID);
    }

    // --- ROLE FLAG OVERRIDES (PER-PLOT) ---

    /**
     * Get the TriState override for a given role + flag key.
     *
     * @param roleName The configured role name (e.g. "member", "trusted")
     * @param flagKey  The plot flag key (e.g. "pvp", "containers")
     * @return TriState.INHERIT if no override exists.
     */
    public TriState getRoleFlagState(String roleName, String flagKey) {
        if (roleName == null || flagKey == null) {
            return TriState.INHERIT;
        }

        Map<String, TriState> flagsForRole = roleFlagStates.get(roleName.toLowerCase());
        if (flagsForRole == null) {
            return TriState.INHERIT;
        }

        return flagsForRole.getOrDefault(flagKey.toLowerCase(), TriState.INHERIT);
    }

    /**
     * Set or clear a per-role override for a given flag.
     *
     * - INHERIT = remove override, use normal plot/world/zone logic.
     * - ALLOW / DENY = explicit role-level override on this plot.
     */
    public void setRoleFlagState(String roleName, String flagKey, TriState state) {
        if (roleName == null || flagKey == null || state == null) {
            return;
        }

        String roleKey = roleName.toLowerCase();
        String flagKeyLower = flagKey.toLowerCase();

        if (state == TriState.INHERIT) {
            Map<String, TriState> flagsForRole = roleFlagStates.get(roleKey);
            if (flagsForRole != null) {
                flagsForRole.remove(flagKeyLower);
                if (flagsForRole.isEmpty()) {
                    roleFlagStates.remove(roleKey);
                }
            }
            return;
        }

        roleFlagStates
                .computeIfAbsent(roleKey, k -> new ConcurrentHashMap<>())
                .put(flagKeyLower, state);
    }

    /**
     * Read-only view of all per-role flag overrides for this plot.
     * Useful for admin/debug menus.
     */
    public Map<String, Map<String, TriState>> getRoleFlagStates() {
        // NOTE: outer map is unmodifiable; inner maps are still live views.
        return Collections.unmodifiableMap(roleFlagStates);
    }

    /**
     * Remove all overrides for a given role (e.g. when deleting a role type).
     */
    public void clearRoleFlagsForRole(String roleName) {
        if (roleName == null) return;
        roleFlagStates.remove(roleName.toLowerCase());
    }

    // --- GETTERS & SETTERS ---

    public UUID getPlotId() {
        return plotId;
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

    public void setOwnerName(String name) {
        this.ownerName = name;
    }

    public String getWorld() {
        return world;
    }

    public int getX1() {
        return x1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getZ2() {
        return z2;
    }

    public void setX1(int x) {
        this.x1 = x;
    }

    public void setZ1(int z) {
        this.z1 = z;
    }

    public void setX2(int x) {
        this.x2 = x;
    }

    public void setZ2(int z) {
        this.z2 = z;
    }

    public void internalSetOwner(UUID newOwner, String newOwnerName) {
        this.owner = newOwner;
        this.ownerName = newOwnerName;
        this.playerRoles.clear();
        if (!isServerZone()) {
            this.playerRoles.put(newOwner, "owner");
        }
        this.bannedPlayers.clear();
        this.likedBy.clear();
        this.roleFlagStates.clear();
        this.entryTitle = null;
        this.description = null;
    }

    // Flags

    public boolean getFlag(String key, boolean def) {
        return flags.getOrDefault(key, def);
    }

    public void setFlag(String key, boolean value) {
        flags.put(key, value);
    }

    public Map<String, Boolean> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    // Serialization Helpers for SQL

    public String serializeFlags() {
        return flags.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    public String serializeRoles() {
        return playerRoles.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * Serialize roleFlagStates to a compact string.
     *
     * Format example:
     *   owner>pvp=ALLOW|entry=DENY;member>pvp=DENY
     *
     * Only non-INHERIT states are stored.
     */
    public String serializeRoleFlags() {
        if (roleFlagStates.isEmpty()) return "";

        return roleFlagStates.entrySet().stream()
                .map(roleEntry -> {
                    String roleName = roleEntry.getKey();
                    String flagsPart = roleEntry.getValue().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue().name())
                            .collect(Collectors.joining("|"));
                    return roleName + ">" + flagsPart;
                })
                .collect(Collectors.joining(";"));
    }

    /**
     * Load roleFlagStates from the string produced by serializeRoleFlags().
     */
    public void deserializeRoleFlags(String data) {
        roleFlagStates.clear();
        if (data == null || data.isEmpty()) return;

        String[] roleBlocks = data.split(";");
        for (String block : roleBlocks) {
            if (block.isEmpty()) continue;

            String[] parts = block.split(">");
            if (parts.length != 2) continue;

            String roleName = parts[0].toLowerCase();
            String flagsPart = parts[1];

            Map<String, TriState> flagsForRole = new ConcurrentHashMap<>();

            String[] flagTokens = flagsPart.split("\\|");
            for (String token : flagTokens) {
                if (token.isEmpty()) continue;
                String[] kv = token.split("=");
                if (kv.length != 2) continue;

                String flagKey = kv[0].toLowerCase();
                String stateName = kv[1];

                try {
                    TriState state = TriState.valueOf(stateName);
                    flagsForRole.put(flagKey, state);
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown / corrupted enum values
                }
            }

            if (!flagsForRole.isEmpty()) {
                roleFlagStates.put(roleName, flagsForRole);
            }
        }
    }

    // Zones

    public List<Zone> getZones() {
        return zones;
    }

    public void addZone(Zone zone) {
        zones.add(Objects.requireNonNull(zone, "zone"));
    }

    public void removeZone(Zone zone) {
        zones.remove(zone);
    }

    public Zone getZoneAt(Location loc) {
        if (loc == null) return null;
        for (Zone z : zones) {
            if (z.isInside(loc)) return z;
        }
        return null;
    }

    // Leveling

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public void addXp(double amount) {
        this.xp += amount;
    }

    // Social

    public Set<UUID> getLikedBy() {
        return Collections.unmodifiableSet(likedBy);
    }

    public int getLikes() {
        return likedBy.size();
    }

    public boolean hasLiked(UUID player) {
        return likedBy.contains(player);
    }

    public void toggleLike(UUID player) {
        if (likedBy.contains(player)) likedBy.remove(player);
        else likedBy.add(player);
    }

    // Bans

    public boolean isBanned(UUID playerUUID) {
        return bannedPlayers.contains(playerUUID);
    }

    public void addBan(UUID playerUUID) {
        playerRoles.remove(playerUUID);
        bannedPlayers.add(playerUUID);
    }

    public void removeBan(UUID playerUUID) {
        bannedPlayers.remove(playerUUID);
    }

    public Set<UUID> getBannedPlayers() {
        return Collections.unmodifiableSet(bannedPlayers);
    }

    // Upkeep & Economy

    public long getLastUpkeep() {
        return lastUpkeepPayment;
    } // Getter alias for SQLDataStore

    public long getLastUpkeepPayment() {
        return lastUpkeepPayment;
    }

    public void setLastUpkeep(long time) {
        this.lastUpkeepPayment = time;
    } // Setter alias

    public void setLastUpkeepPayment(long time) {
        this.lastUpkeepPayment = time;
    }

    public boolean isForSale() {
        return isForSale;
    }

    public void setForSale(boolean forSale, double price) {
        this.isForSale = forSale;
        this.salePrice = price;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public boolean isForRent() {
        return isForRent;
    }

    public double getRentPrice() {
        return rentPrice;
    }

    public void setForRent(boolean forRent, double price) {
        this.isForRent = forRent;
        this.rentPrice = price;
    }

    public UUID getCurrentRenter() {
        return currentRenter;
    }

    public long getRentExpires() {
        return rentExpires;
    }

    public void setRenter(UUID renter, long expirationTime) {
        this.currentRenter = renter;
        this.rentExpires = expirationTime;
    }

    // Auction

    public String getPlotStatus() {
        return plotStatus;
    }

    public void setPlotStatus(String status) {
        this.plotStatus = status;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public UUID getCurrentBidder() {
        return currentBidder;
    }

    public void setCurrentBid(double bid, UUID bidder) {
        this.currentBid = bid;
        this.currentBidder = bidder;
    }

    // Visuals

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location loc) {
        this.spawnLocation = loc;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String msg) {
        this.welcomeMessage = msg;
    }

    public String getFarewellMessage() {
        return farewellMessage;
    }

    public void setFarewellMessage(String msg) {
        this.farewellMessage = msg;
    }

    public String getSpawnLocationString() {
        if (spawnLocation == null) return null;
        return String.format("%s:%.2f:%.2f:%.2f:%.2f:%.2f",
                spawnLocation.getWorld().getName(),
                spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ(),
                spawnLocation.getYaw(), spawnLocation.getPitch());
    }

    public void setSpawnLocationFromString(String s) {
        if (s == null || s.isEmpty()) {
            this.spawnLocation = null;
            return;
        }
        try {
            String[] parts = s.split(":");
            if (parts.length < 4) return;
            World world = Bukkit.getWorld(parts[0]);
            if (world != null) {
                this.spawnLocation = new Location(world,
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        parts.length > 4 ? Float.parseFloat(parts[4]) : 0f,
                        parts.length > 5 ? Float.parseFloat(parts[5]) : 0f
                );
            }
        } catch (Exception ignored) {
        }
    }

    // Identity

    public String getEntryTitle() {
        return entryTitle;
    }

    public void setEntryTitle(String title) {
        this.entryTitle = title;
    }

    public String getEntrySubtitle() {
        return entrySubtitle;
    }

    public void setEntrySubtitle(String sub) {
        this.entrySubtitle = sub;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCustomBiome() {
        return customBiome;
    }

    public void setCustomBiome(String biome) {
        this.customBiome = biome;
    }

    // Cosmetics

    public String getBorderParticle() {
        return borderParticle;
    }

    public void setBorderParticle(String particle) {
        this.borderParticle = particle;
    }

    public String getAmbientParticle() {
        return ambientParticle;
    }

    public void setAmbientParticle(String particle) {
        this.ambientParticle = particle;
    }

    public String getEntryEffect() {
        return entryEffect;
    }

    public void setEntryEffect(String effect) {
        this.entryEffect = effect;
    }

    // Server Warps

    public boolean isServerWarp() {
        return isServerWarp;
    }

    public String getWarpName() {
        return warpName;
    }

    public Material getWarpIcon() {
        return warpIcon;
    }

    public void setServerWarp(boolean isWarp, String name, Material icon) {
        this.isServerWarp = isWarp;
        this.warpName = name;
        this.warpIcon = icon;
    }

    // --- Object overrides ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Plot plot)) return false;
        return plotId.equals(plot.plotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plotId);
    }

    @Override
    public String toString() {
        return "Plot{id=" + plotId + ", owner=" + ownerName + ", world=" + world + "}";
    }
}
