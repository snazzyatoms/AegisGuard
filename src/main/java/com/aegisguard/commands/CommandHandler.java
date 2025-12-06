package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
// ... imports ...

public class CommandHandler implements CommandExecutor {

    private final AegisGuard plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandHandler(AegisGuard plugin) {
        this.plugin = plugin;
        
        // --- 1. GENERAL COMMANDS (AegisCommand) ---
        AegisCommand generalCmd = new AegisCommand(plugin);
        // ... (General commands preserved) ...
        register("consume", generalCmd);

        // --- 2. GUILD COMMANDS (GuildCommand) ---
        GuildCommand guildCmd = new GuildCommand(plugin);
        register("guild", guildCmd);
        register("alliance", guildCmd);

        // --- 3. ESTATE COMMANDS (Player Plots) ---
        EstateCommand estateCmd = new EstateCommand(plugin);
        register("claim", estateCmd);
        register("deed", estateCmd);
        register("unclaim", estateCmd);
        register("vacate", estateCmd);
        register("invite", estateCmd);
        register("trust", estateCmd);
        register("setrole", estateCmd);
        register("resize", estateCmd);
        register("wand", estateCmd); // MOVED: This is now the player wand command
        
        // --- 4. ADMIN COMMANDS (Server Estates & Maintenance) ---
        AdminCommand adminCmd = new AdminCommand(plugin);
        register("admin", adminCmd);
        register("reload", adminCmd);
        // REMOVED: register("wand", adminCmd); <--- WAS CAUSING PLAYER PERMISSION ISSUES
        register("server", adminCmd); // Alias for creating server plots
    }

    // ... (Remaining methods onCommand, register, and SubCommand interface preserved) ...
}
