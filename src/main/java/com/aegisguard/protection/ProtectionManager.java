package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import org.bukkit.ChatColor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.block.EntityChangeBlockEvent;

/**
 * ProtectionManager
 * - Handles Environmental & Entity-based protections.
 */
public class ProtectionManager implements Listener {

    private final AegisGuard plugin;

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
    }
    
    // --- Helper to determine if an estate is a safe zone ---
    private boolean isEstateSafe(Estate estate) {
        if (estate == null) return false;
        // Server Estates are inherently safe (mobs not allowed)
        if (estate.isServerEstate()) return true;
        // Check flags: Safe Zone or Mobs flag is FALSE
        return estate.getFlag("safe_zone") || !estate.getFlag("mobs");
    }

    // ==========================================================
    // 🧟 MOB SPAWNING & TARGETING
    // ==========================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        Estate estate = plugin.getEstateManager().getEstateAt(e.getLocation());
        
        if (estate != null) {
            // Cancel hostile spawns if the estate is designated as safe (mobs disallowed)
            if (isEstateSafe(estate)) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player target)) return;
        
        Estate estate = plugin.getEstateManager().getEstateAt(target.getLocation());
        
        if (isEstateSafe(estate)) {
            e.setCancelled(true);
            e.setTarget(null); // Force them to forget the player
            
            // IMMEDIATE REPEL: Push the mob out or away from the boundary/player
            if (e.getEntity().getLocation().distanceSquared(target.getLocation()) < 25) {
                // Assumes a RepelMob method exists in EffectUtil or elsewhere
                plugin.getEffects().repelMob(e.getEntity()); 
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Enderman || e.getEntity() instanceof Silverfish || e.getEntity() instanceof Wither)) return;

        Estate estate = plugin.getEstateManager().getEstateAt(e.getBlock().getLocation());
        if (isEstateSafe(estate)) {
            e.setCancelled(true);
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
        if (victim instanceof Player pVictim && attacker instanceof Player pAttacker) {
            if (plugin.isAdmin(pAttacker)) return; 
            
            if (estate != null) {
                if (!estate.getFlag("pvp")) {
                    e.setCancelled(true);
                }
            } else if (!plugin.getWorldRules().isPvPAllowed(victim.getWorld())) {
                e.setCancelled(true);
            }
            return;
        }

        // 2. PvE (Mob attacking Player) - God Mode in Safe Zones
        if (victim instanceof Player && isHostile(attacker)) {
            if (isEstateSafe(estate)) {
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
                    // Guild/Alliance Check
                    if (estate.isGuild() && !hasAccess) {
                        Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
                        if (guild != null && guild.isMember(p.getUniqueId())) hasAccess = true;
                    }

                    if (!hasAccess) {
                        e.setCancelled(true);
                        p.sendMessage(ChatColor.RED + "You cannot harm assets in this Estate.");
                    }
                }
            }
        }
    }
    
    // NEW: Prevents Mob Damage from non-Entity Sources
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Mob mob)) return;
        
        Estate estate = plugin.getEstateManager().getEstateAt(mob.getLocation());
        if (isEstateSafe(estate)) {
            DamageCause cause = e.getCause();
            // Prevent mobs from dying from environmental factors in safe zones
            if (cause == DamageCause.SUFFOCATION || cause == DamageCause.LAVA || cause == DamageCause.FIRE || cause == DamageCause.FREEZE) {
                e.setCancelled(true);
            }
        }
    }

    // ==========================================================
    // 💥 EXPLOSIONS & FIRE (Preserved)
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
    // 🍎 HUNGER (Preserved)
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
