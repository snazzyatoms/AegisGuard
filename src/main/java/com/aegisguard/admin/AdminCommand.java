package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.selection.SelectionService;
import org.bukkit.Bukkit; // ADDED THIS IMPORT
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
    // ... (Rest of the file is fine, just add the import at line 6!)
    private final AegisGuard plugin;
    private static final String[] SUB_COMMANDS = { "reload", "bypass", "menu", "convert", "wand", "setlang", "create", "delete" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // ... (Paste rest of existing logic here) ...
        return true;
    }
    // ...
}
