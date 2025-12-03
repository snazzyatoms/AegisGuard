package com.yourname.aegisguard.commands;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.managers.RoleManager;
import com.yourname.aegisguard.objects.Cuboid;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EstateCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public EstateCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        
        if (args.length == 0) {
            // No args? Open the "My Estates" GUI
            // plugin.getGuiManager().openEstateList(player);
            player.sendMessage("§eOpening Estate List... (Coming Soon)");
            return;
        }

        String action = args[0].toLowerCase();

        // =========================================================
        // 🏡 /ag claim <Name>
        // =========================================================
        if (action.equals("claim") || action.equals("deed")) {
            // 1. Check Selection
            Cuboid selection = plugin.getSelection().getSelection(player);
            if (selection == null) {
                player.sendMessage(lang.getMsg(player, "no_selection")); // "Use /ag wand first"
                return;
            }

            // 2. Calculate Cost
            double cost = plugin.getEconomy().calculateClaimCost(selection); // Need to add this helper to EcoManager
            if (!plugin.getEconomy().has(player, cost)) {
                player.sendMessage(lang.getMsg(player, "claim_failed_money").replace("%cost%", String.valueOf(cost)));
                return;
            }

            // 3. Name
            String name = (args.length > 1) ? args[1] : plugin.getConfig().getString("estates.naming.private_format", "My Estate").replace("%player%", player.getName());

            // 4. Create
            Estate estate = plugin.getEstateManager().createEstate(player, selection, name, false); // false = Private
            
            if (estate != null) {
                plugin.getEconomy().withdraw(player, cost);
                player.sendMessage(lang.getMsg(player, "claim_success")
                    .replace("%type%", lang.getTerm("type_private"))
                    .replace("%name%", name));
            } else {
                player.sendMessage(lang.getMsg(player, "claim_failed_overlap"));
            }
            return;
        }

        // =========================================================
        // 👥 /ag invite <Player> (Trusting)
        // =========================================================
        if (action.equals("invite") || action.equals("trust")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: /ag invite <Player>");
                return;
            }

            Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
            if (!validateOwner(player, estate)) return;

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            
            // Get Default Role from Config
            String defaultRole = plugin.getConfig().getString("role_system.default_private_role", "resident");
            
            estate.setMember(target.getUniqueId(), defaultRole);
            player.sendMessage("§a✔ Added " + target.getName() + " as a " + defaultRole + ".");
            return;
        }

        // =========================================================
        // 🛡️ /ag setrole <Player> <Role>
        // =========================================================
        if (action.equals("setrole")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: /ag setrole <Player> <Role>");
                return;
            }

            Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
            if (!validateOwner(player, estate)) return;

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            String roleId = args[2].toLowerCase();

            // Validate Role exists
            RoleManager.RoleDefinition roleDef = plugin.getRoleManager().getPrivateRole(roleId);
            if (roleDef == null) {
                player.sendMessage("§cInvalid Role. Valid options: viceroy, resident, guest, etc.");
                return;
            }

            estate.setMember(target.getUniqueId(), roleId);
            player.sendMessage("§a✔ Updated " + target.getName() + " to " + roleDef.getDisplayName());
            return;
        }
        
        // =========================================================
        // 🚮 /ag unclaim
        // =========================================================
        if (action.equals("unclaim") || action.equals("vacate")) {
            Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
            if (!validateOwner(player, estate)) return;
            
            plugin.getEstateManager().deleteEstate(estate.getId());
            player.sendMessage(lang.getMsg(player, "claim_deleted")); // Add to lang
            return;
        }
    }

    private boolean validateOwner(Player p, Estate e) {
        if (e == null) {
            p.sendMessage("§cYou must be standing in an Estate.");
            return false;
        }
        // Only Owner (or Admin bypass) can manage roles via command
        if (!e.getOwnerId().equals(p.getUniqueId()) && !p.hasPermission("aegis.admin.bypass")) {
            p.sendMessage("§cYou do not own this Estate.");
            return false;
        }
        return true;
    }
}
