package com.aegisguard.language;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageResourceSynchronizerTest {

    @Test
    void addsOnlyMissingKeysAndPreservesAdministratorCustomizations() {
        YamlConfiguration installed = new YamlConfiguration();
        installed.set("button_exit", "&4My Custom Exit");
        installed.set("custom.server_key", "keep me");

        YamlConfiguration packaged = new YamlConfiguration();
        packaged.set("button_exit", "&cClose");
        packaged.set("doctor.menu.title", "&8Doctor");
        packaged.set("doctor.menu.lore", List.of("&7Safe repair tools"));

        assertEquals(2, LanguageResourceSynchronizer.mergeMissing(installed, packaged));
        assertEquals("&4My Custom Exit", installed.getString("button_exit"));
        assertEquals("keep me", installed.getString("custom.server_key"));
        assertEquals("&8Doctor", installed.getString("doctor.menu.title"));
        assertEquals(List.of("&7Safe repair tools"), installed.getStringList("doctor.menu.lore"));
        assertEquals(0, LanguageResourceSynchronizer.mergeMissing(installed, packaged));
    }
}
