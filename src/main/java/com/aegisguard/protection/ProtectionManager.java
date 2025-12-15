package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.hooks.protection.HookAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled; // kept for future use

    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> buffCooldowns = new ConcurrentHashMap<>();

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.wildernessRevertEnabled = plugin.cfg().raw().getBoolean("wilderness_revert.enabled", false);
    }

    // --------------------------------------------------
    // COMPATIBILITY (external protection plugins)
    // --------------------------------------------------

    /**
     * If another protection plugin claims/controls this location, AegisGuard yields.
     * This prevents “double-cancels”, conflicting behavior, and message spam.
     */
    private boolean shouldYieldToExternalProtection(Location loc, Player actor, HookAction action) {
        try {
            if (plugin.protectionHooks() != null) {
                return plugin.protectionHooks().shouldBypass(loc, actor, action);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // --------------------------------------------------
    // LANGUAGE (Codex helper)
    // --------------------------------------------------

    private String tr(Player player, String key, String fallback) {
        try {
            if (plugin.codex() != null) {
                String value = plugin.codex().tr(player, key);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    // --------------------------------------------------
    // FLAG HELPERS (sync with Codex semantics)
    // --------------------------------------------------

    private boolean isProtectionActive(Plot plot, String flagKey, boolean defaultValue) {
        if (plot == null || flagKey == null) {
            return false;
        }

        String key = flagKey.toLowerCase();

        boolean effectiveDefault = defaultValue;

        if (plot.isServerZone() || plot.getFlag("safe_zone", false)) {
            switch (key) {
                case "pvp":
                case "mobs":
                case "animals":
                case "containers":
                case "piston-use":
                case "farm":
                case "redstone":
                case "vehicles":
                case "tnt-damage":
                case "fire-spread":
                case "explosions":
                    effectiveDefault = true;
                    break;
            }
        }

        return plot.getFlag(key, effectiveDefault);
    }

    public boolean isFlagEnabled(Plot plot, String flagKey) {
        if (plot == null || flagKey == null) {
            return false;
        }

        String key = flagKey.toLowerCase();
        boolean defaultValue;

        switch (key) {
            case "pvp":
            case "animals":
                defaultValue = true;
                break;
            default:
                defaultValue = false;
                break;
        }

        return isProtectionActive(plot, key, defaultValue);
    }

    public boolean isMobProtectionEnabled(Plot plot) {
        return isProtectionActive(plot, "mobs", false);
    }

    public boolean isSafeZoneEnabled(Plot plot) {
        return plot != null && plot.getFlag("safe_zone", false);
    }

    public void toggleSafeZone(Plot plot, boolean enabled) {
        if (plot == null) {
            return;
        }
        plot.setFlag("safe_zone", enabled);
        plugin.store().savePlot(plot);
    }

    // --------------------------------------------------
    // MOB SPAWNING & TARGETING
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Monster
                || e.getEntity() instanceof Slime
                || e.getEntity() instanceof Phantom)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(e.getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(e.getLocation(), null, HookAction.MOB_SPAWN)) {
            return;
        }

        if (isMobProtectionEnabled(plot)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player player)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(player.getLocation(), player, HookAction.MOB_TARGET)) {
            return;
        }

        if (isMobProtectionEnabled(plot)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }

        Entity damager = e.getDamager();
        Entity source = damager;

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        if (!(source instanceof Monster || source instanceof Slime || source instanceof Phantom)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) {
            return;
        }

        if (shouldYieldToExternalProtection(victim.getLocation(), victim, HookAction.MOB_DAMAGE_PLAYER)) {
            return;
        }

        if (isMobProtectionEnabled(plot)) {
            e.setCancelled(true);
            plugin.effects().playEffect("mobs", "deny", victim, victim.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamageAnimal(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();

        if (!(target instanceof Animals) && !(target instanceof Tameable)) {
            return;
        }

        Entity damager = e.getDamager();
        Entity source = damager;

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        if (!(source instanceof Monster || source instanceof Slime || source instanceof Phantom)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(target.getLocation());
        if (plot == null) {
            return;
        }

        if (shouldYieldToExternalProtection(target.getLocation(), null, HookAction.MOB_DAMAGE_ANIMAL)) {
            return;
        }

        if (isProtectionActive(plot, "animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", null, target.getLocation());
        }
    }

    // --------------------------------------------------
    // PLAYER MOVEMENT (ENTER / LEAVE)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        Plot from = plugin.store().getPlotAt(e.getFrom());
        Plot to = plugin.store().getPlotAt(e.getTo());

        if (from != null && !from.equals(to)) {
            Bukkit.getPluginManager().callEvent(new PlotLeaveEvent(from, p));

            if (!from.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, from.getFarewellMessage());
            }

            if (from.getFlag("fly", false) && !plugin.isAdmin(p)) {
                plugin.runMain(p, () -> {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.setFallDistance(0);
                });
            }

            clearPlotBuffs(p, from);
        }

        if (to != null && !to.equals(from)) {
            PlotEnterEvent enter = new PlotEnterEvent(to, p);
            Bukkit.getPluginManager().callEvent(enter);
            if (enter.isCancelled()) {
                e.setCancelled(true);
                return;
            }

            if (!to.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, to.getWelcomeMessage());
            }

            if (to.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, to.getEntryEffect(), to.getCenter(plugin));
            }

            if (to.getFlag("fly", false) && to.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                plugin.runMain(p, () -> p.setAllowFlight(true));
            }
        }

        if (to != null) {
            if (to.isBanned(p.getUniqueId())) {
                e.setCancelled(true);
                String bannedMsg = tr(
                        p,
                        "plot_banned_entry",
                        "&c⛔ You are banned from entering this claim."
                );
                sendPlotMessage(p, bannedMsg);
                return;
            }

            if (!to.getFlag("entry", true) && !to.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                e.setCancelled(true);
                String deniedMsg = tr(
                        p,
                        "plot_entry_denied",
                        "&c⛔ Entry denied. This claim is private."
                );
                sendPlotMessage(p, deniedMsg);
                return;
            }

            applyPlotBuffs(p, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        clearPlotBuffs(p, plot);
        buffCooldowns.remove(p.getUniqueId());
        messageCooldowns.remove(p.getUniqueId());
    }

    // --------------------------------------------------
    // COMBAT (PVP)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) {
            return;
        }

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) {
            return;
        }
        if (plugin.isAdmin(attacker)) {
            return;
        }

        if (shouldYieldToExternalProtection(victim.getLocation(), attacker, HookAction.PVP)) {
            return;
        }

        if (isProtectionActive(plot, "pvp", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
        }
    }

    // --------------------------------------------------
    // ANIMALS (INCLUDING TAMEABLE PETS)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalDamage(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();

        if (!(target instanceof Animals) && !(target instanceof Tameable)) {
            return;
        }

        Player p = resolveAttacker(e.getDamager());
        if (p == null || plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(target.getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(target.getLocation(), p, HookAction.ANIMAL_DAMAGE)) {
            return;
        }

        if (isProtectionActive(plot, "animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", p, target.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();

        if (!(clicked instanceof Animals) && !(clicked instanceof Tameable)) {
            return;
        }

        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(clicked.getLocation(), p, HookAction.ANIMAL_INTERACT)) {
            return;
        }

        if (isProtectionActive(plot, "animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", p, clicked.getLocation());
        }
    }

    // --------------------------------------------------
    // REDSTONE INTERACTION
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstoneInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) {
            return;
        }

        Material type = e.getClickedBlock().getType();
        boolean redstone =
                type.name().contains("BUTTON") ||
                        type.name().contains("LEVER") ||
                        type.name().contains("PRESSURE_PLATE") ||
                        type.name().contains("DOOR") ||
                        type.name().contains("TRAPDOOR");

        if (!redstone) {
            return;
        }

        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(e.getClickedBlock().getLocation(), p, HookAction.REDSTONE_INTERACT)) {
            return;
        }

        if (isProtectionActive(plot, "redstone", false)) {
            e.setCancelled(true);
            plugin.effects().playEffect("redstone", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    // --------------------------------------------------
    // VEHICLES
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (!(e.getEntered() instanceof Player p)) {
            return;
        }
        if (plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(e.getVehicle().getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(e.getVehicle().getLocation(), p, HookAction.VEHICLE_ENTER)) {
            return;
        }

        if (isProtectionActive(plot, "vehicles", false)) {
            e.setCancelled(true);
            plugin.effects().playEffect("vehicles", "deny", p, e.getVehicle().getLocation());
        }
    }

    // --------------------------------------------------
    // BUFFS
    // --------------------------------------------------

    private void applyPlotBuffs(Player p, Plot plot) {
        if (!plugin.cfg().isLevelingEnabled()) return;

        long now = System.currentTimeMillis();
        if (buffCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;
        if (!plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) return;

        for (int i = 1; i <= plot.getLevel(); i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (!reward.startsWith("EFFECT:")) continue;
                try {
                    String[] parts = reward.split(":");
                    if (parts.length < 3) continue;
                    PotionEffectType type = PotionEffectType.getByName(parts[1]);
                    int amp = Integer.parseInt(parts[2]) - 1;
                    if (type != null) {
                        p.addPotionEffect(new PotionEffect(
                                type,
                                100,
                                amp,
                                true,
                                false,
                                false
                        ));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        buffCooldowns.put(p.getUniqueId(), now + 2000);
    }

    private void clearPlotBuffs(Player p, Plot plot) {
        if (!plugin.cfg().isLevelingEnabled() || plot == null) return;

        for (int i = 1; i <= plot.getLevel(); i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (!reward.startsWith("EFFECT:")) continue;
                try {
                    String[] parts = reward.split(":");
                    if (parts.length < 2) continue;
                    PotionEffectType type = PotionEffectType.getByName(parts[1]);
                    if (type != null) {
                        p.removePotionEffect(type);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    // --------------------------------------------------
    // UTIL
    // --------------------------------------------------

    private void sendPlotMessage(Player p, String msg) {
        if (msg == null || msg.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (messageCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;

        plugin.runMain(p, () -> p.sendMessage(plugin.msg().color(msg)));
        messageCooldowns.put(p.getUniqueId(), now + TimeUnit.SECONDS.toMillis(5));
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
