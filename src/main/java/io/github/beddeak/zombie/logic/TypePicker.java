package io.github.beddeak.zombie.logic;

import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

public final class TypePicker {
    private TypePicker() {}

    public static Map<ZombieType, Integer> weights(TypeSettings types, int day, int defaultReduction, int sprinterBonus,
                                                   Predicate<ZombieType> fits) {
        Map<ZombieType, Integer> weights = new EnumMap<>(ZombieType.class);
        for (ZombieType type : ZombieType.values()) {
            if (!type.random() || !fits.test(type)) { continue; }
            TypeSettings.Spawn spawn = types.spawn(type);
            if (day < spawn.minDay() || spawn.weight() <= 0) { continue; }
            int weight = spawn.weight();
            if (type == ZombieType.DEFAULT) { weight = Math.max(0, weight - defaultReduction); }
            if (type == ZombieType.SPRINTER) { weight += sprinterBonus; }
            if (weight > 0) { weights.put(type, weight); }
        }
        return weights;
    }

    public static ZombieType pick(Map<ZombieType, Integer> weights, Random random) {
        long total = 0;
        for (int weight : weights.values()) { total += weight; }
        if (total <= 0) { return ZombieType.DEFAULT; }
        long roll = (long) (random.nextDouble() * total);
        for (Map.Entry<ZombieType, Integer> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) { return entry.getKey(); }
        }
        return ZombieType.DEFAULT;
    }
}
