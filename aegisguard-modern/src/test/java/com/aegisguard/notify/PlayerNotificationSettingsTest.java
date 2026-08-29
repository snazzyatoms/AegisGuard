package com.aegisguard.notify;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 5 - plugin-light unit tests for the new guidance-related player preference flags.
 * Uses Bukkit's YamlConfiguration only as an in-memory ConfigurationSection stand-in.
 */
class PlayerNotificationSettingsTest {

    @Test
    void defaultsKeepExistingBehaviourAndLeaveWalkthroughUnseen() {
        PlayerNotificationSettings settings = new PlayerNotificationSettings(UUID.randomUUID());

        assertTrue(settings.isGreetingsEnabled());
        assertTrue(settings.isAdminUpdatesEnabled());
        assertTrue(settings.isRepeatNotificationsEnabled());
        assertFalse(settings.isWalkthroughSeen());
        assertEquals(NotificationMode.ACTION_BAR, settings.getMode());
        assertTrue(settings.isGuestPassNotificationsEnabled());
        assertTrue(settings.isAllianceNotificationsEnabled());
        assertTrue(settings.isLockdownNotificationsEnabled());
        assertTrue(settings.isTravelNotificationsEnabled());
        assertTrue(settings.isPlotNoticeNotificationsEnabled());
    }

    @Test
    void toggleRepeatNotificationsFlipsAndReturnsTheNewState() {
        PlayerNotificationSettings settings = new PlayerNotificationSettings(UUID.randomUUID());

        assertFalse(settings.toggleRepeatNotifications());
        assertFalse(settings.isRepeatNotificationsEnabled());
        assertTrue(settings.toggleRepeatNotifications());
        assertTrue(settings.isRepeatNotificationsEnabled());
    }

    @Test
    void serializeAndDeserializeRoundTripsTheNewGuidanceFlags() {
        UUID id = UUID.randomUUID();
        PlayerNotificationSettings source = new PlayerNotificationSettings(id);
        source.setRepeatNotifications(false);
        source.setWalkthroughSeen(true);
        source.setTravelNotifications(false);
        source.setGuestPassNotifications(false);

        YamlConfiguration yaml = new YamlConfiguration();
        source.serialize(yaml.createSection("player"));

        PlayerNotificationSettings restored = new PlayerNotificationSettings(id, yaml.getConfigurationSection("player"));
        assertFalse(restored.isRepeatNotificationsEnabled());
        assertTrue(restored.isWalkthroughSeen());
        assertTrue(restored.isGreetingsEnabled());
        assertFalse(restored.isTravelNotificationsEnabled());
        assertFalse(restored.isGuestPassNotificationsEnabled());
        assertTrue(restored.isAllianceNotificationsEnabled());
        assertEquals(NotificationMode.ACTION_BAR, restored.getMode());
        assertEquals(PlayerNotificationSettings.ArrivalPreference.OWNER_DEFAULT, restored.getPreferredArrival());
    }

    @Test
    void preferredArrivalRoundTripsAndCycles() {
        UUID id = UUID.randomUUID();
        PlayerNotificationSettings source = new PlayerNotificationSettings(id);
        assertEquals(PlayerNotificationSettings.ArrivalPreference.OWNER_DEFAULT, source.getPreferredArrival());
        assertEquals(PlayerNotificationSettings.ArrivalPreference.CLASSIC, source.cyclePreferredArrival());
        assertEquals(PlayerNotificationSettings.ArrivalPreference.BEACON, source.cyclePreferredArrival());

        YamlConfiguration yaml = new YamlConfiguration();
        source.serialize(yaml.createSection("player"));
        PlayerNotificationSettings restored = new PlayerNotificationSettings(id, yaml.getConfigurationSection("player"));
        assertEquals(PlayerNotificationSettings.ArrivalPreference.BEACON, restored.getPreferredArrival());
    }

    @Test
    void missingGuidanceKeysDefaultSafelyWhenLoadingOlderNotificationsFiles() {
        UUID id = UUID.randomUUID();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player.greetings", false);
        yaml.set("player.admin_updates", true);
        yaml.set("player.mode", "CHAT");

        PlayerNotificationSettings restored = new PlayerNotificationSettings(id, yaml.getConfigurationSection("player"));
        assertFalse(restored.isGreetingsEnabled());
        assertTrue(restored.isRepeatNotificationsEnabled(),
                "Older notifications.yml files without the new key must keep repeating denials");
        assertFalse(restored.isWalkthroughSeen(),
                "Older notifications.yml files must treat the walkthrough as unseen");
        assertEquals(NotificationMode.CHAT, restored.getMode());
    }
}
