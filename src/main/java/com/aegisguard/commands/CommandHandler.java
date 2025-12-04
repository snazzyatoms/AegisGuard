package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CommandHandler implements CommandExecutor {

    private final AegisGuard plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandHandler(AegisGuard plugin) {
        this.plugin = plugin;
        
        // --- GUILD COMMANDS ---
        GuildCommand guildCmd = new GuildCommand(plugin);
        register("guild", guildCmd);
        register("alliance", guildCmd); // Alias

        // --- ESTATE COMMANDS (Private Land) ---
        EstateCommand estateCmd = new EstateCommand(plugin);
        register("claim", estateCmd);
        register("deed", estateCmd);    // RP Alias
        register("unclaim", estateCmd);
        register("vacate", estateCmd);  // RP Alias
        register("invite", estateCmd);
        register("trust", estateCmd);   // Alias
        register("setrole", estateCmd);
        register("resize", estateCmd);  // Handled in EstateCommand now
    }

    private void register(String name, SubCommand cmd) {
        subCommands.put(name, cmd);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use AegisGuard commands.");
            return true;
        }

        Player player = (Player) sender;
        LanguageManager lang = plugin.getLanguageManager();

        // 1. No Arguments? Open Guardian Codex (Main Menu)
        if (args.length == 0) {
            plugin.gui().openMain(player);
            return true;
        }

        // 2. Find Sub-Command
        String sub = args[0].toLowerCase();
        
        if (subCommands.containsKey(sub)) {
            subCommands.get(sub).execute(player, args);
        } else {
            // Fallback: Check for "Unknown Command" message in lang file, or default
            String msg = lang.getMsg(player, "unknown_command");
            if (msg.startsWith("Missing Key")) msg = "§cUnknown command. Type /ag menu for help.";
            player.sendMessage(msg);
        }

        return true;
    }

    // Simple Interface for clean code
    public interface SubCommand {
        void execute(Player player, String[] args);
    }
}
