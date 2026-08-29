package com.aegisguard.config;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ConfigMigrationService {

    public static final int CURRENT_SCHEMA = 1310;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final AegisGuard plugin;
    private final List<String> changes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private File backup;
    private File lastReport;

    public ConfigMigrationService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void migrate() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        boolean migrated = migrate(configFile, plugin.getDataFolder(), () -> plugin.getResource("config.yml"));

        if (migrated) {
            plugin.reloadConfig();
            plugin.getLogger().info("Configuration migrated to schema " + CURRENT_SCHEMA
                    + (backup == null ? "." : "; backup: " + backup.getName()));
            plugin.getLogger().info("Upgraded to 1.4.0. Existing plots were left unchanged. Doctor is not required.");
            return;
        }

        for (String warning : warnings) plugin.getLogger().warning("[Config] " + warning);
    }

    /**
     * Core migration routine, deliberately independent of a live {@code AegisGuard}/{@code JavaPlugin}
     * instance so it can be exercised directly by unit tests without a running server.
     *
     * @param configFile       the server's {@code config.yml} to validate/migrate in place
     * @param dataFolder       the plugin data folder (used for {@code backups/} and {@code reports/})
     * @param defaultsSupplier supplies the shipped default {@code config.yml} contents (closed by this method)
     * @return {@code true} if a schema migration was performed and {@code configFile} was rewritten,
     *         {@code false} if the file was already current and only validated
     */
    boolean migrate(File configFile, File dataFolder, Supplier<InputStream> defaultsSupplier) {
        changes.clear();
        warnings.clear();
        backup = null;
        lastReport = null;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        int previous = config.getInt("config_schema", config.getInt("config-version", 0));

        if (previous < CURRENT_SCHEMA) {
            backup = backup(configFile, dataFolder);
            if (configFile.exists() && backup == null) {
                throw new IllegalStateException("Refusing to migrate config.yml because a safety backup could not be created.");
            }
            migrateAliases(config);
            preserveLegacyModuleFlags(config);
            mergeMissingDefaults(config, defaultsSupplier);
            mergeAvailableLanguages(config);
            syncModuleSwitchboard(config, previous);
            validateAndRepair(config);
            config.set("config_schema", CURRENT_SCHEMA);
            config.set("config-version", null);
            try {
                config.save(configFile);
                changes.add("Schema upgraded from " + previous + " to " + CURRENT_SCHEMA + ".");
            } catch (IOException error) {
                throw new IllegalStateException("Could not save migrated config.yml", error);
            }
            writeReport(previous, dataFolder);
            return true;
        }

        validateOnly(config);
        return false;
    }

    public List<String> changes() { return List.copyOf(changes); }
    public List<String> warnings() { return List.copyOf(warnings); }
    public File backup() { return backup; }
    public File lastReport() { return lastReport; }

    private void migrateAliases(YamlConfiguration config) {
        Map<String, String> aliases = Map.of(
                "claim_blocks.starter_amount", "claim_blocks.starting_blocks",
                "travel_system.enabled", "claims.travel_system.enabled",
                "travel_system.allow_home_teleport", "claims.travel_system.allow_home_teleport",
                "travel_system.allow_visit_teleport", "claims.travel_system.allow_visit_teleport"
        );
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (config.isSet(alias.getKey()) && !config.isSet(alias.getValue())) {
                config.set(alias.getValue(), config.get(alias.getKey()));
                changes.add("Migrated " + alias.getKey() + " to " + alias.getValue() + ".");
            }
        }
    }

    private void mergeMissingDefaults(YamlConfiguration config, Supplier<InputStream> defaultsSupplier) {
        try (InputStream input = defaultsSupplier.get()) {
            if (input == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key) || config.isSet(key)) continue;
                config.set(key, defaults.get(key));
                changes.add("Added missing setting " + key + ".");
            }
        } catch (IOException error) {
            warnings.add("Could not read embedded defaults: " + error.getMessage());
        }
    }

    /**
     * Adds newly shipped language IDs into owner-configured available-language lists
     * without removing custom entries the owner already chose.
     */
    private void mergeAvailableLanguages(YamlConfiguration config) {
        List<String> shipped = List.of(
                "old_english", "modern_english", "spanish_mx", "spanish_ar",
                "portuguese_br", "french_fr", "italian_it", "german_de", "polish_pl"
        );
        appendMissingLanguages(config, "localization.available_languages", shipped);
        appendMissingLanguages(config, "language_styles.available", shipped);
    }

    private void appendMissingLanguages(YamlConfiguration config, String path, List<String> shipped) {
        List<String> current = new ArrayList<>(config.getStringList(path));
        boolean changed = false;
        for (String language : shipped) {
            if (language == null || language.isBlank()) continue;
            boolean present = current.stream().anyMatch(existing ->
                    existing != null && existing.equalsIgnoreCase(language));
            if (!present) {
                current.add(language);
                changed = true;
            }
        }
        if (changed) {
            config.set(path, current);
            changes.add("Merged newly shipped language IDs into " + path + ".");
        }
    }

    /**
     * Copy {@code section.enabled} onto {@code modules.*} when the switchboard key is missing,
     * so a later defaults-merge cannot turn a deliberately disabled system back on.
     */
    private void preserveLegacyModuleFlags(YamlConfiguration config) {
        for (Modules.Id id : Modules.Id.values()) {
            if (!config.isSet(id.modulesPath()) && config.isSet(id.legacyPath())) {
                config.set(id.modulesPath(), config.getBoolean(id.legacyPath()));
            }
        }
    }

    /**
     * On first 1287 upgrade, copy existing section.enabled values onto modules.* so
     * a server that already turned a system off does not get defaulted back on.
     * After that, modules.* is canonical and is copied onto the matching enabled path.
     */
    private void syncModuleSwitchboard(YamlConfiguration config, int previous) {
        for (Modules.Id id : Modules.Id.values()) {
            if (previous < 1287 && config.isSet(id.legacyPath())) {
                config.set(id.modulesPath(), config.getBoolean(id.legacyPath()));
            }
            if (config.isSet(id.modulesPath())) {
                boolean on = config.getBoolean(id.modulesPath());
                if (!config.isSet(id.legacyPath()) || config.getBoolean(id.legacyPath()) != on) {
                    config.set(id.legacyPath(), on);
                    changes.add("Synced " + id.legacyPath() + " from modules." + id.key() + ".");
                }
            }
        }
    }

    private void validateAndRepair(YamlConfiguration config) {
        repairInt(config, "full_plot_renting.duration_days", 1, 365, 7);
        repairInt(config, "full_plot_renting.maximum_duration_days", 1, 365, 90);
        repairInt(config, "full_plot_renting.max_active_rentals_per_player", 1, 100, 3);
        repairInt(config, "full_plot_renting.reminder_hours", 1, 168, 24);
        repairInt(config, "territory_activity.max_entries", 100, 100_000, 5000);
        repairInt(config, "plot_discovery.max_results", 10, 5000, 500);
        repairInt(config, "mob_barrier.interval_seconds", 1, 3600, 3);
        repairInt(config, "mob_barrier.player_cleanup_interval_seconds", 1, 300, 2);
        repairInt(config, "mob_barrier.despawn_grace_seconds", 0, 300, 5);
        repairInt(config, "teleport_beacons.max_per_plot", 1, 64, 3);
        repairInt(config, "teleport_beacons.max_per_server_zone", 1, 64, 8);
        repairInt(config, "plot_chat.max_message_length", 16, 256, 256);
        repairInt(config, "visual_presence.border_label_distance", 1, 16, 3);
        repairInt(config, "expansions.horizons.unlock_level", 30, 1000, 30);
        repairInt(config, "expansions.horizons.pulse_cooldown_seconds", 1, 86_400, 300);
        repairInt(config, "expansions.horizons.max_radius_global", 1, 100_000, 750);
        repairInt(config, "leveling.disciplines.change_cooldown_days", 0, 3650, 7);
        repairInt(config, "snapshots.automatic_player.interval_minutes", 1, 1440, 5);
        repairInt(config, "snapshots.automatic_player.batch_size", 1, 100, 5);
        repairInt(config, "snapshots.automatic_player.minimum_backup_interval_minutes", 0, 10080, 60);
        repairInt(config, "snapshots.automatic_player.retention_per_plot", 1, 100, 5);
        repairInt(config, "snapshots.automatic_player.retention_days", 0, 3650, 14);
        repairInt(config, "snapshots.automatic_player.eligibility.max_owner_inactive_days", 0, 3650, 30);
        repairDouble(config, "snapshots.automatic_player.pause_below_tps", 0.0D, 20.0D, 18.0D);
        repairInt(config, "snapshots.build_backup.max_chunks_per_region_job", 1, 256, 4);
        repairInt(config, "snapshots.build_backup.storage.retention_per_plot", 1, 1000, 10);
        repairInt(config, "snapshots.build_backup.storage.global_max_megabytes", 1, 1_000_000, 4096);
        repairInt(config, "snapshots.build_backup.storage.warning_cooldown_minutes", 1, 10080, 60);
        String orphanPolicy = config.getString("snapshots.build_backup.storage.orphan_policy", "quarantine");
        if (!"quarantine".equalsIgnoreCase(orphanPolicy)
                && !"report_only".equalsIgnoreCase(orphanPolicy)) {
            config.set("snapshots.build_backup.storage.orphan_policy", "quarantine");
            warnings.add("snapshots.build_backup.storage.orphan_policy was invalid and was reset to quarantine.");
        }
        repairDouble(config, "expansions.horizons.renown.expansion_per_block", 0.0D, 1000.0D, 0.05D);
        repairInt(config, "expansions.horizons.renown.expansion_cap", 0, 10_000_000, 1500);
        repairInt(config, "expansions.horizons.renown.unique_visit", 0, 1_000_000, 15);
        repairInt(config, "expansions.horizons.renown.unique_visit_cooldown_days", 1, 3650, 7);
        repairInt(config, "expansions.horizons.renown.unique_like", 0, 1_000_000, 75);
        int[] renownDefaults = {2500, 7500, 17500, 35000, 60000};
        for (int tier = 1; tier <= 5; tier++) {
            String base = "expansions.horizons.ranks." + tier;
            repairInt(config, base + ".required_renown", 1, 100_000_000, renownDefaults[tier - 1]);
            repairInt(config, base + ".radius_gain", 1, 100_000, switch (tier) {
                case 1 -> 60;
                case 2 -> 75;
                case 3 -> 90;
                case 4 -> 110;
                default -> 130;
            });
        }
        repairDouble(config, "full_plot_renting.minimum_price", 0.01D, 1_000_000_000.0D, 1.0D);
        repairDouble(config, "full_plot_renting.maximum_price", 0.01D, 1_000_000_000_000.0D, 1_000_000_000.0D);
        repairDouble(config, "full_plot_renting.maximum_deposit", 0.0D, 1_000_000_000.0D, 1_000_000.0D);

        int defaultDays = config.getInt("full_plot_renting.duration_days", 7);
        int maximumDays = config.getInt("full_plot_renting.maximum_duration_days", 90);
        if (maximumDays < defaultDays) {
            config.set("full_plot_renting.maximum_duration_days", Math.max(defaultDays, 90));
            warnings.add("full_plot_renting.maximum_duration_days was below duration_days and was repaired.");
        }
        double minimumPrice = config.getDouble("full_plot_renting.minimum_price", 1.0D);
        double maximumPrice = config.getDouble("full_plot_renting.maximum_price", 1_000_000_000.0D);
        if (maximumPrice < minimumPrice) {
            config.set("full_plot_renting.maximum_price", Math.max(minimumPrice, 1_000_000_000.0D));
            warnings.add("full_plot_renting.maximum_price was below minimum_price and was repaired.");
        }

        String wandMaterial = config.getString("admin.wand.material", "BLAZE_ROD");
        if (Material.matchMaterial(wandMaterial) == null) {
            config.set("admin.wand.material", "BLAZE_ROD");
            warnings.add("Invalid admin.wand.material was replaced with BLAZE_ROD.");
        }

        List<String> categories = config.getStringList("plot_discovery.categories");
        if (categories.isEmpty()) {
            config.set("plot_discovery.categories", List.of("build", "shop", "town", "farm", "event", "other"));
            changes.add("Added default plot discovery categories.");
        }
    }

    private void validateOnly(YamlConfiguration config) {
        if (config.getInt("full_plot_renting.duration_days", 7) < 1) warnings.add("full_plot_renting.duration_days must be at least 1.");
        if (config.getInt("full_plot_renting.maximum_duration_days", 90)
                < config.getInt("full_plot_renting.duration_days", 7)) {
            warnings.add("full_plot_renting.maximum_duration_days must not be below duration_days.");
        }
        double min = config.getDouble("full_plot_renting.minimum_price", 1.0D);
        double max = config.getDouble("full_plot_renting.maximum_price", 1_000_000_000.0D);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min <= 0.0D || max < min) {
            warnings.add("Full-plot rental price limits are invalid.");
        }
        String material = config.getString("admin.wand.material", "BLAZE_ROD");
        if (Material.matchMaterial(material) == null) warnings.add("admin.wand.material is not a valid material: " + material);
        if (config.getInt("snapshots.automatic_player.interval_minutes", 5) < 1
                || config.getInt("snapshots.automatic_player.batch_size", 5) < 1
                || config.getInt("snapshots.automatic_player.retention_per_plot", 5) < 1) {
            warnings.add("Automatic player-backup interval, batch size, and retention must be positive.");
        }
        double backupTps = config.getDouble("snapshots.automatic_player.pause_below_tps", 18.0D);
        if (!Double.isFinite(backupTps) || backupTps < 0.0D || backupTps > 20.0D) {
            warnings.add("snapshots.automatic_player.pause_below_tps must be between 0 and 20.");
        }
        if (config.getInt("snapshots.build_backup.max_chunks_per_region_job", 4) < 1
                || config.getInt("snapshots.build_backup.storage.retention_per_plot", 10) < 1
                || config.getInt("snapshots.build_backup.storage.global_max_megabytes", 4096) < 1) {
            warnings.add("Build-backup job, retention, and global storage limits must be positive.");
        }
        String orphanPolicy = config.getString("snapshots.build_backup.storage.orphan_policy", "quarantine");
        if (!"quarantine".equalsIgnoreCase(orphanPolicy)
                && !"report_only".equalsIgnoreCase(orphanPolicy)) {
            warnings.add("snapshots.build_backup.storage.orphan_policy must be quarantine or report_only.");
        }
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles == null || roles.getKeys(false).isEmpty()) warnings.add("No plot roles are configured.");
    }

    private void repairInt(YamlConfiguration config, String path, int minimum, int maximum, int fallback) {
        int value = config.getInt(path, fallback);
        if (value >= minimum && value <= maximum) return;
        config.set(path, fallback);
        warnings.add(path + " was outside " + minimum + ".." + maximum + " and was reset to " + fallback + ".");
    }

    private void repairDouble(YamlConfiguration config, String path, double minimum, double maximum, double fallback) {
        double value = config.getDouble(path, fallback);
        if (Double.isFinite(value) && value >= minimum && value <= maximum) return;
        config.set(path, fallback);
        warnings.add(path + " was invalid and was reset to " + fallback + ".");
    }

    private File backup(File configFile, File dataFolder) {
        if (!configFile.exists()) return null;
        File backups = new File(dataFolder, "backups");
        if (!backups.exists() && !backups.mkdirs()) {
            warnings.add("Could not create the backups directory.");
            return null;
        }
        File destination = new File(backups, "config-before-" + CURRENT_SCHEMA + "-" + TS.format(LocalDateTime.now()) + ".yml");
        try {
            Files.copy(configFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException error) {
            warnings.add("Could not back up config.yml: " + error.getMessage());
            return null;
        }
    }

    private void writeReport(int previous, File dataFolder) {
        try {
            File reports = new File(dataFolder, "reports");
            Files.createDirectories(reports.toPath());
            StringBuilder report = new StringBuilder("AegisGuard Configuration Migration\n")
                    .append("From schema: ").append(previous).append('\n')
                    .append("To schema: ").append(CURRENT_SCHEMA).append("\n\nChanges\n-------\n");
            changes.forEach(change -> report.append("- ").append(change).append('\n'));
            report.append("\nWarnings\n--------\n");
            warnings.forEach(warning -> report.append("- ").append(warning).append('\n'));
            lastReport = new File(reports, "config-migration-" + TS.format(LocalDateTime.now()) + ".txt");
            Files.writeString(lastReport.toPath(), report.toString(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            lastReport = null;
            if (plugin != null) {
                plugin.getLogger().warning("Could not write configuration migration report: " + error.getMessage());
            }
        }
    }
}
