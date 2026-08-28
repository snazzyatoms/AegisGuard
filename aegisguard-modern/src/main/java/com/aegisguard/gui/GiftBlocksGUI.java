package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mini GUI: pick a nearby player, choose amount, confirm ClaimBlocks gift. */
public class GiftBlocksGUI {
    private static final long[] AMOUNTS = {10L, 50L, 100L, 250L, 500L, 1000L};

    private final AegisGuard plugin;
    public GiftBlocksGUI(AegisGuard plugin) { this.plugin = plugin; }

    public static final class GiftBlocksHolder implements InventoryHolder {
        private final List<UUID> recipients;
        private final UUID selected;
        private final long amount;
        private final boolean confirm;
        private final String returnTo;
        private final UUID originPlotId;
        public GiftBlocksHolder(List<UUID> recipients, UUID selected, long amount, boolean confirm) {
            this(recipients, selected, amount, confirm, MarketNav.MAIN, null);
        }
        public GiftBlocksHolder(List<UUID> recipients, UUID selected, long amount, boolean confirm,
                                String returnTo, UUID originPlotId) {
            this.recipients = recipients;
            this.selected = selected;
            this.amount = amount;
            this.confirm = confirm;
            this.returnTo = MarketNav.normalize(returnTo);
            this.originPlotId = originPlotId;
        }
        public List<UUID> getRecipients() { return recipients; }
        public UUID getSelected() { return selected; }
        public long getAmount() { return amount; }
        public boolean isConfirm() { return confirm; }
        public String getReturnTo() { return returnTo; }
        public UUID getOriginPlotId() { return originPlotId; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        openFrom(player, MarketNav.MAIN, null);
    }

    public void openFrom(Player player, String returnTo, com.aegisguard.data.Plot originPlot) {
        if (!plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true)) {
            player.sendMessage(GUIManager.color(tr(player, "giftblocks_disabled",
                    "&cClaimBlocks gifting is disabled.")));
            plugin.effects().playError(player);
            return;
        }
        String permission = plugin.getConfig().getString("claim_blocks.gift.permission", "aegis.claimblocks.gift");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(GUIManager.color(tr(player, "giftblocks_no_perm",
                    "&cYou do not have permission to gift ClaimBlocks.")));
            plugin.effects().playError(player);
            return;
        }
        openPickPlayer(player, returnTo, originPlot == null ? null : originPlot.getPlotId());
    }

    private void openPickPlayer(Player player) {
        openPickPlayer(player, MarketNav.MAIN, null);
    }

    private void openPickPlayer(Player player, String returnTo, UUID originPlotId) {
        List<UUID> nearby = nearbyPlayers(player);
        Inventory inv = Bukkit.createInventory(new GiftBlocksHolder(nearby, null, 0L, false, returnTo, originPlotId), 54,
                plugin.gui().title(player, "giftblocks_title", "&aGift ClaimBlocks"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        if (nearby.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.GRAY_DYE,
                    tr(player, "giftblocks_no_players_name", "&7No nearby players"),
                    trList(player, "giftblocks_no_players_lore",
                            List.of("&7Stand near another player,", "&7or use &e/ag giftblocks <player> <amount>&7."))));
        }
        // Players occupy 0-44; keep footer-only chrome so balance never steals a recipient slot.
        for (int i = 0; i < nearby.size() && i < 45; i++) {
            Player target = Bukkit.getPlayer(nearby.get(i));
            if (target == null) continue;
            inv.setItem(i, GUIManager.createItem(Material.PLAYER_HEAD, "&e" + target.getName(),
                    trList(player, "giftblocks_player_lore", List.of("&7Click to choose an amount."))));
        }
        long available = plugin.getClaimBlockManager() == null ? 0L
                : plugin.getClaimBlockManager().getAvailableBlocks(player.getUniqueId());
        inv.setItem(49, GUIManager.createItem(Material.EMERALD,
                tr(player, "giftblocks_balance_name", "&aYour available blocks"),
                List.of(GUIManager.color("&f" + available))));
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void openAmount(Player player, UUID recipient, String returnTo, UUID originPlotId) {
        List<UUID> nearby = List.of(recipient);
        Inventory inv = Bukkit.createInventory(new GiftBlocksHolder(nearby, recipient, 0L, false, returnTo, originPlotId), 27,
                plugin.gui().title(player, "giftblocks_amount_title", "&aChoose Amount"));
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());
        Player target = Bukkit.getPlayer(recipient);
        String name = target == null ? recipient.toString() : target.getName();
        inv.setItem(4, GUIManager.createItem(Material.PLAYER_HEAD, "&e" + name,
                trList(player, "giftblocks_amount_header_lore", List.of("&7Select how many blocks to gift."))));
        long max = Math.max(1L, plugin.getConfig().getLong("claim_blocks.gift.max_amount", 1000L));
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < AMOUNTS.length && i < slots.length; i++) {
            long amount = AMOUNTS[i];
            if (amount > max) continue;
            inv.setItem(slots[i], GUIManager.createItem(Material.GOLD_NUGGET, "&e" + amount,
                    trList(player, "giftblocks_amount_lore", List.of("&7Click to continue."))));
        }
        inv.setItem(18, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Pick another player."))));
        inv.setItem(26, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    private void openConfirm(Player player, UUID recipient, long amount, String returnTo, UUID originPlotId) {
        Inventory inv = Bukkit.createInventory(new GiftBlocksHolder(List.of(recipient), recipient, amount, true, returnTo, originPlotId), 27,
                plugin.gui().title(player, "giftblocks_confirm_title", "&aConfirm Gift"));
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());
        Player target = Bukkit.getPlayer(recipient);
        String name = target == null ? recipient.toString() : target.getName();
        inv.setItem(13, GUIManager.createItem(Material.EMERALD_BLOCK,
                plugin.gui().tr(player, "giftblocks_confirm_name", "&aGift &e{AMOUNT} &ato &f{PLAYER}",
                        Map.of("AMOUNT", String.valueOf(amount), "PLAYER", name)),
                trList(player, "giftblocks_confirm_lore", List.of("&7Click to send the gift."))));
        inv.setItem(11, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Choose a different amount."))));
        inv.setItem(15, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, GiftBlocksHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (holder.isConfirm()) {
            if (e.getRawSlot() == 11) { openAmount(player, holder.getSelected(), holder.getReturnTo(), holder.getOriginPlotId()); return; }
            if (e.getRawSlot() == 15) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
            if (e.getRawSlot() != 13) return;
            executeGift(player, holder.getSelected(), holder.getAmount());
            return;
        }

        if (holder.getSelected() != null && holder.getAmount() == 0L && e.getView().getTopInventory().getSize() == 27) {
            if (e.getRawSlot() == 18) { openPickPlayer(player, holder.getReturnTo(), holder.getOriginPlotId()); return; }
            if (e.getRawSlot() == 26) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
            long max = Math.max(1L, plugin.getConfig().getLong("claim_blocks.gift.max_amount", 1000L));
            int[] slots = {10, 11, 12, 13, 14, 15};
            for (int i = 0; i < AMOUNTS.length && i < slots.length; i++) {
                if (e.getRawSlot() == slots[i] && AMOUNTS[i] <= max) {
                    openConfirm(player, holder.getSelected(), AMOUNTS[i], holder.getReturnTo(), holder.getOriginPlotId());
                    return;
                }
            }
            return;
        }

        if (e.getRawSlot() == 48) {
            MarketNav.back(plugin, player, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }
        if (e.getRawSlot() == 49 || e.getRawSlot() == 50) {
            if (e.getRawSlot() == 50) {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            return;
        }
        if (e.getRawSlot() < 0 || e.getRawSlot() >= holder.getRecipients().size()) return;
        UUID recipient = holder.getRecipients().get(e.getRawSlot());
        if (Bukkit.getPlayer(recipient) == null) {
            plugin.effects().playError(player);
            openPickPlayer(player, holder.getReturnTo(), holder.getOriginPlotId());
            return;
        }
        openAmount(player, recipient, holder.getReturnTo(), holder.getOriginPlotId());
    }

    private void executeGift(Player sender, UUID recipient, long amount) {
        ClaimBlockManager manager = plugin.getClaimBlockManager();
        if (manager == null) {
            sender.sendMessage(GUIManager.color(tr(sender, "giftblocks_unavailable",
                    "&cClaim blocks are unavailable.")));
            plugin.effects().playError(sender);
            return;
        }
        ClaimBlockManager.GiftResult result = manager.gift(sender, recipient, amount);
        if (!result.success()) {
            sender.sendMessage(GUIManager.color(tr(sender, "giftblocks_failed_" + result.reason(),
                    "&cUnable to gift blocks: &7" + result.reason().replace('_', ' ') + "&c.")));
            plugin.effects().playError(sender);
            openPickPlayer(sender);
            return;
        }
        Player online = Bukkit.getPlayer(recipient);
        String targetName = online == null ? recipient.toString() : online.getName();
        sender.sendMessage(GUIManager.color(plugin.gui().tr(sender, "giftblocks_success_sender",
                "&aGifted &e{AMOUNT} &aclaim blocks to &f{PLAYER}&a.",
                Map.of("AMOUNT", String.valueOf(amount), "PLAYER", targetName))));
        if (online != null) {
            online.sendMessage(GUIManager.color(plugin.gui().tr(online, "giftblocks_success_receiver",
                    "&aYou received &e{AMOUNT} &aclaim blocks from &f{PLAYER}&a.",
                    Map.of("AMOUNT", String.valueOf(amount), "PLAYER", sender.getName()))));
        }
        plugin.effects().playConfirm(sender);
        plugin.gui().openMain(sender);
    }

    private List<UUID> nearbyPlayers(Player player) {
        List<UUID> ids = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (other == null || other.getUniqueId().equals(player.getUniqueId())) continue;
            if (other.getLocation().distanceSquared(player.getLocation()) > 64 * 64) continue;
            ids.add(other.getUniqueId());
        }
        return ids;
    }

    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
