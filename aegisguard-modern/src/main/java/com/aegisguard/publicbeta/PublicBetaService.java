package com.aegisguard.publicbeta;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.gui.LanguageSelectGUI;
import com.aegisguard.gui.SettingsGUI;
import com.aegisguard.protection.ProtectionPreset;
import com.aegisguard.publicbeta.PublicBetaWorldService.Role;
import com.aegisguard.travel.SafeTravelResult;
import com.aegisguard.travel.SafeTravelService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Isolated onboarding and world-routing layer for the AegisGuard Public Beta.
 *
 * <p>The Public Beta Player marker is deliberately profile-scoped. It is not a
 * Bukkit permission attachment, plot role, OP flag, or staff role, so it cannot
 * inherit console, recovery, global configuration, or foreign-plot authority.</p>
 */
public final class PublicBetaService implements Listener {

    public static final String ROLE_ID = "PUBLIC_BETA_PLAYER";
    public static final String HUB_PROTECTION_PERMISSION = "aegis.admin.publicbeta.hub-protection";
    private static final String ROOT = "public-beta-mode";
    // Plot stores index claims by chunk. A world-sized plot would require
    // trillions of chunk-index entries, so ENTIRE_WORLD uses event guards and
    // keeps only a bounded server zone around spawn in the spatial index.
    private static final int MAX_INDEXED_HUB_RADIUS = 512;

    private final AegisGuard plugin;
    private final NamespacedKey guideKey;
    private final File profileFile;
    private final YamlConfiguration profiles;
    private final Map<UUID, PendingLanguage> pendingLanguages = new ConcurrentHashMap<>();
    private final Set<UUID> languageReopenScheduled = ConcurrentHashMap.newKeySet();
    private final boolean isolationValid;

    public PublicBetaService(AegisGuard plugin) {
        this.plugin = plugin;
        this.guideKey = new NamespacedKey(plugin, "public_beta_guide");
        this.profileFile = new File(plugin.getDataFolder(), "public-beta/player-profiles.yml");
        this.profiles = loadProfiles();
        this.isolationValid = verifyStoredInstanceIdentity();
    }

    public boolean isEnabled() {
        return isolationValid && plugin.getConfig().getBoolean(ROOT + ".enabled", false);
    }

    public boolean hasBetaPlayerRole(UUID playerId) {
        return playerId != null && ROLE_ID.equals(profiles.getString(path(playerId) + ".role"));
    }

    public PublicBetaVoiceMode voiceMode(UUID playerId) {
        PublicBetaVoiceMode fallback = defaultVoiceMode();
        if (playerId == null || !hasBetaPlayerRole(playerId)) return PublicBetaVoiceMode.PROXIMITY;
        return PublicBetaVoiceMode.parse(profiles.getString(path(playerId) + ".voice-mode"), fallback);
    }

    private PublicBetaVoiceMode defaultVoiceMode() {
        return PublicBetaVoiceMode.parse(
                plugin.getConfig().getString(ROOT + ".voice-chat.default-mode", "PROXIMITY"),
                PublicBetaVoiceMode.PROXIMITY);
    }

    public void setVoiceMode(Player player, PublicBetaVoiceMode mode) {
        if (player == null || !hasBetaPlayerRole(player.getUniqueId())) return;
        PublicBetaVoiceMode safe = mode == null ? PublicBetaVoiceMode.PROXIMITY : mode;
        profiles.set(path(player.getUniqueId()) + ".voice-mode", safe.name());
        saveProfiles();
        plugin.refreshHearthVoice(player);
    }

    public boolean isPublicBetaWorld(World world) {
        return plugin.publicBetaWorlds() != null && plugin.publicBetaWorlds().isPublicBetaWorld(world);
    }

    public void validateConfiguration() {
        if (!isEnabled()) return;

        String instanceId = plugin.getConfig().getString(ROOT + ".isolation.instance-id", "").trim();
        if (instanceId.isEmpty() || instanceId.equalsIgnoreCase("private-development")) {
            plugin.getLogger().severe("[Public Beta] isolation.instance-id must uniquely identify the public beta server.");
        }

        String hub = worldName("welcome-hub");
        String play = worldName("play-world");
        String lab = worldName("test-lab");
        if (hub.equalsIgnoreCase(play) || hub.equalsIgnoreCase(lab) || play.equalsIgnoreCase(lab)) {
            plugin.getLogger().severe("[Public Beta] Welcome Hub, Play World, and Test Lab must be three distinct worlds.");
        }

        World hubWorld = Bukkit.getWorld(hub);
        if (hubWorld == null) {
            plugin.getLogger().warning("[Public Beta] Welcome Hub world is not loaded: " + hub);
            return;
        }
        ensureHubProtection(hubWorld);
        ensureArrivalProtection(Role.PLAY_WORLD);
        ensureArrivalProtection(Role.TEST_LAB);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        String base = path(player.getUniqueId());
        if (profiles.contains(base)) {
            if (!isOnboardingComplete(player.getUniqueId())) {
                plugin.runMain(player, () -> beginFirstJoin(player));
            } else {
                // Upgrade an already-issued tagged paper guide in place and
                // restore a missing guide without duplicating it.
                plugin.runMain(player, () -> giveGuide(player));
            }
            return;
        }

        long now = System.currentTimeMillis();
        profiles.set(base + ".name", player.getName());
        profiles.set(base + ".role", ROLE_ID);
        profiles.set(base + ".first-joined-at", now);
        profiles.set(base + ".onboarding-complete", false);
        profiles.set(base + ".capabilities", List.of("PUBLIC_PLAYER", "TEST_LAB_FLOW"));
        profiles.set(base + ".voice-mode", defaultVoiceMode().name());
        saveProfiles();

        // Delay until the player entity is fully placed, then enforce the
        // protected-hub -> language -> guide ordering.
        plugin.runMain(player, () -> beginFirstJoin(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingLanguages.remove(id);
        languageReopenScheduled.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuideUse(PlayerInteractEvent event) {
        if (!isEnabled() || event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (!isGuide(event.getItem())) return;

        Player player = event.getPlayer();
        String readPath = path(player.getUniqueId()) + ".guide-book-read";
        boolean firstRead = !profiles.getBoolean(readPath, false);
        if (event.getItem().getType() != Material.WRITTEN_BOOK) {
            event.setCancelled(true);
            ItemStack book = createGuideBook(player);
            player.getInventory().setItemInMainHand(book);
            profiles.set(readPath, true);
            saveProfiles();
            plugin.runMain(player, () -> player.openBook(book));
            return;
        }
        if (firstRead || player.isSneaking()) {
            // Let Minecraft open the readable written book normally. Sneaking
            // is the permanent reread gesture after the menu behavior unlocks.
            profiles.set(readPath, true);
            saveProfiles();
            return;
        }
        event.setCancelled(true);
        openChoiceMenu(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, PublicBetaHolder holder) {
        if (!isEnabled() || player == null || holder == null) return;
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        String action = plugin.gui().getAction(clicked);
        if (action == null) return;

        if (holder.screen == Screen.LANGUAGE_CONFIRM) {
            handleLanguageConfirmation(player, holder.detectedStyle, action);
        } else if (holder.screen == Screen.WELCOME) {
            handleWelcomeChoice(player, action);
        } else if (holder.screen == Screen.VOICE) {
            handleVoiceChoice(player, holder.origin, holder.settingsReturn, action);
        } else if (holder.screen == Screen.WORLD_OVERVIEW || holder.screen == Screen.WORLD_CONFIRM) {
            handleWorldWizard(player, holder.screen, action);
        } else {
            handleChoice(player, action);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isEnabled() || !(event.getPlayer() instanceof Player player)) return;
        if (!shouldRestoreLanguageOnboarding(event)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        boolean incompleteConfirm = holder instanceof PublicBetaHolder beta
                && beta.screen == Screen.LANGUAGE_CONFIRM
                && !isOnboardingComplete(player.getUniqueId());
        boolean incompletePicker = holder instanceof LanguageSelectGUI.LanguageSelectHolder language
                && language.getReturnTo() == LanguageSelectGUI.ReturnTo.PUBLIC_BETA
                && !isOnboardingComplete(player.getUniqueId());
        if (!incompleteConfirm && !incompletePicker) return;

        UUID id = player.getUniqueId();
        if (!languageReopenScheduled.add(id)) return;
        plugin.runMain(player, () -> {
            try {
                if (!player.isOnline() || isOnboardingComplete(id)) return;
                InventoryHolder open = player.getOpenInventory().getTopInventory().getHolder();
                if (isLanguageOnboardingHolder(open)) return;
                PendingLanguage pending = pendingLanguages.get(id);
                if (pending != null && pending.supported()) {
                    openLanguageConfirmation(player, pending.style());
                } else {
                    openLanguagePicker(player);
                }
            } finally {
                languageReopenScheduled.remove(id);
            }
        });
    }

    private boolean shouldRestoreLanguageOnboarding(InventoryCloseEvent event) {
        try {
            InventoryCloseEvent.Reason reason = event.getReason();
            // OPEN_NEW/PLUGIN fire when we replace this menu. TELEPORT fires from
            // first-join hub travel and would reopen the picker in a loop.
            return reason == InventoryCloseEvent.Reason.PLAYER
                    || reason == InventoryCloseEvent.Reason.UNKNOWN;
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            return true;
        }
    }

    private boolean isLanguageOnboardingHolder(InventoryHolder holder) {
        if (holder instanceof PublicBetaHolder beta) {
            return beta.screen == Screen.LANGUAGE_CONFIRM;
        }
        return holder instanceof LanguageSelectGUI.LanguageSelectHolder language
                && language.getReturnTo() == LanguageSelectGUI.ReturnTo.PUBLIC_BETA;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleportToTestLab(PlayerTeleportEvent event) {
        if (!isEnabled() || event.getTo() == null || event.getTo().getWorld() == null) return;
        if (!event.getTo().getWorld().getName().equalsIgnoreCase(worldName("test-lab"))) return;
        if (hasBetaPlayerRole(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_test_lab_denied", "&cTest Lab access requires the Public Beta Player role.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBlockBreak(BlockBreakEvent event) {
        if (!isEntireHubProtected(event.getBlock().getWorld()) || canModifyHub(event.getPlayer())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_interaction_denied",
                "&cThis is the Welcome Hub. Use /ag hub, then choose the Play World or Test Lab for building and claims.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBlockPlace(BlockPlaceEvent event) {
        if (!isEntireHubProtected(event.getBlock().getWorld()) || canModifyHub(event.getPlayer())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_interaction_denied",
                "&cThis is the Welcome Hub. Use /ag hub, then choose the Play World or Test Lab for building and claims.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubInteraction(PlayerInteractEvent event) {
        if (!isEntireHubProtected(event.getPlayer().getWorld()) || canModifyHub(event.getPlayer())) return;
        if (isGuide(event.getItem())) return;
        if (event.getClickedBlock() == null) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_interaction_denied",
                "&cThis is the Welcome Hub. Use /ag beta and choose the Play World or Test Lab for interactions and claims.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubEntityInteraction(PlayerInteractEntityEvent event) {
        if (!isEntireHubProtected(event.getPlayer().getWorld()) || canModifyHub(event.getPlayer())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_interaction_denied",
                "&cThis is the Welcome Hub. Use /ag beta and choose the Play World or Test Lab for interactions and claims.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!isEntireHubProtected(event.getPlayer().getWorld()) || canModifyHub(event.getPlayer())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_interaction_denied",
                "&cThis is the Welcome Hub. Use /ag beta and choose the Play World or Test Lab for interactions and claims.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBucketFill(PlayerBucketFillEvent event) {
        if (!isEntireHubProtected(event.getPlayer().getWorld()) || canModifyHub(event.getPlayer())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_interaction_denied",
                "&cThis is the Welcome Hub. Use /ag beta and choose the Play World or Test Lab for interactions and claims.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubEntityDamage(EntityDamageEvent event) {
        if (!isEntireHubProtected(event.getEntity().getWorld())) return;
        if (event.getEntity() instanceof Player player && canModifyHub(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubEntityExplode(EntityExplodeEvent event) {
        if (isEntireHubProtected(event.getLocation().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBlockExplode(BlockExplodeEvent event) {
        if (isEntireHubProtected(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBlockBurn(BlockBurnEvent event) {
        if (isEntireHubProtected(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubBlockIgnite(BlockIgniteEvent event) {
        if (isEntireHubProtected(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHubClaim(PlotClaimEvent event) {
        if (!isHubWorld(event.getPlot().getBukkitWorld())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "public_beta_hub_claim_denied",
                "&cClaims cannot be created in the Welcome Hub. Choose the Play World or Test Lab with /ag beta.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArrivalZoneClaim(PlotClaimEvent event) {
        Plot candidate = event.getPlot();
        if (candidate == null || candidate.getBukkitWorld() == null) return;
        Role role = arrivalRole(candidate.getBukkitWorld());
        if (role == null) return;
        Plot protectedZone = plugin.store().getPlotAt(candidate.getBukkitWorld().getSpawnLocation());
        if (protectedZone == null || !protectedZone.isServerZone()) return;
        if (!overlaps(candidate, protectedZone)) return;
        event.setCancelled(true);
        int distance = configuredArrivalRadius(role) + 1;
        sendReplacing(event.getPlayer(), "public_beta_spawn_claim_denied",
                "&cThis is a protected arrival area. Travel at least {DISTANCE} blocks from spawn before creating a claim.",
                Map.of("DISTANCE", String.valueOf(distance)));
    }

    public void finishLanguageSelection(Player player) {
        if (!isEnabled() || player == null) return;
        String style = plugin.codex() == null
                ? plugin.getConfig().getString("localization.default_language", "modern_english")
                : plugin.codex().getPlayerStyle(player);
        if (plugin.codex() != null) plugin.codex().setPlayerStyle(player, style);

        String base = path(player.getUniqueId());
        profiles.set(base + ".name", player.getName());
        profiles.set(base + ".language", style);
        profiles.set(base + ".onboarding-complete", true);
        profiles.set(base + ".onboarding-completed-at", System.currentTimeMillis());
        profiles.set(base + ".guide-book-read", false);
        saveProfiles();
        pendingLanguages.remove(player.getUniqueId());

        if (plugin.publicBetaChat() != null) plugin.publicBetaChat().refresh(player);

        giveGuide(player);
        send(player, "public_beta_guide_received",
                "&aYour Public Beta Guide is ready. Hold it and right-click to read it.");
        openWelcomeMenu(player);
    }

    public void save() {
        saveProfiles();
    }

    /** Ensures the controlled guidebook exists in the player's active isolated inventory. */
    public void ensureGuide(Player player) {
        if (isEnabled() && player != null && hasBetaPlayerRole(player.getUniqueId())) giveGuide(player);
    }

    private void beginFirstJoin(Player player) {
        if (!player.isOnline()) return;

        String detected = detectStyle(player.getLocale());
        boolean supported = detected != null && plugin.codex() != null
                && plugin.codex().getAvailableStyles().stream().anyMatch(detected::equalsIgnoreCase);
        pendingLanguages.put(player.getUniqueId(), new PendingLanguage(detected, supported));

        Runnable showLanguage = () -> showFirstJoinLanguage(player, detected, supported);
        World hub = Bukkit.getWorld(worldName("welcome-hub"));
        if (hub == null || plugin.safeTravel() == null) {
            showLanguage.run();
            return;
        }
        SafeTravelResult result = plugin.safeTravel().travel(
                player, hub.getSpawnLocation(), SafeTravelService.Kind.OTHER, false);
        if (!result.isSuccess() || result.teleportFuture() == null) {
            showLanguage.run();
            return;
        }
        result.teleportFuture().whenComplete((ok, err) -> plugin.runEntity(player, showLanguage));
    }

    private void showFirstJoinLanguage(Player player, String detected, boolean supported) {
        if (!player.isOnline() || isOnboardingComplete(player.getUniqueId())) return;
        if (supported) {
            openLanguageConfirmation(player, detected);
            return;
        }
        send(player, "public_beta_language_unsupported",
                "&eYour Minecraft client language is not currently supported by AegisGuard. "
                        + "Please choose another available language you understand. "
                        + "You can change it later in Settings. "
                        + "Additional languages may be added in future updates.");
        openLanguagePicker(player);
    }

    private void openLanguageConfirmation(Player player, String detectedStyle) {
        if (player == null || plugin.codex() == null) {
            openLanguagePicker(player);
            return;
        }
        String title = title(plugin.codex().trStyle(detectedStyle,
                "public_beta_language_confirm_title"));
        Inventory inventory = Bukkit.createInventory(
                new PublicBetaHolder(Screen.LANGUAGE_CONFIRM, detectedStyle, Origin.GUIDE, null), 27, title);
        fill(inventory);

        ItemStack continueItem = GUIManager.createItem(Material.LIME_CONCRETE,
                plugin.codex().trStyle(detectedStyle, "public_beta_language_continue_name"),
                plugin.codex().trListStyle(detectedStyle, "public_beta_language_continue_lore"));
        plugin.gui().tagAction(continueItem, "continue_detected");
        inventory.setItem(11, continueItem);

        ItemStack chooseItem = GUIManager.createItem(Material.BOOK,
                plugin.codex().trStyle(detectedStyle, "public_beta_language_choose_name"),
                plugin.codex().trListStyle(detectedStyle, "public_beta_language_choose_lore"));
        plugin.gui().tagAction(chooseItem, "choose_language");
        inventory.setItem(15, chooseItem);
        player.openInventory(inventory);
    }

    private void handleLanguageConfirmation(Player player, String detectedStyle, String action) {
        if ("continue_detected".equals(action)) {
            if (plugin.codex() == null || !plugin.codex().setPlayerStyle(player, detectedStyle)) {
                openLanguagePicker(player);
                return;
            }
            finishLanguageSelection(player);
            return;
        }
        if ("choose_language".equals(action)) openLanguagePicker(player);
    }

    private void openLanguagePicker(Player player) {
        if (player == null) return;
        // Prevent the confirmation close event from restoring the confirmation
        // over the picker the player explicitly requested.
        pendingLanguages.put(player.getUniqueId(), new PendingLanguage(null, false));
        plugin.gui().languageSelect().open(player, null, LanguageSelectGUI.ReturnTo.PUBLIC_BETA);
    }

    private void openChoiceMenu(Player player) {
        if (player == null || !player.isOnline()) return;
        Inventory inventory = Bukkit.createInventory(
                new PublicBetaHolder(Screen.CHOICE, null, Origin.GUIDE, null), 27,
                title(tr(player, "public_beta_choice_title", "&bAegisGuard Public Beta")));
        fill(inventory);

        inventory.setItem(10, destinationItem(player, Role.PLAY_WORLD, Material.GRASS_BLOCK, "enter_play",
                "public_beta_play_name", "&aEnter Play World", "public_beta_play_lore", List.of(
                        "&7Play normally with friends, claim land,",
                        "&7build, and enjoy the public community.")));
        inventory.setItem(12, destinationItem(player, Role.TEST_LAB, Material.RESPAWN_ANCHOR, "enter_test_lab",
                "public_beta_lab_name", "&dEnter Test Lab", "public_beta_lab_lore", List.of(
                        "&7Create your own private testing plot.",
                        "&7Access is limited to land you own.")));
        inventory.setItem(14, actionItem(player, Material.BOOK, "choose_language",
                "public_beta_choose_language_name", "&bChoose Language", "public_beta_choose_language_lore", List.of(
                        "&7Change your AegisGuard language.")));
        inventory.setItem(16, actionItem(player, Material.CLOCK, "decide_later",
                "public_beta_decide_later_name", "&eDecide Later", "public_beta_decide_later_lore", List.of(
                        "&7Stay in the Welcome Hub.",
                        "&7Right-click the guide when ready.")));
        inventory.setItem(22, actionItem(player, Material.SCULK_SENSOR, "open_voice",
                "public_beta_voice_button_name", "&bVoice Chat Scope", "public_beta_voice_button_lore", List.of(
                        "&7Proximity uses your talk key and microphone.",
                        "&7No group is required unless you want a wider channel",
                        "&7or a private party.")));
        inventory.setItem(19, actionItem(player, Material.REDSTONE, "report_problem",
                "public_beta_report_problem_name", "&cReport a Problem", "public_beta_report_problem_lore", List.of(
                        "&7Tell staff about a bug or problem.", "&7Your world and location are included.")));
        inventory.setItem(21, actionItem(player, Material.WRITABLE_BOOK, "send_feedback",
                "public_beta_feedback_name", "&bGeneral Feedback", "public_beta_feedback_lore", List.of(
                        "&7Share what worked or could be better.")));
        inventory.setItem(23, actionItem(player, Material.LIGHT_BLUE_DYE, "suggest_feature",
                "public_beta_suggestion_name", "&dFeature Suggestion", "public_beta_suggestion_lore", List.of(
                        "&7Suggest an idea for AegisGuard.")));
        inventory.setItem(25, actionItem(player, Material.ENDER_CHEST, "my_submissions",
                "public_beta_my_submissions_name", "&bMy Submissions", "public_beta_my_submissions_lore", List.of(
                        "&7View your reports, feedback, suggestions,", "&7and their current staff status.")));
        inventory.setItem(20, actionItem(player, Material.PAPER, "open_post",
                "post_menu_button_name", "&6Aegis Post &e(Beta)", "post_menu_button_lore", List.of(
                        "&7Write and deliver sealed letters to", "&7online or offline Public Beta players.", " ",
                        "&eExperimental Public Beta feature.", "&7Planned for a future release once stable.")));
        inventory.setItem(18, actionItem(player, Material.ARROW, "beta_back",
                "public_beta_back_name", "&aBack", "public_beta_back_lore", List.of("&7Return to the main AegisGuard menu.")));
        inventory.setItem(26, actionItem(player, Material.BARRIER, "beta_exit",
                "public_beta_exit_name", "&cExit", "public_beta_exit_lore", List.of("&7Close this menu.")));
        inventory.setItem(4, destinationItem(player, Role.WELCOME_HUB, Material.LODESTONE, "return_hub",
                "public_beta_hub_name", "&bReturn to Welcome Hub", "public_beta_hub_lore", List.of(
                        "&7Meet other players and choose where to go.",
                        "&7Building and claims are disabled in the Hub.")));
        player.openInventory(inventory);
    }

    public void openBetaMenu(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!isEnabled() || !hasBetaPlayerRole(player.getUniqueId())) {
            send(player, "public_beta_command_unavailable",
                    "&cThe Public Beta menu is not available for this profile.");
            return;
        }
        openChoiceMenu(player);
    }

    private void openWelcomeMenu(Player player) {
        if (player == null || !player.isOnline()) return;
        Inventory inventory = Bukkit.createInventory(
                new PublicBetaHolder(Screen.WELCOME, null, Origin.GUIDE, null), 27,
                title(tr(player, "public_beta_welcome_title", "&bWelcome to AegisGuard 1.4 Beta")));
        fill(inventory);

        inventory.setItem(4, GUIManager.createItem(Material.NETHER_STAR,
                tr(player, "public_beta_welcome_header_name", "&bWelcome, Beta Player!"),
                trList(player, "public_beta_welcome_header_lore", List.of(
                        "&7Your guidebook explains what you can do,",
                        "&7what is restricted, and how to get started.",
                        " ", "&eChoose an option below."))));
        inventory.setItem(10, actionItem(player, Material.WRITTEN_BOOK, "welcome_read_guide",
                "public_beta_welcome_read_name", "&aRead Your Guidebook",
                "public_beta_welcome_read_lore", List.of(
                        "&7Read the rules, commands, worlds,",
                        "&7voice choices, and Test Lab limits.")));
        inventory.setItem(12, actionItem(player, Material.COMPASS, "welcome_open_choices",
                "public_beta_welcome_choices_name", "&bChoose Play or Test Lab",
                "public_beta_welcome_choices_lore", List.of(
                        "&7Open the Public Beta destination menu.")));
        inventory.setItem(14, actionItem(player, Material.CHEST, "welcome_ag_menu",
                "public_beta_welcome_menu_name", "&6Open AegisGuard Menu",
                "public_beta_welcome_menu_lore", List.of(
                        "&7You can return anytime with &e/ag menu&7.",
                        "&7Choose Public Beta from the dashboard.")));
        inventory.setItem(16, actionItem(player, Material.CLOCK, "welcome_close",
                "public_beta_welcome_close_name", "&eDecide Later",
                "public_beta_welcome_close_lore", List.of(
                        "&7Stay in the Welcome Hub for now.",
                        "&7Use &e/ag menu &7or &e/ag beta &7when ready.")));
        player.openInventory(inventory);
    }

    private void handleWelcomeChoice(Player player, String action) {
        switch (action) {
            case "welcome_read_guide" -> {
                ItemStack guide = findGuide(player);
                if (guide == null) {
                    giveGuide(player);
                    guide = findGuide(player);
                }
                if (guide == null) return;
                profiles.set(path(player.getUniqueId()) + ".guide-book-read", true);
                saveProfiles();
                ItemStack readable = guide;
                plugin.runMain(player, () -> {
                    player.closeInventory();
                    player.openBook(readable);
                });
            }
            case "welcome_open_choices" -> openChoiceMenu(player);
            case "welcome_ag_menu" -> plugin.gui().openMain(player);
            case "welcome_close" -> plugin.runMain(player, player::closeInventory);
            default -> { }
        }
    }

    private void handleChoice(Player player, String action) {
        switch (action) {
            case "return_hub" -> returnToHub(player);
            case "enter_play" -> travelTo(player, "play-world", true);
            case "enter_test_lab" -> {
                if (!hasBetaPlayerRole(player.getUniqueId())) {
                    send(player, "public_beta_test_lab_denied", "&cTest Lab access requires the Public Beta Player role.");
                    return;
                }
                travelTo(player, "test-lab", true);
            }
            case "choose_language" -> openLanguagePicker(player);
            case "open_voice" -> openVoiceMenu(player, Origin.GUIDE, null);
            case "report_problem" -> beginFeedback(player, PublicBetaFeedbackService.Category.PROBLEM);
            case "send_feedback" -> beginFeedback(player, PublicBetaFeedbackService.Category.FEEDBACK);
            case "suggest_feature" -> beginFeedback(player, PublicBetaFeedbackService.Category.SUGGESTION);
            case "my_submissions" -> {
                if (plugin.publicBetaFeedback() != null) plugin.publicBetaFeedback().openMySubmissions(player);
            }
            case "open_post" -> {
                if (plugin.publicBetaPost() != null) plugin.publicBetaPost().open(player);
            }
            case "beta_back" -> plugin.gui().openMain(player);
            case "beta_exit" -> plugin.runMain(player, player::closeInventory);
            case "decide_later" -> plugin.runMain(player, player::closeInventory);
            default -> { }
        }
    }

    private void handleVoiceChoice(Player player, Origin origin, SettingsGUI.ReturnTo settingsReturn, String action) {
        if ("beta_exit".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("voice_back".equals(action)) {
            if (origin == Origin.SETTINGS) plugin.gui().settings().open(player,
                    settingsReturn == null ? SettingsGUI.ReturnTo.PLAYER_MENU : settingsReturn);
            else openChoiceMenu(player);
            return;
        }
        if (action == null || !action.startsWith("voice_")) return;
        PublicBetaVoiceMode selected = PublicBetaVoiceMode.parse(
                action.substring("voice_".length()), null);
        setVoiceMode(player, selected);
        send(player, "public_beta_voice_saved", "&aYour Public Beta voice preference was saved.");
        openVoiceMenu(player, origin, settingsReturn);
    }

    public void openWorldWizard(Player player) {
        if (!canManageWorlds(player)) {
            if (player != null) send(player, "public_beta_world_admin_denied", "&cYou cannot manage Public Beta worlds.");
            return;
        }
        PublicBetaWorldService worlds = plugin.publicBetaWorlds();
        if (worlds == null) return;
        Inventory inventory = Bukkit.createInventory(
                new PublicBetaHolder(Screen.WORLD_OVERVIEW, null, Origin.ADMIN, null), 27,
                title(tr(player, "public_beta_worlds_title", "&cPublic Beta Worlds")));
        fill(inventory);
        World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        inventory.setItem(10, GUIManager.createItem(Material.LODESTONE,
                tr(player, "public_beta_world_hub_name", "&bWelcome Hub: Existing Primary"),
                List.of(color("&7" + (primary == null ? "Unavailable" : primary.getName())),
                        color("&8Adopted; never deleted by AegisGuard."))));
        inventory.setItem(12, GUIManager.createItem(Material.GRASS_BLOCK,
                tr(player, "public_beta_world_play_preview_name", "&aPlay World"),
                List.of(color("&7" + worlds.worldName(Role.PLAY_WORLD)), color("&8Normal terrain, structures, random seed."))));
        inventory.setItem(14, GUIManager.createItem(Material.RESPAWN_ANCHOR,
                tr(player, "public_beta_world_lab_preview_name", "&dTest Lab"),
                List.of(color("&7" + worlds.worldName(Role.TEST_LAB)), color("&8Normal terrain, structures, random seed."))));
        inventory.setItem(16, GUIManager.createItem(Material.PAPER,
                tr(player, "public_beta_world_status_name", "&eStatus: {STATE}")
                        .replace("{STATE}", worlds.state().name()),
                List.of(color("&7" + worlds.stateDetail()))));
        ItemStack stage = actionItem(player, Material.COMPARATOR, "world_review",
                "public_beta_world_review_name", "&6Review Provisioning", "public_beta_world_review_lore", List.of(
                        "&7Validate all names and folders, then",
                        "&7open the final restart confirmation."));
        inventory.setItem(22, stage);
        if (worlds.state() == PublicBetaWorldService.State.PENDING_RESTART) {
            inventory.setItem(24, actionItem(player, Material.BARRIER, "world_cancel",
                    "public_beta_world_cancel_name", "&cCancel Pending Setup", "public_beta_world_cancel_lore", List.of(
                            "&7Cancel only before any world is created.", "&7No world data will be deleted.")));
        }
        player.openInventory(inventory);
    }

    public void returnToHub(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!isEnabled() || (!hasBetaPlayerRole(player.getUniqueId()) && !plugin.isAdmin(player))) {
            send(player, "public_beta_command_unavailable",
                    "&cThe Public Beta menu is not available for this profile.");
            return;
        }
        travelTo(player, "welcome-hub", false);
    }

    public void configureHubProtection(Player player, String requested, boolean confirmed) {
        if (player == null || !player.hasPermission(HUB_PROTECTION_PERMISSION) || !plugin.isAdmin(player)) {
            if (player != null) plugin.msg().send(player, "no_perm");
            return;
        }
        int radius;
        boolean entireWorld = requested != null && requested.equalsIgnoreCase("world");
        if (entireWorld) {
            radius = Math.max(16, Math.min(MAX_INDEXED_HUB_RADIUS,
                    plugin.getConfig().getInt(ROOT + ".worlds.welcome-hub.protection-radius", 64)));
        } else {
            try {
                radius = Integer.parseInt(requested == null ? "" : requested);
            } catch (NumberFormatException ex) {
                send(player, "public_beta_hub_protection_usage",
                        "&eUsage: /agadmin publicbeta hubprotect <world|radius> confirm");
                return;
            }
            if (radius < 16 || radius > MAX_INDEXED_HUB_RADIUS) {
                send(player, "public_beta_hub_protection_usage",
                        "&eUsage: /agadmin publicbeta hubprotect <world|radius> confirm");
                return;
            }
        }
        if (!confirmed) {
            String target = entireWorld ? "the entire Hub world" : radius + " blocks from Hub spawn";
            sendReplacing(player, "public_beta_hub_protection_confirm",
                    "&eThis will protect {TARGET}. Repeat with &bconfirm &eto apply it.",
                    Map.of("TARGET", target));
            return;
        }

        World hubWorld = plugin.publicBetaWorlds() == null
                ? Bukkit.getWorld(worldName("welcome-hub"))
                : plugin.publicBetaWorlds().world(Role.WELCOME_HUB);
        if (hubWorld == null || plugin.store() == null) {
            send(player, "public_beta_hub_protection_missing",
                    "&cThe registered Welcome Hub or its protection store is unavailable.");
            return;
        }
        Plot hub = plugin.store().getPlotAt(hubWorld.getSpawnLocation());
        if (hub == null || !hub.isServerZone()) {
            send(player, "public_beta_hub_protection_missing",
                    "&cNo AegisGuard server zone exists at the registered Welcome Hub spawn.");
            return;
        }

        Location spawn = hubWorld.getSpawnLocation();
        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;
        Plot conflict = findHubExpansionConflict(hubWorld, hub, minX, minZ, maxX, maxZ);
        if (conflict != null) {
            sendReplacing(player, "public_beta_hub_protection_conflict",
                    "&cCannot expand Hub protection: plot {PLOT} overlaps the requested area.",
                    Map.of("PLOT", conflict.getPlotId().toString()));
            return;
        }

        plugin.store().updatePlotBounds(hub, minX, minZ, maxX, maxZ);
        plugin.store().savePlotSync(hub);
        plugin.getConfig().set(ROOT + ".worlds.welcome-hub.protection-mode",
                entireWorld ? "ENTIRE_WORLD" : "RADIUS");
        plugin.getConfig().set(ROOT + ".worlds.welcome-hub.protection-radius", radius);
        plugin.saveConfig();
        sendReplacing(player, "public_beta_hub_protection_success",
                entireWorld
                        ? "&aThe entire Welcome Hub world is now protected."
                        : "&aWelcome Hub protection now extends {RADIUS} blocks from spawn.",
                Map.of("RADIUS", String.valueOf(radius)));
    }

    private void openWorldConfirmation(Player player) {
        Inventory inventory = Bukkit.createInventory(
                new PublicBetaHolder(Screen.WORLD_CONFIRM, null, Origin.ADMIN, null), 27,
                title(tr(player, "public_beta_world_confirm_title", "&4Confirm World Provisioning")));
        fill(inventory);
        inventory.setItem(11, actionItem(player, Material.LIME_CONCRETE, "world_confirm",
                "public_beta_world_confirm_name", "&aConfirm and Stage", "public_beta_world_confirm_lore", List.of(
                        "&7Adopt the primary world as Welcome Hub.",
                        "&7Create Play and Test Lab on next restart.",
                        "&cThis does not delete or overwrite worlds.")));
        inventory.setItem(15, actionItem(player, Material.RED_CONCRETE, "world_back",
                "public_beta_world_back_name", "&cGo Back", "public_beta_world_back_lore", List.of("&7Make no changes.")));
        player.openInventory(inventory);
    }

    private void handleWorldWizard(Player player, Screen screen, String action) {
        if (!canManageWorlds(player) || plugin.publicBetaWorlds() == null) {
            player.closeInventory();
            return;
        }
        if ("world_back".equals(action)) { openWorldWizard(player); return; }
        if ("world_review".equals(action)) { openWorldConfirmation(player); return; }
        if ("world_cancel".equals(action)) {
            PublicBetaWorldService.OperationResult result = plugin.publicBetaWorlds().cancelPending();
            player.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            openWorldWizard(player);
            return;
        }
        if (screen == Screen.WORLD_CONFIRM && "world_confirm".equals(action)) {
            World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            PublicBetaWorldService.OperationResult result = plugin.publicBetaWorlds().stageProvisioning(primary);
            player.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            openWorldWizard(player);
        }
    }

    private boolean canManageWorlds(Player player) {
        return player != null && plugin.isAdmin(player)
                && player.hasPermission(PublicBetaWorldService.WORLD_PERMISSION);
    }

    public void openVoiceMenu(Player player, SettingsGUI.ReturnTo settingsReturn) {
        openVoiceMenu(player, Origin.SETTINGS, settingsReturn);
    }

    private void openVoiceMenu(Player player, Origin origin, SettingsGUI.ReturnTo settingsReturn) {
        if (player == null || !hasBetaPlayerRole(player.getUniqueId())) {
            if (player != null) send(player, "public_beta_voice_denied", "&cPublic Beta voice choices require the Public Beta Player role.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                new PublicBetaHolder(Screen.VOICE, null, origin, settingsReturn), 27,
                title(tr(player, "public_beta_voice_title", "&bPublic Beta Voice")));
        fill(inventory);
        PublicBetaVoiceMode selected = voiceMode(player.getUniqueId());
        inventory.setItem(4, GUIManager.createItem(Material.WRITABLE_BOOK,
                tr(player, "public_beta_voice_requirement_name", "&eClient Mod Required"),
                trList(player, "public_beta_voice_requirement_lore", List.of(
                        "&7Hold your talk key and allow your microphone to speak nearby.",
                        "&7No group is required unless you choose Global, Current World,",
                        "&7or a private Simple Voice Chat party.",
                        "&7You may use Prism Launcher or another launcher",
                        "&7that supports your chosen mod loader."))));
        inventory.setItem(11, voiceItem(player, Material.ECHO_SHARD, PublicBetaVoiceMode.PROXIMITY, selected,
                "public_beta_voice_proximity_name", "&aNormal Proximity",
                "public_beta_voice_proximity_lore", List.of(
                        "&7Hold your talk key and use your microphone.",
                        "&7No group is required for nearby voice.")));
        inventory.setItem(13, voiceItem(player, Material.RECOVERY_COMPASS, PublicBetaVoiceMode.GLOBAL, selected,
                "public_beta_voice_global_name", "&bGlobal Public Beta",
                "public_beta_voice_global_lore", List.of(
                        "&7Optional. Talk across Hub, Play, and Test Lab.",
                        "&7Uses a voice group only for this wider channel.")));
        inventory.setItem(15, voiceItem(player, Material.COMPASS, PublicBetaVoiceMode.CURRENT_WORLD, selected,
                "public_beta_voice_world_name", "&dCurrent World Only",
                "public_beta_voice_world_lore", List.of(
                        "&7Optional. Talk only within your current beta world.",
                        "&7Uses a voice group only for this wider channel.")));
        inventory.setItem(22, actionItem(player, Material.ARROW, "voice_back",
                "public_beta_voice_back_name", "&fBack", "public_beta_voice_back_lore", List.of("&7Return to the previous menu.")));
        inventory.setItem(26, actionItem(player, Material.BARRIER, "beta_exit",
                "public_beta_exit_name", "&cExit", "public_beta_exit_lore", List.of("&7Close this menu.")));
        player.openInventory(inventory);
    }

    private ItemStack voiceItem(Player player, Material material, PublicBetaVoiceMode mode,
                                PublicBetaVoiceMode selected, String nameKey, String fallbackName,
                                String loreKey, List<String> fallbackLore) {
        List<String> lore = new ArrayList<>(trList(player, loreKey, fallbackLore));
        lore.add(" ");
        lore.add(tr(player, mode == selected ? "public_beta_voice_selected" : "public_beta_voice_click",
                mode == selected ? "&aSelected" : "&eClick to select."));
        if (Bukkit.getPluginManager().getPlugin("voicechat") == null) {
            lore.add(tr(player, "public_beta_voice_unavailable_lore",
                    "&8Voice chat is unavailable now; this preference will be saved."));
        }
        ItemStack item = GUIManager.createItem(material, tr(player, nameKey, fallbackName), lore);
        plugin.gui().tagAction(item, "voice_" + mode.name().toLowerCase(Locale.ROOT));
        return item;
    }

    private ItemStack destinationItem(Player player, Role role, Material material, String action,
                                      String nameKey, String fallbackName, String loreKey, List<String> fallbackLore) {
        World world = plugin.publicBetaWorlds() == null ? null : plugin.publicBetaWorlds().world(role);
        if (world != null) {
            List<String> lore = new ArrayList<>(trList(player, loreKey, fallbackLore));
            lore.addAll(trList(player, "public_beta_inventory_isolation_lore", List.of(
                    " ", "&cSeparate Inventory",
                    "&7Items, armor, Ender Chest contents, XP,",
                    "&7health, hunger, effects, and game mode stay separate",
                    "&7between Hub, Play World, and Test Lab.")));
            ItemStack item = GUIManager.createItem(material, tr(player, nameKey, fallbackName), lore);
            plugin.gui().tagAction(item, action);
            return item;
        }
        return GUIManager.createItem(Material.BARRIER,
                tr(player, "public_beta_destination_unavailable_name", "&cDestination Not Ready"),
                trList(player, "public_beta_destination_unavailable_lore", List.of(
                        "&7The owner must finish Public Beta world provisioning.",
                        "&7Your current world will not be changed.")));
    }

    private void travelTo(Player player, String worldKey, boolean remember) {
        World world = Bukkit.getWorld(worldName(worldKey));
        if (world == null) {
            send(player, "public_beta_world_unavailable", "&cThat Public Beta destination is not available yet.");
            return;
        }
        SafeTravelResult result = plugin.safeTravel().travel(
                player, world.getSpawnLocation(), SafeTravelService.Kind.OTHER, false);
        if (!result.isSuccess()) return;
        if (remember) {
            profiles.set(path(player.getUniqueId()) + ".last-destination", worldKey);
            saveProfiles();
        }
        result.teleportFuture().thenAccept(success -> {
            if (!success) return;
            // Inventory isolation restores the destination state one tick after
            // the teleport event. Run destination gifts after that restore so
            // the complimentary wand can never be overwritten.
            plugin.runEntityLater(player, () -> {
                if (worldKey.equals("test-lab")) {
                    giveComplimentaryWorldWand(player, worldKey);
                    showFirstArrivalWelcome(player, Role.TEST_LAB);
                    send(player, "public_beta_entered_test_lab", "&aEntered the Test Lab. You only control land you own.");
                } else if (worldKey.equals("welcome-hub")) {
                    send(player, "public_beta_entered_hub",
                            "&aReturned to the Welcome Hub. Use /ag beta to choose your next destination.");
                } else {
                    giveComplimentaryWorldWand(player, worldKey);
                    showFirstArrivalWelcome(player, Role.PLAY_WORLD);
                    send(player, "public_beta_entered_play_world",
                            "&aEntered the Play World. You can return through your guide.");
                }
            }, 2L);
        });
    }

    private void giveComplimentaryWorldWand(Player player, String worldKey) {
        if (player == null || plugin.playerCommand() == null
                || (!"play-world".equals(worldKey) && !"test-lab".equals(worldKey))) return;
        String grantedPath = path(player.getUniqueId()) + ".complimentary-wands." + worldKey;
        if (profiles.getBoolean(grantedPath, false)) return;
        if (plugin.playerCommand().giveClaimWand(player, true)) {
            profiles.set(grantedPath, true);
            saveProfiles();
        }
    }

    private void giveGuide(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (!isGuide(existing)) continue;
            // Refresh tagged books so palette, wording, and selected-language
            // updates reach existing testers instead of remaining baked into
            // an older written-book item.
            inventory.setItem(slot, createGuideBook(player));
            player.updateInventory();
            return;
        }

        ItemStack guide = createGuideBook(player);
        int preferred = Math.max(0, Math.min(8,
                plugin.getConfig().getInt(ROOT + ".guide-book.hotbar-slot",
                        plugin.getConfig().getInt(ROOT + ".guide-paper.hotbar-slot", 1))));
        ItemStack existing = inventory.getItem(preferred);
        if (existing == null || existing.getType().isAir()) inventory.setItem(preferred, guide);
        else inventory.addItem(guide).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.updateInventory();
    }

    private ItemStack findGuide(Player player) {
        if (player == null) return null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isGuide(item) && item.getType() == Material.WRITTEN_BOOK) return item;
        }
        return null;
    }

    private ItemStack createGuideBook(Player player) {
        ItemStack guide = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) guide.getItemMeta();
        if (meta == null) return guide;
        String display = color(tr(player, "public_beta_guide_name", "&bAegisGuard Public Beta Guide"));
        meta.setDisplayName(display);
        String plainTitle = ChatColor.stripColor(display);
        meta.setTitle(plainTitle == null || plainTitle.isBlank()
                ? "AegisGuard Public Beta" : truncate(plainTitle, 32));
        meta.setAuthor("AegisGuard");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        List<String> guideLines = new ArrayList<>(trList(player, "public_beta_guide_lore", List.of(
                "&fWelcome to the AegisGuard Public Beta — Play & Test Server!",
                " ",
                "&aPlay World",
                "&7Play normally with friends, claim land, build, invite trusted",
                "&7players, and enjoy AegisGuard as part of a public community server.",
                " ",
                "&dTest Lab",
                "&7Create your own private testing plot and explore owner-facing features.",
                "&7Experiment freely with plot settings, roles, protections, and other",
                "&7beta systems—without affecting other players or the real server.",
                " ",
                "&cYour Test Lab access is limited to land you own.",
                "&cIt does not provide server administration or other players' plots.",
                " ",
                "&eRight-click to choose your destination.")));
        guideLines.addAll(trList(player, "public_beta_guide_details", List.of(
                " ", "&bGetting Started",
                "&7Use &e/ag menu &7and choose Public Beta whenever you need this menu.",
                "&7You can also use &e/ag beta &7to open it directly.",
                " ", "&aWhat You Can Do",
                "&7Play World: claim, build, invite trusted players, and play normally.",
                "&7Test Lab: test settings, roles, protections, and beta systems on land you own.",
                "&7Change your destination, language, and voice scope later.",
                " ", "&cWhat You Cannot Do",
                "&7You are not server staff or a Minecraft operator.",
                "&7No console, global configuration, staff recovery, or other players' plots.",
                "&7Test Lab never bypasses land ownership.",
                " ", "&bVoice Chat",
                "&7Choose Proximity, Global Beta, or Current World Only.",
                "&7Voice requires Simple Voice Chat on both the server and your client.")));
        guideLines.addAll(trList(player, "public_beta_guide_claiming", List.of(
                " ", "&bHow to Claim Land",
                "&7These methods work in both the Play World and Test Lab.",
                " ", "&aManual Claim",
                "&7Run &b/ag wand &7to receive the Aegis Scepter.",
                "&7Right-click the first corner, then left-click the opposite corner.",
                "&7Run &b/ag claim &7to confirm the selected plot.",
                " ", "&aQuick Claim",
                "&7Stand near the center of the land you want to claim.",
                "&7Run &b/ag quickclaim [radius] &7or &b/ag qc [radius]&7.",
                "&7Leave out the radius to use the server's default plot size.",
                " ", "&cTest Lab Limit",
                "&7You may manage only Test Lab land that you own.")));
        guideLines.addAll(trList(player, "public_beta_guide_wands", List.of(
                " ", "&bComplimentary Claim Wand",
                "&7Your first successful entry into Play World and Test Lab grants one wand.",
                "&7A wand is consumed only after a claim succeeds.",
                "&7Use &b/ag wand &7whenever you need another one.")));
        guideLines.addAll(trList(player, "public_beta_guide_inventory_isolation", List.of(
                " ", "&cSeparate World Inventories",
                "&7Hub, Play World, and Test Lab keep separate items, armor,",
                "&7Ender Chest contents, XP, health, hunger, effects, and game mode.",
                "&7Nothing can be carried between these worlds or traded through the Hub.",
                "&7This protects fair public play and keeps testing contained.")));
        guideLines.addAll(trList(player, "public_beta_guide_hub", List.of(
                " ", "&bReturning to the Hub",
                "&7Use &b/ag hub &7or choose Return to Welcome Hub in &b/ag beta&7.",
                "&7The Hub is for meeting players and choosing a destination.",
                "&cBuilding and creating claims are disabled throughout the Hub world.")));
        guideLines.addAll(trList(player, "public_beta_guide_feedback", List.of(
                " ", "&bReports and Feedback",
                "&7Use the Public Beta menu or main /ag menu to report a problem,",
                "&7send general feedback, or suggest a feature.",
                "&7Commands: /ag report, /ag feedback, and /ag suggest.",
                "&7Submissions are sent privately to the staff inbox.")));
        meta.setPages(guidePages(guideLines));
        meta.setLore(colorize(trList(player, "public_beta_guide_book_lore", List.of(
                "&7First right-click: read this guide.",
                "&7Later right-clicks: open the beta menu.",
                "&8Sneak + right-click to read it again."))));
        meta.getPersistentDataContainer().set(guideKey, PersistentDataType.BYTE, (byte) 1);
        guide.setItemMeta(meta);
        return guide;
    }

    private ItemStack actionItem(Player player, Material material, String action,
                                 String nameKey, String nameFallback,
                                 String loreKey, List<String> loreFallback) {
        ItemStack item = GUIManager.createItem(material, tr(player, nameKey, nameFallback),
                trList(player, loreKey, loreFallback));
        plugin.gui().tagAction(item, action);
        return item;
    }

    private void beginFeedback(Player player, PublicBetaFeedbackService.Category category) {
        if (plugin.publicBetaFeedback() == null) {
            send(player, "public_beta_feedback_unavailable", "&cPublic Beta reporting is unavailable.");
            return;
        }
        plugin.publicBetaFeedback().begin(player, category);
    }

    private List<String> guidePages(List<String> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String raw : lines) {
            String line = readableBookColor(raw);
            if (ChatColor.stripColor(line).isBlank()) {
                flushGuidePage(pages, page);
                continue;
            }
            int separator = page.isEmpty() ? 0 : 1;
            if (!page.isEmpty() && page.length() + separator + line.length() > 220) {
                flushGuidePage(pages, page);
            }
            if (!page.isEmpty()) page.append('\n');
            page.append(line);
        }
        flushGuidePage(pages, page);
        return pages.isEmpty() ? List.of("AegisGuard Public Beta") : pages;
    }

    private String readableBookColor(String raw) {
        // Written books use a light paper background. White and yellow text
        // lose contrast there, so keep those two colors in the user's
        // preferred readable palette without changing menu colors.
        return color(raw)
                .replace(ChatColor.WHITE.toString(), ChatColor.GRAY.toString())
                .replace(ChatColor.YELLOW.toString(), ChatColor.DARK_AQUA.toString());
    }

    private void flushGuidePage(List<String> pages, StringBuilder page) {
        if (page.isEmpty()) return;
        pages.add(truncate(page.toString(), 255));
        page.setLength(0);
    }

    private String truncate(String value, int maximum) {
        if (value == null || value.length() <= maximum) return value;
        return value.substring(0, maximum);
    }

    private boolean isGuide(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(guideKey, PersistentDataType.BYTE);
    }

    private String detectStyle(String clientLocale) {
        if (clientLocale == null || clientLocale.isBlank()) return null;
        String locale = clientLocale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (locale.startsWith("en_")) return "modern_english";
        if (locale.equals("es_ar")) return "spanish_ar";
        if (locale.startsWith("es_")) return "spanish_mx";
        if (locale.startsWith("pt_")) return "portuguese_br";
        if (locale.startsWith("fr_")) return "french_fr";
        if (locale.startsWith("it_")) return "italian_it";
        if (locale.startsWith("de_")) return "german_de";
        if (locale.startsWith("pl_")) return "polish_pl";
        return null;
    }

    private String worldName(String key) {
        if (plugin.publicBetaWorlds() != null) {
            return plugin.publicBetaWorlds().worldName(switch (key) {
                case "welcome-hub" -> Role.WELCOME_HUB;
                case "play-world" -> Role.PLAY_WORLD;
                default -> Role.TEST_LAB;
            });
        }
        return plugin.getConfig().getString(ROOT + ".worlds." + key, switch (key) {
            case "welcome-hub" -> "aegis_beta_hub";
            case "play-world" -> "aegis_beta_play";
            default -> "aegis_beta_test_lab";
        }).trim();
    }

    private void ensureHubProtection(World hubWorld) {
        if (plugin.store() == null || hubWorld == null) return;
        Location spawn = hubWorld.getSpawnLocation();
        Plot existing = plugin.store().getPlotAt(spawn);
        if (existing != null) {
            if (!existing.isServerZone()) {
                plugin.getLogger().severe("[Public Beta] Welcome Hub spawn overlaps player plot "
                        + existing.getPlotId() + "; refusing to overwrite it.");
            } else {
                resizeExistingHubFromConfig(hubWorld, existing);
            }
            return;
        }
        if (plugin.publicBetaWorlds() == null || !plugin.publicBetaWorlds().isRegistered()) {
            plugin.getLogger().warning("[Public Beta] World provisioning is not registered; Hub protection was not created.");
            return;
        }
        int radius = configuredHubRadius();
        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;
        for (Plot plot : plugin.store().getPlotsInWorld(hubWorld.getName())) {
            if (plot == null) continue;
            boolean overlaps = minX <= Math.max(plot.getX1(), plot.getX2())
                    && maxX >= Math.min(plot.getX1(), plot.getX2())
                    && minZ <= Math.max(plot.getZ1(), plot.getZ2())
                    && maxZ >= Math.min(plot.getZ1(), plot.getZ2());
            if (overlaps) {
                plugin.getLogger().severe("[Public Beta] Planned Hub protection overlaps plot "
                        + plot.getPlotId() + "; refusing to overwrite it.");
                return;
            }
        }
        Plot hub = new Plot(UUID.randomUUID(), Plot.SERVER_OWNER_UUID, "Server", hubWorld.getName(),
                minX, minZ, maxX, maxZ, System.currentTimeMillis());
        hub.setPlotName("AegisGuard Public Beta Welcome Hub");
        hub.setWarpCategory("HUB");
        ProtectionPreset.HUB.apply(hub);
        plugin.store().addPlot(hub);
        plugin.getLogger().info("[Public Beta] Created protected Welcome Hub server zone " + hub.getPlotId() + ".");
    }

    private void ensureArrivalProtection(Role role) {
        if (plugin.store() == null || plugin.publicBetaWorlds() == null || role == Role.WELCOME_HUB) return;
        World world = plugin.publicBetaWorlds().world(role);
        if (world == null) return;
        String managedName = role == Role.PLAY_WORLD
                ? "AegisGuard Play World Arrival" : "AegisGuard Test Lab Arrival";
        Location spawn = world.getSpawnLocation();
        int radius = configuredArrivalRadius(role);
        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;
        var worldPlots = plugin.store().getPlotsInWorld(world.getName());
        Plot managed = worldPlots.stream()
                .filter(plot -> isManagedArrivalZone(plot, role))
                .findFirst().orElse(null);
        if (managed == null) {
            List<Plot> legacyCandidates = worldPlots.stream()
                    .filter(plot -> isLegacyArrivalCandidate(plot, radius))
                    .toList();
            if (legacyCandidates.size() == 1) {
                managed = legacyCandidates.get(0);
                markArrivalZone(managed, role);
                plugin.store().savePlotSync(managed);
                plugin.getLogger().info("[Public Beta] Registered legacy " + role.key()
                        + " arrival zone " + managed.getPlotId() + " with durable identity flags.");
            }
        }
        Plot atSpawn = plugin.store().getPlotAt(spawn);
        if (atSpawn != null) {
            if (managed == null || !atSpawn.getPlotId().equals(managed.getPlotId())) {
                plugin.getLogger().severe("[Public Beta] " + role.key() + " spawn overlaps foreign plot "
                        + atSpawn.getPlotId() + "; refusing to overwrite it.");
            } else if (!sameBounds(atSpawn, minX, minZ, maxX, maxZ)) {
                relocateArrivalZone(world, role, atSpawn, minX, minZ, maxX, maxZ);
            }
            return;
        }
        if (managed != null) {
            relocateArrivalZone(world, role, managed, minX, minZ, maxX, maxZ);
            return;
        }
        for (Plot plot : worldPlots) {
            if (plot != null && overlaps(minX, minZ, maxX, maxZ, plot)) {
                plugin.getLogger().severe("[Public Beta] Planned " + role.key()
                        + " arrival protection overlaps plot " + plot.getPlotId() + "; refusing to overwrite it.");
                return;
            }
        }
        Plot zone = new Plot(UUID.randomUUID(), Plot.SERVER_OWNER_UUID, "Server", world.getName(),
                minX, minZ, maxX, maxZ, System.currentTimeMillis());
        zone.setPlotName(managedName);
        ProtectionPreset.HUB.apply(zone);
        markArrivalZone(zone, role);
        plugin.store().addPlot(zone);
        plugin.getLogger().info("[Public Beta] Created protected " + role.key()
                + " arrival zone with radius " + radius + ".");
    }

    private void markArrivalZone(Plot plot, Role role) {
        // Arrival areas must always be publicly walkable safe zones. Keep
        // these explicit so the settings UI matches the server-zone runtime
        // protections instead of showing a confusing disabled/private state.
        plot.setFlag("safe_zone", true);
        plot.setFlag("entry", true);
        plot.setFlag("storm-ward", true);
        plot.setFlag("public_beta_arrival", true);
        plot.setFlag(role == Role.PLAY_WORLD
                ? "public_beta_play_arrival" : "public_beta_test_lab_arrival", true);
    }

    private boolean isManagedArrivalZone(Plot plot, Role role) {
        if (plot == null || !plot.isServerZone() || !plot.getFlag("public_beta_arrival", false)) return false;
        return plot.getFlag(role == Role.PLAY_WORLD
                ? "public_beta_play_arrival" : "public_beta_test_lab_arrival", false);
    }

    private boolean isLegacyArrivalCandidate(Plot plot, int radius) {
        if (plot == null || !plot.isServerZone()) return false;
        int diameter = radius * 2;
        return Math.abs(plot.getX2() - plot.getX1()) == diameter
                && Math.abs(plot.getZ2() - plot.getZ1()) == diameter;
    }

    private void relocateArrivalZone(World world, Role role, Plot managed,
                                     int minX, int minZ, int maxX, int maxZ) {
        for (Plot plot : plugin.store().getPlotsInWorld(world.getName())) {
            if (plot == null || plot.getPlotId().equals(managed.getPlotId())) continue;
            if (overlaps(minX, minZ, maxX, maxZ, plot)) {
                plugin.getLogger().severe("[Public Beta] Cannot move " + role.key()
                        + " arrival protection to the current world spawn because plot "
                        + plot.getPlotId() + " overlaps it; refusing to overwrite it.");
                return;
            }
        }
        plugin.store().updatePlotBounds(managed, minX, minZ, maxX, maxZ);
        plugin.store().savePlotSync(managed);
        plugin.getLogger().info("[Public Beta] Moved protected " + role.key()
                + " arrival zone to the current world spawn.");
    }

    private boolean sameBounds(Plot plot, int minX, int minZ, int maxX, int maxZ) {
        return Math.min(plot.getX1(), plot.getX2()) == minX
                && Math.min(plot.getZ1(), plot.getZ2()) == minZ
                && Math.max(plot.getX1(), plot.getX2()) == maxX
                && Math.max(plot.getZ1(), plot.getZ2()) == maxZ;
    }

    private void showFirstArrivalWelcome(Player player, Role role) {
        if (player == null || role == Role.WELCOME_HUB) return;
        String key = role == Role.PLAY_WORLD ? "play-world" : "test-lab";
        String seenPath = path(player.getUniqueId()) + ".arrival-welcome." + key;
        if (profiles.getBoolean(seenPath, false)) return;
        int distance = configuredArrivalRadius(role) + 1;
        sendReplacing(player, "public_beta_spawn_welcome_message",
                "&bWelcome! &7This spawn is protected. Travel at least &f{DISTANCE} blocks &7in any direction before creating your plot.",
                Map.of("DISTANCE", String.valueOf(distance)));
        profiles.set(seenPath, true);
        saveProfiles();
    }

    private Role arrivalRole(World world) {
        if (world == null || plugin.publicBetaWorlds() == null) return null;
        if (world.getUID().equals(plugin.publicBetaWorlds().world(Role.PLAY_WORLD) == null
                ? null : plugin.publicBetaWorlds().world(Role.PLAY_WORLD).getUID())) return Role.PLAY_WORLD;
        if (world.getUID().equals(plugin.publicBetaWorlds().world(Role.TEST_LAB) == null
                ? null : plugin.publicBetaWorlds().world(Role.TEST_LAB).getUID())) return Role.TEST_LAB;
        return null;
    }

    private int configuredArrivalRadius(Role role) {
        return Math.max(16, Math.min(MAX_INDEXED_HUB_RADIUS, plugin.getConfig().getInt(
                ROOT + ".worlds." + role.key() + ".spawn-protection-radius", 50)));
    }

    private boolean overlaps(Plot first, Plot second) {
        return overlaps(Math.min(first.getX1(), first.getX2()), Math.min(first.getZ1(), first.getZ2()),
                Math.max(first.getX1(), first.getX2()), Math.max(first.getZ1(), first.getZ2()), second);
    }

    private boolean overlaps(int minX, int minZ, int maxX, int maxZ, Plot plot) {
        return minX <= Math.max(plot.getX1(), plot.getX2())
                && maxX >= Math.min(plot.getX1(), plot.getX2())
                && minZ <= Math.max(plot.getZ1(), plot.getZ2())
                && maxZ >= Math.min(plot.getZ1(), plot.getZ2());
    }

    private void resizeExistingHubFromConfig(World hubWorld, Plot hub) {
        int radius = configuredHubRadius();
        Location spawn = hubWorld.getSpawnLocation();
        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;
        if (hub.getX1() == minX && hub.getX2() == maxX && hub.getZ1() == minZ && hub.getZ2() == maxZ) return;
        Plot conflict = findHubExpansionConflict(hubWorld, hub, minX, minZ, maxX, maxZ);
        if (conflict != null) {
            plugin.getLogger().severe("[Public Beta] Hub protection expansion overlaps plot "
                    + conflict.getPlotId() + "; refusing to overwrite it. Resolve the plot, then run "
                    + "/agadmin publicbeta hubprotect world confirm.");
            return;
        }
        plugin.store().updatePlotBounds(hub, minX, minZ, maxX, maxZ);
        plugin.store().savePlotSync(hub);
        plugin.getLogger().info("[Public Beta] Updated Welcome Hub protection radius to " + radius + " blocks.");
    }

    private Plot findHubExpansionConflict(World world, Plot hub, int minX, int minZ, int maxX, int maxZ) {
        for (Plot plot : plugin.store().getPlotsInWorld(world.getName())) {
            if (plot == null || plot.getPlotId().equals(hub.getPlotId())) continue;
            boolean overlaps = minX <= Math.max(plot.getX1(), plot.getX2())
                    && maxX >= Math.min(plot.getX1(), plot.getX2())
                    && minZ <= Math.max(plot.getZ1(), plot.getZ2())
                    && maxZ >= Math.min(plot.getZ1(), plot.getZ2());
            if (overlaps) return plot;
        }
        return null;
    }

    private int configuredHubRadius() {
        return Math.max(16, Math.min(MAX_INDEXED_HUB_RADIUS,
                plugin.getConfig().getInt(ROOT + ".worlds.welcome-hub.protection-radius", 64)));
    }

    private boolean isEntireHubProtected(World world) {
        return isHubWorld(world) && "ENTIRE_WORLD".equalsIgnoreCase(plugin.getConfig().getString(
                ROOT + ".worlds.welcome-hub.protection-mode", "ENTIRE_WORLD"));
    }

    private boolean isHubWorld(World world) {
        if (world == null) return false;
        World registered = plugin.publicBetaWorlds() == null ? null
                : plugin.publicBetaWorlds().world(Role.WELCOME_HUB);
        return registered != null
                ? registered.getUID().equals(world.getUID())
                : world.getName().equalsIgnoreCase(worldName("welcome-hub"));
    }

    private boolean canModifyHub(Player player) {
        return player != null && plugin.isAdmin(player);
    }

    private boolean isOnboardingComplete(UUID id) {
        return profiles.getBoolean(path(id) + ".onboarding-complete", false);
    }

    private String path(UUID id) {
        return "players." + id;
    }

    private YamlConfiguration loadProfiles() {
        if (!profileFile.isFile()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(profileFile);
    }

    private synchronized void saveProfiles() {
        if (!isolationValid) return;
        try {
            File parent = profileFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            profiles.set("instance-id", plugin.getConfig().getString(ROOT + ".isolation.instance-id", "aegisguard-public-beta"));
            profiles.save(profileFile);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save Public Beta player profiles.", error);
        }
    }

    private boolean verifyStoredInstanceIdentity() {
        String stored = profiles.getString("instance-id", "").trim();
        String configured = plugin.getConfig().getString(
                ROOT + ".isolation.instance-id", "aegisguard-public-beta").trim();
        if (stored.isEmpty() || stored.equals(configured)) return true;
        plugin.getLogger().severe("[Public Beta] Refusing to use player profiles from instance '"
                + stored + "' on configured instance '" + configured + "'. Restore the correct Public Beta data folder.");
        return false;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private String tr(Player player, String key, String fallback) {
        String value = plugin.gui().tr(player, key, fallback);
        return value == null || value.equals(key) ? fallback : value;
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        List<String> value = plugin.gui().trList(player, key, fallback);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(color(tr(player, key, fallback)));
    }

    private void sendReplacing(Player player, String key, String fallback, Map<String, String> placeholders) {
        String message = tr(player, key, fallback);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(color(message));
    }

    private List<String> colorize(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(color(line));
        return out;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private String title(String value) {
        String colored = color(value);
        return colored.length() <= 32 ? colored : colored.substring(0, 32);
    }

    private enum Screen { LANGUAGE_CONFIRM, WELCOME, CHOICE, VOICE, WORLD_OVERVIEW, WORLD_CONFIRM }
    private enum Origin { GUIDE, SETTINGS, ADMIN }

    private record PendingLanguage(String style, boolean supported) { }

    public static final class PublicBetaHolder implements InventoryHolder {
        private final Screen screen;
        private final String detectedStyle;
        private final Origin origin;
        private final SettingsGUI.ReturnTo settingsReturn;

        private PublicBetaHolder(Screen screen, String detectedStyle, Origin origin,
                                 SettingsGUI.ReturnTo settingsReturn) {
            this.screen = screen;
            this.detectedStyle = detectedStyle;
            this.origin = origin == null ? Origin.GUIDE : origin;
            this.settingsReturn = settingsReturn;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
