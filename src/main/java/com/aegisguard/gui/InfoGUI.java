package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class InfoGUI {

    private final AegisGuard plugin;

    public InfoGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class InfoHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        // ✅ Title: localized + safe fallback + color + clamp (GUIManager.title)
        String title = plugin.gui().title(
                player,
                "codex_gui_title",
                "&b✦ The Guardian Codex ✦"
        );

        Inventory inv = Bukkit.createInventory(new InfoHolder(), 45, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // --- 1. CLAIMING ---
        inv.setItem(10, GUIManager.createItem(
                Material.GOLDEN_HOE,
                plugin.gui().tr(player, "codex_claim_title", "&e§lI. Claim"),
                plugin.gui().trList(player, "codex_claim_lore", List.of(
                        "&7How to protect land:",
                        " ",
                        "&f1. &7Use &b/ag wand &7to get the tool.",
                        "&f2. &7Right-click the first corner.",
                        "&f3. &7Left-click the opposite corner.",
                        "&f4. &7Use &a/ag claim &7to buy the plot."
                ))
        ));

        // --- 2. TRAVEL ---
        inv.setItem(12, GUIManager.createItem(
                Material.ENDER_PEARL,
                plugin.gui().tr(player, "codex_travel_title", "&b§lII. Travel"),
                plugin.gui().trList(player, "codex_travel_lore", List.of(
                        "&7Fast travel commands:",
                        " ",
                        "&e/ag home &7- Teleport to your plot.",
                        "&e/ag setspawn &7- Set your plot spawn.",
                        "&e/ag visit &7- Visit other player plots."
                ))
        ));

        // --- 3. MENUS ---
        inv.setItem(14, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.gui().tr(player, "codex_menus_title", "&d§lIII. Menus"),
                plugin.gui().trList(player, "codex_menus_lore", List.of(
                        "&7Use &b/ag menu &7to manage:",
                        " ",
                        "&6🚩 Settings: &7PvP, mobs, fire and more.",
                        "&b👥 Members: &7Trusted players and roles.",
                        "&d✨ Cosmetics: &7Border particles and visuals.",
                        "&a🌿 Biomes: &7Plot biome appearance."
                ))
        ));

        // --- 4. SECURITY ---
        inv.setItem(16, GUIManager.createItem(
                Material.SHIELD,
                plugin.gui().tr(player, "codex_security_title", "&c§lIV. Security"),
                plugin.gui().trList(player, "codex_security_lore", List.of(
                        "&7Manage visitors and troublemakers:",
                        " ",
                        "&c/ag kick &7- Kick a player from your plot.",
                        "&4/ag ban &7- Ban a player from entering."
                ))
        ));

        // --- 5. ECONOMY ---
        inv.setItem(22, GUIManager.createItem(
                Material.GOLD_INGOT,
                plugin.gui().tr(player, "codex_economy_title", "&6§lV. Economy"),
                plugin.gui().trList(player, "codex_economy_lore", List.of(
                        "&7Economy basics:",
                        " ",
                        "&eUpkeep: &7Pay upkeep to keep your plot.",
                        "&aMarket: &7Buy and sell plots with others."
                ))
        ));

        // --- 6. IDENTITY ---
        inv.setItem(24, GUIManager.createItem(
                Material.NAME_TAG,
                plugin.gui().tr(player, "codex_identity_title", "&3§lVI. Identity"),
                plugin.gui().trList(player, "codex_identity_lore", List.of(
                        "&7Personalize your plot:",
                        " ",
                        "&b/ag rename &7- Set a custom name.",
                        "&e/ag stuck &7- Escape if you get stuck in blocks."
                ))
        ));

        // --- 7. ADVANCED ---
        inv.setItem(31, GUIManager.createItem(
                Material.EXPERIENCE_BOTTLE,
                plugin.gui().tr(player, "codex_advanced_title", "&5§lVII. Advanced"),
                plugin.gui().trList(player, "codex_advanced_lore", List.of(
                        "&dLeveling: &7Upgrade your plot to unlock perks.",
                        "&eZones: &7Create rental zones inside your plot."
                ))
        ));

        // --- Navigation ---
        inv.setItem(40, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.gui().tr(player, "button_back_menu", "&fBack to Menu"),
                plugin.gui().trList(player, "back_menu_lore", List.of("&7Return to the main panel."))
        ));

        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 40) {
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
        } else if (e.getSlot() == 44) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
        } else {
            if (e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                plugin.effects().playMenuFlip(player);
            }
        }
    }
}
