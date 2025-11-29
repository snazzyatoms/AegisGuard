package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.IDataStore; // Interface
import com.aegisguard.data.SQLDataStore;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.TimeUnit;

/**
 * WildernessRevertTask
 * - This task runs periodically to clean up the wilderness.
 * - It queries the database for block changes older than a set time
 * and reverts them if the player is offline or far away.
 */
public class WildernessRevertTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final IDataStore dataStore; // Should always reference the interface
    private final long revertBeforeTimestamp;
    private final int revertBatchSize;

    public WildernessRevertTask(AegisGuard plugin, IDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        
        // This feature ONLY works with SQLDataStore (for logging purposes)
        if (!(dataStore instanceof SQLDataStore)) {
            plugin.getLogger().warning("Wilderness Revert is enabled in config, but storage.type is not 'sql'. This feature will be disabled.");
            this.revertBeforeTimestamp = 0;
            this.revertBatchSize = 0;
            this.cancel();
            return;
        }

        long hours = plugin.cfg().raw().getLong("wilderness_revert.revert_after_hours", 2);
        this.revertBeforeTimestamp = TimeUnit.HOURS.toMillis(hours);
        this.revertBatchSize = plugin.cfg().raw().getInt("wilderness_revert.revert_batch_size", 500);
        
        plugin.getLogger().info("Wilderness Revert task enabled. Reverting changes older than " + hours + " hours.");
    }

    @Override
    public void run() {
        // Since this class only proceeds if dataStore is an instance of SQLDataStore, 
        // we can safely call the IDataStore method.
        if (revertBatchSize <= 0) {
            this.cancel();
            return;
        }

        // This whole operation runs asynchronously (as scheduled in AegisGuard.java)
        try {
            long checkTime = System.currentTimeMillis() - revertBeforeTimestamp;
            
            // FIX: Call the method via the interface (dataStore), not a concrete field (sqlStore).
            dataStore.revertWildernessBlocks(checkTime, revertBatchSize);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error during wilderness revert task:");
            e.printStackTrace();
        }
    }
}
