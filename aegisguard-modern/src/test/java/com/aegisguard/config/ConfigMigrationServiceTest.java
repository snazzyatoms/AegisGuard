package com.aegisguard.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 0 (release safety): proves that upgrading a customized, older-schema config.yml is
 * backed up first and keeps the server owner's custom values, instead of silently overwriting them.
 *
 * These tests call the package-visible {@link ConfigMigrationService#migrate(File, File, java.util.function.Supplier)}
 * overload directly, which contains the exact same logic as the production {@link ConfigMigrationService#migrate()}
 * entry point but takes explicit file/resource arguments so it can run without a live plugin instance.
 */
class ConfigMigrationServiceTest {

    private static final Path SHIPPED_DEFAULTS = Path.of("src/main/resources/config.yml");

    @Test
    void migratingACustomizedOldConfigBacksItUpAndKeepsCustomValues(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.set("config_schema", 1200);
        // A server owner's deliberate customization that must survive migration.
        oldConfig.set("full_plot_renting.duration_days", 14);
        // An old alias key that should be migrated to its modern path.
        oldConfig.set("claim_blocks.starter_amount", 500);
        // An arbitrary custom key with no equivalent in the shipped defaults; migration must not drop it.
        oldConfig.set("custom_test_marker", "keep-me");
        oldConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        boolean migrated = service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults);

        assertTrue(migrated, "An old-schema config must trigger a migration");

        // A safety backup of the pre-migration file must exist and contain the original custom value.
        File backup = service.backup();
        assertNotNull(backup, "Migration must create a backup before rewriting config.yml");
        assertTrue(backup.exists());
        YamlConfiguration backedUp = YamlConfiguration.loadConfiguration(backup);
        assertEquals(14, backedUp.getInt("full_plot_renting.duration_days"),
                "The backup must preserve the pre-migration value");
        assertEquals(1200, backedUp.getInt("config_schema"));

        // The live config.yml must be upgraded in place without losing the custom values.
        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(ConfigMigrationService.CURRENT_SCHEMA, migratedConfig.getInt("config_schema"));
        assertEquals(14, migratedConfig.getInt("full_plot_renting.duration_days"),
                "Customized settings must not be reset to shipped defaults");
        assertEquals("keep-me", migratedConfig.getString("custom_test_marker"),
                "Unrecognized custom keys must be preserved, not dropped");
        assertEquals(500, migratedConfig.getInt("claim_blocks.starting_blocks"),
                "Old alias keys must be migrated to their modern path");

        // A migration report must be written for staff review.
        File reports = new File(dataFolder, "reports");
        assertTrue(reports.isDirectory());
        assertTrue(Files.list(reports.toPath()).findAny().isPresent(), "A migration report file must be written");
        assertNotNull(service.lastReport());
        assertTrue(service.lastReport().exists());
    }

    @Test
    void migratingPreservesExplicitlyEnabledHooksAndMergesMissingAsFalse(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.set("config_schema", 1279);
        oldConfig.set("hooks.dynmap.enabled", true);
        oldConfig.set("hooks.discord.enabled", true);
        oldConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        boolean migrated = service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults);
        assertTrue(migrated);

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(ConfigMigrationService.CURRENT_SCHEMA, migratedConfig.getInt("config_schema"));
        assertTrue(migratedConfig.getBoolean("hooks.dynmap.enabled"),
                "Existing enabled hooks must not be force-disabled");
        assertTrue(migratedConfig.getBoolean("hooks.discord.enabled"));
        assertFalse(migratedConfig.getBoolean("hooks.bluemap.enabled"),
                "Missing hook keys should merge shipped false defaults");
        assertFalse(migratedConfig.getBoolean("hooks.protection_compat.enabled"));
    }

    @Test
    void migratingAddsNewLanguageIdsWithoutRemovingOwnerChoices(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.set("config_schema", 1283);
        oldConfig.set("localization.available_languages",
                java.util.List.of("old_english", "modern_english", "custom_pack"));
        oldConfig.set("language_styles.available",
                java.util.List.of("old_english", "modern_english"));
        oldConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        boolean migrated = service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults);
        assertTrue(migrated);

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        java.util.List<String> available = migratedConfig.getStringList("localization.available_languages");
        assertTrue(available.contains("custom_pack"), "Owner custom language IDs must be kept");
        assertTrue(available.contains("portuguese_br"));
        assertTrue(available.contains("french_fr"));
        assertTrue(available.contains("italian_it"));
        assertTrue(available.contains("german_de"));
        assertTrue(available.contains("polish_pl"));
        assertTrue(migratedConfig.getStringList("language_styles.available").contains("polish_pl"));
    }

    @Test
    void migratingAnAlreadyCurrentConfigDoesNotRewriteOrBackUpAnything(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration currentConfig = new YamlConfiguration();
        currentConfig.set("config_schema", ConfigMigrationService.CURRENT_SCHEMA);
        currentConfig.set("full_plot_renting.duration_days", 21);
        currentConfig.save(configFile);

        long beforeModified = configFile.lastModified();

        ConfigMigrationService service = new ConfigMigrationService(null);
        boolean migrated = service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults);

        assertFalse(migrated, "A config already on the current schema must not be treated as a migration");
        assertNull(service.backup(), "No backup should be created when nothing is migrated");
        assertEquals(beforeModified, configFile.lastModified(), "An already-current config.yml must not be rewritten");

        File backups = new File(dataFolder, "backups");
        assertFalse(backups.exists(), "No backups directory should be created when nothing is migrated");
    }

    private static InputStream openShippedDefaults() {
        try {
            return Files.newInputStream(SHIPPED_DEFAULTS);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Test
    void migratingPreservesDisabledOptionalModulesOnTheSwitchboard(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.set("config_schema", 1286);
        oldConfig.set("guest_passes.enabled", false);
        oldConfig.set("routes.enabled", false);
        oldConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        assertTrue(service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults));

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(migratedConfig.getBoolean("modules.guest_passes"));
        assertFalse(migratedConfig.getBoolean("guest_passes.enabled"));
        assertFalse(migratedConfig.getBoolean("modules.routes"));
        assertFalse(migratedConfig.getBoolean("routes.enabled"));
    }

    @Test
    void first1287UpgradeKeepsLegacyDisabledArenaAndUpkeep(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.set("config_schema", 1286);
        oldConfig.set("arena.enabled", false);
        oldConfig.set("upkeep.enabled", false);
        oldConfig.set("claims.merging.enabled", false);
        oldConfig.set("wilderness_revert.enabled", false);
        oldConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        assertTrue(service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults));

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(migratedConfig.getBoolean("modules.arena"));
        assertFalse(migratedConfig.getBoolean("arena.enabled"));
        assertFalse(migratedConfig.getBoolean("modules.upkeep"));
        assertFalse(migratedConfig.getBoolean("upkeep.enabled"));
        assertFalse(migratedConfig.getBoolean("modules.claim_merge"));
        assertFalse(migratedConfig.getBoolean("claims.merging.enabled"));
        assertFalse(migratedConfig.getBoolean("modules.wilderness_revert"));
        assertFalse(migratedConfig.getBoolean("wilderness_revert.enabled"));
    }

    @Test
    void missingModuleKeysOnFirst1287UpgradeDefaultOn(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration oldConfig = new YamlConfiguration();
        oldConfig.set("config_schema", 1286);
        oldConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        assertTrue(service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults));

        YamlConfiguration migratedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(migratedConfig.getBoolean("modules.arena"));
        assertTrue(migratedConfig.getBoolean("arena.enabled"));
        assertTrue(migratedConfig.getBoolean("modules.upkeep"));
        assertTrue(migratedConfig.getBoolean("modules.claim_merge"));
        assertTrue(migratedConfig.getBoolean("modules.wilderness_revert"));
        assertTrue(migratedConfig.getBoolean("modules.guest_passes"));
    }

    @Test
    void currentSchemaKeepsSavedModuleFalseValues(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        File dataFolder = tempDir.toFile();
        File configFile = new File(dataFolder, "config.yml");

        YamlConfiguration currentConfig = new YamlConfiguration();
        currentConfig.set("config_schema", ConfigMigrationService.CURRENT_SCHEMA);
        currentConfig.set("modules.arena", false);
        currentConfig.set("arena.enabled", false);
        currentConfig.save(configFile);

        ConfigMigrationService service = new ConfigMigrationService(null);
        assertFalse(service.migrate(configFile, dataFolder, ConfigMigrationServiceTest::openShippedDefaults));

        YamlConfiguration after = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(after.getBoolean("modules.arena"));
        assertFalse(after.getBoolean("arena.enabled"));
    }
}
