package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.TimeUnit;

/**
 * WildernessRevertTask
 * - This task runs periodically to clean up the wilderness.
 * - It queries the database for block changes older than a set time
 * - and reverts them if the player is offline or far away.
 */
public class WildernessRevertTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final IDataStore dataStore;
    private final long revertBeforeTimestamp;
    private final int revertBatchSize;

    public WildernessRevertTask(AegisGuard plugin, IDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        
        // 1. Check Storage Type
        // This feature requires SQL because logging every block break to YML is too slow.
        if (!(dataStore instanceof SQLDataStore)) {
            // Only warn if the feature was actually enabled in config
            if (plugin.getConfig().getBoolean("wilderness_revert.enabled", false)) {
                plugin.getLogger().warning("Wilderness Revert is enabled, but storage type is 'yml'. This feature requires 'sql'. Disabling task.");
            }
            this.revertBeforeTimestamp = 0;
            this.revertBatchSize = 0;
            this.cancel();
            return;
        }

        // 2. Load Settings
        long hours = plugin.getConfig().getLong("wilderness_revert.revert_after_hours", 2);
        this.revertBeforeTimestamp = TimeUnit.HOURS.toMillis(hours);
        this.revertBatchSize = plugin.getConfig().getInt("wilderness_revert.revert_batch_size", 500);
        
        plugin.getLogger().info("Wilderness Revert task active. Reverting changes older than " + hours + " hours.");
    }

    @Override
    public void run() {
        // Safety Check
        if (revertBatchSize <= 0 || this.isCancelled()) {
            return;
        }

        // Logic
        try {
            // Calculate the cutoff time (Current Time - Configured Hours)
            long checkTime = System.currentTimeMillis() - revertBeforeTimestamp;
            
            // Execute the revert via the Interface
            // (The SQL implementation handles the heavy lifting async)
            dataStore.revertWildernessBlocks(checkTime, revertBatchSize);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error during wilderness revert task: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
