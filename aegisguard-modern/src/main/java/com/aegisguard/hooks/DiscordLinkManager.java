package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Foundation for Discord account linking.
 *
 * <p>Stores mappings between Minecraft UUIDs and Discord user IDs. The current
 * implementation persists links to either {@code playerdata.yml} or the SQL
 * backend depending on config. A future release can add the actual Discord bot
 * side (JDA) and role synchronization on top of this manager.</p>
 */
public final class DiscordLinkManager {

    private final AegisGuard plugin;
    private final boolean enabled;
    private final String storageMode;
    private final Map<UUID, String> uuidToDiscord = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToUuid = new ConcurrentHashMap<>();
    private final File dataFile;

    public DiscordLinkManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("hooks.discord.linking.enabled", false);
        this.storageMode = plugin.getConfig().getString("hooks.discord.linking.storage", "playerdata");
        this.dataFile = new File(plugin.getDataFolder(), "discord_links.yml");
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Link a Minecraft account to a Discord ID.
     * @return true if the link was new or changed, false if it was already the same.
     */
    public boolean link(UUID minecraftId, String discordId) {
        if (!enabled || minecraftId == null || discordId == null || discordId.isBlank()) return false;
        String previous = uuidToDiscord.put(minecraftId, discordId);
        discordToUuid.put(discordId, minecraftId);
        if (discordId.equals(previous)) return false;
        if (previous != null) discordToUuid.remove(previous);
        save();
        return true;
    }

    /**
     * Remove the Discord link for a Minecraft account.
     * @return true if a link was removed.
     */
    public boolean unlink(UUID minecraftId) {
        if (!enabled || minecraftId == null) return false;
        String discordId = uuidToDiscord.remove(minecraftId);
        if (discordId != null) {
            discordToUuid.remove(discordId);
            save();
            return true;
        }
        return false;
    }

    public String getDiscordId(UUID minecraftId) {
        return minecraftId == null ? null : uuidToDiscord.get(minecraftId);
    }

    public UUID getMinecraftId(String discordId) {
        return discordId == null ? null : discordToUuid.get(discordId);
    }

    public void reload() {
        uuidToDiscord.clear();
        discordToUuid.clear();
        load();
    }

    private void load() {
        if (!enabled) return;
        if ("sql".equalsIgnoreCase(storageMode) && plugin.store() instanceof com.aegisguard.data.SQLDataStore) {
            // SQL-backed loading can be added here once a schema migration is prepared.
            return;
        }
        if (!dataFile.exists()) return;
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
            for (String key : data.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String discordId = data.getString(key);
                    if (discordId != null && !discordId.isBlank()) {
                        uuidToDiscord.put(uuid, discordId);
                        discordToUuid.put(discordId, uuid);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load Discord links", e);
        }
    }

    private void save() {
        if (!enabled) return;
        if ("sql".equalsIgnoreCase(storageMode)) {
            // SQL-backed saving can be added here once the schema is prepared.
            return;
        }
        try {
            FileConfiguration data = new YamlConfiguration();
            for (Map.Entry<UUID, String> entry : uuidToDiscord.entrySet()) {
                data.set(entry.getKey().toString(), entry.getValue());
            }
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save Discord links", e);
        }
    }
}
