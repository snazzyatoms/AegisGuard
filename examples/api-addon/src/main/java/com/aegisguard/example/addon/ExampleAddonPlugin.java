package com.aegisguard.example.addon;

import com.aegisguard.api.AegisGuardAPI;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Official AegisGuard add-on example.
 *
 * <p>This plugin shows how to:
 * <ul>
 *     <li>Look up the public {@link AegisGuardAPI} service.</li>
 *     <li>Listen to AegisGuard plot enter/leave events.</li>
 *     <li>Read basic claim information safely.</li>
 * </ul>
 *
 * <p>Drop the AegisGuard dev-api JAR on the build path, then compile this
 * add-on and install it alongside AegisGuard on your server.</p>
 */
public final class ExampleAddonPlugin extends JavaPlugin implements Listener {

    private AegisGuardAPI aegis;

    @Override
    public void onEnable() {
        if (!hookAegisGuard()) {
            getLogger().warning("AegisGuard was not found; example add-on will do nothing.");
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AegisGuard example add-on enabled.");
    }

    private boolean hookAegisGuard() {
        RegisteredServiceProvider<AegisGuardAPI> provider =
                Bukkit.getServicesManager().getRegistration(AegisGuardAPI.class);
        if (provider == null) {
            return false;
        }
        this.aegis = provider.getProvider();
        return this.aegis != null;
    }

    @EventHandler
    public void onPlotEnter(PlotEnterEvent event) {
        // Example: announce when a player enters any claim.
        if (aegis == null) return;
        event.getPlayer().sendMessage("Welcome to " + event.getPlot().getPlotName() + "!");
    }

    @EventHandler
    public void onPlotLeave(PlotLeaveEvent event) {
        // Example: simple farewell message.
        if (aegis == null) return;
        event.getPlayer().sendMessage("You left " + event.getPlot().getPlotName() + ".");
    }
}
