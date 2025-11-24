package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled; 
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.wildernessRevertEnabled = plugin.cfg().raw().getBoolean("wilderness_revert.enabled", false);
    }

    public boolean isFlagEnabled(Plot plot, String flag) {
        return hasFlag(plot, flag);
    }

    // --- 1. MOB SPAWN PREVENTION ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.getEntity() instanceof Monster || e.getEntity() instanceof Slime || e.getEntity() instanceof Phantom) {
            Plot plot = plugin.store().getPlotAt(e.getLocation());
            if (plot != null) {
                boolean isServer = plot.isServerZone();
                boolean mobsAllowed = !isMobProtectionEnabled(plot); // Logic Flip: If protection is enabled, mobs are disabled.

                if (isServer || !mobsAllowed) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // --- 2. MOVEMENT LOGIC ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Plot toPlot = plugin.store().getPlotAt(e.getTo());
        Plot fromPlot = plugin.store().getPlotAt(e.getFrom());

        // Welcome Messages
        if (toPlot != null && !toPlot.equals(fromPlot)) {
            if (!toPlot.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, toPlot.getWelcomeMessage(), toPlot.getOwnerName(), "welcome");
            }
            if (toPlot.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, toPlot.getEntryEffect(), toPlot.getCenter(plugin));
            }
        }
        if (fromPlot != null && !fromPlot.equals(toPlot)) {
            if (!fromPlot.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, fromPlot.getFarewellMessage(), fromPlot.getOwnerName(), "farewell");
            }
        }

        // Flight Leaving
        if (fromPlot != null && (toPlot == null || !toPlot.equals(fromPlot))) {
            if (fromPlot.getFlag("fly", false)) {
                if (!p.hasPermission("aegis.admin.bypass") && 
                    p.getGameMode() != org.bukkit.GameMode.CREATIVE && 
                    p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                    p.setFallDistance(0); 
                    p.sendMessage("§c🕊 Leaving flight zone.");
                }
            }
        }

        // Flight Entering
        if (toPlot != null && (fromPlot == null || !fromPlot.equals(toPlot))) {
            if (toPlot.getFlag("fly", false)) {
                if (toPlot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                    p.setAllowFlight(true);
                    p.sendMessage("§a🕊 Entering flight zone.");
                }
            }
        }

        // Entry & Bans
        if (toPlot != null) {
            if (p.hasPermission("aegis.admin.bypass")) return;

            if (toPlot.isBanned(p.getUniqueId())) {
                bouncePlayer(p, e);
                sendPlotMessage(p, plugin.msg().get(p, "plot_banned_entry"), "", "error");
                return;
            }

            boolean entryAllowed = toPlot.getFlag("entry", true);
            if (!entryAllowed) {
                if (!toPlot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                    bouncePlayer(p, e);
                    sendPlotMessage(p, plugin.msg().get(p, "plot_entry_denied"), "", "error");
                }
            }
        }
    }

    private void bouncePlayer(Player p, PlayerMoveEvent e) {
        e.setCancelled(true);
    }
    
    private void sendPlotMessage(Player p, String message, String ownerName, String type) {
        if (message == null || message.isEmpty()) return;
        long currentTime = System.currentTimeMillis();
        if (messageCooldowns.getOrDefault(p.getUniqueId(), 0L) > currentTime) return;

        plugin.runMain(p, () -> {
            p.sendMessage(plugin.msg().color("&6[Plot] " + message));
            messageCooldowns.put(p.getUniqueId(), currentTime + TimeUnit.SECONDS.toMillis(5));
        });
    }

    // --- 3. BLOCK BREAK ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());

        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            plugin.runGlobalAsync(() -> plugin.store().logWildernessBlock(loc, oldMat, Material.AIR.toString(), uuid));
        }
        
        if (plot == null) return;
        if (p.hasPermission("aegis.admin")) return;

        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_break"));
                plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
            }
            return;
        }
        
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }

        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_break"));
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    // --- 4. BLOCK PLACE ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(e.getBlock().getLocation());

        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = e.getBlockReplacedState().getType().toString();
            final String newMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            plugin.runGlobalAsync(() -> plugin.store().logWildernessBlock(loc, oldMat, newMat, uuid));
        }
        
        if (plot == null) return;
        if (p.hasPermission("aegis.admin")) return;
        
        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) { 
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_place"));
                plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
            }
            return;
        }

        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }
        
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg().get("cannot_place"));
            plugin.effects().playEffect("build", "deny", p, e.getBlock().getLocation());
        }
    }

    // --- 5. INTERACT ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        Block block = e.getClickedBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());
        if (plot == null) return;
        if (p.hasPermission("aegis.admin")) return;
        
        if (checkPlotStatus(p, plot)) { 
            e.setCancelled(true); 
            p.sendMessage(plugin.msg().get("plot-is-locked"));
            plugin.effects().playError(p);
            return; 
        }
        
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

        if (isContainer(block.getType())) {
            if (isContainersEnabled(plot) && !plot.hasPermission(p.getUniqueId(), "CONTAINERS", plugin)) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("containers", "deny", p, block.getLocation());
            }
        } else if (isInteractable(block.getType())) {
            if (hasFlag(plot, "interact") && !plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                 e.setCancelled(true);
                 p.sendMessage(plugin.msg().get("cannot_interact"));
                 plugin.effects().playEffect("interact", "deny", p, block.getLocation());
            }
        }
    }

    // --- 6. PVP & PROJECTILE DAMAGE ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        
        Player attacker = null;
        Entity damager = e.getDamager();

        // Logic: Check if attacker is a Player or a Projectile fired by a Player OR Monster
        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            } else if (proj.getShooter() instanceof Monster) {
                // FIX: Block skeleton arrows if PvP/Mob-Damage protection is ON
                Plot plot = plugin.store().getPlotAt(victim.getLocation());
                if (plot != null) {
                     // If safe zone or mob protection is enabled, cancel damage
                     if (isSafeZoneEnabled(plot) || isMobProtectionEnabled(plot)) {
                         e.setCancelled(true);
                         proj.remove(); // Vaporize the arrow
                     }
                }
                return; // Exit early, we handled the mob case
            }
        }
        
        // If attacker is null (e.g. not a player), exit
        if (attacker == null || attacker.equals(victim)) return;

        Plot plot = plugin.store().getPlotAt(victim.getLocation());
        if (plot == null) return;
        if (attacker.hasPermission("aegis.admin")) return;

        if (checkPlotStatus(attacker, plot)) { e.setCancelled(true); return; }

        if (plot.isServerZone()) {
            if (!plot.getFlag("pvp", false)) {
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_attack"));
                plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
            }
            return;
        }

        if (!isPvPEnabled(plot)) { // False = PVP Disabled
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_attack"));
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
        }
    }

    // --- 7. FARM/PETS/EXPLOSIONS ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Tameable pet)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        if (pet.getOwner() != null && pet.getOwner().getUniqueId().equals(attacker.getUniqueId())) return; 

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;
        if (attacker.hasPermission("aegis.admin")) return;
        if (checkPlotStatus(attacker, plot)) { e.setCancelled(true); return; }
        
        if (plot.isServerZone()) {
            if (!plot.getFlag("pets", false)) {
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_interact"));
            }
            return;
        }

        if (isPetProtectionEnabled(plot) && !plot.hasPermission(attacker.getUniqueId(), "PET_DAMAGE", plugin)) {
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_interact"));
            plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.FARMLAND) return;
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot == null) return;
        if (p.hasPermission("aegis.admin")) return;
        if (checkPlotStatus(p, plot)) { e.setCancelled(true); return; }
        
        if (plot.isServerZone()) {
            if (!plot.getFlag("farm", true)) e.setCancelled(true);
            return;
        }
        if (isFarmProtectionEnabled(plot) && !plot.hasPermission(p.getUniqueId(), "FARM_TRAMPLE", plugin)) {
            e.setCancelled(true);
            plugin.effects().playEffect("farm", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block block : new ArrayList<>(e.blockList())) {
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot == null) continue;
            if (enabled(plot, "tnt-damage")) {
                e.blockList().remove(block);
            }
        }
    }

    // --- HELPERS ---

    public boolean isSafeZoneEnabled(Plot plot) {
        return plot.getFlag("safe_zone", false); 
    }
    
    private boolean enabled(Plot plot, String flag) {
        return plot.getFlag("safe_zone", false) || plot.getFlag(flag, true);
    }

    private boolean hasFlag(Plot plot, String flag) {
        return plot != null && enabled(plot, flag);
    }

    public boolean checkPlotStatus(Player player, Plot plot) {
        if (plot == null) return false;
        if (player.hasPermission("aegis.admin")) return false;
        if (plot.getPlotStatus().equalsIgnoreCase("ACTIVE")) return false;
        return true;
    }

    // Public getters for GUI
    public boolean isPvPEnabled(Plot plot) { return !hasFlag(plot, "pvp"); } 
    public void togglePvP(Plot plot) { toggleFlag(plot, "pvp"); }
    
    public boolean isContainersEnabled(Plot plot) { return hasFlag(plot, "containers"); }
    public void toggleContainers(Plot plot) { toggleFlag(plot, "containers"); }
    
    public boolean isMobProtectionEnabled(Plot plot) { return hasFlag(plot, "mobs"); }
    public void toggleMobProtection(Plot plot) { toggleFlag(plot, "mobs"); }
    
    public boolean isPetProtectionEnabled(Plot plot) { return hasFlag(plot, "pets"); }
    public void togglePetProtection(Plot plot) { toggleFlag(plot, "pets"); }
    
    public boolean isEntityProtectionEnabled(Plot plot) { return hasFlag(plot, "entities"); }
    public void toggleEntityProtection(Plot plot) { toggleFlag(plot, "entities"); }
    
    public boolean isFarmProtectionEnabled(Plot plot) { return hasFlag(plot, "farm"); }
    public void toggleFarmProtection(Plot plot) { toggleFlag(plot, "farm"); }

    public void toggleSafeZone(Plot plot, boolean state) {
        boolean newState = !plot.getFlag("safe_zone", false);
        plot.setFlag("safe_zone", newState);
        if (newState) {
            plot.setFlag("pvp", false); 
            plot.setFlag("mobs", false); 
        }
        plugin.store().setDirty(true);
    }

    private void toggleFlag(Plot plot, String flag) {
        if (plot == null) return;
        boolean current = plot.getFlag(flag, true);
        plot.setFlag(flag, !current);
        plugin.store().setDirty(true);
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
            case CHEST, TRAPPED_CHEST, BARREL, ENDER_CHEST, FURNACE, BLAST_FURNACE, SMOKER, HOPPER, DROPPER, DISPENSER, BREWING_STAND, CHISELED_BOOKSHELF -> true;
            default -> false;
        };
    }
    
    private boolean isInteractable(Material type) {
        String name = type.name();
        return name.endsWith("_DOOR") || name.endsWith("_GATE") || name.endsWith("_BUTTON") || type == Material.LEVER || type == Material.DAYLIGHT_DETECTOR;
    }
}
