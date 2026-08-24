package com.aegisguard.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Single scheduling boundary for Bukkit/Paper/Purpur and Folia.
 *
 * <p>Callers select the ownership domain of their work instead of selecting a server scheduler
 * directly. Scheduling failures are reported as values so recovery workflows can fail closed.</p>
 */
public final class AegisScheduler {

    public enum DispatchResult {
        ACCEPTED,
        REJECTED_SHUTDOWN,
        REJECTED_INVALID_TARGET,
        REJECTED_RETIRED_ENTITY,
        FAILED;

        public boolean accepted() {
            return this == ACCEPTED;
        }
    }

    private final JavaPlugin plugin;
    private final boolean folia;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public AegisScheduler(JavaPlugin plugin, boolean folia) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folia = folia;
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public DispatchResult runGlobal(Runnable task) {
        if (!canAccept(task)) return rejectedFor(task);
        try {
            if (folia) Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runGuarded("global", task));
            else Bukkit.getScheduler().runTask(plugin, () -> runGuarded("main", task));
            return DispatchResult.ACCEPTED;
        } catch (Throwable error) {
            return scheduleFailed("global", error);
        }
    }

    public DispatchResult runEntity(Entity entity, Runnable task, Runnable retired) {
        if (!canAccept(task)) return rejectedFor(task);
        if (entity == null) return DispatchResult.REJECTED_INVALID_TARGET;
        try {
            if (!folia) {
                Bukkit.getScheduler().runTask(plugin, () -> runGuarded("entity-main", task));
                return DispatchResult.ACCEPTED;
            }
            ScheduledTask accepted = entity.getScheduler().run(
                    plugin,
                    ignored -> runGuarded("entity", task),
                    () -> runGuarded("retired-entity", retired));
            return accepted == null ? DispatchResult.REJECTED_RETIRED_ENTITY : DispatchResult.ACCEPTED;
        } catch (Throwable error) {
            return scheduleFailed("entity", error);
        }
    }

    public DispatchResult runAt(Location location, Runnable task) {
        if (!canAccept(task)) return rejectedFor(task);
        if (location == null || location.getWorld() == null) return DispatchResult.REJECTED_INVALID_TARGET;
        Location target = location.clone();
        try {
            if (folia) Bukkit.getRegionScheduler().run(plugin, target, ignored -> runGuarded("region", task));
            else Bukkit.getScheduler().runTask(plugin, () -> runGuarded("main-region", task));
            return DispatchResult.ACCEPTED;
        } catch (Throwable error) {
            return scheduleFailed("region", error);
        }
    }

    public DispatchResult runAsync(Runnable task) {
        if (!canAccept(task)) return rejectedFor(task);
        try {
            if (folia) Bukkit.getAsyncScheduler().runNow(plugin, ignored -> runGuarded("async", task));
            else Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runGuarded("async", task));
            return DispatchResult.ACCEPTED;
        } catch (Throwable error) {
            return scheduleFailed("async", error);
        }
    }

    public Object runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        if (!canAccept(task)) return null;
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        try {
            if (folia) {
                return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                        plugin, ignored -> runGuarded("global-repeat", task), delay, period);
            }
            return Bukkit.getScheduler().runTaskTimer(
                    plugin, () -> runGuarded("main-repeat", task), delay, period);
        } catch (Throwable error) {
            scheduleFailed("global-repeat", error);
            return null;
        }
    }

    public Object runAsyncRepeating(Runnable task, long initialDelaySeconds, long periodSeconds) {
        if (!canAccept(task)) return null;
        long delay = Math.max(1L, initialDelaySeconds);
        long period = Math.max(1L, periodSeconds);
        try {
            if (folia) {
                return Bukkit.getAsyncScheduler().runAtFixedRate(
                        plugin, ignored -> runGuarded("async-repeat", task),
                        delay, period, TimeUnit.SECONDS);
            }
            return Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin, () -> runGuarded("async-repeat", task), delay * 20L, period * 20L);
        } catch (Throwable error) {
            scheduleFailed("async-repeat", error);
            return null;
        }
    }

    public Object runEntityRepeating(Entity entity, Runnable task, Runnable retired,
                                     long initialDelayTicks, long periodTicks) {
        if (!canAccept(task) || entity == null) return null;
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        try {
            if (folia) {
                return entity.getScheduler().runAtFixedRate(
                        plugin, ignored -> runGuarded("entity-repeat", task),
                        () -> runGuarded("retired-entity-repeat", retired), delay, period);
            }
            return Bukkit.getScheduler().runTaskTimer(
                    plugin, () -> runGuarded("entity-main-repeat", task), delay, period);
        } catch (Throwable error) {
            scheduleFailed("entity-repeat", error);
            return null;
        }
    }

    public DispatchResult runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        if (!canAccept(task)) return rejectedFor(task);
        if (entity == null) return DispatchResult.REJECTED_INVALID_TARGET;
        long delay = Math.max(1L, delayTicks);
        try {
            if (folia) {
                ScheduledTask accepted = entity.getScheduler().runDelayed(
                        plugin, ignored -> runGuarded("entity-delayed", task),
                        () -> runGuarded("retired-entity-delayed", retired), delay);
                return accepted == null ? DispatchResult.REJECTED_RETIRED_ENTITY : DispatchResult.ACCEPTED;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> runGuarded("entity-main-delayed", task), delay);
            return DispatchResult.ACCEPTED;
        } catch (Throwable error) {
            return scheduleFailed("entity-delayed", error);
        }
    }

    public DispatchResult runGlobalLater(Runnable task, long delayTicks) {
        if (!canAccept(task)) return rejectedFor(task);
        long delay = Math.max(1L, delayTicks);
        try {
            if (folia) Bukkit.getGlobalRegionScheduler().runDelayed(
                    plugin, ignored -> runGuarded("global-delayed", task), delay);
            else Bukkit.getScheduler().runTaskLater(plugin, () -> runGuarded("main-delayed", task), delay);
            return DispatchResult.ACCEPTED;
        } catch (Throwable error) {
            return scheduleFailed("global-delayed", error);
        }
    }

    /** Verify ownership only from inside the dispatched task. */
    public boolean owns(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return !folia || Bukkit.isOwnedByCurrentRegion(location);
    }

    public void cancel(Object task) {
        if (task == null) return;
        try {
            if (task instanceof BukkitTask bukkitTask) {
                bukkitTask.cancel();
            } else if (task instanceof ScheduledTask scheduledTask) {
                scheduledTask.cancel();
            } else {
                Method cancel = task.getClass().getMethod("cancel");
                cancel.invoke(task);
            }
        } catch (Throwable error) {
            plugin.getLogger().log(Level.FINE, "Could not cancel scheduled task", error);
        }
    }

    /** Reject new work and cancel scheduler-owned tasks during plugin shutdown. */
    public void shutdown() {
        if (!accepting.compareAndSet(true, false)) return;
        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
        }
        if (folia) {
            try {
                Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            } catch (Throwable ignored) {
            }
            try {
                Bukkit.getAsyncScheduler().cancelTasks(plugin);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean canAccept(Runnable task) {
        return task != null && accepting.get();
    }

    private DispatchResult rejectedFor(Runnable task) {
        return task == null ? DispatchResult.REJECTED_INVALID_TARGET : DispatchResult.REJECTED_SHUTDOWN;
    }

    private DispatchResult scheduleFailed(String domain, Throwable error) {
        plugin.getLogger().log(Level.WARNING, "Failed to schedule " + domain + " work", error);
        return DispatchResult.FAILED;
    }

    private void runGuarded(String domain, Runnable task) {
        if (task == null || !accepting.get()) return;
        try {
            task.run();
        } catch (Throwable error) {
            plugin.getLogger().log(Level.SEVERE, "Uncaught error in " + domain + " task", error);
        }
    }
}
