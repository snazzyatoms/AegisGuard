package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
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

/**
 * ClaimBlockExchangeGUI
 * - Fully Codex-aware (GUI bundle keys in guis.yml)
 * - Chat messages routed through msg() (system.yml keys) to support all languages
 * - Placeholder-ready for future languages
 */
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

        String title = plugin.gui().title(p, "claimblocks_exchange_gui_title", "&8ClaimBlocks Exchange");
        Inventory inv = Bukkit.createInventory(new ExchangeHolder(p.getUniqueId()), SIZE, title);

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

        // safety: keep GUI locked even if called without GUIListener
        e.setCancelled(true);

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

            // Hard stop if disabled OR service missing
            if (!enabled || exchange == null) {
                send(p, "claimblocks_exchange_msg_disabled", "&cExchange is disabled right now.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                render(p, e.getInventory(), s);
                return;
            }

            ClaimBlockExchangeService.Result res =
                    (s.mode == Mode.BUY) ? exchange.buyTrade(p, s.amount) : exchange.sellTrade(p, s.amount);

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
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(" "));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
        boolean usable = enabled && exchange != null;

        // Mode labels
        String buyLabel = t(p, "claimblocks_exchange_buy_label", "BUY");
        String sellLabel = t(p, "claimblocks_exchange_sell_label", "SELL");

        String buyName = (s.mode == Mode.BUY ? "&a&l" : "&7") + buyLabel;
        String sellName = (s.mode == Mode.SELL ? "&e&l" : "&7") + sellLabel;

        String permExchange = plugin.cfg().raw().getString(
                "claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange"
        );
        String permSell = plugin.cfg().raw().getString(
                "claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell"
        );

        inv.setItem(SLOT_MODE_BUY, item(
                Material.EMERALD,
                buyName,
                tl(p,
                        "claimblocks_exchange_buy_lore",
                        List.of("&7Purchase Claim Blocks with money.", "&8Permission: &f{PERM}"),
                        Map.of("PERM", permExchange)
                )
        ));

        inv.setItem(SLOT_MODE_SELL, item(
                Material.GOLD_INGOT,
                sellName,
                tl(p,
                        "claimblocks_exchange_sell_lore",
                        List.of("&7Sell Claim Blocks for money.", "&8May require: &f{PERM}"),
                        Map.of("PERM", permSell)
                )
        ));

        // Amount controls (lore localized)
        List<String> minusLore = tl(p, "claimblocks_exchange_minus_lore", List.of("&7Decrease amount"));
        List<String> plusLore = tl(p, "claimblocks_exchange_plus_lore", List.of("&7Increase amount"));

        inv.setItem(SLOT_MINUS_100, item(Material.RED_DYE, "&c-100", minusLore));
        inv.setItem(SLOT_MINUS_10, item(Material.RED_DYE, "&c-10", minusLore));
        inv.setItem(SLOT_MINUS_1, item(Material.RED_DYE, "&c-1", minusLore));

        inv.setItem(SLOT_PLUS_1, item(Material.LIME_DYE, "&a+1", plusLore));
        inv.setItem(SLOT_PLUS_10, item(Material.LIME_DYE, "&a+10", plusLore));
        inv.setItem(SLOT_PLUS_100, item(Material.LIME_DYE, "&a+100", plusLore));

        // Balances
        long avail = (blocks != null) ? blocks.getAvailableBlocks(p.getUniqueId()) : 0;
        long total = (blocks != null) ? blocks.getTotalBlocks(p.getUniqueId()) : 0;
        long spent = (blocks != null) ? blocks.getSpentBlocks(p.getUniqueId()) : 0;

        double vaultBal = (plugin.vault() != null) ? plugin.vault().balance(p) : 0.0;

        // Quote lore (fully Codex driven)
        List<String> lore = new ArrayList<>();

        String state = usable ? "&aYes" : "&cNo";

        lore.add(t(p, "claimblocks_exchange_line_enabled", "&7Exchange Enabled: {STATE}")
                .replace("{STATE}", GUIManager.color(state)));

        lore.add(t(p, "claimblocks_exchange_line_money", "&7Your Money: &e{MONEY}")
                .replace("{MONEY}", money(vaultBal)));

        lore.add(t(p, "claimblocks_exchange_line_blocks", "&7Claim Blocks: &f{AVAILABLE} &8available")
                .replace("{AVAILABLE}", String.valueOf(avail)));

        lore.add(t(p, "claimblocks_exchange_line_totals", "&8Total: &7{TOTAL}  &8Spent: &7{SPENT}")
                .replace("{TOTAL}", String.valueOf(total))
                .replace("{SPENT}", String.valueOf(spent)));

        lore.add(" ");

        if (exchange != null) {
            if (s.mode == Mode.BUY) {
                ClaimBlockExchangeService.Quote q = exchange.quoteBuy(s.amount);

                lore.add(t(p, "claimblocks_exchange_line_buy_amount", "&aBuy Amount: &f{AMOUNT}")
                        .replace("{AMOUNT}", String.valueOf(q.blocks())));

                lore.add(t(p, "claimblocks_exchange_line_unit", "&7Unit: &e{UNIT}")
                        .replace("{UNIT}", money(q.unitPrice())));

                lore.add(t(p, "claimblocks_exchange_line_subtotal", "&7Subtotal: &e{SUBTOTAL}")
                        .replace("{SUBTOTAL}", money(q.subtotal())));

                lore.add(t(p, "claimblocks_exchange_line_fee", "&7Fee: &6{FEE}")
                        .replace("{FEE}", money(q.fee())));

                lore.add(t(p, "claimblocks_exchange_line_total_cost", "&aTotal Cost: &e{TOTAL}")
                        .replace("{TOTAL}", money(q.totalOrPayout())));
            } else {
                ClaimBlockExchangeService.Quote q = exchange.quoteSell(s.amount);

                lore.add(t(p, "claimblocks_exchange_line_sell_amount", "&eSell Amount: &f{AMOUNT}")
                        .replace("{AMOUNT}", String.valueOf(q.blocks())));

                lore.add(t(p, "claimblocks_exchange_line_unit", "&7Unit: &e{UNIT}")
                        .replace("{UNIT}", money(q.unitPrice())));

                lore.add(t(p, "claimblocks_exchange_line_gross", "&7Gross: &e{GROSS}")
                        .replace("{GROSS}", money(q.subtotal())));

                lore.add(t(p, "claimblocks_exchange_line_fee", "&7Fee: &6{FEE}")
                        .replace("{FEE}", money(q.fee())));

                lore.add(t(p, "claimblocks_exchange_line_payout", "&aPayout: &e{PAYOUT}")
                        .replace("{PAYOUT}", money(q.totalOrPayout())));
            }
        } else {
            lore.add("&cExchange service is not available.");
        }

        inv.setItem(SLOT_INFO, item(
                Material.PAPER,
                t(p, "claimblocks_exchange_quote_title", "&b&lExchange Quote"),
                lore
        ));

        // Confirm button (Codex driven)
        String confirmTitle = usable
                ? t(p, "claimblocks_exchange_confirm_title", "&a&lCONFIRM")
                : t(p, "claimblocks_exchange_confirm_disabled_title", "&c&lDISABLED");

        List<String> confirmLore = usable
                ? tl(p, "claimblocks_exchange_confirm_lore",
                        List.of("&7Click to perform the trade.", "&8Shift-click is ignored."))
                : tl(p, "claimblocks_exchange_confirm_disabled_lore",
                        List.of("&7Enable: &fclaim_blocks.exchange.enabled: true", "&8Shift-click is ignored."));

        inv.setItem(SLOT_CONFIRM, item(Material.ANVIL, confirmTitle, confirmLore));

        // Close button (Codex driven)
        inv.setItem(SLOT_CLOSE, item(
                Material.BARRIER,
                t(p, "claimblocks_exchange_close_title", "&cClose"),
                tl(p, "claimblocks_exchange_close_lore", List.of("&7Return to your adventures."))
        ));
    }

    // ------------------------------------------------------------
    // RESULT HANDLING (system.yml via msg())
    // ------------------------------------------------------------

    private void handleResult(Player p, ClaimBlockExchangeService.Result res) {
        switch (res.type()) {
            case OK -> {
                // Keep service message as the body (dynamic), but still route through prefix/lang
                send(p, "claimblocks_exchange_msg_success", "&a{MESSAGE}",
                        Map.of("MESSAGE", safe(res.message())));
                trySound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
            }
            case NO_PERMISSION -> {
                send(p, "claimblocks_exchange_msg_no_permission", "&cYou do not have permission.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case VAULT_UNAVAILABLE -> {
                send(p, "claimblocks_exchange_msg_vault_unavailable", "&cVault economy is unavailable.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case WORLD_BLOCKED -> {
                send(p, "claimblocks_exchange_msg_world_blocked", "&cExchange is not allowed in this world.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case COOLDOWN -> {
                send(p, "claimblocks_exchange_msg_cooldown", "&eCooldown: wait &6{SECONDS}s&e.",
                        Map.of("SECONDS", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case DAILY_CAP -> {
                send(p, "claimblocks_exchange_msg_daily_cap", "&eDaily cap reached. Remaining today: &6{REMAINING}&e.",
                        Map.of("REMAINING", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case INSUFFICIENT_FUNDS -> {
                send(p, "claimblocks_exchange_msg_insufficient_funds", "&cNot enough money. Need: &e{AMOUNT}",
                        Map.of("AMOUNT", money(res.dblA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case INSUFFICIENT_BLOCKS -> {
                send(p, "claimblocks_exchange_msg_insufficient_blocks", "&cNot enough sellable Claim Blocks.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case SELL_LOCKED -> {
                send(p, "claimblocks_exchange_msg_sell_locked", "&eSome purchased blocks are locked. Try later.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            default -> {
                send(p, "claimblocks_exchange_msg_trade_failed", "&cTrade failed: &7{REASON}",
                        Map.of("REASON", safe(res.message())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------
    // Helpers (Codex + msg())
    // ------------------------------------------------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback, Map<String, String> placeholders) {
        return plugin.gui().trList(p, key, fallback, placeholders);
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

        if (msg == null || msg.isBlank() || msg.equalsIgnoreCase(key) || msg.contains("[Missing")) {
            msg = fallback;
        }
        if (msg == null || msg.trim().isEmpty()) return;

        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String k = e.getKey();
                String v = e.getValue() == null ? "" : e.getValue();
                msg = msg.replace("{" + k + "}", v);
            }
        }

        p.sendMessage(GUIManager.color(prefix + msg));
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

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "Unknown" : s;
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
