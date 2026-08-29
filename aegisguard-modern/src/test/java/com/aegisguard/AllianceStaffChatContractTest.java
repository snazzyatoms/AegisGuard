package com.aegisguard;

import com.aegisguard.alliance.Alliance;
import com.aegisguard.chat.PlotChatService;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllianceStaffChatContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @SuppressWarnings("unchecked")
    void schemaShipsAllianceAndStaffChatOnByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(RESOURCES.resolve("config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1310, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> alliance = (Map<String, Object>) config.get("alliance_chat");
        Map<String, Object> group = (Map<String, Object>) config.get("group_chat");
        Map<String, Object> staff = (Map<String, Object>) config.get("staff_chat");
        assertEquals(Boolean.TRUE, alliance.get("enabled"));
        assertEquals(Boolean.TRUE, group.get("enabled"));
        assertEquals(Boolean.TRUE, staff.get("enabled"));
        String migration = Files.readString(JAVA.resolve("config/ConfigMigrationService.java"));
        assertTrue(migration.contains("CURRENT_SCHEMA = 1310"));
    }

    @Test
    void chatCommandIsWiredAndNotGatedOnPlotFrequencyAlone() throws Exception {
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("case \"chat\", \"frequency\""));
        assertTrue(command.contains("handlePlotChat"));
        assertTrue(command.contains("handleAllianceChat"));
        assertTrue(command.contains("handleGroupChat"));
        assertTrue(command.contains("handleStaffChat"));
        assertTrue(command.contains("case \"staff\", \"staffchat\""));
        assertTrue(command.contains("aegis.chat.alliance"));
        assertTrue(command.contains("aegis.chat.group"));
        assertTrue(command.contains("turnOffAlliance"));
        assertTrue(command.contains("turnOffGroup"));
        String modules = Files.readString(JAVA.resolve("config/Modules.java"));
        assertFalse(modules.contains("case \"chat\", \"frequency\" -> Id.PLOT_CHAT"));
        String admin = Files.readString(JAVA.resolve("admin/AdminCommand.java"));
        assertTrue(admin.contains("case \"staffchat\", \"sc\""));
        assertTrue(admin.contains("handleStaffChat"));
        String listener = Files.readString(JAVA.resolve("chat/PlotChatListener.java"));
        assertTrue(listener.contains("sendActive"));
        assertFalse(listener.contains("|| !chat.isEnabled()"));
        String pluginYml = Files.readString(RESOURCES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("aegis.chat.alliance"));
        assertTrue(pluginYml.contains("aegis.chat.group"));
        assertTrue(pluginYml.contains("aegis.admin.staffchat"));
    }

    @Test
    void allianceChatTitleFallsBackStripsColorAndCapsAt32() {
        UUID leader = UUID.randomUUID();
        Alliance alliance = Alliance.create("Knights", leader);
        assertEquals("Knights", alliance.getChatTitle());
        assertFalse(alliance.hasCustomChatTitle());
        alliance.setChatTitle("&cRed Radio Extra Long Title That Exceeds Thirty Two Characters!!");
        assertTrue(alliance.hasCustomChatTitle());
        assertFalse(alliance.getChatTitle().contains("&"));
        assertFalse(alliance.getChatTitle().contains("§"));
        assertTrue(alliance.getChatTitle().startsWith("Red Radio"));
        assertTrue(alliance.getChatTitle().length() <= Alliance.MAX_CHAT_TITLE);
        alliance.setChatTitle("   ");
        assertEquals("Knights", alliance.getChatTitle());
        assertNull(alliance.rawChatTitle());
    }

    @Test
    void channelEnumAndMutualExclusionHelpersExist() throws Exception {
        String service = Files.readString(JAVA.resolve("chat/PlotChatService.java"));
        assertTrue(service.contains("enum Channel { NONE, PLOT, ALLIANCE, GROUP, STAFF }"));
        assertTrue(service.contains("toggleAlliance"));
        assertTrue(service.contains("toggleGroup"));
        assertTrue(service.contains("toggleStaff"));
        assertTrue(service.contains("sendAlliance"));
        assertTrue(service.contains("sendGroup"));
        assertTrue(service.contains("sendStaff"));
        assertTrue(service.contains("sendActive"));
        assertTrue(service.contains("isAllianceEnabled"));
        assertTrue(service.contains("isStaffEnabled"));
        assertTrue(service.contains("canHearStaff"));
        assertTrue(service.contains("setChannel"));
        String manager = Files.readString(JAVA.resolve("alliance/AllianceManager.java"));
        assertTrue(manager.contains("chat-title"));
        assertTrue(manager.contains("setChatTitle"));
        assertEquals(PlotChatService.Channel.PLOT, PlotChatService.Channel.valueOf("PLOT"));
        assertEquals(PlotChatService.Channel.ALLIANCE, PlotChatService.Channel.valueOf("ALLIANCE"));
        assertEquals(PlotChatService.Channel.STAFF, PlotChatService.Channel.valueOf("STAFF"));
    }

    @Test
    void languagePacksShipAllianceAndStaffKeys() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/lang/modern_english/system.yml"));
        for (String key : new String[]{
                "alliance_chat_on:", "alliance_chat_off:", "alliance_chat_need:",
                "alliance_chat_disabled:", "alliance_chat_format:", "alliance_chat_no_listeners:",
                "alliance_chat_not_member:", "alliance_chat_renamed:", "alliance_chat_rename_usage:",
                "alliance_chat_rename_denied:", "alliance_chat_empty:",
                "group_chat_on:", "group_chat_off:", "group_chat_need:", "group_chat_disabled:",
                "group_chat_format:", "group_chat_no_listeners:", "group_chat_not_member:",
                "group_chat_renamed:", "group_chat_rename_usage:", "group_chat_rename_denied:",
                "group_chat_empty:",
                "staff_chat_on:", "staff_chat_off:", "staff_chat_disabled:", "staff_chat_denied:",
                "staff_chat_format:", "staff_chat_no_listeners:", "staff_chat_empty:"
        }) {
            assertTrue(english.contains(key), "missing " + key);
        }
        assertTrue(english.contains("{ALLIANCE}"));
        assertTrue(english.contains("{PLAYER}"));
        assertTrue(english.contains("{MESSAGE}"));
    }
}
