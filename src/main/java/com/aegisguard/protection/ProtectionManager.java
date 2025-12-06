package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled;

    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> buffCooldowns = new ConcurrentHashMap<>();

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.wildernessRevertEnabled = plugin.cfg().raw().getBoolean("wilderness_revert.enabled", false);
    }

    // --------------------------------------------------
    // MOB SPAWNING & TARGETING
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Monster || e.getEntity() instanceof Slime || e.getEntity() instanceof Phantom))
            return;

        Plot plot = plugin.store().getPlotAt(e.getLocation());
        if (plot != null && (plot.isServerZone() || plot.getFlag("safe_zone", false) || !plot.getFlag("mobs", true))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot != null && plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
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
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Plot from = plugin.store().getPlotAt(e.getFrom());
        Plot to = plugin.store().getPlotAt(e.getTo());

        if (from != null && !from.equals(to)) {
            Bukkit.getPluginManager().callEvent(new PlotLeaveEvent(from, p));
            plugin.getSidebar().hideSidebar(p);

            if (!from.getOwner().equals(p.getUniqueId()))
                sendPlotMessage(p, from.getFarewellMessage());

            if (from.getFlag("fly", false) && !plugin.isAdmin(p)) {
                plugin.runMain(p, () -> {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                    p.setFallDistance(0);
                });
            }
        }

        if (to != null && !to.equals(from)) {
            PlotEnterEvent enter = new PlotEnterEvent(to, p);
            Bukkit.getPluginManager().callEvent(enter);
            if (enter.isCancelled()) {
                e.setCancelled(true);
                return;
            }

            plugin.getSidebar().showSidebar(p, to);

            if (!to.getOwner().equals(p.getUniqueId()))
                sendPlotMessage(p, to.getWelcomeMessage());

            if (to.getEntryEffect() != null)
                plugin.effects().playCustomEffect(p, to.getEntryEffect(), to.getCenter(plugin));

            if (to.getFlag("fly", false) && to.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                plugin.runMain(p, () -> p.setAllowFlight(true));
            }
        }

        if (to != null) {
            if (to.isBanned(p.getUniqueId())) {
                e.setCancelled(true);
                sendPlotMessage(p, plugin.msg().get(p, "plot_banned_entry"));
                return;
            }

            if (!to.getFlag("entry", true) && !to.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                e.setCancelled(true);
                sendPlotMessage(p, plugin.msg().get(p, "plot_entry_denied"));
                return;
            }

            applyPlotBuffs(p, to);
        }
    }

    // --------------------------------------------------
    // COMBAT (PVP + MOB DAMAGE)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Entity damager = e.getDamager();
        Plot plot = plugin.store().getPlotAt(victim.getLocation());

        if (plot != null && !plot.getFlag("pvp", false)) {
            Player attacker = resolveAttacker(damager);
            if (attacker != null && !plugin.isAdmin(attacker)) {
                e.setCancelled(true);
                plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
            }
        }
    }

    // --------------------------------------------------
    // ANIMALS (NEW)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Animals animal)) return;

        Player p = resolveAttacker(e.getDamager());
        if (p == null || plugin.isAdmin(p)) return;

        Plot plot = plugin.store().getPlotAt(animal.getLocation());
        if (plot != null && !plot.getFlag("animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", p, animal.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnimalInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Animals)) return;

        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) return;

        Plot plot = plugin.store().getPlotAt(e.getRightClicked().getLocation());
        if (plot != null && !plot.getFlag("animals", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("animals", "deny", p, e.getRightClicked().getLocation());
        }
    }

    // --------------------------------------------------
    // REDSTONE (NEW)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstoneInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Material type = e.getClickedBlock().getType();
        boolean redstone =
                type.name().contains("BUTTON") ||
                type.name().contains("LEVER") ||
                type.name().contains("PRESSURE_PLATE");

        if (!redstone) return;

        Player p = e.getPlayer();
        if (plugin.isAdmin(p)) return;

        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot != null && !plot.getFlag("redstone", true)) {
            e.setCancelled(true);
            plugin.effects().playEffect("redstone", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    // --------------------------------------------------
    // VEHICLES (NEW)
    // --------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (!(e.getEntered() instanceof Player p)) return;
        if (plugin.isAdmin(p)) return;

        Plot plot = plugin.store().getPlotAt(e.getVehicle().getLocation());
        if (plot != null && !plot.getFlag("vehicles", true)) {
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
                    PotionEffectType type = PotionEffectType.getByName(parts[1]);
                    int amp = Integer.parseInt(parts[2]) - 1;
                    if (type != null) {
                        p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, false));
                    }
                } catch (Exception ignored) {}
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
