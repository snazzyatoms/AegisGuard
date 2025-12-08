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
     * Semantics:
     *  - Server Zone  => always protected
     *  - Safe Zone    => always protected
     *  - Otherwise    => use the given flag + default
     *
     * This matches the UI: green = ON (more protection), red = OFF (vanilla).
     */
    private boolean isProtectionActive(Plot plot, String flagKey, boolean defaultValue) {
        if (plot == null) {
            return false;
        }
        if (plot.isServerZone()) {
            return true;
        }
        if (plot.getFlag("safe_zone", false)) {
            return true;
        }
        return plot.getFlag(flagKey, defaultValue);
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
        if (isProtectionActive(plot, "mobs", false)) {
            // mobs flag ON (or safe_zone / server_zone) => block hostile spawns
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player player)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (isProtectionActive(plot, "mobs", false)) {
            // Hostile mobs cannot target players in protected plots
            e.setCancelled(true);
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
            plugin.getSidebar().hideSidebar(p);

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
        }

        // --- Entering plot (before entry checks so custom events still fire) ---
        if (to != null && !to.equals(from)) {
            PlotEnterEvent enter = new PlotEnterEvent(to, p);
            Bukkit.getPluginManager().callEvent(enter);
            if (enter.isCancelled()) {
                e.setCancelled(true);
                return;
            }

            plugin.getSidebar().showSidebar(p, to);

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
    // ANIMALS
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Animals animal)) {
            return;
        }

        Player p = resolveAttacker(e.getDamager());
        if (p == null || plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(animal.getLocation());
        // animals flag: true = animals protected, false = vanilla
        if (isProtectionActive(plot, "animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", p, animal.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Animals)) {
            return;
        }

        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(e.getRightClicked().getLocation());
        if (isProtectionActive(plot, "animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", p, e.getRightClicked().getLocation());
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
                                100,           // 5 seconds, refreshed while in plot
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
