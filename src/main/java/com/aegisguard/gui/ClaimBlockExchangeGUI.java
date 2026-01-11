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

        // Prefer new key; fall back to older key if pack still uses it
        String title = plugin.gui().title(
                p,
                "claimblocks_exchange_gui_title",
                plugin.gui().title(p, "claimblocks_exchange_title", "&8ClaimBlocks Exchange")
        );

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
            boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
            if (!enabled) {
                send(p, "claimblocks_exchange_disabled", "&cExchange is disabled in config.yml.");
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
    // RENDER (100% language-pack-driven for GUI text)
    // ------------------------------------------------------------

    private void render(Player p, Inventory inv, Session s) {
        inv.clear();

        // Background
        String fillerName = tr(p, "claimblocks_exchange_filler_name", " ");
        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, fillerName, trList(p, "claimblocks_exchange_filler_lore", List.of()));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);

        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);

        // Labels (localized)
        String buyLabel = tr(p, "claimblocks_exchange_buy_label", missingKey("claimblocks_exchange_buy_label"));
        String sellLabel = tr(p, "claimblocks_exchange_sell_label", missingKey("claimblocks_exchange_sell_label"));

        // Styling kept in-code (language is the label itself)
        String buyName = (s.mode == Mode.BUY ? "&a&l" : "&7") + buyLabel;
        String sellName = (s.mode == Mode.SELL ? "&e&l" : "&7") + sellLabel;

        String permExchange = plugin.cfg().raw().getString("claim_blocks.exchange.permissions.exchange", "aegis.claimblocks.exchange");
        String permSell = plugin.cfg().raw().getString("claim_blocks.exchange.permissions.sell", "aegis.claimblocks.sell");

        inv.setItem(SLOT_MODE_BUY, item(
                Material.EMERALD,
                buyName,
                trListWith(p, "claimblocks_exchange_buy_lore",
                        List.of(missingKey("claimblocks_exchange_buy_lore")),
                        Map.of("PERM", permExchange))
        ));

        inv.setItem(SLOT_MODE_SELL, item(
                Material.GOLD_INGOT,
                sellName,
                trListWith(p, "claimblocks_exchange_sell_lore",
                        List.of(missingKey("claimblocks_exchange_sell_lore")),
                        Map.of("PERM", permSell))
        ));

        // Amount controls (all from lang pack)
        setAmountButton(p, inv, SLOT_MINUS_100, -100);
        setAmountButton(p, inv, SLOT_MINUS_10, -10);
        setAmountButton(p, inv, SLOT_MINUS_1, -1);

        setAmountButton(p, inv, SLOT_PLUS_1, 1);
        setAmountButton(p, inv, SLOT_PLUS_10, 10);
        setAmountButton(p, inv, SLOT_PLUS_100, 100);

        // Balances
        long avail = (blocks != null) ? blocks.getAvailableBlocks(p.getUniqueId()) : 0;
        long total = (blocks != null) ? blocks.getTotalBlocks(p.getUniqueId()) : 0;
        long spent = (blocks != null) ? blocks.getSpentBlocks(p.getUniqueId()) : 0;

        double vaultBal = (plugin.vault() != null) ? plugin.vault().balance(p) : 0.0;

        // Quote lore
        List<String> lore = new ArrayList<>();

        // Use global toggle labels (already localized in your packs); fall back to exchange yes/no if you want them separate
        String state = enabled
                ? tr(p, "toggle_on", tr(p, "claimblocks_exchange_state_yes", "&aYes"))
                : tr(p, "toggle_off", tr(p, "claimblocks_exchange_state_no", "&cNo"));

        // Prefer new key; fall back to older key
        lore.add(apply(
                tr(p, "claimblocks_exchange_line_enabled",
                        tr(p, "claimblocks_exchange_info_enabled", missingKey("claimblocks_exchange_line_enabled"))),
                Map.of("STATE", state)
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_money",
                        tr(p, "claimblocks_exchange_info_money", missingKey("claimblocks_exchange_line_money"))),
                Map.of("MONEY", money(vaultBal))
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_blocks",
                        tr(p, "claimblocks_exchange_info_blocks", missingKey("claimblocks_exchange_line_blocks"))),
                Map.of("BLOCKS", String.valueOf(avail))
        ));

        lore.add(apply(
                tr(p, "claimblocks_exchange_line_total_spent",
                        tr(p, "claimblocks_exchange_info_total_spent", missingKey("claimblocks_exchange_line_total_spent"))),
                Map.of("TOTAL", String.valueOf(total), "SPENT", String.valueOf(spent))
        ));

        // Spacer from lang (optional)
        lore.add(tr(p, "claimblocks_exchange_spacer", " "));

        if (s.mode == Mode.BUY) {
            ClaimBlockExchangeService.Quote q = exchange.quoteBuy(s.amount);

            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_buy_amount", missingKey("claimblocks_exchange_quote_buy_amount")),
                    Map.of("BLOCKS", String.valueOf(q.blocks()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_unit", missingKey("claimblocks_exchange_quote_unit")),
                    Map.of("AMOUNT", money(q.unitPrice()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_subtotal", missingKey("claimblocks_exchange_quote_subtotal")),
                    Map.of("AMOUNT", money(q.subtotal()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_fee", missingKey("claimblocks_exchange_quote_fee")),
                    Map.of("AMOUNT", money(q.fee()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_total_cost", missingKey("claimblocks_exchange_quote_total_cost")),
                    Map.of("AMOUNT", money(q.totalOrPayout()))
            ));
        } else {
            ClaimBlockExchangeService.Quote q = exchange.quoteSell(s.amount);

            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_sell_amount", missingKey("claimblocks_exchange_quote_sell_amount")),
                    Map.of("BLOCKS", String.valueOf(q.blocks()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_unit", missingKey("claimblocks_exchange_quote_unit")),
                    Map.of("AMOUNT", money(q.unitPrice()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_gross", missingKey("claimblocks_exchange_quote_gross")),
                    Map.of("AMOUNT", money(q.subtotal()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_fee", missingKey("claimblocks_exchange_quote_fee")),
                    Map.of("AMOUNT", money(q.fee()))
            ));
            lore.add(apply(
                    tr(p, "claimblocks_exchange_quote_payout", missingKey("claimblocks_exchange_quote_payout")),
                    Map.of("AMOUNT", money(q.totalOrPayout()))
            ));
        }

        inv.setItem(SLOT_INFO, item(
                Material.PAPER,
                tr(p, "claimblocks_exchange_quote_title", missingKey("claimblocks_exchange_quote_title")),
                lore
        ));

        // Confirm (fully lang-driven)
        String confirmLabel = enabled
                ? tr(p, "claimblocks_exchange_confirm_name_enabled",
                        tr(p, "claimblocks_exchange_confirm_enabled", missingKey("claimblocks_exchange_confirm_name_enabled")))
                : tr(p, "claimblocks_exchange_confirm_name_disabled",
                        tr(p, "claimblocks_exchange_confirm_disabled", missingKey("claimblocks_exchange_confirm_name_disabled")));

        List<String> confirmLore = enabled
                ? trList(p, "claimblocks_exchange_confirm_lore_enabled",
                        List.of(missingKey("claimblocks_exchange_confirm_lore_enabled")))
                : trList(p, "claimblocks_exchange_confirm_lore_disabled",
                        List.of(missingKey("claimblocks_exchange_confirm_lore_disabled")));

        inv.setItem(SLOT_CONFIRM, item(Material.ANVIL, confirmLabel, confirmLore));

        // Close (fully lang-driven)
        inv.setItem(SLOT_CLOSE, item(
                Material.BARRIER,
                tr(p, "claimblocks_exchange_close_name",
                        tr(p, "claimblocks_exchange_close", missingKey("claimblocks_exchange_close_name"))),
                trList(p, "claimblocks_exchange_close_lore",
                        List.of(missingKey("claimblocks_exchange_close_lore")))
        ));
    }

    private void setAmountButton(Player p, Inventory inv, int slot, int delta) {
        boolean inc = delta > 0;

        String keyName = inc
                ? "claimblocks_exchange_amount_increase_name"
                : "claimblocks_exchange_amount_decrease_name";

        String keyLore = inc
                ? "claimblocks_exchange_amount_increase_lore"
                : "claimblocks_exchange_amount_decrease_lore";

        String name = apply(
                tr(p, keyName, missingKey(keyName)),
                Map.of("AMOUNT", String.valueOf(Math.abs(delta)))
        );

        List<String> lore = trListWith(
                p,
                keyLore,
                List.of(missingKey(keyLore)),
                Map.of("AMOUNT", String.valueOf(Math.abs(delta)))
        );

        Material mat;
        if (inc) mat = Material.LIME_DYE;
        else mat = Material.RED_DYE;

        inv.setItem(slot, item(mat, name, lore));
    }

    // ------------------------------------------------------------
    // RESULT HANDLING (chat localized, service message preserved)
    // ------------------------------------------------------------

    private void handleResult(Player p, ClaimBlockExchangeService.Result res) {
        switch (res.type()) {
            case OK -> {
                send(p, "claimblocks_exchange_success", "&a" + res.message());
                trySound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.25f);
            }
            case NO_PERMISSION -> {
                send(p, "claimblocks_exchange_no_permission", "&cYou do not have permission.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            case VAULT_UNAVAILABLE -> {
                send(p, "claimblocks_exchange_vault_unavailable", "&cVault economy is unavailable.");
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
                send(p, "claimblocks_exchange_daily_cap", "&eDaily cap reached. Remaining today: &6{REMAINING}&e.",
                        Map.of("REMAINING", String.valueOf(res.longA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            case INSUFFICIENT_FUNDS -> {
                send(p, "claimblocks_exchange_insufficient_funds", "&cNot enough money. Need: &e{AMOUNT}",
                        Map.of("AMOUNT", money(res.dblA())));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case INSUFFICIENT_BLOCKS -> {
                send(p, "claimblocks_exchange_insufficient_blocks", "&cNot enough sellable Claim Blocks.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.85f);
            }
            case SELL_LOCKED -> {
                send(p, "claimblocks_exchange_sell_locked", "&eSome purchased blocks are locked. Try later.");
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.9f);
            }
            default -> {
                send(p, "claimblocks_exchange_trade_failed", "&cTrade failed: &7{REASON}",
                        Map.of("REASON", res.message() == null ? "Unknown" : res.message()));
                trySound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------
    // Helpers (Codex + msg() style)
    // ------------------------------------------------------------

    private String missingKey(String key) {
        return "&c[Missing: " + key + "]";
    }

    private String tr(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> trList(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private List<String> trListWith(Player p, String key, List<String> fallback, Map<String, String> placeholders) {
        List<String> base = plugin.gui().trList(p, key, fallback);
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

        if (msg == null || msg.isBlank() || msg.equalsIgnoreCase(key) || msg.contains("[Missing")) {
            msg = fallback;
        }

        if (msg == null || msg.trim().isEmpty()) return;

        msg = apply(msg, placeholders);

        String out = GUIManager.color(prefix + msg);
        p.spigot().sendMessage(TextComponent.fromLegacyText(out));
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

    // ✅ IMPORTANT: public so GUIListener can instanceof it
    public static final class ExchangeHolder implements InventoryHolder {
        public final UUID owner;
        public ExchangeHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
