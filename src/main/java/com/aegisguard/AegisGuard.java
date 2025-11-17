package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
// ... existing imports ...
import com.aegisguard.util.SoundUtil;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
// ... existing imports ...
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
// ... existing imports ...

/**
 * AegisGuard – main entrypoint
 */
public class AegisGuard extends JavaPlugin { // AegisGuard no longer needs CommandExecutor

    // ... existing fields ...

    // ... existing getter methods (cfg, store, etc) ...

    @Override
    public void onEnable() {
        // Bundle defaults
// ... existing code ...
        saveResource("messages.yml", false);

        // Core systems
// ... existing code ...
        this.sounds     = new SoundUtil(this);

        // Listeners
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManger().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);
        // Do NOT register plotStore; it's not a Listener.

        // --- IMPROVEMENT ---
        // Register our new, cleaner listener for banned players
        if (getConfig().getBoolean("admin.auto_remove_banned", false)) {
            Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);
        }

        // Commands (null-safe)
        PluginCommand aegis = getCommand("aegis");
// ... existing code ...
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        // --- IMPROVEMENT ---
        // Register the new AegisCommand class as the executor AND tab completer
        AegisCommand aegisExecutor = new AegisCommand(this);
        aegis.setExecutor(aegisExecutor);
        aegis.setTabCompleter(aegisExecutor);

        PluginCommand admin = getCommand("aegisadmin");
        if (admin != null) {
            // --- IMPROVEMENT ---
            // Create an instance so we can register both executor and tab completer
            AdminCommand adminExecutor = new AdminCommand(this);
            admin.setExecutor(adminExecutor);
            admin.setTabCompleter(adminExecutor); // This hooks up the TabCompleter from AdminCommand
        } else {
// ... existing code ...
        }

        getLogger().info("AegisGuard v" + getDescription().getVersion() + " enabled.");
// ... existing code ...
    }

    @Override
// ... existing code ...
    public void onDisable() {
        if (plotStore != null) plotStore.flushSync();
        getLogger().info("AegisGuard disabled. Data saved.");
    }

    // --- REMOVED ---
    // All of the onCommand logic has been moved to AegisCommand.java
    // --- REMOVED ---

    // --- REMOVED ---
    // The createScepter() method has been moved to AegisCommand.java
    // --- REMOVED ---


    /* -----------------------------
     * Utility: Sound Control
// ... existing code ...
     */
    public boolean isSoundEnabled(Player player) {
// ... existing code ...
        if (getConfig().isSet(key)) return getConfig().getBoolean(key, true);
        return true;
    }
}
