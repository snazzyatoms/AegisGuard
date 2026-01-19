package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
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

    private static final int SIZE = 54; // Increased to 54 for bottom row with exit button

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
    private static final int SLOT_BACK = 40;     // Arrow - back to menu
    
    // Exit button slot (bottom right)
    private static final int SLOT_EXIT = 44;     // Barrier - close GUI

    public ClaimBlockExchangeGUI(AegisGuard plugin, ClaimBlockExchangeService exchange) {
        this.plugin = plugin;
        this.exchange = exchange;
        this.blocks = plugin.getClaimBlockManager();
    }

    // ------------------------------------------------------------
    // OPEN
    // ------------------------------------------------------------

    public void open(Player p) {
        if (p == null) return;

        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
        if (s.amount <= 0) s.amount = 100;
        if (s.mode == null) s.mode = Mode.BUY;

        // Get title from language pack with color
        String title = plugin.gui().title(p, "claimblocks_exchange_gui_title", "&8ClaimBlock Exchange");
        Inventory inv = Bukkit.createInventory(new ExchangeHolder(p.getUniqueId()), SIZE, GUIManager.color(title));

        render(p, inv, s);

        p.openInventory(inv);
        trySound(p, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.1f);
    }

    // ------------------------------------------------------------
    // CLICK (called by GUIListener)
    // ------------------------------------------------------------

    public void handleClick(Player p, InventoryClickEvent e, ExchangeHolder holder) {
        if (p == null || e == null || holder == null) return;
        if (!holder.owner.equals(p.getUniqueId())) return;

        // Extra safety (GUIListener already cancels)
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());

        // Exit button - closes the menu entirely
        if (slot == SLOT_EXIT) {
            sessions.remove(p.getUniqueId());
            p.closeInventory();
            trySound(p, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        // Back button - returns to main menu
        if (slot == SLOT_BACK) {
            sessions.remove(p.getUniqueId());
            p.closeInventory();
            trySound(p, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            // Open main menu
            if (plugin.gui() != null) {
                plugin.gui().openMain(p);
            }
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
            boolean enabled = isExchangeEnabled();
            if (!enabled) {
                send(p, "claimblocks_exchange_disabled", "&cExchange is disabled in config.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }

            ClaimBlockExchangeService.Result res = (s.mode == Mode.BUY)
                    ? exchange.buyTrade(p, s.amount)
                    : exchange.sellTrade(p, s.amount);

            handleResult(p, res);

            // Re-render to update balances after transaction
            render(p, e.getInventory(), s);
        }
    }

    // ------------------------------------------------------------
    // RENDER
    // ------------------------------------------------------------

    private void render(Player p, Inventory inv, Session s) {
        inv.clear();

        // Background filler - NO lore, empty name
        ItemStack glass = filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        boolean enabled = isExchangeEnabled();

        // Labels (localized, styled by state)
        String buyLabel = tr(p, "claimblocks_exchange_buy_label", "BUY");
        String sellLabel = tr(p, "claimblocks_exchange_sell_label", "SELL");

        String buyName = (s.mode == Mode.BUY ? "&a&l" : "&7") + buyLabel;
        String sellName = (s.mode == Mode.SELL ? "&e&l" : "&7") + sellLabel;

        String permExchange = plugin.cfg().raw().getString("claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange");
        String permSell = plugin.cfg().raw().getString("claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell");

        inv.setItem(SLOT_MODE_BUY, item(
                Material.EMERALD,
                buyName,
                trList(p, "claimblocks_exchange_buy_lore", List.of("&7Buy ClaimBlocks with money.", "&8Requires: &f{PERM}"), Map.of("PERM", permExchange))
        ));

        inv.setItem(SLOT_MODE_SELL, item(
                Material.GOLD_INGOT,
                sellName,
                trList(p, "claimblocks_exchange_sell_lore", List.of("&7Sell ClaimBlocks for money.", "&8Requires: &f{PERM}"), Map.of("PERM", permSell))
        ));

        // Amount controls
        List<String> minusLore = trList(p, "claimblocks_exchange_minus_lore", List.of("&7Decrease the amount."));
        List<String> plusLore = trList(p, "claimblocks_exchange_plus_lore", List.of("&7Increase the amount."));

        inv.setItem(SLOT_MINUS_100, item(Material.RED_DYE, tr(p, "claimblocks_exchange_minus_100_title", "&c-100"), minusLore));
        inv.setItem(SLOT_MINUS_10, item(Material.RED_DYE, tr(p, "claimblocks_exchange_minus_10_title", "&c-10"), minusLore));
        inv.setItem(SLOT_MINUS_1, item(Material.RED_DYE, tr(p, "claimblocks_exchange_minus_1_title", "&c-1"), minusLore));

        inv.setItem(SLOT_PLUS_1, item(Material.LIME_DYE, tr(p, "claimblocks_exchange_plus_1_title", "&a+1"), plusLore));
        inv.setItem(SLOT_PLUS_10, item(Material.LIME_DYE, tr(p, "claimblocks_exchange_plus_10_title", "&a+10"), plusLore));
        inv.setItem(SLOT_PLUS_100, item(Material.LIME_DYE, tr(p, "claimblocks_exchange_plus_100_title", "&a+100"), plusLore));

        // Balances
        long avail = (blocks != null) ? blocks.getAvailableBlocks(p.getUniqueId()) : 0;
        long total = (blocks != null) ? blocks.getTotalBlocks(p.getUniqueId()) : 0;
        long spent = (blocks != null) ? blocks.getSpentBlocks(p.getUniqueId()) : 0;

        double vaultBal = (plugin.vault() != null) ? plugin.vault().balance(p) : 0.0;

        // Quote lore
        List<String> lore = new ArrayList<>();

        String state = enabled
                ? tr(p, "claimblocks_exchange_state_yes", "&aYes")
                : tr(p, "claimblocks_exchange_state_no", "&cNo");

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_enabled", "&7Exchange Enabled: {STATE}"),
                Map.of("STATE", state)
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_money", "&7Your Balance: &e{MONEY}"),
                Map.of("MONEY", money(vaultBal))
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_blocks", "&7ClaimBlocks: &f{AVAILABLE} &8available"),
                Map.of("AVAILABLE", String.valueOf(avail))
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_totals", "&8Total: &7{TOTAL}  &8Spent: &7{SPENT}"),
                Map.of("TOTAL", String.valueOf(total), "SPENT", String.valueOf(spent))
        ));

        lore.add(tr(p, "claimblocks_exchange_spacer", " "));

        if (s.mode == Mode.BUY) {
            ClaimBlockExchangeService.Quote q = exchange.quoteBuy(s.amount);

            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_buy_amount", "&aBuy Amount: &f{AMOUNT}"),
                    Map.of("AMOUNT", String.valueOf(q.blocks()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_unit", "&7Unit Price: &e{UNIT}"),
                    Map.of("UNIT", money(q.unitPrice()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_subtotal", "&7Subtotal: &e{SUBTOTAL}"),
                    Map.of("SUBTOTAL", money(q.subtotal()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_fee", "&7Fee: &6{FEE}"),
                    Map.of("FEE", money(q.fee()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_total_cost", "&aTotal Cost: &e{TOTAL}"),
                    Map.of("TOTAL", money(q.totalOrPayout()))
            ));
        } else {
            ClaimBlockExchangeService.Quote q = exchange.quoteSell(s.amount);

            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_sell_amount", "&eSell Amount: &f{AMOUNT}"),
                    Map.of("AMOUNT", String.valueOf(q.blocks()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_unit", "&7Unit Price: &e{UNIT}"),
                    Map.of("UNIT", money(q.unitPrice()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_gross", "&7Gross: &e{GROSS}"),
                    Map.of("GROSS", money(q.subtotal()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_fee", "&7Fee: &6{FEE}"),
                    Map.of("FEE", money(q.fee()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_payout", "&aPayout: &e{PAYOUT}"),
                    Map.of("PAYOUT", money(q.totalOrPayout()))
            ));
        }

        inv.setItem(SLOT_INFO, item(
                Material.PAPER,
                tr(p, "claimblocks_exchange_quote_title", "&b&lExchange Quote"),
                lore
        ));

        // Confirm
        String confirmTitle = enabled
                ? tr(p, "claimblocks_exchange_confirm_title", "&a&lCONFIRM")
                : tr(p, "claimblocks_exchange_confirm_disabled_title", "&c&lDISABLED");

        List<String> confirmLore = enabled
                ? trList(p, "claimblocks_exchange_confirm_lore", List.of("&7Click to confirm this exchange.", "&8Shift-click is ignored."))
                : trList(p, "claimblocks_exchange_confirm_disabled_lore", List.of("&7Set: &fclaim_blocks.exchange.enabled: true", "&8Shift-click is ignored."));

        inv.setItem(SLOT_CONFIRM, item(Material.ANVIL, confirmTitle, confirmLore));

        // Back button (slot 40 - arrow, returns to main menu)
        inv.setItem(SLOT_BACK, item(
                Material.ARROW,
                tr(p, "button_back_menu", "&e⟵ Back to Menu"),
                trList(p, "back_menu_lore", List.of("&7Return to the main panel."))
        ));
        
        // Exit button (slot 44 - barrier, closes GUI)
        inv.setItem(SLOT_EXIT, item(
                Material.BARRIER,
                tr(p, "button_exit", "&c✖ Close"),
                trList(p, "exit_lore", List.of("&7Close this menu."))
        ));
    }

    /**
     * Tiny robustness tweak:
     * - supports config values like enabled: true and enabled: "yes"
     * (some YAML parsers / configs may write "yes" as a string).
     */
    private boolean isExchangeEnabled() {
        Object raw = null;
        try {
            raw = plugin.cfg().raw().get("claim_blocks.exchange.enabled");
        } catch (Throwable ignored) {}

        if (raw instanceof Boolean b) return b;

        if (raw instanceof String s) {
            String v = s.trim().toLowerCase(Locale.ROOT);
            return v.equals("true")
                    || v.equals("yes")
                    || v.equals("y")
                    || v.equals("1")
                    || v.equals("on")
                    || v.equals("enabled");
        }

        return plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
    }

    // ------------------------------------------------------------
    // RESULT HANDLING (chat localized, service details preserved)
    // ------------------------------------------------------------

    private void handleResult(Player p, ClaimBlockExchangeService.Result res) {
        switch (res.type()) {
            case OK -> {
                send(p, "claimblocks_exchange_success", "&a[OK] Exchange complete: &f{MESSAGE}",
                        Map.of("MESSAGE", safe(res.message())));
                trySound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
            }
            case NO_PERMISSION -> {
                send(p, "claimblocks_exchange_no_permission", "&cYou do not have permission to use the exchange.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case VAULT_UNAVAILABLE -> {
                send(p, "claimblocks_exchange_vault_unavailable", "&cVault economy is not available.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case WORLD_BLOCKED -> {
                send(p, "claimblocks_exchange_world_blocked", "&cExchange is not allowed in this world.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case COOLDOWN -> {
                send(p, "claimblocks_exchange_cooldown", "&eCooldown: wait &6{SECONDS}s&e.",
                        Map.of("SECONDS", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case DAILY_CAP -> {
                send(p, "claimblocks_exchange_daily_cap", "&eDaily cap reached. &7Remaining today: &6{REMAINING}&e.",
                        Map.of("REMAINING", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case INSUFFICIENT_FUNDS -> {
                send(p, "claimblocks_exchange_insufficient_funds", "&cInsufficient funds. Need: &e{AMOUNT}",
                        Map.of("AMOUNT", money(res.dblA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case INSUFFICIENT_BLOCKS -> {
                send(p, "claimblocks_exchange_insufficient_blocks", "&cNot enough ClaimBlocks to sell.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case SELL_LOCKED -> {
                send(p, "claimblocks_exchange_sell_locked", "&eSome purchased blocks are locked. Try again later.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            default -> {
                send(p, "claimblocks_exchange_trade_failed", "&cExchange failed: &7{REASON}",
                        Map.of("REASON", safe(res.message())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------
    // Helpers (Codex + msg() style)
    // ------------------------------------------------------------

    private String tr(Player p, String key, String fallback) {
        String result = plugin.gui().tr(p, key, fallback);
        // If result equals the key itself, use fallback
        if (result == null || result.equals(key) || result.contains("[Missing")) {
            return fallback;
        }
        return result;
    }
    
    private String tr(Player p, String key) {
        return tr(p, key, key);
    }

    private List<String> trList(Player p, String key, List<String> fallback) {
        List<String> result = plugin.gui().trList(p, key, fallback);
        // If result contains the key itself or is empty, use fallback
        if (result == null || result.isEmpty() || (result.size() == 1 && result.get(0).equals(key))) {
            return fallback;
        }
        return result;
    }
    
    private List<String> trList(Player p, String key) {
        return trList(p, key, List.of());
    }

    private List<String> trList(Player p, String key, List<String> fallback, Map<String, String> placeholders) {
        List<String> base = trList(p, key, fallback);
        return applyList(base, placeholders);
    }

    private String apply(String s, Map<String, String> placeholders) {
        if (s == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return s;

        String out = s;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String k = e.getKey();
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + k + "}", v);
        }
        return out;
    }

    private List<String> applyList(List<String> list, Map<String, String> placeholders) {
        if (list == null) return List.of();
        if (placeholders == null || placeholders.isEmpty()) return list;

        List<String> out = new ArrayList<>(list.size());
        for (String line : list) out.add(apply(line, placeholders));
        return out;
    }

    private void send(Player p, String key, String fallback) {
        send(p, key, fallback, Collections.emptyMap());
    }

    private void send(Player p, String key, String fallback, Map<String, String> placeholders) {
        if (p == null) return;

        String prefix = "&8[&bAegisGuard&8]&r ";
        try {
            if (plugin.msg() != null) {
                String px = plugin.msg().get(p, "prefix");
                if (px != null && !px.isBlank() && !px.equalsIgnoreCase("prefix")) prefix = px;
            }
        } catch (Throwable ignored) {}

        String msg = null;
        try {
            if (plugin.msg() != null) msg = plugin.msg().get(p, key);
        } catch (Throwable ignored) {}

        // Use fallback if msg is null, blank, equals key, or is a missing marker
        if (msg == null || msg.isBlank() || msg.equalsIgnoreCase(key) || msg.contains("[Missing")) {
            msg = fallback;
        }

        if (msg == null || msg.trim().isEmpty()) return;

        msg = apply(msg, placeholders);

        String out = GUIManager.color(prefix + msg);
        p.spigot().sendMessage(TextComponent.fromLegacyText(out));
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "Unknown" : s;
    }

    private String money(double amt) {
        if (plugin.vault() != null) return plugin.vault().format(amt);
        return String.format("$%,.2f", amt);
    }

    private void trySound(Player p, Sound s, float v, float pitch) {
        try { p.playSound(p.getLocation(), s, v, pitch); } catch (Throwable ignored) {}
    }

    /**
     * Create a filler item with NO display name and NO lore.
     * This prevents the raw key from showing up when hovering.
     */
    private ItemStack filler(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" "); // Single space - invisible but not null
            meta.setLore(null); // No lore at all
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GUIManager.color(name == null ? "" : name));
            if (lore != null && !lore.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (String line : lore) out.add(GUIManager.color(line));
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

    // IMPORTANT: public so GUIListener can instanceof it
    public static final class ExchangeHolder implements InventoryHolder {
        public final UUID owner;
        public ExchangeHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
