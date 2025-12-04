package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.AllianceManager;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Guild;
import org.bukkit.entity.Player;

public class GuildCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public GuildCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        AllianceManager allianceManager = plugin.getAllianceManager();

        // /ag guild
        if (args.length == 1) {
            plugin.getGuiManager().guild().openDashboard(player);
            return;
        }

        String action = args[1].toLowerCase();

        // --- /ag guild create <Name> ---
        if (action.equals("create")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /ag guild create <Name>");
                return;
            }
            
            if (allianceManager.isInGuild(player.getUniqueId())) {
                player.sendMessage(lang.getMsg(player, "already_in_guild"));
                return;
            }

            double cost = plugin.cfg().raw().getDouble("economy.costs.guild_creation", 5000.0);
            
            // FIX: Direct boolean check (No .transactionSuccess())
            if (!plugin.getEconomy().withdraw(player, cost)) {
                player.sendMessage(lang.getMsg(player, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
                return;
            }

            String name = args[2];
            Guild newGuild = allianceManager.createGuild(player, name);
            
            if (newGuild != null) {
                player.sendMessage(lang.getMsg(player, "guild_created").replace("%guild%", name));
            } else {
                // Refund if creation failed (e.g. name taken)
                plugin.getEconomy().deposit(player, cost);
                player.sendMessage(lang.getMsg(player, "guild_name_taken"));
            }
        }
        
        // --- /ag guild leave ---
        else if (action.equals("leave")) {
            // Placeholder logic
            player.sendMessage("§eLeft guild (Placeholder).");
        }
    }
}
