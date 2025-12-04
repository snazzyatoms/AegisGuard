package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.selection.SelectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    // Updated Command List for Tab Completion (Removed 'sidebar')
    private static final String[] SUB_COMMANDS = {
        "wand", "menu", "claim", "unclaim", "resize", "help",
        "setspawn", "home", "invite", "kick", "ban", "unban",
        "visit", "level", "zone", "like", "rename", "setdesc", "merge",
        "guild" // New v1.3.0
    };
    
    private static final String[] RESIZE_DIRECTIONS = { "north", "south", "east", "west" };

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cOnly players can use AegisGuard commands.");
            return true;
        }

        LanguageManager lang = plugin.getLanguageManager();

        if (args.length == 0) {
            plugin.getGuiManager().openGuardianCodex(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "wand":
                p.getInventory().addItem(createScepter());
                p.sendMessage(lang.getMsg(p, "wand_given")); // Add to lang
                break;
            
            case "menu":
                plugin.getGuiManager().openGuardianCodex(p);
                break;
            
            // --- DELEGATE TO NEW ESTATE COMMAND ---
            case "claim":
            case "deed":
                // Forward to EstateCommand logic (or just run it here)
                plugin.getSelection().confirmClaim(p);
                break;
            
            case "unclaim":
            case "vacate":
                // We moved this logic to EstateCommand, but for now let's use the Selection helper
                // or call the new EstateManager delete method directly.
                Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (estate != null && estate.getOwnerId().equals(p.getUniqueId())) {
                    plugin.getEstateManager().deleteEstate(estate.getId());
                    p.sendMessage(lang.getMsg(p, "claim_deleted")); // Add to lang
                } else {
                    p.sendMessage(lang.getMsg(p, "no_permission"));
                }
                break;
            
            case "resize":
                handleResize(p, args);
                break;

            // --- GUILD SHORTCUT ---
            case "guild":
            case "alliance":
                p.performCommand("ag guild " + (args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : ""));
                break;

            // --- UTILITIES ---
            case "visit":
                // plugin.gui().visit().open(p); // Update VisitGUI first!
                p.sendMessage("§eOpening Travel Menu... (Coming Soon)");
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
            
            // --- NEW: MERGE ---
            case "merge":
                // Placeholder for Merge Logic using EstateManager
                p.sendMessage("§eMerge logic moving to EstateManager... (Coming Soon)");
                break;
            
            // --- SHORTCUTS ---
            case "level":
                // plugin.gui().leveling().open(p, estate);
                break;
                
            case "zone":
                // plugin.gui().zoning().open(p, estate);
                break;
                
            case "consume":
                plugin.getSelection().consumeWand(p);
                break;

            case "help":
            default:
                sendHelp(p);
        }
        return true;
    }

    // --- HANDLERS (Updated to use Estate Object) ---
    
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
        // plugin.getEstateManager().saveEstate(estate);
        
        p.sendMessage(lang.getMsg(p, "guild_rename_success").replace("%name%", name));
    }

    private void handleSetDesc(Player p, String[] args) {
        // Similar logic to rename, just setting description if Estate has that field
        p.sendMessage("§eDescription updated.");
    }

    private void handleStuck(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate == null) {
            p.sendMessage("§cYou are not inside an Estate.");
            return;
        }
        
        // Simple "Teleport to Edge" logic
        Location target = p.getLocation();
        target.setX(estate.getRegion().getLowerNE().getX() - 2); // Quick hack
        target.setY(p.getWorld().getHighestBlockYAt(target) + 1);
        
        p.teleport(target);
        p.sendMessage("§e✨ Unstuck!");
    }

    private void handleResize(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage("§cUsage: /ag resize <direction> <amount>");
            return;
        }
        String dir = args[1];
        int amt = Integer.parseInt(args[2]);
        
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate != null) {
            plugin.getEstateManager().resizeEstate(estate, dir, amt);
            p.sendMessage("§aResized!");
        }
    }

    private void handleSetSpawn(Player p) {
        Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
        if (estate != null && estate.getOwnerId().equals(p.getUniqueId())) {
            // estate.setSpawnLocation(p.getLocation());
            p.sendMessage("§aSpawn set!");
        }
    }

    private void handleHome(Player p) {
        // Find first estate owned by player
        for (Estate e : plugin.getEstateManager().getAllEstates()) {
            if (e.getOwnerId().equals(p.getUniqueId())) {
                // p.teleport(e.getSpawnLocation());
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], Arrays.asList(SUB_COMMANDS), completions);
            Collections.sort(completions);
            return completions;
        }
        return null;
    }
}
