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
        if (!(sender instanceof Player p)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.cfg().reload();
                plugin.getLanguageManager().loadAllLocales();
                sender.sendMessage("[AegisGuard] Reload complete.");
            }
            return true;
        }

        LanguageManager lang = plugin.getLanguageManager();

        if (!p.hasPermission("aegis.admin")) {
            p.sendMessage(lang.getMsg(p, "no_permission"));
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiManager().admin().open(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.cfg().reload();
                plugin.getLanguageManager().loadAllLocales();
                plugin.getRoleManager().loadAllRoles();
                p.sendMessage(ChatColor.GREEN + "✔ [AegisGuard] Reloaded.");
                break;
                
            case "bypass":
                // Toggle Bypass Mode (Critical for editing Server Zones)
                boolean current = p.hasPermission("aegis.admin.bypass"); // Check existing (This usually requires a Permission Plugin toggle, or we store metadata)
                // For simplicity, we tell them to use their permission plugin or we simulate it via metadata if needed.
                // Assuming 'aegis.admin.bypass' is a permission node managed by LuckPerms.
                p.sendMessage(ChatColor.YELLOW + "ℹ To edit Server Zones, ensure you have 'aegis.admin.bypass' set to true in LuckPerms, or use /lp user <name> permission set aegis.admin.bypass true");
                break;
                
            case "menu":
                plugin.getGuiManager().admin().open(p);
                break;

            // --- WORLDGUARD KILLER: CREATE SERVER ZONE ---
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
                
                Cuboid selection = plugin.getSelection().getSelection(p);
                if (selection == null) {
                    p.sendMessage("§cYou must make a selection with the Sentinel Scepter first.");
                    return true;
                }
                
                // 1. Create Estate
                Estate serverEstate = plugin.getEstateManager().createEstate(p, selection, regionName, false);
                
                if (serverEstate != null) {
                    // 2. Convert to SERVER OWNERSHIP
                    plugin.getEstateManager().transferOwnership(serverEstate, Estate.SERVER_UUID, false);
                    
                    // 3. APPLY "HEAVY DUTY" PROTECTIONS (Default: Deny Everything)
                    serverEstate.setFlag("build", false);       // No Building
                    serverEstate.setFlag("interact", false);    // No Buttons/Doors
                    serverEstate.setFlag("pvp", false);         // No Fighting
                    serverEstate.setFlag("mobs", false);        // No Mobs (Despawn)
                    serverEstate.setFlag("tnt-damage", false);  // No TNT
                    serverEstate.setFlag("fire-spread", false); // No Fire
                    serverEstate.setFlag("hunger", false);      // No Hunger
                    serverEstate.setFlag("sleep", false);       // No Sleeping
                    serverEstate.setFlag("safe_zone", true);    // God Mode Active
                    
                    // Visuals
                    serverEstate.setBorderParticle("SOUL_FIRE_FLAME"); // Distinct Admin Look
                    
                    // Save
                    // plugin.getEstateManager().saveEstate(serverEstate);
                    
                    p.sendMessage("§a✔ Server Zone '" + regionName + "' created.");
                    p.sendMessage("§7(Safe Zone: ON | Mobs: OFF | PvP: OFF | Build: OFF)");
                    p.sendMessage("§eTo edit this zone, ensure you have 'aegis.admin.bypass' permission.");
                } else {
                    p.sendMessage("§cFailed to create region (Overlap?).");
                }
                break;

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
                // Allow deleting Server Zones if Admin
                if (target.isServerZone() && !p.hasPermission("aegis.admin.create")) {
                    p.sendMessage("§cOnly Head Admins can delete Server Zones.");
                    return true;
                }
                
                plugin.getEstateManager().deleteEstate(target.getId());
                p.sendMessage("§c✖ Region '" + target.getName() + "' deleted.");
                break;
                
            case "wand":
                p.getInventory().addItem(createAdminScepter());
                p.sendMessage(ChatColor.RED + "⚡ Sentinel's Scepter Received.");
                break;

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
```

### 2. ⚙️ `GUIListener.java` (Allow Admins to Edit Server Zones)
I updated the `manage_current_estate` logic. Previously, it only allowed the **Owner** to edit flags. I added a check so that **Admins** can also edit flags, even if they don't own the land (which is required for Server Zones since the "Owner" is a UUID `0000` placeholder).

**Location:** `src/main/java/com/aegisguard/listeners/GUIListener.java`

```java
// Inside handleMainMenuClick method...

        switch (action) {
            // ... (other cases) ...

            // --- ESTATE MANAGEMENT ---
            case "manage_current_estate":
            case "manage_flags":
                // FIX: Allow Admins to open Flag Menu for Server Zones
                if (isOwner || plugin.isAdmin(player)) { 
                    plugin.getGuiManager().flags().open(player, estate);
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                }
                break;

            // ... (rest of cases) ...
