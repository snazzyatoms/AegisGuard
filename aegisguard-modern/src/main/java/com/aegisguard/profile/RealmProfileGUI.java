package com.aegisguard.profile;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone 4 (Realm Profiles and Noticeboards) player-facing GUI.
 *
 * Gives the existing plot name, description, discovery category, visibility, and entry
 * greeting - previously only reachable through separate chat commands - a single home, plus a
 * new short owner-moderated noticeboard for rules, event details, shop info, or announcements.
 *
 * Never replaces an existing plot name, description, or greeting automatically, and never
 * changes ownership, roles, or protection behavior.
 */
public class RealmProfileGUI {

    private final AegisGuard plugin;

    public RealmProfileGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class RealmProfileMenuHolder implements InventoryHolder {
        private final Plot plot;
        public RealmProfileMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class NoticeboardHolder implements InventoryHolder {
        private final Plot plot;
        private final List<PlotNotice> notices;
        public NoticeboardHolder(Plot plot, List<PlotNotice> notices) { this.plot = plot; this.notices = notices; }
        public Plot getPlot() { return plot; }
        public List<PlotNotice> getNotices() { return notices; }
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, vars);
        } catch (Throwable ignored) {}

        String out = (raw == null || raw.isBlank() || raw.equalsIgnoreCase(key))
                ? (fallback == null ? "" : fallback)
                : raw;

        if (vars != null && !vars.isEmpty()) {
            for (Map.Entry<String, String> en : vars.entrySet()) {
                String k = en.getKey();
                String v = en.getValue() == null ? "" : en.getValue();
                out = out.replace("{" + k + "}", v);
            }
        }
        return out;
    }

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    private boolean canManagePlot(Player actor, Plot plot) {
        return actor != null && plot != null && plot.canManage(actor, plugin);
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("realm_profiles.enabled", true);
    }

    private boolean noticeboardEnabled() {
        return plugin.getConfig().getBoolean("realm_profiles.noticeboard.enabled", true);
    }

    private int maxEntries() {
        return Math.max(1, plugin.getConfig().getInt("realm_profiles.noticeboard.max_entries", 8));
    }

    // --------------------------------------------------
    // OPEN
    // --------------------------------------------------

    public void open(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }
        openMenu(player, plot);
    }

    public void openMenu(Player player, Plot plot) {
        if (plot == null) { plugin.effects().playError(player); return; }

        boolean canManage = canManagePlot(player, plot);
        String title = plugin.gui().title(player, "realm_profile_menu_title", "&3Realm Profile");
        Inventory inv = Bukkit.createInventory(new RealmProfileMenuHolder(plot), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        String plotName = (plot.getPlotName() != null && !plot.getPlotName().isBlank())
                ? plot.getPlotName()
                : (plot.getOwnerName() == null ? "Plot" : plot.getOwnerName() + "'s Plot");
        String description = (plot.getDescription() != null && !plot.getDescription().isBlank())
                ? plot.getDescription()
                : null;

        List<String> headerLore = new ArrayList<>();
        headerLore.add(GUIManager.color(t(player, "realm_profile_name_line", Map.of("NAME", plotName), "&7Name: &f{NAME}")));
        if (description != null) {
            headerLore.add(GUIManager.color(t(player, "realm_profile_desc_line", Map.of("DESC", description), "&7\"{DESC}\"")));
        } else {
            headerLore.add(GUIManager.color(t(player, "realm_profile_no_desc", "&8No description set yet.")));
        }
        headerLore.add(" ");
        headerLore.add(GUIManager.color(t(player, "realm_profile_rename_hint", "&8Use /ag rename <name> to change this.")));
        headerLore.add(GUIManager.color(t(player, "realm_profile_desc_hint", "&8Use /ag setdesc <text> to change this.")));

        inv.setItem(4, GUIManager.createItem(Material.NAME_TAG,
                t(player, "realm_profile_header_name", "&3&lRealm Profile"), headerLore));

        TerritoryLifeService.DiscoveryMeta discovery = plugin.territoryLife().discovery(plot.getPlotId());
        String category = discovery.category();

        List<String> categoryLore = new ArrayList<>(tl(player, "realm_profile_category_lore",
                List.of("&7How this plot is grouped in", "&7the Discover browser.")));
        categoryLore.add(" ");
        categoryLore.add(GUIManager.color(canManage
                ? t(player, "realm_profile_category_click", "&eClick to cycle to the next category.")
                : t(player, "realm_profile_category_locked", "&8Only the owner can change this.")));
        inv.setItem(10, GUIManager.createItem(categoryIcon(category),
                t(player, "realm_profile_category_name", Map.of("CATEGORY", categoryLabel(player, category)),
                        "&6Category: &f{CATEGORY}"), categoryLore));

        boolean visible = discovery.visible();
        List<String> visibilityLore = new ArrayList<>(tl(player, visible
                        ? "realm_profile_visible_lore" : "realm_profile_hidden_lore",
                visible
                        ? List.of("&7This plot can appear in the", "&7public Discover browser.")
                        : List.of("&7This plot is hidden from the", "&7public Discover browser.")));
        visibilityLore.add(" ");
        visibilityLore.add(GUIManager.color(canManage
                ? t(player, "realm_profile_visibility_click", "&eClick to toggle visibility.")
                : t(player, "realm_profile_category_locked", "&8Only the owner can change this.")));
        inv.setItem(12, GUIManager.createItem(visible ? Material.SPYGLASS : Material.GRAY_DYE,
                t(player, visible ? "realm_profile_visible_name" : "realm_profile_hidden_name",
                        visible ? "&a&lPublicly Discoverable" : "&7&lHidden From Discovery"), visibilityLore));

        String welcome = plot.getWelcomeMessage();
        String entryTitle = plot.getEntryTitle();
        List<String> greetingLore = new ArrayList<>();
        if ((welcome == null || welcome.isBlank()) && (entryTitle == null || entryTitle.isBlank())) {
            greetingLore.add(GUIManager.color(t(player, "realm_profile_no_greeting", "&8No entry greeting set yet.")));
        } else {
            if (entryTitle != null && !entryTitle.isBlank()) {
                greetingLore.add(GUIManager.color(t(player, "realm_profile_entry_title_line",
                        Map.of("TITLE", entryTitle), "&7Title: &f{TITLE}")));
            }
            if (welcome != null && !welcome.isBlank()) {
                greetingLore.add(GUIManager.color(t(player, "realm_profile_welcome_line",
                        Map.of("MESSAGE", welcome), "&7Chat: &f{MESSAGE}")));
            }
        }
        greetingLore.add(" ");
        greetingLore.add(GUIManager.color(t(player, "realm_profile_greeting_hint",
                "&8Use /ag rename and /ag welcome to change this.")));
        inv.setItem(14, GUIManager.createItem(Material.OAK_SIGN,
                t(player, "realm_profile_greeting_name", "&bEntry Greeting"), greetingLore));

        int noticeCount = plot.getNoticeboard().size();
        List<String> noticeboardLore = new ArrayList<>(tl(player, "realm_profile_noticeboard_lore",
                List.of("&7Rules, event details, shop info,", "&7or announcements for visitors.")));
        noticeboardLore.add(" ");
        noticeboardLore.add(GUIManager.color(t(player, "realm_profile_noticeboard_count",
                Map.of("COUNT", String.valueOf(noticeCount), "MAX", String.valueOf(maxEntries())),
                "&7Notices: &f{COUNT}/{MAX}")));
        noticeboardLore.add(GUIManager.color(t(player, "realm_profile_noticeboard_click", "&eClick to open the noticeboard.")));
        inv.setItem(16, GUIManager.createItem(Material.WRITTEN_BOOK,
                t(player, "realm_profile_noticeboard_name", "&dNoticeboard"), noticeboardLore));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void openNoticeboard(Player player, Plot plot) {
        if (plot == null) { plugin.effects().playError(player); return; }

        List<PlotNotice> notices = plot.getNoticeboard();
        boolean canManage = canManagePlot(player, plot);

        String title = plugin.gui().title(player, "noticeboard_menu_title", "&dNoticeboard");
        Inventory inv = Bukkit.createInventory(new NoticeboardHolder(plot, notices), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        if (notices.isEmpty()) {
            inv.setItem(13, GUIManager.createItem(Material.BARRIER,
                    t(player, "noticeboard_none_title", "&7No Notices Yet"),
                    tl(player, "noticeboard_none_lore", List.of(
                            "&7This plot has not posted any", "&7rules, events, or announcements."))));
        } else {
            for (int i = 0; i < notices.size() && i < 8; i++) {
                PlotNotice notice = notices.get(i);
                inv.setItem(9 + i, buildNoticeItem(player, notice, canManage));
            }
        }

        List<String> hintLore = new ArrayList<>(tl(player, "noticeboard_add_hint_lore",
                List.of("&7Use /ag notice add <text> to post,", "&7and /ag notice remove <#> to remove.")));
        inv.setItem(4, GUIManager.createItem(Material.WRITTEN_BOOK,
                t(player, "noticeboard_header_name", "&d&lNoticeboard"), hintLore));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    private ItemStack buildNoticeItem(Player player, PlotNotice notice, boolean canManage) {
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(t(player, "noticeboard_entry_author_line",
                Map.of("PLAYER", notice.getAuthorName()), "&7Posted by: &f{PLAYER}")));
        lore.add(GUIManager.color(t(player, "noticeboard_entry_age_line",
                Map.of("AGE", formatAge(player, notice.getAgeMillis())), "&7When: &f{AGE} ago")));
        lore.add(" ");
        lore.add(GUIManager.color(notice.getText()));
        if (canManage) {
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "noticeboard_entry_remove_hint",
                    "&cClick to remove this notice.")));
        }
        return GUIManager.createItem(Material.PAPER,
                t(player, "noticeboard_entry_name", "&e📌 Notice"), lore);
    }

    private String formatAge(Player player, long ageMillis) {
        long minutes = Math.max(0L, ageMillis / 60_000L);
        if (minutes < 1) return t(player, "guest_pass_expiring_now", "<1m");
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        if (days > 0) return t(player, "guest_pass_duration_days", Map.of("DAYS", String.valueOf(days)), "{DAYS}d");
        if (hours > 0) return t(player, "guest_pass_duration_hours_minutes",
                Map.of("HOURS", String.valueOf(hours), "MINUTES", String.valueOf(mins)), "{HOURS}h {MINUTES}m");
        return t(player, "guest_pass_duration_minutes", Map.of("MINUTES", String.valueOf(mins)), "{MINUTES}m");
    }

    private Material categoryIcon(String category) {
        if (category == null) return Material.COMPASS;
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "build" -> Material.BRICKS;
            case "shop" -> Material.EMERALD;
            case "town" -> Material.OAK_DOOR;
            case "farm" -> Material.WHEAT;
            case "event" -> Material.FIREWORK_ROCKET;
            default -> Material.COMPASS;
        };
    }

    private String categoryLabel(Player player, String category) {
        if (category == null || category.isBlank()) category = "other";
        String key = "realm_profile_category_" + category.toLowerCase(Locale.ROOT);
        String fallback = category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1);
        return t(player, key, fallback);
    }

    // --------------------------------------------------
    // CLICK HANDLERS
    // --------------------------------------------------

    public void handleMenuClick(Player player, InventoryClickEvent e, RealmProfileMenuHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        int slot = e.getRawSlot();

        if (slot == 18) { plugin.gui().openMain(player); return; }
        if (slot == 20) { player.closeInventory(); return; }

        boolean canManage = canManagePlot(player, plot);

        if (slot == 10) {
            if (!canManage) { plugin.effects().playError(player); return; }
            List<String> categories = plugin.getConfig().getStringList("plot_discovery.categories");
            if (categories.isEmpty()) { plugin.effects().playError(player); return; }

            String current = plugin.territoryLife().discovery(plot.getPlotId()).category();
            int index = categories.indexOf(current);
            String next = categories.get((index + 1) % categories.size());
            plugin.territoryLife().setCategory(plot.getPlotId(), next);
            plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "DISCOVERY_CATEGORY",
                    "activity_detail_discovery_category",
                    "Discovery category changed to " + next + ".",
                    java.util.Map.of("CATEGORY", next == null ? "" : next));
            plugin.effects().playMenuFlip(player);
            openMenu(player, plot);
            return;
        }

        if (slot == 12) {
            if (!canManage) { plugin.effects().playError(player); return; }
            boolean visible = plugin.territoryLife().discovery(plot.getPlotId()).visible();
            plugin.territoryLife().setVisible(plot.getPlotId(), !visible);
            String visibility = !visible ? "public" : "private";
            plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "DISCOVERY_VISIBILITY",
                    "activity_detail_discovery_visibility",
                    "Discovery visibility changed to " + visibility + ".",
                    java.util.Map.of("VISIBILITY", visibility));
            plugin.effects().playMenuFlip(player);
            openMenu(player, plot);
            return;
        }

        if (slot == 16) {
            if (!noticeboardEnabled()) { plugin.effects().playError(player); return; }
            openNoticeboard(player, plot);
        }
    }

    public void handleNoticeboardClick(Player player, InventoryClickEvent e, NoticeboardHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        int slot = e.getRawSlot();

        if (slot == 18) { openMenu(player, plot); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot < 9 || slot > 16) return;
        int index = slot - 9;
        List<PlotNotice> notices = holder.getNotices();
        if (index < 0 || index >= notices.size()) return;

        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

        PlotNotice notice = notices.get(index);
        plot.removeNotice(notice.getId());
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        plugin.territoryLife().logKey(plot.getPlotId(), player.getUniqueId(), "NOTICE_REMOVED",
                "activity_detail_notice_removed", "Removed a noticeboard notice.", java.util.Map.of());

        plugin.msg().send(player, "notice_removed");
        plugin.effects().playConfirm(player);
        openNoticeboard(player, plot);
    }
}
