package com.aegisguard.groups;

import com.aegisguard.AegisGuard;
import com.aegisguard.commands.CommandBase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * /ag party — open-ended player groups with the sidebar roster. Form a party
 * for a dungeon run, a build session, or just exploring; the sidebar shows the
 * crew either way. Arena-run parties appear on the sidebar automatically.
 */
public final class PartyCommands extends CommandBase {

    private final java.util.Map<java.util.UUID, Long> lastMapAt = new java.util.concurrent.ConcurrentHashMap<>();

    public PartyCommands(AegisGuard plugin) {
        super(plugin);
    }

    public void handleParty(Player p, String[] args) {
        PartyService parties = plugin.parties();
        if (parties == null || !parties.enabled()) {
            sendKey(p, "party_disabled", "&cParties are disabled on this server.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "info";
        switch (sub) {
            case "create" -> {
                String name = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : null;
                Party party = parties.createParty(p, name);
                if (party == null) {
                    sendKey(p, "party_create_failed", "&cCould not create a party right now.");
                } else {
                    sendKey(p, "party_created", "&aParty formed: &f" + party.getName() + "&a. Invite friends with &f/ag party invite <player>&a.");
                }
            }
            case "invite" -> {
                if (args.length < 3) { sendKey(p, "party_invite_usage", "&eUsage: /ag party invite <player>"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null || !target.isOnline()) {
                    sendKey(p, "party_invite_offline", "&cThat player is not online.");
                    return;
                }
                Party party = parties.partyOf(p.getUniqueId());
                if (party != null && !party.isLeader(p.getUniqueId())) {
                    sendKey(p, "party_not_leader", "&cOnly the party leader can invite.");
                    return;
                }
                if (parties.invite(p, target)) {
                    sendKey(p, "party_invited", "&aInvited &f" + target.getName() + "&a to your party.");
                    Player t = target;
                    plugin.scheduler().runEntity(t, () ->
                                    sendKey(t, "party_invite_received",
                                            "&e" + p.getName() + " invited you to their party. &f/ag party accept&e to join."),
                            () -> { });
                } else {
                    sendKey(p, "party_invite_failed", "&cCould not invite that player (party full, already grouped, or cooling down).");
                }
            }
            case "accept", "join" -> {
                PartyService.JoinResult result = parties.join(p);
                switch (result) {
                    case JOINED -> {
                        Party party = parties.partyOf(p.getUniqueId());
                        sendKey(p, "party_joined", "&aYou joined &f" + (party == null ? "the party" : party.getName()) + "&a.");
                        for (Player member : parties.onlineMembers(p.getUniqueId())) {
                            if (!member.equals(p)) sendKey(member, "party_member_joined",
                                    "&a" + p.getName() + " joined the party.");
                        }
                    }
                    case PARTY_FULL -> sendKey(p, "party_full", "&cThat party is full.");
                    case ALREADY_IN_PARTY -> sendKey(p, "party_already_in", "&cYou're already in a party — &f/ag party leave &cfirst.");
                    default -> sendKey(p, "party_no_invite", "&cNo pending party invite.");
                }
            }
            case "decline" -> {
                if (parties.decline(p)) sendKey(p, "party_declined", "&7Invite declined.");
                else sendKey(p, "party_no_invite", "&cNo pending party invite.");
            }
            case "leave" -> {
                Party party = parties.removeMember(p.getUniqueId());
                if (party == null) {
                    sendKey(p, "party_not_in", "&cYou're not in a party.");
                } else {
                    sendKey(p, "party_left", "&7You left the party.");
                    for (Player member : parties.onlineMembers(p.getUniqueId())) {
                        sendKey(member, "party_member_left", "&7" + p.getName() + " left the party.");
                    }
                }
            }
            case "kick" -> {
                if (args.length < 3) { sendKey(p, "party_kick_usage", "&eUsage: /ag party kick <player>"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                UUID targetId = target != null ? target.getUniqueId() : null;
                if (targetId == null) {
                    sendKey(p, "party_kick_offline", "&cThat player is not online.");
                    return;
                }
                if (parties.kick(p, targetId)) {
                    sendKey(p, "party_kicked", "&eRemoved &f" + target.getName() + "&e from the party.");
                    Player t = target;
                    plugin.scheduler().runEntity(t, () ->
                                    sendKey(t, "party_kick_received", "&cYou were removed from the party."),
                            () -> { });
                } else {
                    sendKey(p, "party_kick_failed", "&cOnly the leader can kick members.");
                }
            }
            case "promote", "leader" -> {
                if (args.length < 3) { sendKey(p, "party_promote_usage", "&eUsage: /ag party promote <player>"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sendKey(p, "party_kick_offline", "&cThat player is not online.");
                    return;
                }
                if (parties.promote(p, target.getUniqueId())) {
                    sendKey(p, "party_promoted", "&6★ &e" + target.getName() + " now leads the party.");
                    Player t = target;
                    plugin.scheduler().runEntity(t, () ->
                                    sendKey(t, "party_promoted_you", "&6★ You now lead the party."),
                            () -> { });
                } else {
                    sendKey(p, "party_kick_failed", "&cOnly the leader can promote members.");
                }
            }
            case "rally" -> {
                if (!plugin.getConfig().getBoolean("party.rally_enabled", true)) {
                    sendKey(p, "party_rally_disabled", "&cParty rallies are disabled on this server.");
                    return;
                }
                Party party = parties.partyOf(p.getUniqueId());
                if (party == null) {
                    sendKey(p, "party_not_in", "&cYou're not in a party.");
                    return;
                }
                if (party.isLeader(p.getUniqueId())) {
                    // Leader plants the point at their feet.
                    if (parties.plantRally(p) == null) {
                        sendKey(p, "party_kick_failed", "&cOnly the leader can do that.");
                        return;
                    }
                    sendKey(p, "party_rally_set", "&6⚑ Rally point set. &fMembers: /ag party rally &6to join you.");
                    for (Player member : parties.onlineMembers(p.getUniqueId())) {
                        if (!member.equals(p)) sendKey(member, "party_rally_called",
                                "&6⚑ " + p.getName() + " called a rally — &f/ag party rally &6to join them.");
                    }
                } else {
                    // Member answers the call — self-consenting teleport.
                    org.bukkit.Location rally = parties.rallyPointFor(p.getUniqueId());
                    if (rally == null) {
                        sendKey(p, "party_rally_none", "&cNo active rally — ask the leader to run &f/ag party rally&c.");
                        return;
                    }
                    sendKey(p, "party_rally_going", "&aMoving to the rally point…");
                    plugin.scheduler().runEntity(p, () -> {
                        if (p.isOnline()) p.teleportAsync(rally);
                    }, () -> { });
                }
            }
            case "track" -> {
                if (!parties.trackingEnabled()) {
                    sendKey(p, "party_track_disabled", "&cParty tracking is disabled on this server.");
                    return;
                }
                boolean on = parties.toggleTrack(p.getUniqueId());
                sendKey(p, on ? "party_track_on" : "party_track_off",
                        on ? "&aParty tracker on — member directions show above your hotbar."
                           : "&eParty tracker off.");
            }
            case "hide" -> {
                boolean hidden = parties.toggleHide(p.getUniqueId());
                sendKey(p, hidden ? "party_hide_on" : "party_hide_off",
                        hidden ? "&eYou're now off the party map — members can't see your position."
                               : "&aBack on the party map — members can see you again.");
            }
            case "ping" -> {
                if (!parties.trackingEnabled()
                        || !plugin.getConfig().getBoolean("party.ping_enabled", true)) {
                    sendKey(p, "party_ping_disabled", "&cParty pings are disabled on this server.");
                    return;
                }
                if (parties.partyOf(p.getUniqueId()) == null) {
                    sendKey(p, "party_not_in", "&cYou're not in a party.");
                    return;
                }
                if (parties.plantPing(p) == null) {
                    sendKey(p, "party_ping_wait", "&eGive it a few seconds between pings.");
                    return;
                }
                for (Player member : parties.onlineMembers(p.getUniqueId())) {
                    sendKey(member, "party_ping_marked",
                            "&d◎ " + p.getName() + " marked a spot — watch your tracker & party map.");
                }
            }
            case "map" -> {
                if (!parties.trackingEnabled()
                        || !plugin.getConfig().getBoolean("party.map_enabled", true)) {
                    sendKey(p, "party_map_disabled", "&cThe party map is disabled on this server.");
                    return;
                }
                if (parties.partyOf(p.getUniqueId()) == null) {
                    sendKey(p, "party_not_in", "&cYou're not in a party.");
                    return;
                }
                long now = System.currentTimeMillis();
                Long lastMap = lastMapAt.get(p.getUniqueId());
                if (lastMap != null && now - lastMap < 10_000L) {
                    sendKey(p, "party_map_wait", "&eParty map already handed out — give it a few seconds.");
                    return;
                }
                lastMapAt.put(p.getUniqueId(), now);
                givePartyMap(p);
                sendKey(p, "party_map_given",
                        "&aParty map ready — your crew shows as head icons; pings are flagged.");
            }
            case "hud", "sidebar" -> {
                if (plugin.partySidebar() == null) {
                    sendKey(p, "party_disabled", "&cParties are disabled on this server.");
                    return;
                }
                if (args.length >= 3) {
                    String mode = args[2].toLowerCase(java.util.Locale.ROOT);
                    if (mode.equals("compact") || mode.equals("full")) {
                        boolean on = plugin.partySidebar().setCompact(p, mode.equals("compact"));
                        sendKey(p, on ? "party_hud_compact" : "party_hud_full",
                                on ? "&aSidebar: compact (names only)." : "&aSidebar: full detail (health + distance).");
                        return;
                    }
                    sendKey(p, "party_hud_usage", "&eUsage: /ag party hud [compact|full]");
                    return;
                }
                boolean visible = plugin.partySidebar().toggle(p);
                sendKey(p, visible ? "party_hud_on" : "party_hud_off",
                        visible ? "&aParty sidebar shown." : "&7Party sidebar hidden.");
            }
            case "disband" -> {
                Party party = parties.partyOf(p.getUniqueId());
                if (party == null || !party.isLeader(p.getUniqueId())) {
                    sendKey(p, "party_not_leader", "&cOnly the party leader can do that.");
                    return;
                }
                for (UUID memberId : party.getMembers()) {
                    parties.removeMember(memberId);
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && !member.equals(p)) {
                        sendKey(member, "party_disbanded", "&7The party was disbanded.");
                    }
                }
                sendKey(p, "party_disbanded", "&7The party was disbanded.");
            }
            case "info", "list" -> showInfo(p, parties);
            default -> sendKey(p, "party_usage",
                    "&e/ag party &7— create, invite, accept, leave, kick, promote, rally, ping, map, track, hide, hud");
        }
    }

    private void showInfo(Player p, PartyService parties) {
        PartyService.PartyView view = parties.viewFor(p.getUniqueId());
        if (view == null) {
            sendKey(p, "party_info_none",
                    "&7No party. &f/ag party create&7 to start one, or accept an invite.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (UUID id : view.memberIds()) {
            Player member = Bukkit.getPlayer(id);
            String name = member != null ? member.getName() : Bukkit.getOfflinePlayer(id).getName();
            boolean leader = id.equals(view.leaderId());
            boolean online = member != null && member.isOnline();
            if (sb.length() > 0) sb.append("&8, ");
            sb.append(leader ? "&6★ " : online ? "&f" : "&7");
            sb.append(name == null ? "?" : name);
        }
        sendKey(p, "party_info", "&6" + view.title() + " &8— &f{MEMBERS}",
                Map.of("MEMBERS", sb.toString()));
    }

    /**
     * Hands the player a party radar map centered on where they stand. The custom
     * renderer paints member head icons, the player's own marker, and the party ping.
     */
    private void givePartyMap(Player p) {
        org.bukkit.map.MapView view = Bukkit.createMap(p.getWorld());
        view.setScale(org.bukkit.map.MapView.Scale.FAR);
        view.setCenterX(p.getLocation().getBlockX());
        view.setCenterZ(p.getLocation().getBlockZ());
        view.addRenderer(new PartyMapRenderer(plugin, parties));
        org.bukkit.inventory.ItemStack item =
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta meta) {
            meta.setMapView(view);
            meta.setDisplayName("§e⚑ Party Map");
            meta.setLore(java.util.List.of(
                    "§7Your crew shows as head icons.",
                    "§7Pings are flagged in red.",
                    "§8/ag party hide §7— go off the map"));
            item.setItemMeta(meta);
        }
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover =
                p.getInventory().addItem(item);
        leftover.values().forEach(extra ->
                p.getWorld().dropItemNaturally(p.getLocation(), extra));
    }
}
