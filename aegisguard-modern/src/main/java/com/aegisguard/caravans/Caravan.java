package com.aegisguard.caravans;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * One dispatched shipment traveling a public beacon hop over real time.
 */
public final class Caravan {

    public enum Status {
        IN_TRANSIT, ARRIVED, FAILED, CANCELLED;

        public static Status parse(@Nullable String raw) {
            if (raw == null || raw.isBlank()) return IN_TRANSIT;
            try {
                return Status.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return IN_TRANSIT;
            }
        }

        public boolean terminal() {
            return this == ARRIVED || this == FAILED || this == CANCELLED;
        }
    }

    private final UUID id;
    private UUID ownerId;
    private String ownerName = "Unknown";
    private UUID originBeaconId;
    private UUID destBeaconId;
    private String originName = "Origin";
    private String destName = "Destination";
    private UUID destPlotOwner;
    private UUID escortId;
    private double cargoValue;
    private double fee;
    private double insurancePremium;
    private double chargedVault;
    private boolean insured;
    private Status status = Status.IN_TRANSIT;
    private CaravanRules.Event lastEvent = CaravanRules.Event.SAFE;
    private long dispatchedAt = System.currentTimeMillis();
    private long etaAt;
    private long arrivedAt;
    private double deliveredValue;
    private double tollPaid;
    private double escortPaid;
    private String failReason = "";
    private boolean notified;

    public Caravan(UUID id) {
        this.id = id == null ? UUID.randomUUID() : id;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName; }
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName.trim();
    }
    public UUID getOriginBeaconId() { return originBeaconId; }
    public void setOriginBeaconId(UUID originBeaconId) { this.originBeaconId = originBeaconId; }
    public UUID getDestBeaconId() { return destBeaconId; }
    public void setDestBeaconId(UUID destBeaconId) { this.destBeaconId = destBeaconId; }
    public String getOriginName() { return originName == null || originName.isBlank() ? "Origin" : originName; }
    public void setOriginName(String originName) {
        this.originName = originName == null || originName.isBlank() ? "Origin" : originName.trim();
    }
    public String getDestName() { return destName == null || destName.isBlank() ? "Destination" : destName; }
    public void setDestName(String destName) {
        this.destName = destName == null || destName.isBlank() ? "Destination" : destName.trim();
    }
    public UUID getDestPlotOwner() { return destPlotOwner; }
    public void setDestPlotOwner(UUID destPlotOwner) { this.destPlotOwner = destPlotOwner; }
    public UUID getEscortId() { return escortId; }
    public void setEscortId(UUID escortId) { this.escortId = escortId; }
    public double getCargoValue() { return cargoValue; }
    public void setCargoValue(double cargoValue) { this.cargoValue = CaravanRules.clampMoney(cargoValue); }
    public double getFee() { return fee; }
    public void setFee(double fee) { this.fee = CaravanRules.clampMoney(fee); }
    public double getInsurancePremium() { return insurancePremium; }
    public void setInsurancePremium(double insurancePremium) {
        this.insurancePremium = CaravanRules.clampMoney(insurancePremium);
    }
    public double getChargedVault() { return chargedVault; }
    public void setChargedVault(double chargedVault) { this.chargedVault = CaravanRules.clampMoney(chargedVault); }
    public boolean isInsured() { return insured; }
    public void setInsured(boolean insured) { this.insured = insured; }
    public Status getStatus() { return status == null ? Status.IN_TRANSIT : status; }
    public void setStatus(Status status) { this.status = status == null ? Status.IN_TRANSIT : status; }
    public CaravanRules.Event getLastEvent() { return lastEvent == null ? CaravanRules.Event.SAFE : lastEvent; }
    public void setLastEvent(CaravanRules.Event lastEvent) {
        this.lastEvent = lastEvent == null ? CaravanRules.Event.SAFE : lastEvent;
    }
    public long getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(long dispatchedAt) { this.dispatchedAt = Math.max(0L, dispatchedAt); }
    public long getEtaAt() { return etaAt; }
    public void setEtaAt(long etaAt) { this.etaAt = Math.max(0L, etaAt); }
    public long getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(long arrivedAt) { this.arrivedAt = Math.max(0L, arrivedAt); }
    public double getDeliveredValue() { return deliveredValue; }
    public void setDeliveredValue(double deliveredValue) {
        this.deliveredValue = CaravanRules.clampMoney(deliveredValue);
    }
    public double getTollPaid() { return tollPaid; }
    public void setTollPaid(double tollPaid) { this.tollPaid = CaravanRules.clampMoney(tollPaid); }
    public double getEscortPaid() { return escortPaid; }
    public void setEscortPaid(double escortPaid) { this.escortPaid = CaravanRules.clampMoney(escortPaid); }
    public String getFailReason() { return failReason == null ? "" : failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason == null ? "" : failReason; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }

    public boolean inFlight() { return getStatus() == Status.IN_TRANSIT; }

    public String routeLabel() {
        return getOriginName() + " → " + getDestName();
    }
}
