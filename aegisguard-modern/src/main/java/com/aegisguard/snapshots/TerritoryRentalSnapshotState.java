package com.aegisguard.snapshots;

import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Versioned, exact snapshot payload for the external plot-rental indexes. */
final class TerritoryRentalSnapshotState {
    static final int SCHEMA = 1;

    record State(UUID plotId, TerritoryLifeService.RentalOffer offer,
                 TerritoryLifeService.RentalContract contract) { }

    private TerritoryRentalSnapshotState() { }

    static String capture(TerritoryLifeService service, UUID plotId) {
        if (service == null || plotId == null) return "";
        return encode(plotId, service.offer(plotId), service.contract(plotId));
    }

    static String encode(UUID plotId, TerritoryLifeService.RentalOffer offer,
                         TerritoryLifeService.RentalContract contract) {
        if (plotId == null) throw new IllegalArgumentException("Rental snapshot plot ID is required");
        if (contract != null && !plotId.equals(contract.plotId())) {
            throw new IllegalArgumentException("Rental contract belongs to a different plot");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", SCHEMA);
        yaml.set("plot_id", plotId.toString());
        yaml.set("offer.present", offer != null);
        if (offer != null) {
            yaml.set("offer.price", offer.price());
            yaml.set("offer.deposit", offer.deposit());
            yaml.set("offer.term_days", offer.termDays());
        }
        yaml.set("contract.present", contract != null);
        if (contract != null) {
            yaml.set("contract.owner", contract.ownerId().toString());
            yaml.set("contract.renter", contract.renterId().toString());
            yaml.set("contract.rent", contract.rent());
            yaml.set("contract.deposit", contract.deposit());
            yaml.set("contract.term_days", contract.termDays());
            yaml.set("contract.started_at", contract.startedAt());
            yaml.set("contract.expires_at", contract.expiresAt());
            yaml.set("contract.reminder_sent", contract.reminderSent());
            yaml.set("contract.auto_renew", contract.autoRenew());
        }
        return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    static State decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new StringReader(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid territory rental snapshot state", error);
        }
        int schema = yaml.getInt("schema", 0);
        if (schema < 1 || schema > SCHEMA) {
            throw new IllegalArgumentException("Unsupported territory rental snapshot schema " + schema);
        }
        UUID plotId = requiredUuid(yaml.getString("plot_id"), "plot ID");
        TerritoryLifeService.RentalOffer offer = null;
        if (yaml.getBoolean("offer.present", false)) {
            double price = yaml.getDouble("offer.price");
            double deposit = yaml.getDouble("offer.deposit");
            int termDays = yaml.getInt("offer.term_days");
            if (!Double.isFinite(price) || price <= 0D || !Double.isFinite(deposit)
                    || deposit < 0D || termDays < 1) {
                throw new IllegalArgumentException("Invalid rental offer snapshot values");
            }
            offer = new TerritoryLifeService.RentalOffer(price, deposit, termDays);
        }
        TerritoryLifeService.RentalContract contract = null;
        if (yaml.getBoolean("contract.present", false)) {
            UUID owner = requiredUuid(yaml.getString("contract.owner"), "contract owner");
            UUID renter = requiredUuid(yaml.getString("contract.renter"), "contract renter");
            double rent = yaml.getDouble("contract.rent");
            double deposit = yaml.getDouble("contract.deposit");
            int termDays = yaml.getInt("contract.term_days");
            long startedAt = yaml.getLong("contract.started_at");
            long expiresAt = yaml.getLong("contract.expires_at");
            if (!Double.isFinite(rent) || rent < 0D || !Double.isFinite(deposit)
                    || deposit < 0D || termDays < 1 || startedAt < 0L || expiresAt < 0L) {
                throw new IllegalArgumentException("Invalid rental contract snapshot values");
            }
            contract = new TerritoryLifeService.RentalContract(plotId, owner, renter, rent, deposit,
                    termDays, startedAt, expiresAt,
                    yaml.getBoolean("contract.reminder_sent", false),
                    yaml.getBoolean("contract.auto_renew", false));
        }
        return new State(plotId, offer, contract);
    }

    static void validate(String encoded, UUID expectedPlotId) {
        State state = decode(encoded);
        if (state != null && expectedPlotId != null && !expectedPlotId.equals(state.plotId())) {
            throw new IllegalArgumentException("Territory rental snapshot belongs to a different plot");
        }
    }

    private static UUID requiredUuid(String raw, String field) {
        try {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException();
            return UUID.fromString(raw);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid territory rental snapshot " + field, error);
        }
    }
}
