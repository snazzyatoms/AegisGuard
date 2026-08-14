package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Direct language picker: choose a pack instead of cycling through them.
 */
public class LanguageSelectGUI {

    private final AegisGuard plugin;
    private final NamespacedKey keyStyle;

    public LanguageSelectGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyStyle = new NamespacedKey(plugin, "aegis_lang_style");
    }

    public enum ReturnTo {
        SETTINGS, WALKTHROUGH
    }

    public static final class LanguageSelectHolder implements InventoryHolder {
        private final Plot plot;
        private final ReturnTo returnTo;
        public LanguageSelectHolder(Plot plot) { this(plot, ReturnTo.SETTINGS); }
        public LanguageSelectHolder(Plot plot, ReturnTo returnTo) {
            this.plot = plot;
            this.returnTo = returnTo == null ? ReturnTo.SETTINGS : returnTo;
        }
        public Plot getPlot() { return plot; }
        public ReturnTo getReturnTo() { return returnTo; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, null, ReturnTo.SETTINGS);
    }

    public void open(Player player, Plot plot) {
        open(player, plot, ReturnTo.SETTINGS);
    }

    public void open(Player player, Plot plot, ReturnTo returnTo) {
        if (player == null) return;
        ReturnTo dest = returnTo == null ? ReturnTo.SETTINGS : returnTo;
        if (plugin.codex() == null) {
            resumeAfterLanguage(player, plot, dest);
            return;
        }

        List<String> styles = new ArrayList<>(plugin.codex().getAvailableStyles());
        int size = styles.size() > 18 ? 54 : 27;
        int contentEnd = size - 9;

        String title = plugin.gui().title(player, "language_select_title", "&bChoose Your Language");
        Inventory inv = Bukkit.createInventory(new LanguageSelectHolder(plot, dest), size, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        String current = plugin.codex().getPlayerStyle(player);
        int slot = 0;
        for (String style : styles) {
            if (slot >= contentEnd) break;
            boolean selected = style != null && style.equalsIgnoreCase(current);
            ItemStack item = languageItem(player, style, selected);
            inv.setItem(slot, item);
            slot++;
        }

        int backSlot = size - 9;
        int exitSlot = size - 5;

        ItemStack back = GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to previous menu."))
        );
        plugin.gui().tagAction(back, "back");
        inv.setItem(backSlot, back);

        ItemStack exit = GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        );
        plugin.gui().tagAction(exit, "exit");
        inv.setItem(exitSlot, exit);

        player.openInventory(inv);
        try { if (plugin.effects() != null) plugin.effects().playMenuOpen(player); } catch (Throwable ignored) {}
    }

    public void handleClick(Player player, InventoryClickEvent e, LanguageSelectHolder holder) {
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        Plot plot = holder == null ? null : holder.getPlot();
        ReturnTo dest = holder == null ? ReturnTo.SETTINGS : holder.getReturnTo();
        String action = plugin.gui().getAction(clicked);
        if ("back".equals(action)) {
            playFlip(player);
            plugin.runMain(player, () -> resumeAfterLanguage(player, plot, dest));
            return;
        }
        if ("exit".equals(action)) {
            try { if (plugin.effects() != null) plugin.effects().playMenuClose(player); } catch (Throwable ignored) {}
            plugin.runMain(player, player::closeInventory);
            return;
        }

        String style = readStyle(clicked);
        if (style == null || style.isBlank() || plugin.codex() == null) {
            playError(player);
            return;
        }

        String current = plugin.codex().getPlayerStyle(player);
        if (style.equalsIgnoreCase(current)) {
            playFlip(player);
            plugin.runMain(player, () -> resumeAfterLanguage(player, plot, dest));
            return;
        }

        boolean applied = plugin.codex().setPlayerStyle(player, style);
        if (!applied) {
            playError(player);
            return;
        }

        String display = formatStyle(player, style);
        player.sendMessage(plugin.gui().tr(
                player,
                "language_set_to",
                "&aLanguage set to: {STYLE}",
                Map.of("STYLE", stripColors(display))
        ));
        playFlip(player);
        plugin.runMain(player, () -> resumeAfterLanguage(player, plot, dest));
    }

    private void resumeAfterLanguage(Player player, Plot plot, ReturnTo dest) {
        if (dest == ReturnTo.WALKTHROUGH && plugin.gui().walkthrough() != null) {
            plugin.gui().walkthrough().openAfterLanguageChoice(player);
            return;
        }
        plugin.gui().settings().open(player, plot);
    }

    private ItemStack languageItem(Player player, String style, boolean selected) {
        String display = formatStyle(player, style);
        List<String> lore = selected
                ? tl(player, "language_select_current_lore", List.of(
                        "&aCurrently selected.",
                        "&7Menus and messages already",
                        "&7use this language."))
                : tl(player, "language_select_option_lore", List.of(
                        "&7Switch menus and messages",
                        "&7to this language.",
                        " ",
                        "&eClick to use this language."));

        ItemStack item = GUIManager.createItem(iconFor(style, selected), display, lore);
        tagStyle(item, style);
        plugin.gui().tagAction(item, "select");
        if (selected) glow(item);
        return item;
    }

    private Material iconFor(String style, boolean selected) {
        if (selected) return Material.LIME_CONCRETE;
        if (style == null) return Material.BOOK;
        return switch (style.toLowerCase(Locale.ROOT)) {
            case "old_english" -> Material.ENCHANTED_BOOK;
            case "modern_english" -> Material.BOOK;
            case "spanish_mx" -> Material.RED_CONCRETE;
            case "spanish_ar" -> Material.LIGHT_BLUE_CONCRETE;
            case "portuguese_br" -> Material.GREEN_CONCRETE;
            case "french_fr" -> Material.BLUE_CONCRETE;
            case "italian_it" -> Material.WHITE_CONCRETE;
            case "german_de" -> Material.YELLOW_CONCRETE;
            case "polish_pl" -> Material.RED_WOOL;
            default -> Material.PAPER;
        };
    }

    private void tagStyle(ItemStack item, String style) {
        if (item == null || style == null || style.isBlank()) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyStyle, PersistentDataType.STRING, style.trim().toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private String readStyle(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String value = meta.getPersistentDataContainer().get(keyStyle, PersistentDataType.STRING);
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    String formatStyle(Player player, String style) {
        if (style == null || style.isEmpty()) return t(player, "style_old_english", "&dOld English");
        return switch (style.toLowerCase(Locale.ROOT)) {
            case "old_english" -> t(player, "style_old_english", "&dOld English");
            case "modern_english" -> t(player, "style_modern_english", "&aModern English");
            case "spanish_mx" -> t(player, "style_spanish_mx", "&bEspañol (México)");
            case "spanish_ar" -> t(player, "style_spanish_ar", "&bEspañol (Argentina)");
            case "portuguese_br" -> t(player, "style_portuguese_br", "&bPortuguês (Brasil)");
            case "french_fr" -> t(player, "style_french_fr", "&bFrançais");
            case "italian_it" -> t(player, "style_italian_it", "&bItaliano");
            case "german_de" -> t(player, "style_german_de", "&bDeutsch");
            case "polish_pl" -> t(player, "style_polish_pl", "&bPolski");
            default -> "&f" + pretty(style);
        };
    }

    private String pretty(String raw) {
        String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) return raw;
        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.length() > 1 ? p.substring(1).toLowerCase(Locale.ROOT) : "")
                    .append(' ');
        }
        return out.toString().trim();
    }

    private static String stripColors(String colored) {
        if (colored == null) return "";
        return colored.replaceAll("(?i)[&§][0-9A-FK-ORX]", "");
    }

    private void playFlip(Player p) {
        try { if (plugin.effects() != null) plugin.effects().playMenuFlip(p); } catch (Throwable ignored) {}
    }

    private void playError(Player p) {
        try { if (plugin.effects() != null) plugin.effects().playError(p); } catch (Throwable ignored) {}
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }
}
