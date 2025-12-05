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
        
        // --- 1. GENERAL COMMANDS (AegisCommand) ---
        AegisCommand generalCmd = new AegisCommand(plugin);
        register("menu", generalCmd);
        register("wand", generalCmd);
        register("help", generalCmd);
        register("visit", generalCmd);
        register("home", generalCmd);
        register("setspawn", generalCmd);
        register("stuck", generalCmd);
        register("rename", generalCmd);
        register("setdesc", generalCmd);
        register("level", generalCmd);
        register("zone", generalCmd);
        register("consume", generalCmd);

        // --- 2. GUILD COMMANDS (GuildCommand) ---
        GuildCommand guildCmd = new GuildCommand(plugin);
        register("guild", guildCmd);
        register("alliance", guildCmd);

        // --- 3. ESTATE COMMANDS (EstateCommand) ---
        EstateCommand estateCmd = new EstateCommand(plugin);
        register("claim", estateCmd);
        register("deed", estateCmd);
        register("unclaim", estateCmd);
        register("vacate", estateCmd);
        register("invite", estateCmd);
        register("trust", estateCmd);
        register("setrole", estateCmd);
        register("resize", estateCmd);
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
            plugin.getGuiManager().openGuardianCodex(player);
            return true;
        }

        // 2. Find Sub-Command
        String sub = args[0].toLowerCase();
        
        if (subCommands.containsKey(sub)) {
            subCommands.get(sub).execute(player, args);
        } else {
            // Fallback
            String msg = lang.getMsg(player, "unknown_command");
            if (msg.startsWith("§c[Missing")) msg = "§cUnknown command. Type /ag help.";
            player.sendMessage(msg);
        }

        return true;
    }

    public interface SubCommand {
        void execute(Player player, String[] args);
    }
}
