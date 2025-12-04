package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SoundCommand implements CommandExecutor {

    private final AegisGuard plugin;

    public SoundCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        boolean current = plugin.isSoundEnabled(p);
        plugin.getConfig().set("sounds.players." + p.getUniqueId(), !current);
        plugin.saveConfig();

        String msg = !current ? "&aSounds Enabled" : "&cSounds Disabled";
        // FIXED: Use getLanguageManager().getMsg()
        p.sendMessage(plugin.getLanguageManager().getMsg(p, "sound_toggle") + " " + msg);
        
        return true;
    }
}
