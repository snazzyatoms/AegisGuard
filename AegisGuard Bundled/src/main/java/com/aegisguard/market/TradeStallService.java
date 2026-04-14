package com.aegisguard.market;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

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
        ERROR
    }

    public record Result(ResultType type, String message) {
        public boolean ok() { return type == ResultType.OK; }
    }

    private final AegisGuard plugin;

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
        var leftovers = buyer.getInventory().addItem(sold);
        leftovers.values().forEach(drop -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop));

        return new Result(ResultType.OK, "&aPurchase complete.");
    }

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
