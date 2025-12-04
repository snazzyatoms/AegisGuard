package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class CoreProtectHook {

    private final AegisGuard plugin;
    private CoreProtectAPI api;

    public CoreProtectHook(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp instanceof CoreProtect) {
            CoreProtectAPI api = ((CoreProtect) cp).getAPI();
            if (api.isEnabled() && api.APIVersion() >= 6) {
                this.api = api;
                plugin.getLogger().info("Hooked into CoreProtect!");
            }
        }
    }

    public void logAdminAction(String user, Location loc, String action) {
        if (api == null) return;
        // Log as a "Command" or "Placement" depending on what you want
        // Here we log it as a Chat command for visibility in inspection
        // api.logCommand(user, "/ag " + action); 
        
        // Or log a placement of a "Bedrock" block at the location to signify a secure change
        // api.logPlacement(user, loc, org.bukkit.Material.BEDROCK, null);
    }
}
