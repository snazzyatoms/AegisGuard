package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot; // --- FIX: Correct import (using standalone Plot) ---
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent; // --- NEW IMPORT ---
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ProtectionManager (AegisGuard)
 * ---------------------------------------------
 * Enforces per-plot flags and coordinates event flow.
 *
 * --- UPGRADE NOTES (Final Sync) ---
 * - Handles Server Zones: Checks global flags if owner is SERVER_OWNER_UUID.
 * - Handles Plot Status: Prevents interaction on EXPIRED/AUCTIONED plots.
 * - Handles Welcome/Farewell messages on PlayerMoveEvent.
 * - Handles Wilderness Revert logging (if enabled in config).
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled; // For logging changes
    private final Map<UUID, UUID> lastPlotMap = new ConcurrentHashMap<>(); // PlayerUUID -> LastPlotID
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.wildernessRevertEnabled = plugin.cfg().raw().getBoolean("wilderness_revert.enabled", false);
    }

    /* -----------------------------------------------------
     * PLOT MOVEMENT & MESSAGES (Welcome/Farewell/Ambient)
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Only check when crossing a block boundary
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockY() == e.getTo().getBlockY() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        Plot plotTo = plugin.store().getPlotAt(e.getTo());
        Plot plotFrom = plugin.store().getPlotAt(e.getFrom());

        // --- Handle Ambient Particles ---
        handleAmbientParticles(p, plotTo);

        // --- Plot Entry/Exit Logic ---
        if (plotTo != null && !plotTo.equals(plotFrom)) {
            // Entered a new plot
            if (!plotTo.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, plotTo.getWelcomeMessage(), plotTo.getOwnerName(), "welcome");
            }
            // Check for entry effects
            if (plotTo.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, plotTo.getEntryEffect(), plotTo.getCenter(plugin));
            }
        }
        
        if (plotFrom != null && !plotFrom.equals(plotTo)) {
            // Left a plot
            if (!plotFrom.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, plotFrom.getFarewellMessage(), plotFrom.getOwnerName(), "farewell");
            }
        }
    }

    private void handleAmbientParticles(Player p, Plot plot) {
        // To be implemented: A repeating task should check for ambient particles.
        // For now, this is a placeholder check.
    }
    
    private void sendPlotMessage(Player p, String message, String ownerName, String type) {
        if (message == null || message.isBlank()) return;

        long currentTime = System.currentTimeMillis();
        // Cooldown: only allow a message every 5 seconds per player to prevent spam
        if (messageCooldowns.getOrDefault(p.getUniqueId(), 0L) > currentTime) {
            return;
        }

        plugin.runMain(p, () -> {
            p.sendMessage(plugin.msg().color("&6[Plot] " + message));
            messageCooldowns.put(p.getUniqueId(), currentTime + TimeUnit.SECONDS.toMillis(5));
        });
    }


    /* -----------------------------------------------------
     * EVENT HANDLERS — Build & Interact
     * --- MODIFIED (Wilderness Revert & Server Zones) ---
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());

        // --- NEW: Wilderness Revert Log (Runs on success) ---
        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            
            plugin.runGlobalAsync(() -> {
                plugin.store().logWildernessBlock(loc, oldMat, Material.AIR.toString(), uuid);
            });
        }
        
        if (plot == null) return;
        
        if (p.hasPermission("aegis.admin")) return;

        // --- Server Zone Check (Global Rules) ---
        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) { // Check "build" flag, default FALSE
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_break"));
                plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
            }
            return;
        }
        
        // --- Player Plot Logic (Status & Roles) ---
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }

        // Check Role Permission
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_break"));
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());

        // --- NEW: Wilderness Revert Log (Runs on success) ---
        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = e.getBlockReplacedState().getType().toString();
            final String newMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            
            plugin.runGlobalAsync(() -> {
                plugin.store().logWildernessBlock(loc, oldMat, newMat, uuid);
            });
        }
        
        if (plot == null) return;
        
        if (p.hasPermission("aegis.admin")) return;
        
        // --- Server Zone Check (Global Rules) ---
        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) { // Check "build" flag, default FALSE
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_place"));
                plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
            }
            return;
        }

        // --- Player Plot Logic (Status & Roles) ---
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }
        
        // Check Role Permission
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_place"));
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Player p = e.getPlayer();
        Block block = e.getClickedBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());
        if (plot == null) return;
        
        if (p.hasPermission("aegis.admin")) return;
        
        // --- NEW: Status Check ---
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }
        
        // --- Server Zone Logic ---
        if (plot.isServerZone()) {
            if (isContainer(block.getType())) {
                if (!plot.getFlag("containers", false)) { // Check "containers" flag
                    e.setCancelled(true);
                    p.sendMessage(plugin.msg().get("cannot_interact"));
                    plugin.effects().playEffect("containers", "deny", p, block.getLocation());
                }
            } else if (isInteractable(block.getType())) {
                if (!plot.getFlag("interact", true)) { // Check "interact" flag
                    e.setCancelled(true);
                    p.sendMessage(plugin.msg().get("cannot_interact"));
                    plugin.effects().playEffect("interact", "deny", p, block.getLocation());
                }
            }
            return;
        }

        // --- Player Plot Logic (Roles) ---
        if (isContainer(block.getType())) {
            if (enabled(plot, "containers") && !plot.hasPermission(p.getUniqueId(), "CONTAINERS", plugin)) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("containers", "deny", p, block.getLocation());
            }
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
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) return;
        
        if (attacker.hasPermission("aegis.admin")) return;

        // --- NEW: Status Check ---
        if (checkPlotStatus(attacker, plot) || checkPlotStatus(victim, plot)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(attacker);
            return;
        }

        // --- Server Zone Check (Global Rules) ---
        if (plot.isServerZone()) {
            if (!plot.getFlag("pvp", false)) { // Check "pvp" flag
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_attack"));
                plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
            }
            return;
        }

        // --- Player Plot Logic ---
        if (enabled(plot, "pvp")) { // Player plot PvP flag
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_attack"));
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Tameable pet)) return;

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        if (pet.getOwner() != null && pet.getOwner().getUniqueId().equals(attacker.getUniqueId())) {
            return; 
        }

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;

        if (attacker.hasPermission("aegis.admin")) return;
        
        // --- NEW: Status Check ---
        if (checkPlotStatus(attacker, plot)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(attacker);
            return;
        }
        
        // --- Server Zone Check (Global Rules) ---
        if (plot.isServerZone()) {
            if (!plot.getFlag("pets", false)) { // Check "pets" flag
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
            }
            return;
        }

        // --- Player Plot Logic ---
        if (enabled(plot, "pets") && !plot.hasPermission(attacker.getUniqueId(), "PET_DAMAGE", plugin)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_interact"));
            plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        
        if (p.hasPermission("aegis.admin")) return;

        // --- NEW: Status Check ---
        if (checkPlotStatus(p, plot)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return;
        }

        // --- Server Zone Check (Global Rules) ---
        if (plot.isServerZone()) {
            if (!plot.getFlag("entities", true)) { // Check "entities" flag
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("entities", "deny", p, clicked.getLocation());
            }
            return;
        }

        // --- Player Plot Logic ---
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
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.FARMLAND) return;

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot == null) return;

        if (p.hasPermission("aegis.admin")) return;
        
        // --- NEW: Status Check ---
        if (checkPlotStatus(p, plot)) {
            e.setCancelled(true);
            return; // Don't spam message for trampling
        }
        
        // --- Server Zone Check (Global Rules) ---
        if (plot.isServerZone()) {
            if (!plot.getFlag("farm", true)) { // Check "farm" flag
                e.setCancelled(true);
            }
            return;
        }

        if (enabled(plot, "farm") && !plot.hasPermission(p.getUniqueId(), "FARM_TRAMPLE", plugin)) {
            e.setCancelled(true);
            plugin.effects().playEffect("farm", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    /* -----------------------------------------------------
     * Advanced Protections (TNT, Fire, Piston)
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        // Iterate over a copy of the list to avoid errors while removing
        for (Block block : new ArrayList<>(e.blockList())) {
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot == null) continue;
            
            // --- MODIFIED ---
            // Check server zone OR player zone flag
            String flag = "tnt-damage";
            if (plot.isServerZone()) {
                if (!plot.getFlag(flag, false)) {
                    e.blockList().remove(block);
                }
            } else if (enabled(plot, flag)) {
                e.blockList().remove(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        // We only care about fire spread, not players using flint/steel
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            
            // --- MODIFIED ---
            String flag = "fire-spread";
            if (plot.isServerZone()) {
                if (!plot.getFlag(flag, false)) {
                    e.setCancelled(true);
                }
            } else if (enabled(plot, flag)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent e) {
        Plot pistonPlot = plugin.store().getPlotAt(e.getBlock().getLocation());
        BlockFace direction = e.getDirection();

        for (Block block : e.getBlocks()) {
            // Get the location this block will be *moved to*
            Location targetLoc = block.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ());
            Plot targetPlot = plugin.store().getPlotAt(targetLoc);

            // --- MODIFIED ---
            String flag = "piston-use";
            boolean pistonPlotBlocked = pistonPlot != null && (pistonPlot.isServerZone() ? !pistonPlot.getFlag(flag, false) : enabled(pistonPlot, flag));
            boolean targetPlotBlocked = targetPlot != null && (targetPlot.isServerZone() ? !targetPlot.getFlag(flag, false) : enabled(targetPlot, flag));

            // Case 1: Piston OUTSIDE, pushing IN
            if (pistonPlot == null && targetPlotBlocked) {
                e.setCancelled(true); return;
            }
            // Case 2: Piston INSIDE, pushing OUT
            else if (pistonPlotBlocked && targetPlot == null) {
                e.setCancelled(true); return;
            }
            // Case 3: Piston INSIDE, pushing INSIDE (different plot)
            else if (pistonPlot != null && targetPlot != null && !pistonPlot.equals(targetPlot)) {
                 if (pistonPlotBlocked || targetPlotBlocked) {
                    e.setCancelled(true); return;
                }
            }
        }
    }


    /* -----------------------------------------------------
     * FLAG API (used by PlotFlagsGUI)
     * ----------------------------------------------------- */

    /**
     * Toggles a flag for a specific plot.
     * This is now ASYNC-SAFE and does not cause lag.
     */
    private void toggleFlag(Plot plot, String flag) {
        if (plot == null) return; // Should not happen if called from SettingsGUI

        boolean current = plot.getFlag(flag, true);
        plot.setFlag(flag, !current);

        plugin.store().setDirty(true); // This uses the async auto-saver.
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
}
