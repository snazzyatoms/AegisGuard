package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Folia/Paper scheduler facade for the Arena module.
 * Arena callers must not use Bukkit's legacy scheduler API directly — route through this class.
 * <p>
 * On Paper, immediate entity/location/global work runs inline (callers are on the main thread).
 * On Folia, work is dispatched to the owning entity, region, or global scheduler.
 */
public final class ArenaScheduler {

    private final AegisGuard plugin;
    private final boolean folia;
    private final boolean capabilitiesOk;

    public ArenaScheduler(AegisGuard plugin) {
        this.plugin = plugin;
        this.folia = plugin != null && plugin.isFolia();
        this.capabilitiesOk = probeCapabilities(this.folia);
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean hasRequiredCapabilities() {
        return capabilitiesOk;
    }

    public String pathName() {
        return folia ? "Folia" : "Paper";
    }

    /** Entity-owned work (inventory, health, teleport, equipment, remove). */
    public void runForEntity(Entity entity, Runnable task) {
        if (task == null) return;
        if (entity == null) {
            runGlobal(task);
            return;
        }
        if (!folia) {
            task.run();
            return;
        }
        plugin.runEntity(entity, task);
    }

    public void runForEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (task == null) return;
        if (entity == null) {
            runGlobalLater(task, delayTicks);
            return;
        }
        plugin.runEntityLater(entity, task, Math.max(1L, delayTicks));
    }

    /** Location/region-owned work (spawns at a point, chunk/location ops). */
    public void runAtLocation(Location location, Runnable task) {
        if (task == null) return;
        if (location == null || location.getWorld() == null) {
            runGlobal(task);
            return;
        }
        if (!folia) {
            task.run();
            return;
        }
        plugin.runAt(location, task);
    }

    /** Non-region coordination only (maps, bookkeeping, orchestration). */
    public void runGlobal(Runnable task) {
        if (task == null) return;
        if (!folia) {
            task.run();
            return;
        }
        plugin.runSync(task);
    }

    public void runGlobalLater(Runnable task, long delayTicks) {
        if (task == null) return;
        plugin.runSyncLater(task, Math.max(1L, delayTicks));
    }

    public Object runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        return plugin.runGlobalRepeating(task, initialDelayTicks, periodTicks);
    }

    /** Persistence / pure computation. Never touch Bukkit entities from async. */
    public void runAsync(Runnable task) {
        if (task == null) return;
        plugin.runGlobalAsync(task);
    }

    private static boolean probeCapabilities(boolean folia) {
        if (!folia) return true;
        try {
            return Bukkit.getGlobalRegionScheduler() != null
                    && Bukkit.getRegionScheduler() != null
                    && Bukkit.getAsyncScheduler() != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
