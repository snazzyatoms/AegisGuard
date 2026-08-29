package com.aegisguard.caravans;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.beacon.TeleportBeacon;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Player-dispatched trade caravans over public beacon hops. Charge-then-deliver,
 * Folia-safe ticks, resume-on-load via persisted ETA.
 */
public final class CaravanService {

    private final AegisGuard plugin;
    private final CaravanStore store;
    private final Map<UUID, Long> lastDispatchAt = new ConcurrentHashMap<>();
    private final Set<UUID> dispatchLocks = ConcurrentHashMap.newKeySet();

    public CaravanService(AegisGuard plugin) {
        this.plugin = plugin;
        this.store = new CaravanStore(plugin);
    }

    public CaravanStore store() { return store; }

    public boolean isEnabled() {
        try {
            return plugin.modules().on(Modules.Id.CARAVANS)
                    && plugin.getConfig().getBoolean("caravans.enabled", true);
        } catch (Throwable ignored) {
            return plugin.getConfig().getBoolean("caravans.enabled", true);
        }
    }

    public void load() { store.load(); }
    public void save() { store.save(); }
    public boolean isDirty() { return store.isDirty(); }

    public int maxActive() {
        return Math.max(1, plugin.getConfig().getInt("caravans.max_active_per_player", 3));
    }

    public long dispatchCooldownMs() {
        return Math.max(0L, plugin.getConfig().getLong("caravans.dispatch_cooldown_seconds", 30L)) * 1000L;
    }

    public double minCargo() {
        return Math.max(0.0D, plugin.getConfig().getDouble("caravans.min_cargo", 10.0D));
    }

    public double maxCargo() {
        return Math.max(minCargo(), plugin.getConfig().getDouble("caravans.max_cargo", 10_000.0D));
    }

    public double defaultCargo() {
        double value = plugin.getConfig().getDouble("caravans.default_cargo", 100.0D);
        return Math.min(maxCargo(), Math.max(minCargo(), value));
    }

    public boolean requireVault() {
        return plugin.getConfig().getBoolean("caravans.require_vault", true);
    }

    public boolean insuranceEnabled() {
        return plugin.getConfig().getBoolean("caravans.insurance_enabled", true);
    }

    public List<TradeRoute> listRoutes() {
        List<TradeRoute> routes = new ArrayList<>();
        if (plugin.beacons() == null || !plugin.beacons().isEnabled()) return routes;
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (TeleportBeacon origin : plugin.beacons().store().all()) {
            TradeRoute route = routeFrom(origin);
            if (route == null) continue;
            String key = route.originId() + ">" + route.destId();
            if (!seen.add(key)) continue;
            routes.add(route);
        }
        return routes;
    }

    public @Nullable TradeRoute routeFrom(@Nullable TeleportBeacon origin) {
        if (origin == null || !origin.isEnabled() || !origin.isPublicAccess() || origin.isStaffOnly()) {
            return null;
        }
        if (!origin.isLinked()) return null;
        if (plugin.getConfig().getBoolean("caravans.require_trade_purpose", false)
                && !isTradePurpose(origin.getPurpose())) {
            return null;
        }
        TeleportBeacon dest = plugin.beacons().store().get(origin.getLinkedBeaconId());
        if (dest == null || !dest.isEnabled() || origin.getId().equals(dest.getId())) return null;
        if (!dest.isPublicAccess() || dest.isStaffOnly()) return null;
        int distance = CaravanRules.chebyshev(origin.getX(), origin.getZ(), dest.getX(), dest.getZ());
        if (distance <= 0) distance = 1;
        long travelMs = CaravanRules.travelTimeMs(distance,
                Math.max(1L, plugin.getConfig().getLong("caravans.ms_per_block", 50L)),
                Math.max(1000L, plugin.getConfig().getLong("caravans.min_travel_ms", 5_000L)),
                Math.max(0L, plugin.getConfig().getLong("caravans.max_travel_ms", 600_000L)));
        CaravanRules.Risk risk = CaravanRules.riskForDistance(distance,
                plugin.getConfig().getInt("caravans.risk_medium_distance", 250),
                plugin.getConfig().getInt("caravans.risk_high_distance", 800));
        UUID destOwner = null;
        Plot destPlot = plugin.store() == null ? null : plugin.store().getPlotById(dest.getPlotId());
        if (destPlot != null) destOwner = destPlot.getOwner();
        return new TradeRoute(origin.getId(), dest.getId(), origin.getName(), dest.getName(),
                origin.getWorldName(), origin.getX(), origin.getZ(), dest.getX(), dest.getZ(),
                distance, travelMs, risk, destOwner);
    }

    public CaravanRules.Quote quote(double cargoValue, boolean insured) {
        return CaravanRules.quote(clampCargo(cargoValue),
                plugin.getConfig().getDouble("caravans.fee_rate", 0.05D),
                plugin.getConfig().getDouble("caravans.min_fee", 1.0D),
                insuranceEnabled() && insured ? plugin.getConfig().getDouble("caravans.insurance_rate", 0.15D) : 0.0D,
                insuranceEnabled() && insured);
    }

    public @Nullable Caravan dispatch(Player player, UUID originBeaconId, double cargoValue,
                                      boolean insured, @Nullable UUID escortId) {
        if (player == null || !isEnabled()) return null;
        if (!dispatchLocks.add(player.getUniqueId())) {
            send(player, "caravan_busy", "&eA caravan dispatch is already in progress.");
            return null;
        }
        try {
            return dispatchUnlocked(player, originBeaconId, cargoValue, insured, escortId);
        } finally {
            dispatchLocks.remove(player.getUniqueId());
        }
    }

    private @Nullable Caravan dispatchUnlocked(Player player, UUID originBeaconId, double cargoValue,
                                               boolean insured, @Nullable UUID escortId) {
        if (plugin.beacons() == null || !plugin.beacons().isEnabled()) {
            send(player, "caravan_need_beacons", "&cCaravans need Teleport Beacons to be enabled.");
            return null;
        }
        if (requireVault() && (plugin.vault() == null || !plugin.vault().isEnabled())) {
            send(player, "caravan_need_vault", "&cCaravans require Vault economy on this server.");
            return null;
        }
        long wait = CaravanRules.remainingCooldownMs(
                lastDispatchAt.getOrDefault(player.getUniqueId(), 0L),
                System.currentTimeMillis(), dispatchCooldownMs());
        if (wait > 0L) {
            send(player, "caravan_cooldown",
                    "&eYou can dispatch another caravan in &f{SECONDS}&e second(s).",
                    Map.of("SECONDS", String.valueOf(Math.max(1L, wait / 1000L))));
            return null;
        }
        if (store.activeCount(player.getUniqueId()) >= maxActive()) {
            send(player, "caravan_at_cap",
                    "&cYou already have &f{COUNT}&c caravan(s) in transit.",
                    Map.of("COUNT", String.valueOf(maxActive())));
            return null;
        }
        TeleportBeacon origin = plugin.beacons().store().get(originBeaconId);
        TradeRoute route = routeFrom(origin);
        if (route == null) {
            send(player, "caravan_no_route", "&cThat pad is not a public trade route.");
            return null;
        }
        if (CaravanRules.sameHop(route.originId(), route.destId())) {
            send(player, "caravan_same_pad", "&cA caravan cannot travel from a pad to itself.");
            return null;
        }
        String gate = nodeGate(player, origin, plugin.beacons().store().get(route.destId()), true);
        if (gate != null) {
            send(player, gate, gateMessage(gate));
            return null;
        }
        UUID escort = escortId != null && escortId.equals(player.getUniqueId()) ? null : escortId;
        if (escort != null && escort.equals(route.destPlotOwner()) && escort.equals(player.getUniqueId())) {
            escort = null;
        }
        double cargo = clampCargo(cargoValue);
        boolean useInsurance = insuranceEnabled() && insured;
        CaravanRules.Quote cost = quote(cargo, useInsurance);
        if (cost.charged() > 0.0D && plugin.vault() != null && plugin.vault().isEnabled()) {
            if (!plugin.vault().has(player, cost.charged()) || !plugin.vault().charge(player, cost.charged())) {
                send(player, "caravan_cannot_afford",
                        "&cYou cannot afford this caravan (&f{AMOUNT}&c).",
                        Map.of("AMOUNT", String.format(java.util.Locale.US, "%.2f", cost.charged())));
                return null;
            }
        }
        long now = System.currentTimeMillis();
        Caravan caravan = new Caravan(UUID.randomUUID());
        caravan.setOwnerId(player.getUniqueId());
        caravan.setOwnerName(player.getName());
        caravan.setOriginBeaconId(route.originId());
        caravan.setDestBeaconId(route.destId());
        caravan.setOriginName(route.originName());
        caravan.setDestName(route.destName());
        caravan.setDestPlotOwner(route.destPlotOwner());
        caravan.setEscortId(escort);
        caravan.setCargoValue(cargo);
        caravan.setFee(cost.fee());
        caravan.setInsurancePremium(cost.insurancePremium());
        caravan.setChargedVault(cost.charged());
        caravan.setInsured(useInsurance);
        caravan.setStatus(Caravan.Status.IN_TRANSIT);
        caravan.setDispatchedAt(now);
        caravan.setEtaAt(now + route.travelMs());
        store.put(caravan);
        store.save();
        lastDispatchAt.put(player.getUniqueId(), now);
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.CARAVAN, player, caravan.routeLabel(),
                    "Dispatched cargo " + cargo);
        }
        send(player, "caravan_dispatched",
                "&aCaravan dispatched on &f{ROUTE}&a. ETA &f{SECONDS}&a second(s).",
                Map.of("ROUTE", caravan.routeLabel(),
                        "SECONDS", String.valueOf(Math.max(1L, route.travelMs() / 1000L))));
        if (plugin.effects() != null) plugin.effects().playConfirm(player);
        return caravan;
    }

    public boolean cancel(Player player, UUID caravanId) {
        if (player == null || caravanId == null || !isEnabled()) return false;
        Caravan caravan = store.get(caravanId);
        if (caravan == null || !caravan.inFlight()) return false;
        if (!player.getUniqueId().equals(caravan.getOwnerId()) && !plugin.isAdmin(player)) return false;
        double window = plugin.getConfig().getDouble("caravans.cancel_progress", 0.25D);
        if (!CaravanRules.canCancel(caravan.getDispatchedAt(), caravan.getEtaAt(),
                System.currentTimeMillis(), window)) {
            send(player, "caravan_too_late", "&cThat caravan is too far along the road to recall.");
            return false;
        }
        refund(caravan.getOwnerId(), caravan.getChargedVault());
        caravan.setStatus(Caravan.Status.CANCELLED);
        caravan.setFailReason("cancelled");
        caravan.setArrivedAt(System.currentTimeMillis());
        caravan.setNotified(true);
        store.markDirty();
        store.save();
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.CARAVAN, player, caravan.routeLabel(), "Cancelled in transit");
        }
        send(player, "caravan_cancelled", "&eCaravan recalled. Your stake was refunded.");
        return true;
    }

    public void tick() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Caravan caravan : store.inFlight()) {
            if (!CaravanRules.shouldComplete(caravan.getEtaAt(), now)) continue;
            complete(caravan, now);
            changed = true;
        }
        if (changed) store.save();
    }

    /**
     * Completes overdue in-flight caravans (used after load so downtime still settles).
     */
    public int resumeOverdue(long nowMs) {
        int completed = 0;
        for (Caravan caravan : store.inFlight()) {
            if (!CaravanRules.shouldComplete(caravan.getEtaAt(), nowMs)) continue;
            complete(caravan, nowMs);
            completed++;
        }
        if (completed > 0) store.save();
        return completed;
    }

    public void abortForBeacon(UUID beaconId, String reason) {
        if (beaconId == null) return;
        boolean changed = false;
        for (Caravan caravan : store.inFlight()) {
            if (!beaconId.equals(caravan.getOriginBeaconId()) && !beaconId.equals(caravan.getDestBeaconId())) {
                continue;
            }
            failAndRefund(caravan, reason == null ? "route_closed" : reason);
            changed = true;
        }
        if (changed) store.save();
    }

    public void notifyPending(Player player) {
        if (player == null) return;
        for (Caravan caravan : store.forOwner(player.getUniqueId())) {
            if (caravan.inFlight() || caravan.isNotified()) continue;
            announce(caravan);
            caravan.setNotified(true);
            store.markDirty();
        }
    }

    private void complete(Caravan caravan, long now) {
        TeleportBeacon origin = plugin.beacons() == null ? null : plugin.beacons().store().get(caravan.getOriginBeaconId());
        TeleportBeacon dest = plugin.beacons() == null ? null : plugin.beacons().store().get(caravan.getDestBeaconId());
        Player owner = Bukkit.getPlayer(caravan.getOwnerId());
        String gate = nodeGate(owner, origin, dest, false);
        if (gate != null) {
            failAndRefund(caravan, gate);
            return;
        }
        boolean escorted = caravan.getEscortId() != null;
        int roll = ThreadLocalRandom.current().nextInt(100);
        CaravanRules.Event event = CaravanRules.rollEvent(roll,
                plugin.getConfig().getInt("caravans.events.ambush_weight", 15),
                plugin.getConfig().getInt("caravans.events.toll_weight", 15),
                plugin.getConfig().getInt("caravans.events.boon_weight", 10),
                plugin.getConfig().getInt("caravans.events.delay_weight", 10),
                escorted,
                Math.max(1, plugin.getConfig().getInt("caravans.escort_ambush_divisor", 2)));
        if (event == CaravanRules.Event.DELAY && caravan.getLastEvent() != CaravanRules.Event.DELAY) {
            long extra = Math.max(1000L, plugin.getConfig().getLong("caravans.delay_ms", 15_000L));
            caravan.setLastEvent(CaravanRules.Event.DELAY);
            caravan.setEtaAt(now + extra);
            store.markDirty();
            return;
        }
        CaravanRules.Quote quote = new CaravanRules.Quote(caravan.getCargoValue(), caravan.getFee(),
                caravan.getInsurancePremium(), caravan.getChargedVault());
        CaravanRules.Settlement settlement = CaravanRules.settle(
                caravan.getCargoValue(), quote, event, escorted, caravan.isInsured(),
                plugin.getConfig().getDouble("caravans.ambush_loss_rate", 1.0D),
                plugin.getConfig().getDouble("caravans.boon_bonus_rate", 0.20D),
                plugin.getConfig().getDouble("caravans.toll_rate", 0.05D),
                plugin.getConfig().getDouble("caravans.escort_cut_rate", 0.10D));
        caravan.setLastEvent(event);
        caravan.setArrivedAt(now);
        if (settlement.failed()) {
            refund(caravan.getOwnerId(), settlement.refund());
            caravan.setStatus(Caravan.Status.FAILED);
            caravan.setFailReason("ambush");
            caravan.setDeliveredValue(0.0D);
        } else {
            payout(caravan.getOwnerId(), settlement.merchantPayout());
            payout(caravan.getDestPlotOwner(), settlement.ownerToll());
            payout(caravan.getEscortId(), settlement.escortCut());
            caravan.setStatus(Caravan.Status.ARRIVED);
            caravan.setDeliveredValue(settlement.merchantPayout());
            caravan.setTollPaid(settlement.ownerToll());
            caravan.setEscortPaid(settlement.escortCut());
        }
        store.markDirty();
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.CARAVAN, caravan.getOwnerId(), caravan.getOwnerName(),
                    caravan.routeLabel(),
                    caravan.getStatus().name() + " " + event.name()
                            + " payout=" + caravan.getDeliveredValue());
        }
        announce(caravan);
        sparkle(dest);
        caravan.setNotified(Bukkit.getPlayer(caravan.getOwnerId()) != null);
    }

    private void failAndRefund(Caravan caravan, String reason) {
        refund(caravan.getOwnerId(), caravan.getChargedVault());
        caravan.setStatus(Caravan.Status.FAILED);
        caravan.setFailReason(reason == null ? "failed" : reason);
        caravan.setArrivedAt(System.currentTimeMillis());
        caravan.setLastEvent(CaravanRules.Event.SAFE);
        store.markDirty();
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.CARAVAN, caravan.getOwnerId(), caravan.getOwnerName(),
                    caravan.routeLabel(), "Failed: " + caravan.getFailReason());
        }
        announce(caravan);
        caravan.setNotified(Bukkit.getPlayer(caravan.getOwnerId()) != null);
    }

    private @Nullable String nodeGate(@Nullable Player player, @Nullable TeleportBeacon origin,
                                      @Nullable TeleportBeacon dest, boolean dispatch) {
        if (origin == null || dest == null) return "caravan_route_closed";
        if (!origin.isEnabled() || !dest.isEnabled() || !origin.isLinked()) return "caravan_route_closed";
        if (!dest.getId().equals(origin.getLinkedBeaconId())) return "caravan_route_closed";
        Plot originPlot = plugin.store() == null ? null : plugin.store().getPlotById(origin.getPlotId());
        Plot destPlot = plugin.store() == null ? null : plugin.store().getPlotById(dest.getPlotId());
        if (originPlot == null || destPlot == null) return "caravan_route_closed";
        if (player != null) {
            if (originPlot.isBanned(player.getUniqueId()) || destPlot.isBanned(player.getUniqueId())) {
                return "caravan_banned";
            }
            if (dispatch && plugin.beacons() != null && !plugin.beacons().canDepart(player, origin)) {
                return "caravan_cannot_depart";
            }
        }
        if (destPlot.isLockdownActive()) return "caravan_lockdown";
        if (originPlot.isLockdownActive() && dispatch) return "caravan_lockdown";
        return null;
    }

    private boolean isTradePurpose(TeleportBeacon.Purpose purpose) {
        if (purpose == null) return false;
        List<String> configured = plugin.getConfig().getStringList("caravans.market_purposes");
        if (configured == null || configured.isEmpty()) {
            return purpose == TeleportBeacon.Purpose.SHOP
                    || purpose == TeleportBeacon.Purpose.MARKET
                    || purpose == TeleportBeacon.Purpose.AUCTION;
        }
        for (String raw : configured) {
            if (raw != null && purpose.name().equalsIgnoreCase(raw.trim())) return true;
        }
        return false;
    }

    private double clampCargo(double cargoValue) {
        return Math.min(maxCargo(), Math.max(minCargo(), CaravanRules.clampMoney(cargoValue)));
    }

    private void refund(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0D || plugin.vault() == null || !plugin.vault().isEnabled()) return;
        plugin.vault().deposit(Bukkit.getOfflinePlayer(playerId), amount);
    }

    private void payout(UUID playerId, double amount) {
        refund(playerId, amount);
    }

    private void announce(Caravan caravan) {
        Player owner = Bukkit.getPlayer(caravan.getOwnerId());
        if (owner == null) return;
        plugin.runMain(owner, () -> {
            if (caravan.getStatus() == Caravan.Status.ARRIVED) {
                send(owner, "caravan_arrived",
                        "&aCaravan arrived at &f{DEST}&a via &e{EVENT}&a. You received &f{AMOUNT}&a.",
                        Map.of("DEST", caravan.getDestName(),
                                "EVENT", caravan.getLastEvent().name().toLowerCase(),
                                "AMOUNT", String.format(java.util.Locale.US, "%.2f", caravan.getDeliveredValue())));
                if (plugin.effects() != null) plugin.effects().playClaimSuccess(owner);
            } else if (caravan.getStatus() == Caravan.Status.FAILED) {
                send(owner, "caravan_failed",
                        "&cCaravan failed on &f{ROUTE}&c ({REASON}).",
                        Map.of("ROUTE", caravan.routeLabel(),
                                "REASON", caravan.getFailReason().isBlank() ? "lost" : caravan.getFailReason()));
                if (plugin.effects() != null) plugin.effects().playError(owner);
            }
        });
    }

    private void sparkle(@Nullable TeleportBeacon dest) {
        if (dest == null) return;
        var loc = dest.toStandLocation();
        if (loc == null || loc.getWorld() == null) return;
        Runnable fx = () -> {
            try {
                loc.getWorld().spawnParticle(Particle.HEART, loc, 8, 0.4D, 0.3D, 0.4D, 0.01D);
            } catch (Throwable ignored) {}
        };
        try {
            if (plugin.scheduler() != null) plugin.scheduler().runAt(loc, fx);
            else fx.run();
        } catch (Throwable ignored) {}
    }

    private String gateMessage(String key) {
        return switch (key) {
            case "caravan_banned" -> "&cYou are banned from a plot on this route.";
            case "caravan_cannot_depart" -> "&cYou cannot use the origin pad.";
            case "caravan_lockdown" -> "&cA plot on this route is in lockdown.";
            default -> "&cThat trade route is closed.";
        };
    }

    public void send(Player player, String key, String fallback) {
        send(player, key, fallback, Map.of());
    }

    public void send(Player player, String key, String fallback, Map<String, String> vars) {
        if (player == null) return;
        String msg = fallback;
        try {
            if (plugin.gui() != null) {
                String translated = plugin.gui().tr(player, key, fallback, vars);
                if (translated != null && !translated.isBlank() && !translated.equalsIgnoreCase(key)) {
                    msg = translated;
                }
            }
        } catch (Throwable ignored) {}
        for (var entry : vars.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(GUIManager.color("&8[&bAegisGuard&8]&r " + msg));
    }
}
