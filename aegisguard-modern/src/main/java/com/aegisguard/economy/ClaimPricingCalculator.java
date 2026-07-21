package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * ClaimPricingCalculator - v1.2.5+
 * 
 * Implements "Fairer Initial Pricing" model:
 * - Creating a massive initial claim no longer bypasses expansion costs.
 * - Base price covers 1 chunk (16x16 = 256 blocks).
 * - Every additional block beyond the first chunk is charged at the expansion rate.
 * 
 * Formula:
 *   totalCost = baseCost + max(0, area - baseArea) * expansionRatePerBlock
 * 
 * Example (with defaults):
 *   - Base cost: $100 (covers 256 blocks / 1 chunk)
 *   - Expansion rate: $10 per block
 *   - Claim 1000 blocks: $100 + (1000 - 256) * $10 = $100 + $7,440 = $7,540
 *   - Claim 256 blocks: $100 + 0 = $100
 *   - Claim 100 blocks: $100 (still pays base, even if under threshold)
 */
public class ClaimPricingCalculator {

    // Default chunk size (Minecraft standard)
    public static final int CHUNK_SIZE = 16;
    public static final long DEFAULT_BASE_AREA = CHUNK_SIZE * CHUNK_SIZE; // 256 blocks

    private final AegisGuard plugin;

    // Cached config values
    private boolean fairPricingEnabled;
    private double baseCost;
    private long baseArea;
    private double expansionRatePerBlock;
    private boolean scaleBaseWithArea;
    private double minimumCost;

    public ClaimPricingCalculator(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reload pricing configuration from config.yml.
     * Call this after /agadmin reload.
     */
    public void reload() {
        FileConfiguration cfg = plugin.cfg().raw();

        // Fair pricing toggle (new system)
        this.fairPricingEnabled = cfg.getBoolean("economy.fair_pricing.enabled", true);

        // Base cost (covers base_area blocks)
        this.baseCost = cfg.getDouble("economy.fair_pricing.base_cost", 
                cfg.getDouble("economy.claim_cost", 100.0));

        // Base area covered by base cost (default: 1 chunk = 256 blocks)
        this.baseArea = cfg.getLong("economy.fair_pricing.base_area", DEFAULT_BASE_AREA);
        if (this.baseArea <= 0) this.baseArea = DEFAULT_BASE_AREA;

        // Cost per additional block beyond base area
        this.expansionRatePerBlock = cfg.getDouble("economy.fair_pricing.expansion_rate_per_block",
                cfg.getDouble("economy.expansion_cost_per_block",
                        cfg.getDouble("economy.resize_cost_per_block", 10.0)));

        // If true, claims smaller than base_area still pay proportional base cost
        // If false, any claim pays at least baseCost (default: false = always pay base)
        this.scaleBaseWithArea = cfg.getBoolean("economy.fair_pricing.scale_base_with_area", false);

        // Minimum cost floor (prevents $0 claims if scale_base_with_area is true)
        this.minimumCost = cfg.getDouble("economy.fair_pricing.minimum_cost", 10.0);
    }

    // -------------------------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------------------------

    /**
     * Check if fair pricing is enabled.
     */
    public boolean isEnabled() {
        return fairPricingEnabled;
    }

    /**
     * Calculate the total cost for an initial claim of the given area.
     * 
     * @param area Total blocks in the claim (width * length)
     * @return The monetary cost for this claim
     */
    public double calculateClaimCost(long area) {
        if (!fairPricingEnabled) {
            // Legacy flat cost mode
            return plugin.cfg().raw().getDouble("economy.claim_cost", 100.0);
        }

        if (area <= 0) return 0;

        double cost;

        if (area <= baseArea) {
            // Claim is at or under base threshold
            if (scaleBaseWithArea) {
                // Proportional: (area / baseArea) * baseCost
                cost = ((double) area / baseArea) * baseCost;
            } else {
                // Fixed: always pay full base cost
                cost = baseCost;
            }
        } else {
            // Claim exceeds base threshold
            // baseCost + (extraBlocks * expansionRate)
            long extraBlocks = area - baseArea;
            cost = baseCost + (extraBlocks * expansionRatePerBlock);
        }

        // Apply minimum floor
        return Math.max(cost, minimumCost);
    }

    /**
     * Calculate expansion cost when resizing an existing claim.
     * This only charges for the NEW blocks being added.
     * 
     * @param additionalBlocks Number of new blocks being added
     * @return The monetary cost for expanding
     */
    public double calculateExpansionCost(long additionalBlocks) {
        if (additionalBlocks <= 0) return 0;
        return additionalBlocks * expansionRatePerBlock;
    }

    /**
     * Calculate refund when shrinking or unclaiming.
     * 
     * @param removedBlocks Number of blocks being removed
     * @param refundPercent Percentage to refund (0-100)
     * @return The refund amount
     */
    public double calculateRefund(long removedBlocks, double refundPercent) {
        if (removedBlocks <= 0 || refundPercent <= 0) return 0;

        // Refund based on expansion rate (not base cost)
        double fullValue = removedBlocks * expansionRatePerBlock;
        return fullValue * (refundPercent / 100.0);
    }

    /**
     * Calculate the claim block cost (if using claim blocks instead of Vault money).
     * With fair pricing, large claims cost proportionally more claim blocks.
     * 
     * @param area Total blocks in the claim
     * @return The claim block cost
     */
    public long calculateClaimBlockCost(long area) {
        if (!fairPricingEnabled) {
            // Legacy: 1:1 area to claim blocks
            return area;
        }

        // With fair pricing enabled, we can optionally add a multiplier
        // for claims exceeding base area (configurable)
        double multiplier = plugin.cfg().raw().getDouble(
                "economy.fair_pricing.claimblock_multiplier", 1.0);

        if (multiplier <= 1.0 || area <= baseArea) {
            // No multiplier or under threshold
            return area;
        }

        // Apply multiplier only to excess blocks
        long extraBlocks = area - baseArea;
        long extraCost = (long) Math.ceil(extraBlocks * multiplier);
        return baseArea + extraCost;
    }

    // -------------------------------------------------------------------------
    // BREAKDOWN (for displaying costs to players)
    // -------------------------------------------------------------------------

    /**
     * Get a detailed cost breakdown for displaying to players.
     */
    public CostBreakdown getBreakdown(long area) {
        if (!fairPricingEnabled) {
            double flatCost = plugin.cfg().raw().getDouble("economy.claim_cost", 100.0);
            return new CostBreakdown(area, 0, flatCost, 0, flatCost, false);
        }

        long baseBlocks = Math.min(area, baseArea);
        long extraBlocks = Math.max(0, area - baseArea);

        double basePortionCost;
        if (scaleBaseWithArea && area < baseArea) {
            basePortionCost = ((double) area / baseArea) * baseCost;
        } else {
            basePortionCost = baseCost;
        }

        double extraCost = extraBlocks * expansionRatePerBlock;
        double totalCost = Math.max(basePortionCost + extraCost, minimumCost);

        return new CostBreakdown(baseBlocks, extraBlocks, basePortionCost, extraCost, totalCost, true);
    }

    /**
     * Data class for cost breakdown display.
     */
    public record CostBreakdown(
            long baseBlocks,
            long extraBlocks,
            double baseCost,
            double extraCost,
            double totalCost,
            boolean fairPricingActive
    ) {
        public long totalBlocks() {
            return baseBlocks + extraBlocks;
        }

        public boolean hasExtraCost() {
            return extraBlocks > 0 && extraCost > 0;
        }
    }

    // -------------------------------------------------------------------------
    // GETTERS (for GUIs and other systems)
    // -------------------------------------------------------------------------

    public double getBaseCost() {
        return baseCost;
    }

    public long getBaseArea() {
        return baseArea;
    }

    public double getExpansionRatePerBlock() {
        return expansionRatePerBlock;
    }

    public double getMinimumCost() {
        return minimumCost;
    }

    public int getBaseAreaInChunks() {
        return (int) Math.ceil((double) baseArea / (CHUNK_SIZE * CHUNK_SIZE));
    }
}
