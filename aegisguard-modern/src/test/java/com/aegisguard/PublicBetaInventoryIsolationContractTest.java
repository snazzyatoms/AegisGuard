package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicBetaInventoryIsolationContractTest {

    private static final Path MAIN = Path.of("src/main");

    @Test
    void isolationIsEnabledFailClosedAndCoversEveryPlayerState() throws Exception {
        String source = Files.readString(MAIN.resolve(
                "java/com/aegisguard/publicbeta/PublicBetaInventoryService.java"));
        Map<String, Object> config = new Yaml().load(Files.readString(MAIN.resolve("resources/config.yml")));
        Map<?, ?> beta = (Map<?, ?>) config.get("public-beta-mode");
        Map<?, ?> isolation = (Map<?, ?>) beta.get("inventory-isolation");

        assertEquals(Boolean.TRUE, isolation.get("enabled"));
        assertTrue(source.contains("if (!save(player, from))"));
        assertTrue(source.contains("event.setCancelled(true)"));
        assertTrue(source.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(source.contains("instance-id"));
        for (String state : List.of("contents", "armor", "off-hand", "ender-chest", "exp",
                "health", "food", "saturation", "exhaustion", "effects", "game-mode")) {
            assertTrue(source.contains("\"" + state + "\""), "missing isolated state " + state);
        }
        for (String scope : List.of("HUB", "PLAY", "TEST_LAB", "OTHER")) {
            assertTrue(source.contains(scope), "missing scope " + scope);
        }
    }

    @Test
    void everyLanguageWarnsAboutIsolationAndTravelFailures() throws Exception {
        List<String> keys = List.of("public_beta_inventory_save_failed",
                "public_beta_inventory_restore_failed", "public_beta_inventory_switched",
                "public_beta_inventory_scope_hub", "public_beta_inventory_scope_play",
                "public_beta_inventory_scope_lab", "public_beta_inventory_scope_other",
                "public_beta_inventory_isolation_lore", "public_beta_guide_inventory_isolation");
        try (var languages = Files.list(MAIN.resolve("resources/lang"))) {
            for (Path language : languages.filter(Files::isDirectory).toList()) {
                String gui = Files.readString(language.resolve("guis.yml"));
                for (String key : keys) assertTrue(gui.contains(key + ":"), language + " missing " + key);
            }
        }
    }

    @Test
    void destinationGiftWaitsUntilAfterInventoryRestore() throws Exception {
        String beta = Files.readString(MAIN.resolve("java/com/aegisguard/publicbeta/PublicBetaService.java"));
        assertTrue(beta.contains("public_beta_inventory_isolation_lore"));
        assertTrue(beta.contains("public_beta_guide_inventory_isolation"));
        assertTrue(beta.contains("plugin.runEntityLater(player"));
        assertTrue(beta.contains("giveComplimentaryWorldWand(player, worldKey)"));
    }

    @Test
    void ordinaryChatHasLocalizedWorldTagsWithoutReplacingPrivateChannels() throws Exception {
        String chat = Files.readString(MAIN.resolve(
                "java/com/aegisguard/publicbeta/PublicBetaChatListener.java"));
        assertTrue(chat.contains("EventPriority.HIGHEST"));
        assertTrue(chat.contains("ignoreCancelled = true"));
        assertTrue(chat.contains("event.setFormat(tag + event.getFormat())"));
        assertTrue(chat.contains("public_beta_chat_tag_hub"));
        assertTrue(chat.contains("public_beta_chat_tag_play"));
        assertTrue(chat.contains("public_beta_chat_tag_lab"));

        Map<String, Object> english = loadGui("modern_english");
        try (var languages = Files.list(MAIN.resolve("resources/lang"))) {
            for (Path language : languages.filter(Files::isDirectory).toList()) {
                Map<String, Object> gui = loadGui(language.getFileName().toString());
                for (String key : List.of("public_beta_chat_tag_hub", "public_beta_chat_tag_play",
                        "public_beta_chat_tag_lab")) {
                    assertTrue(gui.containsKey(key), language + " missing " + key);
                }
                assertTrue(gui.containsKey("public_beta_inventory_isolation_lore"));
                if (!language.getFileName().toString().equals("modern_english")) {
                    assertNotEquals(String.valueOf(english.get("public_beta_inventory_isolation_lore")),
                            String.valueOf(gui.get("public_beta_inventory_isolation_lore")),
                            language + " still has the English inventory warning");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadGui(String language) throws Exception {
        return new Yaml().load(Files.readString(MAIN.resolve("resources/lang")
                .resolve(language).resolve("guis.yml")));
    }
}
