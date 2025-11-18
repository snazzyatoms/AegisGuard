package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * WildernessRevertTask
 * - This task runs periodically to clean up the wilderness.
 * - It queries the database for block changes older than a set time
 * and reverts them if the player is offline or far away.
 * - This is a "Legacy Upgrade" feature.
 */
public class WildernessRevertTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final SQLDataStore sqlStore;
    private final long revertDelayMillis;
    private final int revertBatchSize;

    public WildernessRevertTask(AegisGuard plugin, IDataStore dataStore) {
        this.plugin = plugin;
        
        // This feature ONLY works with SQLDataStore
        if (dataStore instanceof SQLDataStore) {
            this.sqlStore = (SQLDataStore) dataStore;
        } else {
            plugin.getLogger().warning("Wilderness Revert is enabled in config, but storage.type is not 'sql'. This feature will be disabled.");
            this.sqlStore = null;
            this.revertDelayMillis = 0;
            this.revertBatchSize = 0;
            this.cancel();
            return;
        }

        long hours = plugin.cfg().raw().getLong("wilderness_revert.revert_after_hours", 2);
        this.revertDelayMillis = java.util.concurrent.TimeUnit.HOURS.toMillis(hours);
        this.revertBatchSize = plugin.cfg().raw().getInt("wilderness_revert.revert_batch_size", 500);
        
        plugin.getLogger().info("Wilderness Revert task enabled. Reverting changes older than " + hours + " hours.");
    }

    @Override
    public void run() {
        if (sqlStore == null) {
            this.cancel();
            return;
        }

        // This whole operation runs asynchronously
        try {
            long revertBeforeTimestamp = System.currentTimeMillis() - revertDelayMillis;
            sqlStore.revertWildernessBlocks(revertBeforeTimestamp, revertBatchSize);
        } catch (Exception e) {
            plugin.getLogger().severe("Error during wilderness revert task:");
            e.printStackTrace();
        }
    }
}
