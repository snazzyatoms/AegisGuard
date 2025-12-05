package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.selection.SelectionService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

// FIX: Implements SubCommand (Not CommandExecutor)
public class AegisCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player p, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();

        // If routed here with empty args (shouldn't happen via CommandHandler but safe to check)
        if (args.length == 0) {
            plugin.getGuiManager().openGuardianCodex(p);
            return;
        }

        String sub = args[0].toLowerCase();

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
            
            case "level":
                Estate lvlEstate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (lvlEstate != null) plugin.getGuiManager().leveling().open(p, lvlEstate);
                else p.sendMessage(lang.getMsg(p, "no_plot_here"));
                break;
                
            case "zone":
                Estate zoneEstate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (zoneEstate != null) plugin.getGuiManager().zoning().open(p, zoneEstate);
                else p.sendMessage(lang.getMsg(p, "no_plot_here"));
                break;
                
            case "consume":
                plugin.getSelection().consumeWand(p);
                break;

            case "help":
            default:
                sendHelp(p);
        }
    }

    // --- HANDLERS ---
    
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
        }
    }

    private void handleStuck(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate == null) {
            p.sendMessage("§cYou are not inside an Estate.");
            return;
        }
        
        Location target = p.getLocation();
        // Teleport slightly outside
        target.setX(estate.getRegion().getLowerNE().getX() - 2);
        target.setY(p.getWorld().getHighestBlockYAt(target) + 1);
        
        p.teleport(target);
        p.sendMessage("§e✨ Unstuck!");
    }

    private void handleResize(Player p, String[] args) {
        // Handled by EstateCommand now
    }

    private void handleSetSpawn(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate != null && estate.getOwnerId().equals(p.getUniqueId())) {
            estate.setSpawnLocation(p.getLocation());
            p.sendMessage("§aSpawn set!");
        }
    }

    private void handleHome(Player p) {
        for (Estate e : plugin.getEstateManager().getAllEstates()) {
            if (e.getOwnerId().equals(p.getUniqueId())) {
                if (e.getSpawnLocation() != null) p.teleport(e.getSpawnLocation());
                else p.teleport(e.getCenter());
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
            meta.getPersistentDataContainer().set(SelectionService.WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            rod.setItemMeta(meta);
        }
        return rod;
    }
    
    private void sendHelp(Player p) {
        p.sendMessage("§8§m------------------------");
        p.sendMessage("§bAegisGuard v1.3.0 Help");
        p.sendMessage("§e/ag menu §7- Open Dashboard");
        p.sendMessage("§e/ag claim §7- Claim Land");
        p.sendMessage("§e/ag guild §7- Guild Commands");
        p.sendMessage("§8§m------------------------");
    }
}
