package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.*;

final class EconomyCommands extends CommandBase {

    private final com.aegisguard.claimblocks.ClaimBlockExchangeService exchange;

    EconomyCommands(AegisGuard plugin) {
        super(plugin);
        this.exchange = plugin.exchange();
    }

    void showLedger(Player p) {
        ClaimBlockManager mgr = plugin.getClaimBlockManager();
        if (mgr == null) {
            sendKey(p, "claim_blocks_disabled", "&cClaim Blocks are disabled on this server.");
            return;
        }

        long total = mgr.getTotalBlocks(p.getUniqueId());
        long used = mgr.getUsedBlocks(p.getUniqueId());
        long avail = mgr.getAvailableBlocks(p.getUniqueId());

        sendMsg(p, "&8&m------------------------");
        sendKey(p, "blocks_ledger_title", "&6&lLand Ledger &7({PLAYER})",
                Map.of("PLAYER", p.getName()));
        sendKey(p, "blocks_ledger_total", "&7Total Capacity: &e{TOTAL}",
                Map.of("TOTAL", String.valueOf(total)));
        sendKey(p, "blocks_ledger_used", "&7Used Land: &c{USED}",
                Map.of("USED", String.valueOf(used)));
        sendKey(p, "blocks_ledger_available", "&7Available: &a{AVAILABLE}",
                Map.of("AVAILABLE", String.valueOf(avail)));
        sendMsg(p, "&8&m------------------------");
    }

    void handleBlocks(Player p, String[] args) {
        // /ag blocks
        if (args.length == 1) {
            showLedger(p);
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "menu", "gui" -> {
                plugin.gui().groupPlots().open(p);
                return;
            }
            case "help" -> {
                sendMsg(p, "&8&m------------------------");
                sendMsg(p, "&6&lClaimBlocks Exchange");
                sendMsg(p, "&e/ag blocks &7- show your ledger");
                sendMsg(p, "&e/ag blocks rates &7- view buy/sell rates");
                sendMsg(p, "&e/ag blocks buy <amount> &7- buy claimblocks");
                sendMsg(p, "&e/ag blocks sell <amount> &7- sell claimblocks");
                sendMsg(p, "&e/ag blocks earnings [on|off|status] &7- manage passive block earnings");
                sendMsg(p, "&8&m------------------------");
            }

            case "rates", "rate", "prices", "price" -> {
                List<String> lines = exchange.getRatesLines(p);
                for (String line : lines) sendMsg(p, line);
            }

            case "buy" -> {
                if (args.length < 3) {
                    sendKey(p, "blocks_buy_usage", "&cUsage: /ag blocks buy <amount>");
                    return;
                }
                long amount = parsePositiveLong(args[2]);
                if (amount <= 0) {
                    sendKey(p, "blocks_amount_positive", "&cAmount must be a positive number.");
                    return;
                }

                ClaimBlockExchangeService.ExchangeResult res = exchange.buy(p, amount);
                if (!res.success()) {
                    sendMsg(p, res.message());
                    plugin.effects().playError(p);
                    return;
                }

                sendMsg(p, res.message());
                plugin.effects().playConfirm(p);
            }

            case "sell" -> {
                if (args.length < 3) {
                    sendKey(p, "blocks_sell_usage", "&cUsage: /ag blocks sell <amount>");
                    return;
                }
                long amount = parsePositiveLong(args[2]);
                if (amount <= 0) {
                    sendKey(p, "blocks_amount_positive", "&cAmount must be a positive number.");
                    return;
                }

                ClaimBlockExchangeService.ExchangeResult res = exchange.sell(p, amount);
                if (!res.success()) {
                    sendMsg(p, res.message());
                    plugin.effects().playError(p);
                    return;
                }

                sendMsg(p, res.message());
                plugin.effects().playConfirm(p);
            }

            case "earnings", "earning", "passive" -> handleBlockEarnings(p, args);

            default -> {
                // If someone typed something else, show ledger + hint
                showLedger(p);
                sendMsg(p, "&7Tip: &e/ag blocks help &7for exchange commands.");
            }
        }
    }

    private void handleBlockEarnings(Player p, String[] args) {
        boolean serverEnabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        boolean optOutAllowed = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.player_opt_out_allowed", true);

        if (!serverEnabled) {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_server_disabled",
                            "&cPassive Claim Block earnings are disabled by the server.")));
            plugin.effects().playError(p);
            return;
        }

        if (args.length < 3 || args[2].equalsIgnoreCase("status")) {
            boolean enabled = plugin.getClaimBlockManager().isPlaytimeEarningEnabled(p.getUniqueId());
            sendMsg(p, "&8&m------------------------");
            sendMsg(p, "&6&lPassive Claim Block Earnings");
            sendMsg(p, "&7Server Playtime Rewards: " + (serverEnabled ? "&aEnabled" : "&cDisabled"));
            sendMsg(p, "&7Player Toggle Allowed: " + (optOutAllowed ? "&aYes" : "&cNo"));
            sendMsg(p, "&7Your Status: " + (enabled ? "&aEnabled" : "&cDisabled"));
            sendMsg(p, "&8&m------------------------");
            return;
        }

        if (!optOutAllowed) {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_toggle_locked",
                            "&cThis server does not allow players to change passive earnings.")));
            plugin.effects().playError(p);
            return;
        }

        Boolean desired = parseBooleanArg(args[2]);
        if (desired == null) {
            sendMsg(p, "&cUsage: /ag blocks earnings <on|off|status>");
            plugin.effects().playError(p);
            return;
        }

        plugin.getClaimBlockManager().setPlaytimeEarningEnabled(p.getUniqueId(), desired);

        if (desired) {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_enabled",
                            "&aPassive Claim Block earnings enabled.")));
            plugin.effects().playConfirm(p);
        } else {
            sendMsg(p, ChatColor.translateAlternateColorCodes('&',
                    tr(p, "claim_blocks_earnings_disabled",
                            "&ePassive Claim Block earnings disabled.")));
            plugin.effects().playError(p);
        }
    }

    void handleGiftBlocks(Player sender, String[] args) {
        if (args.length < 3) {
            if (args.length == 1) {
                plugin.gui().giftBlocks().open(sender);
                return;
            }
            sendKey(sender, "giftblocks_usage", "&eUsage: /ag giftblocks <player> <amount>");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sendMsg(sender, "&cAmount must be a whole number.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sendMsg(sender, "&cThat player has not played on this server.");
            return;
        }
        ClaimBlockManager manager = plugin.getClaimBlockManager();
        if (manager == null) {
            sendMsg(sender, "&cClaim blocks are unavailable.");
            return;
        }
        ClaimBlockManager.GiftResult result = manager.gift(sender, target.getUniqueId(), amount);
        if (!result.success()) {
            sendKey(sender, "giftblocks_failed_" + result.reason(),
                    "&cUnable to gift blocks: &7" + result.reason().replace('_', ' ') + "&c.");
            return;
        }
        String targetName = target.getName() == null ? args[1] : target.getName();
        sendKey(sender, "giftblocks_success_sender",
                "&aGifted &e{AMOUNT} &aclaim blocks to &f{PLAYER}&a.",
                Map.of("AMOUNT", String.valueOf(amount), "PLAYER", targetName));
        Player online = target.getPlayer();
        if (online != null) {
            sendKey(online, "giftblocks_success_receiver",
                    "&aYou received &e{AMOUNT} &aclaim blocks from &f{PLAYER}&a.",
                    Map.of("AMOUNT", String.valueOf(amount), "PLAYER", sender.getName()));
        }
    }

}