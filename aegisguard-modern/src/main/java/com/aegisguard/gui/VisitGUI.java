package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.travel.SafeTravelResult;
import com.aegisguard.travel.SafeTravelService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * VisitGUI (1.2.6 QoL pass)
 * - PDC action routing (aegis_action) for prev/next/back/close/toggle + visit entries.
 * - Visit entries store plot id in PDC (aegis_plot_id) to prevent index desync.
 * - Async list build/sort; inventory build on main thread.
 * - Full inventory fill (no dead holes).
 * - Strict top-inventory click handling + ignore filler clicks.
 * - Keeps entries at 0-44 and provides direct footer tabs for every travel mode.
 */
public class VisitGUI {

    private final AegisGuard plugin;
    /** Four content rows; row 5 holds Help / Recent tools so footer can keep Back/Exit. */
    private static final int PLOTS_PER_PAGE = 36;

    private final NamespacedKey keyAction;
    private final NamespacedKey keyPlotId;

    public VisitGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
        this.keyPlotId = new NamespacedKey(plugin, "aegis_plot_id");
    }

    public enum VisitMode {
        TRUSTED,
        OWNED,
        WARPS,
        DISCOVER,
        FAVORITES,
        RECENT;

        public VisitMode next() {
            return switch (this) {
                case TRUSTED -> OWNED;
                case OWNED -> WARPS;
                case WARPS -> DISCOVER;
                case DISCOVER -> FAVORITES;
                case FAVORITES -> RECENT;
                case RECENT -> TRUSTED;
            };
        }
    }

    /** Narrow the public discovery atlas without changing the other travel modes. */
    public enum DiscoverFilter {
        ALL, FEATURED, FOR_SALE, FOR_RENT, CATEGORY;

        public DiscoverFilter next() {
            return switch (this) {
                case ALL -> FEATURED;
                case FEATURED -> FOR_SALE;
                case FOR_SALE -> FOR_RENT;
                case FOR_RENT -> CATEGORY;
                case CATEGORY -> ALL;
            };
        }
    }

    /** Top-level Travel Atlas tabs. Destinations keeps the existing VisitMode footer. */
    public enum AtlasTab {
        DESTINATIONS, MY_BEACONS, ARRIVAL, CARAVANS
    }

    public static class VisitHolder implements InventoryHolder {
        private final int page;
        private final VisitMode mode;
        private final List<Plot> plots;
        private final DiscoverFilter discoverFilter;
        private final String category;
        private final AtlasTab atlasTab;

        public VisitHolder(List<Plot> plots, int page, VisitMode mode, DiscoverFilter discoverFilter, String category) {
            this(plots, page, mode, discoverFilter, category, AtlasTab.DESTINATIONS);
        }

        public VisitHolder(List<Plot> plots, int page, VisitMode mode, DiscoverFilter discoverFilter, String category,
                           AtlasTab atlasTab) {
            this.plots = plots;
            this.page = page;
            this.mode = mode == null ? VisitMode.TRUSTED : mode;
            this.discoverFilter = discoverFilter == null ? DiscoverFilter.ALL : discoverFilter;
            this.category = category;
            this.atlasTab = atlasTab == null ? AtlasTab.DESTINATIONS : atlasTab;
        }

        public int getPage() { return page; }
        public VisitMode getMode() { return mode; }
        public boolean isShowingWarps() { return mode == VisitMode.WARPS; }
        public List<Plot> getPlots() { return plots; }
        public DiscoverFilter getDiscoverFilter() { return discoverFilter; }
        public String getCategory() { return category; }
        public AtlasTab getAtlasTab() { return atlasTab; }
        @Override public Inventory getInventory() { return null; }
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private String t(Player p, String key, String fallback, Map<String, String> vars) {
        return plugin.gui().tr(p, key, fallback, vars);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback, Map<String, String> vars) {
        return plugin.gui().trList(p, key, fallback, vars);
    }

    private String safeRole(String role) {
        if (role == null || role.isBlank()) return "Member";
        return role;
    }

    private void sendSystem(Player p, String key, String fallback) {
        String msg = t(p, key, fallback);
        if (msg == null || msg.isBlank()) return;
        p.sendMessage(GUIManager.color(msg));
    }

    private boolean canTeleport(Player p) {
        if (plugin.cfg() != null && !plugin.cfg().isTravelSystemEnabled()) {
            sendSystem(p, "travel_system_disabled", "&cTravel System is disabled.");
            return false;
        }
        if (plugin.cfg() != null && !plugin.cfg().allowVisitTeleport()) {
            sendSystem(p, "visit_teleport_disabled", "&cVisiting is currently disabled.");
            return false;
        }
        return true;
    }

    // --------------------------------------------------
    // OPEN
    // --------------------------------------------------

    public void open(Player player, int page, boolean showWarps) {
        open(player, page, showWarps ? VisitMode.WARPS : VisitMode.TRUSTED);
    }

    public void open(Player player, int page, VisitMode mode) {
        open(player, page, mode, DiscoverFilter.ALL, null);
    }

    public void open(Player player, int page, VisitMode mode, DiscoverFilter filter, String category) {
        openAtlas(player, AtlasTab.DESTINATIONS, page, mode, filter, category);
    }

    public void openAtlas(Player player, AtlasTab tab) {
        if (tab == AtlasTab.CARAVANS) {
            plugin.runMain(player, () -> {
                if (plugin.gui() != null && plugin.gui().caravans() != null) {
                    plugin.gui().caravans().open(player);
                }
            });
            return;
        }
        if (tab == AtlasTab.MY_BEACONS || tab == AtlasTab.ARRIVAL) {
            plugin.runMain(player, () -> {
                if (tab == AtlasTab.MY_BEACONS) buildBeaconsTab(player);
                else buildArrivalTab(player);
            });
            return;
        }
        open(player, 0, VisitMode.WARPS);
    }

    public void openAtlas(Player player, AtlasTab tab, int page, VisitMode mode, DiscoverFilter filter, String category) {
        if (tab == AtlasTab.CARAVANS) {
            openAtlas(player, tab);
            return;
        }
        if (tab == AtlasTab.MY_BEACONS || tab == AtlasTab.ARRIVAL) {
            openAtlas(player, tab);
            return;
        }
        final int requestedPage = page;
        final VisitMode requestedMode = mode == null ? VisitMode.TRUSTED : mode;
        final DiscoverFilter requestedFilter = requestedMode == VisitMode.DISCOVER
                ? (filter == null ? DiscoverFilter.ALL : filter) : DiscoverFilter.ALL;
        final String requestedCategory = category;

        plugin.runGlobalAsync(() -> {
            List<Plot> displayPlots = new ArrayList<>();

            // Filter defensively
            List<Plot> all;
            try {
                all = new ArrayList<>(plugin.store().getAllPlots());
            } catch (Throwable t) {
                all = new ArrayList<>();
                plugin.getLogger().warning("[VisitGUI] Failed to load plots: " + t.getMessage());
            }

            for (Plot plot : all) {
                if (plot == null) continue;

                switch (requestedMode) {
                    case WARPS -> {
                        if (plot.isServerWarp()) displayPlots.add(plot);
                    }
                    case OWNED -> {
                        if (plot.getOwner() != null && plot.getOwner().equals(player.getUniqueId())) {
                            displayPlots.add(plot);
                        }
                    }
                    case TRUSTED -> {
                        if ((plot.isRentedBy(player.getUniqueId())
                                || (plot.getPlayerRoles() != null
                                && plot.getPlayerRoles().containsKey(player.getUniqueId())))
                                && plot.getOwner() != null
                                && !plot.getOwner().equals(player.getUniqueId())) {
                            displayPlots.add(plot);
                        }
                    }
                    case DISCOVER -> {
                        var discovery = plugin.territoryLife().discovery(plot.getPlotId());
                        if (!plot.isServerZone() && discovery.visible()
                                && matchesDiscoverFilter(plot, requestedFilter, requestedCategory)) {
                            displayPlots.add(plot);
                        }
                    }
                    case FAVORITES -> {
                        if (!plot.isServerZone() && plugin.territoryLife().discovery(plot.getPlotId()).visible()
                                && plugin.territoryLife().isFavorite(player.getUniqueId(), plot.getPlotId())) {
                            displayPlots.add(plot);
                        }
                    }
                    case RECENT -> {
                        // Filled after the loop from SafeTravel recent history.
                    }
                }
            }

            if (requestedMode == VisitMode.RECENT && plugin.safeTravel() != null) {
                displayPlots.clear();
                Map<UUID, Plot> byId = new java.util.HashMap<>();
                for (Plot plot : all) {
                    if (plot != null && plot.getPlotId() != null) byId.put(plot.getPlotId(), plot);
                }
                for (UUID plotId : plugin.safeTravel().recentDestinations(player.getUniqueId())) {
                    Plot recent = byId.get(plotId);
                    if (recent != null) displayPlots.add(recent);
                }
            }

            if (requestedMode == VisitMode.DISCOVER || requestedMode == VisitMode.FAVORITES) {
                displayPlots.sort(Comparator
                        .comparing((Plot plot) -> plugin.territoryLife().discovery(plot.getPlotId()).featured()).reversed()
                        .thenComparing(Plot::getLikes, Comparator.reverseOrder())
                        .thenComparing((Plot plot) -> plugin.territoryLife().discovery(plot.getPlotId()).visits(), Comparator.reverseOrder())
                        .thenComparing((Plot plot) -> plugin.territoryLife().discovery(plot.getPlotId()).lastVisit(), Comparator.reverseOrder()));
                int maximum = Math.max(10, plugin.getConfig().getInt("plot_discovery.max_results", 500));
                if (displayPlots.size() > maximum) displayPlots = new ArrayList<>(displayPlots.subList(0, maximum));
            } else displayPlots.sort((p1, p2) -> {
                String n1 = requestedMode == VisitMode.WARPS
                        ? p1.getWarpName()
                        : p1.getPlotName() != null && !p1.getPlotName().isBlank() ? p1.getPlotName() : p1.getOwnerName();
                String n2 = requestedMode == VisitMode.WARPS
                        ? p2.getWarpName()
                        : p2.getPlotName() != null && !p2.getPlotName().isBlank() ? p2.getPlotName() : p2.getOwnerName();
                if (n1 == null || n1.isBlank()) n1 = "Unknown";
                if (n2 == null || n2.isBlank()) n2 = "Unknown";
                return n1.compareToIgnoreCase(n2);
            });

            int maxPages = (int) Math.ceil((double) displayPlots.size() / PLOTS_PER_PAGE);
            int fixedPage = requestedPage;
            if (fixedPage < 0) fixedPage = 0;
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            if (maxPages == 0) fixedPage = 0;

            final int finalPage = fixedPage;
            final int safePages = Math.max(1, maxPages);
            final List<Plot> finalDisplayPlots = displayPlots;

            plugin.runMain(player, () -> buildAndOpen(player, finalDisplayPlots, finalPage, safePages,
                    requestedMode, requestedFilter, requestedCategory));
        });
    }

    private boolean matchesDiscoverFilter(Plot plot, DiscoverFilter filter, String category) {
        var discovery = plugin.territoryLife().discovery(plot.getPlotId());
        return switch (filter) {
            case ALL -> true;
            case FEATURED -> discovery.featured();
            case FOR_SALE -> plot.isForSale();
            case FOR_RENT -> plot.isForRent()
                    || plot.getZones().stream().anyMatch(zone -> zone != null && zone.isListedForRent())
                    || plugin.territoryLife().getOffer(plot.getPlotId(), 0.0D, 1).price() > 0.0D;
            case CATEGORY -> category != null && category.equalsIgnoreCase(discovery.category());
        };
    }

    private void buildAndOpen(Player player, List<Plot> displayPlots, int page, int safePages, VisitMode mode,
                              DiscoverFilter discoverFilter, String category) {
        // Title
        String modeTitleKey = switch (mode) {
            case WARPS -> "visit_title_warps";
            case OWNED -> "visit_title_owned";
            case TRUSTED -> "visit_title_trusted";
            case DISCOVER -> "visit_title_discover";
            case FAVORITES -> "visit_title_favorites";
            case RECENT -> "visit_title_recent";
        };
        String fallbackTitle = switch (mode) {
            case WARPS -> "&6Server Destinations";
            case OWNED -> "&aMy Plots";
            case TRUSTED -> "&9Friends & Trusted";
            case DISCOVER -> "&6Discover Plots";
            case FAVORITES -> "&eFavorite Plots";
            case RECENT -> "&bRecent Destinations";
        };

        String baseTitle = plugin.gui().title(player, modeTitleKey, fallbackTitle);
        String suffix = " §8(" + (page + 1) + "/" + safePages + ")";

        String fullTitle = clampTitleWithSuffix(baseTitle, suffix);

        Inventory inv = Bukkit.createInventory(new VisitHolder(displayPlots, page, mode, discoverFilter, category), 54, fullTitle);

        // 1.2.6: fill ALL slots
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Empty state
        if (displayPlots.isEmpty()) {
            ItemStack empty = GUIManager.createItem(
                    Material.BARRIER,
                    t(player, "visit_empty_title", "&cNothing to visit"),
                    tl(player, "visit_empty_lore", List.of(
                            "&7No destinations were found for this mode.",
                            "&7Try switching tabs."
                    ))
            );
            tagAction(empty, "visit_empty");
            inv.setItem(22, empty);
        }

        // Populate entries
        int startIndex = page * PLOTS_PER_PAGE;
        for (int slot = 0; slot < PLOTS_PER_PAGE; slot++) {
            int index = startIndex + slot;
            if (index >= displayPlots.size()) break;

            Plot plot = displayPlots.get(index);
            if (plot == null) continue;

            ItemStack icon;

            if (mode == VisitMode.WARPS) {
                Material mat = (plot.getWarpIcon() != null) ? plot.getWarpIcon() : Material.BEACON;
                String warpName = (plot.getWarpName() != null && !plot.getWarpName().isBlank())
                        ? plot.getWarpName()
                        : t(player, "visit_server_warp_default", "Server Warp");

                String dn = t(player, "visit_warp_name", "&6{WARP}", Map.of("WARP", warpName));

                String warpCategory = plot.getWarpCategory() == null || plot.getWarpCategory().isBlank()
                        ? "HUB" : plot.getWarpCategory();
                List<String> lore = tl(player, "visit_warp_lore", List.of(
                        "&7A staff-managed server destination.",
                        "&7Category: &f{CATEGORY}",
                        " ",
                        "&eClick to teleport"
                ), Map.of("WARP", warpName, "CATEGORY", warpCategory));
                lore = appendArrivalCue(player, plot, lore);

                icon = GUIManager.createItem(mat, dn, lore);
            } else {
                OfflinePlayer owner = (plot.getOwner() != null) ? Bukkit.getOfflinePlayer(plot.getOwner()) : null;

                String role = mode == VisitMode.OWNED
                        ? t(player, "visit_role_owner", "&aOwner")
                        : mode == VisitMode.DISCOVER || mode == VisitMode.FAVORITES
                        ? "&6" + plugin.territoryLife().discovery(plot.getPlotId()).category()
                        : plot.isRentedBy(player.getUniqueId())
                        ? t(player, "visit_role_renter", "&bRenter")
                        : safeRole(plot.getRole(player.getUniqueId()));
                String nickname = plot.getRoleNickname(player.getUniqueId());
                if (nickname != null && !nickname.isBlank()
                        && mode != VisitMode.OWNED && !plot.isRentedBy(player.getUniqueId())) {
                    role = nickname + " &8(" + role + "&8)";
                }
                String ownerName = (plot.getOwnerName() != null && !plot.getOwnerName().isBlank())
                        ? plot.getOwnerName()
                        : t(player, "visit_unknown_label", "Unknown");

                String alias = (plot.getEntryTitle() != null && !plot.getEntryTitle().isBlank())
                        ? plot.getEntryTitle()
                        : plot.getPlotName() != null && !plot.getPlotName().isBlank()
                        ? plot.getPlotName()
                        : ownerName + "'s Plot";

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();

                if (meta != null) {
                    if (owner != null) {
                        try { meta.setOwningPlayer(owner); } catch (Throwable ignored) {}
                    }

                    String displayName = t(player, "visit_plot_name", "&e{PLOT}", Map.of("PLOT", alias));
                    meta.setDisplayName(GUIManager.color(displayName));

                    String worldName = (plot.getWorld() != null && !plot.getWorld().isBlank())
                            ? plot.getWorld()
                            : t(player, "visit_unknown_label", "Unknown");

                    List<String> lore = tl(player, "visit_plot_lore", List.of(
                            "&7World: &f{WORLD}",
                            "&7Role: &b{ROLE}",
                            "&7Capacity: &f{TRUSTED}&7/&f{MAX}",
                            " ",
                            "&eClick to teleport"
                    ), Map.of("WORLD", worldName, "ROLE", role,
                            "TRUSTED", String.valueOf(plot.countTrustedMembers()),
                            "MAX", String.valueOf(plot.getMaxMembers())));

                    if (mode == VisitMode.DISCOVER || mode == VisitMode.FAVORITES) {
                        var discovery = plugin.territoryLife().discovery(plot.getPlotId());
                        lore = new ArrayList<>(lore);
                        lore.add(GUIManager.color(t(player, "visit_plot_likes_line",
                                "&7Likes: &d{LIKES} &8| &7Visits: &b{VISITS}",
                                Map.of("LIKES", String.valueOf(plot.getLikes()),
                                        "VISITS", String.valueOf(discovery.visits())))));
                        if (discovery.featured()) {
                            lore.add(GUIManager.color(t(player, "visit_featured_line", "&6★ Featured Territory")));
                        }
                        if (plot.getDescription() != null && !plot.getDescription().isBlank()) {
                            lore.add(GUIManager.color("&7\"" + plot.getDescription() + "\""));
                        }
                        if (!plot.getNoticeboard().isEmpty()) {
                            lore.add(GUIManager.color(t(player, "visit_plot_notice_count",
                                    "&7📌 " + plot.getNoticeboard().size() + " noticeboard notice(s)",
                                    Map.of("COUNT", String.valueOf(plot.getNoticeboard().size())))));
                        }
                        lore.add(GUIManager.color(plugin.territoryLife().isFavorite(player.getUniqueId(), plot.getPlotId())
                                ? t(player, "visit_favorite_remove", "&eRight-click to remove favorite")
                                : t(player, "visit_favorite_add", "&eRight-click to favorite")));
                    }

                    lore = appendArrivalCue(player, plot, lore);
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }

                icon = head;
            }

            // 1.2.6: tag entry action + plot id
            tagAction(icon, "visit_entry");
            tagPlotId(icon, plot);

            inv.setItem(slot, icon);
        }

        ItemStack help = GUIManager.createItem(
                Material.BOOK,
                t(player, "visit_help_name", "&eTravel Help"),
                tl(player, "visit_help_lore", List.of(
                        "&7Browse Spawn, hubs, towns, and trusted plots.",
                        "&7Favorites and Recent keep useful destinations close.",
                        "&7Unavailable destinations show a clear empty state."
                ))
        );
        tagAction(help, "visit_help");
        inv.setItem(36, help);
        if (mode == VisitMode.DISCOVER) {
            String label = discoverFilter == DiscoverFilter.CATEGORY
                    ? t(player, "visit_discover_category_filter", "Category: {CATEGORY}",
                            Map.of("CATEGORY", category == null ? "other" : category))
                    : prettyFilter(discoverFilter);
            ItemStack filter = GUIManager.createItem(Material.HOPPER,
                    t(player, "visit_discover_filter_name", "&eDiscover Filter: &f{FILTER}",
                            Map.of("FILTER", label)),
                    tl(player, "visit_discover_filter_lore", List.of(
                            "&7Cycle: All, Featured, For Sale,",
                            "&7For Rent, and discovery categories.",
                            "&eClick to change filter."
                    )));
            tagAction(filter, "discover_filter");
            inv.setItem(37, filter);
        }

        ItemStack recentTab = travelTab(player, VisitMode.RECENT, mode == VisitMode.RECENT);
        tagAction(recentTab, "mode_RECENT");
        inv.setItem(40, recentTab);

        int tabSlot = 46;
        for (VisitMode tabMode : List.of(VisitMode.WARPS, VisitMode.OWNED, VisitMode.TRUSTED,
                VisitMode.DISCOVER, VisitMode.FAVORITES)) {
            ItemStack tab = travelTab(player, tabMode, tabMode == mode);
            tagAction(tab, "mode_" + tabMode.name());
            inv.setItem(tabSlot++, tab);
        }

        // Prev / Next (45 / 53)
        if (page > 0) {
            ItemStack prev = GUIManager.createItem(Material.ARROW,
                    t(player, "button_prev_page", "&fPrevious Page"),
                    tl(player, "prev_page_lore", List.of("&7Go to the previous page.")));
            tagAction(prev, "prev_page");
            inv.setItem(45, prev);
        }

        ItemStack back = GUIManager.createItem(
                Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main menu."))
        );
        tagAction(back, "back_menu");
        inv.setItem(51, back);

        ItemStack close = GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        );
        tagAction(close, "close_menu");
        inv.setItem(52, close);

        if (page < (int) Math.ceil((double) displayPlots.size() / PLOTS_PER_PAGE) - 1) {
            ItemStack next = GUIManager.createItem(Material.ARROW,
                    t(player, "button_next_page", "&fNext Page"),
                    tl(player, "next_page_lore", List.of("&7Go to the next page.")));
            tagAction(next, "next_page");
            inv.setItem(53, next);
        }

        paintAtlasTabs(player, inv, AtlasTab.DESTINATIONS);

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private ItemStack travelTab(Player player, VisitMode mode, boolean selected) {
        Material icon = switch (mode) {
            case WARPS -> Material.BEACON;
            case OWNED -> Material.OAK_SIGN;
            case TRUSTED -> Material.PLAYER_HEAD;
            case DISCOVER -> Material.SPYGLASS;
            case FAVORITES -> Material.NETHER_STAR;
            case RECENT -> Material.CLOCK;
        };
        String key = switch (mode) {
            case WARPS -> "visit_switch_warps";
            case OWNED -> "visit_switch_owned";
            case TRUSTED -> "visit_switch_trusted";
            case DISCOVER -> "visit_switch_discover";
            case FAVORITES -> "visit_switch_favorites";
            case RECENT -> "visit_switch_recent";
        };
        String fallback = switch (mode) {
            case WARPS -> "&6Server Places";
            case OWNED -> "&aMy Plots";
            case TRUSTED -> "&9Trusted";
            case DISCOVER -> "&6Discover";
            case FAVORITES -> "&eFavorites";
            case RECENT -> "&bRecent";
        };
        List<String> lore = new ArrayList<>();
        lore.add(selected
                ? t(player, "travel_tab_selected", "&aCurrently viewing this atlas.")
                : t(player, "travel_tab_open", "&eClick to open this atlas."));
        return GUIManager.createItem(selected ? Material.LIME_DYE : icon, t(player, key, fallback), lore);
    }

    private String prettyFilter(DiscoverFilter filter) {
        return switch (filter) {
            case ALL -> "All";
            case FEATURED -> "Featured";
            case FOR_SALE -> "For Sale";
            case FOR_RENT -> "For Rent";
            case CATEGORY -> "Category";
        };
    }

    private String nextCategory(String current) {
        List<String> categories = plugin.store().getAllPlots().stream()
                .filter(plot -> plot != null)
                .map(plot -> plugin.territoryLife().discovery(plot.getPlotId()).category())
                .filter(value -> value != null && !value.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (categories.isEmpty()) return "other";
        int index = current == null ? -1 : categories.indexOf(current);
        return categories.get((index + 1) % categories.size());
    }

    // --------------------------------------------------
    // CLICK HANDLER
    // --------------------------------------------------

    public void handleClick(Player player, InventoryClickEvent e, VisitHolder holder) {
        e.setCancelled(true);

        // Strict: only top inventory
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Ignore filler clicks
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        VisitMode mode = holder.getMode();
        int page = holder.getPage();

        String action = getAction(clicked);
        if (action != null) {
            switch (action) {
                case "prev_page" -> { open(player, page - 1, mode); plugin.effects().playMenuFlip(player); return; }
                case "next_page" -> { open(player, page + 1, mode); plugin.effects().playMenuFlip(player); return; }
                case "mode_WARPS" -> { open(player, 0, VisitMode.WARPS); plugin.effects().playMenuFlip(player); return; }
                case "mode_OWNED" -> { open(player, 0, VisitMode.OWNED); plugin.effects().playMenuFlip(player); return; }
                case "mode_TRUSTED" -> { open(player, 0, VisitMode.TRUSTED); plugin.effects().playMenuFlip(player); return; }
                case "mode_DISCOVER" -> { open(player, 0, VisitMode.DISCOVER); plugin.effects().playMenuFlip(player); return; }
                case "mode_FAVORITES" -> { open(player, 0, VisitMode.FAVORITES); plugin.effects().playMenuFlip(player); return; }
                case "mode_RECENT" -> { open(player, 0, VisitMode.RECENT); plugin.effects().playMenuFlip(player); return; }
                case "atlas_destinations" -> { openAtlas(player, AtlasTab.DESTINATIONS); plugin.effects().playMenuFlip(player); return; }
                case "atlas_beacons" -> { openAtlas(player, AtlasTab.MY_BEACONS); plugin.effects().playMenuFlip(player); return; }
                case "atlas_arrival" -> { openAtlas(player, AtlasTab.ARRIVAL); plugin.effects().playMenuFlip(player); return; }
                case "atlas_caravans" -> { openAtlas(player, AtlasTab.CARAVANS); plugin.effects().playMenuFlip(player); return; }
                case "back_menu" -> { plugin.gui().openMain(player); plugin.effects().playMenuFlip(player); return; }
                case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
                case "visit_empty" -> { plugin.effects().playError(player); return; }
                case "visit_help" -> {
                    sendSystem(player, "visit_help_chat",
                            "&eTravel: use the tabs for Destinations, Owned, Trusted, Discover, Favorites, or Recent.");
                    plugin.effects().playMenuFlip(player);
                    return;
                }
                case "discover_filter" -> {
                    DiscoverFilter next = holder.getDiscoverFilter().next();
                    String nextCategory = next == DiscoverFilter.CATEGORY ? nextCategory(holder.getCategory()) : null;
                    open(player, 0, VisitMode.DISCOVER, next, nextCategory);
                    plugin.effects().playMenuFlip(player);
                    return;
                }
                case "visit_entry" -> { /* continue */ }
                case "give" -> {
                    if (plugin.beacons() != null) plugin.beacons().giveStarterPads(player);
                    return;
                }
                case "arrival_classic" -> { setPlotArrival(player, Plot.ArrivalMode.CLASSIC); return; }
                case "arrival_beacon" -> { setPlotArrival(player, Plot.ArrivalMode.BEACON); return; }
                case "arrival_override" -> { togglePlotTravelerOverride(player); return; }
                case "traveler_pref" -> { cycleTravelerPreference(player); return; }
                default -> {
                    if (action.startsWith("open:") && plugin.gui().beacons() != null && plugin.beacons() != null) {
                        UUID id = parseUuid(action.substring(5));
                        var beacon = id == null ? null : plugin.beacons().store().get(id);
                        if (beacon != null && plugin.beacons().canManage(player, beacon)) {
                            plugin.gui().beacons().openEdit(player, beacon);
                        }
                    }
                    return;
                }
            }
        }

        // Entry click (0..44)
        int slot = e.getSlot();
        if (slot < 0 || slot >= PLOTS_PER_PAGE) return;

        Plot plot = resolvePlotFromItem(clicked, holder, page, slot);
        if (plot == null) return;

        if ((mode == VisitMode.DISCOVER || mode == VisitMode.FAVORITES) && GuiClicks.alternate(e)) {
            boolean added = plugin.territoryLife().toggleFavorite(player.getUniqueId(), plot.getPlotId());
            sendSystem(player, added ? "favorite_added" : "favorite_removed",
                    added ? "&aPlot added to favorites." : "&ePlot removed from favorites.");
            open(player, page, mode);
            return;
        }

        // 1.2.6: re-check access before teleporting
        if (mode == VisitMode.WARPS) {
            if (!plot.isServerWarp()) {
                sendSystem(player, "visit_not_available", "&cThat waypoint is no longer available.");
                plugin.effects().playError(player);
                open(player, page, VisitMode.WARPS);
                return;
            }
        } else if (mode == VisitMode.TRUSTED) {
            if ((!plot.isRentedBy(player.getUniqueId())
                    && (plot.getPlayerRoles() == null
                    || !plot.getPlayerRoles().containsKey(player.getUniqueId())))
                    || (plot.getOwner() != null && plot.getOwner().equals(player.getUniqueId()))) {
                sendSystem(player, "visit_not_trusted", "&cYou are no longer trusted on that plot.");
                plugin.effects().playError(player);
                open(player, page, VisitMode.TRUSTED);
                return;
            }
        } else if (mode == VisitMode.OWNED) {
            if (plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
                sendSystem(player, "visit_not_owner", "&cYou no longer own that plot.");
                plugin.effects().playError(player);
                open(player, page, VisitMode.OWNED);
                return;
            }
        } else if (!plugin.territoryLife().discovery(plot.getPlotId()).visible() || plot.isServerZone()) {
            sendSystem(player, "visit_not_available", "&cThat territory is no longer publicly discoverable.");
            plugin.effects().playError(player);
            open(player, page, mode);
            return;
        }

        if (!canTeleport(player)) {
            plugin.effects().playError(player);
            return;
        }

        com.aegisguard.beacon.TeleportBeacon.Purpose purpose = switch (mode) {
            case WARPS -> com.aegisguard.beacon.TeleportBeacon.Purpose.SPAWN;
            default -> com.aegisguard.beacon.TeleportBeacon.Purpose.SPAWN;
        };
        // 1.4 per-plot arrival choice: only route through beacons when this plot's owner
        // chose beacon arrival (or the server forces it). Classic plots Safe Travel to the
        // plot spawn even if pads exist. Beacon plots fail closed if no public pad exists.
                if (plugin.beacons() != null && plugin.beacons().isEnabled()
                && plugin.beacons().requiresBeaconArrival(player, plot)
                && plugin.beacons().handlePublicListingTravel(player, plot, purpose)) {
            return;
        }

        Location target = plot.getSpawnLocation() != null
                ? plot.getSpawnLocation()
                : plot.getCenter(plugin);

        if (target == null || target.getWorld() == null) {
            sendSystem(player, "visit_fail_no_spawn", "&cThis destination has no valid spawn set.");
            plugin.effects().playError(player);
            return;
        }

        SafeTravelResult result = plugin.safeTravel().travel(player, target, SafeTravelService.Kind.VISIT);
        if (!result.isSuccess()) {
            if (result.status() == SafeTravelResult.Status.UNSAFE_DESTINATION) {
                sendSystem(player, "visit_fail_unsafe_spawn", "&cThis destination does not have a safe teleport point.");
            }
            return;
        }

        player.closeInventory();
        plugin.territoryLife().recordVisit(plot.getPlotId(), player.getUniqueId());
        plugin.safeTravel().recordRecentDestination(player.getUniqueId(), plot.getPlotId());
        sendSystem(player, "visit_teleport_success", "&aTeleported.");
        plugin.effects().playTeleport(player);
    }

    private List<String> appendArrivalCue(Player player, Plot plot, List<String> lore) {
        List<String> out = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        boolean beacon = plugin.beacons() != null && plugin.beacons().isEnabled()
                && plugin.beacons().requiresBeaconArrival(player, plot);
        out.add(GUIManager.color(beacon
                ? t(player, "atlas_arrival_cue_beacon", "&bArrival: &fBeacon pad")
                : t(player, "atlas_arrival_cue_classic", "&7Arrival: &fClassic spawn")));
        return out;
    }

    private void paintAtlasTabs(Player player, Inventory inv, AtlasTab selected) {
        boolean beaconsOn = plugin.beacons() != null && plugin.beacons().isEnabled();
        ItemStack destinations = atlasTabItem(player, Material.COMPASS, selected == AtlasTab.DESTINATIONS,
                "atlas_tab_destinations", "&aDestinations",
                List.of("&7Warps, trusted plots, discover,", "&7favorites, and recent visits."));
        tagAction(destinations, "atlas_destinations");
        inv.setItem(38, destinations);
        if (beaconsOn) {
            ItemStack beacons = atlasTabItem(player, Material.END_PORTAL_FRAME, selected == AtlasTab.MY_BEACONS,
                    "atlas_tab_beacons", "&bMy Beacons",
                    List.of("&7Pads on the plot you stand in.", "&7Create, link, and give starter pads."));
            tagAction(beacons, "atlas_beacons");
            inv.setItem(39, beacons);
            ItemStack arrival = atlasTabItem(player, Material.ENDER_EYE, selected == AtlasTab.ARRIVAL,
                    "atlas_tab_arrival", "&dArrival",
                    List.of("&7Choose classic spawn or a public pad", "&7for visitors, plus traveler override."));
            tagAction(arrival, "atlas_arrival");
            inv.setItem(41, arrival);
        }
        boolean caravansOn = plugin.caravans() != null && plugin.caravans().isEnabled();
        if (caravansOn) {
            ItemStack caravans = atlasTabItem(player, Material.CHEST_MINECART, selected == AtlasTab.CARAVANS,
                    "atlas_tab_caravans", "&6Caravans",
                    List.of("&7Dispatch goods along public beacon hops.", "&7Track ETA, insurance, and payouts."));
            tagAction(caravans, "atlas_caravans");
            inv.setItem(40, caravans);
        }
    }

    public void attachAtlasChrome(Player player, Inventory inv, AtlasTab selected) {
        paintAtlasTabs(player, inv, selected);
        paintVisitChrome(player, inv);
    }

    private ItemStack atlasTabItem(Player player, Material icon, boolean selected, String key, String fallback,
                                   List<String> loreFallback) {
        List<String> lore = new ArrayList<>(tl(player, key + "_lore", loreFallback));
        lore.add(selected
                ? t(player, "travel_tab_selected", "&aCurrently viewing this atlas.")
                : t(player, "travel_tab_open", "&eClick to open this atlas."));
        return GUIManager.createItem(selected ? Material.LIME_DYE : icon, t(player, key, fallback), lore);
    }

    private void paintVisitChrome(Player player, Inventory inv) {
        ItemStack back = GUIManager.createItem(
                Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main menu."))
        );
        tagAction(back, "back_menu");
        inv.setItem(51, back);
        ItemStack close = GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        );
        tagAction(close, "close_menu");
        inv.setItem(52, close);
    }

    private void buildBeaconsTab(Player player) {
        String title = plugin.gui().title(player, "atlas_title_beacons", "&bTravel Atlas · My Beacons");
        Inventory inv = Bukkit.createInventory(
                new VisitHolder(List.of(), 0, VisitMode.OWNED, DiscoverFilter.ALL, null, AtlasTab.MY_BEACONS),
                54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        inv.setItem(4, GUIManager.createItem(Material.END_PORTAL_FRAME,
                t(player, "beacon_manager_guide_name", "&bHow beacons work"),
                tl(player, "beacon_manager_guide_lore", List.of(
                        "&7Place a lodestone (or listed pad).",
                        "&7Sneak-click it to create a beacon.",
                        "&71. Pick a preset  2. Link another pad",
                        "&73. Stand on it to travel."))));
        if (plot == null) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_need_plot", "&cStand in a claim"),
                    List.of(t(player, "beacon_need_plot_lore", "&7Beacons belong to the plot you are in."))));
        } else if (plugin.beacons() != null) {
            int slot = 19;
            for (var beacon : plugin.beacons().store().forPlot(plot.getPlotId())) {
                if (slot > 25 && slot < 28) slot = 28;
                if (slot > 34) break;
                ItemStack item = plugin.gui().beacons().padIcon(player, beacon);
                plugin.gui().tagAction(item, "open:" + beacon.getId());
                inv.setItem(slot++, item);
            }
        }
        ItemStack give = GUIManager.createItem(
                plugin.beacons() == null ? Material.LODESTONE : plugin.beacons().starterPadMaterial(),
                t(player, "beacon_give_button", "&bGet pad blocks"),
                tl(player, "beacon_give_button_lore", List.of(
                        "&7Gives lodestones (or the server's pad).",
                        "&7Place them, then sneak-right-click to bind.",
                        "&7You can also use any allowed pad you already have.")));
        tagAction(give, "give");
        inv.setItem(43, give);
        paintAtlasTabs(player, inv, AtlasTab.MY_BEACONS);
        paintVisitChrome(player, inv);
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void buildArrivalTab(Player player) {
        String title = plugin.gui().title(player, "atlas_title_arrival", "&dTravel Atlas · Arrival");
        Inventory inv = Bukkit.createInventory(
                new VisitHolder(List.of(), 0, VisitMode.OWNED, DiscoverFilter.ALL, null, AtlasTab.ARRIVAL),
                54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean manage = plot != null && plot.canManage(player, plugin);
        if (plot == null) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_need_plot", "&cStand in a claim"),
                    List.of(t(player, "atlas_arrival_need_plot_lore",
                            "&7Stand in a plot you manage to set arrival."))));
        } else {
            boolean beacon = plot.requiresBeaconArrival();
            ItemStack classic = GUIManager.createItem(
                    !beacon && manage ? Material.LIME_DYE : Material.COMPASS,
                    t(player, "atlas_arrival_classic_name", "&aClassic spawn"),
                    tl(player, "atlas_arrival_classic_lore", List.of(
                            "&7Visitors land at this plot's spawn.",
                            manage ? "&eClick to use classic arrival." : "&7Only managers can change this.")));
            tagAction(classic, "arrival_classic");
            inv.setItem(20, classic);
            ItemStack pad = GUIManager.createItem(
                    beacon && manage ? Material.LIME_DYE : Material.END_PORTAL_FRAME,
                    t(player, "atlas_arrival_beacon_name", "&bBeacon pad"),
                    tl(player, "atlas_arrival_beacon_lore", List.of(
                            "&7Visitors must land on a public pad.",
                            "&7Fails closed if no public pad exists.",
                            manage ? "&eClick to require beacon arrival." : "&7Only managers can change this.")));
            tagAction(pad, "arrival_beacon");
            inv.setItem(22, pad);
            boolean allow = plot.isAllowTravelerOverride();
            ItemStack override = GUIManager.createItem(
                    allow ? Material.LIME_DYE : Material.GRAY_DYE,
                    t(player, allow ? "atlas_allow_override_on" : "atlas_allow_override_off",
                            allow ? "&aTraveler override allowed" : "&7Traveler override locked"),
                    tl(player, "atlas_allow_override_lore", List.of(
                            "&7When allowed, visitors may pick classic",
                            "&7or beacon if that mode is available.",
                            manage ? "&eClick to toggle." : "&7Only managers can change this.")));
            tagAction(override, "arrival_override");
            inv.setItem(24, override);
        }
        var pref = plugin.notifications() == null
                ? com.aegisguard.notify.PlayerNotificationSettings.ArrivalPreference.OWNER_DEFAULT
                : plugin.notifications().getSettings(player.getUniqueId()).getPreferredArrival();
        String prefLabel = switch (pref) {
            case CLASSIC -> t(player, "atlas_pref_classic", "&aClassic spawn");
            case BEACON -> t(player, "atlas_pref_beacon", "&bBeacon pad");
            default -> t(player, "atlas_pref_owner", "&7Owner default");
        };
        ItemStack traveler = GUIManager.createItem(Material.NAME_TAG,
                t(player, "atlas_traveler_pref_name", "&eMy arrival preference"),
                List.of(prefLabel,
                        t(player, "atlas_traveler_pref_lore",
                                "&7Used when the destination allows overrides."),
                        t(player, "atlas_traveler_pref_click", "&eClick to cycle.")));
        tagAction(traveler, "traveler_pref");
        inv.setItem(31, traveler);
        paintAtlasTabs(player, inv, AtlasTab.ARRIVAL);
        paintVisitChrome(player, inv);
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void setPlotArrival(Player player, Plot.ArrivalMode mode) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            plugin.effects().playError(player);
            return;
        }
        plot.setArrivalMode(mode);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        sendSystem(player, "arrival_set",
                "&a✔ Public arrival set to &f" + mode.name().toLowerCase(Locale.ROOT) + "&a for this plot.");
        plugin.effects().playConfirm(player);
        buildArrivalTab(player);
    }

    private void togglePlotTravelerOverride(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            plugin.effects().playError(player);
            return;
        }
        plot.setAllowTravelerOverride(!plot.isAllowTravelerOverride());
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(player);
        buildArrivalTab(player);
    }

    private void cycleTravelerPreference(Player player) {
        if (plugin.notifications() == null) {
            plugin.effects().playError(player);
            return;
        }
        plugin.notifications().cyclePreferredArrival(player.getUniqueId());
        plugin.effects().playConfirm(player);
        buildArrivalTab(player);
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // --------------------------------------------------
    // PDC helpers
    // --------------------------------------------------

    private void tagAction(ItemStack item, String action) {
        if (item == null || action == null || action.isBlank()) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action.trim().toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private String getAction(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String v = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
            return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void tagPlotId(ItemStack item, Plot plot) {
        if (item == null || plot == null || plot.getPlotId() == null) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyPlotId, PersistentDataType.STRING, plot.getPlotId().toString());
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private Plot resolvePlotFromItem(ItemStack item, VisitHolder holder, int page, int slot) {
        // 1) PDC-first
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String id = meta.getPersistentDataContainer().get(keyPlotId, PersistentDataType.STRING);
                if (id != null && !id.isBlank()) {
                    UUID plotId = UUID.fromString(id);
                    for (Plot p : holder.getPlots()) {
                        if (p != null && plotId.equals(p.getPlotId())) return p;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2) legacy index fallback
        int index = (page * PLOTS_PER_PAGE) + slot;
        if (index < 0 || index >= holder.getPlots().size()) return null;
        return holder.getPlots().get(index);
    }

    // --------------------------------------------------
    // Title clamp
    // --------------------------------------------------

    private String clampTitleWithSuffix(String base, String suffix) {
        final int MAX = 32;
        if (base == null) base = "";
        if (suffix == null) suffix = "";

        String combined = base + suffix;
        if (combined.length() <= MAX) return combined;

        if (suffix.length() >= MAX) {
            String cut = suffix.substring(0, MAX);
            return cut.endsWith("§") ? cut.substring(0, MAX - 1) : cut;
        }

        int remainingForBase = MAX - suffix.length();
        String trimmedBase = base.length() > remainingForBase ? base.substring(0, remainingForBase) : base;
        if (trimmedBase.endsWith("§")) trimmedBase = trimmedBase.substring(0, Math.max(0, trimmedBase.length() - 1));

        return trimmedBase + suffix;
    }
}
