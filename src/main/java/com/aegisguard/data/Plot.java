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
 * Plot (Data Class) - v1.2.6+
 * - Represents a land claim.
 * - 2D (X/Z only) to ensure Bedrock-to-Sky protection.
 * - [Fix] Added getArea() for Claim Block calculation.
 * - [Fix] Added alias methods for compatibility (getX1, getZ1, etc.)
 * - [Fix] Added welcome/farewell messages, entry effects, level system
 */
public class Plot {

    public static final UUID SERVER_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final Map<String, Boolean> DEFAULT_FLAGS = new ConcurrentHashMap<>();

    static {
        // These defaults act as safe fallbacks when no WorldRules are applied.
        DEFAULT_FLAGS.put("build", true);
        DEFAULT_FLAGS.put("entry", true);
        DEFAULT_FLAGS.put("pvp", false);
        DEFAULT_FLAGS.put("safe_zone", false);
        DEFAULT_FLAGS.put("fly", false);
        DEFAULT_FLAGS.put("explosions", false);
        DEFAULT_FLAGS.put("fire_spread", false);
    }

    private final UUID id;
    private UUID owner;
    private String ownerName;
    private final String worldName;

    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    private final long createdAt;

    // --- SELL / MARKET / RENT ---
    private boolean forSale = false;
    private double salePrice = 0;

    private UUID currentRenter = null;
    private long rentExpires = 0;

    // --- META ---
    private String plotName = null;
    private String description = null;

    // --- 1.2.6+ GREETING MESSAGES ---
    private String welcomeMessage = null;
    private String farewellMessage = null;

    // --- 1.2.6+ ENTRY EFFECT ---
    private String entryEffect = null;

    // --- 1.2.6+ LEVELING SYSTEM ---
    private int level = 1;

    // --- FLAGS ---
    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    // --- TRUST / BANS ---
    private final List<UUID> trustedPlayers = new CopyOnWriteArrayList<>();
    private final List<UUID> bannedPlayers = new CopyOnWriteArrayList<>();

    // --- ROLES ---
    private final Map<UUID, String> playerRoles = new ConcurrentHashMap<>();

    // --- ROLE FLAG OVERRIDES (role -> (flagKey -> TriState)) ---
    private final Map<String, Map<String, TriState>> roleFlagStates = new ConcurrentHashMap<>();

    public Plot(UUID id, UUID owner, String ownerName, String worldName, int minX, int minZ, int maxX, int maxZ, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.worldName = worldName;

        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);

        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);

        this.createdAt = createdAt;

        // Load defaults
        DEFAULT_FLAGS.forEach(flags::putIfAbsent);
    }

    public UUID getId() {
        return id;
    }

    /**
     * Alias for getId() - compatibility method
     */
    public UUID getPlotId() {
        return id;
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

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    // --- ALIAS METHODS FOR COMPATIBILITY ---

    /**
     * Alias for getMinX() - compatibility method
     */
    public int getX1() {
        return minX;
    }

    /**
     * Alias for getMinZ() - compatibility method
     */
    public int getZ1() {
        return minZ;
    }

    /**
     * Alias for getMaxX() - compatibility method
     */
    public int getX2() {
        return maxX;
    }

    /**
     * Alias for getMaxZ() - compatibility method
     */
    public int getZ2() {
        return maxZ;
    }

    /**
     * Set minX bound
     */
    public void setX1(int x1) {
        this.minX = x1;
        normalizeBounds();
    }

    /**
     * Set minZ bound
     */
    public void setZ1(int z1) {
        this.minZ = z1;
        normalizeBounds();
    }

    /**
     * Set maxX bound
     */
    public void setX2(int x2) {
        this.maxX = x2;
        normalizeBounds();
    }

    /**
     * Set maxZ bound
     */
    public void setZ2(int z2) {
        this.maxZ = z2;
        normalizeBounds();
    }

    /**
     * Ensure min values are always less than max values
     */
    private void normalizeBounds() {
        int tempMinX = Math.min(minX, maxX);
        int tempMaxX = Math.max(minX, maxX);
        int tempMinZ = Math.min(minZ, maxZ);
        int tempMaxZ = Math.max(minZ, maxZ);

        this.minX = tempMinX;
        this.maxX = tempMaxX;
        this.minZ = tempMinZ;
        this.maxZ = tempMaxZ;
    }

    public void setBounds(int minX, int minZ, int maxX, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);

        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isServerZone() {
        return SERVER_OWNER_UUID.equals(owner);
    }

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = salePrice;
    }

    public UUID getCurrentRenter() {
        return currentRenter;
    }

    public void setCurrentRenter(UUID currentRenter) {
        this.currentRenter = currentRenter;
    }

    public long getRentExpires() {
        return rentExpires;
    }

    public void setRentExpires(long rentExpires) {
        this.rentExpires = rentExpires;
    }

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

    // --- 1.2.6+ GREETING MESSAGES ---

    /**
     * Get the welcome message displayed when entering the plot
     * @return The welcome message, or null if not set
     */
    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    /**
     * Set the welcome message displayed when entering the plot
     * @param welcomeMessage The message to display, or null to disable
     */
    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    /**
     * Get the farewell message displayed when leaving the plot
     * @return The farewell message, or null if not set
     */
    public String getFarewellMessage() {
        return farewellMessage;
    }

    /**
     * Set the farewell message displayed when leaving the plot
     * @param farewellMessage The message to display, or null to disable
     */
    public void setFarewellMessage(String farewellMessage) {
        this.farewellMessage = farewellMessage;
    }

    // --- 1.2.6+ ENTRY EFFECT ---

    /**
     * Get the entry effect played when entering the plot
     * @return The effect name, or null if not set
     */
    public String getEntryEffect() {
        return entryEffect;
    }

    /**
     * Set the entry effect played when entering the plot
     * @param entryEffect The effect name, or null to disable
     */
    public void setEntryEffect(String entryEffect) {
        this.entryEffect = entryEffect;
    }

    // --- 1.2.6+ LEVELING SYSTEM ---

    /**
     * Get the plot's level (for leveling/upgrade system)
     * @return The plot level (default: 1)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Set the plot's level
     * @param level The new level (minimum: 1)
     */
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    /**
     * Increment the plot's level by 1
     * @return The new level
     */
    public int levelUp() {
        this.level++;
        return this.level;
    }

    public boolean isInside(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) return false;

        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int getArea() {
        return (Math.abs(maxX - minX) + 1) * (Math.abs(maxZ - minZ) + 1);
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getCenter() {
        World world = getWorld();
        if (world == null) return null;

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        return new Location(world, centerX + 0.5, world.getHighestBlockYAt(centerX, centerZ) + 1.0, centerZ + 0.5);
    }

    public List<UUID> getTrustedPlayers() {
        return trustedPlayers;
    }

    public void trust(UUID playerUUID) {
        if (!trustedPlayers.contains(playerUUID)) trustedPlayers.add(playerUUID);
        bannedPlayers.remove(playerUUID);
    }

    public void untrust(UUID playerUUID) {
        trustedPlayers.remove(playerUUID);
    }

    public boolean isTrusted(Player player) {
        if (player == null) return false;
        if (owner.equals(player.getUniqueId())) return true;
        if (trustedPlayers.contains(player.getUniqueId())) return true;

        // Role-based trust: member+ counts as trusted
        String role = getRole(player.getUniqueId());
        return role != null && !role.equalsIgnoreCase("visitor");
    }

    public List<UUID> getBannedPlayers() {
        return bannedPlayers;
    }

    public void ban(UUID playerUUID) {
        if (!bannedPlayers.contains(playerUUID)) bannedPlayers.add(playerUUID);
        trustedPlayers.remove(playerUUID);
        playerRoles.remove(playerUUID);
    }

    public void unban(UUID playerUUID) {
        bannedPlayers.remove(playerUUID);
    }

    public boolean isBanned(UUID playerUUID) {
        return bannedPlayers.contains(playerUUID);
    }

    public boolean isOwner(Player player) {
        if (player == null) return false;
        return owner.equals(player.getUniqueId());
    }

    public boolean isOwner(UUID uuid) {
        if (uuid == null) return false;
        return owner.equals(uuid);
    }

    public boolean hasPermission(UUID playerUUID, String permission, AegisGuard plugin) {
        if (playerUUID == null || permission == null || plugin == null) return false;

        if (owner.equals(playerUUID)) return true;

        if (isBanned(playerUUID)) return false;

        Player online = Bukkit.getPlayer(playerUUID);
        if (online != null) {
            if (online.hasPermission("aegis.bypass")) return true;
        }

        // Server zones: require an explicitly assigned role (prevents everyone defaulting to visitor).
        if (isServerZone() && !playerRoles.containsKey(playerUUID)) {
            return false;
        }

        if (currentRenter != null && currentRenter.equals(playerUUID)) {
            if (System.currentTimeMillis() < rentExpires) {
                Set<String> perms = new HashSet<>();
                List<String> configPerms = plugin.cfg().raw().getStringList("roles.member.permissions");
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

    /**
     * Check if a player can modify (remove or change role) a specific member.
     * v1.2.6: Prevents owners from removing themselves and respects admin bypass.
     *
     * @param editor       The player attempting the modification
     * @param targetUUID   The UUID of the member being modified
     * @return true if modification is allowed
     */
    public boolean canModifyMember(Player editor, UUID targetUUID) {
        if (editor == null || targetUUID == null) return false;

        // Admin bypass: can always modify any plot member
        if (editor.hasPermission("aegis.admin") || editor.hasPermission("aegis.admin.override")) {
            return true;
        }

        // Server plots: only admins can modify
        if (isServerZone()) {
            return editor.hasPermission("aegis.admin");
        }

        // Only owner can modify members (unless admin)
        if (!isOwner(editor)) {
            return false;
        }

        // CRITICAL: Owners cannot remove themselves (prevents plot orphaning)
        if (editor.getUniqueId().equals(targetUUID)) {
            return false;
        }

        return true;
    }

    /**
     * Check if a player can manage this plot (admin override or owner).
     * v1.2.6: Ensures admins are never locked out.
     *
     * @param player The player to check
     * @return true if player can manage the plot
     */
    public boolean canManage(Player player) {
        if (player == null) return false;

        // Admin bypass
        if (player.hasPermission("aegis.admin") || player.hasPermission("aegis.admin.override")) {
            return true;
        }

        // Owner can manage (except server zones)
        if (!isServerZone() && isOwner(player)) {
            return true;
        }

        return false;
    }

    // --- ROLE FLAG OVERRIDES ---

    public TriState getRoleFlagState(String roleName, String flagKey) {
        if (roleName == null || flagKey == null) return TriState.INHERIT;
        Map<String, TriState> perRole = roleFlagStates.get(roleName.toLowerCase());
        if (perRole == null) return TriState.INHERIT;
        return perRole.getOrDefault(flagKey.toLowerCase(), TriState.INHERIT);
    }

    public void setRoleFlagState(String roleName, String flagKey, TriState state) {
        if (roleName == null || flagKey == null || state == null) return;
        roleName = roleName.toLowerCase();
        flagKey = flagKey.toLowerCase();

        roleFlagStates.putIfAbsent(roleName, new ConcurrentHashMap<>());
        Map<String, TriState> perRole = roleFlagStates.get(roleName);

        if (state == TriState.INHERIT) {
            perRole.remove(flagKey);
        } else {
            perRole.put(flagKey, state);
        }
    }

    public Map<String, Map<String, TriState>> getRoleFlagStates() {
        return roleFlagStates;
    }

    public boolean getFlag(String key, boolean def) {
        return flags.getOrDefault(key, def);
    }

    public void setFlag(String key, boolean value) {
        flags.put(key, value);
    }

    public Map<String, Boolean> getFlags() {
        return flags;
    }

    public boolean isInPlot(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public String toPrettyString() {
        return ChatColorStrip.stripColors(getDisplayName()) + " (" + worldName + ")";
    }

    public String getDisplayName() {
        if (plotName != null && !plotName.isEmpty()) return plotName;
        if (isServerZone()) return "Server Zone";
        return ownerName + "'s Plot";
    }

    public String getTrustedDisplay() {
        return trustedPlayers.stream()
                .map(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return p != null ? p.getName() : uuid.toString();
                })
                .collect(Collectors.joining(", "));
    }

    public String getBannedDisplay() {
        return bannedPlayers.stream()
                .map(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return p != null ? p.getName() : uuid.toString();
                })
                .collect(Collectors.joining(", "));
    }

    public Material getIconMaterial() {
        return Material.GRASS_BLOCK;
    }

    // ---------------------------------------------------------------------
    // 1.2.6 QoL: Unified management + build checks (server zones included)
    // ---------------------------------------------------------------------

    /**
     * True if this player should be allowed to manage this plot (flags/roles/settings).
     * This is the preferred check over direct owner comparisons.
     */
    public boolean canManage(@Nullable Player player, @Nullable AegisGuard plugin) {
        if (player == null) return false;

        if (plugin != null && (plugin.isAdmin(player) || plugin.isBypassing(player))) return true;
        if (player.hasPermission("aegis.bypass")) return true;

        return hasPermission(player.getUniqueId(), "MANAGE", plugin != null ? plugin : AegisGuard.getInstance());
    }

    /**
     * True if this player should be allowed to build (place/break) inside this plot.
     *
     * Flag semantics:
     *  - build=true  -> building is generally allowed
     *  - build=false -> building is restricted to trusted roles and overrides
     *
     * Role flag overrides:
     *  - build=ALLOW -> always allow building for that role
     *  - build=DENY  -> always deny building for that role
     *  - build=INHERIT -> follow plot flag + role permissions
     */
    public boolean canBuild(@Nullable Player player, @Nullable AegisGuard plugin, @Nullable String permission) {
        if (player == null) return false;

        // Admins / bypass always build
        if (plugin != null && (plugin.isAdmin(player) || plugin.isBypassing(player))) return true;
        if (player.hasPermission("aegis.bypass")) return true;

        UUID uuid = player.getUniqueId();
        if (owner.equals(uuid)) return true;
        if (isBanned(uuid)) return false;

        // Role override beats everything else
        String roleName = getRole(uuid);
        TriState override = getRoleFlagState(roleName, "build");
        if (override == TriState.ALLOW) return true;
        if (override == TriState.DENY) return false;

        // If building is allowed in this plot, allow it.
        if (getFlag("build", true)) return true;

        // Building is restricted, require role permission.
        String perm = (permission == null || permission.isEmpty()) ? "BUILD" : permission.toUpperCase(Locale.ROOT);

        // Treat BUILD as a wildcard for place/break checks.
        if ("BLOCK_BREAK".equals(perm) || "BLOCK_PLACE".equals(perm)) {
            return hasPermission(uuid, perm, plugin != null ? plugin : AegisGuard.getInstance())
                    || hasPermission(uuid, "BUILD", plugin != null ? plugin : AegisGuard.getInstance());
        }

        return hasPermission(uuid, perm, plugin != null ? plugin : AegisGuard.getInstance());
    }

    /** Convenience overload that uses the plugin singleton. */
    public boolean canBuild(@Nullable Player player, @Nullable String permission) {
        return canBuild(player, AegisGuard.getInstance(), permission);
    }

    /** Convenience overload that uses the plugin singleton. */
    public boolean canManage(@Nullable Player player) {
        return canManage(player, AegisGuard.getInstance());
    }
}
