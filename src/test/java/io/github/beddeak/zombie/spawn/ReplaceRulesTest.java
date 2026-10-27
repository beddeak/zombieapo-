package io.github.beddeak.zombie.spawn;

import static org.junit.jupiter.api.Assertions.*;

import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.spawn.ReplaceRules.Action;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

class ReplaceRulesTest {
    private final Settings.Replace replace = Settings.defaults().replace();

    @Test void hostileMobsAreReplacedOnNaturalSpawnerAndPatrolSpawns() {
        assertEquals(Action.REPLACE, ReplaceRules.decide(EntityType.CREEPER, SpawnReason.NATURAL, replace));
        assertEquals(Action.REPLACE, ReplaceRules.decide(EntityType.SKELETON, SpawnReason.SPAWNER, replace));
        assertEquals(Action.REPLACE, ReplaceRules.decide(EntityType.BREEZE, SpawnReason.TRIAL_SPAWNER, replace));
        assertEquals(Action.REPLACE, ReplaceRules.decide(EntityType.PILLAGER, SpawnReason.PATROL, replace));
        assertEquals(Action.REPLACE, ReplaceRules.decide(EntityType.PHANTOM, SpawnReason.NATURAL, replace));
        assertEquals(Action.REPLACE, ReplaceRules.decide(EntityType.SLIME, SpawnReason.NATURAL, replace));
    }

    @Test void zombieFamilyIsKeptAndTagged() {
        for (EntityType type : new EntityType[]{EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
                EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN}) {
            assertEquals(Action.TAG, ReplaceRules.decide(type, SpawnReason.NATURAL, replace), type.name());
        }
    }

    @Test void excludedBossesEggsCustomRaidsAndPassiveMobsAreLeftAlone() {
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.ENDER_DRAGON, SpawnReason.NATURAL, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.WITHER, SpawnReason.BUILD_WITHER, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.WARDEN, SpawnReason.NATURAL, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.CREEPER, SpawnReason.SPAWNER_EGG, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.CREEPER, SpawnReason.CUSTOM, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.PILLAGER, SpawnReason.RAID, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.ZOMBIE, SpawnReason.SPAWNER_EGG, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.COW, SpawnReason.NATURAL, replace));
        assertEquals(Action.IGNORE, ReplaceRules.decide(EntityType.SLIME, SpawnReason.SLIME_SPLIT, replace));
    }

    /** Pigs, cows, foxes and every other non-hostile mob are never replaced or tagged, whatever the spawn reason. */
    @Test void passiveAndNeutralMobsAreNeverTouched() {
        Settings.Replace everyReason = new Settings.Replace(EnumSet.allOf(SpawnReason.class), Set.of(), 12);
        int checked = 0;
        for (EntityType type : EntityType.values()) {
            Class<? extends Entity> entityClass = type.getEntityClass();
            if (entityClass == null || Enemy.class.isAssignableFrom(entityClass)) { continue; }
            for (SpawnReason reason : SpawnReason.values()) {
                assertEquals(Action.IGNORE, ReplaceRules.decide(type, reason, everyReason), type + " / " + reason);
            }
            checked++;
        }
        assertTrue(checked > 60, "checked " + checked + " entity types");
        for (EntityType type : List.of(EntityType.PIG, EntityType.COW, EntityType.FOX, EntityType.SHEEP, EntityType.CHICKEN,
                EntityType.HORSE, EntityType.WOLF, EntityType.CAT, EntityType.RABBIT, EntityType.VILLAGER, EntityType.IRON_GOLEM,
                EntityType.BEE, EntityType.LLAMA, EntityType.GOAT, EntityType.PANDA, EntityType.POLAR_BEAR, EntityType.SQUID,
                EntityType.AXOLOTL, EntityType.FROG, EntityType.TURTLE, EntityType.CAMEL, EntityType.ARMADILLO, EntityType.MOOSHROOM)) {
            assertEquals(Action.IGNORE, ReplaceRules.decide(type, SpawnReason.NATURAL, everyReason), type.name());
        }
    }

    @Test void spawnerReasonsAreRecognised() {
        assertTrue(ReplaceRules.fromSpawner(SpawnReason.SPAWNER));
        assertTrue(ReplaceRules.fromSpawner(SpawnReason.TRIAL_SPAWNER));
        assertFalse(ReplaceRules.fromSpawner(SpawnReason.NATURAL));
    }
}
