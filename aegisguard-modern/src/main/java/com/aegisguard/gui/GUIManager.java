package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditAdminGUI;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.expansions.ExpansionInstantApprovalsGUI;
import com.aegisguard.expansions.ExpansionRequestAdminGUI;
import com.aegisguard.expansions.ExpansionRequestGUI;
import com.aegisguard.guestpass.GuestPassGUI;
import com.aegisguard.guidance.FirstClaimWalkthroughGUI;
import com.aegisguard.routes.RoutesGUI;
import com.aegisguard.routes.RouteAdminGUI;
import com.aegisguard.alliance.AllianceAccessGUI;
import com.aegisguard.lockdown.LockdownGUI;
import com.aegisguard.profile.RealmProfileGUI;
import com.aegisguard.snapshots.SnapshotAdminGUI;
import com.aegisguard.util.EffectUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GUIManager {

    private final AegisGuard plugin;

    // --- SUB-MENUS ---
    private final PlayerGUI playerGUI;
    private final SettingsGUI settingsGUI;
    private final RolesGUI rolesGUI;
    private final InfoGUI infoGUI;
    private final VisitGUI visitGUI;

    // Admin
    private final AdminGUI adminGUI;
    private final AdminPlotListGUI adminPlotListGUI;
    private final DoctorRepairGUI doctorRepairGUI;
    private final WorldControlsGUI worldControlsGUI;
    private final ExpansionRequestGUI expansionRequestGUI;
    private final ExpansionRequestAdminGUI expansionAdminGUI;
    private final ExpansionInstantApprovalsGUI expansionInstantApprovalsGUI;

    // Plot Management
    private final PlotFlagsGUI plotFlagsGUI;
    private final PlotCosmeticsGUI plotCosmeticsGUI;

    // Economy
    private final PlotMarketGUI plotMarketGUI;
    private final PlotAuctionGUI plotAuctionGUI;
    private final LocalMarketGUI localMarketGUI;
    private final StallBrowseGUI stallBrowseGUI;
    private final StallPurchaseConfirmGUI stallPurchaseConfirmGUI;

    // New v1.1.0+ Features
    private final LevelingGUI levelingGUI;
    private final ZoningGUI zoningGUI;
    private final ZoneBrowseGUI zoneBrowseGUI;
    private final ZoneTenantGUI zoneTenantGUI;
    private final RentConfirmGUI rentConfirmGUI;
    private final MyRentalsGUI myRentalsGUI;
    private final ModerationGUI moderationGUI;
    private final MyTenantsGUI myTenantsGUI;
    private final SettlementsInboxGUI settlementsInboxGUI;
    private final GroupPlotsGUI groupPlotsGUI;
    private final TransferConfirmGUI transferConfirmGUI;
    private final ConvertToServerGUI convertToServerGUI;
    private final ClaimMergeGUI claimMergeGUI;
    private final GiftBlocksGUI giftBlocksGUI;
    private final StorageMigrateGUI storageMigrateGUI;

    // New: Plot Status Codex (replaces sidebar)
    private final PlotStatusGUI plotStatusGUI;

    // ✅ ClaimBlocks Exchange GUI
    private ClaimBlockExchangeGUI claimBlockExchangeGUI;

    // ✅ Snapshot Admin GUI (Rollback System)
    private final SnapshotAdminGUI snapshotAdminGUI;
    private final MigrationAdminGUI migrationAdminGUI;

    // Staff Audit Ledger (1.3.0+)
    private final AuditAdminGUI auditAdminGUI;

    // Temporary Guest Passes (1.3.0+ Milestone 2)
    private final GuestPassGUI guestPassGUI;

    // Emergency Plot Lockdown (1.3.0+ Milestone 3)
    private final LockdownGUI lockdownGUI;

    // Realm Profiles & Noticeboards (1.3.0+ Milestone 4)
    private final RealmProfileGUI realmProfileGUI;

    // Clearer Player Guidance (1.3.0+ Milestone 5)
    private final FirstClaimWalkthroughGUI walkthroughGUI;

    // Routes and Checkpoints (1.3.0+ Milestone 6)
    private final RoutesGUI routesGUI;
    private final RouteAdminGUI routeAdminGUI;

    // Alliance Access (1.3.0+ Milestone 7)
    private final AllianceAccessGUI allianceAccessGUI;

    // Arena / Dungeon runs (1.3.0+, optional)
    private com.aegisguard.arena.ArenaGUI arenaGUI;
    private com.aegisguard.arena.ArenaAdminGUI arenaAdminGUI;

    // Title limits (Spigot inventory titles)
    private static final int TITLE_MAX = 32;

    // Hex pattern (&#RRGGBB)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    // Cached filler (clone on request)
    private static final ItemStack FILLER_ITEM = buildFiller();

    // PDC keys (standardized)
    private final NamespacedKey keyAction;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");

        // Initialize all sub-menus
        this.playerGUI = new PlayerGUI(plugin);
        this.settingsGUI = new SettingsGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.expansionRequestGUI = new ExpansionRequestGUI(plugin);
        this.expansionAdminGUI = new ExpansionRequestAdminGUI(plugin);
        this.expansionInstantApprovalsGUI = new ExpansionInstantApprovalsGUI(plugin);
        this.rolesGUI = new RolesGUI(plugin);
        this.plotFlagsGUI = new PlotFlagsGUI(plugin);
        this.adminPlotListGUI = new AdminPlotListGUI(plugin);
        this.doctorRepairGUI = new DoctorRepairGUI(plugin);
        this.worldControlsGUI = new WorldControlsGUI(plugin);
        this.plotCosmeticsGUI = new PlotCosmeticsGUI(plugin);
        this.plotMarketGUI = new PlotMarketGUI(plugin);
        this.plotAuctionGUI = new PlotAuctionGUI(plugin);
        this.localMarketGUI = new LocalMarketGUI(plugin);
        this.stallBrowseGUI = new StallBrowseGUI(plugin);
        this.stallPurchaseConfirmGUI = new StallPurchaseConfirmGUI(plugin);
        this.infoGUI = new InfoGUI(plugin);
        this.visitGUI = new VisitGUI(plugin);

        // New Features
        this.levelingGUI = new LevelingGUI(plugin);
        this.zoningGUI = new ZoningGUI(plugin);
        this.zoneBrowseGUI = new ZoneBrowseGUI(plugin);
        this.zoneTenantGUI = new ZoneTenantGUI(plugin);
        this.rentConfirmGUI = new RentConfirmGUI(plugin);
        this.myRentalsGUI = new MyRentalsGUI(plugin);
        this.moderationGUI = new ModerationGUI(plugin);
        this.myTenantsGUI = new MyTenantsGUI(plugin);
        this.settlementsInboxGUI = new SettlementsInboxGUI(plugin);
        this.groupPlotsGUI = new GroupPlotsGUI(plugin);
        this.transferConfirmGUI = new TransferConfirmGUI(plugin);
        this.convertToServerGUI = new ConvertToServerGUI(plugin);
        this.claimMergeGUI = new ClaimMergeGUI(plugin);
        this.giftBlocksGUI = new GiftBlocksGUI(plugin);
        this.storageMigrateGUI = new StorageMigrateGUI(plugin);

        // Plot Status Codex GUI
        this.plotStatusGUI = new PlotStatusGUI(plugin);

        // Exchange may initialize slightly later during plugin startup, so this is lazy.
        this.claimBlockExchangeGUI = null;

        // Snapshot Admin GUI (only if SnapshotManager exists)
        if (plugin.getSnapshotManager() != null) {
            this.snapshotAdminGUI = new SnapshotAdminGUI(plugin);
        } else {
            this.snapshotAdminGUI = null;
        }

        this.migrationAdminGUI = new MigrationAdminGUI(plugin);
        this.auditAdminGUI = new AuditAdminGUI(plugin);
        this.guestPassGUI = new GuestPassGUI(plugin);
        this.lockdownGUI = new LockdownGUI(plugin);
        this.realmProfileGUI = new RealmProfileGUI(plugin);
        this.walkthroughGUI = new FirstClaimWalkthroughGUI(plugin);
        this.routesGUI = new RoutesGUI(plugin);
        this.routeAdminGUI = new RouteAdminGUI(plugin);
        this.allianceAccessGUI = new AllianceAccessGUI(plugin);
    }

    // --- OPENERS ---

    public void openMain(Player player) {
        Integer walkthroughPage = walkthroughGUI.consumeLinkedReturn(player);
        if (walkthroughPage != null) {
            playClick(player);
            walkthroughGUI.open(player, walkthroughPage);
            return;
        }
        if (playerGUI != null) {
            playClick(player);
            playerGUI.open(player);
        }
    }

    /**
     * ✅ Open ClaimBlocks Exchange (safe wrapper)
     */
    public void openClaimBlockExchange(Player player) {
        if (player == null) return;

        ClaimBlockExchangeGUI exchangeGUI = exchange();
        if (exchangeGUI == null || plugin.exchange() == null) {
            try {
                player.sendMessage(color(tr(player, "exchange_unavailable",
                        "&cClaimBlocks Exchange is unavailable.")));
            } catch (Throwable ignored) {}
            return;
        }

        boolean enabled = false;
        try { enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false); } catch (Throwable ignored) {}

        if (!enabled) {
            player.sendMessage(color(tr(player, "exchange_disabled",
                    "&cClaimBlocks Exchange is disabled in config.yml.")));
            return;
        }

        exchangeGUI.open(player);
    }

    /**
     * ✅ Open Snapshot Admin GUI (safe wrapper)
     */
    public void openSnapshotAdmin(Player player) {
        if (player == null) return;

        if (snapshotAdminGUI == null || plugin.getSnapshotManager() == null) {
            try {
                player.sendMessage(color(tr(player, "snapshots_unavailable",
                        "&cSnapshot system is unavailable.")));
            } catch (Throwable ignored) {}
            return;
        }

        boolean enabled = false;
        try { enabled = plugin.cfg().raw().getBoolean("snapshots.enabled", true); } catch (Throwable ignored) {}

        if (!enabled) {
            player.sendMessage(color(tr(player, "snapshots_disabled_config",
                    "&cSnapshots are disabled in config.yml.")));
            return;
        }

        snapshotAdminGUI.open(player);
    }

    /**
     * Placeholder method for Diagnostics GUI (Fixes AdminGUI error).
     */
    public void openDiagnostics(Player player) {
        if (player == null) return;
        doctorRepairGUI.open(player);
    }

    // --- GETTERS (Categorized) ---

    // Core
    public PlayerGUI player() { return playerGUI; }
    public SettingsGUI settings() { return settingsGUI; }
    public InfoGUI info() { return infoGUI; }
    public VisitGUI visit() { return visitGUI; }

    // Admin & Staff
    public AdminGUI admin() { return adminGUI; }
    public AdminPlotListGUI plotList() { return adminPlotListGUI; }
    public DoctorRepairGUI doctor() { return doctorRepairGUI; }
    public WorldControlsGUI worldControls() { return worldControlsGUI; }
    public ExpansionRequestGUI expansionRequest() { return expansionRequestGUI; }
    public ExpansionRequestAdminGUI expansionAdmin() { return expansionAdminGUI; }
    public ExpansionInstantApprovalsGUI expansionInstantApprovals() { return expansionInstantApprovalsGUI; }

    // Plot Management
    public RolesGUI roles() { return rolesGUI; }
    public PlotFlagsGUI flags() { return plotFlagsGUI; }
    public PlotCosmeticsGUI cosmetics() { return plotCosmeticsGUI; }
    public LevelingGUI leveling() { return levelingGUI; }
    public ZoningGUI zoning() { return zoningGUI; }
    public ZoneBrowseGUI zoneBrowse() { return zoneBrowseGUI; }
    public ZoneTenantGUI zoneTenant() { return zoneTenantGUI; }
    public RentConfirmGUI rentConfirm() { return rentConfirmGUI; }
    public MyRentalsGUI myRentals() { return myRentalsGUI; }
    public ModerationGUI moderation() { return moderationGUI; }
    public MyTenantsGUI myTenants() { return myTenantsGUI; }
    public SettlementsInboxGUI settlementsInbox() { return settlementsInboxGUI; }
    public GroupPlotsGUI groupPlots() { return groupPlotsGUI; }
    public TransferConfirmGUI transferConfirm() { return transferConfirmGUI; }
    public ConvertToServerGUI convertToServer() { return convertToServerGUI; }
    public ClaimMergeGUI claimMerge() { return claimMergeGUI; }
    public GiftBlocksGUI giftBlocks() { return giftBlocksGUI; }
    public StorageMigrateGUI storageMigrate() { return storageMigrateGUI; }

    // Plot Status Codex
    public PlotStatusGUI plotStatus() { return plotStatusGUI; }

    // Economy
    public PlotMarketGUI market() { return plotMarketGUI; }
    public PlotAuctionGUI auction() { return plotAuctionGUI; }
    public LocalMarketGUI localMarket() { return localMarketGUI; }
    public StallBrowseGUI stallBrowse() { return stallBrowseGUI; }
    public StallPurchaseConfirmGUI stallBuyConfirm() { return stallPurchaseConfirmGUI; }

    // ✅ ClaimBlocks Exchange
    public ClaimBlockExchangeGUI exchange() {
        if (claimBlockExchangeGUI == null && plugin.exchange() != null) {
            claimBlockExchangeGUI = new ClaimBlockExchangeGUI(plugin, plugin.exchange());
        }
        return claimBlockExchangeGUI;
    }

    // ✅ Snapshot Admin
    public SnapshotAdminGUI snapshotAdmin() { return snapshotAdminGUI; }
    public MigrationAdminGUI migration() { return migrationAdminGUI; }

    // Staff Audit Ledger (1.3.0+)
    public AuditAdminGUI audit() { return auditAdminGUI; }

    // Temporary Guest Passes (1.3.0+ Milestone 2)
    public GuestPassGUI guestPasses() { return guestPassGUI; }

    // Emergency Plot Lockdown (1.3.0+ Milestone 3)
    public LockdownGUI lockdownGui() { return lockdownGUI; }
    public RealmProfileGUI realmProfile() { return realmProfileGUI; }

    public FirstClaimWalkthroughGUI walkthrough() { return walkthroughGUI; }

    public RoutesGUI routes() { return routesGUI; }

    public RouteAdminGUI routeAdmin() { return routeAdminGUI; }

    public AllianceAccessGUI allianceAccess() { return allianceAccessGUI; }

    public com.aegisguard.arena.ArenaGUI arena() {
        if (arenaGUI == null && plugin.arena() != null) {
            arenaGUI = new com.aegisguard.arena.ArenaGUI(plugin, plugin.arena());
        }
        return arenaGUI;
    }

    public com.aegisguard.arena.ArenaAdminGUI arenaAdmin() {
        if (arenaAdminGUI == null && plugin.arena() != null) {
            arenaAdminGUI = new com.aegisguard.arena.ArenaAdminGUI(plugin, plugin.arena());
        }
        return arenaAdminGUI;
    }

    // ======================================
    // --- LANGUAGE GATEWAY (Codex Engine) ---
    // ======================================

    public String tr(Player player, String key, String fallback) {
        String value = null;
        try {
            if (plugin.codex() != null) value = plugin.codex().tr(player, key);
        } catch (Throwable ignored) {}
        return safeText(key, value, fallback);
    }

    public String tr(Player player, String key, String fallback, Map<String, String> placeholders) {
        String value = null;
        try {
            if (plugin.codex() != null) value = plugin.codex().tr(player, key, placeholders);
        } catch (Throwable ignored) {}

        String fb = applyPlaceholders(fallback, placeholders);
        return safeText(key, value, fb);
    }

    /**
     * Title (clamped safely to inventory title limits)
     */
    public String title(Player player, String key, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(player, key);
        } catch (Throwable ignored) {}

        String t = safeText(key, raw, fallback);
        return clampInventoryTitle(t, TITLE_MAX);
    }

    /**
     * Title with placeholders (clamped safely)
     */
    public String title(Player player, String key, String fallback, Map<String, String> placeholders) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(player, key, placeholders);
        } catch (Throwable ignored) {}

        String fb = applyPlaceholders(fallback, placeholders);
        String t = safeText(key, raw, fb);
        return clampInventoryTitle(t, TITLE_MAX);
    }

    /**
     * Convenience: build a page suffix and clamp with base title.
     * Keeps your GUIs consistent and avoids broken color tails.
     */
    public String titleWithPageSuffix(Player player, String key, String fallback, int page, int pages) {
        String base = title(player, key, fallback);
        String suffix = " §8(" + page + "/" + pages + ")";
        return clampInventoryTitleWithSuffix(base, suffix, TITLE_MAX);
    }

    public List<String> trList(Player player, String key, List<String> fallback) {
        List<String> out = null;

        try {
            if (plugin.codex() != null) {
                List<String> value = plugin.codex().trList(player, key);
                if (value != null && !value.isEmpty()) out = value;
            }
        } catch (Throwable ignored) {}

        if (out == null) out = (fallback == null ? Collections.emptyList() : fallback);

        // 1.2.6 QoL: always colorize consistently here
        return colorizeList(out);
    }

    public List<String> trList(Player player, String key, List<String> fallback, Map<String, String> placeholders) {
        List<String> out = null;

        try {
            if (plugin.codex() != null) {
                List<String> value = plugin.codex().trList(player, key, placeholders);
                if (value != null && !value.isEmpty()) out = value;
            }
        } catch (Throwable ignored) {}

        if (out == null) {
            if (fallback == null || fallback.isEmpty()) return Collections.emptyList();
            List<String> applied = new ArrayList<>(fallback.size());
            for (String line : fallback) applied.add(applyPlaceholders(line, placeholders));
            out = applied;
        }

        return colorizeList(out);
    }

    // ======================================
    // --- UTILITIES (Static Helpers) ---
    // ======================================

    public static String safeText(String fromCodex, String fallback) {
        return safeText(null, fromCodex, fallback);
    }

    public static String safeText(String requestedKey, String fromCodex, String fallback) {
        if (fallback == null) fallback = "";

        String out;

        if (fromCodex == null) {
            out = fallback;
        } else {
            String s = fromCodex.trim();
            if (s.isEmpty()) out = fallback;
            else if (s.contains("[Missing") || s.equalsIgnoreCase("null")) out = fallback;
            else if (requestedKey != null && !requestedKey.trim().isEmpty()
                    && s.equalsIgnoreCase(requestedKey.trim())) out = fallback;
            else out = fromCodex;
        }

        return color(out);
    }

    private static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) return input;

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isEmpty()) continue;

            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + k + "}", v)
                    .replace("{" + k.toLowerCase(Locale.ROOT) + "}", v)
                    .replace("{" + k.toUpperCase(Locale.ROOT) + "}", v);
        }
        return out;
    }

    public static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) meta.setLore(colorizeList(lore));
            // Do not blanket-apply every ItemFlag here. On newer server versions,
            // that can hide the entire tooltip/lore from GUI items.
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItem(String name, Material mat, List<String> lore) {
        return createItem(mat, name, lore);
    }

    public static ItemStack getFiller() {
        return FILLER_ITEM.clone();
    }

    public static boolean isFiller(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.GRAY_STAINED_GLASS_PANE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String dn = ChatColor.stripColor(meta.getDisplayName());
        return dn != null && dn.trim().isEmpty();
    }

    public static void playClick(Player p) {
        EffectUtil.playToggle(p);
    }

    public static void playSuccess(Player p) {
        EffectUtil.playSuccess(p);
    }

    public static String color(String text) {
        if (text == null) return "";
        String msg = text;

        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String token = matcher.group(0);
            String hex = matcher.group(1);
            try {
                msg = msg.replace(token, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
            } catch (Throwable ignored) {
                msg = msg.replace(token, "");
            }
            matcher = HEX_PATTERN.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private static List<String> colorizeList(List<String> lore) {
        if (lore == null || lore.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(lore.size());
        for (String line : lore) out.add(color(line == null ? "" : line));
        return out;
    }

    private static ItemStack buildFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Safer title clamp:
     * - Ensures <= max chars
     * - Avoids trailing '§'
     * - Avoids dangling incomplete hex prefix '§x.....' fragments at the end
     */
    public static String clampInventoryTitle(String title, int max) {
        if (title == null) return "";
        String t = title;

        if (t.length() > max) t = t.substring(0, max);

        // If we cut mid color-code pair, remove the dangling marker.
        while (t.endsWith("§")) t = t.substring(0, t.length() - 1);

        // If we cut into a hex color sequence (§x§R§R§G§G§B§B), strip the partial tail.
        int idx = t.lastIndexOf("§x");
        if (idx >= 0) {
            int remaining = t.length() - idx;
            // Full hex sequence length is 14 chars: §x§R§R§G§G§B§B
            if (remaining < 14) {
                t = t.substring(0, idx);
                while (t.endsWith("§")) t = t.substring(0, t.length() - 1);
            }
        }

        return t;
    }

    public static String clampInventoryTitleWithSuffix(String base, String suffix, int max) {
        if (base == null) base = "";
        if (suffix == null) suffix = "";

        String combined = base + suffix;
        if (combined.length() <= max) return clampInventoryTitle(combined, max);

        // If suffix alone is too long, clamp it hard.
        if (suffix.length() >= max) return clampInventoryTitle(suffix.substring(0, max), max);

        int remainingForBase = max - suffix.length();
        String trimmedBase = base.length() > remainingForBase ? base.substring(0, remainingForBase) : base;

        // Avoid cutting off a color code marker on the base chunk.
        while (trimmedBase.endsWith("§")) trimmedBase = trimmedBase.substring(0, Math.max(0, trimmedBase.length() - 1));

        return clampInventoryTitle(trimmedBase + suffix, max);
    }

    // ======================================
    // --- 1.2.6 QoL: PDC Action Helpers ---
    // ======================================

    public void tagAction(ItemStack item, String action) {
        if (item == null || action == null || action.isBlank()) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action.trim().toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    public String getAction(ItemStack item) {
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

    // ======================================
    // --- LEDGER ITEM ---
    // ======================================

    public ItemStack createLedgerItem(Player p) {
        String title = tr(p, "ledger_title", "&6📜 Domain Registry");
        List<String> lore = new ArrayList<>();

        ClaimBlockManager cb = plugin.getClaimBlockManager();
        if (cb == null) {
            lore.add(tr(p, "ledger_disabled", "&7Claim Blocks are disabled on this server."));
            lore.add(" ");
            lore.add(tr(p, "ledger_click_disabled", "&8No ledger entries available."));
            return createItem(Material.PAPER, title, lore);
        }

        long total = cb.getTotalBlocks(p.getUniqueId());
        long used = cb.getUsedBlocks(p.getUniqueId());
        long available = cb.getAvailableBlocks(p.getUniqueId());

        lore.add(tr(p, "ledger_available", "&7Available: &a" + available));
        lore.add(tr(p, "ledger_used", "&7Used: &c" + used));
        lore.add(tr(p, "ledger_total", "&7Total Capacity: &e" + total));
        lore.add(" ");
        lore.add(tr(p, "ledger_click", "&eClick to view detailed stats."));

        return createItem(Material.PAPER, title, lore);
    }
}
