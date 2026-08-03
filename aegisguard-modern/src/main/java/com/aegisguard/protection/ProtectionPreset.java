package com.aegisguard.protection;

import com.aegisguard.data.Plot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-click Plot Flags bundles for common plot roles.
 * Values follow GREEN=protected convention (true = protected / restricted).
 */
public enum ProtectionPreset {

    HOME,
    SHOP,
    ARENA,
    FARM;

    public Map<String, Boolean> flagBundle() {
        return switch (this) {
            // Note: plot flag "entry" true = open/public, false = closed/private (enforcement semantics).
            case HOME -> bundle(
                    "entry", false,
                    "containers", true,
                    "build", true,
                    "pvp", true,
                    "tnt-damage", true,
                    "fire-spread", true,
                    "mobs", true,
                    "animals", true,
                    "farm", true,
                    "doors", true,
                    "redstone", true,
                    "vehicles", true,
                    "piston-use", true,
                    "hopper-pipe", true,
                    "liquid-flow", true,
                    "teleport-ward", true,
                    "storm-ward", true,
                    "decor", true,
                    "shop-interact", false
            );
            case SHOP -> bundle(
                    "entry", true,
                    "shop-interact", true,
                    "containers", true,
                    "build", true,
                    "pvp", true,
                    "tnt-damage", true,
                    "fire-spread", true,
                    "mobs", true,
                    "doors", false,
                    "redstone", false,
                    "animals", true,
                    "farm", true,
                    "vehicles", true,
                    "piston-use", true,
                    "hopper-pipe", true,
                    "liquid-flow", true,
                    "teleport-ward", false,
                    "storm-ward", true,
                    "decor", true
            );
            case ARENA -> bundle(
                    "entry", true,
                    "pvp", false,
                    "tnt-damage", true,
                    "fire-spread", true,
                    "mobs", true,
                    "containers", true,
                    "build", true,
                    "doors", false,
                    "redstone", false,
                    "animals", true,
                    "farm", true,
                    "vehicles", false,
                    "hopper-pipe", true,
                    "liquid-flow", true,
                    "teleport-ward", true,
                    "storm-ward", true,
                    "decor", true,
                    "shop-interact", false
            );
            case FARM -> bundle(
                    "entry", false,
                    "farm", false,
                    "animals", false,
                    "mobs", true,
                    "tnt-damage", true,
                    "fire-spread", true,
                    "containers", true,
                    "build", true,
                    "pvp", true,
                    "doors", true,
                    "redstone", true,
                    "vehicles", true,
                    "hopper-pipe", true,
                    "liquid-flow", true,
                    "teleport-ward", true,
                    "storm-ward", true,
                    "decor", true,
                    "shop-interact", false
            );
        };
    }

    public void apply(Plot plot) {
        if (plot == null) return;
        for (Map.Entry<String, Boolean> entry : flagBundle().entrySet()) {
            plot.setFlag(entry.getKey(), entry.getValue());
        }
    }

    public String fallbackLabel() {
        return switch (this) {
            case HOME -> "Home";
            case SHOP -> "Shop";
            case ARENA -> "Arena";
            case FARM -> "Farm";
        };
    }

    public static java.util.List<ProtectionPreset> ordered() {
        return java.util.List.of(HOME, SHOP, ARENA, FARM);
    }

    private static Map<String, Boolean> bundle(Object... keyValues) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out.put(String.valueOf(keyValues[i]), (Boolean) keyValues[i + 1]);
        }
        return Map.copyOf(out);
    }
}
