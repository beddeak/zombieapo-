package io.github.beddeak.zombie.event;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.WorldBar;
import io.github.beddeak.zombie.logic.SpawnLocator;
import io.github.beddeak.zombie.spawn.BukkitSampler;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

/** Spawns the tank boss under its own rules and keeps a world-wide health bar for every loaded tank. */
public final class TankManager {
    private final Apocalypse core;
    private final Map<UUID, WorldBar> bars = new HashMap<>();

    public TankManager(Apocalypse core) {
        this.core = core;
    }

    public int count(World world) {
        return core.registry().ofType(world, ZombieType.TANK).size();
    }

    public int limit(World world) {
        return core.ending().inFinale(world) ? core.settings().ending().finale().tankLimit() : core.settings().tank().limit();
    }

    /** Spawns a tank near a random survivor, respecting the per-world limit. */
    public boolean spawnNearSurvivor(World world) {
        List<Player> survivors = core.survivors(world);
        if (survivors.isEmpty()) { return false; }
        Player player = survivors.get(ThreadLocalRandom.current().nextInt(survivors.size()));
        return spawnNear(world, player.getLocation()) != null;
    }

    /** Spawns a tank 18-28 blocks from {@code around}, or right there when no spot is free. Null when at the limit. */
    public Zombie spawnNear(World world, Location around) {
        if (!core.active(world) || count(world) >= limit(world)) { return null; }
        Optional<SpawnLocator.Spot> spot = SpawnLocator.find(new BukkitSampler(world), around.getX(), around.getY(), around.getZ(),
                18, 28, 0, Math.PI, world.getEnvironment() == World.Environment.NETHER, ThreadLocalRandom.current(), 12);
        Location location = spot.map(found -> new Location(world, found.x() + 0.5, found.y(), found.z() + 0.5)).orElse(around);
        return spawnAt(location);
    }

    public Zombie spawnAt(Location location) {
        World world = location.getWorld();
        Zombie tank = core.factory().spawn(location, Zombie.class, ZombieType.TANK, core.context(world));
        world.playSound(location, Sound.ENTITY_RAVAGER_ROAR, 6f, 0.5f);
        world.playSound(location, Sound.ENTITY_WARDEN_ROAR, 4f, 0.8f);
        Msg.title(world.getPlayers(), "탱크 출현!", NamedTextColor.DARK_RED, "모두 힘을 합쳐 쓰러뜨리세요. 불에 약합니다");
        core.announce(world, Msg.alert("탱크가 나타났습니다! 좌표 " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()));
        updateBars();
        return tank;
    }

    public void updateBars() {
        Set<UUID> alive = new HashSet<>();
        for (World world : core.activeWorlds()) {
            for (Zombie tank : core.registry().ofType(world, ZombieType.TANK)) {
                alive.add(tank.getUniqueId());
                WorldBar bar = bars.computeIfAbsent(tank.getUniqueId(),
                        id -> new WorldBar(Component.text("탱크", NamedTextColor.DARK_RED), BossBar.Color.RED));
                double max = Attributes.maxHealth(tank);
                bar.update(Component.text("탱크  " + (int) Math.ceil(tank.getHealth()) + " / " + (int) Math.ceil(max), NamedTextColor.DARK_RED),
                        tank.getHealth() / Math.max(1, max));
                bar.sync(world.getPlayers());
            }
        }
        for (UUID id : Set.copyOf(bars.keySet())) {
            if (!alive.contains(id)) { bars.remove(id).hide(); }
        }
    }

    public void clear(World world) {
        for (Zombie tank : core.registry().ofType(world, ZombieType.TANK)) {
            WorldBar bar = bars.remove(tank.getUniqueId());
            if (bar != null) { bar.hide(); }
        }
    }

    public void clearAll() {
        for (WorldBar bar : bars.values()) { bar.hide(); }
        bars.clear();
    }
}
