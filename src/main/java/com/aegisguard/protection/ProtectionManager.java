package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * ProtectionManager (AegisGuard)
 * ---------------------------------------------
 * Enforces per-plot flags and coordinates event flow.
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled; 
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.wildernessRevertEnabled = plugin.cfg().raw().getBoolean("wilderness_revert.enabled", false);
    }

    /* -----------------------------------------------------
     * PLOT MOVEMENT & MESSAGES
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockY() == e.getTo().getBlockY() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        Plot plotTo = plugin.store().getPlotAt(e.getTo());
        Plot plotFrom = plugin.store().getPlotAt(e.getFrom());

        if (plotTo != null && !plotTo.equals(plotFrom)) {
            if (!plotTo.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, plotTo.getWelcomeMessage(), plotTo.getOwnerName(), "welcome");
            }
            if (plotTo.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, plotTo.getEntryEffect(), plotTo.getCenter(plugin));
            }
        }
        
        if (plotFrom != null && !plotFrom.equals(plotTo)) {
            if (!plotFrom.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, plotFrom.getFarewellMessage(), plotFrom.getOwnerName(), "farewell");
            }
        }
    }

    private void handleAmbientParticles(Player p, Plot plot) {
        // Placeholder for ambient particles
    }
    
    private void sendPlotMessage(Player p, String message, String ownerName, String type) {
        if (message == null || message.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
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
     * ----------------------------------------------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());

        // Wilderness Revert Log
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

        // Server Zone
        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_break"));
                plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
            }
            return;
        }
        
        // Status Check
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }

        // Role Permission
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

        // Wilderness Revert Log
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
        
        // Server Zone
        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) { 
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_place"));
                plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
            }
            return;
        }

        // Status Check
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }
        
        // Role Permission
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
        
        // Status Check
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }
        
        // Server Zone
        if (plot.isServerZone()) {
            if (isContainer(block.getType())) {
                if (!plot.getFlag("containers", false)) {
                    e.setCancelled(true);
                    p.sendMessage(plugin.msg().get("cannot_interact"));
                    plugin.effects().playEffect("containers", "deny", p, block.getLocation());
                }
            } else if (isInteractable(block.getType())) {
                if (!plot.getFlag("interact", true)) {
                    e.setCancelled(true);
                    p.sendMessage(plugin.msg().get("cannot_interact"));
                    plugin.effects().playEffect("interact", "deny", p, block.getLocation());
                }
            }
            return;
        }

        // Player Plot Logic
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

        // Status Check
        if (checkPlotStatus(attacker, plot) || checkPlotStatus(victim, plot)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(attacker);
            return;
        }

        // Server Zone
        if (plot.isServerZone()) {
            if (!plot.getFlag("pvp", false)) {
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_attack"));
                plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
            }
            return;
        }

        // Player Plot Logic
        if (enabled(plot, "pvp")) {
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
        
        // Status Check
        if (checkPlotStatus(attacker, plot)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(attacker);
            return;
        }
        
        // Server Zone
        if (plot.isServerZone()) {
            if (!plot.getFlag("pets", false)) {
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
            }
            return;
        }

        // Player Plot Logic
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

        // Status Check
        if (checkPlotStatus(p, plot)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return;
        }

        // Server Zone
        if (plot.isServerZone()) {
            if (!plot.getFlag("entities", true)) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("entities", "deny", p, clicked.getLocation());
            }
            return;
        }

        // Player Plot Logic
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
        
        // Status Check
        if (checkPlotStatus(p, plot)) {
            e.setCancelled(true);
            return; 
        }
        
        // Server Zone
        if (plot.isServerZone()) {
            if (!plot.getFlag("farm", true)) {
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
        for (Block block : new ArrayList<>(e.blockList())) {
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot == null) continue;
            
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

        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
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
            Location targetLoc = block.getLocation().add(direction.getModX(), direction.getModY(), direction.getModZ());
            Plot targetPlot = plugin.store().getPlotAt(targetLoc);

            String flag = "piston-use";
            boolean pistonPlotBlocked = pistonPlot != null && (pistonPlot.isServerZone() ? !pistonPlot.getFlag(flag, false) : enabled(pistonPlot, flag));
            boolean targetPlotBlocked = targetPlot != null && (targetPlot.isServerZone() ? !targetPlot.getFlag(flag, false) : enabled(targetPlot, flag));

            if (pistonPlot == null && targetPlotBlocked) {
                e.setCancelled(true); return;
            }
            else if (pistonPlotBlocked && targetPlot == null) {
                e.setCancelled(true); return;
            }
            else if (pistonPlot != null && targetPlot != null && !pistonPlot.equals(targetPlot)) {
                 if (pistonPlotBlocked || targetPlotBlocked) {
                    e.setCancelled(true); return;
                }
            }
        }
    }

    /* -----------------------------------------------------
     * STATUS & FLAG API
     * ----------------------------------------------------- */

    /**
     * CRITICAL FIX: This method checks if a plot is "locked" due to status.
     * Returns true if interaction should be BLOCKED.
     */
    public boolean checkPlotStatus(Player player, Plot plot) {
        if (plot == null) return false;
        
        // Admins override locks
        if (player.hasPermission("aegis.admin")) return false;
        
        // Active plots are not locked
        if (plot.getPlotStatus().equalsIgnoreCase("ACTIVE")) return false;
        
        // Expired or Auctioned plots are locked
        return true;
    }

    /**
     * CRITICAL FIX: Required for PlaceholderAPI expansion.
     * Wraps the internal check.
     */
    public boolean isFlagEnabled(Plot plot, String flag) {
        return hasFlag(plot, flag);
    }

    private void toggleFlag(Plot plot, String flag) {
        if (plot == null) return;
        boolean current = plot.getFlag(flag, true);
        plot.setFlag(flag, !current);
        plugin.store().setDirty(true);
    }

    // Exposed to SettingsGUI
    public boolean isPvPEnabled(Plot plot)         { return hasFlag(plot, "pvp"); }
    public void    togglePvP(Plot plot)            { toggleFlag(plot, "pvp"); }

    public boolean isContainersEnabled(Plot plot) { return hasFlag(plot, "containers"); }
    public void    toggleContainers(Plot plot)    { toggleFlag(plot, "containers"); }

    public boolean isMobProtectionEnabled(Plot plot)  { return hasFlag(plot, "mobs"); }
    public void    toggleMobProtection(Plot plot)     { toggleFlag(plot, "mobs"); }

    public boolean isPetProtectionEnabled(Plot plot)  { return hasFlag(plot, "pets"); }
    public void    togglePetProtection(Plot plot)     { toggleFlag(plot, "pets"); }

    public boolean isEntityProtectionEnabled(Plot plot){ return hasFlag(plot, "entities"); }
    public void    toggleEntityProtection(Plot plot)     { toggleFlag(plot, "entities"); }

    public boolean isFarmProtectionEnabled(Plot plot) { return hasFlag(plot, "farm"); }
    public void    toggleFarmProtection(Plot plot)    { toggleFlag(plot, "farm"); }

    public boolean isSafeZoneEnabled(Plot plot)      { return hasFlag(plot, "safe_zone"); }
    public void    toggleSafeZone(Plot plot, boolean playSound) {
        if (plot == null) return;
        
        boolean next = !plot.getFlag("safe_zone", true);
        plot.setFlag("safe_zone", next);

        if (next) {
            plot.setFlag("pvp", true);
            plot.setFlag("mobs", true);
            plot.setFlag("containers", true);
            plot.setFlag("entities", true);
            plot.setFlag("pets", true);
            plot.setFlag("farm", true);
        }
        plugin.store().setDirty(true);
    }

    public boolean isTntDamageEnabled(Plot plot) { return hasFlag(plot, "tnt-damage"); }
    public void    toggleTntDamage(Plot plot)    { toggleFlag(plot, "tnt-damage"); }
    
    public boolean isFireSpreadEnabled(Plot plot) { return hasFlag(plot, "fire-spread"); }
    public void    toggleFireSpread(Plot plot)    { toggleFlag(plot, "fire-spread"); }
    
    public boolean isPistonUseEnabled(Plot plot) { return hasFlag(plot, "piston-use"); }
    public void    togglePistonUse(Plot plot)    { toggleFlag(plot, "piston-use"); }


    /* -----------------------------------------------------
     * HELPERS
     * ----------------------------------------------------- */

    private boolean enabled(Plot plot, String flag) {
        return plot.getFlag("safe_zone", true) || plot.getFlag(flag, true);
    }

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
    
    private boolean isInteractable(Material type) {
        String name = type.name();
        if (name.endsWith("_DOOR") || name.endsWith("_GATE") || name.endsWith("_BUTTON") || type == Material.LEVER || type == Material.DAYLIGHT_DETECTOR) {
            return true;
        }
        return false;
    }
}
