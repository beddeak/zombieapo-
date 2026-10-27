package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.zombie.SpawnContext;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Behaviour of one zombie type. Per-zombie state lives in the zombie's persistent data, not here. */
public interface Ability {
    /** Called once when a zombie gets this type, before it is added to the world. */
    default void spawned(Zombie zombie, SpawnContext context) {}

    /** Called every 10 ticks for loaded zombies in active worlds. */
    default void tick(Zombie zombie) {}

    /** The zombie hit a player with a melee attack. The event may be cancelled. */
    default void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {}

    default void damaged(Zombie zombie, EntityDamageEvent event) {}

    default void died(Zombie zombie, EntityDeathEvent event) {}
}
