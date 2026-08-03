package com.aegisguard.audit;

import com.aegisguard.AegisGuard;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * AuditService (Milestone 1 - Staff Audit Ledger)
 *
 * A single, central record of high-impact administrative actions: snapshot restores, Doctor
 * repairs, migrations, admin bypass toggles, ClaimBlock adjustments, and (from Milestone 2
 * onward) Guest Pass lifecycle events.
 *
 * Deliberately out of scope for this version: any external webhook/chat export of entries and
 * logging every ordinary player action. See {@link AuditCategory} for the exact recorded categories.
 *
 * The in-memory retention/filtering logic lives in the plugin-independent {@link AuditLedger}
 * (directly unit tested); this class is a thin plugin-integration layer handling config-driven
 * limits and YAML persistence ({@code plugins/AegisGuard/audit-log.yml}), following the same
 * idiom as {@code ExpansionRequestManager}'s decision history.
 */
public class AuditService {

    private final AegisGuard plugin;
    private final File file;
    private final AuditLedger ledger = new AuditLedger();
    private FileConfiguration data;

    private volatile boolean dirty = false;

    public AuditService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "audit-log.yml");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("audit.enabled", true);
    }

    private int maxEntries() {
        return Math.max(50, plugin.getConfig().getInt("audit.max_entries", 500));
    }

    private long retentionDays() {
        return Math.max(0L, plugin.getConfig().getLong("audit.retention_days", 90L));
    }

    private boolean logToConsole() {
        return plugin.getConfig().getBoolean("audit.log_to_console", true);
    }

    /**
     * Records an entry triggered by an online player (or {@code null} for a system/automatic
     * action, such as a future Guest Pass expiry).
     */
    public void record(AuditCategory category, Player actor, String target, String summary) {
        record(category, actor == null ? null : actor.getUniqueId(),
                actor == null ? "System" : actor.getName(), target, summary);
    }

    /** Records an entry with an explicit actor identity (used for system/automatic actions). */
    public void record(AuditCategory category, UUID actorId, String actorName, String target, String summary) {
        if (category == null || !isEnabled()) return;

        AuditEntry entry = new AuditEntry(UUID.randomUUID(), System.currentTimeMillis(),
                category, actorId, actorName, target, summary);
        ledger.add(entry);
        dirty = true;
        pruneIfNeeded();

        if (logToConsole()) {
            plugin.getLogger().info("[Audit] " + category + " by " + entry.getActorName()
                    + (entry.getTarget().isBlank() ? "" : " on " + entry.getTarget())
                    + ": " + entry.getSummary());
        }
    }

    /** All recorded entries, newest first. */
    public List<AuditEntry> recent(int limit) {
        return ledger.recent(null, limit);
    }

    /**
     * Entries matching {@code category} (or every entry when {@code category} is {@code null}),
     * newest first. {@code limit <= 0} returns every matching entry.
     */
    public List<AuditEntry> recent(AuditCategory category, int limit) {
        return ledger.recent(category, limit);
    }

    public int size() {
        return ledger.size();
    }

    private void pruneIfNeeded() {
        long days = retentionDays();
        long maxAgeMillis = days <= 0 ? 0L : days * 86_400_000L;
        ledger.prune(maxEntries(), maxAgeMillis, System.currentTimeMillis());
    }

    // --- Persistence ---

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public void saveSync() { save(); }

    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create audit-log.yml", e);
        }

        data = YamlConfiguration.loadConfiguration(file);
        List<AuditEntry> loaded = new ArrayList<>();

        if (data.isConfigurationSection("entries")) {
            for (String key : data.getConfigurationSection("entries").getKeys(false)) {
                try {
                    String path = "entries." + key;

                    UUID id = UUID.fromString(key);
                    long timestamp = data.getLong(path + ".timestamp");
                    AuditCategory category = AuditCategory.valueOf(
                            data.getString(path + ".category", AuditCategory.ADMIN_BYPASS.name())
                                    .toUpperCase(Locale.ROOT));

                    String actorIdStr = data.getString(path + ".actorId", "");
                    UUID actorId = actorIdStr.isBlank() ? null : UUID.fromString(actorIdStr);
                    String actorName = data.getString(path + ".actorName", "System");
                    String target = data.getString(path + ".target", "");
                    String summary = data.getString(path + ".summary", "");

                    loaded.add(new AuditEntry(id, timestamp, category, actorId, actorName, target, summary));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Skipping invalid audit entry: " + key, ex);
                }
            }
        }

        loaded.sort(Comparator.comparingLong(AuditEntry::getTimestamp));
        ledger.replaceAll(loaded);

        pruneIfNeeded();
        dirty = false;
    }

    public synchronized void save() {
        if (data == null) return;

        data.set("entries", null);
        for (AuditEntry entry : ledger.all()) {
            String path = "entries." + entry.getId();
            data.set(path + ".timestamp", entry.getTimestamp());
            data.set(path + ".category", entry.getCategory().name());
            data.set(path + ".actorId", entry.getActorId() == null ? "" : entry.getActorId().toString());
            data.set(path + ".actorName", entry.getActorName());
            data.set(path + ".target", entry.getTarget());
            data.set(path + ".summary", entry.getSummary());
        }

        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save audit-log.yml", e);
        }
    }
}
