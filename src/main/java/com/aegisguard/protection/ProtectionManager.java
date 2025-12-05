package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;

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
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return; // Allow plugins

        Estate estate = plugin.getEstateManager().getEstateAt(e.getLocation());
        if (estate != null) {
            // If "mobs" flag is FALSE, cancel the spawn
            if (!estate.getFlag("mobs")) {
                if (isHostile(e.getEntity())) {
                    e.setCancelled(true);
                }
            }
        } else {
            // Wilderness Logic (Optional: Check world rules)
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

        // Resolve Projectiles (Arrow -> Player)
        if (attacker instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        Estate estate = plugin.getEstateManager().getEstateAt(victim.getLocation());

        // 1. PvP Protection
        if (victim instanceof Player && attacker instanceof Player) {
            if (estate != null) {
                if (!estate.getFlag("pvp")) {
                    e.setCancelled(true);
                    // Optional: Send message to attacker?
                }
            } else if (!plugin.getWorldRules().isPvPAllowed(victim.getWorld())) {
                e.setCancelled(true); // Wilderness PvP check
            }
            return;
        }

        // 2. PvE (Player attacked by Mob)
        if (victim instanceof Player && isHostile(attacker)) {
            if (estate != null && estate.getFlag("safe_zone")) {
                e.setCancelled(true); // God mode in Safe Zones
            }
            return;
        }

        // 3. Friendly Fire (Pets/Animals)
        if (estate != null && !estate.getFlag("pets")) {
            if (victim instanceof Tameable || victim instanceof Animals || victim instanceof ArmorStand) {
                if (attacker instanceof Player p) {
                    // Allow owner/members to hurt animals, block strangers
                    if (!estate.isMember(p.getUniqueId()) && !plugin.isAdmin(p)) {
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
        // Check all blocks involved. If ANY block is in a protected estate, cancel or filter.
        e.blockList().removeIf(block -> {
            Estate estate = plugin.getEstateManager().getEstateAt(block.getLocation());
            // If estate exists and TNT/Explosions are disabled (default false)
            return estate != null && !estate.getFlag("tnt-damage");
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
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
    // 🍎 HUNGER LOGIC (v1.3.0)
    // ==========================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHungerDeplete(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        if (estate != null) {
            // If "hunger" flag is FALSE (unchecked), prevent loss
            if (!estate.getFlag("hunger")) {
                if (e.getFoodLevel() < player.getFoodLevel()) {
                    e.setCancelled(true);
                    player.setSaturation(20f);
                }
            }
        }
    }

    // --- UTILS ---
    private boolean isHostile(Entity e) {
        if (e instanceof Monster || e instanceof Slime || e instanceof Phantom || e instanceof Shulker || e instanceof Ghast || e instanceof Hoglin) return true;
        String name = e.getType().name();
        return name.contains("WARDEN") || name.contains("BREEZE") || name.contains("CREAKING");
    }
}
