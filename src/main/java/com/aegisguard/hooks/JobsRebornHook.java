package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobsPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class JobsRebornHook {

    private final AegisGuard plugin;
    private boolean enabled = false;

    public JobsRebornHook(AegisGuard plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("Jobs")) {
            this.enabled = true;
            plugin.getLogger().info("Hooked into Jobs Reborn!");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Gives XP to a player's specific job.
     * Useful for rewarding "Architect" or "Builder" jobs when claiming land.
     * @param player The player to reward
     * @param jobName The name of the job (e.g. "Builder")
     * @param amount The amount of XP to give
     */
    public void giveJobExp(Player player, String jobName, double amount) {
        if (!enabled) return;

        JobsPlayer jPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
        if (jPlayer == null) return;

        Job job = Jobs.getJob(jobName);
        if (job == null) {
            // Silently fail if job doesn't exist to avoid console spam
            return; 
        }

        // Check if player actually has this job
        if (jPlayer.isInJob(job)) {
            // Give XP (Currency is usually handled automatically by Jobs based on XP, 
            // or we can ignore currency and just give XP for leveling).
            // This method signature varies by Jobs version, but addExp is standard.
            jPlayer.addJobExp(job, amount);
        }
    }

    /**
     * Checks if a player has a specific job level.
     * Useful if you want to restrict claiming to "Level 10 Builders" only.
     */
    public int getJobLevel(Player player, String jobName) {
        if (!enabled) return 0;
        
        JobsPlayer jPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
        if (jPlayer == null) return 0;
        
        Job job = Jobs.getJob(jobName);
        if (job == null) return 0;
        
        if (jPlayer.isInJob(job)) {
            return jPlayer.getJobProgression(job).getLevel();
        }
        return 0;
    }
}
