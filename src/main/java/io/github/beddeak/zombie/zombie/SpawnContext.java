package io.github.beddeak.zombie.zombie;

import java.util.UUID;

/** World conditions at the moment a custom zombie is created. */
public record SpawnContext(int day, boolean bloodMoon, int horde, int generation, UUID owner) {
    public static SpawnContext of(int day, boolean bloodMoon) {
        return new SpawnContext(day, bloodMoon, 0, 1, null);
    }

    public SpawnContext withHorde(int hordeId) {
        return new SpawnContext(day, bloodMoon, hordeId, generation, owner);
    }

    public SpawnContext withGeneration(int value) {
        return new SpawnContext(day, bloodMoon, horde, value, owner);
    }

    public SpawnContext withOwner(UUID value) {
        return new SpawnContext(day, bloodMoon, horde, generation, value);
    }
}
