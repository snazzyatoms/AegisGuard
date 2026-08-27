package com.aegisguard.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Master on/off switches for optional AegisGuard systems.
 * Core claiming, plot protection, roles, settings, guidebook, and staff tools stay available.
 * {@code modules.<id>} is the switchboard; matching {@code <section>.enabled} keys stay in sync.
 */
public final class Modules {

    public enum Id {
        GUEST_PASSES("guest_passes", "guest_passes.enabled", true, "Guest Passes"),
        LOCKDOWN("lockdown", "lockdown.enabled", true, "Lockdown"),
        ALLIANCE_ACCESS("alliance_access", "alliance_access.enabled", true, "Alliance Access"),
        REALM_PROFILES("realm_profiles", "realm_profiles.enabled", true, "Realm Profiles"),
        FIRST_CLAIM_WALKTHROUGH("first_claim_walkthrough", "first_claim_walkthrough.enabled", true, "First-claim walkthrough"),
        STARTER_KIT("starter_kit", "starter_kit.first_join.enabled", true, "Starter kit"),
        ROUTES("routes", "routes.enabled", true, "Routes"),
        TRAVEL("travel", "claims.travel_system.enabled", true, "Travel"),
        PLOT_DISCOVERY("plot_discovery", "plot_discovery.enabled", true, "Plot discovery"),
        EXPANSIONS("expansions", "expansions.enabled", true, "Expansions"),
        ZONING("zoning", "zoning.enabled", true, "Zoning"),
        GROUP_PLOTS("group_plots", "group_plots.enabled", true, "Group plots"),
        LEVELING("leveling", "leveling.enabled", true, "Leveling"),
        CLAIM_BLOCKS("claim_blocks", "claim_blocks.enabled", true, "ClaimBlocks"),
        ECONOMY("economy", "economy.enabled", true, "Economy"),
        MARKET("market", "market_hub.enabled", true, "Market"),
        MARKET_STALLS("market_stalls", "market_stalls.enabled", true, "TradeStalls"),
        RENTALS("rentals", "full_plot_renting.enabled", true, "Rentals"),
        AUCTION("auction", "auction.enabled", true, "Auctions"),
        UPKEEP("upkeep", "upkeep.enabled", true, "Upkeep"),
        CLAIM_MERGE("claim_merge", "claims.merging.enabled", true, "Claim merging"),
        SNAPSHOTS("snapshots", "snapshots.enabled", true, "Snapshots"),
        AUDIT("audit", "audit.enabled", true, "Audit"),
        COSMETICS("cosmetics", "cosmetics.enabled", true, "Cosmetics"),
        TITLES("titles", "titles.claim_enter_exit.enabled", true, "Entry titles"),
        BIOMES("biomes", "biomes.enabled", true, "Biomes"),
        UNSTUCK("unstuck", "unstuck.enabled", true, "Unstuck"),
        SOCIAL("social", "social.likes_enabled", true, "Likes"),
        TERRITORY_ACTIVITY("territory_activity", "territory_activity.enabled", true, "Territory activity"),
        ARENA("arena", "arena.enabled", true, "Arena"),
        WILDERNESS_REVERT("wilderness_revert", "wilderness_revert.enabled", false, "Wilderness revert"),
        MOB_BARRIER("mob_barrier", "mob_barrier.enabled", true, "Mob barrier"),
        TELEPORT_BEACONS("teleport_beacons", "teleport_beacons.enabled", true, "Teleport Beacons");

        private final String key;
        private final String legacyPath;
        private final boolean defaultOn;
        private final String displayName;

        Id(String key, String legacyPath, boolean defaultOn, String displayName) {
            this.key = key;
            this.legacyPath = legacyPath;
            this.defaultOn = defaultOn;
            this.displayName = displayName;
        }

        public String key() { return key; }
        public String legacyPath() { return legacyPath; }
        public boolean defaultOn() { return defaultOn; }
        public String displayName() { return displayName; }
        public String modulesPath() { return "modules." + key; }
    }

    private final FileConfiguration config;

    public Modules(FileConfiguration config) {
        this.config = config;
    }

    public static Modules of(FileConfiguration config) {
        return new Modules(config);
    }

    public boolean on(Id id) {
        if (id == null || config == null) return id == null || id.defaultOn;
        if (config.isSet(id.modulesPath())) return config.getBoolean(id.modulesPath());
        if (id == Id.TRAVEL) {
            return config.getBoolean("claims.travel_system.enabled",
                    config.getBoolean("travel_system.enabled", id.defaultOn));
        }
        if (id == Id.AUCTION) {
            return config.getBoolean("auction.enabled",
                    config.getBoolean("auctions.enabled",
                            config.getBoolean("market.auctions.enabled", id.defaultOn)));
        }
        return config.getBoolean(id.legacyPath, id.defaultOn);
    }

    public static Id commandModule(String subcommand) {
        if (subcommand == null || subcommand.isBlank()) return null;
        return switch (subcommand.toLowerCase(Locale.ROOT)) {
            case "visit", "home" -> Id.TRAVEL;
            case "stuck" -> Id.UNSTUCK;
            case "profile", "welcome", "farewell", "setdesc", "notice" -> Id.REALM_PROFILES;
            case "guide" -> Id.FIRST_CLAIM_WALKTHROUGH;
            case "market", "sell", "unsell" -> Id.MARKET;
            case "rent", "unrent", "rental" -> Id.RENTALS;
            case "discover", "favorite" -> Id.PLOT_DISCOVERY;
            case "activity" -> Id.TERRITORY_ACTIVITY;
            case "auction" -> Id.AUCTION;
            case "level" -> Id.LEVELING;
            case "zone", "subplot", "subzone" -> Id.ZONING;
            case "like" -> Id.SOCIAL;
            case "ledger", "blocks", "giftblocks" -> Id.CLAIM_BLOCKS;
            case "merge" -> Id.CLAIM_MERGE;
            case "group" -> Id.GROUP_PLOTS;
            case "alliance" -> Id.ALLIANCE_ACCESS;
            case "arena" -> Id.ARENA;
            case "beacon" -> Id.TELEPORT_BEACONS;
            default -> null;
        };
    }
}
