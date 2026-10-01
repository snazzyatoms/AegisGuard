package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the dispenser edge-bypass fix: a dispenser outside a claim facing inward must not be
 * able to place a liquid source block inside the protected region (BlockFromToEvent only covers
 * flow *after* the source lands).
 */
class DispenserProtectionContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void dispenseHandlerExistsAndCancels() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(src.contains("BlockDispenseEvent"), "must handle BlockDispenseEvent");
        assertTrue(src.contains("onBlockDispense"), "dispense handler must exist");
        assertTrue(src.contains("setCancelled(true)"), "handler must be able to cancel");
    }

    @Test
    void dispenseGatedUnderLiquidFlowWard() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(src.contains("liquidFlowProtection()"),
                "dispense check must honor the protections.liquid_flow master switch");
        assertTrue(src.contains("\"liquid-flow\""),
                "dispense check must reuse the liquid-flow plot flag");
        assertTrue(src.contains("getPlotId()"),
                "dispense must compare plot ids so same-claim dispensers keep working");
        assertTrue(src.contains("getFacing()"),
                "dispense must resolve the dispenser's facing target block");
    }

    @Test
    void dispenseOnlyBlocksLiquidBuckets() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(src.contains("isLiquidBucket"), "must classify liquid bucket items");
        assertTrue(src.contains("WATER_BUCKET"), "water buckets must be covered");
        assertTrue(src.contains("LAVA_BUCKET"), "lava buckets must be covered");
    }
}
