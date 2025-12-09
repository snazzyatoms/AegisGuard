package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
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
    // FLAG HELPERS (sync with messages.yml semantics)
    // --------------------------------------------------

    /**
     * Generic "is this protection ON" helper.
     *
     * Semantics (UPDATED):
     *  - Every flag is ultimately controlled by the plot's own flag value.
     *  - Safe zones / server zones only bias the DEFAULT to "ON" for important flags
     *    when no explicit flag has been set yet.
     *  - Once the player/admin toggles a flag in the GUI, that explicit value always wins.
     *
     * This matches the UI idea:
     *  - Green = protected / restricted
     *  - Red   = vulnerable / vanilla-like
     */
    private boolean isProtectionActive(Plot plot, String flagKey, boolean defaultValue) {
        if (plot == null || flagKey == null) {
            return false;
        }

        String key = flagKey.toLowerCase();

        // Start from the caller's default (e.g. pvp/animals default ON, others OFF)
        boolean effectiveDefault = defaultValue;

        // In server/safe zones, lean towards safety by default for important flags,
        // but do NOT hard-force them; explicit per-plot values still override.
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

        // Stored value (if present) always wins over the derived default.
        return plot.getFlag(key, effectiveDefault);
    }

    /**
     * Public helper used by GUIs / hooks to check a plot flag in a
     * protection-centric way.
     *
     * Returns true when the associated protection is ACTIVE, not when
     * the vanilla behavior is allowed.
     *
     * Examples:
     *  - PVP:     true  => PvP blocked, player is safe
     *  - Mobs:    true  => mob protection ON, hostile mobs restricted
     *  - Animals: true  => animals & pets protected from harm / interaction
     */
    public boolean isFlagEnabled(Plot plot, String flagKey) {
        if (plot == null || flagKey == null) {
            return false;
        }

        String key = flagKey.toLowerCase();
        boolean defaultValue;

        // Match in-world behavior:
        // - pvp & animals default to ON (protection enabled)
        // - everything else defaults to OFF unless explicitly set
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

    /**
     * Strongly typed helper used by mob-barrier / GUIs.
     * This is the single source of truth for "mob protection ON?"
     */
    public boolean isMobProtectionEnabled(Plot plot) {
        return isProtectionActive(plot, "mobs", false);
    }

    /**
     * Safe zone helper. Safe zones now primarily represent structural /
     * environmental protections (explosions, block damage, etc.).
     *
     * Combat protections like PVP / mobs are still individually controlled
     * by their own flags so players + server owners have proper choice.
     */
    public boolean isSafeZoneEnabled(Plot plot) {
        return plot != null && plot.getFlag("safe_zone", false);
    }

    /**
     * Toggles the safe zone flag for a plot and persists it.
     * Safe zones primarily protect structures & environment; they no longer
     * automatically force mob / pvp protection, which are now full-choice flags.
     */
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
        // Only interested in hostile-type mobs for this protection
        if (!(e.getEntity() instanceof Monster
                || e.getEntity() instanceof Slime
                || e.getEntity() instanceof Phantom)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(e.getLocation());

        // Mob protection ON => block hostile spawns in this dominion
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

        // Mob protection ON => hostile mobs cannot target players in this plot
        if (isMobProtectionEnabled(plot)) {
            e.setCancelled(true);
        }
    }

    // NEW: explicit damage guard so mobs cannot hurt you inside protected plots
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }

        // Resolve the TRUE mob source (handles projectiles)
        Entity damager = e.getDamager();
        Entity source = damager;

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        // Only care about hostile mobs here
        if (!(source instanceof Monster || source instanceof Slime || source instanceof Phantom)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) {
            return;
        }

        // Mob protection ON => no damage to players from hostile mobs in this plot
        if (isMobProtectionEnabled(plot)) {
            e.setCancelled(true);
            plugin.effects().playEffect("mobs", "deny", victim, victim.getLocation());
        }
    }

    // NEW: hostile mobs cannot damage animals or tameable pets when animals protection is ON
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamageAnimal(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();

        // Only care about animals & tameable pets
        if (!(target instanceof Animals) && !(target instanceof Tameable)) {
            return;
        }

        // Resolve mob source (handles projectiles)
        Entity damager = e.getDamager();
        Entity source = damager;

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        // Only care about hostile mobs here
        if (!(source instanceof Monster || source instanceof Slime || source instanceof Phantom)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(target.getLocation());
        if (plot == null) {
            return;
        }

        // Animals flag: true = animals (including tameables) protected
        if (isProtectionActive(plot, "animals", true)) {
            e.setCancelled(true);
            // No player to play the effect to, so just use the mob target location
            plugin.effects().playEffect("animals", "deny", null, target.getLocation());
        }
    }

    // --------------------------------------------------
    // PLAYER MOVEMENT (ENTER / LEAVE)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        // Only react when changing X/Z; ignore head movement / tiny jitters
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        Plot from = plugin.store().getPlotAt(e.getFrom());
        Plot to = plugin.store().getPlotAt(e.getTo());

        // --- Leaving plot ---
        if (from != null && !from.equals(to)) {
            Bukkit.getPluginManager().callEvent(new PlotLeaveEvent(from, p));

            if (!from.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, from.getFarewellMessage());
            }

            // Disable flight when leaving a fly-enabled plot (unless admin)
            if (from.getFlag("fly", false) && !plugin.isAdmin(p)) {
                plugin.runMain(p, () -> {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.setFallDistance(0);
                });
            }

            // NEW: clear any plot-level buffs when leaving this plot
            clearPlotBuffs(p, from);
        }

        // --- Entering plot (before entry checks so custom events still fire) ---
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

            // Flight flag matches messages.yml: ON => allow flight for trusted players
            if (to.getFlag("fly", false) && to.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                plugin.runMain(p, () -> p.setAllowFlight(true));
            }
        }

        // --- Entry / ban logic + buffs ---
        if (to != null) {
            // Banned from this plot
            if (to.isBanned(p.getUniqueId())) {
                e.setCancelled(true);
                sendPlotMessage(p, plugin.msg().get(p, "plot_banned_entry"));
                return;
            }

            // Entry flag: true = OPEN, false = CLOSED (for non-trusted)
            if (!to.getFlag("entry", true) && !to.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                e.setCancelled(true);
                sendPlotMessage(p, plugin.msg().get(p, "plot_entry_denied"));
                return;
            }

            applyPlotBuffs(p, to);
        }
    }

    // NEW: clear buffs on player quit so nothing lingers between sessions
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
            return; // not a player-sourced attack
        }
        if (plugin.isAdmin(attacker)) {
            return; // admins bypass PvP rules
        }

        // pvp flag: true = PvP protection ON (block PvP)
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

        // Livestock + tameable pets
        if (!(target instanceof Animals) && !(target instanceof Tameable)) {
            return;
        }

        Player p = resolveAttacker(e.getDamager());
        if (p == null || plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(target.getLocation());
        // animals flag: true = animals (and tameables) protected, false = vanilla
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
        // redstone flag: true = redstone / mechanisms blocked
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
        // vehicles flag: true = vehicles protected, false = vanilla
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
                                100,           // ~5 seconds, refreshed while in plot
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

    /**
     * Remove all plot-level buffs that could have been applied by this plot.
     * Called when a player leaves a plot or disconnects so effects don't linger.
     */
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
