package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseResourcesTest {

    @Test
    void everyYamlResourceParses() throws Exception {
        Path root = Path.of("src/main/resources");
        List<Path> files;
        try (var paths = Files.walk(root)) {
            files = paths.filter(path -> path.toString().endsWith(".yml")).sorted().toList();
        }
        assertEquals(24, files.size(), "Unexpected resource count; update this assertion intentionally when adding YAML files");
        Yaml yaml = new Yaml();
        for (Path file : files) {
            String content = Files.readString(file);
            content.codePoints().forEach(codePoint -> assertTrue(isYamlPrintable(codePoint),
                    () -> "Invalid character U+" + Integer.toHexString(codePoint) + " in " + file));
            try {
                // Parse the complete string, matching Bukkit's loadFromString resource path.
                assertNotNull(yaml.load(content), () -> "Empty YAML resource: " + file);
            } catch (RuntimeException error) {
                throw new AssertionError("Invalid YAML resource: " + file, error);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pluginDescriptorDeclaresReleaseSafetyMetadata() throws Exception {
        Yaml yaml = new Yaml();
        Object loaded;
        try (InputStream input = Files.newInputStream(Path.of("src/main/resources/plugin.yml"))) {
            loaded = yaml.load(input);
        }
        assertTrue(loaded instanceof Map<?, ?>, "plugin.yml must contain a YAML map");
        Map<String, Object> plugin = (Map<String, Object>) loaded;
        assertEquals("1.2.7", String.valueOf(plugin.get("version")));
        assertEquals(Boolean.TRUE, plugin.get("folia-supported"));
        Map<String, Object> permissions = (Map<String, Object>) plugin.get("permissions");
        assertTrue(permissions.containsKey("aegis.admin.doctor.repair"));
        assertTrue(permissions.containsKey("aegis.admin.rentals"));
        assertTrue(permissions.containsKey("aegis.discovery"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void configContainsTerritoryLifeSchema() throws Exception {
        Yaml yaml = new Yaml();
        Object loaded;
        try (InputStream input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            loaded = yaml.load(input);
        }
        assertTrue(loaded instanceof Map<?, ?>, "config.yml must contain a YAML map");
        Map<String, Object> config = (Map<String, Object>) loaded;
        assertEquals(1271, ((Number) config.get("config_schema")).intValue());
        assertTrue(config.containsKey("full_plot_renting"));
        assertTrue(config.containsKey("plot_discovery"));
        assertTrue(config.containsKey("territory_activity"));
    }

    private static boolean isYamlPrintable(int codePoint) {
        return (codePoint >= 0x20 && codePoint <= 0x7E)
                || codePoint == 0x09 || codePoint == 0x0A || codePoint == 0x0D || codePoint == 0x85
                || (codePoint >= 0xA0 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }
}
