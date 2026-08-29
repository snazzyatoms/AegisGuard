package com.aegisguard;

import com.aegisguard.chat.PlotChatService;
import com.aegisguard.data.Plot;
import com.aegisguard.visualization.VisualPresence;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotChatContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    @SuppressWarnings("unchecked")
    void configShipsPlotChatAndVisualPresenceOnByDefault() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(in);
        }
        assertEquals(1302, ((Number) config.get("config_schema")).intValue());
        Map<String, Object> modules = (Map<String, Object>) config.get("modules");
        assertEquals(Boolean.TRUE, modules.get("plot_chat"));
        assertEquals(Boolean.TRUE, modules.get("visual_presence"));
        Map<String, Object> chat = (Map<String, Object>) config.get("plot_chat");
        assertEquals(Boolean.TRUE, chat.get("enabled"));
        assertEquals(256, ((Number) chat.get("max_message_length")).intValue());
        Map<String, Object> presence = (Map<String, Object>) config.get("visual_presence");
        assertEquals(Boolean.TRUE, presence.get("holographic_entry"));
        assertEquals(Boolean.TRUE, presence.get("smart_borders"));
        assertEquals(3, ((Number) presence.get("border_label_distance")).intValue());
    }

    @Test
    void frequencyMembersExcludeVisitorsAndGuests() {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Keep", "world", 0, 0, 20, 20);
        plot.setRole(builder, "builder");
        plot.setRole(visitor, "visitor");
        assertTrue(PlotChatService.isFrequencyMember(plot, owner));
        assertTrue(PlotChatService.isFrequencyMember(plot, builder));
        assertFalse(PlotChatService.isFrequencyMember(plot, visitor));
        assertEquals(2, PlotChatService.frequencyMemberIds(plot).size());
    }

    @Test
    void frequencyIsWiredThroughCommandsMenusAndChatListener() throws Exception {
        String plugin = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("new com.aegisguard.chat.PlotChatService(this)"));
        assertTrue(plugin.contains("PlotChatListener"));
        String command = Files.readString(JAVA.resolve("commands/AegisCommand.java"));
        assertTrue(command.contains("handlePlotChat"));
        assertTrue(command.contains("\"chat\""));
        String modules = Files.readString(JAVA.resolve("config/Modules.java"));
        assertTrue(modules.contains("PLOT_CHAT"));
        assertTrue(modules.contains("case \"chat\", \"frequency\""));
        String gui = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(gui.contains("showPlotChat"));
        assertTrue(gui.contains("button_plot_chat"));
        String greeting = Files.readString(JAVA.resolve("listeners/PlotGreetingListener.java"));
        assertTrue(greeting.contains("VisualPresence.showEntry"));
        String visualizer = Files.readString(JAVA.resolve("visualization/PlotVisualizerTask.java"));
        assertTrue(visualizer.contains("VisualPresence.showBorderLabel"));
    }

    @Test
    void smartBorderPicksTheNearestCardinalWithinReach() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Keep", "world", 0, 0, 40, 40);
        assertEquals("west", VisualPresence.nearestCardinal(0, 20, plot, 3));
        assertEquals("east", VisualPresence.nearestCardinal(40, 20, plot, 3));
        assertEquals("north", VisualPresence.nearestCardinal(20, 0, plot, 3));
        assertEquals("south", VisualPresence.nearestCardinal(20, 40, plot, 3));
        assertEquals(null, VisualPresence.nearestCardinal(20, 20, plot, 3));
    }
}
