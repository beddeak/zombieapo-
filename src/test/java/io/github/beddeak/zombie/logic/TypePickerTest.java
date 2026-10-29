package io.github.beddeak.zombie.logic;

import static org.junit.jupiter.api.Assertions.*;

import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class TypePickerTest {
    private final TypeSettings types = Settings.defaults().types();

    @Test void everyRandomTypeAppearsFromDayOne() {
        Map<ZombieType, Integer> weights = TypePicker.weights(types, 1, 0, 0, type -> true);
        for (ZombieType type : ZombieType.values()) {
            assertEquals(type.random(), weights.containsKey(type), type.name());
        }
        assertEquals(40, weights.get(ZombieType.DEFAULT));
        assertEquals(8, weights.get(ZombieType.SPRINTER));
    }

    @Test void startDaysUnlockTypesAndBossTypesNeverAppear() {
        Map<ZombieType, Integer> weights = TypePicker.weights(types, 6, 0, 0, type -> true);
        assertTrue(weights.containsKey(ZombieType.NECROMANCER));
        assertTrue(weights.containsKey(ZombieType.PILLAR));
        assertFalse(weights.containsKey(ZombieType.TANK));
        assertFalse(weights.containsKey(ZombieType.PLAYER));
    }

    @Test void reductionAndSprinterBonusChangeOnlyTheirTypes() {
        Map<ZombieType, Integer> weights = TypePicker.weights(types, 1, 30, 20, type -> true);
        assertEquals(10, weights.get(ZombieType.DEFAULT));
        assertEquals(28, weights.get(ZombieType.SPRINTER));
        assertFalse(TypePicker.weights(types, 1, 500, 0, type -> true).containsKey(ZombieType.DEFAULT));
    }

    @Test void bodyRestrictionsAreApplied() {
        Map<ZombieType, Integer> drowned = TypePicker.weights(types, 30, 0, 0, type -> type.fits(EntityType.DROWNED));
        assertFalse(drowned.containsKey(ZombieType.PILLAR));
        assertFalse(drowned.containsKey(ZombieType.JOCKEY));
        assertTrue(drowned.containsKey(ZombieType.GRABBER));
    }

    @Test void pickFollowsWeights() {
        Map<ZombieType, Integer> weights = new EnumMap<>(ZombieType.class);
        weights.put(ZombieType.DEFAULT, 3);
        weights.put(ZombieType.BOOMER, 1);
        Random random = new Random(7);
        int boomers = 0;
        for (int index = 0; index < 20_000; index++) {
            if (TypePicker.pick(weights, random) == ZombieType.BOOMER) { boomers++; }
        }
        assertEquals(5_000, boomers, 400);
        assertEquals(ZombieType.DEFAULT, TypePicker.pick(Map.of(), random));
    }
}
