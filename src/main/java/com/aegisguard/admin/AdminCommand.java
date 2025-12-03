package com.yourname.aegisguard.admin;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
import com.yourname.aegisguard.selection.SelectionService;
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
    private static final String[] SUB_COMMANDS = { "reload", "bypass", "menu", "convert", "wand", "setlang" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // --- CONSOLE HANDLING ---
        if (!(sender instanceof Player p)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.cfg().reload();
                // plugin.msg().reload(); // msg() is replaced by LanguageManager
                plugin.getLanguageManager().loadAllLocales();
                // plugin.store().load(); // Handled by EstateManager
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
            // Open Admin GUI (Ensure you update GuiManager to have openAdminMenu())
            // plugin.getGuiManager().openAdminMenu(p);
            p.sendMessage("§eOpening Admin Panel... (Coming Soon)");
            return true;
        }

        // --- SUBCOMMANDS ---
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.cfg().reload();
                plugin.getLanguageManager().loadAllLocales();
                plugin.getRoleManager().loadAllRoles();
                // Estate reloading logic if needed
                p.sendMessage(ChatColor.GREEN + "✔ [AegisGuard] v1.3.0 Configuration & Locales reloaded.");
                // plugin.effects().playConfirm(p);
                break;
                
            case "bypass":
                if (p.hasPermission("aegis.admin.bypass")) {
                    p.sendMessage(ChatColor.YELLOW + "⚠ Bypass Mode: ENABLED (via Permission)");
                } else {
                    p.sendMessage(ChatColor.RED + "❌ Bypass Mode: DISABLED");
                }
                break;
                
            case "menu":
                // plugin.getGuiManager().openAdminMenu(p);
                p.sendMessage("§eOpening Admin Panel... (Coming Soon)");
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
                
                // 1. Convert Logic (Use Estate Object)
                // This is a Placeholder UUID for "Server"
                // Ideally, use a constant like Estate.SERVER_UUID
                UUID serverUUID = UUID.fromString("00000000-0000-0000-0000-000000000000"); 
                
                // You will need a method in EstateManager to transfer ownership safely
                // plugin.getEstateManager().transferOwnership(estate, serverUUID, true); // true = isGuild (Server acts like Guild)
                
                // 2. Lock Down Flags
                estate.setFlag("pvp", false);
                estate.setFlag("mobs", false);
                estate.setFlag("build", false);
                estate.setFlag("safe_zone", true);
                
                // plugin.getEstateManager().saveEstate(estate); // Save changes
                
                p.sendMessage(ChatColor.GREEN + "✔ Estate converted to Server Zone.");
                // plugin.effects().playConfirm(p);
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
                // plugin.effects().playClaimSuccess(p);
                break;

            // --- SET LANGUAGE (Debug) ---
            case "setlang":
                if (args.length < 3) {
                    p.sendMessage("§cUsage: /agadmin setlang <player> <file>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    p.sendMessage("§cPlayer not found.");
                    return true;
                }
                plugin.getLanguageManager().setPlayerLang(target, args[2]);
                p.sendMessage("§aSet language for " + target.getName() + " to " + args[2]);
                break;

            default:
                p.sendMessage(ChatColor.RED + "Unknown subcommand. Usage: /agadmin <reload|bypass|convert|wand>");
        }
        return true;
    }

    private ItemStack createAdminScepter() {
        // Updated to use the new Config path if you changed it, or fallback
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
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            
            // KEY: This NBT tag tells SelectionService that this is a SERVER claim tool
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
