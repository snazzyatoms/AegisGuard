package com.aegisguard.publicbeta;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Experimental, text-only Public Beta player mail and physical sealed-letter service. */
public final class PublicBetaPostService implements Listener {

    private static final String ROOT = "public-beta-mode.post";
    private static final int PAGE_SIZE = 45;
    private static final int MAX_PAGES = 20;
    private static final int MAX_PAGE_LENGTH = 256;
    private static final long DEFAULT_COOLDOWN_MILLIS = 30_000L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AegisGuard plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey draftKey;
    private final NamespacedKey draftIdKey;
    private final NamespacedKey mailKey;
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();
    private final boolean isolationValid;

    public PublicBetaPostService(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "public-beta/post-office.yml");
        this.data = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        this.draftKey = new NamespacedKey(plugin, "aegis_post_draft");
        this.draftIdKey = new NamespacedKey(plugin, "aegis_post_draft_id");
        this.mailKey = new NamespacedKey(plugin, "aegis_post_mail_id");
        String stored = data.getString("instance-id", "").trim();
        String configured = plugin.getConfig().getString("public-beta-mode.isolation.instance-id",
                "aegisguard-public-beta").trim();
        this.isolationValid = stored.isEmpty() || stored.equals(configured);
        if (!isolationValid) {
            plugin.getLogger().severe("[Aegis Post] Refusing mail from another Public Beta instance.");
        }
    }

    public boolean isEnabled() {
        return isolationValid && plugin.publicBeta() != null && plugin.publicBeta().isEnabled()
                && plugin.getConfig().getBoolean(ROOT + ".enabled", true);
    }

    public void open(Player player) {
        if (!canUse(player)) return;
        Inventory inventory = menu(new PostHolder(Screen.MAIN, 0, null, -1, null), 27,
                tr(player, "post_title", "&6Aegis Post"));
        inventory.setItem(4, item(player, Material.BELL, "post_beta_name", "&eExperimental Public Beta Feature",
                "post_beta_lore", List.of("&7Aegis Post is being tested in this Public Beta.",
                        "&7It may enter a future AegisGuard release", "&7after it is proven stable and safe."), null));
        inventory.setItem(10, item(player, Material.CHEST, "post_inbox_name", "&aInbox",
                "post_inbox_lore", List.of("&7Read sealed letters sent to you.", "&eClick to open."), "inbox"));
        inventory.setItem(12, item(player, Material.WRITABLE_BOOK, "post_draft_name", "&bCreate Draft Letter",
                "post_draft_lore", List.of("&7Receive a writable Aegis Post draft.",
                        "&7Write in it, then return here to send it."), "draft"));
        inventory.setItem(14, item(player, Material.PAPER, "post_send_name", "&6Send a Letter",
                "post_send_lore", List.of("&7Choose a completed draft from your inventory", "&7and hand it to the Aegis Courier."), "drafts"));
        inventory.setItem(16, item(player, Material.BOOK, "post_sent_name", "&bSent Mail",
                "post_sent_lore", List.of("&7Review read-only records of letters you sent."), "sent"));
        inventory.setItem(22, item(player, Material.IRON_DOOR, "post_blocked_name", "&cBlocked Players",
                "post_blocked_lore", List.of("&7Manage who cannot send you letters."), "blocked"));
        inventory.setItem(24, item(player, Material.PAPER, "post_future_name", "&bPotential Future Features",
                "post_future_lore", List.of("&7Item or money attachments may be considered", "&7only after delivery is proven stable and safe.",
                        "&8These additions are not currently promised."), null));
        inventory.setItem(18, item(player, Material.ARROW, "public_beta_back_name", "&aBack",
                "public_beta_back_lore", List.of("&7Return to the Public Beta menu."), "back"));
        inventory.setItem(26, item(player, Material.BARRIER, "public_beta_exit_name", "&cExit",
                "public_beta_exit_lore", List.of("&7Close this menu."), "close"));
        player.openInventory(inventory);
    }

    private void openDrafts(Player player, int page) {
        List<Draft> drafts = drafts(player);
        Inventory inventory = menu(new PostHolder(Screen.DRAFTS, page, null, -1, null), 54,
                tr(player, "post_drafts_title", "&6Letters Ready to Send"));
        int from = Math.max(0, page) * PAGE_SIZE;
        for (int index = from; index < Math.min(drafts.size(), from + PAGE_SIZE); index++) {
            Draft draft = drafts.get(index);
            ItemStack display = item(player, Material.WRITABLE_BOOK, "post_draft_entry_name",
                    "&f{SUBJECT}", "post_draft_entry_lore",
                    List.of("&7Pages: &f{PAGES}", "&eClick to choose a recipient."), "choose_draft:" + draft.slot());
            replace(display, Map.of("SUBJECT", draft.subject(), "PAGES", String.valueOf(draft.pages().size())));
            inventory.setItem(index - from, display);
        }
        if (drafts.isEmpty()) inventory.setItem(22, item(player, Material.PAPER, "post_no_drafts_name",
                "&7No completed drafts", "post_no_drafts_lore",
                List.of("&7Create a draft, write your letter,", "&7then return to send it."), null));
        navigation(player, inventory, page, drafts.size(), "drafts");
        player.openInventory(inventory);
    }

    private void openRecipients(Player player, int draftSlot, int page) {
        ItemStack draftItem = validDraft(player, draftSlot);
        if (draftItem == null) { send(player, "post_draft_missing", "&cThat draft is no longer available.", Map.of()); open(player); return; }
        List<OfflinePlayer> recipients = recipients(player);
        Inventory inventory = menu(new PostHolder(Screen.RECIPIENTS, page, null, draftSlot, null), 54,
                tr(player, "post_recipients_title", "&6Choose a Recipient"));
        int from = Math.max(0, page) * PAGE_SIZE;
        for (int index = from; index < Math.min(recipients.size(), from + PAGE_SIZE); index++) {
            OfflinePlayer target = recipients.get(index);
            String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
            ItemStack entry = GUIManager.createItem(Material.NAME_TAG,
                    color("&f" + name), List.of(color(tr(player, "post_recipient_lore", "&eClick to address this letter."))));
            tag(entry, "recipient:" + target.getUniqueId());
            inventory.setItem(index - from, entry);
        }
        if (recipients.isEmpty()) inventory.setItem(22, item(player, Material.BARRIER, "post_no_recipients_name",
                "&7No recipients available", "post_no_recipients_lore",
                List.of("&7No other Public Beta players are known yet."), null));
        navigation(player, inventory, page, recipients.size(), "recipients");
        player.openInventory(inventory);
    }

    private void openConfirm(Player player, int draftSlot, UUID recipient) {
        ItemStack draft = validDraft(player, draftSlot);
        OfflinePlayer target = recipient == null ? null : Bukkit.getOfflinePlayer(recipient);
        if (draft == null || target == null || !isEligibleRecipient(player, target)) {
            send(player, "post_send_invalid", "&cThat letter or recipient is no longer available.", Map.of());
            open(player); return;
        }
        BookMeta meta = (BookMeta) draft.getItemMeta();
        String targetName = target.getName() == null ? recipient.toString() : target.getName();
        String subject = subject(meta);
        Inventory inventory = menu(new PostHolder(Screen.CONFIRM, 0, null, draftSlot, recipient), 27,
                tr(player, "post_confirm_title", "&6Seal and Send"));
        ItemStack preview = item(player, Material.WRITABLE_BOOK, "post_confirm_preview_name", "&f{SUBJECT}",
                "post_confirm_preview_lore", List.of("&7To: &f{PLAYER}", "&7Pages: &f{PAGES}"), null);
        replace(preview, Map.of("SUBJECT", subject, "PLAYER", targetName,
                "PAGES", String.valueOf(safePages(meta).size())));
        inventory.setItem(13, preview);
        inventory.setItem(11, item(player, Material.LIME_CONCRETE, "post_confirm_send_name", "&aHand to Courier",
                "post_confirm_send_lore", List.of("&7Seal this letter and send it now.", "&cThe draft leaves your inventory."), "confirm_send"));
        inventory.setItem(15, item(player, Material.RED_CONCRETE, "post_confirm_cancel_name", "&cGo Back",
                "post_confirm_cancel_lore", List.of("&7Keep your draft and choose again."), "confirm_back"));
        inventory.setItem(26, item(player, Material.BARRIER, "public_beta_exit_name", "&cExit",
                "public_beta_exit_lore", List.of("&7Close this menu."), "close"));
        player.openInventory(inventory);
    }

    private void openInbox(Player player, int page) { openMailList(player, page, false); }
    private void openSent(Player player, int page) { openMailList(player, page, true); }

    private void openMailList(Player player, int page, boolean sent) {
        List<Mail> mails = sent ? sent(player.getUniqueId()) : inbox(player.getUniqueId());
        Inventory inventory = menu(new PostHolder(sent ? Screen.SENT : Screen.INBOX, page, null, -1, null), 54,
                tr(player, sent ? "post_sent_title" : "post_inbox_title", sent ? "&6Sent Mail" : "&6Post Office Inbox"));
        int from = Math.max(0, page) * PAGE_SIZE;
        for (int index = from; index < Math.min(mails.size(), from + PAGE_SIZE); index++) {
            Mail mail = mails.get(index);
            String other = sent ? mail.recipientName() : mail.senderName();
            List<String> fallback = sent
                    ? List.of("&7To: &f{PLAYER}", "&7Sent: &f{DATE}", "&eClick to read your sent copy.")
                    : List.of("&7From: &f{PLAYER}", "&7Received: &f{DATE}", "&eLeft-click to read.", "&cRight-click to report.");
            ItemStack entry = item(player, mail.read() || sent ? Material.PAPER : Material.MAP,
                    sent ? "post_sent_entry_name" : "post_inbox_entry_name",
                    sent ? "&f{SUBJECT}" : "&e{SUBJECT}", sent ? "post_sent_entry_lore" : "post_inbox_entry_lore",
                    fallback, "mail:" + mail.id());
            replace(entry, Map.of("SUBJECT", mail.subject(), "PLAYER", other, "DATE", DATE.format(Instant.ofEpochMilli(mail.createdAt()))));
            inventory.setItem(index - from, entry);
        }
        if (mails.isEmpty()) inventory.setItem(22, item(player, Material.PAPER,
                sent ? "post_sent_empty_name" : "post_inbox_empty_name",
                sent ? "&7No sent letters" : "&7Your inbox is empty",
                sent ? "post_sent_empty_lore" : "post_inbox_empty_lore",
                List.of(sent ? "&7Letters you send will appear here." : "&7New letters will appear here."), null));
        navigation(player, inventory, page, mails.size(), sent ? "sent" : "inbox");
        player.openInventory(inventory);
    }

    private void openBlocked(Player player) {
        Set<UUID> blocked = blocked(player.getUniqueId());
        Inventory inventory = menu(new PostHolder(Screen.BLOCKED, 0, null, -1, null), 54,
                tr(player, "post_blocked_title", "&cBlocked Players"));
        int slot = 0;
        for (UUID id : blocked) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(id);
            String name = target.getName() == null ? id.toString() : target.getName();
            ItemStack entry = GUIManager.createItem(Material.BARRIER, color("&c" + name),
                    List.of(color(tr(player, "post_unblock_lore", "&eClick to allow mail again."))));
            tag(entry, "unblock:" + id);
            inventory.setItem(slot++, entry);
            if (slot >= PAGE_SIZE) break;
        }
        if (blocked.isEmpty()) inventory.setItem(22, item(player, Material.LIME_DYE, "post_blocked_empty_name",
                "&aNobody is blocked", "post_blocked_empty_lore", List.of("&7You may receive mail from eligible beta players."), null));
        inventory.setItem(45, item(player, Material.NAME_TAG, "post_block_player_name", "&cBlock a Player",
                "post_block_player_lore", List.of("&7Enter a player name in chat."), "block_player"));
        inventory.setItem(48, item(player, Material.ARROW, "public_beta_back_name", "&aBack",
                "public_beta_back_lore", List.of("&7Return to Aegis Post."), "main"));
        inventory.setItem(49, item(player, Material.BARRIER, "public_beta_exit_name", "&cExit",
                "public_beta_exit_lore", List.of("&7Close this menu."), "close"));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof PostHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(event.getCurrentItem());
        if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.equals("back")) { plugin.publicBeta().openBetaMenu(player); return; }
        if (action.equals("main")) { open(player); return; }
        if (action.equals("draft")) { giveDraft(player); return; }
        if (action.equals("drafts")) { openDrafts(player, 0); return; }
        if (action.equals("inbox")) { openInbox(player, 0); return; }
        if (action.equals("sent")) { openSent(player, 0); return; }
        if (action.equals("blocked")) { openBlocked(player); return; }
        if (action.equals("block_player")) { beginBlock(player); return; }
        if (action.equals("confirm_back")) { openRecipients(player, holder.draftSlot(), 0); return; }
        if (action.equals("confirm_send")) { sendLetter(player, holder.draftSlot(), holder.recipient()); return; }
        if (action.startsWith("choose_draft:")) { openRecipients(player, integer(action.substring(13), -1), 0); return; }
        if (action.startsWith("recipient:")) { openConfirm(player, holder.draftSlot(), uuid(action.substring(10))); return; }
        if (action.startsWith("unblock:")) { unblock(player, uuid(action.substring(8))); return; }
        if (action.startsWith("mail:")) {
            String id = action.substring(5);
            if (event.isRightClick() && holder.screen() == Screen.INBOX) report(player, id);
            else read(player, id);
            return;
        }
        if (action.startsWith("page:")) {
            int next = integer(action.substring(5), 0);
            switch (holder.screen()) {
                case DRAFTS -> openDrafts(player, next);
                case RECIPIENTS -> openRecipients(player, holder.draftSlot(), next);
                case INBOX -> openInbox(player, next);
                case SENT -> openSent(player, next);
                default -> open(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSealedLetter(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(mailKey, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        read(event.getPlayer(), id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PendingInput input = pending.remove(event.getPlayer().getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String value = ChatColor.stripColor(event.getMessage() == null ? "" : event.getMessage().trim());
        plugin.runMain(event.getPlayer(), () -> completeBlock(event.getPlayer(), value));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!canUseQuiet(player)) return;
        long unread = inbox(player.getUniqueId()).stream().filter(mail -> !mail.read()).count();
        if (unread > 0) send(player, "post_join_notice", "&6You’ve got mail! &f{COUNT} &6unread letter(s) await in Aegis Post.",
                Map.of("COUNT", String.valueOf(unread)));
        deliverPhysical(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        lastSent.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDraftEdited(PlayerEditBookEvent event) {
        ItemStack held = event.getPlayer().getInventory().getItem(event.getSlot());
        if (held == null || !(held.getItemMeta() instanceof BookMeta oldMeta)
                || oldMeta.getPersistentDataContainer().get(draftKey, PersistentDataType.BYTE) == null) return;
        BookMeta updated = event.getNewBookMeta();
        updated.getPersistentDataContainer().set(draftKey, PersistentDataType.BYTE, (byte) 1);
        String draftId = oldMeta.getPersistentDataContainer().get(draftIdKey, PersistentDataType.STRING);
        if (draftId != null) updated.getPersistentDataContainer().set(draftIdKey, PersistentDataType.STRING, draftId);
        event.setNewBookMeta(updated);
    }

    public synchronized void save() { saveAtomic(); }

    private void giveDraft(Player player) {
        ItemStack draft = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) draft.getItemMeta();
        meta.setDisplayName(color(tr(player, "post_draft_item_name", "&bAegis Post Draft Letter")));
        meta.setLore(colorList(trList(player, "post_draft_item_lore", List.of("&7Write your letter, then open Aegis Post", "&7and choose Send a Letter."))));
        meta.getPersistentDataContainer().set(draftKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(draftIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        draft.setItemMeta(meta);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(draft);
        if (!overflow.isEmpty()) { send(player, "post_inventory_full", "&cMake room in your inventory first.", Map.of()); return; }
        player.closeInventory();
        send(player, "post_draft_given", "&aA draft letter was placed in your inventory. Right-click it to write.", Map.of());
    }

    private void sendLetter(Player sender, int draftSlot, UUID recipientId) {
        ItemStack draft = validDraft(sender, draftSlot);
        OfflinePlayer recipient = recipientId == null ? null : Bukkit.getOfflinePlayer(recipientId);
        if (draft == null || recipient == null || !isEligibleRecipient(sender, recipient)) {
            send(sender, "post_send_invalid", "&cThat letter or recipient is no longer available.", Map.of()); open(sender); return;
        }
        if (blocked(recipientId).contains(sender.getUniqueId())) {
            send(sender, "post_recipient_blocked", "&cThat player is not accepting letters from you.", Map.of()); return;
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, plugin.getConfig().getLong(ROOT + ".send-cooldown-seconds", 30L) * 1000L);
        if (now - lastSent.getOrDefault(sender.getUniqueId(), 0L) < cooldown) {
            send(sender, "post_cooldown", "&cPlease wait before sending another letter.", Map.of()); return;
        }
        BookMeta meta = (BookMeta) draft.getItemMeta();
        List<String> pages = safePages(meta);
        if (pages.isEmpty() || pages.stream().allMatch(String::isBlank)) {
            send(sender, "post_empty_letter", "&cWrite something in the letter before sending it.", Map.of()); return;
        }
        String draftId = meta.getPersistentDataContainer().get(draftIdKey, PersistentDataType.STRING);
        if (draftId == null || uuid(draftId) == null) {
            send(sender, "post_send_invalid", "&cThat letter or recipient is no longer available.", Map.of()); return;
        }
        String deliveredMail = data.getString("delivered-drafts." + draftId);
        if (deliveredMail != null && mail(deliveredMail) != null) {
            sender.getInventory().setItem(draftSlot, null);
            send(sender, "post_already_sent", "&7That draft was already accepted by the Aegis Courier.", Map.of());
            openSent(sender, 0);
            return;
        }
        String id = UUID.randomUUID().toString();
        String recipientName = recipient.getName() == null ? recipientId.toString() : recipient.getName();
        boolean saved;
        synchronized (data) {
            String base = "mail." + id;
            data.set(base + ".sender-uuid", sender.getUniqueId().toString());
            data.set(base + ".sender-name", sender.getName());
            data.set(base + ".recipient-uuid", recipientId.toString());
            data.set(base + ".recipient-name", recipientName);
            data.set(base + ".subject", subject(meta));
            data.set(base + ".pages", pages);
            data.set(base + ".created-at", now);
            data.set(base + ".read", false);
            data.set(base + ".reported", false);
            data.set(base + ".physical-delivered", false);
            data.set("delivered-drafts." + draftId, id);
            saved = saveAtomic();
            if (!saved) {
                data.set(base, null);
                data.set("delivered-drafts." + draftId, null);
            }
        }
        if (!saved) {
            send(sender, "post_save_failed", "&cThe courier could not safely store that letter. Your draft was kept.", Map.of());
            return;
        }
        sender.getInventory().setItem(draftSlot, null);
        lastSent.put(sender.getUniqueId(), now);
        sender.closeInventory();
        plugin.effects().playSound(sender, Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.8f, 1.25f);
        send(sender, "post_sent_success", "&6Whoosh! The Aegis Courier is delivering your sealed letter to &f{PLAYER}&6.",
                Map.of("PLAYER", recipientName));
        Player online = Bukkit.getPlayer(recipientId);
        if (online != null && online.isOnline()) {
            plugin.effects().playSound(online, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
            send(online, "post_received_notice", "&6You’ve got mail! &7Open Aegis Post to discover who sent it.", Map.of());
            deliverPhysical(online);
        }
    }

    private void read(Player player, String id) {
        Mail mail = mail(id);
        if (mail == null || (!mail.recipient().equals(player.getUniqueId()) && !mail.sender().equals(player.getUniqueId()))) {
            send(player, "post_letter_private", "&cThis sealed letter is not addressed to you.", Map.of()); return;
        }
        if (mail.recipient().equals(player.getUniqueId()) && !mail.read()) {
            synchronized (data) { data.set("mail." + id + ".read", true); saveAtomic(); }
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(truncate(mail.subject(), 32));
        meta.setAuthor(mail.senderName());
        List<String> pages = new ArrayList<>();
        pages.add("§6Aegis Post\n\n§0From: " + mail.senderName() + "\nTo: " + mail.recipientName()
                + "\n\n§8" + DATE.format(Instant.ofEpochMilli(mail.createdAt())));
        pages.addAll(mail.pages());
        meta.setPages(pages);
        book.setItemMeta(meta);
        player.closeInventory();
        plugin.runMain(player, () -> player.openBook(book));
    }

    private void report(Player player, String id) {
        Mail mail = mail(id);
        if (mail == null || !mail.recipient().equals(player.getUniqueId())) return;
        if (mail.reported()) { send(player, "post_already_reported", "&7This letter was already reported.", Map.of()); return; }
        synchronized (data) { data.set("mail." + id + ".reported", true); saveAtomic(); }
        if (plugin.publicBetaFeedback() != null) {
            String excerpt = truncate(String.join(" ", mail.pages()).replaceAll("\\s+", " "), 280);
            plugin.publicBetaFeedback().startOrSubmit(player, PublicBetaFeedbackService.Category.PROBLEM,
                    "Aegis Post letter reported. Mail ID: " + id + "; sender: " + mail.senderName()
                            + "; subject: " + mail.subject() + "; content: " + excerpt);
        }
        send(player, "post_reported", "&aThe letter was privately reported to Public Beta staff.", Map.of());
    }

    private void beginBlock(Player player) {
        pending.put(player.getUniqueId(), new PendingInput());
        player.closeInventory();
        send(player, "post_block_prompt", "&bType the player name to block, or &ccancel&b.", Map.of());
    }

    private void completeBlock(Player player, String value) {
        if (value == null || value.equalsIgnoreCase("cancel")) { send(player, "post_block_cancelled", "&7Blocking cancelled.", Map.of()); return; }
        OfflinePlayer target = findPlayer(value);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) {
            send(player, "post_player_not_found", "&cThat eligible Public Beta player was not found.", Map.of()); return;
        }
        Set<UUID> blocked = blocked(player.getUniqueId());
        blocked.add(target.getUniqueId());
        setBlocked(player.getUniqueId(), blocked);
        send(player, "post_player_blocked", "&a{PLAYER} can no longer send you Aegis Post.", Map.of("PLAYER", target.getName() == null ? value : target.getName()));
        openBlocked(player);
    }

    private void unblock(Player player, UUID id) {
        if (id == null) return;
        Set<UUID> blocked = blocked(player.getUniqueId());
        blocked.remove(id);
        setBlocked(player.getUniqueId(), blocked);
        send(player, "post_player_unblocked", "&aThat player may send you letters again.", Map.of());
        openBlocked(player);
    }

    private void deliverPhysical(Player player) {
        if (!plugin.getConfig().getBoolean(ROOT + ".physical-letters", true)) return;
        for (Mail mail : inbox(player.getUniqueId())) {
            if (mail.physicalDelivered()) continue;
            ItemStack letter = sealedLetter(player, mail);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(letter);
            if (!overflow.isEmpty()) return;
            synchronized (data) { data.set("mail." + mail.id() + ".physical-delivered", true); saveAtomic(); }
        }
    }

    private ItemStack sealedLetter(Player viewer, Mail mail) {
        ItemStack letter = new ItemStack(Material.PAPER);
        ItemMeta meta = letter.getItemMeta();
        meta.setDisplayName(color(tr(viewer, "post_sealed_letter_name", "&6Sealed Letter")));
        meta.setLore(colorList(trList(viewer, "post_sealed_letter_lore", List.of(
                "&7A private letter delivered by Aegis Courier.", "&eRight-click to break the seal and read."))));
        meta.getPersistentDataContainer().set(mailKey, PersistentDataType.STRING, mail.id());
        letter.setItemMeta(meta);
        return letter;
    }

    private List<Draft> drafts(Player player) {
        List<Draft> out = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = validDraft(player, slot);
            if (item == null) continue;
            BookMeta meta = (BookMeta) item.getItemMeta();
            if (safePages(meta).stream().anyMatch(page -> !page.isBlank())) out.add(new Draft(slot, subject(meta), safePages(meta)));
        }
        return out;
    }

    private ItemStack validDraft(Player player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getSize()) return null;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || (item.getType() != Material.WRITABLE_BOOK && item.getType() != Material.WRITTEN_BOOK)
                || !(item.getItemMeta() instanceof BookMeta meta)) return null;
        Byte tagged = meta.getPersistentDataContainer().get(draftKey, PersistentDataType.BYTE);
        return tagged != null && tagged == (byte) 1 ? item : null;
    }

    private List<OfflinePlayer> recipients(Player sender) {
        List<OfflinePlayer> out = new ArrayList<>();
        for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) if (isEligibleRecipient(sender, candidate)) out.add(candidate);
        for (Player online : Bukkit.getOnlinePlayers()) if (isEligibleRecipient(sender, online)
                && out.stream().noneMatch(existing -> existing.getUniqueId().equals(online.getUniqueId()))) out.add(online);
        out.sort(Comparator.comparing(candidate -> candidate.getName() == null ? "" : candidate.getName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private boolean isEligibleRecipient(Player sender, OfflinePlayer candidate) {
        return candidate != null && !candidate.getUniqueId().equals(sender.getUniqueId())
                && plugin.publicBeta().hasBetaPlayerRole(candidate.getUniqueId());
    }

    private OfflinePlayer findPlayer(String name) {
        if (name == null || name.isBlank()) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && plugin.publicBeta().hasBetaPlayerRole(online.getUniqueId())) return online;
        for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
            if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(name)
                    && plugin.publicBeta().hasBetaPlayerRole(candidate.getUniqueId())) return candidate;
        }
        return null;
    }

    private List<Mail> inbox(UUID player) { return mails().stream().filter(mail -> mail.recipient().equals(player)).toList(); }
    private List<Mail> sent(UUID player) { return mails().stream().filter(mail -> mail.sender().equals(player)).toList(); }

    private List<Mail> mails() {
        List<Mail> out = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("mail");
        if (section == null) return out;
        for (String id : section.getKeys(false)) { Mail mail = mail(id); if (mail != null) out.add(mail); }
        out.sort(Comparator.comparingLong(Mail::createdAt).reversed());
        return out;
    }

    private Mail mail(String id) {
        if (id == null || !data.isConfigurationSection("mail." + id)) return null;
        String base = "mail." + id;
        UUID sender = uuid(data.getString(base + ".sender-uuid"));
        UUID recipient = uuid(data.getString(base + ".recipient-uuid"));
        if (sender == null || recipient == null) return null;
        return new Mail(id, sender, data.getString(base + ".sender-name", "Unknown"), recipient,
                data.getString(base + ".recipient-name", "Unknown"), data.getString(base + ".subject", "Letter"),
                List.copyOf(data.getStringList(base + ".pages")), data.getLong(base + ".created-at", 0L),
                data.getBoolean(base + ".read", false), data.getBoolean(base + ".reported", false),
                data.getBoolean(base + ".physical-delivered", false));
    }

    private Set<UUID> blocked(UUID player) {
        Set<UUID> out = new HashSet<>();
        for (String raw : data.getStringList("blocked." + player)) { UUID id = uuid(raw); if (id != null) out.add(id); }
        return out;
    }

    private void setBlocked(UUID player, Set<UUID> blocked) {
        synchronized (data) { data.set("blocked." + player, blocked.stream().map(UUID::toString).sorted().toList()); saveAtomic(); }
    }

    private Inventory menu(PostHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title(title));
        ItemStack filler = GUIManager.getFiller();
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, filler);
        return inventory;
    }

    private void navigation(Player player, Inventory inventory, int page, int count, String returnAction) {
        if (page > 0) inventory.setItem(45, item(player, Material.ARROW, "post_previous_name", "&aPrevious Page",
                "post_previous_lore", List.of("&7View the previous page."), "page:" + (page - 1)));
        inventory.setItem(48, item(player, Material.ARROW, "public_beta_back_name", "&aBack",
                "public_beta_back_lore", List.of("&7Return to Aegis Post."), "main"));
        inventory.setItem(49, item(player, Material.BARRIER, "public_beta_exit_name", "&cExit",
                "public_beta_exit_lore", List.of("&7Close this menu."), "close"));
        if ((page + 1) * PAGE_SIZE < count) inventory.setItem(53, item(player, Material.ARROW, "post_next_name", "&aNext Page",
                "post_next_lore", List.of("&7View the next page."), "page:" + (page + 1)));
    }

    private ItemStack item(Player player, Material material, String nameKey, String fallbackName,
                           String loreKey, List<String> fallbackLore, String action) {
        ItemStack item = GUIManager.createItem(material, tr(player, nameKey, fallbackName), trList(player, loreKey, fallbackLore));
        if (action != null) tag(item, action);
        return item;
    }

    private void tag(ItemStack item, String action) { plugin.gui().tagAction(item, action); }

    private void replace(ItemStack item, Map<String, String> values) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String name = meta.getDisplayName();
        List<String> lore = meta.getLore();
        for (Map.Entry<String, String> value : values.entrySet()) {
            name = name.replace("{" + value.getKey() + "}", value.getValue());
            if (lore != null) for (int index = 0; index < lore.size(); index++)
                lore.set(index, lore.get(index).replace("{" + value.getKey() + "}", value.getValue()));
        }
        meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta);
    }

    private List<String> safePages(BookMeta meta) {
        List<String> pages = new ArrayList<>();
        if (meta == null) return pages;
        for (String page : meta.getPages()) {
            if (pages.size() >= MAX_PAGES) break;
            String stripped = ChatColor.stripColor(page == null ? "" : page);
            pages.add(truncate(stripped == null ? "" : stripped, MAX_PAGE_LENGTH));
        }
        return pages;
    }

    private String subject(BookMeta meta) {
        String title = meta == null ? null : meta.getTitle();
        if ((title == null || title.isBlank()) && meta != null) {
            title = safePages(meta).stream().filter(page -> !page.isBlank()).findFirst().orElse("Letter");
        }
        if (title == null || title.isBlank()) title = "Letter";
        return truncate(title, 48);
    }

    private boolean canUse(Player player) {
        if (canUseQuiet(player)) return true;
        if (player != null) send(player, "post_unavailable", "&cAegis Post is available only to Public Beta players.", Map.of());
        return false;
    }

    private boolean canUseQuiet(Player player) {
        return player != null && isEnabled() && plugin.publicBeta().hasBetaPlayerRole(player.getUniqueId());
    }

    private boolean saveAtomic() {
        if (!isolationValid) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
            data.set("instance-id", plugin.getConfig().getString("public-beta-mode.isolation.instance-id", "aegisguard-public-beta"));
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            data.save(temp);
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save Aegis Post data.", error);
            return false;
        }
    }

    private String tr(Player player, String key, String fallback) {
        if (plugin.codex() == null) return color(fallback);
        try { String value = plugin.codex().tr(player, key); return color(value == null || value.equals(key) ? fallback : value); }
        catch (Throwable ignored) { return color(fallback); }
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        if (plugin.codex() == null) return colorList(fallback);
        try { List<String> value = plugin.codex().trList(player, key); return value == null || value.isEmpty() ? colorList(fallback) : colorList(value); }
        catch (Throwable ignored) { return colorList(fallback); }
    }

    private void send(Player player, String key, String fallback, Map<String, String> values) {
        String message = tr(player, key, fallback);
        for (Map.Entry<String, String> value : values.entrySet()) message = message.replace("{" + value.getKey() + "}", value.getValue());
        player.sendMessage(message);
    }

    private List<String> colorList(List<String> lines) { return lines.stream().map(PublicBetaPostService::color).toList(); }
    private static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
    private static String title(String value) { String colored = color(value); return colored.length() <= 32 ? colored : colored.substring(0, 32); }
    private static String truncate(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
    private static int integer(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static UUID uuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }

    private enum Screen { MAIN, DRAFTS, RECIPIENTS, CONFIRM, INBOX, SENT, BLOCKED }
    private record PendingInput() { }
    private record Draft(int slot, String subject, List<String> pages) { }
    private record Mail(String id, UUID sender, String senderName, UUID recipient, String recipientName,
                        String subject, List<String> pages, long createdAt, boolean read, boolean reported,
                        boolean physicalDelivered) { }
    private record PostHolder(Screen screen, int page, String mailId, int draftSlot, UUID recipient) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
