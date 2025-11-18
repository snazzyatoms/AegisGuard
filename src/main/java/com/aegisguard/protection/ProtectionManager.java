package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import com.aegisguard.data.PlotStore.Plot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace; // --- NEW IMPORT ---
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // --- NEW IMPORT ---
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent; // --- NEW IMPORT ---
import org.bukkit.event.block.BlockPistonExtendEvent; // --- NEW IMPORT ---
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent; // --- NEW IMPORT ---
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList; // --- NEW IMPORT ---
import java.util.List;

/**
 * ProtectionManager (AegisGuard)
 * ---------------------------------------------
 * Enforces per-plot flags:
 * - pvp, containers, mobs, pets, entities, farm
 * - safe_zone (master switch: if true, all protections apply)
 *
 * --- UPGRADE NOTES (Roles) ---
 * - CRITICAL: Replaced all `canBuild(p, plot)` calls with the new
 * granular `plot.hasPermission(uuid, "PERMISSION", plugin)` system.
 * - This file now correctly uses the new Role-Based-Access-Control (RBAC).
 *
 * --- UPGRADE NOTES (Advanced Protections) ---
 * - Added new handlers for TNT/Creeper explosions (onEntityExplode).
 * - Added new handler for Fire Spread (onBlockIgnite).
 * - Added new handler for Piston Griefing (onBlockPistonExtend).
 * - Added new toggle methods for these flags.
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------------------------------
     * EVENT HANDLERS — Build & Interact
     * --- HEAVILY MODIFIED ---
     * ----------------------------------------------------- */

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        // --- MODIFIED ---
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_break"));
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        // --- MODIFIED ---
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_place"));
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

        // --- MODIFIED ---
        // Check for container permission
        if (isContainer(block.getType())) {
            if (enabled(plot, "containers") && !plot.hasPermission(p.getUniqueId(), "CONTAINERS", plugin)) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("containers", "deny", p, block.getLocation());
            }
        // Check for basic interact permission (doors, buttons, etc)
        } else if (isInteractable(block.getType())) {
            if (enabled(plot, "interact") && !plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                 e.setCancelled(true);
                 p.sendMessage(plugin.msg().get("cannot_interact"));
                 plugin.effects().playEffect("interact", "deny", p, block.getLocation());
            }
        }
    }

    /* -----------------------------------------------------
     * PVP & Entity Protections
     * --- HEAVILY MODIFIED ---
     * ----------------------------------------------------- */

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return; // No self-harm

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) return;

        if (enabled(plot, "pvp")) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_attack"));
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Tameable pet)) return;

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        
        // Prevent player from harming their own pet
        if (pet.getOwner() != null && pet.getOwner().getUniqueId().equals(attacker.getUniqueId())) {
            return; 
        }

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;

        // --- MODIFIED ---
        if (enabled(plot, "pets") && !plot.hasPermission(attacker.getUniqueId(), "PET_DAMAGE", plugin)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_interact")); // reuse localized denial
            plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (!(clicked instanceof ArmorStand
                || clicked instanceof ItemFrame
                || clicked instanceof GlowItemFrame
                || clicked instanceof Painting)) {
            return;
        }

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(clicked.getLocation());
        if (plot == null) return;

        // --- MODIFIED ---
        // Protect decorative entities for non-role players
        if (enabled(plot, "entities") && !plot.hasPermission(p.getUniqueId(), "ENTITY_INTERACT", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_interact"));
            plugin.effects().playEffect("entities", "deny", p, clicked.getLocation());
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) return;

        if (enabled(plot, "mobs") && e.getEntity() instanceof Monster) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;

        Plot plot = plugin.store().getPlotAt(e.getLocation());
        if (plot == null) return;

        if (enabled(plot, "mobs")) {
            e.setCancelled(true);
        }
    }

    /* -----------------------------------------------------
     * Crop / Farm Protections
     * --- HEAVILY MODIFIED ---
     * ----------------------------------------------------- */

    @EventHandler
    public void onFarmTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.FARMLAND) return;

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot == null) return;

        // --- MODIFIED ---
        if (enabled(plot, "farm") && !plot.hasPermission(p.getUniqueId(), "FARM_TRAMPLE", plugin)) {
            e.setCancelled(true);
            // Don't send a message, it's too spammy
            plugin.effects().playEffect("farm", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    /* -----------------------------------------------------
     * --- NEW: Advanced Protections ---
     * ----------------------------------------------------- */

    /**
     * Prevents blocks from being destroyed by explosions (TNT, Creepers)
     * if the 'tnt-damage' flag is enabled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        // Iterate over a copy of the list to avoid errors while removing
        for (Block block : new ArrayList<>(e.blockList())) {
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot != null && enabled(plot, "tnt-damage")) {
                // Remove this block from the list of blocks to be destroyed
                e.blockList().remove(block);
            }
        }
    }

    /**
     * Prevents fire from spreading or starting in a claim
     * if the 'fire-spread' flag is enabled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        // We only care about fire spread, not players using flint/steel
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            if (enabled(plot, "fire-spread")) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * Prevents pistons from pushing blocks out of or pulling blocks into
     * a protected claim, if the 'piston-use' flag is enabled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent e) {
        Plot pistonPlot = plugin.store().getPlotAt(e.getBlock().getLocation());
        BlockFace direction = e.getDirection();

        for (Block block : e.getBlocks()) {
            // Get the location this block will be *moved to*
            Location targetLoc = block.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ());
            Plot targetPlot = plugin.store().getPlotAt(targetLoc);

            // Case 1: Piston is OUTSIDE, pushing IN
            if (pistonPlot == null && targetPlot != null) {
                if (enabled(targetPlot, "piston-use")) {
                    e.setCancelled(true);
                    return;
                }
            }
            // Case 2: Piston is INSIDE, pushing OUT
            else if (pistonPlot != null && targetPlot == null) {
                if (enabled(pistonPlot, "piston-use")) {
                    e.setCancelled(true);
                    return;
                }
            }
            // Case 3: Piston is INSIDE, pushing INSIDE (different plot)
            else if (pistonPlot != null && targetPlot != null && !pistonPlot.equals(targetPlot)) {
                 if (enabled(pistonPlot, "piston-use") || enabled(targetPlot, "piston-use")) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }


    /* -----------------------------------------------------
     * FLAG API (used by Settings GUI)
     * --- MODIFIED ---
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


    // --- NEW: Toggles for Advanced Protections ---
    public boolean isTntDamageEnabled(Plot plot) { return hasFlag(plot, "tnt-damage"); }
    public void    toggleTntDamage(Plot plot)    { toggleFlag(plot, "tnt-damage"); }
    
    public boolean isFireSpreadEnabled(Plot plot) { return hasFlag(plot, "fire-spread"); }
    public void    toggleFireSpread(Plot plot)    { toggleFlag(plot, "fire-spread"); }
    
    public boolean isPistonUseEnabled(Plot plot) { return hasFlag(plot, "piston-use"); }
    public void    togglePistonUse(Plot plot)    { toggleFlag(plot, "piston-use"); }


    /* -----------------------------------------------------
     * HELPERS
     * ----------------------------------------------------- */

    /** Master enable: true if safe_zone OR the flag itself is true. */
    private boolean enabled(Plot plot, String flag) {
        // "safe_zone" defaults to TRUE in the plot object
        return plot.getFlag("safe_zone", true) || plot.getFlag(flag, true);
    }

    // --- REMOVED ---
    // private boolean canBuild(Player p, Plot plot) { ... }
    // (This is now replaced by the plot.hasPermission() logic)

    /** Checks if a plot has a flag enabled. */
    private boolean hasFlag(Plot plot, String flag) {
        return plot != null && enabled(plot, flag);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player dp) return dp;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player sp) return sp;
        }
        return null;
    }

    private boolean isContainer(Material type) {
        // Cover common inventories + all colored shulker boxes
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
    
    // --- NEW ---
    private boolean isInteractable(Material type) {
        String name = type.name();
        if (name.endsWith("_DOOR") || name.endsWith("_GATE") || name.endsWith("_BUTTON") || type == Material.LEVER || type == Material.DAYLIGHT_DETECTOR) {
            return true;
        }
        return false;
    }

    /* -----------------------------------------------------
     * playEffect(...)
     * --- REMOVED ---
     * (Moved to new EffectUtil.java class)
     * ----------------------------------------------------- */
}
