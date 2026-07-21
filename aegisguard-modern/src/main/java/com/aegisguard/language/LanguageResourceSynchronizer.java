package com.aegisguard.language;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Adds new packaged language keys without replacing server-owner customizations. */
public final class LanguageResourceSynchronizer {

    private static final String BACKUP_FOLDER = "backups/language-sync-1.2.7";

    private final JavaPlugin plugin;

    public LanguageResourceSynchronizer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int synchronize(String resourcePath) {
        String normalized = normalize(resourcePath);
        File target = new File(plugin.getDataFolder(), normalized);
        try (InputStream stream = plugin.getResource(normalized)) {
            if (stream == null) {
                plugin.getLogger().warning("Packaged language resource is missing: " + normalized);
                return 0;
            }
            if (!target.exists()) {
                plugin.saveResource(normalized, false);
                return 0;
            }

            YamlConfiguration packaged = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            YamlConfiguration installed = YamlConfiguration.loadConfiguration(target);
            int additions = mergeMissing(installed, packaged);

            if (additions == 0) return 0;
            createBackupOnce(target.toPath(), normalized);
            installed.save(target);
            return additions;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not synchronize language resource " + normalized + ": " + e.getMessage());
            return 0;
        }
    }

    static int mergeMissing(YamlConfiguration installed, YamlConfiguration packaged) {
        int additions = 0;
        for (String key : packaged.getKeys(true)) {
            if (packaged.isConfigurationSection(key) || installed.contains(key)) continue;
            installed.set(key, packaged.get(key));
            additions++;
        }
        return additions;
    }

    public void ensure(String resourcePath) {
        String normalized = normalize(resourcePath);
        File target = new File(plugin.getDataFolder(), normalized);
        if (target.exists()) return;
        try (InputStream stream = plugin.getResource(normalized)) {
            if (stream != null) plugin.saveResource(normalized, false);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not inspect optional language resource " + normalized + ": " + e.getMessage());
        }
    }

    private void createBackupOnce(Path source, String resourcePath) throws IOException {
        Path backup = plugin.getDataFolder().toPath()
                .resolve(BACKUP_FOLDER)
                .resolve(resourcePath.replace('/', File.separatorChar));
        if (Files.exists(backup)) return;
        Files.createDirectories(backup.getParent());
        Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private String normalize(String resourcePath) {
        String normalized = resourcePath == null ? "" : resourcePath.replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid language resource path: " + resourcePath);
        }
        return normalized;
    }
}
