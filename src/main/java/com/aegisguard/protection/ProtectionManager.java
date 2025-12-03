package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate; // v1.3.0 Object
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionManager implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, Long> buffCooldowns = new ConcurrentHashMap<>();

    public ProtectionManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ==========================================================
    // 🧟 MOB LOGIC (The "Bouncer")
    // ==========================================================

    // --- 1. MOB SPAWN PREVENTION ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent e) {
        if (isHostile(e.getEntity())) {
            Estate estate = plugin.getEstateManager().getEstateAt(e.getLocation());
            if (estate != null) {
                // If "mobs" flag is false (default), cancel spawn
                if (!estate.getFlag("mobs")) {
                    e.setCancelled(true);
                }
            }
        }
    }

    // --- 2. MOB TARGETING (Stop targeting trusted players) ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player target)) return;
        if (!isHostile(e.getEntity())) return;

        Estate estate = plugin.getEstateManager().getEstateAt(target.getLocation());
        if (estate != null) {
            // v1.3.0: If player is a Member (any role), the mob ignores them
            if (estate.isMember(target.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }
    
    // --- 3. MOB DAMAGE NEGATION ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDamagePlayer(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        
        Entity attacker = e.getDamager();
        
        // Advanced Projectile Check (Skeleton Arrows, Breeze Wind Charge, Bogged Poison)
        if (attacker instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Entity shooter && isHostile(shooter)) {
                attacker = shooter; // Treat the shooter as the attacker
            }
        }

        if (isHostile(attacker)) {
            Estate estate = plugin.getEstateManager().getEstateAt(victim.getLocation());
            if (estate != null) {
                // If "mobs" flag is disabled OR player is trusted -> No Damage
                if (!estate.getFlag("mobs") || estate.isMember(victim.getUniqueId())) {
                    e.setCancelled(true);
                    // Optional: Remove the arrow/wind charge to reduce lag
                    if (e.getDamager() instanceof Projectile) e.getDamager().remove();
                }
            }
        }
    }

    // ==========================================================
    // 💣 EXPLOSIONS & PHYSICS
    // ==========================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        // Prevent Creeper/Wither/Ghast holes inside estates
        e.blockList().removeIf(block -> {
            Estate estate = plugin.getEstateManager().getEstateAt(block.getLocation());
            // If estate exists AND tnt-damage flag is false -> Protect block
            return estate != null && !estate.getFlag("tnt-damage");
        });
    }

    // ==========================================================
    // 🏃 MOVEMENT & BUFFS
    // ==========================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Optimization: Only run if block changed
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        Estate toEstate = plugin.getEstateManager().getEstateAt(e.getTo());
        Estate fromEstate = plugin.getEstateManager().getEstateAt(e.getFrom());

        // A. Entering Logic
        if (toEstate != null && !toEstate.equals(fromEstate)) {
            plugin.getLanguageManager().sendTitle(p, "enter_title", toEstate.getName());
        }

        // B. Leaving Logic
        if (fromEstate != null && !fromEstate.equals(toEstate)) {
            plugin.getLanguageManager().sendTitle(p, "exit_title", "");
            
            // Disable Flight if they left the base (and not in Creative)
            if (p.getAllowFlight() && !p.getGameMode().name().contains("CREATIVE")) {
                p.setAllowFlight(false);
                p.setFlying(false);
                p.sendMessage("§c🕊 Leaving flight zone.");
            }
        }

        // C. Apply Buffs (Speed, Haste)
        if (toEstate != null && toEstate.isMember(p.getUniqueId())) {
            applyBuffs(p, toEstate);
        }
    }

    private void applyBuffs(Player p, Estate estate) {
        long now = System.currentTimeMillis();
        if (buffCooldowns.getOrDefault(p.getUniqueId(), 0L) > now) return;

        // Note: Real buff logic will be hooked into Ascension/Bastion manager later
        buffCooldowns.put(p.getUniqueId(), now + 2000);
    }

    // ==========================================================
    // 🧬 ADVANCED MOB DETECTION (1.21 / 1.22 READY)
    // ==========================================================
    private boolean isHostile(Entity e) {
        if (e == null) return false;

        // 1. Standard Category Check (Covers Zombies, Spiders, Pillagers, etc.)
        if (e instanceof Monster) return true;
        if (e instanceof Slime) return true; // Slimes & Magma Cubes
        if (e instanceof Phantom) return true;
        if (e instanceof Shulker) return true;
        if (e instanceof Ghast) return true;
        if (e instanceof Hoglin) return true; // 1.16
        
        // 2. Bosses
        if (e instanceof EnderDragon || e instanceof Wither) return true;

        // 3. String-Based Checks for New/Future Mobs 
        // This makes the plugin compatible with 1.21+ even if compiled on older Java
        String name = e.getType().name();

        // 1.19: The Warden
        if (name.contains("WARDEN")) return true;
        
        // 1.21: The Breeze & The Bogged
        if (name.contains("BREEZE")) return true;
        if (name.contains("BOGGED")) return true;
        
        // 1.22 (Upcoming): The Creaking
        if (name.contains("CREAKING")) return true;

        return false;
    }
}
