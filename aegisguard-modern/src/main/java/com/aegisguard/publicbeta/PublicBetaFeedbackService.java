package com.aegisguard.publicbeta;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Private, instance-scoped Public Beta feedback inbox. */
public final class PublicBetaFeedbackService implements Listener {

    public static final String STAFF_PERMISSION = "aegis.admin.publicbeta.feedback";
    private static final String ROOT = "public-beta-mode";
    private static final int PAGE_SIZE = 45;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final long COOLDOWN_MILLIS = 30_000L;

    private final AegisGuard plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey reportKey;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSubmitted = new ConcurrentHashMap<>();
    private final boolean isolationValid;

    public PublicBetaFeedbackService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "public-beta/feedback-inbox.yml");
        this.data = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        this.reportKey = new NamespacedKey(plugin, "public_beta_feedback_id");
        String stored = data.getString("instance-id", "").trim();
        String configured = instanceId();
        this.isolationValid = stored.isEmpty() || stored.equals(configured);
        if (!isolationValid) {
            plugin.getLogger().severe("[Public Beta] Refusing feedback inbox from instance '" + stored
                    + "' on configured instance '" + configured + "'.");
        }
    }

    public boolean isEnabled() {
        return isolationValid && plugin.publicBeta() != null && plugin.publicBeta().isEnabled();
    }

    public void begin(Player player, Category category) {
        if (!canSubmit(player)) return;
        Category safe = category == null ? Category.FEEDBACK : category;
        Location at = player.getLocation();
        pending.put(player.getUniqueId(), new PendingInput(safe, at.getWorld() == null ? "unknown" : at.getWorld().getName(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        player.closeInventory();
        send(player, "public_beta_feedback_prompt", "&bType your {CATEGORY} in chat. Type &ccancel &bto stop.",
                Map.of("CATEGORY", categoryDisplay(player, safe)));
    }

    public void startOrSubmit(Player player, Category category, String message) {
        if (!canSubmit(player)) return;
        if (message == null || message.isBlank()) {
            begin(player, category);
            return;
        }
        Location at = player.getLocation();
        submit(player, category, message, at.getWorld() == null ? "unknown" : at.getWorld().getName(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    private boolean canSubmit(Player player) {
        if (player == null || !isEnabled() || !plugin.publicBeta().hasBetaPlayerRole(player.getUniqueId())) {
            if (player != null) send(player, "public_beta_feedback_unavailable",
                    "&cPublic Beta reporting is not available for this profile.", Map.of());
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        if (message.equalsIgnoreCase("cancel")) {
            plugin.runMain(event.getPlayer(), () -> send(event.getPlayer(), "public_beta_feedback_cancelled",
                    "&7Submission cancelled.", Map.of()));
            return;
        }
        plugin.runMain(event.getPlayer(), () -> submit(event.getPlayer(), input.category(), message,
                input.world(), input.x(), input.y(), input.z()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        deliverPendingNotifications(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        lastSubmitted.remove(id);
    }

    private void submit(Player player, Category category, String raw, String world, int x, int y, int z) {
        String message = raw == null ? "" : ChatColor.stripColor(raw.trim());
        if (message == null || message.length() < 3) {
            send(player, "public_beta_feedback_too_short", "&cPlease enter at least 3 characters.", Map.of());
            return;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            send(player, "public_beta_feedback_too_long", "&cPlease keep submissions to 500 characters or fewer.", Map.of());
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastSubmitted.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MILLIS) {
            send(player, "public_beta_feedback_cooldown", "&cPlease wait before sending another submission.", Map.of());
            return;
        }
        lastSubmitted.put(player.getUniqueId(), now);
        String id = UUID.randomUUID().toString();
        String base = "submissions." + id;
        synchronized (data) {
            data.set(base + ".category", category.name());
            data.set(base + ".status", Status.NEW.name());
            data.set(base + ".message", message);
            data.set(base + ".player-uuid", player.getUniqueId().toString());
            data.set(base + ".player-name", player.getName());
            data.set(base + ".created-at", now);
            data.set(base + ".world", world);
            data.set(base + ".x", x);
            data.set(base + ".y", y);
            data.set(base + ".z", z);
            data.set(base + ".plugin-version", plugin.getDescription().getVersion());
            data.set(base + ".server-version", Bukkit.getVersion());
            save();
        }
        send(player, "public_beta_feedback_submitted", "&aThank you! Your {CATEGORY} was sent privately to the staff inbox.",
                Map.of("CATEGORY", categoryDisplay(player, category)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(STAFF_PERMISSION) || plugin.isAdmin(online)) {
                send(online, "public_beta_feedback_staff_notice",
                        "&bNew Public Beta {CATEGORY} from &f{PLAYER}&b. Open &f/agadmin feedback&b.",
                        Map.of("CATEGORY", categoryDisplay(online, category), "PLAYER", player.getName()));
            }
        }
        openMySubmissions(player);
    }

    public void openInbox(Player player) {
        openInbox(player, 0);
    }

    public void openMySubmissions(Player player) {
        openMySubmissions(player, 0);
    }

    private void openMySubmissions(Player player, int requestedPage) {
        if (!canSubmit(player)) return;
        List<Entry> entries = entries(player.getUniqueId());
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        Inventory inventory = Bukkit.createInventory(new MySubmissionsHolder(page), 54,
                title(tr(player, "public_beta_my_submissions_title", "&bMy Public Beta Submissions")));
        ItemStack filler = GUIManager.getFiller();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            Entry entry = entries.get(index);
            ItemStack item = GUIManager.createItem(entry.category().material(),
                    color(entry.status().color() + categoryDisplay(player, entry.category()) + " &8- &f" + entry.playerName()),
                    entryLore(player, entry));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(reportKey, PersistentDataType.STRING, entry.id());
                item.setItemMeta(meta);
            }
            inventory.setItem(index - from, item);
        }
        if (entries.isEmpty()) inventory.setItem(22, GUIManager.createItem(Material.PAPER,
                color(tr(player, "public_beta_my_submissions_empty_name", "&7No submissions yet")),
                List.of(color(tr(player, "public_beta_my_submissions_empty_lore",
                        "&7Your reports, feedback, and suggestions will appear here.")))));
        if (page > 0) inventory.setItem(45, navItem(player, Material.ARROW,
                "public_beta_feedback_previous_name", "&aPrevious Page", "my_previous"));
        inventory.setItem(48, navItem(player, Material.ARROW,
                "public_beta_back_name", "&aBack", "my_back"));
        inventory.setItem(49, navItem(player, Material.BARRIER,
                "public_beta_feedback_close_name", "&cClose", "my_close"));
        if (page < maxPage) inventory.setItem(53, navItem(player, Material.ARROW,
                "public_beta_feedback_next_name", "&aNext Page", "my_next"));
        player.openInventory(inventory);
    }

    private void openInbox(Player player, int requestedPage) {
        if (player == null || (!player.hasPermission(STAFF_PERMISSION) && !plugin.isAdmin(player))) {
            if (player != null) plugin.msg().send(player, "no_perm");
            return;
        }
        List<Entry> entries = entries();
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        Inventory inventory = Bukkit.createInventory(new FeedbackInboxHolder(page), 54,
                title(tr(player, "public_beta_feedback_inbox_title", "&bPublic Beta Feedback Inbox")));
        ItemStack filler = GUIManager.getFiller();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            Entry entry = entries.get(index);
            ItemStack item = GUIManager.createItem(entry.category().material(),
                    color(entry.status().color() + categoryDisplay(player, entry.category()) + " &8- &f" + entry.playerName()),
                    entryLore(player, entry));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(reportKey, PersistentDataType.STRING, entry.id());
                item.setItemMeta(meta);
            }
            inventory.setItem(index - from, item);
        }
        if (page > 0) inventory.setItem(45, navItem(player, Material.ARROW,
                "public_beta_feedback_previous_name", "&aPrevious Page", "previous"));
        inventory.setItem(48, navItem(player, Material.ARROW,
                "public_beta_back_name", "&aBack", "back_admin"));
        inventory.setItem(49, navItem(player, Material.BARRIER,
                "public_beta_feedback_close_name", "&cClose", "close"));
        if (page < maxPage) inventory.setItem(53, navItem(player, Material.ARROW,
                "public_beta_feedback_next_name", "&aNext Page", "next"));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInboxClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FeedbackInboxHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(event.getCurrentItem());
        if ("close".equals(action)) { player.closeInventory(); return; }
        if ("back_admin".equals(action)) {
            plugin.gui().admin().open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if ("previous".equals(action)) { openInbox(player, holder.page() - 1); return; }
        if ("next".equals(action)) { openInbox(player, holder.page() + 1); return; }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(reportKey, PersistentDataType.STRING);
        Entry entry = entry(id);
        if (entry == null) return;
        if (event.isRightClick()) {
            Status next = entry.status().next();
            synchronized (data) {
                data.set("submissions." + id + ".status", next.name());
                data.set("submissions." + id + ".updated-at", System.currentTimeMillis());
                data.set("submissions." + id + ".updated-by", player.getName());
                data.set("submissions." + id + ".notification-pending", true);
                data.set("submissions." + id + ".notification-status", next.name());
                save();
            }
            send(player, "public_beta_feedback_status_changed", "&aSubmission status changed to {STATUS}.",
                    Map.of("STATUS", statusDisplay(player, next)));
            Player submitter = Bukkit.getPlayer(entry.playerUuid());
            if (submitter != null) deliverPendingNotifications(submitter);
            openInbox(player, holder.page());
            return;
        }
        openSubmissionDetail(player, entry, holder.page(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMySubmissionsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MySubmissionsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(event.getCurrentItem());
        if ("my_close".equals(action)) { player.closeInventory(); return; }
        if ("my_back".equals(action)) { plugin.publicBeta().openBetaMenu(player); return; }
        if ("my_previous".equals(action)) { openMySubmissions(player, holder.page() - 1); return; }
        if ("my_next".equals(action)) { openMySubmissions(player, holder.page() + 1); return; }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(reportKey, PersistentDataType.STRING);
        Entry entry = entry(id);
        if (entry == null || !entry.playerUuid().equals(player.getUniqueId())) return;
        openSubmissionDetail(player, entry, holder.page(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSubmissionDetailClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SubmissionDetailHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(event.getCurrentItem());
        if ("detail_exit".equals(action)) { player.closeInventory(); return; }
        if ("detail_back".equals(action)) {
            if (holder.staffView()) openInbox(player, holder.page());
            else openMySubmissions(player, holder.page());
        }
    }

    private void openSubmissionDetail(Player player, Entry entry, int page, boolean staffView) {
        if (player == null || entry == null) return;
        if (staffView) {
            if (!player.hasPermission(STAFF_PERMISSION) && !plugin.isAdmin(player)) return;
        } else if (!entry.playerUuid().equals(player.getUniqueId())) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                new SubmissionDetailHolder(entry.id(), page, staffView), 27,
                title(tr(player, "public_beta_feedback_detail_title", "&bSubmission Details")));
        ItemStack filler = GUIManager.getFiller();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + tr(player, "public_beta_feedback_status_label", "Status") + ": "
                + entry.status().color() + statusDisplay(player, entry.status())));
        if (staffView) {
            lore.add(color("&7" + tr(player, "public_beta_feedback_from_label", "From") + ": &f" + entry.playerName()));
            lore.add(color("&7" + tr(player, "public_beta_feedback_location_label", "Location") + ": &f"
                    + entry.world() + " " + entry.x() + ", " + entry.y() + ", " + entry.z()));
        }
        lore.add(" ");
        for (String line : wrap(entry.message(), 42)) lore.add(color("&f" + line));
        inventory.setItem(13, GUIManager.createItem(entry.category().material(),
                color("&b" + categoryDisplay(player, entry.category())), lore));
        inventory.setItem(18, navItem(player, Material.ARROW,
                "public_beta_back_name", "&aBack", "detail_back"));
        inventory.setItem(26, navItem(player, Material.BARRIER,
                "public_beta_exit_name", "&cExit", "detail_exit"));
        player.openInventory(inventory);
    }

    private List<String> entryLore(Player player, Entry entry) {
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + tr(player, "public_beta_feedback_status_label", "Status") + ": "
                + entry.status().color() + statusDisplay(player, entry.status())));
        lore.add(color("&7" + tr(player, "public_beta_feedback_world_label", "World") + ": &f"
                + entry.world() + " &8(" + entry.x() + ", " + entry.y() + ", " + entry.z() + ")"));
        lore.add(" ");
        for (String line : wrap(entry.message(), 38)) lore.add(color("&f" + line));
        lore.add(" ");
        lore.add(color(tr(player, "public_beta_feedback_read_lore", "&eLeft-click to read in chat.")));
        lore.add(color(tr(player, "public_beta_feedback_cycle_lore", "&bRight-click to change status.")));
        return lore;
    }

    private List<Entry> entries() {
        ConfigurationSection section = data.getConfigurationSection("submissions");
        if (section == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            Entry entry = entry(id);
            if (entry != null) entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(Entry::createdAt).reversed());
        return entries;
    }

    private List<Entry> entries(UUID playerUuid) {
        if (playerUuid == null) return List.of();
        List<Entry> own = new ArrayList<>();
        for (Entry entry : entries()) {
            if (playerUuid.equals(entry.playerUuid())) own.add(entry);
        }
        return own;
    }

    private Entry entry(String id) {
        if (id == null || !data.isConfigurationSection("submissions." + id)) return null;
        String base = "submissions." + id;
        Category category = Category.parse(data.getString(base + ".category"));
        Status status = Status.parse(data.getString(base + ".status"));
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(data.getString(base + ".player-uuid", ""));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        return new Entry(id, category, status, data.getString(base + ".message", ""), playerUuid,
                data.getString(base + ".player-name", "Unknown"), data.getLong(base + ".created-at"),
                data.getString(base + ".world", "unknown"), data.getInt(base + ".x"),
                data.getInt(base + ".y"), data.getInt(base + ".z"));
    }

    private void deliverPendingNotifications(Player player) {
        if (player == null || !player.isOnline() || !isEnabled()) return;
        List<PendingNotification> notifications = new ArrayList<>();
        synchronized (data) {
            ConfigurationSection section = data.getConfigurationSection("submissions");
            if (section == null) return;
            for (String id : section.getKeys(false)) {
                String base = "submissions." + id;
                if (!data.getBoolean(base + ".notification-pending", false)) continue;
                if (!player.getUniqueId().toString().equals(data.getString(base + ".player-uuid", ""))) continue;
                notifications.add(new PendingNotification(id,
                        Category.parse(data.getString(base + ".category")),
                        Status.parse(data.getString(base + ".notification-status"))));
            }
        }
        for (PendingNotification notification : notifications) {
            String key = notification.status() == Status.ACKNOWLEDGED
                    ? "public_beta_feedback_player_acknowledged"
                    : "public_beta_feedback_player_status";
            String fallback = notification.status() == Status.ACKNOWLEDGED
                    ? "&aStaff has read and noted your {CATEGORY}. It will be considered for a future update if action is needed."
                    : "&aStaff updated your {CATEGORY}. Status: {STATUS}.";
            send(player, key, fallback, Map.of(
                    "CATEGORY", categoryDisplay(player, notification.category()),
                    "STATUS", statusDisplay(player, notification.status())));
            synchronized (data) {
                String base = "submissions." + notification.id();
                if (data.getBoolean(base + ".notification-pending", false)
                        && notification.status().name().equals(data.getString(base + ".notification-status"))) {
                    data.set(base + ".notification-pending", false);
                    data.set(base + ".player-notified-at", System.currentTimeMillis());
                    save();
                }
            }
        }
    }

    public synchronized void save() {
        if (!isolationValid) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
            data.set("instance-id", instanceId());
            File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save Public Beta feedback inbox.", error);
        }
    }

    private ItemStack navItem(Player player, Material material, String key, String fallback, String action) {
        ItemStack item = GUIManager.createItem(material, color(tr(player, key, fallback)), List.of());
        plugin.gui().tagAction(item, action);
        return item;
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private String instanceId() {
        return plugin.getConfig().getString(ROOT + ".isolation.instance-id", "aegisguard-public-beta").trim();
    }

    private String tr(Player player, String key, String fallback) {
        String value = plugin.gui().tr(player, key, fallback);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private String categoryDisplay(Player player, Category category) {
        return switch (category) {
            case PROBLEM -> tr(player, "public_beta_category_problem", "problem report");
            case FEEDBACK -> tr(player, "public_beta_category_feedback", "general feedback");
            case SUGGESTION -> tr(player, "public_beta_category_suggestion", "feature suggestion");
        };
    }

    private String statusDisplay(Player player, Status status) {
        return switch (status) {
            case NEW -> tr(player, "public_beta_status_new", "New");
            case ACKNOWLEDGED -> tr(player, "public_beta_status_acknowledged", "Acknowledged");
            case REVIEWING -> tr(player, "public_beta_status_reviewing", "Reviewing");
            case RESOLVED -> tr(player, "public_beta_status_resolved", "Resolved");
            case DISMISSED -> tr(player, "public_beta_status_dismissed", "Dismissed");
        };
    }

    private void send(Player player, String key, String fallback, Map<String, String> replacements) {
        String message = tr(player, key, fallback);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        player.sendMessage(color(message));
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private String title(String value) {
        String colored = color(value);
        return colored.length() <= 32 ? colored : colored.substring(0, 32);
    }

    public enum Category {
        PROBLEM("Report a Problem", Material.REDSTONE),
        FEEDBACK("General Feedback", Material.WRITABLE_BOOK),
        SUGGESTION("Feature Suggestion", Material.LIGHT_BLUE_DYE);

        private final String display;
        private final Material material;
        Category(String display, Material material) { this.display = display; this.material = material; }
        public String display() { return display; }
        public Material material() { return material; }
        static Category parse(String value) {
            try { return valueOf(value == null ? "FEEDBACK" : value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return FEEDBACK; }
        }
    }

    private enum Status {
        NEW("New", "&a"), ACKNOWLEDGED("Acknowledged", "&2"), REVIEWING("Reviewing", "&e"),
        RESOLVED("Resolved", "&b"), DISMISSED("Dismissed", "&8");
        private final String display;
        private final String color;
        Status(String display, String color) { this.display = display; this.color = color; }
        String display() { return display; }
        String color() { return color; }
        Status next() { return values()[(ordinal() + 1) % values().length]; }
        static Status parse(String value) {
            try { return valueOf(value == null ? "NEW" : value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return NEW; }
        }
    }

    private record PendingInput(Category category, String world, int x, int y, int z) { }
    private record Entry(String id, Category category, Status status, String message, UUID playerUuid, String playerName,
                         long createdAt, String world, int x, int y, int z) { }
    private record PendingNotification(String id, Category category, Status status) { }
    public record FeedbackInboxHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record MySubmissionsHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public record SubmissionDetailHolder(String id, int page, boolean staffView) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
