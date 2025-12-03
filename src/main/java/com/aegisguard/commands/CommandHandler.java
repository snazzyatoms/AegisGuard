package com.yourname.aegisguard.commands;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
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
        
        // Register Sub-Commands Here
        register("guild", new GuildCommand(plugin));
        register("alliance", new GuildCommand(plugin)); // Alias
        // TODO: register("claim", new ClaimCommand(plugin));
        // TODO: register("menu", new MenuCommand(plugin));
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

        // 1. No Arguments? Open Main Menu
        if (args.length == 0) {
            // plugin.getGuiManager().openMainMenu(player); (We will enable this later)
            player.sendMessage("§eOpening Guardian Codex... (Coming Soon)");
            return true;
        }

        // 2. Find Sub-Command
        String sub = args[0].toLowerCase();
        if (subCommands.containsKey(sub)) {
            subCommands.get(sub).execute(player, args);
        } else {
            player.sendMessage(lang.getMsg(player, "unknown_command")); // Add this key to lang files later
        }

        return true;
    }

    // Simple Interface for clean code
    public interface SubCommand {
        void execute(Player player, String[] args);
    }
}
