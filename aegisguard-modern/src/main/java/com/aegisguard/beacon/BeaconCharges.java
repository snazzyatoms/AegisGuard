package com.aegisguard.beacon;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Server-wide charging policy for Teleport Beacons.
 * {@code off} = never charge, {@code owner_choice} = each pad sets its own fee,
 * {@code always} = every trip uses the configured server fee.
 */
public final class BeaconCharges {

    public enum Mode {
        OFF,
        OWNER_CHOICE,
        ALWAYS;

        public static Mode parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) return OWNER_CHOICE;
            String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            return switch (key) {
                case "off", "none", "disabled", "free", "no_charge", "no_charges", "never" -> OFF;
                case "always", "all", "required", "forced", "server", "full", "full_charge" -> ALWAYS;
                default -> OWNER_CHOICE;
            };
        }
    }

    public record TripCost(double vault, long claimBlocks, @Nullable UUID payOwner) {
        public static final TripCost FREE = new TripCost(0.0D, 0L, null);

        public boolean isFree() {
            return vault <= 0.0D && claimBlocks <= 0L;
        }
    }

    private final AegisGuard plugin;

    public BeaconCharges(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public Mode mode() {
        return Mode.parse(plugin.getConfig().getString("teleport_beacons.charges.mode", "owner_choice"));
    }

    public boolean allowVault() {
        return plugin.getConfig().getBoolean("teleport_beacons.charges.allow_vault", true);
    }

    public boolean allowClaimBlocks() {
        return plugin.getConfig().getBoolean("teleport_beacons.charges.allow_claim_blocks", true);
    }

    public boolean payPlotOwner() {
        return plugin.getConfig().getBoolean("teleport_beacons.charges.pay_plot_owner", true);
    }

    public boolean exemptManagers() {
        return plugin.getConfig().getBoolean("teleport_beacons.charges.exempt_managers", true);
    }

    public boolean exemptAdmins() {
        return plugin.getConfig().getBoolean("teleport_beacons.charges.exempt_admins", true);
    }

    public double maxVault() {
        return Math.max(0.0D, plugin.getConfig().getDouble("teleport_beacons.charges.max_vault_cost", 10000.0D));
    }

    public long maxClaimBlocks() {
        return Math.max(0L, plugin.getConfig().getLong("teleport_beacons.charges.max_claim_block_cost", 500L));
    }

    public boolean canEditVaultFees() {
        return mode() == Mode.OWNER_CHOICE && allowVault();
    }

    public boolean canEditClaimBlockFees() {
        return mode() == Mode.OWNER_CHOICE && allowClaimBlocks();
    }

    public double clampVault(double amount) {
        if (!allowVault() || mode() == Mode.OFF) return 0.0D;
        return Math.min(Math.max(0.0D, amount), maxVault());
    }

    public long clampClaimBlocks(long amount) {
        if (!allowClaimBlocks() || mode() == Mode.OFF) return 0L;
        return Math.min(Math.max(0L, amount), maxClaimBlocks());
    }

    /** What this pad advertises (ignores traveler exemptions). */
    public TripCost listedFee(TeleportBeacon beacon) {
        return resolve(null, beacon, false);
    }

    public TripCost resolve(@Nullable Player traveler, @Nullable TeleportBeacon billed) {
        return resolve(traveler, billed, true);
    }

    private TripCost resolve(@Nullable Player traveler, @Nullable TeleportBeacon billed, boolean applyExempt) {
        Mode mode = mode();
        if (mode == Mode.OFF || billed == null) return TripCost.FREE;

        Plot plot = plugin.store() == null ? null : plugin.store().getPlotById(billed.getPlotId());
        if (applyExempt && traveler != null && isExempt(traveler, billed, plot)) return TripCost.FREE;

        double vault;
        long blocks;
        if (mode == Mode.ALWAYS) {
            vault = plugin.getConfig().getDouble("teleport_beacons.charges.always_vault_cost", 0.0D);
            blocks = plugin.getConfig().getLong("teleport_beacons.charges.always_claim_block_cost", 0L);
        } else {
            vault = billed.getVaultCost();
            blocks = billed.getClaimBlockCost();
        }
        vault = clampVault(vault);
        blocks = clampClaimBlocks(blocks);
        if (!vaultAvailable()) vault = 0.0D;
        if (!claimBlocksAvailable()) blocks = 0L;

        UUID owner = null;
        if (payPlotOwner() && plot != null && plot.getOwner() != null) {
            owner = plot.getOwner();
            if (traveler != null && owner.equals(traveler.getUniqueId())) owner = null;
        }
        if (vault <= 0.0D && blocks <= 0L) return TripCost.FREE;
        return new TripCost(vault, blocks, owner);
    }

    public boolean isExempt(Player player, TeleportBeacon beacon, @Nullable Plot plot) {
        if (player == null) return false;
        if (exemptAdmins() && plugin.isAdmin(player)) return true;
        if (!exemptManagers()) return false;
        Plot resolved = plot;
        if (resolved == null && beacon != null && plugin.store() != null) {
            resolved = plugin.store().getPlotById(beacon.getPlotId());
        }
        return resolved != null && resolved.canManage(player, plugin);
    }

    public boolean charge(Player player, TripCost cost) {
        if (cost == null || cost.isFree()) return true;
        boolean tookVault = false;
        if (cost.vault() > 0.0D) {
            if (!vaultAvailable() || !plugin.vault().charge(player, cost.vault())) return false;
            tookVault = true;
        }
        if (cost.claimBlocks() > 0L) {
            if (plugin.getClaimBlockManager() == null
                    || !plugin.getClaimBlockManager().spend(player.getUniqueId(), cost.claimBlocks())) {
                if (tookVault && plugin.vault() != null) plugin.vault().deposit(player, cost.vault());
                return false;
            }
        }
        return true;
    }

    public void refund(Player player, TripCost cost) {
        if (player == null || cost == null || cost.isFree()) return;
        if (cost.vault() > 0.0D && plugin.vault() != null) {
            plugin.vault().deposit(player, cost.vault());
        }
        if (cost.claimBlocks() > 0L && plugin.getClaimBlockManager() != null) {
            plugin.getClaimBlockManager().refund(player.getUniqueId(), cost.claimBlocks());
        }
    }

    public void payoutOwner(TripCost cost) {
        if (cost == null || cost.payOwner() == null || cost.isFree()) return;
        UUID ownerId = cost.payOwner();
        if (cost.vault() > 0.0D && vaultAvailable()) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
            plugin.vault().deposit(owner, cost.vault());
        }
        if (cost.claimBlocks() > 0L && plugin.getClaimBlockManager() != null) {
            plugin.getClaimBlockManager().addBonus(ownerId, cost.claimBlocks());
        }
    }

    public String vaultLabel(double amount) {
        if (amount <= 0.0D) return "0";
        if (amount == Math.rint(amount)) return String.valueOf((long) amount);
        return String.format(Locale.US, "%.2f", amount);
    }

    private boolean vaultAvailable() {
        return plugin.vault() != null && plugin.vault().isEnabled();
    }

    private boolean claimBlocksAvailable() {
        return plugin.getClaimBlockManager() != null
                && plugin.getConfig().getBoolean("claim_blocks.enabled", true);
    }
}
