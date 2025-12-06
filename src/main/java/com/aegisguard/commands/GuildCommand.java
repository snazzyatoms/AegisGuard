package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.AllianceManager;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Guild;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GuildCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public GuildCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Routed from CommandHandler when a player runs:
     *   /ag guild
     *   /ag guild create <Name>
     *   /ag guild leave
     *
     * subLabel will be "guild" or "alliance" (from CommandHandler#register),
     * args are everything AFTER that, e.g.:
     *   /ag guild create MyGuild  -> args = ["create", "MyGuild"]
     *   /ag guild leave           -> args = ["leave"]
     */
    @Override
    public boolean execute(CommandSender sender,
                           String label,
                           String subLabel,
                           String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use guild commands.");
            return true;
        }

        Player player = (Player) sender;
        LanguageManager lang = plugin.getLanguageManager();
        AllianceManager allianceManager = plugin.getAllianceManager();

        // /ag guild   OR   /ag alliance  (no extra args)
        if (args.length == 0) {
            plugin.getGuiManager().guild().openDashboard(player);
            return true;
        }

        String action = args[0].toLowerCase();

        // ---------------------------------------------------------------------
        //  /ag guild create <Name>
        // ---------------------------------------------------------------------
        if (action.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /ag guild create <Name>");
                return true;
            }

            if (allianceManager.isInGuild(player.getUniqueId())) {
                player.sendMessage(lang.getMsg(player, "already_in_guild"));
                return true;
            }

            double cost = plugin.cfg().raw().getDouble("economy.costs.guild_creation", 5000.0);

            // Direct boolean check, as you requested earlier
            if (!plugin.getEconomy().withdraw(player, cost)) {
                player.sendMessage(
                        lang.getMsg(player, "claim_failed_money")
                            .replace("%cost%", String.valueOf(cost))
                );
                return true;
            }

            String name = args[1];
            Guild newGuild = allianceManager.createGuild(player, name);

            if (newGuild != null) {
                player.sendMessage(
                        lang.getMsg(player, "guild_created")
                            .replace("%guild%", name)
                );
            } else {
                // Refund if creation failed (e.g. name taken)
                plugin.getEconomy().deposit(player, cost);
                player.sendMessage(lang.getMsg(player, "guild_name_taken"));
            }
            return true;
        }

        // ---------------------------------------------------------------------
        //  /ag guild leave
        // ---------------------------------------------------------------------
        if (action.equals("leave")) {
            // Placeholder; you can wire real leave logic into AllianceManager later
            player.sendMessage("§eLeft guild (Placeholder).");
            return true;
        }

        // Unknown sub-action: just show dashboard for now
        plugin.getGuiManager().guild().openDashboard(player);
        return true;
    }
}
