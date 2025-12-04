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
            // Open GUI (Dashboard)
            // plugin.getGuiManager().openGuildDashboard(player);
            player.sendMessage("§eOpening Guild Dashboard... (Coming Soon)");
            return;
        }

        String action = args[1].toLowerCase();

        // --- /ag guild create <Name> ---
        if (action.equals("create")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /ag guild create <Name>");
                return;
            }
            
            // 1. Check if already in guild
            if (allianceManager.isInGuild(player.getUniqueId())) {
                player.sendMessage(lang.getMsg(player, "already_in_guild")); // Add to lang
                return;
            }

            // 2. Check Economy (Vault)
            double cost = plugin.getConfig().getDouble("economy.costs.guild_creation", 5000.0);
            if (!plugin.getEconomy().withdrawPlayer(player, cost).transactionSuccess()) {
                player.sendMessage(lang.getMsg(player, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
                return;
            }

            // 3. Create
            String name = args[2];
            Guild newGuild = allianceManager.createGuild(player, name);
            
            if (newGuild != null) {
                player.sendMessage(lang.getMsg(player, "guild_created").replace("%guild%", name));
                // Optional: Broadcast globally
            } else {
                player.sendMessage(lang.getMsg(player, "guild_name_taken"));
            }
            return;
        }

        // --- /ag guild invite <Player> ---
        if (action.equals("invite")) {
            // Hook into invite logic (We need an InviteManager later)
            player.sendMessage("§eInvite sent! (Placeholder)");
        }
    }
}
