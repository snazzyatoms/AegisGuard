package com.aegisguard.alliance;

import java.util.Locale;

/**
 * Milestone 7 - the per-plot, opt-in alliance access toggles.
 * Risky toggles default OFF. Membership alone never grants manage/ownership/money/rental rights.
 */
public final class AllianceAccess {

    private boolean enter;
    private boolean interact;
    private boolean containers;
    private boolean build;
    private boolean animals;
    private boolean vehicles;
    private boolean friendlyPvp;

    public AllianceAccess() {
        // All default false — safe for existing plots.
    }

    public boolean isEnter() { return enter; }
    public boolean isInteract() { return interact; }
    public boolean isContainers() { return containers; }
    public boolean isBuild() { return build; }
    public boolean isAnimals() { return animals; }
    public boolean isVehicles() { return vehicles; }
    public boolean isFriendlyPvp() { return friendlyPvp; }

    public void setEnter(boolean enter) { this.enter = enter; }
    public void setInteract(boolean interact) { this.interact = interact; }
    public void setContainers(boolean containers) { this.containers = containers; }
    public void setBuild(boolean build) { this.build = build; }
    public void setAnimals(boolean animals) { this.animals = animals; }
    public void setVehicles(boolean vehicles) { this.vehicles = vehicles; }
    public void setFriendlyPvp(boolean friendlyPvp) { this.friendlyPvp = friendlyPvp; }

    public boolean toggle(String key) {
        if (key == null) return false;
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "enter" -> { enter = !enter; yield enter; }
            case "interact" -> { interact = !interact; yield interact; }
            case "containers" -> { containers = !containers; yield containers; }
            case "build" -> { build = !build; yield build; }
            case "animals" -> { animals = !animals; yield animals; }
            case "vehicles" -> { vehicles = !vehicles; yield vehicles; }
            case "friendly_pvp", "pvp" -> { friendlyPvp = !friendlyPvp; yield friendlyPvp; }
            default -> false;
        };
    }

    public boolean isEnabled(String key) {
        if (key == null) return false;
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "enter" -> enter;
            case "interact" -> interact;
            case "containers" -> containers;
            case "build" -> build;
            case "animals" -> animals;
            case "vehicles" -> vehicles;
            case "friendly_pvp", "pvp" -> friendlyPvp;
            default -> false;
        };
    }

    /** Whether this access set grants the given plot permission token to alliance members. */
    public boolean grantsPermission(String permission) {
        if (permission == null || permission.isBlank()) return false;
        String needle = permission.trim().toUpperCase(Locale.ROOT);
        return switch (needle) {
            case "INTERACT" -> interact;
            case "CONTAINERS" -> containers;
            case "BUILD", "BLOCK_BREAK", "BLOCK_PLACE" -> build;
            case "ANIMALS", "FARM" -> animals;
            case "VEHICLES" -> vehicles;
            default -> false;
        };
    }

    public void clear() {
        enter = false;
        interact = false;
        containers = false;
        build = false;
        animals = false;
        vehicles = false;
        friendlyPvp = false;
    }

    public String serialize() {
        return "enter:" + (enter ? 1 : 0)
                + ",interact:" + (interact ? 1 : 0)
                + ",containers:" + (containers ? 1 : 0)
                + ",build:" + (build ? 1 : 0)
                + ",animals:" + (animals ? 1 : 0)
                + ",vehicles:" + (vehicles ? 1 : 0)
                + ",friendlyPvp:" + (friendlyPvp ? 1 : 0);
    }

    public static AllianceAccess deserialize(String blob) {
        AllianceAccess access = new AllianceAccess();
        if (blob == null || blob.isBlank()) return access;
        for (String part : blob.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            boolean on = "1".equals(kv[1]) || "true".equalsIgnoreCase(kv[1]);
            switch (kv[0].trim()) {
                case "enter" -> access.enter = on;
                case "interact" -> access.interact = on;
                case "containers" -> access.containers = on;
                case "build" -> access.build = on;
                case "animals" -> access.animals = on;
                case "vehicles" -> access.vehicles = on;
                case "friendlyPvp" -> access.friendlyPvp = on;
                default -> {}
            }
        }
        return access;
    }
}
