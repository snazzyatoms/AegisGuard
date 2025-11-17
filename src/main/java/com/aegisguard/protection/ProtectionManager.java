package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import com.aegisguard.data.PlotStore.Plot; // --- NEW IMPORT ---
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * ProtectionManager (AegisGuard)
 * ... (existing comments) ...
 *
 * --- UPGRADE NOTES ---
 * - CRITICAL LAG FIX: toggleFlag() no longer calls flushSync(). It now sets the
 * PlotStore's dirty flag to use the async auto-saver.
 * - CRITICAL DESIGN FIX: The Flag API (isPvPEnabled, togglePvP, etc.)
 * now operates on a Plot object, not a Player's location.
 * - CLEANUP: All effect/sound logic has been moved to EffectUtil.java
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------------------------------
     * EVENT HANDLERS — Build & Interact
     * ----------------------------------------------------- */

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (!canBuild(p, plot)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_break"));
            // --- MODIFIED ---
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (!canBuild(p, plot)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_place"));
            // --- MODIFIED ---
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Player p = e.getPlayer();
        Block block = e.getClickedBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());
        if (plot == null) return;

        // Container access is protected for non-trusted when flag active (or safe_zone)
        if (!canBuild(p, plot) && isContainer(block.getType()) && enabled(plot, "containers")) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_interact"));
            // --- MODIFIED ---
            plugin.effects().playEffect("containers", "deny", p, block.getLocation());
        }
    }

    /* -----------------------------------------------------
     * PVP & Entity Protections
     * ----------------------------------------------------- */

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) return;

        if (enabled(plot, "pvp")) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_attack"));
            // --- MODIFIED ---
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Tameable pet)) return;

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;

        if (enabled(plot, "pets")) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_interact")); // reuse localized denial
            // --- MODIFIED ---
            plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent e) {
// ... (existing logic for entity checks) ...
        if (!(clicked instanceof ArmorStand
                || clicked instanceof ItemFrame
                || clicked instanceof GlowItemFrame
                || clicked instanceof Painting)) {
            return;
        }

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null) return;

        // Protect decorative entities for non-trusted
        if (!canBuild(p, plot) && enabled(plot, "entities")) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_interact"));
            // --- MODIFIED ---
            plugin.effects().playEffect("entities", "deny", p, clicked.getLocation());
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
// ... (existing logic is fine) ...
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent e) {
// ... (existing logic is fine) ...
    }

    /* -----------------------------------------------------
     * Crop / Farm Protections
     * ----------------------------------------------------- */

    @EventHandler
    public void onFarmTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.FARMLAND) return;

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot == null) return;

        if (enabled(plot, "farm")) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_interact")); // localized, simple denial
            // --- MODIFIED ---
            plugin.effects().playEffect("farm", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    /* -----------------------------------------------------
     * FLAG API (used by Settings GUI)
     * --- HEAVILY MODIFIED ---
     * ----------------------------------------------------- */

    /**
     * Toggles a flag for a specific plot.
     * This is now ASYNC-SAFE and does not cause lag.
     */
    private void toggleFlag(Plot plot, String flag) {
        if (plot == null) return; // Should not happen if called from SettingsGUI

        boolean current = plot.getFlag(flag, true);
        plot.setFlag(flag, !current);

        // --- CRITICAL LAG FIX ---
        // plugin.store().flushSync(); // <-- REMOVED! This causes server lag.
        plugin.store().setDirty(true); // <-- ADDED! This uses the async auto-saver.

        // Feedback is handled by GUI refresh
    }

    // Exposed to SettingsGUI
    public boolean isPvPEnabled(Plot plot)     { return hasFlag(plot, "pvp"); }
    public void    togglePvP(Plot plot)        { toggleFlag(plot, "pvp"); }

    public boolean isContainersEnabled(Plot plot) { return hasFlag(plot, "containers"); }
    public void    toggleContainers(Plot plot)    { toggleFlag(plot, "containers"); }

    public boolean isMobProtectionEnabled(Plot plot)  { return hasFlag(plot, "mobs"); }
    public void    toggleMobProtection(Plot plot)     { toggleFlag(plot, "mobs"); }

    public boolean isPetProtectionEnabled(Plot plot)  { return hasFlag(plot, "pets"); }
    public void    togglePetProtection(Plot plot)     { toggleFlag(plot, "pets"); }

    public boolean isEntityProtectionEnabled(Plot plot){ return hasFlag(plot, "entities"); }
    public void    toggleEntityProtection(Plot plot)    { toggleFlag(plot, "entities"); }

    public boolean isFarmProtectionEnabled(Plot plot) { return hasFlag(plot, "farm"); }
    public void    toggleFarmProtection(Plot plot)    { toggleFlag(plot, "farm"); }

    public boolean isSafeZoneEnabled(Plot plot)     { return hasFlag(plot, "safe_zone"); }
    public void    toggleSafeZone(Plot plot, boolean playSound) {
        // This is a special toggle with extra logic
        if (plot == null) return;
        
        boolean next = !plot.getFlag("safe_zone", true);
        plot.setFlag("safe_zone", next);

        // When toggling Safe Zone ON, also ensure the individual protections are ON
        if (next) {
            plot.setFlag("pvp", true);
            plot.setFlag("mobs", true);
            plot.setFlag("containers", true);
            plot.setFlag("entities", true);
            plot.setFlag("pets", true);
            plot.setFlag("farm", true);
        }

        plugin.store().setDirty(true); // Async-safe save
    }


    /* -----------------------------------------------------
     * HELPERS
     * ----------------------------------------------------- */

    /** Master enable: true if safe_zone OR the flag itself is true. */
    private boolean enabled(Plot plot, String flag) {
        // --- MODIFIED --- "safe_zone" defaults to TRUE in the plot object
        return plot.getFlag("safe_zone", true) || plot.getFlag(flag, true);
    }

    private boolean canBuild(Player p, Plot plot) {
        if (p.hasPermission("aegis.admin")) return true; // unified admin perm
        if (p.getUniqueId().equals(plot.getOwner())) return true;
        return plot.getTrusted().contains(p.getUniqueId());
    }

    /** Checks if a plot has a flag enabled. */
    private boolean hasFlag(Plot plot, String flag) {
        return plot != null && enabled(plot, flag);
    }

    private Player resolveAttacker(Entity damager) {
// ... (existing logic is fine) ...
        if (damager instanceof Player dp) return dp;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player sp) return sp;
        }
        return null;
    }

    private boolean isContainer(Material type) {
// ... (existing logic is fine) ...
        if (type == Material.SHULKER_BOX || type.name().endsWith("_SHULKER_BOX")) return true;
        return switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL,
                 ENDER_CHEST,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 HOPPER, DROPPER, DISPENSER,
                 BREWING_STAND,
                 CHISELED_BOOKSHELF -> true;
            default -> false;
        };
    }

    /* -----------------------------------------------------
     * playEffect(...)
     * --- REMOVED ---
     * (Moved to new EffectUtil.java class)
     * ----------------------------------------------------- */
}
