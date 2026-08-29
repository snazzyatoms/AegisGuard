package com.aegisguard.protection;

import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSanctuaryRulesTest {

    @Test
    void voidAndSuicideStillHurtInsideSanctuary() {
        assertTrue(ProtectionManager.isUnavoidableDamage(EntityDamageEvent.DamageCause.VOID));
        assertTrue(ProtectionManager.isUnavoidableDamage(EntityDamageEvent.DamageCause.SUICIDE));
    }

    @Test
    void combatAndEnvironmentDamageAreBlocked() {
        assertFalse(ProtectionManager.isUnavoidableDamage(EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        assertFalse(ProtectionManager.isUnavoidableDamage(EntityDamageEvent.DamageCause.FALL));
        assertFalse(ProtectionManager.isUnavoidableDamage(EntityDamageEvent.DamageCause.FIRE));
        assertFalse(ProtectionManager.isUnavoidableDamage(EntityDamageEvent.DamageCause.DROWNING));
        assertFalse(ProtectionManager.isUnavoidableDamage(null));
    }

    @Test
    void killAndWorldBorderStayUnavoidableWhenTheApiExposesThem() {
        for (String name : new String[]{"KILL", "WORLD_BORDER"}) {
            try {
                EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.valueOf(name);
                assertTrue(ProtectionManager.isUnavoidableDamage(cause), name);
            } catch (IllegalArgumentException ignored) {
                // Older 1.20 APIs do not define these causes.
            }
        }
    }
}
