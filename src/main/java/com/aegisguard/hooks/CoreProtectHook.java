package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class CoreProtectHook {

    private final AegisGuard plugin;
    private Object api; // Use Object to avoid import errors if dependency missing

    public CoreProtectHook(AegisGuard plugin) {
        this.plugin = plugin;
        connect();
    }

    private void connect() {
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp != null && cp.isEnabled()) {
            try {
                // Reflection to get API
                Method getAPI = cp.getClass().getMethod("getAPI");
                this.api = getAPI.invoke(cp);
                
                // Check version
                Method apiVersion = this.api.getClass().getMethod("APIVersion");
                int version = (int) apiVersion.invoke(this.api);
                
                if (version >= 6) {
                    plugin.getLogger().info("Hooked into CoreProtect!");
                } else {
                    this.api = null;
                }
            } catch (Exception e) {
                this.api = null;
            }
        }
    }

    public void logAdminAction(String user, Location loc, String action) {
        if (api == null) return;
        try {
            // api.logPlacement(user, loc, Material.BEDROCK, null); 
            // Reflection call:
            Method logPlacement = api.getClass().getMethod("logPlacement", String.class, Location.class, org.bukkit.Material.class, org.bukkit.block.data.BlockData.class);
            logPlacement.invoke(api, user, loc, org.bukkit.Material.BEDROCK, null);
        } catch (Exception ignored) {}
    }
}
