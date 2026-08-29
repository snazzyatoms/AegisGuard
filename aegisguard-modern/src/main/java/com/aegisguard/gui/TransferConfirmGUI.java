package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/** Explicit confirmation before a no-cost ownership transfer. */
public class TransferConfirmGUI {
    private final AegisGuard plugin;
    public TransferConfirmGUI(AegisGuard plugin) { this.plugin = plugin; }
    public static final class TransferConfirmHolder implements InventoryHolder {
        private final UUID plotId, recipientId;
        public TransferConfirmHolder(UUID plotId, UUID recipientId) { this.plotId = plotId; this.recipientId = recipientId; }
        public UUID getPlotId() { return plotId; } public UUID getRecipientId() { return recipientId; }
        @Override public Inventory getInventory() { return null; }
    }
    public void open(Player owner, Plot plot, OfflinePlayer recipient) {
        if (owner == null || plot == null || recipient == null || !canTransfer(owner, plot, recipient.getUniqueId())) {
            if (owner != null) plugin.effects().playError(owner);
            return;
        }
        Inventory inv = Bukkit.createInventory(new TransferConfirmHolder(plot.getPlotId(), recipient.getUniqueId()), 27,
                plugin.gui().title(owner, "transfer_confirm_title", "&cConfirm Transfer"));
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());
        inv.setItem(13, GUIManager.createItem(Material.WRITABLE_BOOK, tr(owner, "transfer_confirm_details_name", "&eTransfer Ownership"),
                trList(owner, "transfer_confirm_details_lore", List.of(
                        "&7Plot: &f" + plotName(plot),
                        "&7Recipient: &f" + name(recipient),
                        " ",
                        "&cListings clear. Active deposits are",
                        "&crefunded or queued before transfer."))));
        inv.setItem(11, GUIManager.createItem(Material.EMERALD_BLOCK, tr(owner, "transfer_confirm_accept", "&aConfirm Transfer"),
                trList(owner, "transfer_confirm_accept_lore", List.of("&7Transfer this plot now."))));
        inv.setItem(15, GUIManager.createItem(Material.ARROW, tr(owner, "button_back", "&fBack"),
                trList(owner, "back_lore", List.of("&7Return without transferring."))));
        inv.setItem(22, GUIManager.createItem(Material.BARRIER, tr(owner, "button_exit", "&cClose"),
                trList(owner, "exit_lore", List.of("&7Close this menu."))));
        owner.openInventory(inv); plugin.effects().playMenuFlip(owner);
    }
    public void handleClick(Player player, InventoryClickEvent e, TransferConfirmHolder holder) {
        e.setCancelled(true); if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 15) {
            Plot plot = find(holder.getPlotId());
            if (plot != null) plugin.gui().plotStatus().open(player, plot);
            else plugin.gui().openMain(player);
            return;
        }
        if (e.getRawSlot() == 22) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (e.getRawSlot() != 11) return;
        Plot plot = find(holder.getPlotId());
        OfflinePlayer recipient = Bukkit.getOfflinePlayer(holder.getRecipientId());
        if (plot == null || !canTransfer(player, plot, recipient.getUniqueId())) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }
        if (plugin.succession() != null && plugin.succession().enabled()
                && !plugin.succession().canTransferNow(plot)) {
            long wait = Math.max(1L, plugin.succession().remainingTransferCooldown(plot.getPlotId()) / 1000L);
            player.sendMessage(GUIManager.color(tr(player, "transfer_cooldown",
                    "&cWait {SECONDS}s before transferring this plot again.")
                    .replace("{SECONDS}", String.valueOf(wait))));
            plugin.effects().playError(player);
            return;
        }
        UUID previous = plot.getOwner();
        String previousName = plot.getOwnerName();
        settleBeforeTransfer(plot, player);
        plot.setForSale(false, 0);
        plot.setForRent(false, 0);
        plot.setForAuction(false);
        plot.clearRenter();
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.store().changePlotOwner(plot, recipient.getUniqueId(), name(recipient));
        plugin.store().savePlotSync(plot);
        if (plugin.succession() != null) {
            plugin.succession().recordTransfer(plot, previous, previousName, recipient.getUniqueId(), player);
        }
        if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
        plugin.effects().playConfirm(player);
        player.sendMessage(GUIManager.color(tr(player, "transfer_confirm_success",
                "&aTransferred plot to &f{PLAYER}&a.").replace("{PLAYER}", name(recipient))));
        plugin.gui().openMain(player);
    }

    /** Refund full-plot and zone deposits, then clear renters before ownership changes. */
    private void settleBeforeTransfer(Plot plot, Player actor) {
        TerritoryLifeService life = plugin.territoryLife();
        TerritoryLifeService.RentalContract contract = life.contract(plot.getPlotId());
        if (contract != null) {
            life.refundDeposit(contract, "Plot transfer deposit refund");
            life.removeContract(plot.getPlotId());
            life.queueNotice(contract.renterId(),
                    tr(actor, "transfer_renter_cleared",
                            "&eYour rental on &f{PLOT} &eended because ownership transferred.")
                            .replace("{PLOT}", plotName(plot)));
        }
        if (plot.getZones() != null) {
            for (Zone zone : plot.getZones()) {
                if (zone == null || !zone.isRented()) continue;
                UUID renter = zone.getRenter();
                double held = zone.takeHeldDeposit();
                if (renter != null && held > 0.0D) {
                    if (plugin.vault() == null || !plugin.vault().deposit(Bukkit.getOfflinePlayer(renter), held)) {
                        life.addSettlement(renter, held, "Zone deposit refund on plot transfer");
                    }
                }
                zone.evict();
                if (renter != null) {
                    life.queueNotice(renter, tr(actor, "transfer_zone_renter_cleared",
                            "&eYour zone rental on &f{PLOT} &eended because ownership transferred.")
                            .replace("{PLOT}", plotName(plot)));
                }
            }
        }
        plot.clearRenter();
        life.clearOffer(plot.getPlotId());
        life.logKey(plot.getPlotId(), actor.getUniqueId(), "OWNERSHIP_TRANSFER_SETTLE",
                "activity_detail_transfer_settle",
                "Cleared rentals/deposits before ownership transfer.", java.util.Map.of());
    }

    private boolean canTransfer(Player owner, Plot plot, UUID recipient) {
        return recipient != null && !recipient.equals(owner.getUniqueId()) && plot.isOwner(owner.getUniqueId())
                && plot.canManage(owner, plugin) && plugin.store().getPlotAt(owner.getLocation()) == plot;
    }
    private Plot find(UUID id) {
        return plugin.store().getAllPlots().stream()
                .filter(p -> p != null && id.equals(p.getPlotId())).findFirst().orElse(null);
    }
    private String plotName(Plot p) {
        return p.getPlotName() == null || p.getPlotName().isBlank() ? "Plot" : p.getPlotName();
    }
    private String name(OfflinePlayer p) {
        return p.getName() == null ? p.getUniqueId().toString() : p.getName();
    }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
