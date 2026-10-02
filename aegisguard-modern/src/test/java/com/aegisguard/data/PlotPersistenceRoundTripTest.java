package com.aegisguard.data;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: the SQL store previously read the flags/roles columns with ",":"/"-style
 * delimiters while {@link Plot#serializeFlags()} / {@link Plot#serializeRoles()} write
 * "k=v;k=v". Every flag (e.g. safe_zone) and every role silently dropped on each boot.
 * These tests pin the serializer/deserializer contract and the loader's call sites.
 */
class PlotPersistenceRoundTripTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void flagsRoundTripPreservesEveryEntry() {
        Plot src = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        src.setFlag("safe_zone", true);
        src.setFlag("mob_barrier", true);
        src.setFlag("pvp", false);

        String blob = src.serializeFlags();
        assertFalse(blob.isEmpty());
        // Serialized form must only contain delimiters the deserializer understands.
        assertFalse(blob.contains(","), "flags blob must not use ',' separators");
        assertFalse(blob.contains(":"), "flags blob must not use ':' separators");

        Plot dst = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        dst.deserializeFlags(blob);
        assertTrue(dst.getFlag("safe_zone", false));
        assertTrue(dst.getFlag("mob_barrier", false));
        assertFalse(dst.getFlag("pvp", true));
        assertFalse(dst.getFlag("missing", false));
    }

    @Test
    void rolesRoundTripPreservesEveryMember() {
        UUID member = UUID.randomUUID();
        UUID guard = UUID.randomUUID();
        Plot src = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        assertTrue(src.setRole(member, "farmer", false));
        assertTrue(src.setRole(guard, "guard", false));

        String blob = src.serializeRoles();
        assertFalse(blob.contains(","), "roles blob must not use ',' separators");
        assertFalse(blob.contains(":"), "roles blob must not use ':' separators");

        Plot dst = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        dst.deserializeRoles(blob);
        assertEquals("farmer", dst.getRole(member));
        assertEquals("guard", dst.getRole(guard));
    }

    @Test
    void deserializersTolerateNullAndBlank() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        assertDoesNotThrow(() -> plot.deserializeFlags(null));
        assertDoesNotThrow(() -> plot.deserializeFlags(""));
        assertDoesNotThrow(() -> plot.deserializeRoles(null));
    }

    @Test
    void roleFlagsMultiEntryRoundTrip() {
        // Regression: serializeRoleFlags joins entries with ';' — the SQL settings blob
        // must escape that or entries past the first are dropped on load.
        Plot src = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        src.setRoleFlagState("member", "build", com.aegisguard.flags.TriState.ALLOW);
        src.setRoleFlagState("member", "interact", com.aegisguard.flags.TriState.DENY);
        src.setRoleFlagState("guard", "pvp", com.aegisguard.flags.TriState.ALLOW);

        String blob = src.serializeRoleFlags();
        Plot dst = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        dst.deserializeRoleFlags(blob);
        assertEquals(com.aegisguard.flags.TriState.ALLOW, dst.getRoleFlagState("member", "build"));
        assertEquals(com.aegisguard.flags.TriState.DENY, dst.getRoleFlagState("member", "interact"));
        assertEquals(com.aegisguard.flags.TriState.ALLOW, dst.getRoleFlagState("guard", "pvp"));
    }

    @Test
    void sqlSettingsBlobEscapesSemicolons() throws Exception {
        String sql = Files.readString(JAVA.resolve("data/SQLDataStore.java"));
        assertTrue(sql.contains("splitSettings(settings)"),
                "applySettings must split on unescaped ';' only");
        assertTrue(sql.contains("unescapeSettingsValue"),
                "applySettings must reverse value escapes");
        assertTrue(sql.contains("replace(\";\", \"\\\\;\")"),
                "serializeSettings must escape ';' inside values");
    }

    @Test
    void sqlSettingsCoversWarpCategoryAndGroup() throws Exception {
        String sql = Files.readString(JAVA.resolve("data/SQLDataStore.java"));
        // YML persists warp.warp-category and group.* — SQL must not drop them.
        for (String key : new String[]{"warpCategory", "groupEnabled", "groupTreasury", "groupId", "groupName"}) {
            assertTrue(sql.contains("\"" + key + "\""), "settings blob missing " + key);
        }
    }

    @Test
    void sqlLoaderUsesCanonicalDeserializers() throws Exception {
        String sql = Files.readString(JAVA.resolve("data/SQLDataStore.java"));
        assertTrue(sql.contains("plot.deserializeFlags("),
                "SQLDataStore must hydrate flags through Plot.deserializeFlags");
        assertTrue(sql.contains("plot.deserializeRoles("),
                "SQLDataStore must hydrate roles through Plot.deserializeRoles");
        assertFalse(sql.contains("flagsStr.split(\",\")"),
                "SQLDataStore must not parse the flags column with comma/colon delimiters");
        assertFalse(sql.contains("rolesStr.split(\",\")"),
                "SQLDataStore must not parse the roles column with comma/colon delimiters");
    }
}
