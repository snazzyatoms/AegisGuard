package com.aegisguard.routes;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Milestone 6 - staff-authored routes with ordered checkpoints, player browse/progress, and
 * optional completion rewards. Never forces teleports and never alters claim boundaries.
 */
public class RouteService {

    private final AegisGuard plugin;
    private final File routesFile;
    private final File progressFile;
    private final Map<UUID, Route> routes = new ConcurrentHashMap<>();
    private final Map<String, RouteProgress> progress = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeRoutes = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirtyRoutes;
    private volatile boolean dirtyProgress;
    private volatile boolean routesStorageReady;
    private volatile boolean progressStorageReady;

    public RouteService(AegisGuard plugin) {
        this.plugin = plugin;
        this.routesFile = new File(plugin.getDataFolder(), "routes.yml");
        this.progressFile = new File(plugin.getDataFolder(), "routes-progress.yml");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("routes.enabled", true);
    }

    public double defaultCheckpointRadius() {
        return Math.max(1.0D, plugin.getConfig().getDouble("routes.default_checkpoint_radius", 4.0D));
    }

    public boolean rewardsEnabled() {
        return plugin.getConfig().getBoolean("routes.rewards.enabled", true);
    }

    public Collection<Route> allRoutes() {
        return List.copyOf(routes.values());
    }

    public List<Route> enabledRoutes() {
        List<Route> list = new ArrayList<>();
        for (Route route : routes.values()) {
            if (route != null && route.isEnabled()) list.add(route);
        }
        list.sort(Comparator.comparing(Route::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Route getRoute(UUID id) {
        return id == null ? null : routes.get(id);
    }

    public Route createRoute(String name, Player creator) {
        Route route = Route.create(name);
        routes.put(route.getId(), route);
        dirtyRoutes = true;
        saveRoutes();
        return route;
    }

    public boolean deleteRoute(UUID routeId) {
        if (routeId == null || routes.remove(routeId) == null) return false;
        progress.keySet().removeIf(key -> key.endsWith(":" + routeId));
        dirtyRoutes = true;
        dirtyProgress = true;
        saveRoutes();
        saveProgress();
        return true;
    }

    public void saveRoute(Route route) {
        if (route == null) return;
        routes.put(route.getId(), route);
        dirtyRoutes = true;
        saveRoutes();
    }

    public RouteProgress progressOf(UUID playerId, UUID routeId) {
        if (playerId == null || routeId == null) return null;
        String key = key(playerId, routeId);
        return progress.computeIfAbsent(key, ignored -> new RouteProgress(playerId, routeId));
    }

    public Checkpoint nextCheckpoint(UUID playerId, Route route) {
        if (route == null || playerId == null) return null;
        RouteProgress p = progressOf(playerId, route.getId());
        return route.nextAfter(p.getDiscoveredCount());
    }

    public void setActiveRoute(UUID playerId, UUID routeId) {
        if (playerId == null) return;
        if (routeId == null) activeRoutes.remove(playerId);
        else activeRoutes.put(playerId, routeId);
    }

    public Route activeRoute(UUID playerId) {
        UUID routeId = playerId == null ? null : activeRoutes.get(playerId);
        Route route = getRoute(routeId);
        if (route == null || !route.isEnabled()) {
            if (playerId != null) activeRoutes.remove(playerId);
            return null;
        }
        return route;
    }

    public Checkpoint activeNextCheckpoint(UUID playerId) {
        Route route = activeRoute(playerId);
        return route == null ? null : nextCheckpoint(playerId, route);
    }

    /**
     * Discovers the next checkpoint if the player is standing within its radius.
     * Returns the newly discovered checkpoint, or null if nothing new was found.
     */
    public Checkpoint tryDiscoverAt(Player player) {
        if (!isEnabled() || player == null) return null;
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return null;

        for (Route route : enabledRoutes()) {
            Checkpoint next = nextCheckpoint(player.getUniqueId(), route);
            if (next == null) continue;
            if (!next.isWithinRange(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ())) continue;

            RouteProgress p = progressOf(player.getUniqueId(), route.getId());
            if (!p.discover(next.getId())) continue;
            dirtyProgress = true;
            saveProgressAsync();

            plugin.msg().send(player, "route_checkpoint_discovered",
                    Map.of("ROUTE", route.getName(), "CHECKPOINT", next.getName(),
                            "COUNT", String.valueOf(p.getDiscoveredCount()),
                            "TOTAL", String.valueOf(route.size())));

            if (route.isComplete(p.getDiscoveredCount())) {
                grantCompletionReward(player, route, p);
            }
            return next;
        }
        return null;
    }

    private void grantCompletionReward(Player player, Route route, RouteProgress p) {
        if (!rewardsEnabled() || p.isRewardClaimed()) {
            plugin.msg().send(player, "route_completed", Map.of("ROUTE", route.getName()));
            return;
        }

        boolean any = false;
        if (route.getRewardMoney() > 0 && plugin.eco() != null) {
            try {
                plugin.eco().deposit(player, route.getRewardMoney(), CurrencyType.VAULT);
                any = true;
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to grant the money reward for route " + route.getId()
                                + " to player " + player.getUniqueId() + ".",
                        error);
            }
        }
        if (route.getRewardClaimBlocks() > 0 && plugin.getClaimBlockManager() != null) {
            try {
                plugin.getClaimBlockManager().addEarned(player.getUniqueId(), (long) route.getRewardClaimBlocks());
                any = true;
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to grant the claim-block reward for route " + route.getId()
                                + " to player " + player.getUniqueId() + ".",
                        error);
            }
        }

        p.setRewardClaimed(true);
        dirtyProgress = true;
        saveProgressAsync();

        if (any) {
            plugin.msg().send(player, "route_completed_rewarded", Map.of(
                    "ROUTE", route.getName(),
                    "MONEY", String.valueOf(route.getRewardMoney()),
                    "BLOCKS", String.valueOf(route.getRewardClaimBlocks())));
        } else {
            plugin.msg().send(player, "route_completed", Map.of("ROUTE", route.getName()));
        }
    }

    public Location toLocation(Checkpoint checkpoint) {
        if (checkpoint == null) return null;
        World world = Bukkit.getWorld(checkpoint.getWorld());
        if (world == null) return null;
        return new Location(world, checkpoint.getX(), checkpoint.getY(), checkpoint.getZ());
    }

    public synchronized void load() {
        loadRoutes();
        loadProgress();
    }

    public synchronized void save() {
        saveRoutes();
        saveProgress();
    }

    public boolean isDirty() {
        return dirtyRoutes || dirtyProgress;
    }

    private void loadRoutes() {
        synchronized (ioLock) {
            if (!ensureFile(routesFile)) {
                routesStorageReady = false;
                return;
            }
            routesStorageReady = true;
            routes.clear();
            FileConfiguration data = YamlConfiguration.loadConfiguration(routesFile);
            ConfigurationSection sec = data.getConfigurationSection("routes");
            if (sec == null) {
                dirtyRoutes = false;
                return;
            }
            for (String key : sec.getKeys(false)) {
                try {
                    ConfigurationSection r = sec.getConfigurationSection(key);
                    if (r == null) continue;
                    UUID id = UUID.fromString(key);
                    List<Checkpoint> checkpoints = new ArrayList<>();
                    ConfigurationSection cps = r.getConfigurationSection("checkpoints");
                    if (cps != null) {
                        List<String> order = r.getStringList("checkpoint_order");
                        if (order.isEmpty()) order = new ArrayList<>(cps.getKeys(false));
                        for (String cpKey : order) {
                            ConfigurationSection c = cps.getConfigurationSection(cpKey);
                            if (c == null) continue;
                            checkpoints.add(new Checkpoint(
                                    UUID.fromString(cpKey),
                                    c.getString("name", "Checkpoint"),
                                    c.getString("world", "world"),
                                    c.getDouble("x"),
                                    c.getDouble("y"),
                                    c.getDouble("z"),
                                    c.getDouble("radius", defaultCheckpointRadius())
                            ));
                        }
                    }
                    routes.put(id, new Route(
                            id,
                            r.getString("name", "Route"),
                            r.getString("description", ""),
                            r.getBoolean("enabled", true),
                            checkpoints,
                            r.getDouble("reward_money", 0.0D),
                            r.getInt("reward_claim_blocks", 0)
                    ));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load route " + key + ": " + ex.getMessage());
                }
            }
            dirtyRoutes = false;
            plugin.console().info("log_routes_loaded", "Loaded {COUNT} exploration route(s).", "COUNT", String.valueOf(routes.size()));
        }
    }

    private void saveRoutes() {
        synchronized (ioLock) {
            if (!routesStorageReady) {
                plugin.getLogger().severe("Refusing to save routes.yml because route storage did not initialize successfully.");
                return;
            }
            if (!dirtyRoutes && routesFile.exists()) return;
            YamlConfiguration out = new YamlConfiguration();
            ConfigurationSection root = out.createSection("routes");
            for (Route route : routes.values()) {
                ConfigurationSection r = root.createSection(route.getId().toString());
                r.set("name", route.getName());
                r.set("description", route.getDescription());
                r.set("enabled", route.isEnabled());
                r.set("reward_money", route.getRewardMoney());
                r.set("reward_claim_blocks", route.getRewardClaimBlocks());
                List<String> order = new ArrayList<>();
                ConfigurationSection cps = r.createSection("checkpoints");
                for (Checkpoint cp : route.getCheckpoints()) {
                    order.add(cp.getId().toString());
                    ConfigurationSection c = cps.createSection(cp.getId().toString());
                    c.set("name", cp.getName());
                    c.set("world", cp.getWorld());
                    c.set("x", cp.getX());
                    c.set("y", cp.getY());
                    c.set("z", cp.getZ());
                    c.set("radius", cp.getRadius());
                }
                r.set("checkpoint_order", order);
            }
            try {
                out.save(routesFile);
                dirtyRoutes = false;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save routes.yml", e);
            }
        }
    }

    private void loadProgress() {
        synchronized (ioLock) {
            if (!ensureFile(progressFile)) {
                progressStorageReady = false;
                return;
            }
            progressStorageReady = true;
            progress.clear();
            FileConfiguration data = YamlConfiguration.loadConfiguration(progressFile);
            ConfigurationSection players = data.getConfigurationSection("players");
            if (players == null) {
                dirtyProgress = false;
                return;
            }
            for (String playerKey : players.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerKey);
                    ConfigurationSection routesSec = players.getConfigurationSection(playerKey);
                    if (routesSec == null) continue;
                    for (String routeKey : routesSec.getKeys(false)) {
                        UUID routeId = UUID.fromString(routeKey);
                        ConfigurationSection pSec = routesSec.getConfigurationSection(routeKey);
                        if (pSec == null) continue;
                        RouteProgress p = new RouteProgress(playerId, routeId);
                        Set<UUID> discovered = new LinkedHashSet<>();
                        for (String id : pSec.getStringList("discovered")) {
                            try { discovered.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
                        }
                        p.replaceDiscovered(discovered);
                        p.setRewardClaimed(pSec.getBoolean("reward_claimed", false));
                        progress.put(key(playerId, routeId), p);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load route progress for " + playerKey + ": " + ex.getMessage());
                }
            }
            dirtyProgress = false;
        }
    }

    private void saveProgress() {
        synchronized (ioLock) {
            if (!progressStorageReady) {
                plugin.getLogger().severe("Refusing to save routes-progress.yml because route-progress storage did not initialize successfully.");
                return;
            }
            if (!dirtyProgress && progressFile.exists()) return;
            YamlConfiguration out = new YamlConfiguration();
            for (RouteProgress p : progress.values()) {
                String base = "players." + p.getPlayerId() + "." + p.getRouteId();
                List<String> ids = new ArrayList<>();
                for (UUID id : p.getDiscoveredCheckpointIds()) ids.add(id.toString());
                out.set(base + ".discovered", ids);
                out.set(base + ".reward_claimed", p.isRewardClaimed());
            }
            try {
                out.save(progressFile);
                dirtyProgress = false;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save routes-progress.yml", e);
            }
        }
    }

    private void saveProgressAsync() {
        dirtyProgress = true;
        plugin.runGlobalAsync(this::saveProgress);
    }

    private boolean ensureFile(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create plugin data directory " + parent);
            }
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("Could not create " + file);
            }
            if (!file.isFile() || !file.canRead()) {
                throw new IOException(file + " is not a readable file");
            }
            return true;
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not create or access " + file.getName()
                            + ". Existing in-memory route data was retained and saves are disabled for this file.",
                    error);
            return false;
        }
    }

    private static String key(UUID playerId, UUID routeId) {
        return playerId + ":" + routeId;
    }
}
