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

    // ------------------------------------------------------------
    // OPEN
    // ------------------------------------------------------------

    public void open(Player p) {
        if (p == null) return;

        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
        if (s.amount <= 0) s.amount = 100;
        if (s.mode == null) s.mode = Mode.BUY;

        // Fully language-pack-driven: fallback = key
        String title = plugin.gui().title(p, "claimblocks_exchange_gui_title", "claimblocks_exchange_gui_title");
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

        if (slot == SLOT_CLOSE) {
            sessions.remove(p.getUniqueId());
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
            boolean enabled = isExchangeEnabled();
            if (!enabled) {
                send(p, "claimblocks_exchange_disabled", "claimblocks_exchange_disabled");
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

        // Background
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, tr(p, "claimblocks_exchange_filler_name"), List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        boolean enabled = isExchangeEnabled();

        // Labels (localized, styled by state)
        String buyLabel = tr(p, "claimblocks_exchange_buy_label");
        String sellLabel = tr(p, "claimblocks_exchange_sell_label");

        String buyName = (s.mode == Mode.BUY ? "&a&l" : "&7") + buyLabel;
        String sellName = (s.mode == Mode.SELL ? "&e&l" : "&7") + sellLabel;

        String permExchange = plugin.cfg().raw().getString("claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange");
        String permSell = plugin.cfg().raw().getString("claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell");

        inv.setItem(SLOT_MODE_BUY, item(
                Material.EMERALD,
                buyName,
                trList(p, "claimblocks_exchange_buy_lore", Map.of("PERM", permExchange))
        ));

        inv.setItem(SLOT_MODE_SELL, item(
                Material.GOLD_INGOT,
                sellName,
                trList(p, "claimblocks_exchange_sell_lore", Map.of("PERM", permSell))
        ));

        // Amount controls
        List<String> minusLore = trList(p, "claimblocks_exchange_minus_lore");
        List<String> plusLore = trList(p, "claimblocks_exchange_plus_lore");

        inv.setItem(SLOT_MINUS_100, item(Material.RED_DYE, tr(p, "claimblocks_exchange_minus_100_title"), minusLore));
        inv.setItem(SLOT_MINUS_10, item(Material.RED_DYE, tr(p, "claimblocks_exchange_minus_10_title"), minusLore));
        inv.setItem(SLOT_MINUS_1, item(Material.RED_DYE, tr(p, "claimblocks_exchange_minus_1_title"), minusLore));

        inv.setItem(SLOT_PLUS_1, item(Material.LIME_DYE, tr(p, "claimblocks_exchange_plus_1_title"), plusLore));
        inv.setItem(SLOT_PLUS_10, item(Material.LIME_DYE, tr(p, "claimblocks_exchange_plus_10_title"), plusLore));
        inv.setItem(SLOT_PLUS_100, item(Material.LIME_DYE, tr(p, "claimblocks_exchange_plus_100_title"), plusLore));

        // Balances
        long avail = (blocks != null) ? blocks.getAvailableBlocks(p.getUniqueId()) : 0;
        long total = (blocks != null) ? blocks.getTotalBlocks(p.getUniqueId()) : 0;
        long spent = (blocks != null) ? blocks.getSpentBlocks(p.getUniqueId()) : 0;

        double vaultBal = (plugin.vault() != null) ? plugin.vault().balance(p) : 0.0;

        // Quote lore
        List<String> lore = new ArrayList<>();

        String state = enabled
                ? tr(p, "claimblocks_exchange_state_yes")
                : tr(p, "claimblocks_exchange_state_no");

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_enabled"),
                Map.of("STATE", state)
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_money"),
                Map.of("MONEY", money(vaultBal))
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_blocks"),
                Map.of("AVAILABLE", String.valueOf(avail))
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_totals"),
                Map.of("TOTAL", String.valueOf(total), "SPENT", String.valueOf(spent))
        ));

        lore.add(tr(p, "claimblocks_exchange_spacer"));

        if (s.mode == Mode.BUY) {
            ClaimBlockExchangeService.Quote q = exchange.quoteBuy(s.amount);

            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_buy_amount"),
                    Map.of("AMOUNT", String.valueOf(q.blocks()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_unit"),
                    Map.of("UNIT", money(q.unitPrice()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_subtotal"),
                    Map.of("SUBTOTAL", money(q.subtotal()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_fee"),
                    Map.of("FEE", money(q.fee()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_total_cost"),
                    Map.of("TOTAL", money(q.totalOrPayout()))
            ));
        } else {
            ClaimBlockExchangeService.Quote q = exchange.quoteSell(s.amount);

            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_sell_amount"),
                    Map.of("AMOUNT", String.valueOf(q.blocks()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_unit"),
                    Map.of("UNIT", money(q.unitPrice()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_gross"),
                    Map.of("GROSS", money(q.subtotal()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_fee"),
                    Map.of("FEE", money(q.fee()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_line_payout"),
                    Map.of("PAYOUT", money(q.totalOrPayout()))
            ));
        }

        inv.setItem(SLOT_INFO, item(
                Material.PAPER,
                tr(p, "claimblocks_exchange_quote_title"),
                lore
        ));

        // Confirm
        String confirmTitle = enabled
                ? tr(p, "claimblocks_exchange_confirm_title")
                : tr(p, "claimblocks_exchange_confirm_disabled_title");

        List<String> confirmLore = enabled
                ? trList(p, "claimblocks_exchange_confirm_lore")
                : trList(p, "claimblocks_exchange_confirm_disabled_lore");

        inv.setItem(SLOT_CONFIRM, item(Material.ANVIL, confirmTitle, confirmLore));

        // Close
        inv.setItem(SLOT_CLOSE, item(
                Material.BARRIER,
                tr(p, "claimblocks_exchange_close_title"),
                trList(p, "claimblocks_exchange_close_lore")
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
                send(p, "claimblocks_exchange_success", "claimblocks_exchange_success",
                        Map.of("MESSAGE", safe(res.message())));
                trySound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
            }
            case NO_PERMISSION -> {
                send(p, "claimblocks_exchange_no_permission", "claimblocks_exchange_no_permission");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case VAULT_UNAVAILABLE -> {
                send(p, "claimblocks_exchange_vault_unavailable", "claimblocks_exchange_vault_unavailable");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case WORLD_BLOCKED -> {
                send(p, "claimblocks_exchange_world_blocked", "claimblocks_exchange_world_blocked");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case COOLDOWN -> {
                send(p, "claimblocks_exchange_cooldown", "claimblocks_exchange_cooldown",
                        Map.of("SECONDS", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case DAILY_CAP -> {
                send(p, "claimblocks_exchange_daily_cap", "claimblocks_exchange_daily_cap",
                        Map.of("REMAINING", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case INSUFFICIENT_FUNDS -> {
                send(p, "claimblocks_exchange_insufficient_funds", "claimblocks_exchange_insufficient_funds",
                        Map.of("AMOUNT", money(res.dblA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case INSUFFICIENT_BLOCKS -> {
                send(p, "claimblocks_exchange_insufficient_blocks", "claimblocks_exchange_insufficient_blocks");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case SELL_LOCKED -> {
                send(p, "claimblocks_exchange_sell_locked", "claimblocks_exchange_sell_locked");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            default -> {
                send(p, "claimblocks_exchange_trade_failed", "claimblocks_exchange_trade_failed",
                        Map.of("REASON", safe(res.message())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------
    // Helpers (Codex + msg() style)
    // ------------------------------------------------------------

    private String tr(Player p, String key) {
        return plugin.gui().tr(p, key, key);
    }

    private List<String> trList(Player p, String key) {
        return plugin.gui().trList(p, key, List.of(key));
    }

    private List<String> trList(Player p, String key, Map<String, String> placeholders) {
        List<String> base = plugin.gui().trList(p, key, List.of(key));
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

        // Fully language-pack-driven: fallback should not be English.
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

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(GUIManager.color(name == null ? "" : name));
            if (lore != null) {
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
