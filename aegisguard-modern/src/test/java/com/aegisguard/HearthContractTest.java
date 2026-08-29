package com.aegisguard;

import com.aegisguard.chat.HearthService;
import com.aegisguard.data.Plot;
import com.aegisguard.protection.ProtectionPreset;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsHearthOffByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1309, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> protections = (Map<String, Object>) config.get("protections");
        assertEquals(Boolean.FALSE, protections.get("hearth"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1309"));
    }

    @Test
    void roomsIsolateHouseFromYardAndStreet() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Keep", "world", 0, 0, 40, 40);
        plot.setFlag("hearth", true);

        HearthService.Room house = HearthService.roomOf(plot, "House");
        HearthService.Room yard = HearthService.roomOf(plot, null);
        assertNotNull(house);
        assertNotNull(yard);
        assertNotEquals(house, yard);
        assertTrue(house.isZone());
        assertFalse(yard.isZone());

        assertTrue(HearthService.canHear(house, house, false));
        assertFalse(HearthService.canHear(house, yard, false));
        assertFalse(HearthService.canHear(yard, house, false));
        assertFalse(HearthService.canHear(house, null, false));
        assertFalse(HearthService.canHear(null, house, false));
        assertTrue(HearthService.canHear(null, null, false));
        assertTrue(HearthService.canHear(house, yard, true));
    }

    @Test
    void arenaPitAndLobbyDoNotHearEachOther() {
        Plot plot = new Plot(UUID.randomUUID(), Plot.SERVER_OWNER_UUID, "Server", "world", 0, 0, 80, 80);
        plot.setFlag("hearth", true);
        HearthService.Room pit = HearthService.roomOf(plot, "Pit");
        HearthService.Room lobby = HearthService.roomOf(plot, "Lobby");
        assertFalse(HearthService.canHear(pit, lobby, false));
        assertFalse(HearthService.canHear(lobby, pit, false));
        assertTrue(HearthService.canHear(pit, pit, false));
        assertTrue(HearthService.canHear(lobby, lobby, false));
    }

    @Test
    void hearthOffMeansNoRoom() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Keep", "world", 0, 0, 20, 20);
        assertNull(HearthService.roomOf(plot, "House"));
        assertTrue(HearthService.canHear(null, null, false));
    }

    @Test
    void flagIsWiredAndPresetsNeverWriteIt() throws Exception {
        String flags = Files.readString(JAVA.resolve("gui/PlotFlagsGUI.java"));
        assertTrue(flags.contains("\"hearth\""));
        assertTrue(flags.contains("hearth_toggle_lore"));
        assertTrue(flags.contains("case 23"));
        String listener = Files.readString(JAVA.resolve("chat/HearthListener.java"));
        assertTrue(listener.contains("AsyncPlayerChatEvent"));
        assertTrue(listener.contains("interceptPublicChat"));
        assertTrue(listener.contains("cachedRoom"));
        assertFalse(listener.contains("hearth.roomOf(speaker)"));
        String service = Files.readString(JAVA.resolve("chat/HearthService.java"));
        assertTrue(service.contains("cachedRoom"));
        assertTrue(service.contains("updatePresenceAt"));
        String pluginMain = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(pluginMain.contains("HearthService"));
        assertTrue(pluginMain.contains("HearthListener"));
        String perms = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(perms.contains("aegis.admin.hearth"));
        String englishGuis = Files.readString(RESOURCES.resolve("lang/modern_english/guis.yml"));
        assertTrue(englishGuis.contains("hearth_toggle_lore:"));
        assertTrue(englishGuis.contains("button_hearth_on:"));
        assertTrue(englishGuis.contains("button_keep_health_on:"));
        for (ProtectionPreset preset : ProtectionPreset.values()) {
            assertFalse(preset.flagBundle().containsKey("hearth"));
        }
    }
}
