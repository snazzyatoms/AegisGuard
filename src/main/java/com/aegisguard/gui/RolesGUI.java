package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.flags.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
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

/**
 * RolesGUI
 * - Manage trusted players and their permission levels.
 * - Also manages per-role flag permissions via a submenu.
 * - Fully localized (Codex-backed) with safe fallbacks + title clamp.
 */
public class RolesGUI {

    private final AegisGuard plugin;

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
        if (plugin.isAdmin(player)) {
            Plot standingPlot = plugin.store().getPlotAt(player.getLocation());
            if (standingPlot != null) {
                openRolesMenu(player, standingPlot);
                return;
            }
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
                t(player, "button_page", "&7Page: &f{PAGE}",
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

            String roleDisplay = getRoleDisplayName(player, role);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);

                String displayName = t(player, "roles_member_name",
                        Map.of("PLAYER", name),
                        "&e{PLAYER}"
                );
                meta.setDisplayName(GUIManager.color(displayName));

                List<String> lore = new ArrayList<>();

                String roleLine = t(player, "roles_member_role_line",
                        Map.of("ROLE", roleDisplay),
                        "&7Role: &f{ROLE}"
                );
                lore.add(GUIManager.color(roleLine));
                lore.add(" ");

                String clickLine = t(player, "roles_member_click_lore", "&eClick to Edit Role & Permissions");
                lore.add(GUIManager.color(clickLine));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

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
                t(player, "button_page", "&7Page: &f{PAGE}",
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

        boolean isAdmin = plugin.isAdmin(viewer);
        UUID owner = plot.getOwner();

        for (UUID uuid : roleMap.keySet()) {
            if (uuid == null) continue;

            boolean isOwnerEntry = owner != null && uuid.equals(owner);
            boolean isViewerOwner = uuid.equals(viewer.getUniqueId());

            // Preserve your original behavior:
            // - Hide owner's own entry when owner is viewing
            // - Hide owner entry for non-admin viewers
            if (isOwnerEntry && isViewerOwner) continue;
            if (isOwnerEntry && !isAdmin) continue;

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

        Map<UUID, String> currentRoles = plot.getPlayerRoles();
        if (currentRoles == null) currentRoles = new LinkedHashMap<>();

        // Build nearby candidates (distanceSquared optimization)
        List<Player> candidates = new ArrayList<>();
        double maxDistSq = 50.0 * 50.0;

        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby == null) continue;
            if (nearby.equals(player)) continue;
            if (currentRoles.containsKey(nearby.getUniqueId())) continue;

            if (nearby.getLocation().distanceSquared(player.getLocation()) > maxDistSq) continue;
            candidates.add(nearby);
        }

        candidates.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));

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
                t(player, "button_page", "&7Page: &f{PAGE}",
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
                t(player, "button_page", "&7Page: &f{PAGE}",
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

        String roleDisplay = (currentRole != null)
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
        if (roleName == null || roleName.trim().isEmpty()) {
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
        if (plugin.isAdmin(actor)) return true;
        UUID owner = plot.getOwner();
        return owner != null && owner.equals(actor.getUniqueId());
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
        int slot = e.getRawSlot();

        if (slot == 49) {
            if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }
            openAddMenu(player, plot, 0);
            return;
        }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); return; }

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
            if (meta != null && meta.getOwningPlayer() != null) openManageMenu(player, plot, meta.getOwningPlayer(), 0);
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

        // Paging buttons
        // Rebuild candidate list size for maxPage (same logic as openAddMenu)
        Map<UUID, String> currentRoles = plot.getPlayerRoles();
        if (currentRoles == null) currentRoles = new LinkedHashMap<>();

        List<Player> candidates = new ArrayList<>();
        double maxDistSq = 50.0 * 50.0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby == null) continue;
            if (nearby.equals(player)) continue;
            if (currentRoles.containsKey(nearby.getUniqueId())) continue;
            if (nearby.getLocation().distanceSquared(player.getLocation()) > maxDistSq) continue;
            candidates.add(nearby);
        }
        candidates.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));

        int maxPage = Math.max(0, (int) Math.ceil(candidates.size() / (double) PLAYERS_PER_PAGE) - 1);
        if (slot == 48 && page > 0) { openAddMenu(player, plot, page - 1); return; }
        if (slot == 51 && page < maxPage) { openAddMenu(player, plot, page + 1); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) openManageMenu(player, plot, meta.getOwningPlayer(), 0);
        }
    }

    public void handleManageRoleClick(Player player, InventoryClickEvent e, RoleManageHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        int slot = e.getRawSlot();

        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

        // Back
        if (slot == 18) { openRolesMenu(player, plot, 0); return; }

        // Paging
        List<String> roles = getRoleNamesSortedByPriority();
        int page = holder.getPage();
        int maxPage = Math.max(0, (int) Math.ceil(roles.size() / (double) ROLES_VISIBLE) - 1);

        if (slot == 19 && page > 0) { openManageMenu(player, plot, target, page - 1); return; }
        if (slot == 25 && page < maxPage) { openManageMenu(player, plot, target, page + 1); return; }

        // Protect owner from removal/edits unless admin
        UUID owner = plot.getOwner();
        boolean isTargetOwner = owner != null && target != null && owner.equals(target.getUniqueId());
        boolean isAdmin = plugin.isAdmin(player);

        // Remove trusted
        if (slot == 22) {
            if (isTargetOwner && !isAdmin) {
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
            String currentRole = plot.getRole(target.getUniqueId());
            if (currentRole == null) {
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

            if (isTargetOwner && !isAdmin) {
                plugin.effects().playError(player);
                return;
            }

            plugin.store().addPlayerRole(plot, target.getUniqueId(), newRole);
            plugin.msg().send(player, "role_set_to", Map.of("PLAYER", safeName(target), "ROLE", getRoleDisplayName(player, newRole)));
            plugin.effects().playConfirm(player);
            openRolesMenu(player, plot, 0);
        }
    }

    public void handleRoleFlagsClick(Player player, InventoryClickEvent e, RoleFlagsHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        String roleName = holder.getRoleName();
        int slot = e.getRawSlot();

        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

        if (slot == 22) { openManageMenu(player, plot, target, 0); return; }
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
        out.removeIf(r -> r == null || r.trim().isEmpty());

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
