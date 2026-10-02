package com.aegisguard;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the grief-vector coverage pass: mob block griefing, sponge/bonemeal cross-border
 * mutation, harmful splash/lingering potions, entity placement (boats/minecarts/end crystals),
 * leashing, breeding, lectern books, and interactable decor blocks.
 */
class ClaimEdgeProtectionContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");
    private static final Path RES = Path.of("src/main/resources");
    private static final List<String> LOCALES = List.of(
            "modern_english", "old_english", "spanish_ar", "spanish_mx",
            "portuguese_br", "french_fr", "german_de", "italian_it", "polish_pl");

    @Test
    void mobGriefingHandlersExist() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(src.contains("EntityChangeBlockEvent"), "must handle EntityChangeBlockEvent");
        assertTrue(src.contains("EntityBlockFormEvent"), "must handle EntityBlockFormEvent (frost walker/trails)");
        assertTrue(src.contains("EntityBreakDoorEvent"), "must handle EntityBreakDoorEvent (zombie doors)");
        assertTrue(src.contains("EntityInteractEvent"), "must handle EntityInteractEvent (egg/farmland trample)");
        assertTrue(src.contains("\"mob-griefing\""), "mob block changes must consult the mob-griefing flag");
        assertTrue(src.contains("TURTLE_EGG"), "turtle eggs must be covered");
        assertTrue(src.contains("instanceof Player"),
                "player-attributed entity changes must still run the build check");
    }

    @Test
    void crossBorderMutationHandlersExist() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(src.contains("SpongeAbsorbEvent"), "must handle SpongeAbsorbEvent");
        assertTrue(src.contains("BlockFertilizeEvent"), "must handle BlockFertilizeEvent");
        assertTrue(src.contains("getBlocks().removeIf"),
                "sponge/fertilize must filter affected blocks instead of blanket-cancelling");
        assertTrue(src.contains("getPlotId()"),
                "same-claim sponges/dispensers must keep working via plot-id compare");
    }

    @Test
    void potionAndEntityVectorsExist() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(src.contains("PotionSplashEvent"), "must handle PotionSplashEvent");
        assertTrue(src.contains("AreaEffectCloudApplyEvent"), "must handle lingering cloud application");
        assertTrue(src.contains("HARMFUL_POTION_TYPES"), "must classify harmful potion effects");
        assertTrue(src.contains("EntityPlaceEvent"), "must handle EntityPlaceEvent (boats/minecarts/crystals)");
        assertTrue(src.contains("PlayerLeashEntityEvent"), "must handle leashing");
        assertTrue(src.contains("EntityBreedEvent"), "must handle breeding");
        assertTrue(src.contains("PlayerTakeLecternBookEvent"), "must handle lectern book theft");
        assertTrue(src.contains("filterSplashVictims"), "potion victims must be filtered, not blanket-cancelled");
    }

    @Test
    void flagRegistrationAndGuiWiring() throws Exception {
        String protection = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        String worldRules = Files.readString(JAVA.resolve("world/WorldRulesManager.java"));
        String gui = Files.readString(JAVA.resolve("gui/PlotFlagsGUI.java"));
        String config = Files.readString(RES.resolve("config.yml"));

        assertTrue(protection.contains("\"mob-griefing\"") && protection.contains("\"interactables\""),
                "both new flags must be registered in ProtectionManager sets");
        assertTrue(worldRules.contains("protections.mob_griefing")
                        && worldRules.contains("protections.interactables"),
                "new claims must seed both flags from config");
        assertTrue(config.contains("mob_griefing: true") && config.contains("interactables: true"),
                "config.yml must ship both master switches on");
        assertTrue(gui.contains("\"mob-griefing\"") && gui.contains("\"interactables\""),
                "both flags must be toggleable in the flag GUI");
    }

    @Test
    void sweepTwoHandlersExist() throws Exception {
        String blocks = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(blocks.contains("ProjectileHitEvent"),
                "projectile hits on target blocks/buttons must gate the redstone ward");
        assertTrue(blocks.contains("isProjectileTriggerable"), "triggerable-block classifier must exist");
        assertTrue(blocks.contains("Material.TARGET"), "target blocks must be covered");
        assertTrue(blocks.contains("PortalCreateEvent"), "portal creation must filter claim blocks");
        assertTrue(blocks.contains("BlockShearEntityEvent"), "dispenser shears must be gated");
        assertTrue(blocks.contains("StructureGrowEvent"), "tree/mushroom growth must be border-filtered");
        assertTrue(blocks.contains("CauldronLevelChangeEvent"), "cauldron fill/drain must be gated");
        assertTrue(blocks.contains("BlockMultiPlaceEvent"), "multi-block placements must be checked");
        assertTrue(blocks.contains("getReplacedBlockStates"),
                "multi-place must inspect every placed block state");
    }

    @Test
    void sweepTwoEntityHandlersExist() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(src.contains("PlayerShearEntityEvent"), "player shearing must be gated");
        assertTrue(src.contains("RaidTriggerEvent"), "raid triggers inside claims must be gated");
        assertTrue(src.contains("e.getRaid().getLocation()"),
                "raid check must resolve the raid center plot");
    }

    @Test
    void alertsPermissionDeclared() throws Exception {
        String pluginYml = Files.readString(RES.resolve("plugin.yml"));
        assertTrue(pluginYml.contains("aegis.admin.alerts:"),
                "the grief-alert permission node must be declared");
        assertTrue(pluginYml.contains("aegis.admin.alerts: true"),
                "aegis.admin.alerts must be a child of the aegis.admin bundle");
    }

    @Test
    void denialChokePointIsReused() throws Exception {
        String src = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(src.contains("DenialGuidance.send"),
                "new denials must flow through DenialGuidance for throttle + staff alerts");
        assertTrue(src.contains("shouldYieldToExternalProtection"),
                "new denials must honor external protection-hook bypass");
    }

    @Test
    void residualVectorsClosed() throws Exception {
        String bpl = Files.readString(JAVA.resolve("protection/BlockProtectionListener.java"));
        assertTrue(bpl.contains("FluidLevelChangeEvent"),
                "dispensers must not drain liquids across a claim border");
        assertTrue(bpl.contains("DRAIN_FACES"),
                "fluid drain must scan adjacent faces for the source dispenser");
        assertTrue(bpl.contains("getOppositeFace"),
                "only dispensers facing the fluid may drain it");

        String pm = Files.readString(JAVA.resolve("protection/ProtectionManager.java"));
        assertTrue(pm.contains("SignChangeEvent"),
                "sign text edits inside claims must be protected");
        assertTrue(pm.contains("endsWith(\"SIGN\")"),
                "all sign variants must be interactables");
        assertTrue(pm.contains("PlayerBucketEntityEvent"),
                "bucket-scooping pets must respect the animals ward");
        assertTrue(pm.contains("EntityType.ENDERMAN") && pm.contains("EntityType.SHULKER"),
                "neutral teleporters must respect claim borders");
    }

    @Test
    void allLocalesHaveFlagStrings() throws Exception {
        for (String locale : LOCALES) {
            String guis = Files.readString(RES.resolve("lang/" + locale + "/guis.yml"));
            String codex = Files.readString(RES.resolve("codex/" + locale + ".yml"));
            for (String key : List.of("button_mob_griefing_on:", "button_mob_griefing_off:",
                    "mob_griefing_toggle_lore:", "button_interactables_on:",
                    "button_interactables_off:", "interactables_toggle_lore:")) {
                assertTrue(guis.contains(key), locale + "/guis.yml missing " + key);
                assertTrue(codex.contains(key), locale + ".yml codex missing " + key);
            }
        }
    }
}
