package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AdminCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender,
                           String label,
                           String subLabel,
                           String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use admin tools.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("aegisguard.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }

        // subLabel is the key registered in CommandHandler:
        //  - "admin"   -> /ag admin ...
        //  - "reload"  -> /ag reload
        //  - "server"  -> /ag server <Name>
        String mode = subLabel.toLowerCase();

        switch (mode) {
            // Direct aliases: /ag reload
            case "reload":
                doReload(player);
                return true;

            // Direct alias: /ag server <Name>
            case "server":
                handleServerClaim(player, args);
                return true;

            // Root admin router: /ag admin <sub>
            case "admin":
            default:
                if (args.length == 0) {
                    sendAdminHelp(player);
                    return true;
                }

                String sub = args[0].toLowerCase();

                switch (sub) {
                    case "reload":
                        doReload(player);
                        break;

                    case "wand":
                        // Centralized item manager for Sentinel Scepter
                        player.getInventory().addItem(plugin.getItemManager().getSentinelScepter());
                        player.sendMessage(ChatColor.GOLD + "You have received the Sentinel's Scepter.");
                        break;

                    case "claim":
                    case "server":
                        // args[0] = "claim" or "server", remainder is the name
                        String[] nameArgs = (args.length > 1)
                                ? java.util.Arrays.copyOfRange(args, 1, args.length)
                                : new String[0];
                        handleServerClaim(player, nameArgs);
                        break;

                    case "delete":
                        handleForceDelete(player);
                        break;

                    default:
                        sendAdminHelp(player);
                        break;
                }
                return true;
        }
    }

    // ---------------------------------------------------------------------
    //  ACTIONS
    // ---------------------------------------------------------------------

    private void doReload(Player player) {
        plugin.reloadConfig();
        if (plugin.getLanguageManager() != null) {
            plugin.getLanguageManager().loadAllLocales();
        }
        player.sendMessage(ChatColor.GREEN + "AegisGuard configuration reloaded.");
    }

    /**
     * Creates a server-owned Estate from the current selection.
     *
     * Supports:
     *  - /ag admin claim <Name...>
     *  - /ag admin server <Name...>
     *  - /ag server <Name...>
     */
    private void handleServerClaim(Player player, String[] nameArgs) {
        if (nameArgs.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /ag admin claim <Name>");
            player.sendMessage(ChatColor.RED + "   or: /ag server <Name>");
            return;
        }

        String plotName = String.join(" ", nameArgs).trim();

        // NOTE: getSelectionManager() is an alias for getSelection()
        Location[] sel = plugin.getSelectionManager().getSelectionLocations(player.getUniqueId());

        if (sel == null || sel[0] == null || sel[1] == null) {
            player.sendMessage(ChatColor.RED + "Select an area with the Wand first.");
            return;
        }

        // Check for overlaps at the first corner
        Estate existing = plugin.getDataStore().getEstateAt(sel[0]);
        if (existing != null) {
            player.sendMessage(ChatColor.RED + "Overlaps with: " + existing.getName());
            return;
        }

        UUID serverUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

        // Build a full-height Cuboid from world min to max
        Location min = sel[0].clone();
        Location max = sel[1].clone();

        int worldMinY = min.getWorld().getMinHeight();
        int worldMaxY = min.getWorld().getMaxHeight();

        min.setY(worldMinY);
        max.setY(worldMaxY);

        Cuboid region = new Cuboid(min, max);

        Estate serverEstate = new Estate(
                UUID.randomUUID(),
                plotName,
                serverUUID,
                false, // isGuild
                sel[0].getWorld(),
                region
        );

        // Default flags for server safe-zones
        serverEstate.setFlag("pvp", false);
        serverEstate.setFlag("mobs", false);
        serverEstate.setFlag("safe_zone", true);

        // Persist to datastore & manager
        plugin.getDataStore().saveEstate(serverEstate);
        plugin.getEstateManager().registerEstateFromLoad(serverEstate);

        player.sendMessage(ChatColor.GREEN + "Server Estate '" + plotName + "' created.");
    }

    private void handleForceDelete(Player player) {
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());

        if (estate == null) {
            player.sendMessage(ChatColor.RED + "You are not standing inside an Estate.");
            return;
        }

        plugin.getDataStore().removeEstate(estate.getId());
        plugin.getEstateManager().removeEstate(estate.getId());

        player.sendMessage(ChatColor.GREEN + "Estate '" + estate.getName() + "' deleted.");
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- AegisGuard Admin ---");
        player.sendMessage(ChatColor.YELLOW + "/ag admin wand §7- Give Sentinel's Scepter");
        player.sendMessage(ChatColor.YELLOW + "/ag admin claim <Name> §7- Create server Estate from selection");
        player.sendMessage(ChatColor.YELLOW + "/ag server <Name> §7- Quick server Estate claim");
        player.sendMessage(ChatColor.YELLOW + "/ag admin delete §7- Delete Estate at your feet");
        player.sendMessage(ChatColor.YELLOW + "/ag admin reload §7- Reload config & locales");
    }
}
