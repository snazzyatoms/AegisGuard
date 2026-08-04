package com.aegisguard.territory;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import com.aegisguard.api.events.PlotDeleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TerritoryLifeService implements Listener {

    public record ActivityEntry(long timestamp, UUID plotId, UUID actorId, String type, String details) {}

    public record RentalOffer(double price, double deposit, int termDays) {}

    /** Zone rental deposit snapshot keyed by plotId + zone name (cross-backend). */
    public record ZoneDepositState(double listingDeposit, double heldDeposit) {}

    public static final class RentalContract {
        private final UUID plotId;
        private final UUID ownerId;
        private final UUID renterId;
        private final double rent;
        private final double deposit;
        private final int termDays;
        private final long startedAt;
        private long expiresAt;
        private boolean reminderSent;
        private boolean autoRenew;

        public RentalContract(UUID plotId, UUID ownerId, UUID renterId, double rent, double deposit,
                              int termDays, long startedAt, long expiresAt, boolean reminderSent) {
            this(plotId, ownerId, renterId, rent, deposit, termDays, startedAt, expiresAt, reminderSent, false);
        }

        public RentalContract(UUID plotId, UUID ownerId, UUID renterId, double rent, double deposit,
                              int termDays, long startedAt, long expiresAt, boolean reminderSent, boolean autoRenew) {
            this.plotId = plotId;
            this.ownerId = ownerId;
            this.renterId = renterId;
            this.rent = rent;
            this.deposit = deposit;
            this.termDays = termDays;
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
            this.reminderSent = reminderSent;
            this.autoRenew = autoRenew;
        }

        public UUID plotId() { return plotId; }
        public UUID ownerId() { return ownerId; }
        public UUID renterId() { return renterId; }
        public double rent() { return rent; }
        public double deposit() { return deposit; }
        public int termDays() { return termDays; }
        public long startedAt() { return startedAt; }
        public long expiresAt() { return expiresAt; }
        public boolean reminderSent() { return reminderSent; }
        public boolean autoRenew() { return autoRenew; }
        public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
        public void extendFrom(long base) {
            expiresAt = Math.max(base, expiresAt) + termDays * 86_400_000L;
            reminderSent = false;
        }
        public void markReminderSent() { reminderSent = true; }
    }

    public record PendingSettlement(UUID playerId, double amount, String reason, long createdAt) {}

    public static final class DiscoveryMeta {
        private String category = "other";
        private boolean visible = true;
        private boolean featured;
        private long visits;
        private long lastVisit;

        public String category() { return category; }
        public boolean visible() { return visible; }
        public boolean featured() { return featured; }
        public long visits() { return visits; }
        public long lastVisit() { return lastVisit; }
    }

    private final AegisGuard plugin;
    private final File file;
    private final Object ioLock = new Object();
    private final Map<UUID, RentalOffer> offers = new ConcurrentHashMap<>();
    private final Map<UUID, RentalContract> contracts = new ConcurrentHashMap<>();
    private final List<ActivityEntry> activity = new ArrayList<>();
    private final Map<UUID, List<String>> notices = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> favorites = new ConcurrentHashMap<>();
    private final Map<UUID, DiscoveryMeta> discovery = new ConcurrentHashMap<>();
    private final List<PendingSettlement> settlements = new ArrayList<>();
    private final Map<String, ZoneDepositState> zoneDeposits = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public TerritoryLifeService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "territory-life.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void load() {
        synchronized (ioLock) {
            offers.clear();
            contracts.clear();
            activity.clear();
            notices.clear();
            favorites.clear();
            discovery.clear();
            settlements.clear();
            zoneDeposits.clear();
            if (!file.exists()) return;

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection offerSection = yaml.getConfigurationSection("rental-offers");
            if (offerSection != null) {
                for (String key : offerSection.getKeys(false)) {
                    UUID id = uuid(key);
                    if (id == null) continue;
                    double price = offerSection.getDouble(key + ".price", 0.0D);
                    double deposit = offerSection.getDouble(key + ".deposit", 0.0D);
                    int days = Math.max(1, offerSection.getInt(key + ".term-days", 7));
                    if (Double.isFinite(price) && price > 0.0D && Double.isFinite(deposit) && deposit >= 0.0D) {
                        offers.put(id, new RentalOffer(price, deposit, days));
                    }
                }
            }

            ConfigurationSection contractSection = yaml.getConfigurationSection("contracts");
            if (contractSection != null) {
                for (String key : contractSection.getKeys(false)) {
                    UUID plotId = uuid(key);
                    UUID ownerId = uuid(contractSection.getString(key + ".owner"));
                    UUID renterId = uuid(contractSection.getString(key + ".renter"));
                    if (plotId == null || ownerId == null || renterId == null) continue;
                    contracts.put(plotId, new RentalContract(
                            plotId, ownerId, renterId,
                            contractSection.getDouble(key + ".rent", 0.0D),
                            contractSection.getDouble(key + ".deposit", 0.0D),
                            Math.max(1, contractSection.getInt(key + ".term-days", 7)),
                            contractSection.getLong(key + ".started-at", 0L),
                            contractSection.getLong(key + ".expires-at", 0L),
                            contractSection.getBoolean(key + ".reminder-sent", false),
                            contractSection.getBoolean(key + ".auto-renew", false)
                    ));
                }
            }

            for (Map<?, ?> raw : yaml.getMapList("activity")) {
                UUID plotId = uuid(string(raw.get("plot")));
                if (plotId == null) continue;
                activity.add(new ActivityEntry(number(raw.get("time")), plotId,
                        uuid(string(raw.get("actor"))), string(raw.get("type")), string(raw.get("details"))));
            }

            ConfigurationSection noticeSection = yaml.getConfigurationSection("notices");
            if (noticeSection != null) {
                for (String key : noticeSection.getKeys(false)) {
                    UUID playerId = uuid(key);
                    if (playerId != null) notices.put(playerId, new CopyOnWriteArrayList<>(noticeSection.getStringList(key)));
                }
            }

            ConfigurationSection favoriteSection = yaml.getConfigurationSection("favorites");
            if (favoriteSection != null) {
                for (String key : favoriteSection.getKeys(false)) {
                    UUID playerId = uuid(key);
                    if (playerId == null) continue;
                    Set<UUID> ids = ConcurrentHashMap.newKeySet();
                    for (String value : favoriteSection.getStringList(key)) {
                        UUID plotId = uuid(value);
                        if (plotId != null) ids.add(plotId);
                    }
                    favorites.put(playerId, ids);
                }
            }

            ConfigurationSection discoverySection = yaml.getConfigurationSection("discovery");
            if (discoverySection != null) {
                for (String key : discoverySection.getKeys(false)) {
                    UUID plotId = uuid(key);
                    if (plotId == null) continue;
                    DiscoveryMeta meta = new DiscoveryMeta();
                    meta.category = normalizeCategory(discoverySection.getString(key + ".category", "other"));
                    meta.visible = discoverySection.getBoolean(key + ".visible", true);
                    meta.featured = discoverySection.getBoolean(key + ".featured", false);
                    meta.visits = Math.max(0L, discoverySection.getLong(key + ".visits", 0L));
                    meta.lastVisit = Math.max(0L, discoverySection.getLong(key + ".last-visit", 0L));
                    discovery.put(plotId, meta);
                }
            }

            for (Map<?, ?> raw : yaml.getMapList("pending-settlements")) {
                UUID playerId = uuid(string(raw.get("player")));
                double amount = decimal(raw.get("amount"));
                if (playerId == null || !Double.isFinite(amount) || amount <= 0.0D) continue;
                settlements.add(new PendingSettlement(playerId, amount, string(raw.get("reason")), number(raw.get("time"))));
            }

            ConfigurationSection zoneDepositSection = yaml.getConfigurationSection("zone-deposits");
            if (zoneDepositSection != null) {
                for (String key : zoneDepositSection.getKeys(false)) {
                    if (key == null || key.isBlank()) continue;
                    double listing = zoneDepositSection.getDouble(key + ".listing", 0.0D);
                    double held = zoneDepositSection.getDouble(key + ".held", 0.0D);
                    if ((!Double.isFinite(listing) || listing < 0.0D) && (!Double.isFinite(held) || held < 0.0D)) continue;
                    zoneDeposits.put(key, new ZoneDepositState(
                            Double.isFinite(listing) ? Math.max(0.0D, listing) : 0.0D,
                            Double.isFinite(held) ? Math.max(0.0D, held) : 0.0D));
                }
            }
            trimActivity();
            dirty = false;
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (!dirty && file.exists()) return;
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, RentalOffer> entry : offers.entrySet()) {
                String base = "rental-offers." + entry.getKey();
                yaml.set(base + ".price", entry.getValue().price());
                yaml.set(base + ".deposit", entry.getValue().deposit());
                yaml.set(base + ".term-days", entry.getValue().termDays());
            }
            for (RentalContract contract : contracts.values()) {
                String base = "contracts." + contract.plotId();
                yaml.set(base + ".owner", contract.ownerId().toString());
                yaml.set(base + ".renter", contract.renterId().toString());
                yaml.set(base + ".rent", contract.rent());
                yaml.set(base + ".deposit", contract.deposit());
                yaml.set(base + ".term-days", contract.termDays());
                yaml.set(base + ".started-at", contract.startedAt());
                yaml.set(base + ".expires-at", contract.expiresAt());
                yaml.set(base + ".reminder-sent", contract.reminderSent());
                yaml.set(base + ".auto-renew", contract.autoRenew());
            }
            List<Map<String, Object>> activityRows = new ArrayList<>();
            for (ActivityEntry entry : activity) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("time", entry.timestamp());
                row.put("plot", entry.plotId().toString());
                if (entry.actorId() != null) row.put("actor", entry.actorId().toString());
                row.put("type", entry.type());
                row.put("details", entry.details());
                activityRows.add(row);
            }
            yaml.set("activity", activityRows);
            notices.forEach((id, values) -> yaml.set("notices." + id, new ArrayList<>(values)));
            favorites.forEach((id, values) -> yaml.set("favorites." + id,
                    values.stream().map(UUID::toString).sorted().toList()));
            discovery.forEach((id, meta) -> {
                String base = "discovery." + id;
                yaml.set(base + ".category", meta.category);
                yaml.set(base + ".visible", meta.visible);
                yaml.set(base + ".featured", meta.featured);
                yaml.set(base + ".visits", meta.visits);
                yaml.set(base + ".last-visit", meta.lastVisit);
            });
            List<Map<String, Object>> settlementRows = new ArrayList<>();
            for (PendingSettlement settlement : settlements) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("player", settlement.playerId().toString());
                row.put("amount", settlement.amount());
                row.put("reason", settlement.reason());
                row.put("time", settlement.createdAt());
                settlementRows.add(row);
            }
            yaml.set("pending-settlements", settlementRows);
            for (Map.Entry<String, ZoneDepositState> entry : zoneDeposits.entrySet()) {
                String base = "zone-deposits." + entry.getKey();
                yaml.set(base + ".listing", entry.getValue().listingDeposit());
                yaml.set(base + ".held", entry.getValue().heldDeposit());
            }
            if (atomicSave(yaml.saveToString())) dirty = false;
        }
    }

    private static String zoneDepositKey(UUID plotId, String zoneName) {
        if (plotId == null || zoneName == null || zoneName.isBlank()) return null;
        return plotId + ":" + zoneName;
    }

    public void rememberZoneDeposit(UUID plotId, String zoneName, double listingDeposit, double heldDeposit) {
        String key = zoneDepositKey(plotId, zoneName);
        if (key == null) return;
        zoneDeposits.put(key, new ZoneDepositState(Math.max(0.0D, listingDeposit), Math.max(0.0D, heldDeposit)));
        dirty = true;
    }

    public void clearZoneDeposit(UUID plotId, String zoneName) {
        String key = zoneDepositKey(plotId, zoneName);
        if (key == null) return;
        if (zoneDeposits.remove(key) != null) dirty = true;
    }

    public ZoneDepositState zoneDeposit(UUID plotId, String zoneName) {
        String key = zoneDepositKey(plotId, zoneName);
        return key == null ? null : zoneDeposits.get(key);
    }

    /** Apply remembered deposits onto an in-memory zone (SQL backends). */
    public void applyZoneDeposit(UUID plotId, com.aegisguard.data.Zone zone) {
        if (zone == null) return;
        ZoneDepositState state = zoneDeposit(plotId, zone.getName());
        if (state == null) return;
        if (state.listingDeposit() > 0.0D) zone.setDeposit(state.listingDeposit());
        if (state.heldDeposit() > 0.0D) zone.setHeldDeposit(state.heldDeposit());
    }

    public RentalOffer getOffer(UUID plotId, double fallbackPrice, int fallbackDays) {
        RentalOffer offer = plotId == null ? null : offers.get(plotId);
        return offer != null ? offer : new RentalOffer(fallbackPrice, 0.0D, Math.max(1, fallbackDays));
    }

    public void setOffer(UUID plotId, double price, double deposit, int termDays) {
        if (plotId == null || !Double.isFinite(price) || price <= 0.0D || !Double.isFinite(deposit) || deposit < 0.0D) return;
        offers.put(plotId, new RentalOffer(price, deposit, Math.max(1, termDays)));
        dirty = true;
    }

    public void clearOffer(UUID plotId) {
        if (plotId != null && offers.remove(plotId) != null) dirty = true;
    }

    public RentalContract activateContract(UUID plotId, UUID ownerId, UUID renterId, RentalOffer offer, long expiresAt) {
        if (plotId == null || ownerId == null || renterId == null || offer == null) return null;
        RentalContract contract = new RentalContract(plotId, ownerId, renterId, offer.price(), offer.deposit(),
                offer.termDays(), System.currentTimeMillis(), expiresAt, false);
        contracts.put(plotId, contract);
        dirty = true;
        return contract;
    }

    public RentalContract contract(UUID plotId) { return plotId == null ? null : contracts.get(plotId); }

    public List<RentalContract> contracts() { return List.copyOf(contracts.values()); }

    public void renew(UUID plotId) {
        RentalContract contract = contracts.get(plotId);
        if (contract == null) return;
        contract.extendFrom(System.currentTimeMillis());
        dirty = true;
    }

    /** Mark dirty so the next save persists in-memory mutations (e.g. auto-renew toggle). */
    public void touch() { dirty = true; }

    public List<PendingSettlement> settlementsFor(UUID playerId) {
        if (playerId == null) return List.of();
        synchronized (ioLock) {
            return settlements.stream().filter(s -> playerId.equals(s.playerId())).toList();
        }
    }

    /**
     * Attempt Vault auto-renew for contracts that opted in and are at/past expiry.
     * Returns the number of contracts successfully renewed.
     */
    public int processAutoRenewals() {
        if (!plugin.getConfig().getBoolean("full_plot_renting.auto_renew.enabled", true)) return 0;
        if (plugin.vault() == null) return 0;
        int renewed = 0;
        long now = System.currentTimeMillis();
        for (RentalContract contract : List.copyOf(contracts.values())) {
            if (contract == null || !contract.autoRenew() || contract.expiresAt() > now) continue;
            OfflinePlayer renter = Bukkit.getOfflinePlayer(contract.renterId());
            OfflinePlayer owner = Bukkit.getOfflinePlayer(contract.ownerId());
            if (!plugin.vault().has(renter, contract.rent())
                    || !plugin.vault().charge(renter, contract.rent())) {
                queueNoticeKey(contract.renterId(), "rental_auto_renew_insufficient",
                        "&cAuto-renew failed: insufficient funds. Your rental expires soon.", Map.of());
                continue;
            }
            if (!plugin.vault().deposit(owner, contract.rent())) {
                if (!plugin.vault().deposit(renter, contract.rent())) {
                    addSettlement(contract.renterId(), contract.rent(), "Failed auto-renew refund");
                }
                queueNoticeKey(contract.renterId(), "rental_auto_renew_payment_failed",
                        "&cAuto-renew payment failed. No time was added.", Map.of());
                continue;
            }
            contract.extendFrom(now);
            dirty = true;
            renewed++;
            queueNoticeKey(contract.renterId(), "rental_auto_renewed_renter",
                    "&aRental auto-renewed for &e{DAYS} day(s)&a.",
                    Map.of("DAYS", String.valueOf(contract.termDays())));
            queueNoticeKey(contract.ownerId(), "rental_auto_renewed_owner",
                    "&aA rental contract auto-renewed for &e{DAYS} day(s)&a.",
                    Map.of("DAYS", String.valueOf(contract.termDays())));
        }
        return renewed;
    }

    public RentalContract removeContract(UUID plotId) {
        RentalContract removed = plotId == null ? null : contracts.remove(plotId);
        if (removed != null) dirty = true;
        return removed;
    }

    public void refundDeposit(RentalContract contract, String reason) {
        if (contract == null || contract.deposit() <= 0.0D) return;
        OfflinePlayer renter = Bukkit.getOfflinePlayer(contract.renterId());
        if (plugin.vault() != null && plugin.vault().deposit(renter, contract.deposit())) {
            queueNoticeKey(contract.renterId(), "rental_deposit_refunded",
                    "&aRental deposit refunded: &6{AMOUNT}&a.",
                    Map.of("AMOUNT", String.valueOf(contract.deposit())));
            return;
        }
        addSettlement(contract.renterId(), contract.deposit(), reason);
    }

    public void addSettlement(UUID playerId, double amount, String reason) {
        if (playerId == null || !Double.isFinite(amount) || amount <= 0.0D) return;
        synchronized (ioLock) {
            settlements.add(new PendingSettlement(playerId, amount, safe(reason), System.currentTimeMillis()));
            dirty = true;
        }
        plugin.console().warning("log_settlement_queued",
                "Queued pending economy settlement of {AMOUNT} for {PLAYER}: {REASON}",
                "AMOUNT", String.valueOf(amount),
                "PLAYER", String.valueOf(playerId),
                "REASON", reason == null ? "" : reason);
    }

    /** Admin / scheduled retry of every pending settlement. */
    public int retrySettlements() {
        return retrySettlements(null);
    }

    /**
     * Retry pending Vault settlements.
     * @param playerId when non-null, only that player's settlements are retried
     */
    public int retrySettlementsFor(UUID playerId) {
        return retrySettlements(playerId);
    }

    private int retrySettlements(UUID playerId) {
        if (plugin.vault() == null) return 0;
        int settled = 0;
        synchronized (ioLock) {
            var iterator = settlements.iterator();
            while (iterator.hasNext()) {
                PendingSettlement settlement = iterator.next();
                if (playerId != null && !playerId.equals(settlement.playerId())) continue;
                if (plugin.vault().deposit(Bukkit.getOfflinePlayer(settlement.playerId()), settlement.amount())) {
                    iterator.remove();
                    settled++;
                    queueNotice(settlement.playerId(), settlementDeliveredNotice(settlement));
                    dirty = true;
                }
            }
        }
        return settled;
    }

    public List<PendingSettlement> settlements() {
        synchronized (ioLock) { return List.copyOf(settlements); }
    }

    private String settlementDeliveredNotice(PendingSettlement settlement) {
        String fallback = "&aA pending payment of &6" + settlement.amount() + " &ahas been delivered.";
        Player online = Bukkit.getPlayer(settlement.playerId());
        if (online == null || plugin.codex() == null) return fallback;
        String localized = plugin.codex().tr(online, "settlement_delivered",
                Map.of("AMOUNT", String.valueOf(settlement.amount())));
        if (localized == null || localized.isBlank() || localized.equals("settlement_delivered")) return fallback;
        return localized;
    }

    public void log(UUID plotId, UUID actorId, String type, String details) {
        if (plotId == null || !plugin.getConfig().getBoolean("territory_activity.enabled", true)) return;
        synchronized (ioLock) {
            activity.add(new ActivityEntry(System.currentTimeMillis(), plotId, actorId,
                    safe(type).toUpperCase(Locale.ROOT), safe(details)));
            trimActivity();
            dirty = true;
        }
    }

    /**
     * Records activity with a localizable details template. TYPE remains a program ID;
     * details are encoded for viewer-language resolution at display time.
     */
    public void logKey(UUID plotId, UUID actorId, String type, String detailsKey,
                       String englishFallback, Map<String, String> placeholders) {
        log(plotId, actorId, type, ActivityText.encode(detailsKey, placeholders, englishFallback));
    }

    public List<ActivityEntry> activity(UUID plotId, int limit) {
        synchronized (ioLock) {
            return activity.stream().filter(entry -> plotId == null || plotId.equals(entry.plotId()))
                    .sorted(Comparator.comparingLong(ActivityEntry::timestamp).reversed())
                    .limit(Math.max(1, limit)).toList();
        }
    }

    public void queueNotice(UUID playerId, String message) {
        if (playerId == null || message == null || message.isBlank()) return;
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            plugin.runMain(online, () -> online.sendMessage(color(message)));
            return;
        }
        notices.computeIfAbsent(playerId, ignored -> new CopyOnWriteArrayList<>()).add(message);
        dirty = true;
    }

    public void queueNoticeKey(UUID playerId, String key, String fallback, Map<String, String> placeholders) {
        if (playerId == null) return;
        Map<String, String> ph = placeholders == null ? Map.of() : placeholders;
        Player online = Bukkit.getPlayer(playerId);
        String message = null;
        if (plugin.codex() != null) {
            try {
                if (online != null && online.isOnline()) {
                    message = plugin.codex().tr(online, key, ph);
                } else {
                    message = plugin.codex().tr(key, ph);
                }
            } catch (Throwable ignored) {
                message = null;
            }
        }
        if (message == null || message.isBlank() || message.equals(key)) {
            message = fallback == null ? "" : fallback;
            for (Map.Entry<String, String> entry : ph.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}",
                        entry.getValue() == null ? "" : entry.getValue());
            }
        }
        queueNotice(playerId, message);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        List<String> pending = notices.remove(event.getPlayer().getUniqueId());
        if (pending == null || pending.isEmpty()) return;
        dirty = true;
        for (String message : pending) event.getPlayer().sendMessage(color(message));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlotClaim(PlotClaimEvent event) {
        String world = event.getWorldName() == null ? "" : event.getWorldName();
        logKey(event.getPlotId(), event.getPlayerId(), "PLOT_CLAIMED",
                "activity_detail_plot_claimed",
                "Territory claimed in " + world + ".",
                Map.of("WORLD", world));
    }

    @EventHandler
    public void onPlotDelete(PlotDeleteEvent event) {
        RentalContract contract = removeContract(event.getPlotId());
        refundDeposit(contract, "Deposit after plot deletion");
        clearOffer(event.getPlotId());
        logKey(event.getPlotId(), null, "PLOT_DELETED",
                "activity_detail_plot_deleted", "Territory removed.", Map.of());
    }

    public DiscoveryMeta discovery(UUID plotId) {
        if (plotId == null) return new DiscoveryMeta();
        return discovery.computeIfAbsent(plotId, ignored -> new DiscoveryMeta());
    }

    public void setCategory(UUID plotId, String category) {
        if (plotId == null) return;
        discovery(plotId).category = normalizeCategory(category);
        dirty = true;
    }

    public void setVisible(UUID plotId, boolean visible) {
        if (plotId == null) return;
        discovery(plotId).visible = visible;
        dirty = true;
    }

    public void setFeatured(UUID plotId, boolean featured) {
        if (plotId == null) return;
        discovery(plotId).featured = featured;
        dirty = true;
    }

    public void recordVisit(UUID plotId, UUID visitorId) {
        if (plotId == null) return;
        DiscoveryMeta meta = discovery(plotId);
        meta.visits++;
        meta.lastVisit = System.currentTimeMillis();
        if (plugin.getConfig().getBoolean("territory_activity.log_visits", false)) {
            logKey(plotId, visitorId, "VISIT", "activity_detail_visit",
                    "Plot visited through discovery or travel.", Map.of());
        }
        if (plugin.horizons() != null) {
            plugin.store().getAllPlots().stream()
                    .filter(plot -> plotId.equals(plot.getPlotId()))
                    .findFirst()
                    .ifPresent(plot -> plugin.horizons().recordVisit(plot, visitorId));
        }
        dirty = true;
    }

    public boolean toggleFavorite(UUID playerId, UUID plotId) {
        if (playerId == null || plotId == null) return false;
        Set<UUID> values = favorites.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
        boolean added = values.add(plotId);
        if (!added) values.remove(plotId);
        dirty = true;
        return added;
    }

    public boolean isFavorite(UUID playerId, UUID plotId) {
        Set<UUID> values = favorites.get(playerId);
        return values != null && values.contains(plotId);
    }

    public void markReminderSent(UUID plotId) {
        RentalContract contract = contracts.get(plotId);
        if (contract != null) {
            contract.markReminderSent();
            dirty = true;
        }
    }

    public boolean isDirty() { return dirty; }

    private void trimActivity() {
        int max = Math.max(100, plugin.getConfig().getInt("territory_activity.max_entries", 5000));
        if (activity.size() <= max) return;
        activity.sort(Comparator.comparingLong(ActivityEntry::timestamp));
        activity.subList(0, activity.size() - max).clear();
    }

    private boolean atomicSave(String content) {
        try {
            Files.createDirectories(file.toPath().getParent());
            var temp = file.toPath().resolveSibling(file.getName() + ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            plugin.console().severe("log_territory_life_save_failed",
                    "Failed to save territory-life.yml: {ERROR}",
                    "ERROR", error.getMessage() == null ? "" : error.getMessage());
            return false;
        }
    }

    private static UUID uuid(String value) {
        try { return value == null || value.isBlank() ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
    private static double decimal(Object value) { return value instanceof Number number ? number.doubleValue() : 0.0D; }
    private static String safe(String value) { return value == null || value.isBlank() ? "unknown" : value; }
    private static String normalizeCategory(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.matches("[a-z0-9_-]{1,24}") ? normalized : "other";
    }
    private static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
}
