package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;

/**
 * ProtectionManager
 * - Handles Environmental & Entity-based protections.
 * - Ensures Mobs, PvP, Fire, and Explosions respect Estate flags.
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ==========================================================
    // 🧟 MOB SPAWNING & TARGETING
    // ==========================================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        Estate estate = plugin.getEstateManager().getEstateAt(e.getLocation());
        
        if (estate != null) {
            // If "mobs" flag is FALSE, strictly cancel hostile spawns
            if (!estate.getFlag("mobs")) {
                if (isHostile(e.getEntity())) {
                    e.setCancelled(true);
                }
            }
        } else {
            // Wilderness Logic
            if (!plugin.getWorldRules().allowMobs(e.getLocation().getWorld())) {
                if (isHostile(e.getEntity())) e.setCancelled(true);
            }
        }
    }

    // --- NEW: Stop mobs from seeing/targeting players in Safe Zones ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player target)) return;
        
        Estate estate = plugin.getEstateManager().getEstateAt(target.getLocation());
        
        // If player is in a Safe Zone or an Estate with disabled mobs
        if (estate != null) {
            if (estate.getFlag("safe_zone") || !estate.getFlag("mobs")) {
                e.setCancelled(true);
                e.setTarget(null); // Force them to forget the player
            }
        }
    }

    // ==========================================================
    // ⚔️ DAMAGE & COMBAT
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity attacker = e.getDamager();

        if (attacker instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        Estate estate = plugin.getEstateManager().getEstateAt(victim.getLocation());

        // 1. PvP Protection
        if (victim instanceof Player && attacker instanceof Player) {
            if (estate != null) {
                if (!estate.getFlag("pvp")) {
                    e.setCancelled(true);
                }
            } else if (!plugin.getWorldRules().isPvPAllowed(victim.getWorld())) {
                e.setCancelled(true);
            }
            return;
        }

        // 2. PvE (Player attacked by Mob) - God Mode in Safe Zones
        if (victim instanceof Player && isHostile(attacker)) {
            if (estate != null && estate.getFlag("safe_zone")) {
                e.setCancelled(true); 
            }
            return;
        }

        // 3. Asset Protection (Pets, Armor Stands)
        if (estate != null && !estate.getFlag("pets")) {
            if (victim instanceof Tameable || victim instanceof Animals || victim instanceof ArmorStand || victim instanceof ItemFrame) {
                if (attacker instanceof Player p) {
                    if (plugin.isAdmin(p)) return;

                    boolean hasAccess = estate.isMember(p.getUniqueId());
                    if (estate.isGuild() && !hasAccess) {
                        Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
                        if (guild != null && guild.isMember(p.getUniqueId())) hasAccess = true;
                    }

                    if (!hasAccess) {
                        e.setCancelled(true);
                        plugin.getLanguageManager().sendTitle(p, "cannot_attack", "");
                    }
                }
            }
        }
    }

    // ==========================================================
    // 💥 EXPLOSIONS & FIRE
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> {
            Estate estate = plugin.getEstateManager().getEstateAt(block.getLocation());
            return estate != null && !estate.getFlag("tnt-damage");
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            Estate estate = plugin.getEstateManager().getEstateAt(e.getBlock().getLocation());
            if (estate != null && !estate.getFlag("fire-spread")) {
                e.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        Estate estate = plugin.getEstateManager().getEstateAt(e.getBlock().getLocation());
        if (estate != null && !estate.getFlag("fire-spread")) {
            e.setCancelled(true);
        }
    }

    // ==========================================================
    // 🍎 HUNGER
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHungerDeplete(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        if (estate != null && !estate.getFlag("hunger")) {
            if (e.getFoodLevel() < player.getFoodLevel()) {
                e.setCancelled(true);
                player.setSaturation(20f);
            }
        }
    }

    // ==========================================================
    // 🛠️ UTILITIES
    // ==========================================================
    public boolean isHostile(Entity e) {
        if (e == null) return false;
        if (e instanceof Monster) return true;
        if (e instanceof Slime) return true;
        if (e instanceof Phantom) return true;
        if (e instanceof Shulker) return true;
        if (e instanceof Ghast) return true;
        if (e instanceof Hoglin) return true;
        if (e instanceof EnderDragon || e instanceof Wither) return true;

        String name = e.getType().name();
        return name.contains("WARDEN") || 
               name.contains("BREEZE") || 
               name.contains("BOGGED") || 
               name.contains("CREAKING");
    }
}
