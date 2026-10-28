package com.aegisguard.caravans;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.gui.HubOriginHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Travel Atlas Caravans tab: dispatch, track ETA, and view recent history. */
public final class CaravanGUI {

    private static final double[] CARGO_STEPS = {25.0D, 50.0D, 100.0D, 250.0D, 500.0D, 1000.0D};

    private final AegisGuard plugin;
    private final Map<UUID, Double> cargoChoice = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> insureChoice = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> escortChoice = new ConcurrentHashMap<>();

    public CaravanGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /** Drops a player's remembered cargo/insurance/escort choices (called on quit). */
    public void clearSession(UUID playerId) {
        if (playerId == null) return;
        cargoChoice.remove(playerId);
        insureChoice.remove(playerId);
        escortChoice.remove(playerId);
    }

    public static final class Holder implements HubOriginHolder {
        private boolean fromHub;
        @Override public boolean isFromHub() { return fromHub; }
        @Override public void setFromHub(boolean fromHub) { this.fromHub = fromHub; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (player == null) return;
        CaravanService service = plugin.caravans();
        if (service == null || !service.isEnabled()) {
            player.sendMessage(GUIManager.color("&8[&bAegisGuard&8]&r "
                    + t(player, "caravans_disabled", "&cCaravans are disabled on this server.")));
            return;
        }
        Holder holder = new Holder();
        holder.setFromHub(GUIManager.hubOriginActive(player));
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.gui().title(player, "caravan_menu_title", "&6Caravans"));
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, GUIManager.createItem(Material.CHEST_MINECART,
                t(player, "caravan_guide_name", "&6Trade caravans"),
                List.of(
                        t(player, "caravan_guide_lore_1", "&7Dispatch goods along public beacon hops."),
                        t(player, "caravan_guide_lore_2", "&7Pay up front. Arrival pays you, the dest owner, and escorts."),
                        t(player, "caravan_guide_lore_3", "&7Ambush risk drops with an escort or insurance."))));

        List<TradeRoute> routes = service.listRoutes();
        int slot = 10;
        if (routes.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "caravan_no_routes_name", "&cNo public trade routes"),
                    List.of(t(player, "caravan_no_routes_lore",
                            "&7Link two public pads (shop/market) to open a route."))));
        } else {
            double cargo = cargoFor(player, service);
            boolean insured = insureChoice.getOrDefault(player.getUniqueId(), false);
            CaravanRules.Quote quote = service.quote(cargo, insured);
            for (TradeRoute route : routes) {
                if (slot == 17) slot = 19;
                if (slot > 25) break;
                List<String> lore = new ArrayList<>();
                lore.add(t(player, "caravan_route_distance", "&7Distance: &f{BLOCKS} blocks",
                        Map.of("BLOCKS", String.valueOf(route.distance()))));
                lore.add(t(player, "caravan_route_eta", "&7Travel: &f{SECONDS}s",
                        Map.of("SECONDS", String.valueOf(Math.max(1L, route.travelMs() / 1000L)))));
                lore.add(t(player, "caravan_route_risk", "&7Risk: &f{RISK}",
                        Map.of("RISK", route.risk().name())));
                lore.add(t(player, "caravan_route_cost", "&7Stake: &f{AMOUNT}",
                        Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", quote.charged()))));
                lore.add(t(player, "caravan_route_click", "&eClick to dispatch."));
                ItemStack item = GUIManager.createItem(iconFor(route.risk()),
                        t(player, "caravan_route_name", "&6{ROUTE}", Map.of("ROUTE", route.label())),
                        lore);
                plugin.gui().tagAction(item, "dispatch:" + route.originId());
                inv.setItem(slot++, item);
            }
        }

        int hist = 28;
        List<Caravan> owned = new ArrayList<>(service.store().forOwner(player.getUniqueId()));
        owned.sort((a, b) -> Long.compare(b.getDispatchedAt(), a.getDispatchedAt()));
        if (owned.isEmpty()) {
            inv.setItem(31, GUIManager.createItem(Material.MAP,
                    t(player, "caravan_history_empty_name", "&7No caravans yet"),
                    List.of(t(player, "caravan_history_empty_lore", "&7Dispatch a route above to get started."))));
        } else {
            for (Caravan caravan : owned) {
                if (hist > 34) break;
                inv.setItem(hist++, caravanIcon(player, caravan));
            }
            inv.setItem(45, ledgerSummary(player, owned));
        }

        double cargo = cargoFor(player, service);
        ItemStack cargoItem = GUIManager.createItem(Material.GOLD_INGOT,
                t(player, "caravan_cargo_name", "&eCargo: &f{AMOUNT}",
                        Map.of("AMOUNT", String.format(java.util.Locale.US, "%.0f", cargo))),
                List.of(t(player, "caravan_cargo_lore", "&7Click to cycle cargo value.")));
        plugin.gui().tagAction(cargoItem, "cycle_cargo");
        inv.setItem(42, cargoItem);

        boolean insured = insureChoice.getOrDefault(player.getUniqueId(), false);
        ItemStack insure = GUIManager.createItem(insured ? Material.LIME_DYE : Material.GRAY_DYE,
                t(player, insured ? "caravan_insure_on" : "caravan_insure_off",
                        insured ? "&aInsurance on" : "&7Insurance off"),
                List.of(t(player, "caravan_insure_lore",
                        "&7Pays a premium; ambush no longer wipes the cargo.")));
        plugin.gui().tagAction(insure, "toggle_insure");
        inv.setItem(43, insure);

        if (service.escortsEnabled()) {
            Player escort = escortFor(player);
            String escortName = escort == null ? "" : escort.getName();
            ItemStack escortItem = GUIManager.createItem(Material.SHIELD,
                    t(player, escort == null ? "caravan_escort_none" : "caravan_escort_picked",
                            escort == null ? "&7Escort: &fnone" : "&aEscort: &f{PLAYER}",
                            Map.of("PLAYER", escortName)),
                    List.of(t(player, "caravan_escort_lore",
                            "&7Halves ambush risk for a {PCT}% cut of delivery. Click to cycle online players.",
                            Map.of("PCT", String.valueOf(Math.round(service.escortCutRate() * 100.0D))))));
            plugin.gui().tagAction(escortItem, "cycle_escort");
            inv.setItem(44, escortItem);
        }

        // Standalone nav row: hub return (when launched from the hub), menu, close.
        if (holder.isFromHub()) {
            ItemStack hub = GUIManager.createItem(Material.COMPASS,
                    t(player, "button_back_hub", "&fBack to Travel Hub"),
                    List.of(t(player, "back_hub_lore", "&7Return to the travel hub.")));
            plugin.gui().tagAction(hub, "hub_return");
            inv.setItem(50, hub);
        }
        ItemStack back = GUIManager.createItem(Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                List.of(t(player, "back_menu_lore", "&7Go back to the main menu.")));
        plugin.gui().tagAction(back, "back_menu");
        inv.setItem(51, back);
        ItemStack close = GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                List.of(t(player, "exit_lore", "&7Close this menu.")));
        plugin.gui().tagAction(close, "close_menu");
        inv.setItem(52, close);

        player.openInventory(inv);
        if (plugin.effects() != null) plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String action = plugin.gui().getAction(event.getCurrentItem());
        if (action == null || action.isBlank()) return;
        CaravanService service = plugin.caravans();
        switch (action) {
            case "hub_return" -> {
                if (holder.isFromHub()) plugin.gui().travelHub().open(player);
                return;
            }
            case "back_menu" -> {
                plugin.gui().openMain(player);
                return;
            }
            case "close_menu" -> {
                player.closeInventory();
                return;
            }
            case "cycle_cargo" -> {
                cycleCargo(player, service);
                open(player);
                return;
            }
            case "toggle_insure" -> {
                if (service != null && service.insuranceEnabled()) {
                    boolean next = !insureChoice.getOrDefault(player.getUniqueId(), false);
                    insureChoice.put(player.getUniqueId(), next);
                }
                open(player);
                return;
            }
            case "cycle_escort" -> {
                if (service != null && service.escortsEnabled()) {
                    cycleEscort(player);
                }
                open(player);
                return;
            }
            default -> { }
        }
        if (action.startsWith("dispatch:") && service != null) {
            UUID origin = parseUuid(action.substring("dispatch:".length()));
            if (origin == null) return;
            boolean insured = insureChoice.getOrDefault(player.getUniqueId(), false);
            UUID escortId = escortChoice.get(player.getUniqueId());
            service.dispatch(player, origin, cargoFor(player, service), insured, escortId);
            open(player);
            return;
        }
        if (action.startsWith("cancel:") && service != null) {
            UUID id = parseUuid(action.substring("cancel:".length()));
            if (id != null) service.cancel(player, id);
            open(player);
            return;
        }
        if (action.startsWith("return:") && service != null) {
            UUID id = parseUuid(action.substring("return:".length()));
            if (id != null) service.dispatchReturn(player, id);
            open(player);
        }
    }

    private ItemStack caravanIcon(Player player, Caravan caravan) {
        Material icon = switch (caravan.getStatus()) {
            case IN_TRANSIT -> Material.CHEST_MINECART;
            case ARRIVED -> Material.EMERALD;
            case FAILED -> Material.TNT;
            case CANCELLED -> Material.BARRIER;
        };
        List<String> lore = new ArrayList<>();
        lore.add(t(player, "caravan_status_line", "&7Status: &f{STATUS}",
                Map.of("STATUS", caravan.getStatus().name())));
        lore.add(t(player, "caravan_event_line", "&7Event: &f{EVENT}",
                Map.of("EVENT", caravan.getLastEvent().name())));
        lore.add(t(player, "caravan_entry_cargo", "&7Cargo: &f{AMOUNT} &8(stake {STAKE})",
                Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", caravan.getCargoValue()),
                        "STAKE", String.format(java.util.Locale.US, "%.2f", caravan.getChargedVault()))));
        if (caravan.inFlight()) {
            long remain = Math.max(0L, caravan.getEtaAt() - System.currentTimeMillis());
            lore.add(t(player, "caravan_eta_line", "&7ETA: &f{SECONDS}s",
                    Map.of("SECONDS", String.valueOf(Math.max(1L, remain / 1000L)))));
            lore.add(t(player, "caravan_cancel_hint", "&eClick to recall if still near the origin."));
        } else if (caravan.getStatus() == Caravan.Status.ARRIVED) {
            lore.add(t(player, "caravan_payout_line", "&7Payout: &f{AMOUNT}",
                    Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", caravan.getDeliveredValue()))));
            if (caravan.getTollPaid() > 0.0D) {
                lore.add(t(player, "caravan_entry_toll", "&7Route toll: &f{AMOUNT}",
                        Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", caravan.getTollPaid()))));
            }
            if (caravan.getEscortPaid() > 0.0D) {
                lore.add(t(player, "caravan_entry_escort", "&7Escort cut: &f{AMOUNT}",
                        Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", caravan.getEscortPaid()))));
            }
            lore.add(t(player, "caravan_return_hint", "&eClick to send the return caravan."));
        } else if (!caravan.getFailReason().isBlank()) {
            lore.add(t(player, "caravan_fail_line", "&c{REASON}", Map.of("REASON", caravan.getFailReason())));
        }
        ItemStack item = GUIManager.createItem(icon,
                t(player, "caravan_entry_name", "&6{ROUTE}", Map.of("ROUTE", caravan.routeLabel())),
                lore);
        if (caravan.inFlight()) {
            plugin.gui().tagAction(item, "cancel:" + caravan.getId());
        } else if (caravan.getStatus() == Caravan.Status.ARRIVED) {
            plugin.gui().tagAction(item, "return:" + caravan.getId());
        }
        return item;
    }

    /** Ledger summary: totals across the owner's recorded shipments. */
    private ItemStack ledgerSummary(Player player, List<Caravan> owned) {
        int arrived = 0;
        int failed = 0;
        int transit = 0;
        double delivered = 0.0D;
        double staked = 0.0D;
        for (Caravan caravan : owned) {
            if (caravan == null) continue;
            staked += caravan.getChargedVault();
            switch (caravan.getStatus()) {
                case ARRIVED -> { arrived++; delivered += caravan.getDeliveredValue(); }
                case FAILED, CANCELLED -> failed++;
                case IN_TRANSIT -> transit++;
            }
        }
        return GUIManager.createItem(Material.BOOK,
                t(player, "caravan_ledger_name", "&6Ledger"),
                List.of(
                        t(player, "caravan_ledger_runs", "&7Runs: &f{COUNT}",
                                Map.of("COUNT", String.valueOf(owned.size()))),
                        t(player, "caravan_ledger_earned", "&7Delivered: &a{AMOUNT}",
                                Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", delivered))),
                        t(player, "caravan_ledger_staked", "&7Staked: &f{AMOUNT}",
                                Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", staked))),
                        t(player, "caravan_ledger_split", "&7Arrived &a{OK}&7 / lost &c{BAD}&7 / riding &e{FLYING}",
                                Map.of("OK", String.valueOf(arrived), "BAD", String.valueOf(failed),
                                        "FLYING", String.valueOf(transit)))));
    }

    private double cargoFor(Player player, CaravanService service) {
        double fallback = service == null ? 100.0D : service.defaultCargo();
        return cargoChoice.getOrDefault(player.getUniqueId(), fallback);
    }

    private void cycleCargo(Player player, CaravanService service) {
        double current = cargoFor(player, service);
        double next = CARGO_STEPS[0];
        for (int i = 0; i < CARGO_STEPS.length; i++) {
            if (Math.abs(CARGO_STEPS[i] - current) < 0.01D) {
                next = CARGO_STEPS[(i + 1) % CARGO_STEPS.length];
                break;
            }
            if (CARGO_STEPS[i] > current) {
                next = CARGO_STEPS[i];
                break;
            }
            next = CARGO_STEPS[0];
        }
        if (service != null) next = Math.min(service.maxCargo(), Math.max(service.minCargo(), next));
        cargoChoice.put(player.getUniqueId(), next);
    }

    /** Current escort pick as an online Player, or null when unset/gone (stale picks are pruned). */
    private Player escortFor(Player player) {
        UUID pick = escortChoice.get(player.getUniqueId());
        if (pick == null) return null;
        Player escort = Bukkit.getPlayer(pick);
        if (escort == null) {
            escortChoice.remove(player.getUniqueId());
            return null;
        }
        return escort;
    }

    /**
     * Cycle the escort pick through online players (excluding the dispatcher):
     * none → first eligible → … → last eligible → none.
     */
    private void cycleEscort(Player player) {
        List<Player> eligible = new ArrayList<>(Bukkit.getOnlinePlayers());
        eligible.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
        eligible.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        UUID current = escortChoice.get(player.getUniqueId());
        UUID next = null;
        if (current == null) {
            next = eligible.isEmpty() ? null : eligible.get(0).getUniqueId();
        } else {
            for (int i = 0; i < eligible.size(); i++) {
                if (eligible.get(i).getUniqueId().equals(current)) {
                    next = i + 1 < eligible.size() ? eligible.get(i + 1).getUniqueId() : null;
                    break;
                }
            }
            // Current pick vanished from the list (went offline): restart the cycle.
            if (next == null && !eligible.isEmpty()
                    && eligible.stream().noneMatch(p -> p.getUniqueId().equals(current))) {
                next = eligible.get(0).getUniqueId();
            }
        }
        if (next == null) escortChoice.remove(player.getUniqueId());
        else escortChoice.put(player.getUniqueId(), next);
    }

    private Material iconFor(CaravanRules.Risk risk) {
        return switch (risk) {
            case HIGH -> Material.TNT_MINECART;
            case MEDIUM -> Material.FURNACE_MINECART;
            default -> Material.CHEST_MINECART;
        };
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String t(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private String t(Player player, String key, String fallback, Map<String, String> vars) {
        return plugin.gui().tr(player, key, fallback, vars);
    }
}
