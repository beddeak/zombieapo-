package io.github.beddeak.zombie.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RulesTest {
    @Test void hordeGrowsWithDaysAndIsCappedEvenly() {
        assertEquals(8, HordeMath.perPlayer(8, 1, 1, 30, 1));
        assertEquals(12, HordeMath.perPlayer(8, 1, 5, 30, 1));
        assertEquals(18, HordeMath.perPlayer(8, 1, 5, 30, 1.5));
        assertEquals(37, HordeMath.perPlayer(8, 1, 999, 30, 1));
        assertEquals(10, HordeMath.capped(10, 5, 80));
        assertEquals(8, HordeMath.capped(20, 10, 80));
        assertEquals(0, HordeMath.capped(10, 0, 80));
    }

    @Test void multiplierStopsAtEveryCap() {
        assertTrue(AbilityRules.canSplit(1, 4, 0, 60, true));
        assertFalse(AbilityRules.canSplit(4, 4, 0, 60, true));
        assertTrue(AbilityRules.canSplit(400, 0, 0, 60, true));
        assertFalse(AbilityRules.canSplit(1, 4, 60, 60, true));
        assertFalse(AbilityRules.canSplit(1, 4, 0, 60, false));
        assertEquals(1.0, AbilityRules.generationFactor(0.8, 1), 1e-9);
        assertEquals(0.64, AbilityRules.generationFactor(0.8, 3), 1e-9);
    }

    @Test void thiefNeverTakesWeaponsArmorOrTheHeldSlot() {
        assertTrue(AbilityRules.weaponOrArmor(Material.DIAMOND_SWORD));
        assertTrue(AbilityRules.weaponOrArmor(Material.IRON_AXE));
        assertTrue(AbilityRules.weaponOrArmor(Material.NETHERITE_CHESTPLATE));
        assertTrue(AbilityRules.weaponOrArmor(Material.SHIELD));
        assertTrue(AbilityRules.weaponOrArmor(Material.BOW));
        assertFalse(AbilityRules.weaponOrArmor(Material.COOKED_BEEF));
        assertFalse(AbilityRules.weaponOrArmor(Material.DIAMOND_BLOCK));
        Random random = new Random(3);
        for (int index = 0; index < 200; index++) {
            int slot = AbilityRules.pickSlot(9, 4, candidate -> candidate == 4 || candidate == 6, random);
            assertEquals(6, slot);
        }
        assertEquals(-1, AbilityRules.pickSlot(9, 0, candidate -> candidate == 0, random));
    }

    @Test void thiefAlwaysStealsSomethingWhenPossible() {
        Random random = new Random(11);
        java.util.Set<Integer> weapons = java.util.Set.of(0, 1, 20);
        assertEquals(5, AbilityRules.theftSlot(36, 0, slot -> slot == 0 || slot == 5 || slot == 30, weapons::contains, random));
        assertEquals(30, AbilityRules.theftSlot(36, 0, slot -> slot == 0 || slot == 1 || slot == 30, weapons::contains, random));
        assertEquals(3, AbilityRules.theftSlot(36, 3, slot -> slot == 1 || slot == 3, weapons::contains, random));
        assertEquals(1, AbilityRules.theftSlot(36, 0, slot -> slot == 0 || slot == 1 || slot == 20, weapons::contains, random));
        assertEquals(20, AbilityRules.theftSlot(36, 0, slot -> slot == 0 || slot == 20, weapons::contains, random));
        assertEquals(0, AbilityRules.theftSlot(36, 0, slot -> slot == 0, weapons::contains, random));
        assertEquals(-1, AbilityRules.theftSlot(36, 0, slot -> false, weapons::contains, random));
    }

    @Test void jockeyIsShakenOffByThreeQuickSneaks() {
        AbilityRules.Shake shake = new AbilityRules.Shake(3, 30);
        assertFalse(shake.press(0));
        assertFalse(shake.press(10));
        assertTrue(shake.press(20));
        assertFalse(shake.press(100));
        assertFalse(shake.press(140));
        assertFalse(shake.press(180), "presses more than 30 ticks apart do not count together");
    }

    @Test void radioMustStandOnNaturalGround() {
        java.util.List<Integer> flat = java.util.Collections.nCopies(16, 64);
        assertEquals(EndingRules.Ground.OK, EndingRules.ground(65, true, 3, flat));
        assertEquals(EndingRules.Ground.OK, EndingRules.ground(70, true, 3, flat), "a small hill is fine");
        assertEquals(EndingRules.Ground.TOO_HIGH, EndingRules.ground(80, true, 3, flat), "sky base on a thick platform");
        assertEquals(EndingRules.Ground.FLOATING, EndingRules.ground(65, true, 1, flat), "thin platform or one block");
        assertEquals(EndingRules.Ground.COVERED, EndingRules.ground(30, false, 3, flat), "underground or under a roof");
        java.util.List<Integer> fewSamples = java.util.List.of(64, 64);
        assertEquals(EndingRules.Ground.OK, EndingRules.ground(90, true, 3, fewSamples), "too few samples to judge height");
        java.util.List<Integer> platformAround = new java.util.ArrayList<>(java.util.Collections.nCopies(6, 85));
        platformAround.addAll(java.util.Collections.nCopies(10, 64));
        assertEquals(EndingRules.Ground.TOO_HIGH, EndingRules.ground(86, true, 3, platformAround),
                "a platform covering a few sample columns does not fool the median");
    }

    @Test void antennaMustTouchTheRadioOnTopOrASide() {
        assertTrue(EndingRules.attached(0, 1, 0), "on top");
        assertTrue(EndingRules.attached(1, 0, 0));
        assertTrue(EndingRules.attached(-1, 0, 0));
        assertTrue(EndingRules.attached(0, 0, 1));
        assertTrue(EndingRules.attached(0, 0, -1));
        assertFalse(EndingRules.attached(0, 0, 0));
        assertFalse(EndingRules.attached(0, -1, 0), "under the radio");
        assertFalse(EndingRules.attached(1, 0, 1), "diagonal");
        assertFalse(EndingRules.attached(0, 2, 0), "one block too high");
        assertFalse(EndingRules.attached(1, 1, 0), "on top of a neighbour");
        assertFalse(EndingRules.attached(2, 0, 0));
    }

    @Test void arrowPointsRelativeToTheView() {
        assertEquals("↑", EndingRules.arrow(0, 0, 10));
        assertEquals("↓", EndingRules.arrow(0, 0, -10));
        assertEquals("→", EndingRules.arrow(0, -10, 0));
        assertEquals("←", EndingRules.arrow(0, 10, 0));
        assertEquals("↑", EndingRules.arrow(90, -10, 0));
        assertEquals("↗", EndingRules.arrow(0, -10, 10));
        int[] column = EndingRules.column(0, 0, 0, 100);
        assertArrayEquals(new int[]{100, 0}, column);
    }

    @Test void escapeWaitsForPlayersWhoAreStillRunningOrRespawning() {
        assertFalse(EndingRules.escapeOver(1, 1, 600), "the last one left died and is respawning");
        assertTrue(EndingRules.escapeOver(2, 0, 600), "everyone made it");
        assertFalse(EndingRules.escapeOver(0, 0, 600), "nobody made it yet");
        assertTrue(EndingRules.escapeOver(0, 3, 0), "time is up");
    }
}
