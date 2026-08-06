package com.aegisguard.arena;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.UUID;

/**
 * Combat, inventory, spawn, and teleport listeners for Arena runs.
 */
public final class ArenaListener implements Listener {

    private final ArenaService service;

    public ArenaListener(ArenaService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageHigh(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!service.isEnabled()) return;
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run == null || !run.getState().isActiveCombat()) return;
        ArenaDefinition def = service.getArena(run.getArenaId());
        if (def == null || !def.getInventoryPolicy().isProtectedInventory()) return;

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 0.0D) return;

        // Lethal for protected inventory policy — handle totem then eliminate; cancel death path
        boolean cancelDeath = service.handleTotemThenEliminate(player, run);
        if (cancelDeath) {
            event.setCancelled(true);
            player.setHealth(Math.max(1.0D, player.getHealth()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!service.isEnabled()) return;
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run == null) return;
        // Track damage dealt by fighters onto tagged mobs is handled on death; nothing here.
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!service.isEnabled()) return;
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run == null) return;
        ArenaParticipant part = run.getParticipant(player.getUniqueId());
        if (part != null && part.isEliminatedHandled()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepLevel(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!service.isEnabled()) return;
        service.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!service.isEnabled()) return;
        service.handleReconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!service.isEnabled()) return;
        Player player = event.getPlayer();
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run == null || !run.getState().isActiveCombat()) return;
        if (service.hasTeleportAllow(player.getUniqueId())) return;
        if (player.hasPermission("aegis.arena.admin") || service.plugin().isAdmin(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!service.isEnabled()) return;
        ItemStack stack = event.getItemDrop().getItemStack();
        if (service.keys().isArenaItem(stack)) {
            event.setCancelled(true);
            return;
        }
        ArenaRun run = service.getRunForPlayer(event.getPlayer().getUniqueId());
        if (run == null) return;
        ArenaDefinition def = service.getArena(run.getArenaId());
        if (def != null && def.getInventoryPolicy().isProtectedInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!service.isEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run == null) return;
        // Block moving arena items into containers / other inventories
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if ((current != null && service.keys().isArenaItem(current))
                || (cursor != null && service.keys().isArenaItem(cursor))) {
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                return;
            }
        }
        ArenaDefinition def = service.getArena(run.getArenaId());
        if (def != null && def.getInventoryPolicy().isProtectedInventory()
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() != InventoryType.PLAYER
                && event.getClickedInventory().getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!service.isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (!service.keys().isArenaItem(stack)) return;
        ArenaRun run = service.getRunForPlayer(player.getUniqueId());
        if (run == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!service.isEnabled()) return;
        LivingEntity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        UUID runId = service.keys().readRunId(pdc);
        if (runId == null) return;
        ArenaRun run = service.getRun(runId);
        if (run == null) return;
        run.getActiveMobIds().remove(entity.getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = entity.getKiller();
        ArenaRarity rarity = ArenaRarity.COMMON;
        boolean boss = service.keys().isBoss(pdc);
        service.recordMobKill(run, killer, rarity, boss);

        if (run.getActiveMobIds().isEmpty() && run.getState().isActiveCombat()) {
            if (service.isFinalWaveCleared(run)) {
                service.endRun(run, ArenaEndReason.CLEAR);
            } else {
                run.setState(ArenaRunState.WAVE_CLEAR);
                run.addPartyScore(ArenaScoreService.waveClearScore(run.getWaveIndex()));
                service.advanceWave(run);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!service.isEnabled()) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Monster)) return;
        if (!service.isLocationInActiveArenaPlot(event.getLocation())) return;
        // Cancel natural hostiles in active arena combat plots
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!service.isEnabled()) return;
        if (isTaggedArenaMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!service.isEnabled()) return;
        if (event.getEntity() instanceof Player) return;
        if (isTaggedArenaMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isTaggedArenaMob(Entity entity) {
        if (entity == null) return false;
        return service.keys().readRunId(entity.getPersistentDataContainer()) != null;
    }
}
