package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary staff-granted flight, plus staff fly and Ascension/Horizon level-30
 * plot flight. Hub Safety never grants flight.
 */
public final class FlightSkillService implements Listener {
    private final AegisGuard plugin;
    private final Map<UUID, Long> expiresAt = new ConcurrentHashMap<>();
    private final File file;

    public FlightSkillService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "flight-skills.yml");
        load();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("flight_skill.enabled", true);
    }

    public int defaultSeconds() {
        return Math.max(1, plugin.getConfig().getInt("flight_skill.default_seconds", 300));
    }

    public int maxSeconds() {
        return Math.max(defaultSeconds(), plugin.getConfig().getInt("flight_skill.max_seconds", 3600));
    }

    public boolean staffAlways() {
        return plugin.getConfig().getBoolean("flight_skill.staff_always", true);
    }

    public long grant(UUID playerId, int seconds) {
        if (playerId == null) return 0L;
        int clamped = Math.max(1, Math.min(seconds, maxSeconds()));
        long until = System.currentTimeMillis() + clamped * 1000L;
        expiresAt.put(playerId, until);
        save();
        return until;
    }

    public boolean clear(UUID playerId) {
        if (playerId == null) return false;
        boolean removed = expiresAt.remove(playerId) != null;
        if (removed) save();
        return removed;
    }

    public boolean isSkillActive(UUID playerId) {
        if (playerId == null) return false;
        Long until = expiresAt.get(playerId);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            expiresAt.remove(playerId, until);
            return false;
        }
        return true;
    }

    public long remainingMillis(UUID playerId) {
        Long until = expiresAt.get(playerId);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public boolean shouldAllowFlight(Player player) {
        if (player == null || !isEnabled()) return false;
        if (isCreativeLike(player)) return true;
        if (staffAlways() && (plugin.isAdmin(player) || player.hasPermission("aegis.admin.fly"))) {
            return true;
        }
        if (isSkillActive(player.getUniqueId())) return true;
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        if (plot == null || plugin.ascensionEffects() == null) return false;
        return plugin.ascensionEffects().hasFlightReward(plot)
                && (plot.isOwner(player) || plot.isTrusted(player));
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        if (isCreativeLike(player)) return;
        boolean allow = shouldAllowFlight(player);
        player.setAllowFlight(allow);
        if (!allow && player.isFlying()) player.setFlying(false);
    }

    public void refreshLater(Player player) {
        if (player == null) return;
        plugin.runEntity(player, () -> refresh(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pruneExpired();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        refreshLater(event.getPlayer());
    }

    private boolean isCreativeLike(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<UUID, Long>> it = expiresAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() == null || entry.getValue() <= now) {
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }

    private void load() {
        expiresAt.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        long now = System.currentTimeMillis();
        if (yaml.isConfigurationSection("grants")) {
            for (String key : yaml.getConfigurationSection("grants").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    long until = yaml.getLong("grants." + key, 0L);
                    if (until > now) expiresAt.put(id, until);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : expiresAt.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > now) {
                yaml.set("grants." + entry.getKey(), entry.getValue());
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(file);
        } catch (IOException error) {
            plugin.getLogger().warning("Could not save flight-skills.yml: " + error.getMessage());
        }
    }
}
