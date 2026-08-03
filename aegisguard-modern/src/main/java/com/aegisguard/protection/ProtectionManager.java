package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.hooks.protection.HookAction;
import com.aegisguard.util.CompatParticle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProtectionManager implements Listener {
    private static final Set<String> EXPLICIT_HOSTILE_TYPES = Set.of(
            "BLAZE",
            "BOGGED",
            "BREEZE",
            "CAVE_SPIDER",
            "CREAKING",
            "CREEPER",
            "DROWNED",
            "ELDER_GUARDIAN",
            "ENDERMAN",
            "ENDERMITE",
            "ENDER_DRAGON",
            "EVOKER",
            "GHAST",
            "GIANT",
            "GUARDIAN",
            "HOGLIN",
            "HUSK",
            "ILLUSIONER",
            "MAGMA_CUBE",
            "PHANTOM",
            "PIGLIN",
            "PIGLIN_BRUTE",
            "PILLAGER",
            "RAVAGER",
            "SHULKER",
            "SILVERFISH",
            "SKELETON",
            "SLIME",
            "SPIDER",
            "STRAY",
            "VEX",
            "VINDICATOR",
            "WARDEN",
            "WITCH",
            "WITHER",
            "WITHER_SKELETON",
            "ZOGLIN",
            "ZOMBIE",
            "ZOMBIE_VILLAGER",
            "ZOMBIFIED_PIGLIN"
    );

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled; // kept for future use

    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mobCleanupCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingMobRemovals = new ConcurrentHashMap<>();

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
        if (plot == null) return false;
        if (!isMobBarrierEnabledForWorld(plot.getWorld())) return false;
        return plot.isServerZone() || isProtectionActive(plot, "mobs", false);
    }

    public boolean isMobBarrierEnabledForWorld(String worldName) {
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", true)) return false;
        if (worldName == null || worldName.isBlank()) return true;
        String path = "mob_barrier.per_world." + worldName + ".enabled";
        if (plugin.cfg().raw().isSet(path)) {
            return plugin.cfg().raw().getBoolean(path);
        }
        return true;
    }

    /**
     * Whether this entity should be affected by mob-barrier protection based on
     * hostile/passive/boss category toggles (defaults preserve hostile-only).
     */
    public boolean isProtectedMobCategory(Entity entity) {
        if (entity == null) return false;
        boolean protectHostile = plugin.cfg().raw().getBoolean("mob_barrier.protect_hostile", true);
        boolean protectPassive = plugin.cfg().raw().getBoolean("mob_barrier.protect_passive", false);
        boolean protectBoss = plugin.cfg().raw().getBoolean("mob_barrier.protect_boss", false);

        if (entity instanceof org.bukkit.entity.Boss || entity instanceof org.bukkit.entity.EnderDragon
                || entity instanceof org.bukkit.entity.Wither) {
            return protectBoss;
        }
        if (isHostileMob(entity)) {
            return protectHostile;
        }
        if (entity instanceof org.bukkit.entity.Animals || entity instanceof org.bukkit.entity.Ambient
                || entity instanceof org.bukkit.entity.WaterMob) {
            return protectPassive;
        }
        return false;
    }

    public String diagnoseMobProtection(Plot plot, Entity entity) {
        if (plot == null) return "no_plot";
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", true)) return "barrier_disabled";
        if (!isMobBarrierEnabledForWorld(plot.getWorld())) return "world_disabled:" + plot.getWorld();
        if (!isMobProtectionEnabled(plot)) return "plot_flag_off";
        if (entity != null && !isProtectedMobCategory(entity)) return "category_excluded:" + entity.getType();
        if (plot.isServerZone()) return "server_zone";
        return "plot_mobs_flag";
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
        if (!isHostileMob(e.getEntity())) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(e.getLocation());
        if (plot == null) return;

        if (shouldYieldToExternalProtection(e.getLocation(), null, HookAction.MOB_SPAWN)) {
            return;
        }

        if (isMobProtectionEnabled(plot)) {
            e.setCancelled(true);
            removeHostileMob(e.getEntity());
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
            if (isHostileMob(e.getEntity()) && plot.isInside(e.getEntity().getLocation())) {
                queueProtectedHostileRemoval(e.getEntity());
            }
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

        if (!isHostileMob(source)) {
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
            if (plot.isInside(source.getLocation())) {
                queueProtectedHostileRemoval(source);
            }
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

        if (!isHostileMob(source)) {
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
            if (plot.isInside(source.getLocation())) {
                queueProtectedHostileRemoval(source);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileTeleport(EntityTeleportEvent e) {
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", false)
                || !plugin.cfg().raw().getBoolean("mob_barrier.block_boundary_entry", true)
                || !isHostileMob(e.getEntity())
                || e.getTo() == null) {
            return;
        }

        Plot destination = plugin.store().getPlotAt(e.getTo());
        if (!isMobProtectionEnabled(destination)) {
            return;
        }

        Plot origin = plugin.store().getPlotAt(e.getFrom());
        if (!isSamePlot(origin, destination)) {
            e.setCancelled(true);
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
        }

        if (to != null && !to.equals(from)) {
            PlotEnterEvent enter = new PlotEnterEvent(to, p);
            Bukkit.getPluginManager().callEvent(enter);
            if (enter.isCancelled()) {
                e.setCancelled(true);
                return;
            }

            if (to.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, to.getEntryEffect(), to.getCenter(plugin));
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

            // Private plots deny entry unless the player has INTERACT trust OR this plot's
            // Alliance Entry toggle is ON and the player is a member of the joined alliance.
            // Alliance Entry defaults OFF — membership alone never opens a private plot.
            // Role-flag ENTRY overrides (Allow/Deny) beat the plot entry flag for that role.
            Boolean entryOverride = to.resolveRoleFlagOverride(p.getUniqueId(), "entry");
            boolean entryDenied = entryOverride != null
                    ? !entryOverride
                    : (!to.getFlag("entry", true)
                    && !to.hasPermission(p.getUniqueId(), "INTERACT", plugin)
                    && !to.allowsAllianceEntry(p.getUniqueId(), plugin));
            if (entryDenied) {
                e.setCancelled(true);
                String deniedMsg = tr(
                        p,
                        "plot_entry_denied",
                        "&c⛔ Entry denied. This claim is private."
                );
                sendPlotMessage(p, deniedMsg);
                return;
            }


            if (isMobProtectionEnabled(to)) {
                purgePlotHostilesForPlayer(p, to);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        messageCooldowns.remove(p.getUniqueId());
        mobCleanupCooldowns.remove(p.getUniqueId());
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

        Boolean pvpOverride = plot.resolveRoleFlagOverride(attacker.getUniqueId(), "pvp");
        if (pvpOverride != null) {
            if (!pvpOverride) {
                e.setCancelled(true);
                plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
            }
            return;
        }

        if (isProtectionActive(plot, "pvp", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
            return;
        }

        // Milestone 7: when plot PvP is open, Alliance Friendly PvP (default OFF) can still
        // cancel damage between members of the alliance this plot has joined.
        if (plot.areAllianceAllies(attacker.getUniqueId(), victim.getUniqueId(), plugin)) {
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

        Boolean animalsOverride = plot.resolveRoleFlagOverride(p.getUniqueId(), "animals");
        if (animalsOverride != null) {
            if (!animalsOverride) {
                e.setCancelled(true);
                plugin.effects().playEffect("animals", "deny", p, target.getLocation());
            }
            return;
        }
        if (isProtectionActive(plot, "animals", true)
                && !plot.hasPermission(p.getUniqueId(), "ANIMALS", plugin)) {
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

        Boolean animalsOverride = plot.resolveRoleFlagOverride(p.getUniqueId(), "animals");
        if (animalsOverride != null) {
            if (!animalsOverride) {
                e.setCancelled(true);
                plugin.effects().playEffect("animals", "deny", p, clicked.getLocation());
            }
            return;
        }
        if (isProtectionActive(plot, "animals", true)
                && !plot.hasPermission(p.getUniqueId(), "ANIMALS", plugin)) {
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

        if (isProtectionActive(plot, "redstone", false)
                && !plot.canInteractAt(p, e.getClickedBlock().getLocation(), plugin, "INTERACT")) {
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

        if (isProtectionActive(plot, "vehicles", false)
                && !plot.canInteractAt(p, e.getVehicle().getLocation(), plugin, "VEHICLES")) {
            e.setCancelled(true);
            plugin.effects().playEffect("vehicles", "deny", p, e.getVehicle().getLocation());
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

    public boolean isHostileMob(Entity entity) {
        if (entity == null) {
            return false;
        }

        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Phantom
                || EXPLICIT_HOSTILE_TYPES.contains(entity.getType().name());
    }

    private void removeHostileMob(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }

        try {
            if (plugin.cfg().raw().getBoolean("mob_barrier.remove_particles", true)) {
                Particle particle = CompatParticle.match("SMOKE_NORMAL");
                if (particle != null) {
                    int count = plugin.cfg().raw().getBoolean("mob_barrier.low_particle_mode", true) ? 2 : 5;
                    entity.getWorld().spawnParticle(
                            particle,
                            entity.getLocation().add(0, 1, 0),
                            count,
                            0.1, 0.1, 0.1,
                            0.05
                    );
                }
            }
            entity.remove();
        } catch (Throwable ignored) {
        }
    }

    public void queueProtectedHostileRemoval(Entity entity) {
        if (entity == null || !entity.isValid() || !isHostileMob(entity)) {
            return;
        }

        UUID entityId = entity.getUniqueId();
        long now = System.currentTimeMillis();
        long graceSeconds = Math.max(0L,
                plugin.cfg().raw().getLong("mob_barrier.despawn_grace_seconds", 5L));
        long removalAt = now + TimeUnit.SECONDS.toMillis(graceSeconds);
        AtomicBoolean scheduled = new AtomicBoolean(false);
        pendingMobRemovals.compute(entityId, (ignored, existingRemoval) -> {
            if (existingRemoval != null && existingRemoval > now) {
                return existingRemoval;
            }
            scheduled.set(true);
            return removalAt;
        });
        if (!scheduled.get()) {
            return;
        }

        if (graceSeconds == 0L) {
            pendingMobRemovals.remove(entityId, removalAt);
            removeHostileMob(entity);
            return;
        }

        plugin.runEntityLater(entity, () -> {
            if (!pendingMobRemovals.remove(entityId, removalAt)) {
                return;
            }
            if (!entity.isValid() || !isHostileMob(entity)) {
                return;
            }

            Plot current = plugin.store().getPlotAt(entity.getLocation());
            if (isMobProtectionEnabled(current)) {
                removeHostileMob(entity);
            }
        }, graceSeconds * 20L);
    }

    public void pruneExpiredMobRemovalTickets() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1L);
        pendingMobRemovals.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public boolean isSamePlot(Plot first, Plot second) {
        return first != null
                && second != null
                && first.getPlotId() != null
                && first.getPlotId().equals(second.getPlotId());
    }

    private void purgePlotHostilesForPlayer(Player player, Plot plot) {
        if (player == null || plot == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalSeconds = Math.max(1L, plugin.cfg().raw().getLong("mob_barrier.player_cleanup_interval_seconds", 2L));
        if (mobCleanupCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }

        mobCleanupCooldowns.put(player.getUniqueId(), now + TimeUnit.SECONDS.toMillis(intervalSeconds));
        purgePlotHostiles(plot);
    }

    private void purgePlotHostiles(Plot plot) {
        if (plot == null) {
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(plot.getWorld());
        if (world == null) {
            return;
        }

        int minChunkX = plot.getX1() >> 4;
        int minChunkZ = plot.getZ1() >> 4;
        int maxChunkX = plot.getX2() >> 4;
        int maxChunkZ = plot.getZ2() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }

                for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                    if (!isHostileMob(entity)) {
                        continue;
                    }
                    if (!plot.isInside(entity.getLocation())) {
                        continue;
                    }
                    queueProtectedHostileRemoval(entity);
                }
            }
        }
    }
}
