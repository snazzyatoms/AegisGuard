package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.objects.Guild;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AllianceManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Guild> guilds = new HashMap<>(); // GuildID -> Guild
    private final Map<UUID, UUID> playerGuildMap = new HashMap<>(); // PlayerID -> GuildID (Fast lookup)

    public AllianceManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a new Guild.
     */
    public Guild createGuild(Player leader, String name) {
        if (isInGuild(leader.getUniqueId())) return null; // Already in one!
        if (nameExists(name)) return null; // Name taken!

        UUID id = UUID.randomUUID();
        Guild guild = new Guild(id, name, leader.getUniqueId());
        
        // Register
        guilds.put(id, guild);
        playerGuildMap.put(leader.getUniqueId(), id);
        
        // TODO: Save to Database
        return guild;
    }

    /**
     * Disband a Guild (The Split Logic).
     */
    public void disbandGuild(Guild guild) {
        // 1. Handle Treasury (Split money among remaining members?)
        double balance = guild.getBalance();
        Set<UUID> remainingMembers = guild.getMembers();
        
        if (balance > 0 && !remainingMembers.isEmpty()) {
            double splitShare = balance / remainingMembers.size();
            // TODO: Distribute 'splitShare' to each member's Vault balance here.
        }

        // 2. Remove all members from lookup cache
        for (UUID memberId : remainingMembers) {
            playerGuildMap.remove(memberId);
        }

        // 3. Delete Guild
        guilds.remove(guild.getId());
        // TODO: Delete from Database
    }

    public Guild getGuild(UUID guildId) {
        return guilds.get(guildId);
    }

    /**
     * Fast lookup: Get the guild a player belongs to.
     */
    public Guild getPlayerGuild(UUID playerId) {
        UUID guildId = playerGuildMap.get(playerId);
        if (guildId == null) return null;
        return guilds.get(guildId);
    }

    public boolean isInGuild(UUID playerId) {
        return playerGuildMap.containsKey(playerId);
    }
    
    public boolean nameExists(String name) {
        return guilds.values().stream().anyMatch(g -> g.getName().equalsIgnoreCase(name));
    }

    // ==========================================================
    // 🛡️ SECURITY & FREEZE LOGIC
    // ==========================================================
    
    /**
     * Checks if a Guild's bank should be frozen.
     * Logic: If members < min_required, freeze it.
     */
    public boolean isTreasuryFrozen(Guild guild) {
        int minMembers = plugin.getConfig().getInt("guilds.security.min_members_required", 2);
        
        // If they have fewer members than required, LOCK THE BANK.
        return guild.getMemberCount() < minMembers;
    }
}
