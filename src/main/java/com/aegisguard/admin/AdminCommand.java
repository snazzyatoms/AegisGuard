package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import com.aegisguard.selection.SelectionService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private static final String[] SUB_COMMANDS = { "reload", "bypass", "menu", "convert", "wand", "setlang", "create", "delete" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // --- CONSOLE HANDLING ---
        if (!(sender instanceof Player p)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.cfg().reload();
                plugin.getLanguageManager().loadAllLocales();
                sender.sendMessage("[AegisGuard] Reload complete.");
            } else {
                sender.sendMessage("[AegisGuard] GUI commands are player-only. Use 'aegisadmin reload' to reload config.");
            }
            return true;
        }

        LanguageManager lang = plugin.getLanguageManager();

        // --- PERMISSION CHECK ---
        if (!p.hasPermission("aegis.admin")) {
            p.sendMessage(lang.getMsg(p, "no_permission"));
            return true;
        }

        // --- DEFAULT: OPEN MENU ---
        if (args.length == 0) {
            plugin.getGuiManager().admin().open(p);
            return true;
        }

        // --- SUBCOMMANDS ---
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.cfg().reload();
                plugin.getLanguageManager().loadAllLocales();
                plugin.getRoleManager().loadAllRoles();
                p.sendMessage(ChatColor.GREEN + "✔ [AegisGuard] v1.3.0 Configuration & Locales reloaded.");
                break;
                
            case "bypass":
                if (p.hasPermission("aegis.admin.bypass")) {
                    p.sendMessage(ChatColor.YELLOW + "⚠ Bypass Mode: ENABLED (via Permission)");
                } else {
                    p.sendMessage(ChatColor.RED + "❌ Bypass Mode: DISABLED");
                }
                break;
                
            case "menu":
                plugin.getGuiManager().admin().open(p);
                break;
                
            // --- CONVERT TO SERVER ZONE ---
            case "convert":
                if (!p.hasPermission("aegis.convert")) { 
                    p.sendMessage(lang.getMsg(p, "no_permission")); 
                    return true; 
                }
                
                Estate estate = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (estate == null) {
                    p.sendMessage(ChatColor.RED + "❌ You must be standing in an Estate to convert it.");
                    return true;
                }
                
                // 1. Convert Logic
                // Use the Estate constant for Server UUID
                UUID serverUUID = Estate.SERVER_UUID;
                
                // Transfer ownership to Server
                plugin.getEstateManager().transferOwnership(estate, serverUUID, false); 
                
                // 2. Lock Down Flags
                estate.setFlag("pvp", false);
                estate.setFlag("mobs", false);
                estate.setFlag("build", false);
                estate.setFlag("safe_zone", true);
                
                p.sendMessage(ChatColor.GREEN + "✔ Estate converted to Server Zone.");
                break;
                
            // --- WORLDGUARD KILLER: CREATE REGION ---
            case "create":
            case "define":
                if (!p.hasPermission("aegis.admin.create")) {
                    p.sendMessage(lang.getMsg(p, "no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /agadmin create <Name>");
                    return true;
                }
                
                String regionName = args[1];
                
                // 1. Get Selection from Wand
                Cuboid selection = plugin.getSelection().getSelection(p);
                if (selection == null) {
                    p.sendMessage("§cYou must make a selection with the Sentinel Scepter first.");
                    return true;
                }
                
                // 2. Create "Server Estate"
                // Use Estate.SERVER_UUID for owner
                Estate serverEstate = plugin.getEstateManager().createEstate(p, selection, regionName, false);
                
                if (serverEstate != null) {
                    // 3. Configure it as a Safe Zone immediately
                    plugin.getEstateManager().transferOwnership(serverEstate, Estate.SERVER_UUID, false);
                    
                    serverEstate.setFlag("pvp", false);
                    serverEstate.setFlag("mobs", false);
                    serverEstate.setFlag("build", false);
                    serverEstate.setFlag("interact", false);
                    serverEstate.setFlag("safe_zone", true);
                    serverEstate.setFlag("hunger", false);
                    serverEstate.setFlag("sleep", false);
                    
                    p.sendMessage("§a✔ Server Zone '" + regionName + "' created successfully.");
                    p.sendMessage("§7(PvP, Mobs, Hunger, and Sleep have been disabled.)");
                } else {
                    p.sendMessage("§cFailed to create region (Overlap?).");
                }
                break;

            // --- WORLDGUARD KILLER: DELETE REGION ---
            case "delete":
            case "remove":
                if (!p.hasPermission("aegis.admin.delete")) {
                    p.sendMessage(lang.getMsg(p, "no_permission"));
                    return true;
                }
                
                Estate target = plugin.getEstateManager().getEstateAt(p.getLocation());
                if (target == null) {
                    p.sendMessage("§cYou are not standing in a region.");
                    return true;
                }
                
                plugin.getEstateManager().deleteEstate(target.getId());
                p.sendMessage("§c✖ Region '" + target.getName() + "' deleted.");
                break;
                
            // --- ADMIN WAND ---
            case "wand":
                if (!p.hasPermission("aegis.admin.wand")) { 
                    p.sendMessage(lang.getMsg(p, "no_permission")); 
                    return true; 
                }
                
                p.getInventory().addItem(createAdminScepter());
                p.sendMessage(ChatColor.RED + "⚡ Sentinel's Scepter Received.");
                p.sendMessage(ChatColor.GRAY + "Use this to create Server Zones.");
                break;

            // --- SET LANGUAGE ---
            case "setlang":
                if (args.length < 3) {
                    p.sendMessage("§cUsage: /agadmin setlang <player> <file>");
                    return true;
                }
                Player t = Bukkit.getPlayer(args[1]);
                if (t != null) {
                    plugin.getLanguageManager().setPlayerLang(t, args[2]);
                    p.sendMessage("§aLanguage set.");
                }
                break;

            default:
                p.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /agadmin <reload|bypass|convert|wand|create|delete>");
        }
        return true;
    }

    private ItemStack createAdminScepter() {
        String matName = plugin.getConfig().getString("admin.wand_material", "BLAZE_ROD");
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.BLAZE_ROD;
        
        ItemStack rod = new ItemStack(mat);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Sentinel's Scepter");
            meta.setLore(Arrays.asList(
                "§7A tool of absolute authority.",
                " ",
                "§eRight-Click: §fSelect Pos 1",
                "§eLeft-Click: §fSelect Pos 2",
                " ",
                "§c⚠ Creates SERVER ZONES directly."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            
            meta.getPersistentDataContainer().set(SelectionService.SERVER_WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            
            rod.setItemMeta(meta);
        }
        return rod;
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
