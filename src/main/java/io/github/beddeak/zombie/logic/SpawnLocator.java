package io.github.beddeak.zombie.logic;

import java.util.Optional;
import java.util.Random;

public final class SpawnLocator {
    public enum Kind { SOLID, OPEN, LIQUID, BLOCKED }

    public interface Sampler {
        boolean loaded(int x, int z);
        Kind kind(int x, int y, int z);
        int surface(int x, int z);
        int minY();
        int maxY();
    }

    public record Spot(int x, int y, int z) {}

    private static final int CAVE_SPAN = 8;

    private SpawnLocator() {}

    public static Optional<Spot> find(Sampler sampler, double cx, double cy, double cz, double minDistance,
                                      double maxDistance, double angle, double halfWidth, boolean cave,
                                      Random random, int attempts) {
        boolean underground = cave || cy < sampler.surface((int) Math.floor(cx), (int) Math.floor(cz)) - 6;
        for (int attempt = 0; attempt < attempts; attempt++) {
            double direction = angle + (random.nextDouble() * 2 - 1) * halfWidth;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.floor(cx + Math.cos(direction) * distance);
            int z = (int) Math.floor(cz + Math.sin(direction) * distance);
            if (!sampler.loaded(x, z)) { continue; }
            Optional<Spot> spot = underground ? cave(sampler, x, (int) Math.floor(cy), z) : surface(sampler, x, z);
            if (spot.isPresent()) { return spot; }
        }
        return Optional.empty();
    }

    public static Optional<Spot> cave(Sampler sampler, int x, int y, int z) {
        for (int offset = 0; offset <= CAVE_SPAN; offset++) {
            if (standable(sampler, x, y - offset, z)) { return Optional.of(new Spot(x, y - offset, z)); }
            if (offset > 0 && standable(sampler, x, y + offset, z)) { return Optional.of(new Spot(x, y + offset, z)); }
        }
        return Optional.empty();
    }

    public static Optional<Spot> surface(Sampler sampler, int x, int z) {
        int y = sampler.surface(x, z) + 1;
        return standable(sampler, x, y, z) ? Optional.of(new Spot(x, y, z)) : Optional.empty();
    }

    public static boolean standable(Sampler sampler, int x, int y, int z) {
        if (y - 1 < sampler.minY() || y + 1 >= sampler.maxY()) { return false; }
        return sampler.kind(x, y - 1, z) == Kind.SOLID && sampler.kind(x, y, z) == Kind.OPEN
                && sampler.kind(x, y + 1, z) == Kind.OPEN;
    }
}
