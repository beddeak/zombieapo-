package io.github.beddeak.zombie.data;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStoreTest {
    @TempDir Path folder;

    @Test void everythingSurvivesASaveAndLoad() {
        File file = folder.resolve("data.yml").toFile();
        DataStore store = new DataStore(file, Logger.getAnonymousLogger());
        WorldData data = new WorldData(123);
        data.lastDayShown = 4;
        data.hordeDay = 3;
        data.hordeTime = 15_000;
        data.hordeDone = true;
        data.tankDay = 10;
        data.bloodMoonTankDay = 7;
        data.preparingTicks = 2400;
        data.preparingTotal = 6000;
        UUID crafter = UUID.randomUUID();
        data.assemblyItem = "ANTENNA";
        data.assemblyPos = new BlockPos(3, 70, -9);
        data.assemblyRemaining = 400;
        data.assemblyTotal = 900;
        data.assemblyCrafter = crafter;
        UUID escaped = UUID.randomUUID();
        data.ending.phase = EndingData.Phase.ESCAPE;
        data.ending.totalTicks = 3600;
        data.ending.remainingTicks = 1200;
        data.ending.tanksSpawned.add(60);
        data.ending.escapePoint = new BlockPos(100, 70, -5);
        data.ending.escaped.add(escaped);
        data.ending.originalModes.put(escaped, GameMode.ADVENTURE);
        store.worlds().put("world.with.dots", data);
        store.radios().put("world.with.dots", new BlockPos(1, 64, 2));
        store.antennas().put("world.with.dots", new BlockPos(1, 65, 2));
        UUID owner = UUID.randomUUID();
        UUID zombie = UUID.randomUUID();
        store.playerZombies().put(owner, zombie);
        store.pendingItems().put(owner, List.of("abc", "def"));
        store.pendingModes().put(escaped, new DataStore.PendingMode(GameMode.SURVIVAL, "world.with.dots", new BlockPos(5, 6, 7)));
        store.save();

        DataStore loaded = new DataStore(file, Logger.getAnonymousLogger());
        loaded.load();
        WorldData read = loaded.worlds().get("world.with.dots");
        assertNotNull(read);
        assertEquals(123, read.startDay);
        assertEquals(4, read.lastDayShown);
        assertEquals(3, read.hordeDay);
        assertEquals(15_000, read.hordeTime);
        assertTrue(read.hordeDone);
        assertEquals(10, read.tankDay);
        assertEquals(7, read.bloodMoonTankDay);
        assertEquals(2400, read.preparingTicks);
        assertEquals(6000, read.preparingTotal);
        assertEquals("ANTENNA", read.assemblyItem);
        assertEquals(new BlockPos(3, 70, -9), read.assemblyPos);
        assertEquals(400, read.assemblyRemaining);
        assertEquals(900, read.assemblyTotal);
        assertEquals(crafter, read.assemblyCrafter);
        assertEquals(new BlockPos(1, 65, 2), loaded.antennas().get("world.with.dots"));
        assertEquals(EndingData.Phase.ESCAPE, read.ending.phase);
        assertEquals(3600, read.ending.totalTicks);
        assertEquals(1200, read.ending.remainingTicks);
        assertEquals(List.of(60), read.ending.tanksSpawned);
        assertEquals(new BlockPos(100, 70, -5), read.ending.escapePoint);
        assertEquals(List.of(escaped), List.copyOf(read.ending.escaped));
        assertEquals(GameMode.ADVENTURE, read.ending.originalModes.get(escaped));
        assertEquals(new BlockPos(1, 64, 2), loaded.radios().get("world.with.dots"));
        assertEquals(zombie, loaded.playerZombies().get(owner));
        assertEquals(List.of("abc", "def"), loaded.pendingItems().get(owner));
        assertEquals(new DataStore.PendingMode(GameMode.SURVIVAL, "world.with.dots", new BlockPos(5, 6, 7)),
                loaded.pendingModes().get(escaped));
    }

    @Test void aBrokenFileIsMovedAsideInsteadOfCrashing() throws Exception {
        File file = folder.resolve("data.yml").toFile();
        Files.writeString(file.toPath(), "worlds: [unclosed", StandardCharsets.UTF_8);
        DataStore store = new DataStore(file, Logger.getAnonymousLogger());
        store.load();
        assertTrue(store.worlds().isEmpty());
        assertFalse(file.exists());
        try (var files = Files.list(folder)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("data.yml.broken-")));
        }
    }

    @Test void endingResetClearsProgress() {
        EndingData ending = new EndingData();
        ending.phase = EndingData.Phase.DONE;
        ending.totalTicks = 6000;
        ending.remainingTicks = 0;
        ending.tanksSpawned.add(60);
        ending.escaped.add(UUID.randomUUID());
        assertEquals(300, ending.elapsedSeconds());
        ending.reset();
        assertEquals(EndingData.Phase.NONE, ending.phase);
        assertTrue(ending.tanksSpawned.isEmpty());
        assertTrue(ending.escaped.isEmpty());
    }
}
