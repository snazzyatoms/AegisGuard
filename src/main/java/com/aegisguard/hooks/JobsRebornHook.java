package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobProgression; // Added Import
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

    public void giveJobExp(Player player, String jobName, double amount) {
        if (!enabled) return;

        JobsPlayer jPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
        if (jPlayer == null) return;

        Job job = Jobs.getJob(jobName);
        if (job == null) return;

        if (jPlayer.isInJob(job)) {
            // FIX: Get Progression Object first, then add Exp
            JobProgression prog = jPlayer.getJobProgression(job);
            if (prog != null) {
                prog.addExperience(amount);
            }
        }
    }

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
