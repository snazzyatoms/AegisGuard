package com.aegisguard.caravans;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.gui.VisitGUI;
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

    public CaravanGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (player == null) return;
        CaravanService service = plugin.caravans();
        if (service == null || !service.isEnabled()) {
            player.sendMessage(GUIManager.color("&8[&bAegisGuard&8]&r &cCaravans are disabled on this server."));
            return;
        }
        Inventory inv = Bukkit.createInventory(new Holder(), 54,
                plugin.gui().title(player, "atlas_title_caravans", "&6Travel Atlas · Caravans"));
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

        if (plugin.gui() != null && plugin.gui().visit() != null) {
            plugin.gui().visit().attachAtlasChrome(player, inv, VisitGUI.AtlasTab.CARAVANS);
        }
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
            case "atlas_destinations" -> {
                plugin.gui().visit().openAtlas(player, VisitGUI.AtlasTab.DESTINATIONS);
                return;
            }
            case "atlas_beacons" -> {
                plugin.gui().visit().openAtlas(player, VisitGUI.AtlasTab.MY_BEACONS);
                return;
            }
            case "atlas_arrival" -> {
                plugin.gui().visit().openAtlas(player, VisitGUI.AtlasTab.ARRIVAL);
                return;
            }
            case "atlas_caravans" -> {
                open(player);
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
            default -> { }
        }
        if (action.startsWith("dispatch:") && service != null) {
            UUID origin = parseUuid(action.substring("dispatch:".length()));
            if (origin == null) return;
            boolean insured = insureChoice.getOrDefault(player.getUniqueId(), false);
            service.dispatch(player, origin, cargoFor(player, service), insured, null);
            open(player);
            return;
        }
        if (action.startsWith("cancel:") && service != null) {
            UUID id = parseUuid(action.substring("cancel:".length()));
            if (id != null) service.cancel(player, id);
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
        if (caravan.inFlight()) {
            long remain = Math.max(0L, caravan.getEtaAt() - System.currentTimeMillis());
            lore.add(t(player, "caravan_eta_line", "&7ETA: &f{SECONDS}s",
                    Map.of("SECONDS", String.valueOf(Math.max(1L, remain / 1000L)))));
            lore.add(t(player, "caravan_cancel_hint", "&eClick to recall if still near the origin."));
        } else if (caravan.getStatus() == Caravan.Status.ARRIVED) {
            lore.add(t(player, "caravan_payout_line", "&7Payout: &f{AMOUNT}",
                    Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", caravan.getDeliveredValue()))));
        } else if (!caravan.getFailReason().isBlank()) {
            lore.add(t(player, "caravan_fail_line", "&c{REASON}", Map.of("REASON", caravan.getFailReason())));
        }
        ItemStack item = GUIManager.createItem(icon,
                t(player, "caravan_entry_name", "&6{ROUTE}", Map.of("ROUTE", caravan.routeLabel())),
                lore);
        if (caravan.inFlight()) plugin.gui().tagAction(item, "cancel:" + caravan.getId());
        return item;
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
