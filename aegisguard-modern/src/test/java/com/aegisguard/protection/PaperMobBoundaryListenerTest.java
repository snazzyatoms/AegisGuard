package com.aegisguard.protection;

import com.aegisguard.data.Plot;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperMobBoundaryListenerTest {

    private final Plot plot = new Plot(
            UUID.randomUUID(), UUID.randomUUID(), "owner", "world", 0, 0, 9, 9
    );

    @Test
    void calculatesRetreatDirectionForEveryBorder() {
        assertDirection(-1.0D, 0.0D, -0.1D, 5.0D);
        assertDirection(1.0D, 0.0D, 10.0D, 5.0D);
        assertDirection(0.0D, -1.0D, 5.0D, -0.1D);
        assertDirection(0.0D, 1.0D, 5.0D, 10.0D);

        Vector corner = PaperMobBoundaryListener.calculateOutwardDirection(plot, -0.1D, -0.1D);
        assertTrue(corner.getX() < 0.0D);
        assertTrue(corner.getZ() < 0.0D);
        assertEquals(1.0D, corner.length(), 0.000_001D);
    }

    @Test
    void boundaryResponseStopsPathAndMovesMobOutward() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/aegisguard/protection/PaperMobBoundaryListener.java"
        ));

        assertTrue(source.contains("stopPathfinding()"));
        assertTrue(source.contains("event.setTo(retreat)"));
        assertTrue(source.contains("entity.setVelocity(velocity)"));
    }

    private void assertDirection(double expectedX, double expectedZ, double x, double z) {
        Vector direction = PaperMobBoundaryListener.calculateOutwardDirection(plot, x, z);
        assertEquals(expectedX, direction.getX(), 0.000_001D);
        assertEquals(expectedZ, direction.getZ(), 0.000_001D);
    }
}
