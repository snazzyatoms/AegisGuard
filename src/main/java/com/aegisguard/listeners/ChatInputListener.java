package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles "GUI-to-Chat" input.
 * Allows players to rename estates/guilds by typing in chat
 * after clicking a button.
 */
public class ChatInputListener implements Listener {

    private final AegisGuard plugin;
    // Store who we are waiting for, and what code to run when they talk
    private final Map<UUID, InputAction> pendingInputs = new HashMap<>();

    public ChatInputListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Request input from a player.
     * @param player The player to ask.
     * @param prompt The message to show (Title/Subtitle).
     * @param callback The code to run when they type.
     */
    public void requestInput(Player player, String prompt, Consumer<String> callback) {
        UUID uuid = player.getUniqueId();
        
        // 1. Close inventory so they can type
        player.closeInventory();
        
        // 2. Send Instructions
        player.sendTitle(
            ChatColor.AQUA + "Type Input", 
            ChatColor.GRAY + prompt, 
            10, 100, 20
        );
        player.sendMessage(ChatColor.GREEN + "✍ " + prompt);
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.RED + "cancel" + ChatColor.GRAY + " to abort.");

        // 3. Register the callback
        pendingInputs.put(uuid, new InputAction(callback));

        // 4. Timeout Safety (Remove listener after 30 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingInputs.containsKey(uuid)) {
                    pendingInputs.remove(uuid);
                    player.sendMessage(ChatColor.RED + "✖ Input timed out.");
                }
            }
        }.runTaskLater(plugin, 600L); // 30 Seconds
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingInputs.containsKey(player.getUniqueId())) return;

        event.setCancelled(true); // Stop message from going to global chat
        
        String input = event.getMessage();
        InputAction action = pendingInputs.remove(player.getUniqueId());

        // Handle Cancel
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.RED + "✖ Operation cancelled.");
            player.sendTitle("", "", 0, 1, 0); // Clear title
            return;
        }

        // Run the Logic (Back on main thread to be safe)
        plugin.runMain(player, () -> action.callback.accept(input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    // Wrapper class
    private static class InputAction {
        final Consumer<String> callback;
        InputAction(Consumer<String> callback) { this.callback = callback; }
    }
}
