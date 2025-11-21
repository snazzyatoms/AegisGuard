package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (!p.hasPermission("aegis.admin")) {
            p.sendMessage("§cNo Permission.");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().admin().open(p);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            p.sendMessage("§7Reloading...");
            plugin.cfg().reload();
            plugin.msg().reload();
            plugin.worldRules().reload();
            p.sendMessage("§aReload Complete.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("menu", "reload"), new ArrayList<>());
        }
        return null;
    }
}
