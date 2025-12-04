package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // FIXED
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BiomeGUI {
    // ... (Rest of the file remains the same as previous version)
    // Just needed the import
    private final AegisGuard plugin;

    public BiomeGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class BiomeHolder implements InventoryHolder {
        private final Estate estate;
        public BiomeHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Estate estate) {
        // ... Copy your body here, the import was the main error ...
        // Use the version I provided earlier
        LanguageManager lang = plugin.getLanguageManager();
        String title = lang.getGui("title_biome_menu");
        // ...
        // Ensure getGui() and getMsg() are used correctly
        // ...
    }
    
    // ...
}
