package com.aegisguard.market;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeStallService {

    public enum IntegrationMode {
        COEXIST,
        DISABLE_ON_BRIDGED_PLOTS,
        DISABLE_GLOBALLY_IF_EXTERNAL_AVAILABLE
    }

    public enum ResultType {
        OK,
        INVALID,
        DISABLED,
        OUT_OF_STOCK,
        NOT_LISTED,
        INSUFFICIENT_FUNDS,
        CURRENCY_UNAVAILABLE,
        STALL_INACTIVE,
        BUSY,
        ERROR
    }

    public record Result(ResultType type, String message) {
        public boolean ok() { return type == ResultType.OK; }
    }

    private static final long BIND_MS = 30_000L;

    private final AegisGuard plugin;
    private final ConcurrentHashMap<String, Boolean> purchaseLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> pendingBindUntil = new ConcurrentHashMap<>();

    public TradeStallService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("market_stalls.enabled", true);
    }

    public IntegrationMode getIntegrationMode() {
        String raw = plugin.getConfig().getString("market_stalls.integration.mode", "COEXIST");
        if (raw == null || raw.isBlank()) return IntegrationMode.COEXIST;
        try {
            return IntegrationMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return IntegrationMode.COEXIST;
        }
    }

    public boolean isEnabledFor(@Nullable Plot plot) {
        if (!isEnabled()) return false;

        if (plugin.marketBridges() == null) return true;

        return switch (getIntegrationMode()) {
            case COEXIST -> true;
            case DISABLE_ON_BRIDGED_PLOTS -> plot == null || !plugin.marketBridges().hasBridgeForPlot(plot, true);
            case DISABLE_GLOBALLY_IF_EXTERNAL_AVAILABLE -> !plugin.marketBridges().hasInstalledBridge();
        };
    }

    public CurrencyType getDefaultCurrency() {
        String raw = plugin.getConfig().getString("market_stalls.currencies.default", "VAULT");
        try {
            CurrencyType parsed = CurrencyType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (parsed == CurrencyType.VAULT || parsed == CurrencyType.CLAIM_BLOCKS) {
                if (isCurrencyAllowed(parsed)) return parsed;
            }
        } catch (Exception ignored) {}
        if (isCurrencyAllowed(CurrencyType.CLAIM_BLOCKS)) return CurrencyType.CLAIM_BLOCKS;
        return CurrencyType.VAULT;
    }

    public boolean isCurrencyAllowed(CurrencyType currency) {
        if (currency == null) return false;
        return switch (currency) {
            case VAULT -> plugin.getConfig().getBoolean("market_stalls.currencies.vault_enabled", true)
                    && plugin.vault() != null && plugin.vault().isEnabled();
            case CLAIM_BLOCKS -> plugin.getConfig().getBoolean("market_stalls.currencies.claim_blocks_enabled", true)
                    && plugin.getClaimBlockManager() != null;
            default -> false;
        };
    }

    public int getDefaultBundleAmount() {
        return Math.max(1, plugin.getConfig().getInt("market_stalls.pricing.default_bundle_size", 1));
    }

    public int getMaxBundleAmount() {
        return Math.max(1, plugin.getConfig().getInt("market_stalls.pricing.max_bundle_size", 64));
    }

    public void ensureListingDefaults(MarketStall stall, int chestSlot) {
        if (stall == null || chestSlot < 0 || chestSlot > 26) return;
        MarketStall.StallListing existing = stall.getListing(chestSlot);
        if (existing != null) return;
        stall.setListing(chestSlot, new MarketStall.StallListing(1.0D, getDefaultCurrency(), getDefaultBundleAmount()));
    }

    public Result purchase(@Nullable Player buyer, @Nullable Plot plot, @Nullable MarketStall stall, int chestSlot) {
        if (buyer == null || plot == null || stall == null) {
            return new Result(ResultType.INVALID, "&cThis TradeStall could not be processed.");
        }
        String lockKey = lockKey(stall, chestSlot);
        if (purchaseLocks.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            return new Result(ResultType.BUSY, "&eThat listing is already being purchased. Try again.");
        }
        try {
            return purchaseLocked(buyer, plot, stall, chestSlot);
        } finally {
            purchaseLocks.remove(lockKey);
        }
    }

    String lockKey(MarketStall stall, int chestSlot) {
        if (stall == null) return "invalid:" + chestSlot;
        return stall.getWorld() + ":" + stall.getStorageKey() + ":" + chestSlot;
    }

    boolean isPurchaseLocked(MarketStall stall, int chestSlot) {
        return purchaseLocks.containsKey(lockKey(stall, chestSlot));
    }

    private Result purchaseLocked(Player buyer, Plot plot, MarketStall stall, int chestSlot) {
        if (!isEnabledFor(plot)) {
            return new Result(ResultType.DISABLED, "&cTradeStalls are disabled on this server.");
        }
        if (!stall.isActive(plot)) {
            return new Result(ResultType.STALL_INACTIVE, "&cThat TradeStall is currently unavailable.");
        }
        if (stall.getOwnerId() != null && stall.getOwnerId().equals(buyer.getUniqueId())) {
            return new Result(ResultType.INVALID, "&eYou already own this TradeStall.");
        }

        MarketStall.StallListing listing = stall.getListing(chestSlot);
        if (listing == null || !listing.isValid()) {
            return new Result(ResultType.NOT_LISTED, "&cThat item is not listed for sale.");
        }
        if (!isCurrencyAllowed(listing.getCurrency())) {
            return new Result(ResultType.CURRENCY_UNAVAILABLE, "&cThat listing currency is unavailable right now.");
        }

        Inventory inventory = resolveInventory(stall);
        if (inventory == null || chestSlot < 0 || chestSlot >= inventory.getSize()) {
            return new Result(ResultType.ERROR, "&cThat TradeStall could not be opened.");
        }

        ItemStack stack = inventory.getItem(chestSlot);
        if (stack == null || stack.getType().isAir()) {
            stall.removeListing(chestSlot);
            plugin.store().savePlot(plot);
            refreshSign(stall);
            return new Result(ResultType.OUT_OF_STOCK, "&cThat listing is out of stock.");
        }

        int bundleAmount = Math.max(1, listing.getBundleAmount());
        if (stack.getAmount() < bundleAmount) {
            return new Result(ResultType.OUT_OF_STOCK, "&cThat listing does not have enough stock right now.");
        }

        if (!chargeBuyer(buyer, listing)) {
            return new Result(ResultType.INSUFFICIENT_FUNDS, "&cYou do not have enough funds for that purchase.");
        }

        if (!paySeller(stall, listing)) {
            refundBuyer(buyer, listing);
            return new Result(ResultType.ERROR, "&cThe seller could not be paid, so the purchase was cancelled.");
        }

        ItemStack sold = stack.clone();
        sold.setAmount(bundleAmount);

        int remaining = stack.getAmount() - bundleAmount;
        if (remaining <= 0) {
            inventory.setItem(chestSlot, null);
        } else {
            stack.setAmount(remaining);
            inventory.setItem(chestSlot, stack);
        }

        stall.clearInvalidListings();
        if (inventory.getItem(chestSlot) == null) {
            stall.removeListing(chestSlot);
        }

        plugin.store().savePlot(plot);
        refreshSign(stall);
        var leftovers = buyer.getInventory().addItem(sold);
        leftovers.values().forEach(drop -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop));

        return new Result(ResultType.OK, "&aPurchase complete.");
    }

    public void startCreateBind(@Nullable Player player) {
        if (player == null) return;
        pendingBindUntil.put(player.getUniqueId(), System.currentTimeMillis() + BIND_MS);
    }

    public boolean hasCreateBind(@Nullable Player player) {
        if (player == null) return false;
        Long until = pendingBindUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            pendingBindUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean consumeCreateBind(@Nullable Player player) {
        if (player == null) return false;
        Long until = pendingBindUntil.remove(player.getUniqueId());
        return until != null && until >= System.currentTimeMillis();
    }

    public void clearCreateBind(@Nullable Player player) {
        if (player == null) return;
        pendingBindUntil.remove(player.getUniqueId());
    }

    public boolean isStallContainer(@Nullable Location location) {
        if (location == null || plugin.store() == null) return false;
        Plot plot = plugin.store().getPlotAt(location);
        return plot != null && plot.getStallAtChest(location) != null;
    }

    public @Nullable Location visitLocation(@Nullable MarketStall stall) {
        if (stall == null) return null;
        Location sign = stall.getSignLocation();
        Location chest = stall.getChestLocation();
        Location base = sign != null ? sign : chest;
        if (base == null) return null;
        return base.clone().add(0.5D, 1.0D, 0.5D);
    }

    public void refreshSign(@Nullable MarketStall stall) {
        if (stall == null) return;
        Location signLoc = stall.getSignLocation();
        if (signLoc == null || signLoc.getWorld() == null) return;
        Block block = signLoc.getBlock();
        BlockState state = block.getState();
        if (!(state instanceof Sign sign)) return;

        String title = stall.getTitle() == null ? "TradeStall" : stall.getTitle();
        sign.setLine(0, ChatColor.GOLD + "[TradeStall]");
        sign.setLine(1, clipSign(title));

        ListingsHint hint = listingsHint(stall);
        if (hint.listedCount() == 1 && hint.priceLine() != null && !hint.priceLine().isBlank()) {
            sign.setLine(2, clipSign(hint.priceLine()));
            sign.setLine(3, clipSign("to browse"));
        } else {
            sign.setLine(2, clipSign("Open chest"));
            sign.setLine(3, clipSign("to browse"));
        }
        sign.update();
    }

    public void applyCreatedSignLines(@Nullable org.bukkit.event.block.SignChangeEvent event, @Nullable MarketStall stall) {
        if (event == null) return;
        String title = stall == null || stall.getTitle() == null ? "TradeStall" : stall.getTitle();
        event.setLine(0, ChatColor.GOLD + "[TradeStall]");
        event.setLine(1, clipSign(title));
        event.setLine(2, clipSign("Open chest"));
        event.setLine(3, clipSign("to browse"));
    }

    private ListingsHint listingsHint(MarketStall stall) {
        Inventory inventory = resolveInventory(stall);
        if (inventory == null) return new ListingsHint(0, null);

        int count = 0;
        String priceLine = null;
        for (int slot = 0; slot < Math.min(27, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            MarketStall.StallListing listing = stall.getListing(slot);
            if (item == null || item.getType().isAir() || listing == null || !listing.isValid()) continue;
            count++;
            if (count == 1) {
                priceLine = plugin.eco() == null
                        ? String.format(Locale.US, "%.0f", listing.getPrice())
                        : plugin.eco().format(listing.getPrice(), listing.getCurrency());
            }
        }
        return new ListingsHint(count, priceLine);
    }

    private String clipSign(String raw) {
        String stripped = ChatColor.stripColor(raw == null ? "" : raw).trim();
        if (stripped.length() <= 15) return stripped;
        return stripped.substring(0, 15);
    }

    private record ListingsHint(int listedCount, String priceLine) {}

    private boolean chargeBuyer(Player buyer, MarketStall.StallListing listing) {
        return switch (listing.getCurrency()) {
            case VAULT -> plugin.eco() != null && plugin.eco().withdrawVaultMoney(buyer, listing.getPrice());
            case CLAIM_BLOCKS -> {
                ClaimBlockManager blocks = plugin.getClaimBlockManager();
                if (blocks == null) yield false;
                yield blocks.spend(buyer.getUniqueId(), (long) Math.ceil(listing.getPrice()));
            }
            default -> false;
        };
    }

    private void refundBuyer(Player buyer, MarketStall.StallListing listing) {
        switch (listing.getCurrency()) {
            case VAULT -> {
                if (plugin.eco() != null) plugin.eco().depositVaultMoney(buyer, listing.getPrice());
            }
            case CLAIM_BLOCKS -> {
                ClaimBlockManager blocks = plugin.getClaimBlockManager();
                if (blocks != null) {
                    blocks.addEarned(buyer.getUniqueId(), (long) Math.ceil(listing.getPrice()));
                }
            }
            default -> {
            }
        }
    }

    private boolean paySeller(MarketStall stall, MarketStall.StallListing listing) {
        if (stall.getOwnerId() == null) return false;
        return switch (listing.getCurrency()) {
            case VAULT -> {
                if (plugin.vault() == null || !plugin.vault().isEnabled()) yield false;
                OfflinePlayer owner = Bukkit.getOfflinePlayer(stall.getOwnerId());
                yield plugin.vault().deposit(owner, listing.getPrice());
            }
            case CLAIM_BLOCKS -> {
                ClaimBlockManager blocks = plugin.getClaimBlockManager();
                if (blocks == null) yield false;
                blocks.addEarned(stall.getOwnerId(), (long) Math.ceil(listing.getPrice()));
                yield true;
            }
            default -> false;
        };
    }

    public @Nullable Inventory resolveInventory(@Nullable MarketStall stall) {
        if (stall == null) return null;
        Block block = resolveContainerBlock(stall);
        if (block == null) return null;

        BlockState state = block.getState();
        if (!(state instanceof InventoryHolder holder)) return null;
        return holder.getInventory();
    }

    public @Nullable Block resolveContainerBlock(@Nullable MarketStall stall) {
        if (stall == null) return null;
        var location = stall.getChestLocation();
        if (location == null || location.getWorld() == null) return null;
        Block block = location.getBlock();
        return switch (block.getType()) {
            case CHEST, TRAPPED_CHEST, BARREL -> block;
            default -> null;
        };
    }
}
