package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Confirmation step before Vault charges for full-plot rent, zone rent, or extend.
 */
public class RentConfirmGUI {

    public enum Action {
        PLOT_RENT,
        ZONE_RENT,
        ZONE_EXTEND,
        PLOT_RENEW,
        PLOT_CANCEL
    }

    private final AegisGuard plugin;

    public RentConfirmGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class RentConfirmHolder implements InventoryHolder {
        private final Action action;
        private final UUID plotId;
        private final String zoneName;
        private final double rent;
        private final double deposit;
        private final int days;
        private final String returnTo;

        public RentConfirmHolder(Action action, UUID plotId, String zoneName,
                                 double rent, double deposit, int days, String returnTo) {
            this.action = action;
            this.plotId = plotId;
            this.zoneName = zoneName;
            this.rent = rent;
            this.deposit = deposit;
            this.days = days;
            this.returnTo = returnTo == null ? "market" : returnTo;
        }

        public Action getAction() { return action; }
        public UUID getPlotId() { return plotId; }
        public String getZoneName() { return zoneName; }
        public double getRent() { return rent; }
        public double getDeposit() { return deposit; }
        public int getDays() { return days; }
        public String getReturnTo() { return returnTo; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void openPlotRent(Player player, Plot plot, double rent, double deposit, int days, String returnTo) {
        if (player == null || plot == null) return;
        open(player, new RentConfirmHolder(Action.PLOT_RENT, plot.getPlotId(), null, rent, deposit, days, returnTo),
                plotDisplayName(plot));
    }

    public void openPlotRenew(Player player, Plot plot, double rent, int days, String returnTo) {
        if (player == null || plot == null) return;
        open(player, new RentConfirmHolder(Action.PLOT_RENEW, plot.getPlotId(), null, rent, 0.0D, days, returnTo),
                plotDisplayName(plot));
    }

    public void openPlotCancel(Player player, Plot plot, double deposit, String returnTo) {
        if (player == null || plot == null) return;
        open(player, new RentConfirmHolder(Action.PLOT_CANCEL, plot.getPlotId(), null, 0.0D, deposit, 0, returnTo),
                plotDisplayName(plot));
    }

    public void openZoneRent(Player player, Plot plot, Zone zone, boolean extend, String returnTo) {
        if (player == null || plot == null || zone == null) return;
        int days = Math.max(1, plugin.getConfig().getInt("zoning.default_rental_days", 7));
        open(player, new RentConfirmHolder(
                        extend ? Action.ZONE_EXTEND : Action.ZONE_RENT,
                        plot.getPlotId(),
                        zone.getName(),
                        zone.getRentPrice(),
                        0.0D,
                        days,
                        returnTo),
                safeZoneName(zone));
    }

    private void open(Player player, RentConfirmHolder holder, String subjectName) {
        String titleKey = holder.getAction() == Action.PLOT_CANCEL
                ? "rent_confirm_cancel_title"
                : "rent_confirm_title";
        String titleFallback = holder.getAction() == Action.PLOT_CANCEL
                ? "&cConfirm Cancel"
                : "&6Confirm Rental";
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.gui().title(player, titleKey, titleFallback));

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        double total = holder.getRent() + holder.getDeposit();
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(tr(player, "rent_confirm_subject_line", "&7Listing: &f{NAME}")
                .replace("{NAME}", subjectName)));
        if (holder.getAction() != Action.PLOT_CANCEL) {
            lore.add(GUIManager.color(tr(player, "rent_confirm_price_line", "&7Rent: &6{PRICE}")
                    .replace("{PRICE}", plugin.eco().format(holder.getRent(), CurrencyType.VAULT))));
            if (holder.getDeposit() > 0.0D) {
                lore.add(GUIManager.color(tr(player, "rent_confirm_deposit_line", "&7Deposit: &6{DEPOSIT}")
                        .replace("{DEPOSIT}", plugin.eco().format(holder.getDeposit(), CurrencyType.VAULT))));
            }
            if (holder.getDays() > 0) {
                lore.add(GUIManager.color(tr(player, "rent_confirm_term_line", "&7Term: &b{DAYS} day(s)")
                        .replace("{DAYS}", String.valueOf(holder.getDays()))));
            }
            lore.add(GUIManager.color(tr(player, "rent_confirm_total_line", "&7Total due: &e{TOTAL}")
                    .replace("{TOTAL}", plugin.eco().format(total, CurrencyType.VAULT))));
            lore.add(" ");
            lore.add(GUIManager.color(tr(player, "rent_confirm_click_lore", "&aClick to confirm and pay")));
        } else {
            if (holder.getDeposit() > 0.0D) {
                lore.add(GUIManager.color(tr(player, "rent_confirm_deposit_refund_line",
                        "&7Deposit refund: &6{DEPOSIT}")
                        .replace("{DEPOSIT}", plugin.eco().format(holder.getDeposit(), CurrencyType.VAULT))));
            }
            lore.add(" ");
            lore.add(GUIManager.color(tr(player, "rent_confirm_cancel_click_lore",
                    "&cClick to end this rental contract")));
        }

        Material confirmMat = holder.getAction() == Action.PLOT_CANCEL
                ? Material.REDSTONE_BLOCK
                : Material.EMERALD_BLOCK;
        String confirmName = holder.getAction() == Action.PLOT_CANCEL
                ? tr(player, "rent_confirm_cancel_name", "&cConfirm Cancel")
                : tr(player, "rent_confirm_name", "&aConfirm Rental");
        inv.setItem(13, GUIManager.createItem(confirmMat, confirmName, lore));

        inv.setItem(11, GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return without paying."))
        ));
        inv.setItem(15, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, RentConfirmHolder holder) {
        if (player == null || holder == null) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getRawSlot();
        if (slot == 11) {
            goBack(player, holder);
            return;
        }
        if (slot == 15) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot != 13) return;

        Plot plot = findPlot(holder.getPlotId());
        if (plot == null) {
            plugin.effects().playError(player);
            send(player, "rent_confirm_missing", "&cThat listing is no longer available.");
            player.closeInventory();
            return;
        }

        switch (holder.getAction()) {
            case PLOT_RENT -> plugin.gui().market().executeRent(player, plot);
            case PLOT_RENEW -> plugin.gui().myRentals().executePlotRenew(player, plot);
            case PLOT_CANCEL -> plugin.gui().myRentals().executePlotCancel(player, plot);
            case ZONE_RENT, ZONE_EXTEND -> {
                Zone zone = findZone(plot, holder.getZoneName());
                if (zone == null) {
                    plugin.effects().playError(player);
                    send(player, "rent_confirm_missing", "&cThat listing is no longer available.");
                    return;
                }
                boolean extend = holder.getAction() == Action.ZONE_EXTEND;
                plugin.gui().zoneBrowse().executeRentOrExtend(player, plot, zone, extend);
            }
        }
    }

    private void goBack(Player player, RentConfirmHolder holder) {
        Plot plot = findPlot(holder.getPlotId());
        String dest = holder.getReturnTo() == null ? "market" : holder.getReturnTo().toLowerCase(Locale.ROOT);
        switch (dest) {
            case "my_rentals" -> plugin.gui().myRentals().open(player);
            case "zone_browse" -> {
                if (plot != null) plugin.gui().zoneBrowse().open(player, plot);
                else plugin.gui().myRentals().open(player);
            }
            case "local_market" -> {
                if (plot != null) plugin.gui().localMarket().open(player, plot);
                else plugin.gui().market().open(player, 0);
            }
            default -> plugin.gui().market().open(player, 0);
        }
        plugin.effects().playMenuFlip(player);
    }

    private Plot findPlot(UUID plotId) {
        if (plotId == null) return null;
        return plugin.store().getAllPlots().stream()
                .filter(p -> p != null && plotId.equals(p.getPlotId()))
                .findFirst().orElse(null);
    }

    private Zone findZone(Plot plot, String zoneName) {
        if (plot == null || zoneName == null || plot.getZones() == null) return null;
        for (Zone zone : plot.getZones()) {
            if (zone != null && zoneName.equals(zone.getName())) return zone;
        }
        return null;
    }

    private String plotDisplayName(Plot plot) {
        if (plot == null) return "Plot";
        String name = plot.getPlotName();
        if (name != null && !name.isBlank()) return name;
        String owner = plot.getOwnerName();
        return (owner == null || owner.isBlank()) ? "Plot" : owner + "'s Plot";
    }

    private String safeZoneName(Zone zone) {
        String zoneName = zone == null ? null : zone.getName();
        return (zoneName == null || zoneName.isBlank()) ? "Zone" : zoneName;
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(GUIManager.color(tr(player, key, fallback)));
    }
}
