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
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
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
    
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> buffCooldowns = new ConcurrentHashMap<>();    

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
                if (plot.isServerZone() || plot.getFlag("safe_zone", false) || !plot.getFlag("mobs", true)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // --- 2. MOB TARGETING (Stop targeting trusted players) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player target)) return;
        if (!(e.getEntity() instanceof Monster || e.getEntity() instanceof Slime || e.getEntity() instanceof Phantom)) return;

        Plot plot = plugin.store().getPlotAt(target.getLocation());
        if (plot != null) {
            // If trusted, mob ignores player
            if (plot.hasPermission(target.getUniqueId(), "INTERACT", plugin)) {
                e.setCancelled(true);
            }
        }
    }

    // --- 3. MOVEMENT LOGIC (Sidebar & Messages) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // ✅ Safety: handle potential null 'to' location
        if (e.getTo() == null) return;

        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Plot toPlot = plugin.store().getPlotAt(e.getTo());
        Plot fromPlot = plugin.store().getPlotAt(e.getFrom());

        // A. Leaving Logic
        if (fromPlot != null && !fromPlot.equals(toPlot)) {
            PlotLeaveEvent leaveEvent = new PlotLeaveEvent(fromPlot, p);
            Bukkit.getPluginManager().callEvent(leaveEvent);
            
            // Hide Sidebar when leaving plot
            plugin.getSidebar().hideSidebar(p);
            
            if (!fromPlot.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, fromPlot.getFarewellMessage());
            }

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
            
            // Show Sidebar when entering plot
            plugin.getSidebar().showSidebar(p, toPlot);

            if (!toPlot.getOwner().equals(p.getUniqueId())) {
                sendPlotMessage(p, toPlot.getWelcomeMessage());
            }
            
            if (toPlot.getEntryEffect() != null) {
                plugin.effects().playCustomEffect(p, toPlot.getEntryEffect(), toPlot.getCenter(plugin));
            }

            if (toPlot.getFlag("fly", false)) {
                if (toPlot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                    plugin.runMain(p, () -> {
                        p.setAllowFlight(true);
                        p.sendMessage("§a🕊 Entering flight zone.");
                    });
                }
            }
        }

        // C. Continuous Checks
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
                if (!toPlot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                    bouncePlayer(p, e);
                    sendPlotMessage(p, plugin.msg().get(p, "plot_entry_denied"));
                    return;
                }
            }
            applyPlotBuffs(p, toPlot);
        }
    }
    
    // --- 4. COMBAT ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        
        Entity damager = e.getDamager();
        Plot plot = plugin.store().getPlotAt(victim.getLocation());

        if (damager instanceof Monster || damager instanceof Slime || damager instanceof Phantom || 
           (damager instanceof Projectile proj && proj.getShooter() instanceof Monster)) {
            if (plot != null) {
                if (plot.isServerZone() || plot.getFlag("safe_zone", false) || !plot.getFlag("mobs", true)) {
                    e.setCancelled(true);
                    if (damager instanceof Projectile) damager.remove();    
                    return;
                }
                // Trust Check: If trusted, cancel mob damage
                if (plot.hasPermission(victim.getUniqueId(), "INTERACT", plugin)) {
                    e.setCancelled(true);
                    if (damager instanceof Projectile) damager.remove();
                    return;
                }
            }
        }

        Player attacker = resolveAttacker(damager);
        if (attacker == null || attacker.equals(victim)) return;
        if (plot == null) return;
        if (plugin.isAdmin(attacker)) return;

        if (isPlotLocked(attacker, plot)) { e.setCancelled(true); return; }

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

        if (plot == null && wildernessRevertEnabled) {
            final String oldMat = block.getType().toString();
            final UUID uuid = p.getUniqueId();
            final Location loc = block.getLocation();
            plugin.runGlobalAsync(() -> plugin.store().logWildernessBlock(loc, oldMat, "AIR", uuid));
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

    // --- 7. INTERACT ---
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
        
        Zone zone = plot.getZoneAt(block.getLocation());
        if (zone != null && zone.isRented()) {
            boolean isRenter = p.getUniqueId().equals(zone.getRenter()) || p.getUniqueId().equals(plot.getOwner());
            if (!isRenter) {
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

        if (shopAllowed && (isContainer || isSign(block.getType()))) return;

        if (plot.isServerZone()) {
            if (isContainer && !plot.getFlag("containers", false)) cancelInteract(e, p, "containers");
            else if (isInteractable && !plot.getFlag("interact", true)) cancelInteract(e, p, "interact");
            return;
        }

        if (isContainer) {
            if (!plot.hasPermission(p.getUniqueId(), "CONTAINERS", plugin)) cancelInteract(e, p, "containers");
        } else if (isInteractable && !isSign(block.getType())) {
            if (!plot.getFlag("interact", true) || !plot.hasPermission(p.getUniqueId(), "INTERACT", plugin)) {
                cancelInteract(e, p, "interact");
            }
        }
    }

    // --- 8. PET/FARM/EXPLOSION ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Tameable pet)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        
        // FIX: Use getOwner().getUniqueId()
        if (pet.getOwner() != null && pet.getOwner().getUniqueId().equals(attacker.getUniqueId())) return;    

        Plot plot = plugin.store().getPlotAt(e.getEntity().getLocation());
        if (plot == null) return;
        if (attacker.hasPermission("aegis.admin.bypass")) return;
        
        boolean petsAllowed = plot.getFlag("pets", false);
        
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

    // --- HELPER: APPLY BUFFS ---
    
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
                            // FIX: ambient=true, particles=false, icon=false (Clean Inventory)
                            p.addPotionEffect(new PotionEffect(type, 100, amp, true, false, false));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        buffCooldowns.put(p.getUniqueId(), now + 2000);
    }

    // --- OTHER HELPERS ---
    
    public boolean isFlagEnabled(Plot plot, String flag) { return plot != null && plot.getFlag(flag, true); }
    public void toggleSafeZone(Plot plot, boolean state) { 
        boolean newState = !plot.getFlag("safe_zone", false);
        plot.setFlag("safe_zone", newState);
        if (newState) {
            plot.setFlag("pvp", false); 
            plot.setFlag("mobs", false); 
        }
        plugin.store().setDirty(true);
    }
    public boolean isSafeZoneEnabled(Plot plot) { return plot != null && plot.getFlag("safe_zone", false); }
    private boolean isPlotLocked(Player player, Plot plot) { return plot != null && !plugin.isAdmin(player) && !plot.getPlotStatus().equalsIgnoreCase("ACTIVE"); }
    private void bouncePlayer(Player p, PlayerMoveEvent e) { e.setCancelled(true); }
    
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

    private void cancelBuild(org.bukkit.event.Cancellable e, Player p) { e.setCancelled(true); p.sendMessage(plugin.msg().get("cannot_break")); plugin.effects().playError(p); }
    private void cancelInteract(org.bukkit.event.Cancellable e, Player p, String type) { e.setCancelled(true); p.sendMessage(plugin.msg().get("cannot_interact")); plugin.effects().playEffect(type, "deny", p, p.getLocation()); }
    private boolean isContainer(Material type) { return type == Material.SHULKER_BOX || type.name().endsWith("_SHULKER_BOX") || type == Material.CHEST || type == Material.BARREL; } 
    private boolean isSign(Material type) { return type.name().contains("SIGN"); }
    private boolean isInteractable(Material type) { return type.name().contains("DOOR") || type.name().contains("BUTTON"); } 
}
