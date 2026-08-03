package com.aegisguard.travel;

import org.bukkit.Location;

import java.util.concurrent.CompletableFuture;

/**
 * Outcome of a Safe Travel request. Callers that already show their own success
 * message can ignore {@link #messageKey()} when {@link #isSuccess()} is true.
 */
public final class SafeTravelResult {

    public enum Status {
        SUCCESS,
        CANCELLED,
        UNSAFE_DESTINATION,
        COOLDOWN,
        IN_COMBAT,
        CONFIRMATION_REQUIRED,
        DISABLED,
        INVALID
    }

    private final Status status;
    private final String messageKey;
    private final String fallbackMessage;
    private final Location resolvedLocation;
    private final long remainingMillis;
    private final CompletableFuture<Boolean> teleportFuture;

    private SafeTravelResult(
            Status status,
            String messageKey,
            String fallbackMessage,
            Location resolvedLocation,
            long remainingMillis,
            CompletableFuture<Boolean> teleportFuture
    ) {
        this.status = status;
        this.messageKey = messageKey;
        this.fallbackMessage = fallbackMessage;
        this.resolvedLocation = resolvedLocation;
        this.remainingMillis = Math.max(0L, remainingMillis);
        this.teleportFuture = teleportFuture == null
                ? CompletableFuture.completedFuture(status == Status.SUCCESS)
                : teleportFuture;
    }

    public static SafeTravelResult success(Location resolved, CompletableFuture<Boolean> future) {
        return new SafeTravelResult(Status.SUCCESS, null, null, resolved, 0L, future);
    }

    public static SafeTravelResult failure(Status status, String key, String fallback) {
        return new SafeTravelResult(status, key, fallback, null, 0L, CompletableFuture.completedFuture(false));
    }

    public static SafeTravelResult cooldown(long remainingMillis) {
        return new SafeTravelResult(Status.COOLDOWN, "travel_fail_cooldown",
                "&cTravel is cooling down. Try again in &e{SECONDS}&c second(s).",
                null, remainingMillis, CompletableFuture.completedFuture(false));
    }

    public static SafeTravelResult inCombat(long remainingMillis) {
        return new SafeTravelResult(Status.IN_COMBAT, "travel_fail_combat",
                "&cYou cannot travel while in combat (&e{SECONDS}&cs remaining).",
                null, remainingMillis, CompletableFuture.completedFuture(false));
    }

    public static SafeTravelResult confirmationRequired() {
        return new SafeTravelResult(Status.CONFIRMATION_REQUIRED, "travel_confirm_prompt",
                "&eClick or run again within &f{SECONDS}&e seconds to confirm travel.",
                null, 0L, CompletableFuture.completedFuture(false));
    }

    public static SafeTravelResult unsafe() {
        return failure(Status.UNSAFE_DESTINATION, "travel_fail_unsafe",
                "&cNo safe teleport point was found near that destination.");
    }

    public static SafeTravelResult invalid() {
        return failure(Status.INVALID, "travel_fail_invalid",
                "&cThat travel destination is unavailable.");
    }

    public static SafeTravelResult disabled() {
        return failure(Status.DISABLED, "travel_fail_disabled",
                "&cSafe Travel is disabled on this server.");
    }

    public static SafeTravelResult cancelled() {
        return failure(Status.CANCELLED, "travel_fail_cancelled",
                "&cTravel cancelled.");
    }

    public Status status() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isConfirmationRequired() { return status == Status.CONFIRMATION_REQUIRED; }
    public String messageKey() { return messageKey; }
    public String fallbackMessage() { return fallbackMessage; }
    public Location resolvedLocation() { return resolvedLocation; }
    public long remainingMillis() { return remainingMillis; }
    public long remainingSeconds() { return (remainingMillis + 999L) / 1000L; }
    public CompletableFuture<Boolean> teleportFuture() { return teleportFuture; }
}
