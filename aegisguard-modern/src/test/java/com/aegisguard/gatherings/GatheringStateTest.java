package com.aegisguard.gatherings;

import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.guestpass.GuestPassPreset;
import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class GatheringStateTest {

    @Test
    void onlyTheOwnerCoOwnerOrStewardMayHost() {
        UUID owner = UUID.randomUUID();
        UUID coOwner = UUID.randomUUID();
        UUID steward = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        plot.setRole(coOwner, "co_owner");
        plot.setRole(steward, "steward");
        plot.setRole(member, "member");
        assertTrue(GatheringService.hostHasRole(plot, owner));
        assertTrue(GatheringService.hostHasRole(plot, coOwner));
        assertTrue(GatheringService.hostHasRole(plot, steward));
        assertFalse(GatheringService.hostHasRole(plot, member));
        assertFalse(GatheringService.hostHasRole(plot, UUID.randomUUID()));
        plot.removeRole(steward, true);
        assertFalse(GatheringService.hostHasRole(plot, steward),
                "Revoking the host role must remove the live listing and prevent new passes");
    }

    @Test
    void storeRoundTripsExpiryAndIssuedPassIdentity(@TempDir Path temp) {
        UUID plotId = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Gathering original = new Gathering(plotId, UUID.randomUUID(), "Host", "Plot",
                now, now + 60_000, true);
        original.recordIssued(visitor, now + 1);
        var file = temp.resolve("gatherings.yml").toFile();
        GatheringStore writer = new GatheringStore(file, Logger.getAnonymousLogger());
        writer.put(original);
        assertTrue(writer.save());

        GatheringStore reader = new GatheringStore(file, Logger.getAnonymousLogger());
        reader.load();
        Gathering restored = reader.get(plotId);
        assertNotNull(restored);
        assertTrue(restored.isLive(now));
        assertEquals(original.endsAt(), restored.endsAt());
        assertEquals(now + 1, restored.issuedPasses().get(visitor));
        reader.remove(plotId);
        assertTrue(reader.save());
        GatheringStore afterStop = new GatheringStore(file, Logger.getAnonymousLogger());
        afterStop.load();
        assertNull(afterStop.get(plotId));
    }

    @Test
    void expiredListingStaysExpiredAfterRestart(@TempDir Path temp) {
        long now = System.currentTimeMillis();
        UUID plotId = UUID.randomUUID();
        var file = temp.resolve("gatherings.yml").toFile();
        GatheringStore writer = new GatheringStore(file, Logger.getAnonymousLogger());
        writer.put(new Gathering(plotId, UUID.randomUUID(), "Host", "Plot",
                now - 120_000, now - 60_000, true));
        assertTrue(writer.save());
        GatheringStore reader = new GatheringStore(file, Logger.getAnonymousLogger());
        reader.load();
        assertNotNull(reader.get(plotId));
        assertFalse(reader.get(plotId).isLive(now));
    }

    @Test
    void onlyAPositiveFutureEndIsLive() {
        long now = System.currentTimeMillis();
        Gathering expired = new Gathering(UUID.randomUUID(), UUID.randomUUID(), "Host", "Plot", now - 10, 0, true);
        assertFalse(expired.isLive(now), "Corrupt zero expiry must not create a permanent listing");
        Gathering live = new Gathering(UUID.randomUUID(), UUID.randomUUID(), "Host", "Plot", now, now + 60_000, true);
        assertTrue(live.isLive(now));
        assertFalse(live.isLive(now + 60_000));
    }

    @Test
    void endingHouseMatchesOnlyThePassItIssued() {
        UUID visitor = UUID.randomUUID();
        UUID host = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Gathering gathering = new Gathering(UUID.randomUUID(), host, "Host", "Plot", now, now + 60_000, true);
        GuestPass issued = GuestPass.issue(visitor, "Visitor", GuestPassPreset.VISITOR, host, "Host", 60_000);
        gathering.recordIssued(visitor, issued.getIssuedAt());
        assertTrue(gathering.issuedByThisGathering(issued));
        GuestPass renewed = gathering.renewedPass(issued, now + 1000, now + 120_000);
        assertNotNull(renewed);
        assertEquals(now + 120_000, renewed.getExpiresAt());
        assertEquals(issued.getPermissions(), renewed.getPermissions());

        GuestPass replacement = new GuestPass(visitor, "Visitor", GuestPassPreset.VISITOR,
                GuestPassPreset.VISITOR.getPermissions(), host, "Host", issued.getIssuedAt() + 1,
                issued.getExpiresAt() + 60_000);
        assertFalse(gathering.issuedByThisGathering(replacement),
                "A later manually issued visitor pass must survive gathering expiry");
        assertNull(gathering.renewedPass(replacement, now + 1000, now + 120_000));
        assertFalse(gathering.issuedByThisGathering(GuestPass.issue(UUID.randomUUID(), "Other",
                GuestPassPreset.VISITOR, host, "Host", 60_000)));
    }
}
