package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimBlockExchangeGUI {

    private final AegisGuard plugin;
    private final ClaimBlockExchangeService exchange;
    private final ClaimBlockManager blocks;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private static final int SIZE = 45;

    private static final int SLOT_MODE_BUY = 10;
    private static final int SLOT_MODE_SELL = 12;
    private static final int SLOT_INFO = 13;

    private static final int SLOT_MINUS_100 = 19;
    private static final int SLOT_MINUS_10 = 20;
    private static final int SLOT_MINUS_1 = 21;

    private static final int SLOT_PLUS_1 = 23;
    private static final int SLOT_PLUS_10 = 24;
    private static final int SLOT_PLUS_100 = 25;

    private static final int SLOT_CONFIRM = 31;
    private static final int SLOT_CLOSE = 40;

    public ClaimBlockExchangeGUI(AegisGuard plugin, ClaimBlockExchangeService exchange) {
        this.plugin = plugin;
        this.exchange = exchange;
        this.blocks = plugin.getClaimBlockManager();
    }

    public void open(Player p) {
        if (p == null) return;

        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
        if (s.amount <= 0) s.amount = 100;
        if (s.mode == null) s.mode = Mode.BUY;

        Inventory inv = Bukkit.createInventory(new ExchangeHolder(p.getUniqueId()), SIZE, color("&8ClaimBlocks Exchange"));
        render(p, inv, s);
        p.openInventory(inv);
        trySound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.1f);
    }

    public void handleClick(Player p, InventoryClickEvent e, ExchangeHolder holder) {
        if (p == null || e == null || holder == null) return;
        if (!holder.owner.equals(p.getUniqueId())) return;

        int slot = e.getRawSlot();
        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());

        if (slot == SLOT_CLOSE) {
            p.closeInventory();
            return;
        }

        if (slot == SLOT_MODE_BUY) {
            s.mode = Mode.BUY;
            trySound(p, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            render(p, e.getInventory(), s);
            return;
        }

        if (slot == SLOT_MODE_SELL) {
            s.mode = Mode.SELL;
            trySound(p, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            render(p, e.getInventory(), s);
            return;
        }

        long delta = switch (slot) {
            case SLOT_MINUS_100 -> -100;
            case SLOT_MINUS_10 -> -10;
            case SLOT_MINUS_1 -> -1;
            case SLOT_PLUS_1 -> 1;
            case SLOT_PLUS_10 -> 10;
            case SLOT_PLUS_100 -> 100;
            default -> 0;
        };

        if (delta != 0) {
            s.amount = Math.max(1, s.amount + delta);
            trySound(p, Sound.UI_BUTTON_CLICK, 0.6f, delta > 0 ? 1.25f : 0.85f);
            render(p, e.getInventory(), s);
            return;
        }

        if (slot == SLOT_CONFIRM) {
            boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
            if (!enabled) {
                msg(p, "&cExchange is disabled in config.yml.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }

            ClaimBlockExchangeService.Result res;
            if (s.mode == Mode.BUY) {
                res = exchange.buyTrade(p, s.amount);
            } else {
                res = exchange.sellTrade(p, s.amount);
            }

            handleResult(p, res);

            // Re-render to update balances after transaction
            render(p, e.getInventory(), s);
        }
    }

    private void render(Player p, Inventory inv, Session s) {
        inv.clear();

        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, "&0", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);

        inv.setItem(SLOT_MODE_BUY, item(
                Material.EMERALD,
                (s.mode == Mode.BUY ? "&a&lBUY" : "&7BUY"),
                List.of(
                        "&7Purchase Claim Blocks with money.",
                        "&8Permission: &f" + plugin.cfg().raw().getString("claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange")
                )
        ));

        inv.setItem(SLOT_MODE_SELL, item(
                Material.GOLD_INGOT,
                (s.mode == Mode.SELL ? "&e&lSELL" : "&7SELL"),
                List.of(
                        "&7Sell Claim Blocks for money.",
                        "&8May require: &f" + plugin.cfg().raw().getString("claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell")
                )
        ));

        inv.setItem(SLOT_MINUS_100, item(Material.RED_DYE, "&c-100", List.of("&7Decrease amount")));
        inv.setItem(SLOT_MINUS_10, item(Material.RED_DYE, "&c-10", List.of("&7Decrease amount")));
        inv.setItem(SLOT_MINUS_1, item(Material.RED_DYE, "&c-1", List.of("&7Decrease amount")));

        inv.setItem(SLOT_PLUS_1, item(Material.LIME_DYE, "&a+1", List.of("&7Increase amount")));
        inv.setItem(SLOT_PLUS_10, item(Material.LIME_DYE, "&a+10", List.of("&7Increase amount")));
        inv.setItem(SLOT_PLUS_100, item(Material.LIME_DYE, "&a+100", List.of("&7Increase amount")));

        long avail = (blocks != null) ? blocks.getAvailableBlocks(p.getUniqueId()) : 0;
        long total = (blocks != null) ? blocks.getTotalBlocks(p.getUniqueId()) : 0;
        long spent = (blocks != null) ? blocks.getSpentBlocks(p.getUniqueId()) : 0;

        double vaultBal = (plugin.vault() != null) ? plugin.vault().balance(p) : 0.0;

        List<String> lore = new ArrayList<>();
        lore.add("&7Exchange Enabled: " + (enabled ? "&aYes" : "&cNo"));
        lore.add("&7Your Money: &e" + money(vaultBal));
        lore.add("&7Claim Blocks: &f" + avail + " &8available");
        lore.add("&8Total: &7" + total + "  &8Spent: &7" + spent);
        lore.add(" ");

        if (s.mode == Mode.BUY) {
            ClaimBlockExchangeService.Quote q = exchange.quoteBuy(s.amount);
            lore.add("&aBuy Amount: &f" + q.blocks());
            lore.add("&7Unit: &e" + money(q.unitPrice()));
            lore.add("&7Subtotal: &e" + money(q.subtotal()));
            lore.add("&7Fee: &6" + money(q.fee()));
            lore.add("&aTotal Cost: &e" + money(q.totalOrPayout()));
        } else {
            ClaimBlockExchangeService.Quote q = exchange.quoteSell(s.amount);
            lore.add("&eSell Amount: &f" + q.blocks());
            lore.add("&7Unit: &e" + money(q.unitPrice()));
            lore.add("&7Gross: &e" + money(q.subtotal()));
            lore.add("&7Fee: &6" + money(q.fee()));
            lore.add("&aPayout: &e" + money(q.totalOrPayout()));
        }

        inv.setItem(SLOT_INFO, item(Material.PAPER, "&b&lExchange Quote", lore));

        inv.setItem(SLOT_CONFIRM, item(
                Material.ANVIL,
                (enabled ? "&a&lCONFIRM" : "&c&lDISABLED"),
                List.of(
                        enabled ? "&7Click to perform the trade." : "&7Enable: &fclaim_blocks.exchange.enabled: true",
                        "&8Shift-click is ignored."
                )
        ));

        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, "&cClose", List.of("&7Return to your adventures.")));
    }

    private void handleResult(Player p, ClaimBlockExchangeService.Result res) {
        switch (res.type()) {
            case OK -> {
                msg(p, "&a" + res.message());
                trySound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
            }
            case NO_PERMISSION -> {
                msg(p, "&cYou do not have permission.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case VAULT_UNAVAILABLE -> {
                msg(p, "&cVault economy is unavailable.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case WORLD_BLOCKED -> {
                msg(p, "&cExchange is not allowed in this world.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case COOLDOWN -> {
                msg(p, "&eCooldown: wait &6" + res.longA() + "s&e.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case DAILY_CAP -> {
                msg(p, "&eDaily cap reached. Remaining today: &6" + res.longA() + "&e.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case INSUFFICIENT_FUNDS -> {
                msg(p, "&cNot enough money. Need: &e" + money(res.dblA()));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case INSUFFICIENT_BLOCKS -> {
                msg(p, "&cNot enough sellable Claim Blocks.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case SELL_LOCKED -> {
                msg(p, "&eSome purchased blocks are locked. Try later.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            default -> {
                msg(p, "&cTrade failed: &7" + res.message());
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }
    }

    private String money(double amt) {
        if (plugin.vault() != null) return plugin.vault().format(amt);
        return String.format("$%,.2f", amt);
    }

    private void msg(Player p, String s) {
        if (p == null || s == null) return;
        String out = ChatColor.translateAlternateColorCodes('&', s);
        p.spigot().sendMessage(TextComponent.fromLegacyText(out));
    }

    private void trySound(Player p, Sound s, float v, float pitch) {
        try { p.playSound(p.getLocation(), s, v, pitch); } catch (Throwable ignored) {}
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> out = new ArrayList<>();
                for (String line : lore) out.add(color(line));
                meta.setLore(out);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
        }
        return it;
    }

    private enum Mode { BUY, SELL }

    private static final class Session {
        Mode mode = Mode.BUY;
        long amount = 100;
    }

    // ✅ IMPORTANT: public so GUIListener can instanceof it
    public static final class ExchangeHolder implements InventoryHolder {
        public final UUID owner;
        public ExchangeHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
