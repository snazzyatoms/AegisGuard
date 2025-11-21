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
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("aegis.admin")) {
            plugin.msg().send(player, "no_perm");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().admin().open(player);
            return true;
        }

        // FIX: Converted to standard switch case for Java 8 compatibility
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.msg().send(player, "admin_reloading");
                plugin.runGlobalAsync(() -> {
                    plugin.cfg().reload();
                    plugin.msg().reload();
                    plugin.effects().reload();
                    plugin.worldRules().reload();
                    plugin.store().load();
                    plugin.runMain(player, () -> plugin.msg().send(player, "admin_reload_complete"));
                });
                break;

            case "menu":
                plugin.gui().admin().open(player);
                break;

            case "bypass":
                // Toggle admin bypass logic
                // Implementation depends on how you track bypass (Metadata or List)
                player.sendMessage("§cAdmin Bypass toggled (Logic placeholder)");
                break;

            default:
                player.sendMessage("§cUnknown Admin Command. /aegisadmin [menu|reload]");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], Arrays.asList("menu", "reload", "bypass"), completions);
            Collections.sort(completions);
            return completions;
        }
        return null;
    }
}
