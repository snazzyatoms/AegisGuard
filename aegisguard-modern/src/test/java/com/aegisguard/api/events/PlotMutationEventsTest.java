package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.guestpass.GuestPassPreset;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The API change-events must exist and be cancellable, and Plot mutations must
 * keep working in a plain unit-test JVM (no Bukkit server). The last group is a
 * regression guard for the {@code Bukkit.getServer() == null} check inside
 * {@code Plot}: without it these calls would throw off-server.
 */
class PlotMutationEventsTest {

    private static Plot plot() {
        return new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
    }

    @Test
    void flagEventCarriesPlotFlagAndValues() {
        Plot plot = plot();
        PlotFlagChangeEvent event = new PlotFlagChangeEvent(plot, "pvp", false, true);
        assertSame(plot, event.getPlot());
        assertEquals("pvp", event.getFlag());
        assertFalse(event.getOldValue());
        assertTrue(event.getNewValue());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        event.setNewValue(false);
        assertFalse(event.getNewValue());
        assertNotNull(PlotFlagChangeEvent.getHandlerList());
    }

    @Test
    void roleEventExposesTargetAndRoles() {
        Plot plot = plot();
        UUID target = UUID.randomUUID();
        PlotRoleChangeEvent grant = new PlotRoleChangeEvent(plot, target, null, "farmer");
        assertSame(plot, grant.getPlot());
        assertEquals(target, grant.getTarget());
        assertNull(grant.getOldRole());
        assertEquals("farmer", grant.getNewRole());
        assertFalse(grant.isRemoval());

        PlotRoleChangeEvent removal = new PlotRoleChangeEvent(plot, target, "farmer", null);
        assertTrue(removal.isRemoval());
        assertNotNull(PlotRoleChangeEvent.getHandlerList());
    }

    @Test
    void guestPassEventsWrapThePass() {
        Plot plot = plot();
        GuestPass pass = GuestPass.issue(UUID.randomUUID(), "Guest",
                GuestPassPreset.VISITOR, plot.getOwner(), "Owner", 60_000L);

        GuestPassGrantEvent grant = new GuestPassGrantEvent(plot, pass);
        assertSame(plot, grant.getPlot());
        assertSame(pass, grant.getPass());
        grant.setCancelled(true);
        assertTrue(grant.isCancelled());

        GuestPassRevokeEvent revoke = new GuestPassRevokeEvent(plot, pass);
        assertSame(plot, revoke.getPlot());
        assertSame(pass, revoke.getPass());
        assertFalse(revoke instanceof org.bukkit.event.Cancellable);
        assertNotNull(GuestPassRevokeEvent.getHandlerList());
    }

    @Test
    void mutationsStillWorkWithoutServer() {
        Plot plot = plot();
        UUID member = UUID.randomUUID();

        plot.setFlag("pvp", true);
        assertTrue(plot.getFlag("pvp", false));

        assertTrue(plot.setRole(member, "farmer", false));
        assertEquals("farmer", plot.getRole(member));
        assertTrue(plot.removeRole(member, false));
        assertEquals("visitor", plot.getRole(member));

        GuestPass pass = GuestPass.issue(member, "Guest",
                GuestPassPreset.VISITOR, plot.getOwner(), "Owner", 60_000L);
        plot.addGuestPass(pass);
        assertNotNull(plot.getGuestPass(member));
        assertTrue(plot.revokeGuestPass(member));
        assertNull(plot.getGuestPass(member));
    }

    @Test
    void suppressionFlagIsControllable() {
        Plot plot = plot();
        assertFalse(plot.isApiEventsSuppressed());
        plot.setApiEventsSuppressed(true);
        assertTrue(plot.isApiEventsSuppressed());
        plot.setApiEventsSuppressed(false);
        assertFalse(plot.isApiEventsSuppressed());
    }
}
