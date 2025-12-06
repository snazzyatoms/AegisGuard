package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EstateCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public EstateCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Called from CommandHandler when a player runs things like:
     *  /ag claim <name>
     *  /ag wand
     *  /ag invite <player>
     *  /ag setrole <player> <role>
     *  /ag unclaim
     *  /ag resize <direction> <amount>
     */
    @Override
    public boolean execute(CommandSender sender,
                           String label,
                           String subLabel,
                           String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use Estate commands.");
            return true;
        }

        Player player = (Player) sender;
        LanguageManager lang = plugin.getLanguageManager();
        String action = subLabel.toLowerCase(); // alias registered in CommandHandler

        switch (action) {
            // ==================================================================
            // 🪄 /ag wand
            // ==================================================================
            case "wand":
                handleWand(player);
                return true;

            // ==================================================================
            // 🏡 /ag claim <Name...>   or   /ag deed <Name...>
            // ==================================================================
            case "claim":
            case "deed":
                handleClaim(player, args, lang);
                return true;

            // ==================================================================
            // 🚮 /ag unclaim   or   /ag vacate
            // ==================================================================
            case "unclaim":
            case "vacate":
                handleUnclaim(player, lang);
                return true;

            // ==================================================================
            // 👥 /ag invite <Player>   or   /ag trust <Player>
            // ==================================================================
            case "invite":
            case "trust":
                handleInvite(player, args, lang);
                return true;

            // ==================================================================
            // 🛡️ /ag setrole <Player> <Role>
            // ==================================================================
            case "setrole":
                handleSetRole(player, args, lang);
                return true;

            // ==================================================================
            // 📏 /ag resize <direction> <amount>
            // ==================================================================
            case "resize":
                handleResize(player, args, lang);
                return true;

            default:
                // No specific sub-alias matched; could open an estate menu later
                player.sendMessage("§eOpening Estate List... (Coming Soon)");
                return true;
        }
    }

    // ----------------------------------------------------------------------
    //  HANDLERS
    // ----------------------------------------------------------------------

    private void handleWand(Player player) {
        // Permission check for claim wand
        if (!player.hasPermission("aegis.wand")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use the claim tool.");
            return;
        }

        // Use centralized ItemManager for player wand
        player.getInventory().addItem(plugin.getItemManager().getPlayerWand());
        player.sendMessage(ChatColor.AQUA + "You have received the Claim Wand.");
    }

    private void handleClaim(Player player, String[] args, LanguageManager lang) {
        // 1) Require a name
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /ag claim <Name>");
            return;
        }

        // 2) Get selection
        Cuboid selection = plugin.getSelection().getSelection(player);
        if (selection == null) {
            player.sendMessage(lang.getMsg(player, "no_selection")); // e.g. "Use /ag wand first"
            return;
        }

        // 3) Normalise vertical bounds sky-to-bedrock for 1.21
        if (selection.getWorld() != null) {
            int minY = selection.getWorld().getMinHeight();
            int maxY = selection.getWorld().getMaxHeight();
            selection = selection.withFullHeight(minY, maxY); // If your Cuboid doesn't have this, you can adjust Y via a new constructor instead.
        }

        // 4) Overlap check
        if (plugin.getEstateManager().isOverlapping(selection)) {
            player.sendMessage(lang.getMsg(player, "plot_overlap"));
            return;
        }

        // 5) Build the estate name
        String name = String.join(" ", args);
        if (name.length() > 32) {
            player.sendMessage(ChatColor.RED + "Name too long (max 32 characters).");
            return;
        }

        // 6) Create via EstateManager (this also calls datastore.saveEstate())
        Estate estate = plugin.getEstateManager().createEstate(player, selection, name, false);

        // 7) Initial flag setup can be done here or in PlotFlagsGUI.initializeDefaultFlags()
        //    For now, we trust Estate constructor defaults.

        player.sendMessage(ChatColor.GREEN + "Estate '" + estate.getName() + "' created.");
    }

    private void handleUnclaim(Player player, LanguageManager lang) {
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());

        if (estate == null) {
            player.sendMessage(lang.getMsg(player, "no_plot_here"));
            return;
        }

        if (!validateOwnerOrAdmin(player, estate)) {
            player.sendMessage(lang.getMsg(player, "no_permission"));
            return;
        }

        plugin.getDataStore().removeEstate(estate.getId());
        plugin.getEstateManager().removeEstate(estate.getId());

        player.sendMessage(ChatColor.GREEN + "Estate '" + estate.getName() + "' unclaimed.");
    }

    private void handleInvite(Player player, String[] args, LanguageManager lang) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /ag invite <Player>");
            return;
        }

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        if (estate == null) {
            player.sendMessage(lang.getMsg(player, "no_plot_here"));
            return;
        }

        if (!validateOwnerOrAdmin(player, estate)) {
            player.sendMessage(lang.getMsg(player, "no_permission"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();

        // Simple default role; later we can hook into RoleManager if needed
        estate.setMember(targetId, "resident");
        plugin.getDataStore().saveEstate(estate);

        player.sendMessage(ChatColor.GREEN + "Trusted " + target.getName() + " in your Estate.");
        if (target.isOnline()) {
            ((Player) target).sendMessage(ChatColor.GREEN + "You have been trusted in " + player.getName() + "'s Estate.");
        }
    }

    private void handleSetRole(Player player, String[] args, LanguageManager lang) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /ag setrole <Player> <Role>");
            return;
        }

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        if (estate == null) {
            player.sendMessage(lang.getMsg(player, "no_plot_here"));
            return;
        }

        if (!validateOwnerOrAdmin(player, estate)) {
            player.sendMessage(lang.getMsg(player, "no_permission"));
            return;
        }

        String targetName = args[0];
        String roleName = args[1];

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetId = target.getUniqueId();

        estate.setMember(targetId, roleName.toLowerCase());
        plugin.getDataStore().saveEstate(estate);

        player.sendMessage(ChatColor.GREEN + "Set role of " + targetName + " to '" + roleName + "'.");
        if (target.isOnline()) {
            ((Player) target).sendMessage(ChatColor.YELLOW + "Your role in this Estate is now '" + roleName + "'.");
        }
    }

    private void handleResize(Player player, String[] args, LanguageManager lang) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /ag resize <direction> <amount>");
            player.sendMessage(ChatColor.GRAY + "Directions: NORTH, SOUTH, EAST, WEST, UP, DOWN");
            return;
        }

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        if (estate == null) {
            player.sendMessage(lang.getMsg(player, "no_plot_here"));
            return;
        }

        if (!validateOwnerOrAdmin(player, estate)) {
            player.sendMessage(lang.getMsg(player, "no_permission"));
            return;
        }

        String direction = args[0].toUpperCase();
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "Amount must be a number.");
            return;
        }

        boolean success = plugin.getEstateManager().resizeEstate(estate, direction, amount);
        if (!success) {
            player.sendMessage(ChatColor.RED + "Could not resize plot (overlap or invalid direction).");
        } else {
            player.sendMessage(ChatColor.GREEN + "Estate resized " + direction + " by " + amount + " blocks.");
        }
    }

    // ----------------------------------------------------------------------
    //  UTIL
    // ----------------------------------------------------------------------

    private boolean validateOwnerOrAdmin(Player p, Estate e) {
        if (e.getOwnerId() != null && e.getOwnerId().equals(p.getUniqueId())) {
            return true;
        }
        // If you have a nicer helper, use plugin.isAdmin(p)
        return p.hasPermission("aegisguard.admin") || plugin.isAdmin(p);
    }
}
