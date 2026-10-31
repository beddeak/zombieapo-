package io.github.beddeak.zombie.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpawnLocatorTest {
    private static final class World implements SpawnLocator.Sampler {
        @Override public boolean loaded(int x, int z) { return x >= -50; }

        @Override public SpawnLocator.Kind kind(int x, int y, int z) {
            if (x >= 100 && y == 64) { return SpawnLocator.Kind.LIQUID; }
            if (y <= 64 && !(y >= 21 && y <= 23)) { return SpawnLocator.Kind.SOLID; }
            return SpawnLocator.Kind.OPEN;
        }

        @Override public int surface(int x, int z) { return 64; }
        @Override public int minY() { return -64; }
        @Override public int maxY() { return 320; }
    }

    private final World world = new World();

    @Test void surfaceSpotsAreOnTopOfTheGroundAtTheRightDistance() {
        Random random = new Random(5);
        for (int index = 0; index < 200; index++) {
            Optional<SpawnLocator.Spot> spot = SpawnLocator.find(world, 0, 65, 0, 20, 40, 0, Math.PI, false, random, 6);
            assertTrue(spot.isPresent());
            assertEquals(65, spot.get().y());
            double distance = Math.hypot(spot.get().x(), spot.get().z());
            assertTrue(distance >= 18 && distance <= 42, "distance " + distance);
        }
    }

    @Test void undergroundCentersSearchNearTheirOwnHeight() {
        Optional<SpawnLocator.Spot> spot = SpawnLocator.find(world, 0, 21, 0, 5, 10, 0, Math.PI, false, new Random(1), 6);
        assertTrue(spot.isPresent());
        assertEquals(21, spot.get().y());
    }

    @Test void waterAndUnloadedChunksAreNeverUsed() {
        assertTrue(SpawnLocator.surface(world, 120, 0).isEmpty());
        assertTrue(SpawnLocator.find(world, -200, 65, 0, 20, 40, 0, Math.PI, false, new Random(2), 20).isEmpty());
    }

    @Test void sectorKeepsSpotsInOneDirection() {
        Random random = new Random(9);
        for (int index = 0; index < 100; index++) {
            SpawnLocator.Spot spot = SpawnLocator.find(world, 0, 65, 0, 30, 45, 0, 0.3, false, random, 6).orElseThrow();
            assertTrue(spot.x() > 25, "x " + spot.x());
            assertTrue(Math.abs(spot.z()) < spot.x(), "z " + spot.z());
        }
    }

    @Test void standableNeedsSolidFloorAndTwoOpenBlocks() {
        assertTrue(SpawnLocator.standable(world, 0, 65, 0));
        assertFalse(SpawnLocator.standable(world, 0, 64, 0));
        assertFalse(SpawnLocator.standable(world, 0, 319, 0));
    }
}
