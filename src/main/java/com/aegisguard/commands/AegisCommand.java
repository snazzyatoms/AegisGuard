package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Root /ag handler for general player utilities:
 *   /ag wand
 *   /ag menu
 *   /ag visit
 *   /ag setspawn
 *   /ag home
 *   /ag stuck
 *   /ag rename <name>
 *   /ag setdesc <text>
 *   /ag level
 *   /ag zone
 *   /ag consume
 */
public class AegisCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender,
                           String label,
                           String subLabel,
                           String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;
        LanguageManager lang = plugin.getLanguageManager();

        // If no extra args: open main menu
        if (args.length == 0) {
            plugin.getGuiManager().openGuardianCodex(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            // -----------------------------------------------------------------
            // /ag wand  -> give player claim wand (NOT admin scepter)
            // -----------------------------------------------------------------
            case "wand":
                if (!p.hasPermission("aegis.wand")) {
                    p.sendMessage(ChatColor.RED + "You do not have permission to use the claim tool.");
                    return true;
                }
                // Use ItemManager’s official player wand
                p.getInventory().addItem(plugin.getItemManager().getPlayerWand());
                p.sendMessage(lang.getMsg(p, "wand_given"));
                return true;

            // -----------------------------------------------------------------
            // /ag menu
            // -----------------------------------------------------------------
            case "menu":
                plugin.getGuiManager().openGuardianCodex(p);
                return true;

            // -----------------------------------------------------------------
            // /ag visit
            // -----------------------------------------------------------------
            case "visit":
                plugin.getGuiManager().visit().open(p, 0, false);
                return true;

            // -----------------------------------------------------------------
            // /ag setspawn
            // -----------------------------------------------------------------
            case "setspawn":
                handleSetSpawn(p);
                return true;

            // -----------------------------------------------------------------
            // /ag home
            // -----------------------------------------------------------------
            case "home":
                handleHome(p);
                return true;

            // -----------------------------------------------------------------
            // /ag stuck
            // -----------------------------------------------------------------
            case "stuck":
                handleStuck(p);
                return true;

            // -----------------------------------------------------------------
            // /ag rename <Name>
            // -----------------------------------------------------------------
            case "rename":
                handleRename(p, args);
                return true;

            // -----------------------------------------------------------------
            // /ag setdesc <Description...>
            // -----------------------------------------------------------------
            case "setdesc":
                handleSetDesc(p, args);
                return true;

            // -----------------------------------------------------------------
            // /ag merge  (placeholder)
            // -----------------------------------------------------------------
            case "merge":
                p.sendMessage("§eMerge logic moving to EstateManager... (Coming Soon)");
                return true;

            // -----------------------------------------------------------------
            // /ag level
            // -----------------------------------------------------------------
            case "level":
                Estate lvlEstate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (lvlEstate != null) {
                    plugin.getGuiManager().leveling().open(p, lvlEstate);
                } else {
                    p.sendMessage(lang.getMsg(p, "no_plot_here"));
                }
                return true;

            // -----------------------------------------------------------------
            // /ag zone
            // -----------------------------------------------------------------
            case "zone":
                Estate zoneEstate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (zoneEstate != null) {
                    plugin.getGuiManager().zoning().open(p, zoneEstate);
                } else {
                    p.sendMessage(lang.getMsg(p, "no_plot_here"));
                }
                return true;

            // -----------------------------------------------------------------
            // /ag consume  (dev / debug)
            // -----------------------------------------------------------------
            case "consume":
                plugin.getSelection().consumeWand(p);
                return true;

            // -----------------------------------------------------------------
            // /ag help / unknown
            // -----------------------------------------------------------------
            case "help":
            default:
                sendHelp(p);
                return true;
        }
    }

    // ---------------------------------------------------------------------
    // HANDLERS
    // ---------------------------------------------------------------------

    private void handleRename(Player p, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());

        if (estate == null) {
            p.sendMessage(lang.getMsg(p, "no_plot_here"));
            return;
        }
        if (!estate.getOwnerId().equals(p.getUniqueId()) && !plugin.isAdmin(p)) {
            p.sendMessage(lang.getMsg(p, "no_permission"));
            return;
        }

        if (args.length < 2) {
            p.sendMessage("§cUsage: /ag rename <Name>");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        name = ChatColor.translateAlternateColorCodes('&', name);

        if (name.length() > 32) {
            p.sendMessage("§cName too long.");
            return;
        }

        estate.setName(name);
        p.sendMessage(lang.getMsg(p, "guild_rename_success").replace("%name%", name));
    }

    private void handleSetDesc(Player p, String[] args) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate != null && estate.getOwnerId().equals(p.getUniqueId())) {
            String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            estate.setDescription(ChatColor.translateAlternateColorCodes('&', desc));
            p.sendMessage("§eDescription updated.");
        } else {
            p.sendMessage("§cYou must stand in your Estate to set its description.");
        }
    }

    private void handleStuck(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate == null) {
            p.sendMessage("§cYou are not inside an Estate.");
            return;
        }

        Location target = p.getLocation();
        target.setX(estate.getRegion().getLowerNE().getX() - 2);
        target.setY(p.getWorld().getHighestBlockYAt(target) + 1);

        p.teleport(target);
        p.sendMessage("§e✨ Unstuck!");
    }

    private void handleSetSpawn(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate != null && estate.getOwnerId().equals(p.getUniqueId())) {
            estate.setSpawnLocation(p.getLocation());
            p.sendMessage("§aSpawn set!");
        } else {
            p.sendMessage("§cYou must stand in your Estate to set its spawn.");
        }
    }

    private void handleHome(Player p) {
        for (Estate e : plugin.getEstateManager().getAllEstates()) {
            if (e.getOwnerId() != null && e.getOwnerId().equals(p.getUniqueId())) {
                if (e.getSpawnLocation() != null) {
                    p.teleport(e.getSpawnLocation());
                } else {
                    p.teleport(e.getCenter());
                }
                p.sendMessage("§aTeleporting home...");
                return;
            }
        }
        p.sendMessage("§cYou have no homes.");
    }

    private void sendHelp(Player p) {
        p.sendMessage("§8§m------------------------");
        p.sendMessage("§bAegisGuard v1.3.0 Help");
        p.sendMessage("§e/ag menu §7- Open Dashboard");
        p.sendMessage("§e/ag claim §7- Claim Land");
        p.sendMessage("§e/ag guild §7- Guild Commands");
        p.sendMessage("§e/ag wand §7- Get Claim Wand");
        p.sendMessage("§8§m------------------------");
    }

    // Optional: if you still want a special “Aegis Scepter” item distinct
    // from ItemManager’s default player wand, you can use this and ALSO
    // teach ItemManager to recognize it. For now, not used.
    @SuppressWarnings("unused")
    private ItemStack createLegacyScepter() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Aegis Scepter");
            meta.setLore(Arrays.asList("§7Right-click: Open Menu", "§7Left-click: Select Corners"));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            rod.setItemMeta(meta);
        }
        return rod;
    }
}
