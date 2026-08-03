package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.flags.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RolesGUI
 * - Manage trusted players and their permission levels.
 * - Also manages per-role flag permissions via a submenu.
 * - Fully localized (Codex-backed) with safe fallbacks + title clamp.
 */
public class RolesGUI implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, NicknamePrompt> pendingNicknames = new ConcurrentHashMap<>();
    private final Map<UUID, AddByNamePrompt> pendingNames = new ConcurrentHashMap<>();

    private record NicknamePrompt(UUID plotId, UUID targetId) {}
    private record AddByNamePrompt(UUID plotId) {}

    private static final List<String> ROLE_FLAG_KEYS = List.of(
            "PVP",
            "CONTAINERS",
            "MOBS",
            "PETS",
            "ENTITIES",
            "FARM",
            "TNT",
            "FIRE",
            "PISTON",
            "ENTRY",
            "SHOP",
            "FLY",
            "ANIMALS",
            "REDSTONE",
            "VEHICLES"
    );

    private static final int PLOTS_PER_PAGE = 45;
    private static final int MEMBERS_PER_PAGE = 45;
    private static final int PLAYERS_PER_PAGE = 45;

    private static final int ROLES_VISIBLE = 18; // 0-17 in the manage menu

    public RolesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------
    // ✅ v1.2.6: Role Icon & Display Support
    // --------------------------------------------------

    /**
     * Get the display name for a role from config (with color codes).
     * Falls back to capitalized role name if not defined.
     *
     * @param roleName The role ID (e.g., "farmer", "moderator")
     * @return Display name with color codes
     */
    private String getRoleDisplayName(String roleName) {
        if (roleName == null) return "&7Unknown";

        // Try new "display" key (v1.2.6+)
        String display = plugin.cfg().raw().getString("roles." + roleName + ".display");
        if (display != null && !display.isEmpty()) {
            return display;
        }

        // Fall back to legacy "name" key (v1.2.5 compatibility)
        String name = plugin.cfg().raw().getString("roles." + roleName + ".name");
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // Final fallback: capitalize role name
        return capitalizeRole(roleName);
    }

    /**
     * Get the icon material for a role from config.
     * Falls back to sensible defaults based on role name.
     *
     * @param roleName The role ID
     * @return Material for the role icon
     */
    private Material getRoleIcon(String roleName) {
        if (roleName == null) return Material.PAPER;

        // Try to read icon from config
        String iconStr = plugin.cfg().raw().getString("roles." + roleName + ".icon");
        if (iconStr != null && !iconStr.isEmpty()) {
            try {
                return Material.valueOf(iconStr.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException ignored) {
                // Invalid material, fall through to defaults
            }
        }

        // Fallback to role-specific defaults
        return switch (roleName.toLowerCase()) {
            case "owner" -> Material.DIAMOND;
            case "member" -> Material.IRON_INGOT;
            case "visitor" -> Material.FEATHER;
            case "moderator" -> Material.REDSTONE_TORCH;
            case "guard" -> Material.IRON_SWORD;
            case "steward" -> Material.GOLDEN_APPLE;
            case "farmer" -> Material.IRON_HOE;
            case "farmland_merchant" -> Material.WHEAT;
            case "shopkeeper" -> Material.EMERALD;
            case "redstone_engineer" -> Material.REDSTONE;
            case "animal_handler" -> Material.WHEAT_SEEDS;
            case "tenant", "resident" -> Material.OAK_DOOR;
            case "event_host" -> Material.FIREWORK_ROCKET;
            case "builder", "builder_plus" -> Material.DIAMOND_PICKAXE;
            default -> Material.PAPER;
        };
    }

    /**
     * Get the priority of a role for sorting.
     * Higher priority = more important role.
     *
     * @param roleName The role ID
     * @return Priority value (default 50)
     */
    private int getRolePriority(String roleName) {
        if (roleName == null) return 0;
        return plugin.cfg().raw().getInt("roles." + roleName + ".priority", 50);
    }

    /**
     * Capitalize a role name for display.
     */
    private String capitalizeRole(String role) {
        if (role == null || role.isEmpty()) return "";
        String[] words = role.toLowerCase().replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    // --------------------------------------------------
    // HOLDERS
    // --------------------------------------------------

    public static class PlotSelectorHolder implements InventoryHolder {
        private final int page;
        public PlotSelectorHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RolesMenuHolder implements InventoryHolder {
        private final Plot plot;
        private final int page;
        public RolesMenuHolder(Plot plot, int page) { this.plot = plot; this.page = page; }
        public Plot getPlot() { return plot; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleAddHolder implements InventoryHolder {
        private final Plot plot;
        private final int page;
        public RoleAddHolder(Plot plot, int page) { this.plot = plot; this.page = page; }
        public Plot getPlot() { return plot; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleManageHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final int page;
        public RoleManageHolder(Plot plot, OfflinePlayer target, int page) {
            this.plot = plot;
            this.target = target;
            this.page = page;
        }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RoleFlagsHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final String roleName;

        public RoleFlagsHolder(Plot plot, OfflinePlayer target, String roleName) {
            this.plot = plot;
            this.target = target;
            this.roleName = roleName;
        }

        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public String getRoleName() { return roleName; }

        @Override public Inventory getInventory() { return null; }
    }

    // --------------------------------------------------
    // TRANSLATION HELPERS
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    /**
     * Map-aware translate with safe fallback.
     * Tries Codex map API first (since many of your keys use {VARS}).
     */
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
                out = out.replace("{" + k + "}", v).replace("{" + k.toLowerCase(Locale.ROOT) + "}", v);
            }
        }

        return out;
    }

    /**
     * Map-aware list translate with safe fallback.
     * plugin.gui().trList doesn't know about {VARS}, so we apply replacements here.
     */
    private List<String> tl(Player p, String key, Map<String, String> vars, List<String> fallback) {
        List<String> base = plugin.gui().trList(p, key, fallback);
        if (base == null) base = List.of();

        List<String> out = new ArrayList<>(base.size());
        for (String line : base) {
            String s = (line == null) ? "" : line;

            if (vars != null && !vars.isEmpty()) {
                for (Map.Entry<String, String> en : vars.entrySet()) {
                    String k = en.getKey();
                    String v = en.getValue() == null ? "" : en.getValue();
                    s = s.replace("{" + k + "}", v).replace("{" + k.toLowerCase(Locale.ROOT) + "}", v);
                }
            }

            out.add(GUIManager.color(s));
        }

        return out;
    }

    private String clampTitle(String raw, String fallback) {
        String tt = GUIManager.safeText(raw, fallback);
        tt = GUIManager.color(tt);
        if (tt.length() > 32) tt = tt.substring(0, 32);
        if (tt.endsWith("§")) tt = tt.substring(0, tt.length() - 1);
        return tt;
    }

    // --------------------------------------------------
    // ENTRY POINT
    // --------------------------------------------------

    public void open(Player player) {
        Plot standingPlot = plugin.store().getPlotAt(player.getLocation());
        if (standingPlot != null && standingPlot.canManage(player, plugin)) {
            openRolesMenu(player, standingPlot);
            return;
        }

        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        if (plots == null || plots.isEmpty()) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }

        if (plots.size() > 1) openPlotSelector(player, plots, 0);
        else openRolesMenu(player, plots.get(0));
    }

    // --------------------------------------------------
    // GUI 1: SELECT PLOT
    // --------------------------------------------------

    private void openPlotSelector(Player player, List<Plot> plots, int page) {
        if (plots == null) plots = List.of();

        int maxPage = Math.max(0, (int) Math.ceil(plots.size() / (double) PLOTS_PER_PAGE) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));

        String title = plugin.gui().title(player, "trusted_plot_selector_title", "&8Select Plot");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(safePage), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int start = safePage * PLOTS_PER_PAGE;
        int end = Math.min(plots.size(), start + PLOTS_PER_PAGE);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            Plot plot = plots.get(idx);
            if (slot >= PLOTS_PER_PAGE) break;

            List<String> lore = new ArrayList<>();

            String worldLine = t(player, "trusted_plot_world_line",
                    Map.of("WORLD", plot.getWorld()),
                    "&7World: &f{WORLD}"
            );
            lore.add(GUIManager.color(worldLine));

            int sizeX = (plot.getX2() - plot.getX1()) + 1;
            int sizeZ = (plot.getZ2() - plot.getZ1()) + 1;

            String sizeLine = t(player, "trusted_plot_size_line",
                    Map.of("X", String.valueOf(sizeX), "Z", String.valueOf(sizeZ)),
                    "&7Size: &e{X}x{Z}"
            );
            lore.add(GUIManager.color(sizeLine));

            lore.add(" ");

            String clickLine = t(player, "trusted_plot_click_manage", "&eClick to Manage Roles");
            lore.add(GUIManager.color(clickLine));

            String name = t(player, "trusted_plot_name",
                    Map.of("INDEX", String.valueOf(idx + 1)),
                    "&aPlot #{INDEX}"
            );

            inv.setItem(slot++, GUIManager.createItem(
                    Material.GRASS_BLOCK,
                    GUIManager.color(name),
                    lore
            ));
        }

        // Page controls (48/51/52)
        if (safePage > 0) {
            inv.setItem(48, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_prev", "&fPrevious Page"),
                    tl(player, "button_prev_lore", List.of("&7Go to the previous page."))
            ));
        }
        if (safePage < maxPage) {
            inv.setItem(51, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_next", "&fNext Page"),
                    tl(player, "button_next_lore", List.of("&7Go to the next page."))
            ));
        }
        inv.setItem(52, GUIManager.createItem(
                Material.PAPER,
                t(player, "button_page",
                        Map.of("PAGE", (safePage + 1) + "/" + (maxPage + 1)),
                        "&7Page: &f" + (safePage + 1) + "/" + (maxPage + 1)
                ),
                List.of(GUIManager.color("&7 "))
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --------------------------------------------------
    // GUI 2: ROLES LIST
    // --------------------------------------------------

    public void openRolesMenu(Player player, Plot plot) {
        openRolesMenu(player, plot, 0);
    }

    public void openRolesMenu(Player player, Plot plot, int page) {
        if (plot == null) {
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "roles_gui_title", "&eManage Plot Roles");
        Inventory inv = Bukkit.createInventory(new RolesMenuHolder(plot, page), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        Map<UUID, String> roleMap = plot.getPlayerRoles();
        if (roleMap == null) roleMap = new LinkedHashMap<>();

        List<UUID> members = buildEligibleMemberList(plot, player, roleMap);

        int maxPage = Math.max(0, (int) Math.ceil(members.size() / (double) MEMBERS_PER_PAGE) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));

        int start = safePage * MEMBERS_PER_PAGE;
        int end = Math.min(members.size(), start + MEMBERS_PER_PAGE);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            if (slot >= 45) break;

            UUID uuid = members.get(idx);
            String role = roleMap.get(uuid);

            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String name = (member.getName() != null) ? member.getName() : "Unknown";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);

                String nickname = plot.getRoleNickname(uuid);
                String shown = (nickname != null && !nickname.isBlank()) ? nickname : name;
                String displayName = t(player, "roles_member_name",
                        Map.of("PLAYER", shown),
                        "&e{PLAYER}"
                );
                meta.setDisplayName(GUIManager.color(displayName));

                List<String> lore = new ArrayList<>();

                // v1.2.6: Use role display name
                String roleDisplay = getRoleDisplayName(role);
                String roleLine = t(player, "roles_member_role_line",
                        Map.of("ROLE", roleDisplay),
                        "&7Role: &f{ROLE}"
                );
                lore.add(GUIManager.color(roleLine));
                if (nickname != null && !nickname.isBlank()) {
                    lore.add(GUIManager.color(t(player, "roles_member_account_line",
                            Map.of("PLAYER", name),
                            "&7Account: &f{PLAYER}")));
                }
                lore.add(" ");

                String clickLine = t(player, "roles_member_click_lore", "&eClick to Edit Role & Permissions");
                lore.add(GUIManager.color(clickLine));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        inv.setItem(45, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                t(player, "roles_guide_name", "&eRoles & Access Guide"),
                tl(player, "roles_guide_lore", List.of(
                        "&71. Add a nearby player",
                        "&72. Assign a role from the list",
                        "&73. Optional: set a nickname label",
                        "&74. Edit role flags if needed",
                        " ",
                        "&8Owner is not assignable here.",
                        "&8Capacity limits new trusted members."
                ))
        ));
        inv.setItem(47, GUIManager.createItem(
                Material.ARMOR_STAND,
                t(player, "roles_capacity_name", "&bTerritory Capacity"),
                tl(player, "roles_capacity_lore", List.of(
                        "&7Assigned members: &f{USED}",
                        "&7Current capacity: &f{MAX}",
                        "&8Plot Ascension unlocks more capacity."
                )).stream().map(line -> line
                        .replace("{USED}", String.valueOf(plot.countTrustedMembers()))
                        .replace("{MAX}", String.valueOf(plot.getMaxMembers()))).toList()
        ));
        inv.setItem(51, GUIManager.createItem(
                Material.BOOKSHELF,
                t(player, "roles_permission_model_name", "&dPermission Model"),
                tl(player, "roles_permission_model_lore", List.of(
                        "&aAllow &7overrides a plot restriction.",
                        "&cDeny &7always blocks the action.",
                        "&fInherit &7uses normal plot behavior."
                ))
        ));
        inv.setItem(44, GUIManager.createItem(Material.IRON_SWORD,
                t(player, "roles_moderation_name", "&cKick / Ban"),
                tl(player, "roles_moderation_lore", List.of(
                        "&7Manage removals and bans for",
                        "&7this territory."
                ))));

        // Page controls in bottom row (46 prev, 52 next, 53 page)
        if (safePage > 0) {
            inv.setItem(46, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_prev", "&fPrevious Page"),
                    tl(player, "button_prev_lore", List.of("&7Go to the previous page."))
            ));
        }
        if (safePage < maxPage) {
            inv.setItem(52, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_next", "&fNext Page"),
                    tl(player, "button_next_lore", List.of("&7Go to the next page."))
            ));
        }
        inv.setItem(53, GUIManager.createItem(
                Material.PAPER,
                t(player, "button_page",
                        Map.of("PAGE", (safePage + 1) + "/" + (maxPage + 1)),
                        "&7Page: &f" + (safePage + 1) + "/" + (maxPage + 1)
                ),
                List.of(GUIManager.color("&7 "))
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.EMERALD,
                t(player, "button_add_trusted", "&aAdd Trusted Player"),
                tl(player, "add_trusted_lore", List.of("&7Invite a nearby player to this dominion."))
        ));

        inv.setItem(48, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))
        ));

        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private List<UUID> buildEligibleMemberList(Plot plot, Player viewer, Map<UUID, String> roleMap) {
        List<UUID> members = new ArrayList<>();
        if (roleMap == null || roleMap.isEmpty()) return members;

        for (UUID uuid : roleMap.keySet()) {
            if (uuid == null) continue;
            if (isProtectedRoleTarget(plot, uuid)) continue;
            if (viewer != null && uuid.equals(viewer.getUniqueId())) continue;

            String role = roleMap.get(uuid);
            if (role == null || role.isBlank()) continue;
            if (role.equalsIgnoreCase("visitor") || role.equalsIgnoreCase("default") || role.equalsIgnoreCase("none")) continue;

            members.add(uuid);
        }

        // Deterministic ordering: by player name (case-insensitive), fallback UUID
        members.sort(Comparator
                .comparing((UUID u) -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    String n = op.getName();
                    return n == null ? "{" + u + "}" : n.toLowerCase(Locale.ROOT);
                })
                .thenComparing(UUID::toString)
        );

        return members;
    }

    // --------------------------------------------------
    // GUI 3: ADD PLAYER
    // --------------------------------------------------

    private void openAddMenu(Player player, Plot plot) {
        openAddMenu(player, plot, 0);
    }

    private void openAddMenu(Player player, Plot plot, int page) {
        String title = plugin.gui().title(player, "add_trusted_title", "&8Add Trusted Player");
        Inventory inv = Bukkit.createInventory(new RoleAddHolder(plot, page), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        List<Player> candidates = buildAddCandidates(player, plot);

        int maxPage = Math.max(0, (int) Math.ceil(candidates.size() / (double) PLAYERS_PER_PAGE) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));

        int start = safePage * PLAYERS_PER_PAGE;
        int end = Math.min(candidates.size(), start + PLAYERS_PER_PAGE);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            if (slot >= 45) break;

            Player nearby = candidates.get(idx);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(nearby);

                String headName = t(player, "add_trusted_player_name",
                        Map.of("PLAYER", nearby.getName()),
                        "&a{PLAYER}"
                );

                String clickLore = t(player, "add_trusted_click_lore", "&7Click to add to plot.");
                meta.setDisplayName(GUIManager.color(headName));
                meta.setLore(List.of(GUIManager.color(clickLore)));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (candidates.isEmpty()) {
            String noneName = t(player, "add_trusted_none_title", "&cNo Players Nearby");
            List<String> noneLore = tl(player, "add_trusted_none_lore", List.of("&7Ask your friend to stand closer!"));
            inv.setItem(22, GUIManager.createItem(Material.BARRIER, noneName, noneLore));
        }

        inv.setItem(45, GUIManager.createItem(
                Material.NAME_TAG,
                t(player, "roles_add_by_name", "&eAdd by Name"),
                tl(player, "roles_add_by_name_lore", List.of(
                        "&7Add an online or known player",
                        "&7without requiring them nearby.",
                        " ",
                        "&eClick, then type their name in chat."
                ))
        ));

        // Page controls
        if (safePage > 0) {
            inv.setItem(48, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_prev", "&fPrevious Page"),
                    tl(player, "button_prev_lore", List.of("&7Go to the previous page."))
            ));
        }
        if (safePage < maxPage) {
            inv.setItem(51, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_next", "&fNext Page"),
                    tl(player, "button_next_lore", List.of("&7Go to the next page."))
            ));
        }
        inv.setItem(52, GUIManager.createItem(
                Material.PAPER,
                t(player, "button_page",
                        Map.of("PAGE", (safePage + 1) + "/" + (maxPage + 1)),
                        "&7Page: &f" + (safePage + 1) + "/" + (maxPage + 1)
                ),
                List.of(GUIManager.color("&7 "))
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))
        ));

        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 4: MANAGE SPECIFIC PLAYER
    // --------------------------------------------------

    private void openManageMenu(Player player, Plot plot, OfflinePlayer target) {
        openManageMenu(player, plot, target, 0);
    }

    private void openManageMenu(Player player, Plot plot, OfflinePlayer target, int page) {
        UUID targetId = (target == null) ? null : target.getUniqueId();
        if (!canManagePlot(player, plot) || targetId == null || !plot.canModifyMember(player, targetId, plugin)) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }

        String targetName = (target.getName() != null) ? target.getName() : "Unknown";

        String rawTitle = t(player, "roles_manage_title",
                Map.of("PLAYER", targetName),
                "&8Manage: {PLAYER}"
        );

        String title = clampTitle(rawTitle, "&8Manage: " + targetName);
        Inventory inv = Bukkit.createInventory(new RoleManageHolder(plot, target, page), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> roles = getRoleNamesSortedByPriority();
        String currentRole = plot.getRole(target.getUniqueId());

        int maxPage = Math.max(0, (int) Math.ceil(roles.size() / (double) ROLES_VISIBLE) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));

        int start = safePage * ROLES_VISIBLE;
        int end = Math.min(roles.size(), start + ROLES_VISIBLE);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            String roleName = roles.get(idx);
            boolean isCurrent = roleName != null && currentRole != null && roleName.equalsIgnoreCase(currentRole);

            Material icon = isCurrent ? Material.LIME_DYE : Material.GRAY_DYE;

            String displayRoleName = t(player, "roles_role_name",
                    Map.of("ROLE", getRoleDisplayName(player, roleName)),
                    (isCurrent ? "&a" : "&7") + getRoleDisplayName(player, roleName)
            );

            String loreLine = t(player,
                    isCurrent ? "roles_role_current_lore" : "roles_role_click_set_lore",
                    isCurrent ? "&a(Current Role)" : "&eClick to Set"
            );

            inv.setItem(slot++, GUIManager.createItem(
                    icon,
                    GUIManager.color(displayRoleName),
                    List.of(GUIManager.color(loreLine))
            ));
        }

        // Back
        inv.setItem(18, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the roles list."))
        ));
        inv.setItem(20, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        // Paging for role list (19 prev, 25 next, 26 page)
        if (safePage > 0) {
            inv.setItem(19, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_prev", "&fPrevious Page"),
                    tl(player, "button_prev_lore", List.of("&7Go to the previous page."))
            ));
        }
        if (safePage < maxPage) {
            inv.setItem(25, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_next", "&fNext Page"),
                    tl(player, "button_next_lore", List.of("&7Go to the next page."))
            ));
        }
        inv.setItem(26, GUIManager.createItem(
                Material.PAPER,
                t(player, "button_page",
                        Map.of("PAGE", (safePage + 1) + "/" + (maxPage + 1)),
                        "&7Page: &f" + (safePage + 1) + "/" + (maxPage + 1)
                ),
                List.of(GUIManager.color("&7 "))
        ));

        // Remove trusted
        inv.setItem(22, GUIManager.createItem(
                Material.REDSTONE_BLOCK,
                t(player, "button_remove_trusted", "&cRemove Trusted"),
                tl(player, "remove_trusted_lore", List.of("&7Revoke all access."))
        ));

        String nickname = plot.getRoleNickname(target.getUniqueId());
        List<String> nickLore = new ArrayList<>(tl(player, "roles_nickname_button_lore", List.of(
                "&7Set a plot-local display label",
                "&7for this member (max 24 chars).",
                " ",
                "&eClick, then type in chat",
                "&8Type &fcancel &8to abort."
        )));
        if (nickname != null && !nickname.isBlank()) {
            nickLore.add(0, GUIManager.color(t(player, "roles_nickname_current_line",
                    Map.of("NICK", nickname),
                    "&7Current: &f{NICK}")));
            nickLore.add(1, " ");
        }
        inv.setItem(21, GUIManager.createItem(
                Material.NAME_TAG,
                t(player, "button_roles_nickname", "&eRename Nickname"),
                nickLore
        ));

        String roleDisplay = (currentRole != null && !currentRole.equalsIgnoreCase("visitor"))
                ? getRoleDisplayName(player, currentRole)
                : t(player, "roles_unassigned", "Unassigned");

        // ✅ FULLY TRANSLATABLE LORE WITH {ROLE}
        List<String> permsLore = tl(player, "role_permissions_lore",
                Map.of("ROLE", roleDisplay),
                List.of(
                        "&7Role: &f{ROLE}",
                        " ",
                        "&7Adjust what this role may do",
                        "&7inside this dominion.",
                        " ",
                        "&eClick to open role flags"
                )
        );

        inv.setItem(24, GUIManager.createItem(
                Material.BOOK,
                t(player, "button_role_permissions", "&bEdit Role Permissions"),
                permsLore
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 5: ROLE FLAG PERMISSIONS
    // --------------------------------------------------

    private void openRoleFlagsMenu(Player player, Plot plot, OfflinePlayer target, String roleName) {
        UUID targetId = (target == null) ? null : target.getUniqueId();
        if (!canManagePlot(player, plot) || targetId == null || !plot.canModifyMember(player, targetId, plugin)) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }

        if (roleName == null || roleName.trim().isEmpty() || roleName.equalsIgnoreCase("visitor")) {
            plugin.msg().send(player, "role_self");
            plugin.effects().playError(player);
            openManageMenu(player, plot, target);
            return;
        }

        String rawTitle = t(player, "role_flags_title",
                Map.of("ROLE", getRoleDisplayName(player, roleName)),
                "&8Role Flags: {ROLE}"
        );
        String title = clampTitle(rawTitle, "&8Role Flags: " + roleName);

        Inventory inv = Bukkit.createInventory(new RoleFlagsHolder(plot, target, roleName), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int slot = 0;
        for (String flagKey : ROLE_FLAG_KEYS) {
            if (slot >= 18) break;

            TriState state = TriState.INHERIT;
            try {
                TriState stored = plugin.store().getRoleFlagState(plot, roleName, flagKey);
                if (stored != null) state = stored;
            } catch (NoSuchMethodError ignored) {}

            Material icon = mapFlagToIcon(flagKey);
            inv.setItem(slot++, buildRoleFlagItem(player, flagKey, state, icon));
        }

        inv.setItem(22, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))
        ));
        inv.setItem(20, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        inv.setItem(26, GUIManager.createItem(
                Material.PAPER,
                t(player, "role_flags_legend_title", "&7Legend"),
                tl(player, "role_flags_legend_lore", List.of(
                        "&aAllow &7= Role may bypass this rule.",
                        "&cDeny  &7= Role is always blocked.",
                        "&7Inherit &7= Follow normal claim logic."
                ))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // HANDLERS (HARDENED)
    // --------------------------------------------------

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    private boolean canManagePlot(Player actor, Plot plot) {
        if (actor == null || plot == null) return false;
        return plot.canManage(actor, plugin);
    }

    private boolean isProtectedRoleTarget(Plot plot, UUID uuid) {
        if (plot == null || uuid == null) return true;
        return plot.isOwner(uuid) || Plot.SERVER_OWNER_UUID.equals(uuid);
    }

    private List<Player> buildAddCandidates(Player player, Plot plot) {
        List<Player> candidates = new ArrayList<>();
        if (player == null || plot == null || player.getWorld() == null) return candidates;

        Map<UUID, String> currentRoles = plot.getPlayerRoles();
        if (currentRoles == null) currentRoles = new LinkedHashMap<>();

        double radius = 50.0;
        for (org.bukkit.entity.Entity nearbyEntity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearbyEntity instanceof Player nearby)) continue;
            if (nearby.equals(player)) continue;
            if (nearby.getWorld() == null || !nearby.getWorld().equals(player.getWorld())) continue;
            if (isProtectedRoleTarget(plot, nearby.getUniqueId())) continue;
            if (currentRoles.containsKey(nearby.getUniqueId())) continue;
            candidates.add(nearby);
        }

        candidates.sort(Comparator
                .comparingDouble((Player nearby) -> nearby.getLocation().distanceSquared(player.getLocation()))
                .thenComparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        return candidates;
    }

    public void handlePlotSelectorClick(Player player, InventoryClickEvent e, PlotSelectorHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); return; }

        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        if (plots == null) plots = List.of();

        int page = holder.getPage();
        int maxPage = Math.max(0, (int) Math.ceil(plots.size() / (double) PLOTS_PER_PAGE) - 1);

        if (slot == 48 && page > 0) { openPlotSelector(player, plots, page - 1); return; }
        if (slot == 51 && page < maxPage) { openPlotSelector(player, plots, page + 1); return; }

        if (slot >= PLOTS_PER_PAGE) return;

        int index = (page * PLOTS_PER_PAGE) + slot;
        if (index >= 0 && index < plots.size()) openRolesMenu(player, plots.get(index), 0);
    }

    public void handleRolesMenuClick(Player player, InventoryClickEvent e, RolesMenuHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        int currentPage = holder.getPage();
        int slot = e.getRawSlot();

        if (slot == 49) {
            if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }
            openAddMenu(player, plot, 0);
            return;
        }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); return; }
        if (slot == 44) {
            if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }
            plugin.gui().moderation().open(player, plot);
            return;
        }

        int page = holder.getPage();

        // Paging controls
        Map<UUID, String> roleMap = plot.getPlayerRoles();
        if (roleMap == null) roleMap = new LinkedHashMap<>();
        List<UUID> members = buildEligibleMemberList(plot, player, roleMap);

        int maxPage = Math.max(0, (int) Math.ceil(members.size() / (double) MEMBERS_PER_PAGE) - 1);
        if (slot == 46 && page > 0) { openRolesMenu(player, plot, page - 1); return; }
        if (slot == 52 && page < maxPage) { openRolesMenu(player, plot, page + 1); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetId = meta.getOwningPlayer().getUniqueId();
                if (!plot.canModifyMember(player, targetId, plugin)) {
                    plugin.effects().playError(player);
                    return;
                }
                openManageMenu(player, plot, meta.getOwningPlayer(), 0);
            }
        }
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, RoleAddHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

        int slot = e.getRawSlot();
        int page = holder.getPage();

        if (slot == 49) { openRolesMenu(player, plot, 0); return; }
        if (slot == 50) { player.closeInventory(); return; }
        if (slot == 45) {
            beginAddByNamePrompt(player, plot);
            return;
        }

        // Paging buttons
        List<Player> candidates = buildAddCandidates(player, plot);

        int maxPage = Math.max(0, (int) Math.ceil(candidates.size() / (double) PLAYERS_PER_PAGE) - 1);
        if (slot == 48 && page > 0) { openAddMenu(player, plot, page - 1); return; }
        if (slot == 51 && page < maxPage) { openAddMenu(player, plot, page + 1); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                UUID targetId = meta.getOwningPlayer().getUniqueId();
                if (!plot.canModifyMember(player, targetId, plugin)) {
                    plugin.effects().playError(player);
                    openAddMenu(player, plot, page);
                    return;
                }
                openManageMenu(player, plot, meta.getOwningPlayer(), 0);
            }
        }
    }

    public void handleManageRoleClick(Player player, InventoryClickEvent e, RoleManageHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        int slot = e.getRawSlot();

        if (!canManagePlot(player, plot) || target == null || target.getUniqueId() == null) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }
        if (!plot.canModifyMember(player, target.getUniqueId(), plugin)) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }

        // Back
        if (slot == 18) { openRolesMenu(player, plot, 0); return; }
        if (slot == 20) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        // Paging
        List<String> roles = getRoleNamesSortedByPriority();
        int page = holder.getPage();
        int maxPage = Math.max(0, (int) Math.ceil(roles.size() / (double) ROLES_VISIBLE) - 1);

        if (slot == 19 && page > 0) { openManageMenu(player, plot, target, page - 1); return; }
        if (slot == 25 && page < maxPage) { openManageMenu(player, plot, target, page + 1); return; }

        // Nickname rename via chat prompt
        if (slot == 21) {
            beginNicknamePrompt(player, plot, target);
            return;
        }

        // Remove trusted
        if (slot == 22) {
            // v1.2.6: Safety check - prevent owner self-removal and enforce admin override
            if (!plot.canModifyMember(player, target.getUniqueId())) {
                if (player.getUniqueId().equals(target.getUniqueId())) {
                    plugin.msg().send(player, "role_cannot_remove_self", Map.of());
                } else {
                    plugin.msg().send(player, "role_no_permission", Map.of());
                }
                plugin.effects().playError(player);
                return;
            }

            plugin.store().removePlayerRole(plot, target.getUniqueId());
            plugin.msg().send(player, "role_removed", Map.of("PLAYER", safeName(target)));
            plugin.effects().playUnclaim(player);
            openRolesMenu(player, plot, 0);
            return;
        }

        // Role flags submenu
        if (slot == 24) {
            String currentRole = plot.getPlayerRoles().get(target.getUniqueId());
            if (currentRole == null || currentRole.isBlank() || currentRole.equalsIgnoreCase("visitor")) {
                plugin.msg().send(player, "role_self");
                plugin.effects().playError(player);
                return;
            }
            openRoleFlagsMenu(player, plot, target, currentRole);
            return;
        }

        // Role selection (0..17 on current page)
        if (slot >= 0 && slot < ROLES_VISIBLE) {
            int index = (page * ROLES_VISIBLE) + slot;
            if (index < 0 || index >= roles.size()) return;

            String newRole = roles.get(index);
            if (newRole == null || newRole.isBlank()) return;
            if (newRole.equalsIgnoreCase("owner")) {
                plugin.effects().playError(player);
                plugin.msg().send(player, "roles_owner_not_assignable", Map.of());
                return;
            }

            String existing = plot.getRole(target.getUniqueId());
            boolean alreadyCounted = existing != null
                    && !existing.isBlank()
                    && !existing.equalsIgnoreCase("visitor")
                    && !existing.equalsIgnoreCase("default")
                    && !existing.equalsIgnoreCase("none");
            if (!alreadyCounted && plot.isAtMemberCapacity()) {
                plugin.effects().playError(player);
                player.sendMessage(GUIManager.color(t(player, "roles_capacity_full",
                        Map.of("MAX", String.valueOf(plot.getMaxMembers())),
                        "&cThis plot is at capacity (&f{MAX}&c members).")));
                return;
            }

            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", safeName(target), "ROLE", getRoleDisplayName(player, newRole)));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot, 0);
        }
    }

    private void beginNicknamePrompt(Player player, Plot plot, OfflinePlayer target) {
        if (player == null || plot == null || target == null || target.getUniqueId() == null) return;
        pendingNicknames.put(player.getUniqueId(), new NicknamePrompt(plot.getPlotId(), target.getUniqueId()));
        player.closeInventory();
        player.sendMessage(GUIManager.color(t(player, "roles_nickname_prompt",
                Map.of("PLAYER", safeName(target)),
                "&eType a nickname for &f{PLAYER}&e in chat (max 24), or &fcancel&e.")));
        plugin.effects().playMenuFlip(player);
    }

    private void beginAddByNamePrompt(Player player, Plot plot) {
        if (player == null || plot == null) return;
        pendingNames.put(player.getUniqueId(), new AddByNamePrompt(plot.getPlotId()));
        player.closeInventory();
        player.sendMessage(GUIManager.color(t(player, "roles_add_by_name_prompt",
                "&eType a player name in chat, or &fcancel&e.")));
        plugin.effects().playMenuFlip(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNicknameChat(AsyncPlayerChatEvent e) {
        NicknamePrompt prompt = pendingNicknames.remove(e.getPlayer().getUniqueId());
        AddByNamePrompt namePrompt = pendingNames.remove(e.getPlayer().getUniqueId());
        if (prompt == null && namePrompt == null) return;
        e.setCancelled(true);

        Player player = e.getPlayer();
        String raw = e.getMessage() == null ? "" : e.getMessage().trim();
        plugin.runMain(player, () -> {
            if (prompt != null) finishNicknamePrompt(player, prompt, raw);
            else finishAddByNamePrompt(player, namePrompt, raw);
        });
    }

    @EventHandler
    public void onNicknameQuit(PlayerQuitEvent e) {
        pendingNicknames.remove(e.getPlayer().getUniqueId());
        pendingNames.remove(e.getPlayer().getUniqueId());
    }

    private void finishAddByNamePrompt(Player player, AddByNamePrompt prompt, String raw) {
        Plot plot = plugin.store().getAllPlots().stream()
                .filter(p -> p != null && prompt.plotId().equals(p.getPlotId()))
                .findFirst().orElse(null);
        if (plot == null || !canManagePlot(player, plot)) {
            plugin.effects().playError(player);
            return;
        }
        if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("c")) {
            openAddMenu(player, plot, 0);
            return;
        }
        if (raw.isBlank() || raw.length() > 16) {
            player.sendMessage(GUIManager.color("&cEnter a valid Minecraft player name."));
            openAddMenu(player, plot, 0);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(raw);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(GUIManager.color("&cThat player has not played on this server."));
            plugin.effects().playError(player);
            openAddMenu(player, plot, 0);
            return;
        }
        if (!plot.canModifyMember(player, target.getUniqueId(), plugin)) {
            plugin.effects().playError(player);
            openAddMenu(player, plot, 0);
            return;
        }
        openManageMenu(player, plot, target, 0);
    }

    private void finishNicknamePrompt(Player player, NicknamePrompt prompt, String raw) {
        Plot plot = plugin.store().getAllPlots().stream()
                .filter(p -> p != null && prompt.plotId().equals(p.getPlotId()))
                .findFirst().orElse(null);
        if (plot == null || !canManagePlot(player, plot)) {
            plugin.effects().playError(player);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(prompt.targetId());
        if (!plot.canModifyMember(player, prompt.targetId(), plugin)) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }

        if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("c")) {
            player.sendMessage(GUIManager.color(t(player, "roles_nickname_cancelled", "&7Nickname rename cancelled.")));
            openManageMenu(player, plot, target, 0);
            return;
        }
        if (raw.equalsIgnoreCase("clear") || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("reset")) {
            plot.clearRoleNickname(prompt.targetId());
            plugin.store().savePlotSync(plot);
            player.sendMessage(GUIManager.color(t(player, "roles_nickname_cleared",
                    Map.of("PLAYER", safeName(target)),
                    "&aCleared nickname for &f{PLAYER}&a.")));
            plugin.effects().playConfirm(player);
            openManageMenu(player, plot, target, 0);
            return;
        }

        plot.setRoleNickname(prompt.targetId(), raw);
        String applied = plot.getRoleNickname(prompt.targetId());
        if (applied == null) {
            plugin.effects().playError(player);
            player.sendMessage(GUIManager.color(t(player, "roles_nickname_invalid",
                    "&cThat nickname is empty or invalid.")));
            openManageMenu(player, plot, target, 0);
            return;
        }
        plugin.store().savePlotSync(plot);
        player.sendMessage(GUIManager.color(t(player, "roles_nickname_set",
                Map.of("PLAYER", safeName(target), "NICK", applied),
                "&aSet nickname for &f{PLAYER}&a to &e{NICK}&a.")));
        plugin.effects().playConfirm(player);
        openManageMenu(player, plot, target, 0);
    }

    public void handleRoleFlagsClick(Player player, InventoryClickEvent e, RoleFlagsHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        String roleName = holder.getRoleName();
        int slot = e.getRawSlot();

        if (!canManagePlot(player, plot) || target == null || target.getUniqueId() == null) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }
        if (!plot.canModifyMember(player, target.getUniqueId(), plugin)) {
            plugin.effects().playError(player);
            openRolesMenu(player, plot, 0);
            return;
        }

        if (slot == 22) { openManageMenu(player, plot, target, 0); return; }
        if (slot == 20) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot < 0 || slot >= ROLE_FLAG_KEYS.size()) return;

        String flagKey = ROLE_FLAG_KEYS.get(slot);

        TriState current = TriState.INHERIT;
        try {
            TriState stored = plugin.store().getRoleFlagState(plot, roleName, flagKey);
            if (stored != null) current = stored;
        } catch (NoSuchMethodError ignored) {}

        TriState next = nextTriState(current);

        try {
            plugin.store().setRoleFlagState(plot, roleName, flagKey, next);
        } catch (NoSuchMethodError ignored) {}

        plugin.effects().playToggle(player);
        openRoleFlagsMenu(player, plot, target, roleName);
    }

    // --------------------------------------------------
    // SMALL HELPERS
    // --------------------------------------------------

    private String safeName(OfflinePlayer p) {
        if (p == null) return "Unknown";
        String n = p.getName();
        return (n == null || n.isBlank()) ? "Unknown" : n;
    }

    private TriState nextTriState(TriState current) {
        if (current == null || current == TriState.INHERIT) return TriState.ALLOW;
        if (current == TriState.ALLOW) return TriState.DENY;
        return TriState.INHERIT;
    }

    private Material mapFlagToIcon(String key) {
        switch (key.toUpperCase(Locale.ROOT)) {
            case "PVP":        return Material.IRON_SWORD;
            case "CONTAINERS": return Material.CHEST;
            case "MOBS":       return Material.ZOMBIE_HEAD;
            case "PETS":       return Material.BONE;
            case "ENTITIES":   return Material.ARMOR_STAND;
            case "FARM":       return Material.WHEAT;
            case "TNT":        return Material.TNT;
            case "FIRE":       return Material.FLINT_AND_STEEL;
            case "PISTON":     return Material.PISTON;
            case "ENTRY":      return Material.OAK_DOOR;
            case "SHOP":       return Material.EMERALD;
            case "FLY":        return Material.FEATHER;
            case "ANIMALS":    return Material.COW_SPAWN_EGG;
            case "REDSTONE":   return Material.REDSTONE;
            case "VEHICLES":   return Material.MINECART;
            default:           return Material.PAPER;
        }
    }

    private ItemStack buildRoleFlagItem(Player player, String flagKey, TriState state, Material icon) {
        String pretty = flagKey.toLowerCase(Locale.ROOT).replace("_", " ");
        if (!pretty.isEmpty()) pretty = Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);

        String name = t(player, "role_flag_name_" + flagKey.toUpperCase(Locale.ROOT), "&b" + pretty);

        String allowLbl   = t(player, "role_flags_state_allow", "&aAllow");
        String denyLbl    = t(player, "role_flags_state_deny", "&cDeny");
        String inheritLbl = t(player, "role_flags_state_inherit", "&7Inherit");

        String stateLabel = (state == TriState.ALLOW) ? allowLbl : (state == TriState.DENY) ? denyLbl : inheritLbl;

        String currentLine = t(player, "role_flags_current_line",
                Map.of("STATE", stateLabel),
                "&7Current: {STATE}"
        );

        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(currentLine));
        lore.add(" ");

        lore.addAll(tl(player, "role_flags_item_lore", List.of(
                "&7Inherit: Follow normal plot/world rules.",
                "&7Allow: This role may bypass this restriction.",
                "&7Deny: This role is always blocked.",
                " ",
                "&eClick to cycle"
        )));

        return GUIManager.createItem(icon, GUIManager.color(name), lore);
    }

    // --------------------------------------------------
    // 1.2.6 Role QoL: Priority + Display Name (config-safe)
    // --------------------------------------------------

    private List<String> getRoleNamesSortedByPriority() {
        List<String> roles = plugin.cfg().getRoleNames();
        if (roles == null) roles = List.of();

        List<String> out = new ArrayList<>(roles);
        out.removeIf(r -> r == null || r.trim().isEmpty() || r.equalsIgnoreCase("owner"));

        out.sort(Comparator
                .comparingInt((String r) -> rolePriority(configRoleKey(r))).reversed()
                .thenComparing(r -> r.toLowerCase(Locale.ROOT))
        );

        return out;
    }

    private String getRoleDisplayName(Player viewer, String role) {
        if (role == null || role.isBlank()) {
            return t(viewer, "roles_unassigned", "Unassigned");
        }

        // Optional: config-driven display name
        String key = configRoleKey(role);
        try {
            String cfgDisp = plugin.cfg() != null ? plugin.cfg().raw().getString("roles." + key + ".display", null) : null;
            if (cfgDisp != null && !cfgDisp.trim().isEmpty()) return GUIManager.color(cfgDisp);
        } catch (Throwable ignored) {}

        // Optional: translation key per role
        String trKey = "role_display_" + key;
        String translated = t(viewer, trKey, role);
        if (translated != null && !translated.isBlank() && !translated.equalsIgnoreCase(trKey)) {
            return translated;
        }

        return role;
    }

    private String configRoleKey(String role) {
        // Normalize to a stable config key: "Plot Steward" -> "plot_steward"
        String s = role.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
        s = s.replaceAll("[^a-z0-9_]", "");
        if (s.isBlank()) s = "role";
        return s;
    }

    private int rolePriority(String roleKey) {
        try {
            if (plugin.cfg() != null) {
                return plugin.cfg().raw().getInt("roles." + roleKey + ".priority", 0);
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
