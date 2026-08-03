package com.aegisguard.guestpass;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 2 (Temporary Guest Passes) player-facing GUI.
 *
 * Flow: active-pass list -&gt; add (pick nearby player) -&gt; pick preset -&gt; pick duration -&gt;
 * pick expiry mode (real-time vs active playtime) -&gt; confirm (with an explicit container-access
 * warning for Temporary Trusted Guest) -&gt; issued.
 * Clicking an existing pass opens a detail screen with a Revoke control.
 *
 * Passes are entirely separate from {@code RolesGUI}'s permanent trust list: issuing, revoking, or
 * letting a pass expire never touches a player's permanent role.
 */
public class GuestPassGUI implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, AddByNamePrompt> pendingNames = new ConcurrentHashMap<>();

    private static final int PASSES_PER_PAGE = 45;
    private static final int PLAYERS_PER_PAGE = 45;

    private record AddByNamePrompt(UUID plotId) {}

    public GuestPassGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------
    // HOLDERS
    // --------------------------------------------------

    public static class GuestPassMenuHolder implements InventoryHolder {
        private final Plot plot;
        private final int page;
        public GuestPassMenuHolder(Plot plot, int page) { this.plot = plot; this.page = page; }
        public Plot getPlot() { return plot; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class GuestPassAddHolder implements InventoryHolder {
        private final Plot plot;
        private final int page;
        public GuestPassAddHolder(Plot plot, int page) { this.plot = plot; this.page = page; }
        public Plot getPlot() { return plot; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class GuestPassPresetHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        public GuestPassPresetHolder(Plot plot, OfflinePlayer target) { this.plot = plot; this.target = target; }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class GuestPassDurationHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final GuestPassPreset preset;
        public GuestPassDurationHolder(Plot plot, OfflinePlayer target, GuestPassPreset preset) {
            this.plot = plot; this.target = target; this.preset = preset;
        }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public GuestPassPreset getPreset() { return preset; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class GuestPassModeHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final GuestPassPreset preset;
        private final long minutes;
        public GuestPassModeHolder(Plot plot, OfflinePlayer target, GuestPassPreset preset, long minutes) {
            this.plot = plot; this.target = target; this.preset = preset; this.minutes = minutes;
        }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public GuestPassPreset getPreset() { return preset; }
        public long getMinutes() { return minutes; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class GuestPassConfirmHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        private final GuestPassPreset preset;
        private final long minutes;
        private final GuestPassMode mode;
        public GuestPassConfirmHolder(Plot plot, OfflinePlayer target, GuestPassPreset preset,
                                       long minutes, GuestPassMode mode) {
            this.plot = plot; this.target = target; this.preset = preset;
            this.minutes = minutes; this.mode = mode == null ? GuestPassMode.REAL_TIME : mode;
        }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
        public GuestPassPreset getPreset() { return preset; }
        public long getMinutes() { return minutes; }
        public GuestPassMode getMode() { return mode; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class GuestPassDetailHolder implements InventoryHolder {
        private final Plot plot;
        private final OfflinePlayer target;
        public GuestPassDetailHolder(Plot plot, OfflinePlayer target) { this.plot = plot; this.target = target; }
        public Plot getPlot() { return plot; }
        public OfflinePlayer getTarget() { return target; }
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

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    private boolean canManagePlot(Player actor, Plot plot) {
        return actor != null && plot != null && plot.canManage(actor, plugin);
    }

    private String safeName(OfflinePlayer p) {
        if (p == null) return "Unknown";
        String n = p.getName();
        return (n == null || n.isBlank()) ? "Unknown" : n;
    }

    // --------------------------------------------------
    // ENTRY POINT
    // --------------------------------------------------

    public void open(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }
        if (!canManagePlot(player, plot)) {
            plugin.msg().send(player, "not_plot_owner");
            plugin.effects().playError(player);
            return;
        }
        openMenu(player, plot, 0);
    }

    // --------------------------------------------------
    // GUI 1: ACTIVE PASS LIST
    // --------------------------------------------------

    public void openMenu(Player player, Plot plot) {
        openMenu(player, plot, 0);
    }

    public void openMenu(Player player, Plot plot, int page) {
        if (plot == null) {
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "guest_pass_menu_title", "&eGuest Passes");
        Inventory inv = Bukkit.createInventory(new GuestPassMenuHolder(plot, page), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        long now = System.currentTimeMillis();
        List<GuestPass> passes = new ArrayList<>(plot.getActiveGuestPasses());
        passes.sort(Comparator.comparing(GuestPass::getPlayerName, String.CASE_INSENSITIVE_ORDER));

        int maxPage = Math.max(0, (int) Math.ceil(passes.size() / (double) PASSES_PER_PAGE) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));

        int start = safePage * PASSES_PER_PAGE;
        int end = Math.min(passes.size(), start + PASSES_PER_PAGE);

        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            if (slot >= 45) break;
            GuestPass pass = passes.get(idx);

            OfflinePlayer member = Bukkit.getOfflinePlayer(pass.getPlayerId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);
                meta.setDisplayName(GUIManager.color(t(player, "guest_pass_entry_name",
                        Map.of("PLAYER", pass.getPlayerName()), "&e{PLAYER}")));

                List<String> lore = new ArrayList<>();
                lore.add(GUIManager.color(t(player, "guest_pass_entry_preset_line",
                        Map.of("PRESET", presetLabel(player, pass.getPreset())), "&7Preset: &f{PRESET}")));
                lore.add(GUIManager.color(t(player, "guest_pass_entry_mode_line",
                        Map.of("MODE", modeLabel(player, pass.getMode())), "&7Mode: &f{MODE}")));
                lore.add(GUIManager.color(t(player, remainingLineKey(pass),
                        Map.of("TIME", formatRemaining(player, pass.getRemainingMillis(now))),
                        remainingLineFallback(pass))));
                lore.add(GUIManager.color(t(player, "guest_pass_entry_issuer_line",
                        Map.of("PLAYER", pass.getIssuerName()), "&7Issued by: &f{PLAYER}")));
                lore.add(" ");
                lore.add(GUIManager.color(t(player, "guest_pass_entry_click_lore", "&eClick to view or revoke")));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (passes.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "guest_pass_none_title", "&7No Active Guest Passes"),
                    tl(player, "guest_pass_none_lore", List.of(
                            "&7Issue a temporary pass so a friend,",
                            "&7event guest, or hired builder can",
                            "&7help out without permanent trust."))));
        }

        inv.setItem(45, GUIManager.createItem(Material.WRITABLE_BOOK,
                t(player, "guest_pass_guide_name", "&eGuest Pass Guide"),
                tl(player, "guest_pass_guide_lore", List.of(
                        "&7Guest Passes grant temporary access",
                        "&7that expires automatically - even",
                        "&7after a server restart.",
                        " ",
                        "&7Choose real-time (always ticking) or",
                        "&7active playtime (only while online).",
                        " ",
                        "&8Expiring or revoking a pass never",
                        "&8touches this plot's permanent roles."))));

        if (safePage > 0) {
            inv.setItem(46, GUIManager.createItem(Material.ARROW,
                    t(player, "button_prev", "&fPrevious Page"),
                    tl(player, "button_prev_lore", List.of("&7Go to the previous page."))));
        }
        if (safePage < maxPage) {
            inv.setItem(52, GUIManager.createItem(Material.ARROW,
                    t(player, "button_next", "&fNext Page"),
                    tl(player, "button_next_lore", List.of("&7Go to the next page."))));
        }
        inv.setItem(53, GUIManager.createItem(Material.PAPER,
                t(player, "button_page", Map.of("PAGE", (safePage + 1) + "/" + (maxPage + 1)),
                        "&7Page: &f" + (safePage + 1) + "/" + (maxPage + 1)),
                List.of(GUIManager.color("&7 "))));

        inv.setItem(49, GUIManager.createItem(Material.EMERALD,
                t(player, "button_add_guest_pass", "&aIssue Guest Pass"),
                tl(player, "add_guest_pass_lore", List.of("&7Grant a nearby player temporary access."))));

        inv.setItem(48, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --------------------------------------------------
    // GUI 2: ADD PLAYER
    // --------------------------------------------------

    private void openAddMenu(Player player, Plot plot, int page) {
        String title = plugin.gui().title(player, "guest_pass_add_title", "&8Issue Guest Pass");
        Inventory inv = Bukkit.createInventory(new GuestPassAddHolder(plot, page), 54, title);

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
                meta.setDisplayName(GUIManager.color(t(player, "add_trusted_player_name",
                        Map.of("PLAYER", nearby.getName()), "&a{PLAYER}")));
                meta.setLore(List.of(GUIManager.color(t(player, "guest_pass_add_click_lore", "&7Click to choose a preset."))));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        if (candidates.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "add_trusted_none_title", "&cNo Players Nearby"),
                    tl(player, "add_trusted_none_lore", List.of("&7Ask your friend to stand closer!"))));
        }

        inv.setItem(45, GUIManager.createItem(Material.NAME_TAG,
                t(player, "guest_pass_add_by_name", "&eAdd by Name"),
                tl(player, "guest_pass_add_by_name_lore", List.of(
                        "&7Issue a pass to an online or",
                        "&7previously seen player.",
                        " ",
                        "&eClick, then type their name in chat."))));

        if (safePage > 0) {
            inv.setItem(48, GUIManager.createItem(Material.ARROW,
                    t(player, "button_prev", "&fPrevious Page"),
                    tl(player, "button_prev_lore", List.of("&7Go to the previous page."))));
        }
        if (safePage < maxPage) {
            inv.setItem(51, GUIManager.createItem(Material.ARROW,
                    t(player, "button_next", "&fNext Page"),
                    tl(player, "button_next_lore", List.of("&7Go to the next page."))));
        }
        inv.setItem(52, GUIManager.createItem(Material.PAPER,
                t(player, "button_page", Map.of("PAGE", (safePage + 1) + "/" + (maxPage + 1)),
                        "&7Page: &f" + (safePage + 1) + "/" + (maxPage + 1)),
                List.of(GUIManager.color("&7 "))));

        inv.setItem(49, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    private List<Player> buildAddCandidates(Player player, Plot plot) {
        List<Player> candidates = new ArrayList<>();
        if (player == null || plot == null || player.getWorld() == null) return candidates;

        double radius = 50.0;
        for (org.bukkit.entity.Entity nearbyEntity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearbyEntity instanceof Player nearby)) continue;
            if (nearby.equals(player)) continue;
            if (nearby.getWorld() == null || !nearby.getWorld().equals(player.getWorld())) continue;
            if (plot.isOwner(nearby.getUniqueId()) || Plot.SERVER_OWNER_UUID.equals(nearby.getUniqueId())) continue;
            if (plot.isBanned(nearby.getUniqueId())) continue;
            candidates.add(nearby);
        }

        candidates.sort(Comparator
                .comparingDouble((Player nearby) -> nearby.getLocation().distanceSquared(player.getLocation()))
                .thenComparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        return candidates;
    }

    // --------------------------------------------------
    // GUI 3: PRESET SELECTION
    // --------------------------------------------------

    private void openPresetMenu(Player player, Plot plot, OfflinePlayer target) {
        String rawTitle = t(player, "guest_pass_preset_title", Map.of("PLAYER", safeName(target)), "&8Preset: {PLAYER}");
        String title = clampTitle(rawTitle, "&8Preset: " + safeName(target));
        Inventory inv = Bukkit.createInventory(new GuestPassPresetHolder(plot, target), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int[] slots = {10, 12, 14, 16};
        List<GuestPassPreset> presets = GuestPassPreset.ordered();
        for (int i = 0; i < presets.size() && i < slots.length; i++) {
            GuestPassPreset preset = presets.get(i);
            inv.setItem(slots[i], buildPresetItem(player, preset));
        }

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    private ItemStack buildPresetItem(Player player, GuestPassPreset preset) {
        Material icon = switch (preset) {
            case VISITOR -> Material.FEATHER;
            case EVENT_GUEST -> Material.FIREWORK_ROCKET;
            case TEMPORARY_BUILDER -> Material.DIAMOND_PICKAXE;
            case TEMPORARY_TRUSTED_GUEST -> Material.CHEST;
        };

        List<String> lore = new ArrayList<>(tl(player, "guest_pass_preset_desc_" + preset.name(),
                fallbackPresetDescription(preset)));

        if (preset.requiresContainerWarning()) {
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "guest_pass_preset_container_warning",
                    "&c⚠ Grants container access!")));
        }
        lore.add(" ");
        lore.add(GUIManager.color(t(player, "guest_pass_preset_click_lore", "&eClick to choose a duration")));

        return GUIManager.createItem(icon, presetLabel(player, preset), lore);
    }

    private List<String> fallbackPresetDescription(GuestPassPreset preset) {
        return switch (preset) {
            case VISITOR -> List.of("&7Entry and ordinary doors/buttons only.");
            case EVENT_GUEST -> List.of("&7Entry plus safe event interaction.", "&7No building or containers.");
            case TEMPORARY_BUILDER -> List.of("&7Build and break access.", "&7Containers stay closed.");
            case TEMPORARY_TRUSTED_GUEST -> List.of("&7Build, break, and container access.");
        };
    }

    private String presetLabel(Player player, GuestPassPreset preset) {
        return t(player, "guest_pass_preset_name_" + preset.name(), "&e" + preset.fallbackLabel());
    }

    private String modeLabel(Player player, GuestPassMode mode) {
        GuestPassMode resolved = mode == null ? GuestPassMode.REAL_TIME : mode;
        return t(player, "guest_pass_mode_name_" + resolved.name(), "&f" + resolved.fallbackLabel());
    }

    private String remainingLineKey(GuestPass pass) {
        return pass != null && pass.isActivePlaytime()
                ? "guest_pass_entry_playtime_remaining_line"
                : "guest_pass_entry_remaining_line";
    }

    private String remainingLineFallback(GuestPass pass) {
        return pass != null && pass.isActivePlaytime()
                ? "&7Playtime left: &f{TIME}"
                : "&7Expires in: &f{TIME}";
    }

    // --------------------------------------------------
    // GUI 4: DURATION SELECTION
    // --------------------------------------------------

    private void openDurationMenu(Player player, Plot plot, OfflinePlayer target, GuestPassPreset preset) {
        String rawTitle = t(player, "guest_pass_duration_title", Map.of("PLAYER", safeName(target)), "&8Duration: {PLAYER}");
        String title = clampTitle(rawTitle, "&8Duration: " + safeName(target));
        Inventory inv = Bukkit.createInventory(new GuestPassDurationHolder(plot, target, preset), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<Integer> durations = plugin.guestPasses().durationPresetsMinutes();
        int slot = 0;
        for (int minutes : durations) {
            if (slot >= 18) break;
            inv.setItem(slot++, GUIManager.createItem(Material.CLOCK,
                    t(player, "guest_pass_duration_label", Map.of("TIME", formatMinutes(player, minutes)), "&e{TIME}"),
                    tl(player, "guest_pass_duration_click_lore", List.of("&7Click to choose how time is counted."))));
        }

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 5: EXPIRY MODE SELECTION
    // --------------------------------------------------

    private void openModeMenu(Player player, Plot plot, OfflinePlayer target, GuestPassPreset preset, long minutes) {
        String rawTitle = t(player, "guest_pass_mode_title", Map.of("PLAYER", safeName(target)), "&8Timing: {PLAYER}");
        String title = clampTitle(rawTitle, "&8Timing: " + safeName(target));
        Inventory inv = Bukkit.createInventory(new GuestPassModeHolder(plot, target, preset, minutes), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(11, GUIManager.createItem(Material.CLOCK,
                t(player, "guest_pass_mode_name_REAL_TIME", "&eReal-time"),
                tl(player, "guest_pass_mode_desc_REAL_TIME", List.of(
                        "&7Duration always counts down,",
                        "&7including while offline or the",
                        "&7server is stopped.",
                        " ",
                        "&8Default / classic Guest Pass timing.",
                        " ",
                        "&eClick to continue"))));

        inv.setItem(15, GUIManager.createItem(Material.COMPASS,
                t(player, "guest_pass_mode_name_ACTIVE_PLAYTIME", "&aActive Playtime"),
                tl(player, "guest_pass_mode_desc_ACTIVE_PLAYTIME", List.of(
                        "&7Duration only counts down while",
                        "&7this player is online.",
                        "&7Pauses while offline or when the",
                        "&7server is stopped.",
                        " ",
                        "&eClick to continue"))));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 6: CONFIRMATION
    // --------------------------------------------------

    private void openConfirmMenu(Player player, Plot plot, OfflinePlayer target, GuestPassPreset preset,
                                  long minutes, GuestPassMode mode) {
        String title = clampTitle(t(player, "guest_pass_confirm_title", "&8Confirm Guest Pass"), "&8Confirm Guest Pass");
        Inventory inv = Bukkit.createInventory(new GuestPassConfirmHolder(plot, target, preset, minutes, mode), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(t(player, "guest_pass_confirm_player_line",
                Map.of("PLAYER", safeName(target)), "&7Player: &f{PLAYER}")));
        lore.add(GUIManager.color(t(player, "guest_pass_confirm_preset_line",
                Map.of("PRESET", presetLabel(player, preset)), "&7Preset: &f{PRESET}")));
        lore.add(GUIManager.color(t(player, "guest_pass_confirm_duration_line",
                Map.of("TIME", formatMinutes(player, (int) minutes)), "&7Duration: &f{TIME}")));
        lore.add(GUIManager.color(t(player, "guest_pass_confirm_mode_line",
                Map.of("MODE", modeLabel(player, mode)), "&7Mode: &f{MODE}")));
        if (preset.requiresContainerWarning()) {
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "guest_pass_confirm_container_warning",
                    "&c⚠ This guest will be able to open chests,")));
            lore.add(GUIManager.color(t(player, "guest_pass_confirm_container_warning_2",
                    "&cbarrels, and other containers on this plot.")));
        }
        lore.add(" ");
        lore.add(GUIManager.color(t(player, "guest_pass_confirm_click_lore", "&aClick to confirm and issue")));

        inv.setItem(13, GUIManager.createItem(preset.requiresContainerWarning() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                t(player, "guest_pass_confirm_name", "&aConfirm Guest Pass"), lore));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // GUI 7: PASS DETAIL / REVOKE
    // --------------------------------------------------

    private void openDetailMenu(Player player, Plot plot, OfflinePlayer target) {
        GuestPass pass = plot.getGuestPass(target.getUniqueId());
        if (pass == null) {
            plugin.effects().playError(player);
            openMenu(player, plot, 0);
            return;
        }

        String rawTitle = t(player, "guest_pass_detail_title", Map.of("PLAYER", safeName(target)), "&8Pass: {PLAYER}");
        String title = clampTitle(rawTitle, "&8Pass: " + safeName(target));
        Inventory inv = Bukkit.createInventory(new GuestPassDetailHolder(plot, target), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        long now = System.currentTimeMillis();
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(t(player, "guest_pass_entry_preset_line",
                Map.of("PRESET", presetLabel(player, pass.getPreset())), "&7Preset: &f{PRESET}")));
        lore.add(GUIManager.color(t(player, "guest_pass_entry_mode_line",
                Map.of("MODE", modeLabel(player, pass.getMode())), "&7Mode: &f{MODE}")));
        lore.add(GUIManager.color(t(player, remainingLineKey(pass),
                Map.of("TIME", formatRemaining(player, pass.getRemainingMillis(now))),
                remainingLineFallback(pass))));
        lore.add(GUIManager.color(t(player, "guest_pass_entry_issuer_line",
                Map.of("PLAYER", pass.getIssuerName()), "&7Issued by: &f{PLAYER}")));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(GUIManager.color(t(player, "guest_pass_entry_name",
                    Map.of("PLAYER", pass.getPlayerName()), "&e{PLAYER}")));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        inv.setItem(13, head);

        inv.setItem(11, GUIManager.createItem(Material.REDSTONE_BLOCK,
                t(player, "button_revoke_guest_pass", "&cRevoke Pass"),
                tl(player, "revoke_guest_pass_lore", List.of(
                        "&7Immediately end this guest's access.",
                        " ",
                        "&cShift-click to confirm revoke."))));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the pass list."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    // --------------------------------------------------
    // FORMATTING HELPERS
    // --------------------------------------------------

    private String formatMinutes(Player player, int minutes) {
        if (minutes <= 0) return t(player, "guest_pass_duration_unknown", "?");
        if (minutes % 1440 == 0) {
            long days = minutes / 1440L;
            return t(player, "guest_pass_duration_days", Map.of("DAYS", String.valueOf(days)), days + "d");
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60L;
            return t(player, "guest_pass_duration_hours", Map.of("HOURS", String.valueOf(hours)), hours + "h");
        }
        return t(player, "guest_pass_duration_minutes", Map.of("MINUTES", String.valueOf(minutes)), minutes + "m");
    }

    private String formatRemaining(Player player, long remainingMillis) {
        if (remainingMillis >= Long.MAX_VALUE - 1000) return t(player, "guest_pass_duration_unknown", "?");
        long minutes = Math.max(0L, remainingMillis / 60_000L);
        if (minutes < 1) return t(player, "guest_pass_expiring_now", "<1m");
        if (minutes >= 1440 && minutes % 1440 == 0) return formatMinutes(player, (int) minutes);
        if (minutes >= 60) {
            long hours = minutes / 60L;
            long remMinutes = minutes % 60L;
            if (remMinutes == 0) return formatMinutes(player, (int) minutes);
            return t(player, "guest_pass_duration_hours_minutes",
                    Map.of("HOURS", String.valueOf(hours), "MINUTES", String.valueOf(remMinutes)),
                    hours + "h " + remMinutes + "m");
        }
        return formatMinutes(player, (int) minutes);
    }

    // --------------------------------------------------
    // CLICK HANDLERS
    // --------------------------------------------------

    public void handleMenuClick(Player player, InventoryClickEvent e, GuestPassMenuHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        int page = holder.getPage();
        int slot = e.getRawSlot();

        if (slot == 49) {
            if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }
            openAddMenu(player, plot, 0);
            return;
        }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); return; }

        List<GuestPass> passes = new ArrayList<>(plot.getActiveGuestPasses());
        passes.sort(Comparator.comparing(GuestPass::getPlayerName, String.CASE_INSENSITIVE_ORDER));
        int maxPage = Math.max(0, (int) Math.ceil(passes.size() / (double) PASSES_PER_PAGE) - 1);

        if (slot == 46 && page > 0) { openMenu(player, plot, page - 1); return; }
        if (slot == 52 && page < maxPage) { openMenu(player, plot, page + 1); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                openDetailMenu(player, plot, meta.getOwningPlayer());
            }
        }
    }

    public void handleAddClick(Player player, InventoryClickEvent e, GuestPassAddHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

        int slot = e.getRawSlot();
        int page = holder.getPage();

        if (slot == 49) { openMenu(player, plot, 0); return; }
        if (slot == 50) { player.closeInventory(); return; }
        if (slot == 45) {
            beginAddByNamePrompt(player, plot);
            return;
        }

        List<Player> candidates = buildAddCandidates(player, plot);
        int maxPage = Math.max(0, (int) Math.ceil(candidates.size() / (double) PLAYERS_PER_PAGE) - 1);
        if (slot == 48 && page > 0) { openAddMenu(player, plot, page - 1); return; }
        if (slot == 51 && page < maxPage) { openAddMenu(player, plot, page + 1); return; }

        if (e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null && meta.getOwningPlayer() != null) {
                openPresetMenu(player, plot, meta.getOwningPlayer());
            }
        }
    }

    public void handlePresetClick(Player player, InventoryClickEvent e, GuestPassPresetHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot, 0); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { openAddMenu(player, plot, 0); return; }
        if (slot == 20) { player.closeInventory(); return; }

        int[] slots = {10, 12, 14, 16};
        List<GuestPassPreset> presets = GuestPassPreset.ordered();
        for (int i = 0; i < slots.length && i < presets.size(); i++) {
            if (slot == slots[i]) {
                openDurationMenu(player, plot, target, presets.get(i));
                return;
            }
        }
    }

    public void handleDurationClick(Player player, InventoryClickEvent e, GuestPassDurationHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        GuestPassPreset preset = holder.getPreset();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot, 0); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { openPresetMenu(player, plot, target); return; }
        if (slot == 20) { player.closeInventory(); return; }

        List<Integer> durations = plugin.guestPasses().durationPresetsMinutes();
        if (slot >= 0 && slot < durations.size() && slot < 18) {
            int minutes = durations.get(slot);
            openModeMenu(player, plot, target, preset, minutes);
        }
    }

    public void handleModeClick(Player player, InventoryClickEvent e, GuestPassModeHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        GuestPassPreset preset = holder.getPreset();
        long minutes = holder.getMinutes();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot, 0); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { openDurationMenu(player, plot, target, preset); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 11) {
            openConfirmMenu(player, plot, target, preset, minutes, GuestPassMode.REAL_TIME);
            return;
        }
        if (slot == 15) {
            openConfirmMenu(player, plot, target, preset, minutes, GuestPassMode.ACTIVE_PLAYTIME);
        }
    }

    public void handleConfirmClick(Player player, InventoryClickEvent e, GuestPassConfirmHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        GuestPassPreset preset = holder.getPreset();
        long minutes = holder.getMinutes();
        GuestPassMode mode = holder.getMode();

        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot, 0); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { openModeMenu(player, plot, target, preset, minutes); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 13) {
            String failureKey = plugin.guestPasses().issue(player, plot, target.getUniqueId(),
                    safeName(target), preset, minutes, mode);
            if (failureKey != null) {
                plugin.msg().send(player, failureKey);
                plugin.effects().playError(player);
                openMenu(player, plot, 0);
                return;
            }
            plugin.msg().send(player, "guest_pass_issued", Map.of(
                    "PLAYER", safeName(target), "PRESET", presetLabel(player, preset)));
            plugin.effects().playConfirm(player);
            if (plugin.getDiscord() != null) {
                plugin.getDiscord().sendEvent("guest_pass", "Guest pass issued",
                        player.getName() + " issued " + preset.fallbackLabel() + " access to "
                                + safeName(target) + " for " + plotDisplayName(plot) + ".",
                        0x4CAF50);
            }

            Player online = target instanceof Player ? (Player) target : Bukkit.getPlayer(target.getUniqueId());
            if (online != null && online.isOnline()
                    && (plugin.getNotificationManager() == null
                    || plugin.getNotificationManager().allowsCategory(online.getUniqueId(), "guest_pass"))) {
                plugin.msg().send(online, "guest_pass_received", Map.of(
                        "PRESET", presetLabel(player, preset), "PLOT", plotDisplayName(plot)));
            }

            openMenu(player, plot, 0);
        }
    }

    public void handleDetailClick(Player player, InventoryClickEvent e, GuestPassDetailHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        OfflinePlayer target = holder.getTarget();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot, 0); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { openMenu(player, plot, 0); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 11) {
            if (!e.getClick().isShiftClick()) {
                plugin.effects().playError(player);
                player.sendMessage(GUIManager.color(t(player, "guest_pass_revoke_hint",
                        "&eTip: &7Shift-click to confirm revoke.")));
                return;
            }
            boolean revoked = plugin.guestPasses().revoke(player, plot, target.getUniqueId());
            if (revoked) {
                plugin.msg().send(player, "guest_pass_revoked", Map.of("PLAYER", safeName(target)));
                plugin.effects().playUnclaim(player);
            } else {
                plugin.effects().playError(player);
            }
            openMenu(player, plot, 0);
        }
    }

    private String plotDisplayName(Plot plot) {
        if (plot == null) return "";
        String name = plot.getPlotName();
        return (name == null || name.isBlank()) ? plot.getWorld() : name;
    }

    private void beginAddByNamePrompt(Player player, Plot plot) {
        pendingNames.put(player.getUniqueId(), new AddByNamePrompt(plot.getPlotId()));
        player.closeInventory();
        player.sendMessage(GUIManager.color(t(player, "guest_pass_add_by_name_prompt",
                "&eType a player name in chat, or &fcancel&e.")));
        plugin.effects().playMenuFlip(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAddByNameChat(AsyncPlayerChatEvent event) {
        AddByNamePrompt prompt = pendingNames.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String raw = event.getMessage() == null ? "" : event.getMessage().trim();
        plugin.runMain(event.getPlayer(), () -> finishAddByNamePrompt(event.getPlayer(), prompt, raw));
    }

    @EventHandler
    public void onAddByNameQuit(PlayerQuitEvent event) {
        pendingNames.remove(event.getPlayer().getUniqueId());
    }

    private void finishAddByNamePrompt(Player player, AddByNamePrompt prompt, String raw) {
        Plot plot = plugin.store().getAllPlots().stream()
                .filter(candidate -> candidate != null && prompt.plotId().equals(candidate.getPlotId()))
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
        if (plot.isOwner(target.getUniqueId()) || Plot.SERVER_OWNER_UUID.equals(target.getUniqueId())
                || plot.isBanned(target.getUniqueId())) {
            player.sendMessage(GUIManager.color("&cThat player cannot receive a pass for this plot."));
            plugin.effects().playError(player);
            openAddMenu(player, plot, 0);
            return;
        }
        openPresetMenu(player, plot, target);
    }
}
