package io.github.beddeak.zombie.spawn;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.logic.SpawnLocator;
import io.github.beddeak.zombie.zombie.SpawnContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Keeps zombies around players regardless of light level, within per-player and server caps. */
public final class ExtraSpawner {
    private final Apocalypse core;
    private final Map<String, Long> nextRun = new HashMap<>();

    public ExtraSpawner(Apocalypse core) {
        this.core = core;
    }

    public void tick(long ticks) {
        Settings settings = core.settings();
        if (!settings.spawning().extraEnabled()) { return; }
        for (World world : core.activeWorlds()) {
            if (ticks < nextRun.getOrDefault(world.getName(), 0L)) { continue; }
            boolean bloodMoon = core.bloodMoon(world);
            double multiplier = bloodMoon ? settings.bloodMoon().spawnIntervalMultiplier() : 1;
            nextRun.put(world.getName(), ticks + Math.max(1, Math.round(settings.spawning().intervalTicks() * multiplier)));
            run(world, bloodMoon);
        }
    }

    public void forget(World world) {
        nextRun.remove(world.getName());
    }

    private void run(World world, boolean bloodMoon) {
        Settings settings = core.settings();
        Settings.Spawning spawning = settings.spawning();
        int global = core.registry().size();
        int cap = core.crowdCap(world);
        SpawnContext context = SpawnContext.of(core.day(world), bloodMoon);
        BukkitSampler sampler = new BukkitSampler(world);
        boolean cave = world.getEnvironment() == World.Environment.NETHER;
        for (Player player : core.survivors(world)) {
            if (global >= spawning.globalCap()) { return; }
            int near = core.registry().countNear(player.getLocation(), spawning.countRadius());
            int wanted = Math.min(spawning.perAttempt(), Math.min(cap - near, spawning.globalCap() - global));
            Location center = player.getLocation();
            for (int index = 0; index < wanted; index++) {
                Optional<SpawnLocator.Spot> spot = SpawnLocator.find(sampler, center.getX(), center.getY(), center.getZ(),
                        spawning.minDistance(), spawning.maxDistance(), 0, Math.PI, cave, ThreadLocalRandom.current(), 6);
                if (spot.isEmpty()) { continue; }
                core.factory().spawnRandom(new Location(world, spot.get().x() + 0.5, spot.get().y(), spot.get().z() + 0.5), context, 0);
                global++;
            }
        }
    }
}
