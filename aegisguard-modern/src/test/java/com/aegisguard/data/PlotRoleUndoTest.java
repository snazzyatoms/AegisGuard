package com.aegisguard.data;

import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotRoleUndoTest {

    @Test
    void ordinaryOwnerCanEditOthersButStrangerAndSelfCannot() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 20, 20);
        assertTrue(plot.canModifyMember(player(owner), member, null));
        assertFalse(plot.canModifyMember(player(stranger), member, null));
        assertFalse(plot.canModifyMember(player(owner), owner, null));
        assertFalse(plot.canModifyMember(player(member), member, null));
    }

    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    if (method.getName().equals("hasPermission") || method.getName().equals("isOp")) return false;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }

    @Test
    void lockedMemberCannotBeChangedByUndoAndHistoryIsRetained() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        UUID member = UUID.randomUUID();
        assertTrue(plot.setRole(member, "farmer", false));
        assertTrue(plot.lockMember(member));
        assertFalse(plot.undoLastRoleChange());
        assertEquals("farmer", plot.getRole(member));
        assertNotNull(plot.peekLastRoleChange());
        assertTrue(plot.unlockMember(member));
        assertTrue(plot.undoLastRoleChange());
        assertEquals("visitor", plot.getRole(member));
        assertNull(plot.peekLastRoleChange());
    }

    @Test
    void staleUndoDoesNotOverwriteAnInterveningRoleEdit() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 20, 20);
        UUID member = UUID.randomUUID();
        assertTrue(plot.setRole(member, "farmer", false));
        plot.getPlayerRoles().put(member, "guard");
        assertFalse(plot.undoLastRoleChange());
        assertEquals("guard", plot.getRole(member));
    }
}
