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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent; // NEW IMPORT
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final boolean wildernessRevertEnabled;    
    
    // Cooldown Maps (Concurrent for Folia/Async safety)
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> buffCooldowns = new ConcurrentHashMap<>();    

    // Dependency Injection
    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.wildernessRevertEnabled = plugin.cfg().raw().getBoolean("wilderness_revert.enabled", false);
    }

    // --- 1. MOB SPAWN PREVENTION ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.getEntity() instanceof Monster || e.getEntity() instanceof Slime || e.getEntity() instanceof Phantom) {
            Plot plot = plugin.store().getPlotAt(e.getLocation());
            if (plot != null) {
                // If it's a server zone OR safe zone OR mobs flag is false -> Prevent Spawn
                if (plot.isServerZone() || plot.getFlag("safe_zone", false) || !plot.getFlag("mobs", true)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // --- 2. MOB TARGETING (NEW: Ignore Trusted Players) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player target)) return;
        
        // Only stop hostile mobs
        if (!(e.getEntity() instanceof Monster || e.getEntity() instanceof Slime || e.getEntity() instanceof Phantom)) return;

        Plot plot = plugin.store().getPlotAt(target.getLocation());
        if (plot != null) {
            // If the player is trusted (Owner/Member), the mob loses interest
            if (plot.hasPermission(target.getUniqueId(), "INTERACT", plugin)) {
                e.setCancelled(true);
            }
        }
    }

    // --- 3. MOVEMENT LOGIC (Optimized) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Performance: Don't run logic if they just rotated their head
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Plot toPlot = plugin.store().getPlotAt(e.getTo());
        Plot fromPlot = plugin.store().getPlotAt(e.getFrom());

        // A. Leaving Logic
        if (fromPlot != null && !fromPlot.equals(toPlot)) {
            PlotLeaveEvent leaveEvent = new PlotLeaveEvent(fromPlot, p);
            Bukkit.getPluginManager().callEvent(leaveEvent);
            
            if (!fromPlot.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, fromPlot.getFarewellMessage());
            }

            // Strip Flight
            if (fromPlot.getFlag("fly", false)) {
                if (!p.hasPermission("aegis.admin.bypass") && 
                    p.getGameMode() != org.bukkit.GameMode.CREATIVE && 
                    p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    
                    plugin.runMain(p, () -> {
                        p.setAllowFlight(false);
                        p.setFlying(false);
                        p.setFallDistance(0);    
                        p.sendMessage("§c🕊 Leaving flight zone.");
                    });
                }
            }
        }

        // B. Entering Logic
        if (toPlot != null && !toPlot.equals(fromPlot)) {
            PlotEnterEvent enterEvent = new PlotEnterEvent(toPlot, p);
            Bukkit.getPluginManager().callEvent(enterEvent);
            
            if (enterEvent.isCancelled()) {
                bouncePlayer(p, e);
                return;
            }

            if (!toPlot.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, toPlot.getWelcomeMessage());
            }
            
            // Visual Effect
            if (toPlot.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, toPlot.getEntryEffect(), toPlot.getCenter(plugin));
            }

            // Grant Flight
            if (toPlot.getFlag("fly", false)) {
                if (toPlot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                    plugin.runMain(p, () -> {
                        p.setAllowFlight(true);
                        p.sendMessage("§a🕊 Entering flight zone.");
                    });
                }
            }
        }

        // C. Continuous Checks (Bans & Locks)
        if (toPlot != null) {
            if (p.hasPermission("aegis.admin.bypass")) {
                 applyPlotBuffs(p, toPlot);
                 return;
            }

            if (toPlot.isBanned(p.getUniqueId())) {
                bouncePlayer(p, e);
                sendPlotMessage(p, plugin.msg().get(p, "plot_banned_entry"));
                return;
            }

            boolean entryAllowed = toPlot.getFlag("entry", true);
            if (!entryAllowed) {
                // If entry is denied, check if they are a member (trusted interactors can enter)
                if (!toPlot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                    bouncePlayer(p, e);
                    sendPlotMessage(p, plugin.msg().get(p, "plot_entry_denied"));
                    return;
                }
            }
            applyPlotBuffs(p, toPlot);
        }
    }
    
    // --- 4. COMBAT & DAMAGE ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        
        Entity damager = e.getDamager();
        Plot plot = plugin.store().getPlotAt(victim.getLocation());

        // A. Mob Damage (Updated)
        if (damager instanceof Monster || damager instanceof Slime || damager instanceof Phantom || 
           (damager instanceof Projectile proj && proj.getShooter() instanceof Monster)) {
            if (plot != null) {
                // 1. Flag Checks (Server Zone / Safe Zone / No Mobs)
                if (plot.isServerZone() || plot.getFlag("safe_zone", false) || !plot.getFlag("mobs", true)) {
                    e.setCancelled(true);
                    if (damager instanceof Projectile) damager.remove();    
                    return;
                }
                
                // 2. Trust Check: If player is trusted/owner, they are safe from mobs
                if (plot.hasPermission(victim.getUniqueId(), "INTERACT", plugin)) {
                    e.setCancelled(true);
                    if (damager instanceof Projectile) damager.remove();
                    return;
                }
            }
        }

        // B. PvP Logic
        Player attacker = resolveAttacker(damager);
        if (attacker == null || attacker.equals(victim)) return;
        if (plot == null) return;
        if (plugin.isAdmin(attacker)) return; // Admin Bypass

        // Check if Plot is Locked (inactive/expired)
        if (isPlotLocked(attacker, plot)) { e.setCancelled(true); return; }

        // Check PvP Flag
        boolean pvpAllowed = plot.getFlag("pvp", false);
        if (!pvpAllowed) {    
            e.setCancelled(true);
            attacker.sendMessage(plugin.msg().get("cannot_attack"));
            plugin.effects().playEffect("pvp", "deny", attacker, victim.getLocation());
        }
    }

    // --- 5. BLOCK BREAK ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());

        // Wilderness Logging (Async)
        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            plugin.runGlobalAsync(() -> plugin.store().logWildernessBlock(loc, oldMat, "AIR", uuid));
            return;
        }
        
        if (plot == null) return;
        if (p.hasPermission("aegis.admin.bypass")) return;

        // Sub-Zone Logic
        Zone zone = plot.getZoneAt(block.getLocation());
        if (zone != null && zone.isRented()) {
            if (!p.getUniqueId().equals(zone.getRenter()) && !p.getUniqueId().equals(plot.getOwner())) {
                e.setCancelled(true);
                p.sendMessage("§cThis zone is rented by " + Bukkit.getOfflinePlayer(zone.getRenter()).getName());
                return;    
            }
        }

        if (isPlotLocked(p, plot)) { e.setCancelled(true); return; }

        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) cancelBuild(e, p);
            return;
        }
        
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) cancelBuild(e, p);
    }

    // --- 6. BLOCK PLACE ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());

        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = e.getBlockReplacedState().getType().toString();
            final String newMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            plugin.runGlobalAsync(() -> plugin.store().logWildernessBlock(loc, oldMat, newMat, uuid));
            return;
        }
        
        if (plot == null) return;
        if (p.hasPermission("aegis.admin.bypass")) return;
        
        Zone zone = plot.getZoneAt(block.getLocation());
        if (zone != null && zone.isRented()) {
            if (!p.getUniqueId().equals(zone.getRenter()) && !p.getUniqueId().equals(plot.getOwner())) {
                e.setCancelled(true);
                p.sendMessage("§cThis zone is rented by " + Bukkit.getOfflinePlayer(zone.getRenter()).getName());
                return;    
            }
        }
        
        if (isPlotLocked(p, plot)) { e.setCancelled(true); return; }

        if (plot.isServerZone()) {
            if (!plot.getFlag("build", false)) cancelBuild(e, p);
            return;
        }
        
        if (!plot.hasPermission(p.getUniqueId(), "BUILD", plugin)) cancelBuild(e, p);
    }

    // --- 7. INTERACT (Shop & Containers) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        Block block = e.getClickedBlock();
        Plot plot = plugin.store().getPlotAt(block.getLocation());
        
        if (plot == null) return;
        if (p.hasPermission("aegis.admin.bypass")) return;
        
        if (isPlotLocked(p, plot)) {    
            e.setCancelled(true);    
            p.sendMessage(plugin.msg().get("plot-is-locked"));    
            return;    
        }
        
        // Sub-Zone Logic
        Zone zone = plot.getZoneAt(block.getLocation());
        if (zone != null && zone.isRented()) {
            boolean isRenter = p.getUniqueId().equals(zone.getRenter()) || p.getUniqueId().equals(plot.getOwner());
            if (!isRenter) {
                // Allow shopping interaction even in rented zones if flag is on
                if (plot.getFlag("shop-interact", false) && (isContainer(block.getType()) || isSign(block.getType()))) {
                    return;    
                }
                e.setCancelled(true);
                p.sendMessage("§cThis zone is rented.");
                return;
            }
        }
        
        boolean shopAllowed = plot.getFlag("shop-interact", false);
        boolean isContainer = isContainer(block.getType());
        boolean isInteractable = isInteractable(block.getType());

        // Shop Override (QuickShop/ChestShop)
        if (shopAllowed && (isContainer || isSign(block.getType()))) return;

        // Logic for Server Zones
        if (plot.isServerZone()) {
            if (isContainer && !plot.getFlag("containers", false)) cancelInteract(e, p, "containers");
            else if (isInteractable && !plot.getFlag("interact", true)) cancelInteract(e, p, "interact");
            return;
        }

        // Logic for Player Plots
        if (isContainer) {
            if (!plot.hasPermission(p.getUniqueId(), "CONTAINERS", plugin)) cancelInteract(e, p, "containers");
        } else if (isInteractable && !isSign(block.getType())) {
            // If the global interact flag is off, OR they don't have permission
            if (!plot.getFlag("interact", true) || !plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                cancelInteract(e, p, "interact");
            }
        }
    }

    // --- 8. PET DAMAGE ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Tameable pet)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        
        // FIX: Use getOwner().getUniqueId() to avoid method not found errors on modern APIs
        if (pet.getOwner() != null && pet.getOwner().getUniqueId().equals(attacker.getUniqueId())) return;    

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;
        if (attacker.hasPermission("aegis.admin.bypass")) return;
        
        boolean petsAllowed = plot.getFlag("pets", false);
        
        // If it's a server zone or pets are protected
        if (plot.isServerZone() || !petsAllowed) {
            if (!plot.hasPermission(attacker.getUniqueId(), "PET_DAMAGE", plugin)) {
                e.setCancelled(true);
                attacker.sendMessage(plugin.msg().get("cannot_interact"));
                plugin.effects().playEffect("pets", "deny", attacker, pet.getLocation());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.FARMLAND) return;
        
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(e.getClickedBlock().getLocation());
        if (plot == null) return;
        if (p.hasPermission("aegis.admin.bypass")) return;

        boolean farmAllowed = plot.getFlag("farm", true); 
        
        if (!farmAllowed || !plot.hasPermission(p.getUniqueId(), "FARM_TRAMPLE", plugin)) {
            e.setCancelled(true);
            plugin.effects().playEffect("farm", "deny", p, e.getClickedBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block block : new ArrayList<>(e.blockList())) {
            Plot plot = plugin.store().getPlotAt(block.getLocation());
            if (plot != null) {
                if (!plot.getFlag("tnt-damage", false)) { 
                    e.blockList().remove(block);
                }
            }
        }
    }

    // ======================================
    // --- HELPER METHODS ---
    // ======================================

    public boolean isFlagEnabled(Plot plot, String flag) {
        return plot != null && plot.getFlag(flag, true); 
    }

    public void toggleSafeZone(Plot plot, boolean state) {
        boolean newState = !plot.getFlag("safe_zone", false);
        plot.setFlag("safe_zone", newState);
        if (newState) {
            plot.setFlag("pvp", false); 
            plot.setFlag("mobs", false); 
        }
        plugin.store().setDirty(true);
    }
    
    public boolean isSafeZoneEnabled(Plot plot) {
        return plot != null && plot.getFlag("safe_zone", false); 
    }

    private boolean isPlotLocked(Player player, Plot plot) {
        if (plot == null) return false;
        if (plugin.isAdmin(player)) return false;
        if (plot.getPlotStatus().equalsIgnoreCase("ACTIVE")) return false;
        return true;
    }
    
    private void applyPlotBuffs(Player p, Plot plot) {
        if (!plugin.cfg().isLevelingEnabled()) return;
        long now = System.currentTimeMillis();
        if (buffCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;
        
        if (!plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) return;

        int level = plot.getLevel();
        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;
            for (String reward : rewards) {
                if (reward.startsWith("EFFECT:")) {
                    try {
                        String[] parts = reward.split(":");
                        PotionEffectType type = PotionEffectType.getByName(parts[1]);
                        int amp = Integer.parseInt(parts[2]) - 1;
                        if (type != null) {
                            p.addPotionEffect(new PotionEffect(type, 100, amp, true, false));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        buffCooldowns.put(p.getUniqueId(), now + 2000);
    }

    private void bouncePlayer(Player p, PlayerMoveEvent e) {
        e.setCancelled(true);
    }
    
    private void sendPlotMessage(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        long currentTime = System.currentTimeMillis();
        if (messageCooldowns.getOrDefault(p.getUniqueId(), 0L) > currentTime) return;

        plugin.runMain(p, () -> {
            p.sendMessage(plugin.msg().color(message));
            messageCooldowns.put(p.getUniqueId(), currentTime + TimeUnit.SECONDS.toMillis(5));
        });
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player dp) return dp;
        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player sp) return sp;
        }
        return null;
    }

    private void cancelBuild(org.bukkit.event.Cancellable e, Player p) {
        e.setCancelled(true);
        p.sendMessage(plugin.msg().get("cannot_break")); 
        plugin.effects().playError(p);
    }
    
    private void cancelInteract(org.bukkit.event.Cancellable e, Player p, String type) {
        e.setCancelled(true);
        p.sendMessage(plugin.msg().get("cannot_interact"));
        plugin.effects().playEffect(type, "deny", p, p.getLocation());
    }

    private boolean isContainer(Material type) {
        if (type == Material.SHULKER_BOX || type.name().endsWith("_SHULKER_BOX")) return true;
        return switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL, ENDER_CHEST, FURNACE, 
                 BLAST_FURNACE, SMOKER, HOPPER, DROPPER, DISPENSER, 
                 BREWING_STAND, CHISELED_BOOKSHELF -> true;
            default -> false;
        };
    }
    
    private boolean isSign(Material type) {
        return type.name().contains("SIGN");
    }
    
    private boolean isInteractable(Material type) {
        String name = type.name();
        return name.endsWith("_DOOR") || name.endsWith("_GATE") || 
               name.endsWith("_BUTTON") || name.endsWith("_TRAPDOOR") ||
               type == Material.LEVER || type == Material.DAYLIGHT_DETECTOR ||
               type == Material.REPEATER || type == Material.COMPARATOR;
    }
}
