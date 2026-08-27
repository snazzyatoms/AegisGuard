package com.aegisguard;

import com.aegisguard.data.IDataStore;
import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contracts for the 1.3.5 soak fixes that span service and GUI boundaries. */
class RecoveryRegressionContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void stablePlotLookupFindsTransferredPlotRegardlessOfSnapshotOwner() {
        UUID plotId = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        Plot transferred = new Plot(plotId, newOwner, "NewOwner", "world", 0, 0, 10, 10);

        IDataStore store = (IDataStore) Proxy.newProxyInstance(
                IDataStore.class.getClassLoader(),
                new Class<?>[]{IDataStore.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    return switch (method.getName()) {
                        case "getAllPlots" -> List.of(transferred);
                        case "getPlot" -> null;
                        default -> primitiveDefault(method.getReturnType());
                    };
                });

        assertSame(transferred, store.getPlotById(plotId));
    }

    @Test
    void rollbackResolvesStableIdBeforeOwnerFallback() throws Exception {
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        int stable = manager.indexOf("getPlotById(snapshot.getPlotId())");
        assertTrue(stable >= 0,
                "Rollback must resolve the live plot by stable id instead of the snapshot's historic owner");
        assertTrue(!manager.contains("getPlot(snapshot.getOwner(), snapshot.getPlotId())"),
                "Rollback must not fall back to a historic owner lookup after plot transfer");
    }

    @Test
    void everyStaffRestoreEntryPointUsesTheOwnedRegionRestoreDispatcher() throws Exception {
        String menu = Files.readString(JAVA.resolve("snapshots/SnapshotAdminGUI.java"));
        String command = Files.readString(JAVA.resolve("admin/AdminCommand.java"));
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        assertTrue(menu.contains("restoreAsync(snapshotId,"),
                "Snapshot menu rollback must use the restore dispatcher");
        assertTrue(command.contains("restoreAsync(snapshotId,"),
                "The standing-plot restore command must use the restore dispatcher");
        assertTrue(manager.contains("plugin.scheduler().runAt(target")
                        && manager.contains("plugin.scheduler().owns(target)"),
                "Restore dispatcher must enter and verify the snapshot plot region");
    }

    @Test
    void disabledOptionalModulesDoNotCreateStaffMenuActions() throws Exception {
        String admin = Files.readString(JAVA.resolve("gui/AdminGUI.java"));
        for (String condition : List.of(
                "mod(com.aegisguard.config.Modules.Id.EXPANSIONS)",
                "mod(com.aegisguard.config.Modules.Id.SNAPSHOTS)",
                "mod(com.aegisguard.config.Modules.Id.ROUTES)",
                "mod(com.aegisguard.config.Modules.Id.ARENA)",
                "mod(com.aegisguard.config.Modules.Id.AUDIT)")) {
            assertTrue(admin.contains(condition), "Missing staff-menu module gate: " + condition);
        }
    }

    @Test
    void nonAdminsDoNotReceiveEmptyClickableFooterItems() throws Exception {
        String player = Files.readString(JAVA.resolve("gui/PlayerGUI.java"));
        assertTrue(player.contains("if (ctx.admin) {") && player.contains("inv.setItem(SLOT_ADMIN"));
        assertTrue(player.contains("if (GUIManager.isFiller(e.getCurrentItem())) return;"),
                "Unassigned/filler footer cells must not be actionable");
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        return null;
    }
}
