package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.snapshots.ClaimSnapshot;
import com.aegisguard.territory.TerritoryLifeService;
import com.aegisguard.util.TerritoryGeometry;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DoctorRepairService {

    public enum Severity { INFO, WARNING, CRITICAL }

    public record Issue(Severity severity, String code, UUID plotId, boolean repairable, String message) {}

    public record ScanResult(List<Issue> issues, int plotsScanned) {
        public long repairableCount() { return issues.stream().filter(Issue::repairable).count(); }
        public long criticalCount() { return issues.stream().filter(issue -> issue.severity() == Severity.CRITICAL).count(); }
    }

    public record RepairResult(ScanResult before, ScanResult after, int repairedPlots, int refundedContracts) {}

    private DoctorRepairService() {}

    public static ScanResult scan(AegisGuard plugin) {
        List<Plot> plots = new ArrayList<>(safePlots(plugin));
        List<Issue> issues = new ArrayList<>();
        Map<UUID, Integer> idCounts = new HashMap<>();
        Map<String, List<Plot>> byWorld = new HashMap<>();

        for (Plot plot : plots) {
            if (plot == null) continue;
            idCounts.merge(plot.getPlotId(), 1, Integer::sum);
            byWorld.computeIfAbsent(plot.getWorld() == null ? "" : plot.getWorld().toLowerCase(), ignored -> new ArrayList<>()).add(plot);

            if (plot.getOwner() == null) {
                issues.add(new Issue(Severity.CRITICAL, "MISSING_OWNER", plot.getPlotId(), false,
                        "Plot has no owner and requires manual recovery."));
            }
            if (plot.getWorld() == null || Bukkit.getWorld(plot.getWorld()) == null) {
                issues.add(new Issue(Severity.WARNING, "MISSING_WORLD", plot.getPlotId(), false,
                        "Plot references unavailable world '" + plot.getWorld() + "'."));
            }

            int marketStates = (plot.isForSale() ? 1 : 0) + (plot.isForRent() ? 1 : 0) + (plot.isForAuction() ? 1 : 0);
            if (marketStates > 1) {
                issues.add(new Issue(Severity.CRITICAL, "MARKET_CONFLICT", plot.getPlotId(), true,
                        "Plot has multiple sale, rental, or auction states."));
            }
            if (plot.isForSale() && (!Double.isFinite(plot.getSalePrice()) || plot.getSalePrice() <= 0.0D)) {
                issues.add(new Issue(Severity.WARNING, "INVALID_SALE", plot.getPlotId(), true,
                        "Sale listing has an invalid price."));
            }
            if (plot.isForRent() && (!Double.isFinite(plot.getRentPrice()) || plot.getRentPrice() <= 0.0D)) {
                issues.add(new Issue(Severity.WARNING, "INVALID_RENT", plot.getPlotId(), true,
                        "Rental listing has an invalid price."));
            }
            if (plot.getCurrentRenter() != null && !plot.hasActiveRental()) {
                issues.add(new Issue(Severity.WARNING, "STALE_RENTER", plot.getPlotId(), true,
                        "Expired renter data is still attached to the plot."));
            }

            TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
            if (plot.hasActiveRental() && (contract == null
                    || !Objects.equals(plot.getCurrentRenter(), contract.renterId())
                    || !Objects.equals(plot.getOwner(), contract.ownerId()))) {
                issues.add(new Issue(Severity.CRITICAL, "CONTRACT_MISMATCH", plot.getPlotId(), true,
                        "Active plot rental and durable contract data do not match."));
            }
            if (!plot.hasActiveRental() && contract != null && contract.expiresAt() > System.currentTimeMillis()) {
                issues.add(new Issue(Severity.CRITICAL, "PLOT_RENTAL_MISSING", plot.getPlotId(), true,
                        "Durable contract exists but the plot has no matching active renter."));
            }
        }

        idCounts.forEach((id, count) -> {
            if (count > 1) issues.add(new Issue(Severity.CRITICAL, "DUPLICATE_ID", id, false,
                    count + " cached plots share the same ID; restore from snapshot or resolve manually."));
        });

        Set<UUID> knownIds = new HashSet<>(idCounts.keySet());
        for (TerritoryLifeService.RentalContract contract : plugin.territoryLife().contracts()) {
            if (!knownIds.contains(contract.plotId())) {
                issues.add(new Issue(Severity.CRITICAL, "ORPHAN_CONTRACT", contract.plotId(), true,
                        "Rental contract references a deleted plot."));
            }
        }

        for (List<Plot> worldPlots : byWorld.values()) findOverlaps(worldPlots, issues);
        for (TerritoryLifeService.PendingSettlement settlement : plugin.territoryLife().settlements()) {
            issues.add(new Issue(Severity.WARNING, "PENDING_SETTLEMENT", null, true,
                    "Payment of " + settlement.amount() + " is pending for " + settlement.playerId() + "."));
        }

        issues.sort(Comparator.comparing(Issue::severity).reversed().thenComparing(Issue::code));
        return new ScanResult(List.copyOf(issues), plots.size());
    }

    public static RepairResult repair(AegisGuard plugin) {
        ScanResult before = scan(plugin);
        Set<UUID> affected = new HashSet<>();
        Set<UUID> snapshotted = new HashSet<>();
        int refundedContracts = 0;

        Map<UUID, Plot> plots = new HashMap<>();
        for (Plot plot : safePlots(plugin)) if (plot != null) plots.putIfAbsent(plot.getPlotId(), plot);

        for (Issue issue : before.issues()) {
            if (!issue.repairable()) continue;
            Plot plot = issue.plotId() == null ? null : plots.get(issue.plotId());
            if (plot != null && snapshotted.add(plot.getPlotId()) && plugin.getSnapshotManager() != null) {
                plugin.getSnapshotManager().createSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL,
                        "Before AegisGuard Doctor automatic repair", null);
            }
            switch (issue.code()) {
                case "MARKET_CONFLICT" -> {
                    if (plot == null) break;
                    if (plot.hasActiveRental()) {
                        plot.setForSale(false, 0.0D);
                        plot.setForAuction(false);
                    } else if (plot.isForSale()) {
                        plot.setForRent(false, 0.0D);
                        plugin.territoryLife().clearOffer(plot.getPlotId());
                        plot.setForAuction(false);
                    } else if (plot.isForRent()) {
                        plot.setForAuction(false);
                    }
                    affected.add(plot.getPlotId());
                }
                case "INVALID_SALE" -> {
                    if (plot != null) { plot.setForSale(false, 0.0D); affected.add(plot.getPlotId()); }
                }
                case "INVALID_RENT" -> {
                    if (plot != null) {
                        plot.setForRent(false, 0.0D);
                        plugin.territoryLife().clearOffer(plot.getPlotId());
                        affected.add(plot.getPlotId());
                    }
                }
                case "STALE_RENTER" -> {
                    if (plot != null) {
                        TerritoryLifeService.RentalContract stale = plugin.territoryLife().removeContract(plot.getPlotId());
                        plugin.territoryLife().refundDeposit(stale, "Doctor repair refund for stale rental");
                        plot.clearRenter();
                        affected.add(plot.getPlotId());
                        if (stale != null) refundedContracts++;
                    }
                }
                case "CONTRACT_MISMATCH" -> {
                    if (plot == null || !plot.hasActiveRental()) break;
                    TerritoryLifeService.RentalContract previous = plugin.territoryLife().removeContract(plot.getPlotId());
                    if (previous != null && !previous.renterId().equals(plot.getCurrentRenter())) {
                        plugin.territoryLife().refundDeposit(previous, "Doctor repair refund for mismatched rental");
                        refundedContracts++;
                    }
                    TerritoryLifeService.RentalOffer offer = plugin.territoryLife().getOffer(plot.getPlotId(), plot.getRentPrice(),
                            Math.max(1, plugin.getConfig().getInt("full_plot_renting.duration_days", 7)));
                    plugin.territoryLife().activateContract(plot.getPlotId(), plot.getOwner(), plot.getCurrentRenter(), offer, plot.getRentEndTime());
                    affected.add(plot.getPlotId());
                }
                case "PLOT_RENTAL_MISSING" -> {
                    if (plot == null) break;
                    TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
                    if (contract != null) {
                        plot.setRenter(contract.renterId(), contract.expiresAt());
                        affected.add(plot.getPlotId());
                    }
                }
                case "ORPHAN_CONTRACT" -> {
                    TerritoryLifeService.RentalContract orphan = plugin.territoryLife().removeContract(issue.plotId());
                    plugin.territoryLife().refundDeposit(orphan, "Doctor repair refund for deleted rental plot");
                    if (orphan != null) refundedContracts++;
                }
                case "PENDING_SETTLEMENT" -> plugin.territoryLife().retrySettlements();
                default -> { }
            }
        }

        for (UUID plotId : affected) {
            Plot plot = plots.get(plotId);
            if (plot != null) {
                plugin.store().savePlotSync(plot);
                plugin.territoryLife().logKey(plotId, null, "DOCTOR_REPAIR",
                        "activity_detail_doctor_repair", "Doctor repaired inconsistent territory state.", java.util.Map.of());
            }
        }
        plugin.territoryLife().save();
        return new RepairResult(before, scan(plugin), affected.size(), refundedContracts);
    }

    private static void findOverlaps(List<Plot> plots, List<Issue> issues) {
        plots.sort(Comparator.comparingInt(Plot::getX1));
        for (int i = 0; i < plots.size(); i++) {
            Plot first = plots.get(i);
            for (int j = i + 1; j < plots.size(); j++) {
                Plot second = plots.get(j);
                if (second.getX1() > first.getX2()) break;
                if (first.getPlotId().equals(second.getPlotId())) continue;
                if (TerritoryGeometry.overlaps(first.getX1(), first.getZ1(), first.getX2(), first.getZ2(),
                        second.getX1(), second.getZ1(), second.getX2(), second.getZ2())) {
                    issues.add(new Issue(Severity.CRITICAL, "OVERLAP", first.getPlotId(), false,
                            "Overlaps plot " + second.getPlotId() + "; no automatic geometry repair was attempted."));
                }
            }
        }
    }

    private static Collection<Plot> safePlots(AegisGuard plugin) {
        return plugin.store() == null ? List.of() : plugin.store().getAllPlots();
    }
}
