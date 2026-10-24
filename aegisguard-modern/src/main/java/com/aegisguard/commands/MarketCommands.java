package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;  // ✅ NEW: Currency Type enum
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.entity.Player;
import java.util.*;

final class MarketCommands extends CommandBase {

    MarketCommands(AegisGuard plugin) { super(plugin); }

    void handleSell(Player p, String[] args) {
    Plot plot = plugin.store().getPlotAt(p.getLocation());
    if (plot == null || !plot.isOwner(p)) {
        sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
        return;
    }
    if (plot.isServerZone() || plot.isGroupPlot() || plot.isForAuction() || plot.isForRent() || plot.hasActiveRental()
            || plot.getZones().stream().anyMatch(zone -> zone.isRented() || zone.isListedForRent())) {
        sendKey(p, "market-listing-conflict", "&cThis plot cannot be listed while another ownership or market state is active.");
        return;
    }
    if (args.length < 2) {
        sendKey(p, "sell_usage", "&cUsage: /ag sell <price>");
        return;
    }

    double price;
    try {
        price = Double.parseDouble(args[1]);
        if (!Double.isFinite(price) || price <= 0) {
            sendKey(p, "price_must_positive", "&cPrice must be positive.");
            return;
        }
    } catch (NumberFormatException e) {
        sendKey(p, "invalid_number", "&cInvalid number.");
        return;
    }

    if (price <= 0) {
        sendKey(p, "price_must_positive", "&cPrice must be positive.");
        return;
    }

    plot.setForSale(true, price);

    plugin.store().savePlot(plot);
    plugin.store().setDirty(true);

    String formatted;
    if (plugin.eco() != null && plugin.eco().isVaultEnabled()) {
        formatted = plugin.eco().format(price, CurrencyType.VAULT);
    } else {
        formatted = String.valueOf(price);
    }

    sendKey(p, "market-for-sale", "&a✔ Claim listed for &6{PRICE}&a.",
            Map.of("PRICE", formatted));
    }

    void handleUnsell(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.canManage(p, plugin)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }

        plot.setForSale(false, 0);

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        sendKey(p, "market-not-for-sale", "&e✔ Claim listing removed.");
    }

    void openMarketMenu(Player p, String[] args) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        boolean canUseLocal = plot != null
                && plugin.marketBridges() != null
                && plugin.marketBridges().plotQualifiesForLocalMarket(plot, p);

        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("global")) {
                plugin.gui().market().open(p, 0);
                return;
            }
            if (mode.equals("local")) {
                if (canUseLocal) {
                    plugin.gui().localMarket().open(p, plot);
                } else {
                    sendKey(p, "local_market_not_available", "&cThere is no local market set up on this plot.");
                }
                return;
            }
        }

        if (canUseLocal && plugin.marketBridges() != null && plugin.marketBridges().preferLocalWhenInPlot()) {
            plugin.gui().localMarket().open(p, plot);
        } else {
            plugin.gui().market().open(p, 0);
        }
    }

    void handleRentListing(Player p, String[] args) {
        if (!plugin.cfg().raw().getBoolean("full_plot_renting.enabled", true)) {
            sendKey(p, "market-renting-disabled", "&cFull-plot renting is disabled on this server.");
            return;
        }
        if (!p.hasPermission("aegis.rent")) {
            sendKey(p, "no_permission", "&cYou do not have permission to list plots for rent.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.isOwner(p)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (plot.isServerZone() || plot.isGroupPlot() || plot.isForAuction() || plot.isForSale() || plot.hasActiveRental()
                || plot.getZones().stream().anyMatch(zone -> zone.isRented() || zone.isListedForRent())) {
            sendKey(p, "market-listing-conflict", "&cThis plot cannot be listed while another ownership or market state is active.");
            return;
        }
        if (args.length < 2) {
            sendKey(p, "rent_usage", "&cUsage: /ag rent <price> [days] [deposit]");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
        } catch (NumberFormatException exception) {
            sendKey(p, "invalid_number", "&cInvalid number.");
            return;
        }
        double minimum = Math.max(0.01, plugin.cfg().raw().getDouble("full_plot_renting.minimum_price", 1.0));
        double maximum = Math.max(minimum, plugin.cfg().raw().getDouble("full_plot_renting.maximum_price", 1_000_000_000.0));
        if (!Double.isFinite(price) || price < minimum || price > maximum) {
            sendKey(p, "rent_price_range", "&cRent must be between {MIN} and {MAX}.", Map.of(
                    "MIN", String.valueOf(minimum), "MAX", String.valueOf(maximum)));
            return;
        }

        int defaultDays = Math.max(1, plugin.cfg().raw().getInt("full_plot_renting.duration_days", 7));
        int maximumDays = Math.max(defaultDays, plugin.cfg().raw().getInt("full_plot_renting.maximum_duration_days", 90));
        int days = defaultDays;
        double deposit = 0.0D;
        try {
            if (args.length >= 3) days = Integer.parseInt(args[2]);
            if (args.length >= 4) deposit = Double.parseDouble(args[3]);
        } catch (NumberFormatException exception) {
            sendKey(p, "invalid_number", "&cDays and deposit must be valid numbers.");
            return;
        }
        double maximumDeposit = Math.max(0.0D,
                plugin.cfg().raw().getDouble("full_plot_renting.maximum_deposit", 1_000_000.0D));
        if (days < 1 || days > maximumDays || !Double.isFinite(deposit) || deposit < 0.0D || deposit > maximumDeposit) {
            sendMsg(p, "&cDays must be 1-" + maximumDays + " and deposit must be 0-" + maximumDeposit + ".");
            return;
        }

        plot.setForRent(true, price);
        plugin.territoryLife().setOffer(plot.getPlotId(), price, deposit, days);
        plugin.store().savePlotSync(plot);
        sendKey(p, "market-for-rent", "&a✔ Claim listed for rent at &6{PRICE}&a per term.", Map.of(
                "PRICE", plugin.eco().format(price, CurrencyType.VAULT)));
        sendMsg(p, "&7Term: &b" + days + " day(s) &8| &7Deposit: &6"
                + plugin.eco().format(deposit, CurrencyType.VAULT));
        plugin.territoryLife().logKey(plot.getPlotId(), p.getUniqueId(), "RENTAL_LISTED",
                "activity_detail_rental_listed",
                "Listed for " + price + ", deposit=" + deposit + ", term=" + days + " day(s).",
                Map.of("PRICE", String.valueOf(price), "DEPOSIT", String.valueOf(deposit), "DAYS", String.valueOf(days)));
    }

    void handleUnrent(Player p) {
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null || !plot.isOwner(p)) {
            sendKey(p, "no_plot_here", "&c❌ You must be standing inside a plot you own to do that.");
            return;
        }
        if (plot.hasActiveRental()) {
            sendKey(p, "market-rental-active", "&cYou cannot remove this listing until the active rental expires.");
            return;
        }
        plot.setForRent(false, 0);
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.store().savePlotSync(plot);
        sendKey(p, "market-not-for-rent", "&e✔ Rental listing removed.");
        plugin.territoryLife().logKey(plot.getPlotId(), p.getUniqueId(), "RENTAL_UNLISTED",
                "activity_detail_rental_unlisted", "Rental listing removed.", Map.of());
    }

    void handleRentalContract(Player p, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("status") || action.equals("info") || action.equals("gui") || action.equals("menu")) {
            plugin.gui().myRentals().open(p);
            return;
        }

        TerritoryLifeService.RentalContract contract = findRentalContract(p);
        if (contract == null) {
            sendKey(p, "rental_contract_none", "&eYou do not have an active full-plot rental contract.");
            plugin.gui().myRentals().open(p);
            return;
        }
        Plot plot = plugin.store().getAllPlots().stream()
                .filter(candidate -> candidate != null && contract.plotId().equals(candidate.getPlotId()))
                .findFirst().orElse(null);
        if (plot == null) {
            sendKey(p, "rental_contract_plot_missing", "&cThis contract's plot is missing. Ask an admin to run /agadmin doctor scan.");
            return;
        }

        if (action.equals("renew")) {
            if (!contract.renterId().equals(p.getUniqueId())) {
                sendKey(p, "rental_contract_only_renter", "&cOnly the renter can renew this contract.");
                return;
            }
            plugin.gui().rentConfirm().openPlotRenew(p, plot, contract.rent(), contract.termDays(), "my_rentals");
            return;
        }

        if (action.equals("cancel")) {
            boolean renter = contract.renterId().equals(p.getUniqueId());
            boolean owner = contract.ownerId().equals(p.getUniqueId());
            if (!renter && !owner) {
                sendKey(p, "rental_contract_not_party", "&cYou are not part of this rental contract.");
                return;
            }
            if (owner && !plugin.getConfig().getBoolean("full_plot_renting.allow_owner_early_cancel", false)) {
                sendKey(p, "rental_contract_owner_cancel_disabled", "&cOwners cannot end active contracts early on this server.");
                return;
            }
            plugin.gui().rentConfirm().openPlotCancel(p, plot, contract.deposit(), "my_rentals");
            return;
        }
        sendKey(p, "rental_contract_usage", "&cUsage: /ag rental <status|renew|cancel confirm>");
    }

    private TerritoryLifeService.RentalContract findRentalContract(Player player) {
        Plot here = plugin.store().getPlotAt(player.getLocation());
        if (here != null) {
            TerritoryLifeService.RentalContract local = plugin.territoryLife().contract(here.getPlotId());
            if (local != null && (local.ownerId().equals(player.getUniqueId()) || local.renterId().equals(player.getUniqueId()))) {
                return local;
            }
        }
        return plugin.territoryLife().contracts().stream()
                .filter(contract -> contract.ownerId().equals(player.getUniqueId()) || contract.renterId().equals(player.getUniqueId()))
                .findFirst().orElse(null);
    }

    void handleDiscover(Player player, String[] args) {
        if (!player.hasPermission("aegis.discovery")) {
            sendKey(player, "no_permission", "&cYou do not have permission to use plot discovery.");
            return;
        }
        if (!plugin.getConfig().getBoolean("plot_discovery.enabled", true)) {
            sendKey(player, "discovery_disabled", "&cPlot discovery is disabled on this server.");
            return;
        }
        if (args.length == 1) {
            plugin.gui().visit().open(player, 0, com.aegisguard.gui.VisitGUI.VisitMode.DISCOVER);
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("favorites")) {
            plugin.gui().visit().open(player, 0, com.aegisguard.gui.VisitGUI.VisitMode.FAVORITES);
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.isOwner(player)) {
            sendKey(player, "discovery_owner_required", "&cStand inside a plot you own to change its discovery settings.");
            return;
        }
        if (action.equals("category") && args.length >= 3) {
            String category = args[2].toLowerCase(Locale.ROOT);
            List<String> allowed = plugin.getConfig().getStringList("plot_discovery.categories");
            if (!allowed.stream().anyMatch(value -> value.equalsIgnoreCase(category))) {
                sendKey(player, "discovery_invalid_category", "&cCategory must be one of: &f{CATEGORIES}", Map.of(
                        "CATEGORIES", String.join(", ", allowed)));
                return;
            }
            plugin.territoryLife().setCategory(plot.getPlotId(), category);
            plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "DISCOVERY_CATEGORY",
                    "activity_detail_discovery_category",
                    "Discovery category changed to " + category + ".",
                    Map.of("CATEGORY", category == null ? "" : category));
            sendKey(player, "discovery_category_set", "&aDiscovery category set to &e{CATEGORY}&a.", Map.of(
                    "CATEGORY", category));
            return;
        }
        if (action.equals("visibility") && args.length >= 3) {
            if (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off")
                    && !args[2].equalsIgnoreCase("public") && !args[2].equalsIgnoreCase("private")) {
                sendKey(player, "discovery_visibility_invalid", "&cVisibility must be on or off.");
                return;
            }
            boolean visible = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("public");
            plugin.territoryLife().setVisible(plot.getPlotId(), visible);
            String visibility = visible ? "public" : "private";
            plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "DISCOVERY_VISIBILITY",
                    "activity_detail_discovery_visibility",
                    "Discovery visibility changed to " + visibility + ".",
                    Map.of("VISIBILITY", visibility));
            sendKey(player, visible ? "discovery_visible" : "discovery_hidden",
                    visible ? "&aThis plot is visible in discovery." : "&eThis plot is hidden from discovery.");
            return;
        }
        sendMsg(player, "&e/ag discover &7| &e/ag discover favorites &7| &e/ag discover category <name> &7| &e/ag discover visibility <on|off>");
    }

    void handleFavorite(Player player) {
        if (!player.hasPermission("aegis.discovery")) {
            sendKey(player, "no_permission", "&cYou do not have permission to use plot discovery.");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            sendKey(player, "favorite_plot_required", "&cStand inside a plot to favorite it.");
            return;
        }
        boolean added = plugin.territoryLife().toggleFavorite(player.getUniqueId(), plot.getPlotId());
        sendKey(player, added ? "favorite_added" : "favorite_removed",
                added ? "&aPlot added to your favorites." : "&ePlot removed from your favorites.");
    }

    void handleActivity(Player player) {
        if (!player.hasPermission("aegis.activity")) {
            sendKey(player, "no_permission", "&cYou do not have permission to view territory activity.");
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            sendKey(player, "activity_plot_required", "&cStand inside a plot you can manage to view its activity.");
            return;
        }
        List<TerritoryLifeService.ActivityEntry> entries = plugin.territoryLife().activity(plot.getPlotId(), 10);
        sendKey(player, "activity_title", "&6Recent Territory Activity &8(&f{PLOT}&8)", Map.of(
                "PLOT", getReadablePlotName(plot)));
        if (entries.isEmpty()) {
            sendKey(player, "activity_empty", "&7No activity has been recorded yet.");
            return;
        }
        long now = System.currentTimeMillis();
        for (TerritoryLifeService.ActivityEntry entry : entries) {
            long minutes = Math.max(0L, (now - entry.timestamp()) / 60_000L);
            sendKey(player, "activity_entry", "&8- &e{TYPE} &7{DETAILS} &8({MINUTES}m ago)", Map.of(
                    "TYPE", com.aegisguard.territory.ActivityText.resolveTypeLabel(plugin, player, entry.type()),
                    "DETAILS", com.aegisguard.territory.ActivityText.resolveDetails(plugin, player, entry.details()),
                    "MINUTES", Long.toString(minutes)));
        }
    }

    void handleLike(Player p) {
        if (!plugin.cfg().isLikesEnabled()) {
            sendKey(p, "like_disabled", "&cLikes are disabled on this server/world.");
            return;
        }

        Plot plot = plugin.store().getPlotAt(p.getLocation());
        if (plot == null) {
            sendKey(p, "no_plot_here", "&c❌ You are not standing inside your claim.");
            return;
        }
        if (plot.getOwner().equals(p.getUniqueId())) {
            sendKey(p, "like_own_plot", "&cError: You cannot like your own claim.");
            return;
        }

        if (plugin.cfg().oneLikePerPlayer() && plot.hasLiked(p.getUniqueId())) {
            plot.toggleLike(p.getUniqueId());
            sendKey(p, "like_removed", "&eYour like has been removed.");
        } else {
            plot.toggleLike(p.getUniqueId());
            sendKey(p, "like_success", "&a✔ You liked this claim. Total likes: &e{AMOUNT}",
                    Map.of("AMOUNT", String.valueOf(plot.getLikes())));
            if (plugin.horizons() != null) plugin.horizons().recordLike(plot, p.getUniqueId());
            plugin.effects().playConfirm(p);
        }

        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
    }

}