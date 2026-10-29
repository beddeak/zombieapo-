package io.github.beddeak.zombie.spawn;

import io.github.beddeak.zombie.config.Settings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/** What to do with a mob spawning in an active world. */
public final class ReplaceRules {
    public enum Action {
        /** Leave the spawn alone. */
        IGNORE,
        /** A zombie-family mob: keep it and give it a custom type. */
        TAG,
        /** A hostile mob: cancel it and spawn a custom zombie instead. */
        REPLACE
    }

    private ReplaceRules() {}

    public static Action decide(EntityType type, SpawnReason reason, Settings.Replace replace) {
        if (!replace.reasons().contains(reason)) { return Action.IGNORE; }
        if (fromSpawner(reason) && replace.keepFromSpawner().contains(type)) { return Action.IGNORE; }
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null) { return Action.IGNORE; }
        if (Zombie.class.isAssignableFrom(entityClass)) { return Action.TAG; }
        if (!Enemy.class.isAssignableFrom(entityClass) || replace.exclude().contains(type)) { return Action.IGNORE; }
        return Action.REPLACE;
    }

    public static boolean fromSpawner(SpawnReason reason) {
        return reason == SpawnReason.SPAWNER || reason == SpawnReason.TRIAL_SPAWNER;
    }
}
