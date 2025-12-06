package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

/**
 * Central dispatcher for all AegisGuard commands.
 *
 * Root label (aegisguard / aegis / ag) is handled by this class,
 * and the first argument is used to route to a SubCommand implementation:
 *
 *   /aegis consume ...
 *   /aegis guild ...
 *   /aegis claim ...
 *   /aegis admin ...
 */
public class CommandHandler implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public CommandHandler(AegisGuard plugin) {
        this.plugin = plugin;

        // --- 1. GENERAL COMMANDS (AegisCommand) ---
        AegisCommand generalCmd = new AegisCommand(plugin);
        // You can register more general aliases here as needed
        register("consume", generalCmd);

        // --- 2. GUILD COMMANDS (GuildCommand) ---
        GuildCommand guildCmd = new GuildCommand(plugin);
        register("guild", guildCmd);
        register("alliance", guildCmd);

        // --- 3. ESTATE COMMANDS (Player Plots) ---
        EstateCommand estateCmd = new EstateCommand(plugin);
        register("claim", estateCmd);
        register("deed", estateCmd);
        register("unclaim", estateCmd);
        register("vacate", estateCmd);
        register("invite", estateCmd);
        register("trust", estateCmd);
        register("setrole", estateCmd);
        register("resize", estateCmd);
        register("wand", estateCmd); // player wand command

        // --- 4. ADMIN COMMANDS (Server Estates & Maintenance) ---
        AdminCommand adminCmd = new AdminCommand(plugin);
        register("admin", adminCmd);
        register("reload", adminCmd);
        register("server", adminCmd); // alias for creating server plots
    }

    /**
     * Register an alias for a SubCommand implementation.
     *
     * Example:
     *   register("claim", estateCommand);
     */
    public void register(String alias, SubCommand command) {
        if (alias == null || command == null) return;
        subCommands.put(alias.toLowerCase(Locale.ROOT), command);
    }

    // ------------------------------------------------------------------------
    // CommandExecutor
    // ------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (args.length == 0) {
            // No subcommand provided -> route to a default handler, usually help
            SubCommand root = subCommands.get("help");
            if (root != null) {
                return root.execute(sender, label, "help", new String[0]);
            }

            sender.sendMessage("§cUnknown usage. Try /" + label + " help");
            return true;
        }

        String subLabel = args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = subCommands.get(subLabel);

        if (sub == null) {
            sender.sendMessage("§cUnknown subcommand: §f" + subLabel);
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, label, subLabel, subArgs);
    }

    // ------------------------------------------------------------------------
    // TabCompleter (optional but handy)
    // ------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        // First arg: suggest subcommand names
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String key : subCommands.keySet()) {
                if (key.startsWith(prefix)) {
                    result.add(key);
                }
            }
            Collections.sort(result);
            return result;
        }

        // Delegate deeper tab completion to the subcommand
        String subLabel = args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = subCommands.get(subLabel);
        if (sub == null) {
            return Collections.emptyList();
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        List<String> suggestions = sub.tabComplete(sender, alias, subLabel, subArgs);
        return suggestions != null ? suggestions : Collections.emptyList();
    }

    // ------------------------------------------------------------------------
    // Nested SubCommand interface
    // ------------------------------------------------------------------------

    /**
     * All sub-command classes (AegisCommand, GuildCommand, EstateCommand, AdminCommand)
     * should implement this interface:
     *
     *   public class EstateCommand implements CommandHandler.SubCommand { ... }
     */
    public interface SubCommand {

        /**
         * Execute this subcommand.
         *
         * @param sender    The command sender
         * @param label     Root command label (e.g., "aegis")
         * @param subLabel  Subcommand label (e.g., "claim")
         * @param args      Remaining arguments after the subcommand
         * @return true if handled, false to show default usage
         */
        boolean execute(CommandSender sender, String label, String subLabel, String[] args);

        /**
         * Tab completion for this subcommand.
         */
        default List<String> tabComplete(CommandSender sender,
                                         String label,
                                         String subLabel,
                                         String[] args) {
            return Collections.emptyList();
        }
    }
}
