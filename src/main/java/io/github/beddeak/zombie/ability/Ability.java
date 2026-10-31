package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.zombie.SpawnContext;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public interface Ability {
    default void spawned(Zombie zombie, SpawnContext context) {}

    default void tick(Zombie zombie) {}

    default void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {}

    default void damaged(Zombie zombie, EntityDamageEvent event) {}

    default void died(Zombie zombie, EntityDeathEvent event) {}
}
