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

import java.util.UUID;

/**
 * ProtectionManager
 * - Handles Environmental & Entity-based protections.
 * - Ensures Mobs, PvP, Fire, and Explosions respect Estate flags.
 * - Supports both Private Estates and Guild Estates.
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ==========================================================
    // 🧟 MOB SPAWNING (The "Disappear" Logic)
    // ==========================================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        // Allow custom spawning (e.g. plugins, commands), only block natural/spawners if flagged
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
            // Wilderness Logic: Check global world rules
            if (!plugin.getWorldRules().allowMobs(e.getLocation().getWorld())) {
                if (isHostile(e.getEntity())) e.setCancelled(true);
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

        // Resolve Projectiles (Arrow/Trident -> Shooter)
        if (attacker instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        Estate estate = plugin.getEstateManager().getEstateAt(victim.getLocation());

        // 1. PvP Protection (Player vs Player)
        if (victim instanceof Player && attacker instanceof Player) {
            if (estate != null) {
                // If PvP is disabled in this Estate
                if (!estate.getFlag("pvp")) {
                    e.setCancelled(true);
                    // Optional: Send feedback?
                    // plugin.getLanguageManager().sendTitle((Player) attacker, "cannot_attack", "");
                }
            } else if (!plugin.getWorldRules().isPvPAllowed(victim.getWorld())) {
                e.setCancelled(true); // Wilderness PvP check
            }
            return;
        }

        // 2. PvE (Player attacked by Mob) - "Safe Zone" God Mode
        if (victim instanceof Player && isHostile(attacker)) {
            if (estate != null && estate.getFlag("safe_zone")) {
                e.setCancelled(true); 
            }
            return;
        }

        // 3. Friendly Fire / Asset Protection (Pets, Armor Stands, Animals)
        if (estate != null && !estate.getFlag("pets")) {
            if (victim instanceof Tameable || victim instanceof Animals || victim instanceof ArmorStand || victim instanceof ItemFrame) {
                if (attacker instanceof Player p) {
                    // Allow admins to bypass
                    if (plugin.isAdmin(p)) return;

                    // Check Access: Private Member OR Guild Member
                    boolean hasAccess = estate.isMember(p.getUniqueId());
                    
                    if (estate.isGuild() && !hasAccess) {
                        Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
                        if (guild != null && guild.isMember(p.getUniqueId())) {
                            hasAccess = true;
                        }
                    }

                    // If not allowed, block damage
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
        // Iterate through all blocks the explosion WOULD destroy
        e.blockList().removeIf(block -> {
            Estate estate = plugin.getEstateManager().getEstateAt(block.getLocation());
            
            // If the block is inside an estate, and that estate does NOT allow TNT damage
            // (Default: tnt-damage is false, so !false = true -> remove from list)
            return estate != null && !estate.getFlag("tnt-damage");
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            Estate estate = plugin.getEstateManager().getEstateAt(e.getBlock().getLocation());
            
            // If fire-spread flag is FALSE (default), stop it
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
    // 🍎 HUNGER & STATUS (v1.3.0)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHungerDeplete(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        if (estate != null) {
            // If "hunger" flag is FALSE (unchecked), prevent loss
            // Useful for Spawn / Safe Zones
            if (!estate.getFlag("hunger")) {
                if (e.getFoodLevel() < player.getFoodLevel()) {
                    e.setCancelled(true);
                    player.setSaturation(20f);
                }
            }
        }
    }

    // ==========================================================
    // 🛠️ UTILITIES
    // ==========================================================
    
    /**
     * Determines if an entity is considered hostile/dangerous.
     * Includes 1.21+ mobs (Breeze, Bogged, Creaking) via name check.
     */
    private boolean isHostile(Entity e) {
        if (e == null) return false;
        
        if (e instanceof Monster) return true;
        if (e instanceof Slime) return true;
        if (e instanceof Phantom) return true;
        if (e instanceof Shulker) return true;
        if (e instanceof Ghast) return true;
        if (e instanceof Hoglin) return true;
        if (e instanceof EnderDragon || e instanceof Wither) return true;

        // String-Based Checks for Future Mobs (Version Agnostic)
        String name = e.getType().name();
        return name.contains("WARDEN") || 
               name.contains("BREEZE") || 
               name.contains("BOGGED") || 
               name.contains("CREAKING");
    }
}
