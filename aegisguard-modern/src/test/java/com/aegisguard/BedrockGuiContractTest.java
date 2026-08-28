package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockGuiContractTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void floodgateAndGeyserAreOptionalSoftDependsWithRuntimeDetection() throws Exception {
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        String clients = Files.readString(JAVA_ROOT.resolve("hooks/BedrockClients.java"));
        String clicks = Files.readString(JAVA_ROOT.resolve("gui/GuiClicks.java"));
        String main = Files.readString(JAVA_ROOT.resolve("AegisGuard.java"));
        String config = Files.readString(RESOURCES.resolve("config.yml"));

        assertTrue(pluginYml.contains("- floodgate"));
        assertTrue(pluginYml.contains("- Geyser-Spigot"));
        assertTrue(clients.contains("org.geysermc.floodgate.api.FloodgateApi"));
        assertTrue(clients.contains("org.geysermc.geyser.api.GeyserApi"));
        assertTrue(clients.contains("isFloodgatePlayer"));
        assertTrue(clicks.contains("BedrockClients.isBedrock"));
        assertTrue(clicks.contains("SWAP_OFFHAND"));
        assertTrue(main.contains("BedrockClients.bind"));
        assertTrue(config.contains("\ngui:"));
        assertTrue(config.contains("bedrock:"));
        assertTrue(config.contains("detect: true"));
    }
}
