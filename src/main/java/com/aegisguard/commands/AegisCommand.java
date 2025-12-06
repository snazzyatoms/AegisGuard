package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.selection.SelectionService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

// Implements CommandHandler.SubCommand (new shape)
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
            sender.sendMessage("§cOnly players may use this command.");
            return true;
        }

        Player p = (Player) sender;
        LanguageManager lang = plugin.getLanguageManager();

        // subLabel is the keyword registered in CommandHandler (e.g. "menu", "home", "rename", "consume")
        String sub = subLabel.toLowerCase();

        switch (sub) {
            // --- GENERAL TOOLS ---
            case "wand":
                p.getInventory().addItem(createScepter());
                p.sendMessage(lang.getMsg(p, "wand_given"));
                break;

            case "menu":
                plugin.getGuiManager().openGuardianCodex(p);
                break;

            // --- UTILITIES ---
            case "visit":
                plugin.getGuiManager().visit().open(p, 0, false);
                break;

            case "setspawn":
                handleSetSpawn(p);
                break;

            case "home":
                handleHome(p);
                break;

            case "stuck":
                handleStuck(p);
                break;

            case "rename":
                handleRename(p, args);
                break;

            case "setdesc":
                handleSetDesc(p, args);
                break;

            case "merge":
                p.sendMessage("§eMerge logic moving to EstateManager... (Coming Soon)");
                break;

            case "level": {
                Estate lvlEstate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (lvlEstate != null) {
                    plugin.getGuiManager().leveling().open(p, lvlEstate);
                } else {
                    p.sendMessage(lang.getMsg(p, "no_plot_here"));
                }
                break;
            }

            case "zone": {
                Estate zoneEstate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (zoneEstate != null) {
                    plugin.getGuiManager().zoning().open(p, zoneEstate);
                } else {
                    p.sendMessage(lang.getMsg(p, "no_plot_here"));
                }
                break;
            }

            case "consume":
                // Consume the selection wand
                plugin.getSelection().consumeWand(p);
                break;

            case "help":
            default:
                sendHelp(p);
                break;
        }

        return true;
    }

    // --- HANDLERS ---

    /**
     * /ag rename <Name...>
     * In this new model, args already exclude the subLabel ("rename"),
     * so args[0] is the first word of the new name.
     */
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

        if (args.length == 0) {
            p.sendMessage("§cUsage: /ag rename <Name>");
            return;
        }

        String name = String.join(" ", args);
        name = ChatColor.translateAlternateColorCodes('&', name);

        if (name.length() > 32) {
            p.sendMessage("§cName too long.");
            return;
        }

        estate.setName(name);
        p.sendMessage(lang.getMsg(p, "guild_rename_success").replace("%name%", name));
    }

    /**
     * /ag setdesc <Description...>
     * args contains only the description parts.
     */
    private void handleSetDesc(Player p, String[] args) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate == null || !estate.getOwnerId().equals(p.getUniqueId())) {
            p.sendMessage("§cYou must be the owner of this Estate to change its description.");
            return;
        }

        if (args.length == 0) {
            p.sendMessage("§cUsage: /ag setdesc <Description>");
            return;
        }

        String desc = String.join(" ", args);
        estate.setDescription(ChatColor.translateAlternateColorCodes('&', desc));
        p.sendMessage("§eDescription updated.");
    }

    private void handleStuck(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate == null) {
            p.sendMessage("§cYou are not inside an Estate.");
            return;
        }

        Location target = p.getLocation();
        // Teleport slightly outside on X, then up to surface
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
            p.sendMessage("§cYou must own this Estate to set its spawn.");
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

    private ItemStack createScepter() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Aegis Scepter");
            meta.setLore(Arrays.asList("§7Right-click: Open Menu", "§7Left-click: Select Corners"));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            // Uses SelectionService.WAND_KEY (we'll fix this once we see SelectionService)
            meta.getPersistentDataContainer().set(SelectionService.WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            rod.setItemMeta(meta);
        }
        return rod;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§8§m------------------------");
        p.sendMessage("§bAegisGuard v1.3.0 Help");
        p.sendMessage("§e/ag menu §7- Open Dashboard");
        p.sendMessage("§e/ag home §7- Teleport to your Estate");
        p.sendMessage("§e/ag setspawn §7- Set Estate spawn");
        p.sendMessage("§e/ag rename <name> §7- Rename your Estate");
        p.sendMessage("§e/ag setdesc <text> §7- Set Estate description");
        p.sendMessage("§8§m------------------------");
    }
}
