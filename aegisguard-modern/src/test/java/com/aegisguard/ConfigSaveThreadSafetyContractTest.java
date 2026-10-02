package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSaveThreadSafetyContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void playerPreferenceAndSoundConfigSavesStayOffAsyncWorkers() throws Exception {
        String codex = Files.readString(JAVA.resolve("language/CodexEngine.java"));
        String sound = Files.readString(JAVA.resolve("gui/SettingsGUI.java"));

        assertFalse(codex.contains("runGlobalAsync(plugin::saveConfig)"),
                "Language selection must not serialize Bukkit config asynchronously");
        assertTrue(codex.contains("runMainGlobal(plugin::saveConfig)"));
        assertTrue(codex.contains("rebuildLeafMaps()"));
        assertTrue(codex.contains("lookupAnyLeaf("));
        assertFalse(sound.contains("runGlobalAsync(plugin::saveConfig)"),
                "Sound preferences must not serialize Bukkit config asynchronously");
        assertTrue(sound.contains("saveConfigSafe()"));
    }

    @Test
    void getPlayerStyleMustNotUpgradeReadLockToWriteLock() throws Exception {
        String codex = Files.readString(JAVA.resolve("language/CodexEngine.java"));

        int methodStart = codex.indexOf("public String getPlayerStyle(Player player)");
        int methodEnd = codex.indexOf("public void evictPlayerStyle", methodStart);
        assertTrue(methodStart > 0 && methodEnd > methodStart,
                "getPlayerStyle and evictPlayerStyle signatures must exist for lock-order audit");

        String body = codex.substring(methodStart, methodEnd);
        assertFalse(body.contains("writeLock().lock()"),
                "getPlayerStyle runs under tr()'s read lock; acquiring the write lock there "
                        + "self-deadlocks the server thread (read->write upgrade is unsupported)");
        assertTrue(body.contains("ConcurrentHashMap") || codex.contains("Map<UUID, String> playerStyles = new ConcurrentHashMap<>()"),
                "playerStyles must stay concurrent so the style cache can be populated under the read lock");
    }

    @Test
    void adminConfigSaveBlocksUseTheGlobalServerThread() throws Exception {
        String admin = Files.readString(JAVA.resolve("gui/AdminGUI.java"));

        assertFalse(admin.contains("plugin.runGlobalAsync(() -> {\n            try {\n                plugin.saveConfig();"),
                "Admin config must not be serialized by an async worker");
    }
}
