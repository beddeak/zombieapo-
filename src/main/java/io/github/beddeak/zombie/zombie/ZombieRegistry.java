package io.github.beddeak.zombie.zombie;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;

public final class ZombieRegistry {
    private final Map<UUID, Zombie> zombies = new HashMap<>();
    private final Map<UUID, ZombieType> types = new HashMap<>();

    public void track(Entity entity) {
        if (!(entity instanceof Zombie zombie)) { return; }
        ZombieType type = Tags.type(zombie);
        if (type == null) { return; }
        zombies.put(zombie.getUniqueId(), zombie);
        types.put(zombie.getUniqueId(), type);
    }

    public void forget(Entity entity) {
        zombies.remove(entity.getUniqueId());
        types.remove(entity.getUniqueId());
    }

    public void clear() {
        zombies.clear();
        types.clear();
    }

    public void rescan(Server server) {
        clear();
        for (World world : server.getWorlds()) {
            for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) { track(zombie); }
        }
    }

    public ZombieType type(Zombie zombie) {
        ZombieType type = types.get(zombie.getUniqueId());
        return type != null ? type : Tags.type(zombie);
    }

    public List<Zombie> snapshot() {
        List<Zombie> result = new ArrayList<>(zombies.size());
        for (Zombie zombie : zombies.values()) {
            if (zombie.isValid() && !zombie.isDead()) { result.add(zombie); }
        }
        return result;
    }

    public List<Zombie> inWorld(World world) {
        List<Zombie> result = new ArrayList<>();
        for (Zombie zombie : zombies.values()) {
            if (zombie.isValid() && !zombie.isDead() && zombie.getWorld().equals(world)) { result.add(zombie); }
        }
        return result;
    }

    public List<Zombie> ofType(World world, ZombieType type) {
        List<Zombie> result = new ArrayList<>();
        for (Zombie zombie : inWorld(world)) {
            if (types.get(zombie.getUniqueId()) == type) { result.add(zombie); }
        }
        return result;
    }

    public int size() {
        int count = 0;
        for (Zombie zombie : zombies.values()) {
            if (zombie.isValid() && !zombie.isDead()) { count++; }
        }
        return count;
    }

    public int countNear(Location center, double radius) {
        double squared = radius * radius;
        int count = 0;
        for (Zombie zombie : zombies.values()) {
            if (zombie.isValid() && !zombie.isDead() && zombie.getWorld().equals(center.getWorld())
                    && zombie.getLocation().distanceSquared(center) <= squared) {
                count++;
            }
        }
        return count;
    }

    public int countNear(Location center, double radius, ZombieType type) {
        double squared = radius * radius;
        int count = 0;
        for (Zombie zombie : zombies.values()) {
            if (types.get(zombie.getUniqueId()) == type && zombie.isValid() && !zombie.isDead()
                    && zombie.getWorld().equals(center.getWorld())
                    && zombie.getLocation().distanceSquared(center) <= squared) {
                count++;
            }
        }
        return count;
    }

    public Map<ZombieType, Integer> counts(World world) {
        Map<ZombieType, Integer> counts = new EnumMap<>(ZombieType.class);
        for (Zombie zombie : inWorld(world)) { counts.merge(type(zombie), 1, Integer::sum); }
        return counts;
    }
}
