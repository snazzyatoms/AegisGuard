package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Unified hub for a player's active full-plot contracts and zone rentals.
 */
public class MyRentalsGUI {

    private static final int ITEMS_PER_PAGE = 45;

    private final AegisGuard plugin;

    public MyRentalsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class MyRentalsHolder implements InventoryHolder {
        private final int page;
        private final List<RentalEntry> entries;
        private final String returnTo;
        private final UUID originPlotId;

        public MyRentalsHolder(List<RentalEntry> entries, int page) {
            this(entries, page, MarketNav.MAIN, null);
        }

        public MyRentalsHolder(List<RentalEntry> entries, int page, String returnTo, UUID originPlotId) {
            this.entries = entries == null ? List.of() : entries;
            this.page = page;
            this.returnTo = MarketNav.normalize(returnTo);
            this.originPlotId = originPlotId;
        }

        public int getPage() { return page; }
        public List<RentalEntry> getEntries() { return entries; }
        public String getReturnTo() { return returnTo; }
        public UUID getOriginPlotId() { return originPlotId; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public record RentalEntry(Kind kind, UUID plotId, String zoneName) {
        public enum Kind { FULL_PLOT, ZONE }
    }

    public void open(Player player) {
        openFrom(player, 0, MarketNav.MAIN, null);
    }

    public void open(Player player, int page) {
        openFrom(player, page, MarketNav.MAIN, null);
    }

    public void openFrom(Player player, int page, String returnTo, Plot originPlot) {
        if (player == null) return;
        List<RentalEntry> entries = collectEntries(player.getUniqueId());
        UUID originId = originPlot == null ? null : originPlot.getPlotId();

        Inventory inv = Bukkit.createInventory(
                new MyRentalsHolder(entries, page, returnTo, originId),
                54,
                plugin.gui().title(player, "my_rentals_title", "&6My Rentals")
        );

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int maxPage = Math.max(0, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * ITEMS_PER_PAGE;
        int end = Math.min(entries.size(), start + ITEMS_PER_PAGE);

        // Keep visual slot == entry index so clicks never resolve to a different rental
        // when a stale/missing plot or zone is skipped.
        for (int idx = start; idx < end; idx++) {
            RentalEntry entry = entries.get(idx);
            int slot = idx - start;
            Plot plot = findPlot(entry.plotId());
            if (plot == null) continue;

            if (entry.kind() == RentalEntry.Kind.FULL_PLOT) {
                inv.setItem(slot, buildFullPlotItem(player, plot));
            } else {
                Zone zone = findZone(plot, entry.zoneName());
                if (zone == null) continue;
                inv.setItem(slot, buildZoneItem(player, plot, zone));
            }
        }

        if (entries.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.GRAY_DYE,
                    tr(player, "my_rentals_empty_name", "&7No Active Rentals"),
                    trList(player, "my_rentals_empty_lore", List.of(
                            "&7You are not renting any full plots",
                            "&7or zones right now.",
                            " ",
                            "&8Browse Real Estate or Local Market",
                            "&8to find rentals."
                    ))
            ));
        }

        inv.setItem(45, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                tr(player, "my_rentals_guide_name", "&eRentals Guide"),
                trList(player, "my_rentals_guide_lore", List.of(
                        "&7Right-click: renew / extend",
                        "&7Shift-right-click: cancel / leave",
                        "&7Zones: left-click opens room controls",
                        "&7Shift-left: toggle auto-renew (full-plot)"
                ))
        ));

        if (safePage > 0) {
            inv.setItem(46, GUIManager.createItem(Material.ARROW,
                    tr(player, "button_prev", "&fPrevious Page"),
                    trList(player, "button_prev_lore", List.of("&7Go to the previous page."))));
        }
        if (safePage < maxPage) {
            inv.setItem(52, GUIManager.createItem(Material.ARROW,
                    tr(player, "button_next", "&fNext Page"),
                    trList(player, "button_next_lore", List.of("&7Go to the next page."))));
        }
        inv.setItem(53, GUIManager.createItem(Material.PAPER,
                tr(player, "button_page", "&7Page: &f{PAGE}")
                        .replace("{PAGE}", (safePage + 1) + "/" + (maxPage + 1)),
                List.of(GUIManager.color("&7 "))));

        inv.setItem(48, GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, MyRentalsHolder holder) {
        if (player == null || holder == null) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;

        int slot = e.getRawSlot();
        int page = holder.getPage();
        List<RentalEntry> entries = holder.getEntries();
        int maxPage = Math.max(0, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE) - 1);

        if (slot == 48) {
            MarketNav.back(plugin, player, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }
        if (slot == 50) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot == 46 && page > 0) {
            openFrom(player, page - 1, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }
        if (slot == 52 && page < maxPage) {
            openFrom(player, page + 1, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }
        if (slot >= ITEMS_PER_PAGE) return;

        int index = (page * ITEMS_PER_PAGE) + slot;
        if (index < 0 || index >= entries.size()) return;

        RentalEntry entry = entries.get(index);
        Plot plot = findPlot(entry.plotId());
        if (plot == null) {
            plugin.effects().playError(player);
            openFrom(player, page, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }

        if (entry.kind() == RentalEntry.Kind.FULL_PLOT) {
            TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
            if (contract == null || !contract.renterId().equals(player.getUniqueId())) {
                plugin.effects().playError(player);
                openFrom(player, page, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
                return;
            }
            if (e.getClick().isShiftClick() && e.getClick().isLeftClick()) {
                toggleAutoRenew(player, plot, contract);
                return;
            }
            if (e.getClick().isShiftClick() && e.getClick().isRightClick()) {
                plugin.gui().rentConfirm().openPlotCancel(player, plot, contract.deposit(), "my_rentals");
                return;
            }
            if (e.getClick().isRightClick()) {
                plugin.gui().rentConfirm().openPlotRenew(player, plot, contract.rent(), contract.termDays(), "my_rentals");
                return;
            }
            plugin.effects().playMenuFlip(player);
            return;
        }

        Zone zone = findZone(plot, entry.zoneName());
        if (zone == null || !zone.isRentedBy(player.getUniqueId())) {
            plugin.effects().playError(player);
            openFrom(player, page, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }
        if (e.getClick().isShiftClick() && e.getClick().isRightClick()) {
            plugin.gui().rentConfirm().openZoneLeave(player, plot, zone, "my_rentals");
            return;
        }
        if (e.getClick().isRightClick()) {
            plugin.gui().rentConfirm().openZoneRent(player, plot, zone, true, "my_rentals");
            return;
        }
        if (e.getClick().isLeftClick()) {
            plugin.gui().zoneTenant().open(player, plot, zone, MarketNav.nest(MarketNav.MY_RENTALS, holder.getReturnTo()));
            plugin.effects().playMenuFlip(player);
        }
    }

    private void toggleAutoRenew(Player player, Plot plot, TerritoryLifeService.RentalContract contract) {
        if (!plugin.getConfig().getBoolean("full_plot_renting.auto_renew.enabled", true)) {
            plugin.effects().playError(player);
            send(player, "rental_auto_renew_disabled", "&cRental auto-renew is disabled on this server.");
            return;
        }
        boolean next = !contract.autoRenew();
        contract.setAutoRenew(next);
        plugin.territoryLife().touch();
        plugin.territoryLife().save();
        plugin.effects().playConfirm(player);
        send(player, next ? "rental_auto_renew_on" : "rental_auto_renew_off",
                next
                        ? "&aAuto-renew enabled. Vault balance will be checked at expiry."
                        : "&eAuto-renew disabled for this contract.");
        open(player);
    }

    public void executePlotRenew(Player player, Plot plot) {
        if (player == null || plot == null) return;
        TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
        if (contract == null || !contract.renterId().equals(player.getUniqueId())) {
            plugin.effects().playError(player);
            send(player, "rental_contract_only_renter", "&cOnly the renter can renew this contract.");
            open(player);
            return;
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(contract.ownerId());
        if (!plugin.eco().withdraw(player, contract.rent(), CurrencyType.VAULT)) {
            plugin.effects().playError(player);
            send(player, "rental_contract_need_funds",
                    "&cYou need {AMOUNT} to renew."
                            .replace("{AMOUNT}", plugin.eco().format(contract.rent(), CurrencyType.VAULT)));
            open(player);
            return;
        }
        if (plugin.vault() == null || !plugin.vault().deposit(owner, contract.rent())) {
            if (plugin.vault() == null || !plugin.vault().deposit(player, contract.rent())) {
                plugin.territoryLife().addSettlement(player.getUniqueId(), contract.rent(), "Failed rental renewal refund");
            }
            plugin.effects().playError(player);
            send(player, "rental_contract_payment_failed", "&cRenewal payment failed. No contract time was added.");
            open(player);
            return;
        }
        plugin.territoryLife().renew(plot.getPlotId());
        plot.setRentEndTime(contract.expiresAt());
        plugin.store().savePlotSync(plot);
        plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "RENTAL_RENEWED",
                "activity_detail_rental_renewed",
                "Contract renewed for " + contract.termDays() + " day(s).",
                java.util.Map.of("DAYS", Integer.toString(contract.termDays())));
        plugin.territoryLife().queueNoticeKey(contract.ownerId(), "rental_contract_renewed_owner",
                "&aA rental contract was renewed for &e{DAYS} day(s)&a.",
                java.util.Map.of("DAYS", Integer.toString(contract.termDays())));
        plugin.effects().playConfirm(player);
        send(player, "rental_contract_renewed",
                "&aRental renewed for &e{DAYS} day(s)&a."
                        .replace("{DAYS}", Integer.toString(contract.termDays())));
        open(player);
    }

    public void executePlotCancel(Player player, Plot plot) {
        if (player == null || plot == null) return;
        TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
        if (contract == null) {
            plugin.effects().playError(player);
            open(player);
            return;
        }
        boolean renter = contract.renterId().equals(player.getUniqueId());
        boolean owner = contract.ownerId().equals(player.getUniqueId());
        if (!renter && !owner) {
            plugin.effects().playError(player);
            send(player, "rental_contract_not_party", "&cYou are not part of this rental contract.");
            open(player);
            return;
        }
        if (owner && !plugin.getConfig().getBoolean("full_plot_renting.allow_owner_early_cancel", false)) {
            plugin.effects().playError(player);
            send(player, "rental_contract_owner_cancel_disabled",
                    "&cOwners cannot end active contracts early on this server.");
            open(player);
            return;
        }
        plugin.territoryLife().removeContract(plot.getPlotId());
        plugin.territoryLife().refundDeposit(contract, "Deposit after early rental cancellation");
        plot.clearRenter();
        plugin.store().savePlotSync(plot);
        String actorRole = owner ? "owner" : "renter";
        plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "RENTAL_CANCELLED",
                "activity_detail_rental_cancelled",
                "Contract ended early by " + actorRole + ".",
                java.util.Map.of("ACTOR_ROLE", actorRole));
        plugin.territoryLife().queueNoticeKey(owner ? contract.renterId() : contract.ownerId(),
                "rental_contract_ended_early_notice",
                "&eThe rental contract for plot &f{PLOT} &ewas ended early.",
                java.util.Map.of("PLOT", String.valueOf(plot.getPlotId())));
        if (plugin.getDiscord() != null) {
            plugin.getDiscord().sendEventKey("rental_end",
                    "discord_event_rental_end_title", "Plot rental ended",
                    "discord_event_rental_end_description",
                    "{PLAYER} ended the rental for {PLOT}",
                    java.util.Map.of("PLAYER", player.getName(), "PLOT", plotDisplayName(plot)),
                    0xE67E22);
        }
        plugin.effects().playConfirm(player);
        send(player, "rental_contract_cancelled",
                "&aRental contract ended. The deposit was refunded or queued for delivery.");
        open(player);
    }

    private List<RentalEntry> collectEntries(UUID playerId) {
        List<RentalEntry> out = new ArrayList<>();
        if (playerId == null) return out;

        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;
            if (plot.isRentedBy(playerId)) {
                out.add(new RentalEntry(RentalEntry.Kind.FULL_PLOT, plot.getPlotId(), null));
            }
            if (plot.getZones() == null) continue;
            for (Zone zone : plot.getZones()) {
                if (zone != null && zone.isRentedBy(playerId)) {
                    out.add(new RentalEntry(RentalEntry.Kind.ZONE, plot.getPlotId(), zone.getName()));
                }
            }
        }

        out.sort(Comparator
                .comparing((RentalEntry e) -> e.kind() != RentalEntry.Kind.FULL_PLOT)
                .thenComparing(e -> {
                    Plot p = findPlot(e.plotId());
                    return p == null ? "" : plotDisplayName(p).toLowerCase(Locale.ROOT);
                })
                .thenComparing(e -> e.zoneName() == null ? "" : e.zoneName().toLowerCase(Locale.ROOT)));
        return out;
    }

    private ItemStack buildFullPlotItem(Player player, Plot plot) {
        TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(tr(player, "my_rentals_type_full", "&7Type: &fFull Plot")));
        lore.add(GUIManager.color(tr(player, "my_rentals_landlord_line", "&7Landlord: &f{PLAYER}")
                .replace("{PLAYER}", plot.getOwnerName() == null ? "Unknown" : plot.getOwnerName())));
        if (contract != null) {
            lore.add(GUIManager.color(tr(player, "my_rentals_price_line", "&7Rent: &6{PRICE}")
                    .replace("{PRICE}", plugin.eco().format(contract.rent(), CurrencyType.VAULT))));
            if (contract.deposit() > 0.0D) {
                lore.add(GUIManager.color(tr(player, "my_rentals_deposit_line", "&7Deposit: &6{DEPOSIT}")
                        .replace("{DEPOSIT}", plugin.eco().format(contract.deposit(), CurrencyType.VAULT))));
            }
            long remaining = Math.max(0L, contract.expiresAt() - System.currentTimeMillis());
            lore.add(GUIManager.color(tr(player, "my_rentals_remaining_line",
                    "&7Remaining: &b{DAYS}d {HOURS}h")
                    .replace("{DAYS}", Long.toString(remaining / 86_400_000L))
                    .replace("{HOURS}", Long.toString((remaining / 3_600_000L) % 24L))));
            lore.add(GUIManager.color(tr(player, "my_rentals_auto_renew_line", "&7Auto-renew: &f{STATE}")
                    .replace("{STATE}", contract.autoRenew()
                            ? tr(player, "my_rentals_auto_renew_on", "&aON")
                            : tr(player, "my_rentals_auto_renew_off", "&cOFF"))));
        }
        lore.add(" ");
        lore.add(GUIManager.color(tr(player, "my_rentals_full_actions",
                "&eRight renew &8| &cShift-right cancel &8| &bShift-left auto-renew")));
        return GUIManager.createItem(Material.GOLDEN_HOE,
                tr(player, "my_rentals_full_name", "&6{PLOT}").replace("{PLOT}", plotDisplayName(plot)),
                lore);
    }

    private ItemStack buildZoneItem(Player player, Plot plot, Zone zone) {
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(tr(player, "my_rentals_type_zone", "&7Type: &fZone")));
        lore.add(GUIManager.color(tr(player, "my_rentals_plot_line", "&7Plot: &f{PLOT}")
                .replace("{PLOT}", plotDisplayName(plot))));
        lore.add(GUIManager.color(tr(player, "my_rentals_price_line", "&7Rent: &6{PRICE}")
                .replace("{PRICE}", plugin.eco().format(zone.getRentPrice(), CurrencyType.VAULT))));
        lore.add(GUIManager.color(tr(player, "my_rentals_zone_remaining_line", "&7Remaining: &b{TIME}")
                .replace("{TIME}", zone.getRemainingTimeFormatted())));
        lore.add(" ");
        lore.add(GUIManager.color(tr(player, "my_rentals_zone_actions",
                "&aRight-click extend &8| &eLeft room &8| &cShift-right leave")));
        return GUIManager.createItem(Material.OAK_DOOR,
                tr(player, "my_rentals_zone_name", "&a{ZONE}").replace("{ZONE}", safeZoneName(zone)),
                lore);
    }

    public void executeZoneLeave(Player player, Plot plot, Zone zone) {
        if (player == null || plot == null || zone == null) return;
        if (!zone.isRentedBy(player.getUniqueId())) {
            plugin.effects().playError(player);
            send(player, "zone_leave_not_renter", "&cYou are not renting this zone.");
            open(player);
            return;
        }
        UUID landlord = plot.getOwner();
        double held = zone.takeHeldDeposit();
        zone.evict();
        if (held > 0.0D) {
            if (plugin.vault() == null
                    || !plugin.vault().deposit(org.bukkit.Bukkit.getOfflinePlayer(player.getUniqueId()), held)) {
                plugin.territoryLife().addSettlement(player.getUniqueId(), held, "Zone deposit refund on leave");
            } else {
                send(player, "zone_deposit_refunded",
                        "&aZone deposit refunded: &6{DEPOSIT}"
                                .replace("{DEPOSIT}", plugin.eco().format(held, CurrencyType.VAULT)));
            }
        }
        plugin.territoryLife().clearZoneDeposit(plot.getPlotId(), zone.getName());
        plugin.store().savePlotSync(plot);
        plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "ZONE_RENT_LEFT",
                "activity_detail_zone_rent_left",
                "Zone " + safeZoneName(zone) + " left early by renter.",
                java.util.Map.of("ZONE", safeZoneName(zone)));
        if (landlord != null) {
            plugin.territoryLife().queueNoticeKey(landlord, "zone_rent_left_landlord_notice",
                    "&eTenant &f{PLAYER} &eleft zone &f{ZONE}&e early.",
                    java.util.Map.of("PLAYER", player.getName(), "ZONE", safeZoneName(zone)));
        }
        if (plugin.getDiscord() != null) {
            plugin.getDiscord().sendEventKey("rental_end",
                    "discord_event_zone_rental_end_title", "Zone rental ended",
                    "discord_event_zone_rental_end_description",
                    "{PLAYER} left zone {ZONE} on {PLOT}",
                    java.util.Map.of(
                            "PLAYER", player.getName(),
                            "ZONE", safeZoneName(zone),
                            "PLOT", plotDisplayName(plot)),
                    0xE67E22);
        }
        plugin.effects().playConfirm(player);
        send(player, "zone_leave_success", "&aYou left the rented zone.");
        open(player);
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
