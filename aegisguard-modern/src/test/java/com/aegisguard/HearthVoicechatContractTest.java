package com.aegisguard;

import com.aegisguard.chat.HearthService;
import com.aegisguard.data.Plot;
import com.aegisguard.hooks.HearthVoicechatHook;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthVoicechatContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsVoicechatHookOffTheClasspath() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1309, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> hearth = (Map<String, Object>) config.get("hearth");
        assertEquals(Boolean.TRUE, hearth.get("voicechat"));
        assertEquals(Boolean.FALSE, hearth.get("voicechat_override_player_groups"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1309"));
    }

    @Test
    void hookIsSoftAndUsesIsolatedHearthGroups() throws Exception {
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("- voicechat"));
        assertFalse(pluginYml.contains("depend:\n  - voicechat")
                || pluginYml.contains("depend: [voicechat]"));
        String hook = Files.readString(JAVA.resolve("hooks/HearthVoicechatHook.java"));
        assertTrue(hook.contains("Group.Type.ISOLATED"));
        assertTrue(hook.contains("GROUP_PREFIX"));
        assertTrue(hook.contains("voicechat_override_player_groups"));
        assertTrue(hook.contains("plugin.runSync"));
        assertTrue(hook.contains("RemoveGroupEvent"));
        assertTrue(hook.contains("voice.getGroup"));
        assertTrue(hook.contains("voice.removeGroup"));
        assertTrue(hook.contains("lastVoiceRoom"));
        assertFalse(hook.contains("Player player = Bukkit.getPlayer(id);")
                && hook.contains("if (player != null) refreshLater(player);")
                && !hook.contains("plugin.runSync"));
        String main = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(main.contains("registerHearthVoicechatHook"));
        assertTrue(main.contains("BukkitVoicechatService"));
        assertTrue(main.contains("refreshAllHearthVoice"));
        assertFalse(main.contains("import de.maxhenkel.voicechat"));
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("voicechat-api"));
        assertTrue(pom.contains("maven.maxhenkel.de"));
        assertTrue(pom.contains("<artifactId>voicechat-api</artifactId>")
                && pom.contains("<scope>provided</scope>"));
        String englishGuis = Files.readString(Path.of("src/main/resources/lang/modern_english/guis.yml"));
        String englishSystem = Files.readString(Path.of("src/main/resources/lang/modern_english/system.yml"));
        assertTrue(englishGuis.contains("hearth_toggle_lore:"));
        assertTrue(englishGuis.contains("button_hearth_on:"));
        assertTrue(englishSystem.contains("log_voicechat_hooked:"));
        assertTrue(englishSystem.contains("flight_skill_granted:"));
    }

    @Test
    void groupNamesStayPrefixedAndBounded() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Keep", "world", 0, 0, 20, 20);
        plot.setFlag("hearth", true);
        HearthService.Room house = HearthService.roomOf(plot, "House");
        String name = HearthVoicechatHook.groupName(house);
        assertTrue(name.startsWith(HearthVoicechatHook.GROUP_PREFIX));
        assertTrue(name.length() <= 24);
        assertTrue(HearthVoicechatHook.roomKey(house).contains(plot.getPlotId().toString()));
    }
}
