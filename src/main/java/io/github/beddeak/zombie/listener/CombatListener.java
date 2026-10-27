package io.github.beddeak.zombie.listener;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.UUID;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/** Routes melee hits, damage, deaths and landed projectiles to the zombie types. */
public final class CombatListener implements Listener {
    private final Apocalypse core;

    public CombatListener(Apocalypse core) {
        this.core = core;
    }

    /** Celebration fireworks and signal flares are decoration only. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void firework(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework && Tags.helper(event.getDamager()) != null) { event.setCancelled(true); }
    }

    /** Zombies never hurt each other: melee, projectiles, spit clouds and boomer/bomber blasts are all cancelled. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void noFriendlyFire(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie) || !core.active(event.getEntity().getWorld())) { return; }
        if (core.zombieBlast() || (event instanceof EntityDamageByEntityEvent byEntity && fromZombie(byEntity.getDamager()))) {
            event.setCancelled(true);
        }
    }

    /** No infighting: a zombie never takes another zombie as its target, even after being hit by one. */
    @EventHandler(ignoreCancelled = true)
    public void noInfighting(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Zombie && event.getTarget() instanceof Zombie && core.active(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    private static boolean fromZombie(Entity damager) {
        if (damager instanceof Zombie) { return true; }
        if (damager instanceof Projectile projectile) { return projectile.getShooter() instanceof Zombie; }
        return damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Zombie;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (damager instanceof Zombie zombie && victim instanceof Player player && Tags.custom(zombie)
                && core.active(zombie.getWorld()) && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            core.abilities().get(core.registry().type(zombie)).attacked(zombie, player, event);
        }
        if (victim instanceof Zombie zombie && Tags.custom(zombie) && core.registry().type(zombie) == ZombieType.JOCKEY) {
            Player attacker = damager instanceof Player player ? player
                    : damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
            if (attacker != null) { core.abilities().jockey().struck(zombie, attacker); }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Zombie zombie && Tags.custom(zombie) && core.active(zombie.getWorld())) {
            core.abilities().get(core.registry().type(zombie)).damaged(zombie, event);
        }
    }

    @EventHandler
    public void died(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie zombie) || !Tags.custom(zombie)) { return; }
        ZombieType type = core.registry().type(zombie);
        core.abilities().get(type).died(zombie, event);
        if (type == ZombieType.PLAYER) {
            UUID owner = Tags.uuid(zombie, Keys.OWNER);
            if (owner != null && core.store().playerZombies().remove(owner, zombie.getUniqueId())) { core.save(); }
        }
    }

    @EventHandler
    public void landed(ProjectileHitEvent event) {
        core.abilities().projectileLanded(event.getEntity(), event);
    }
}
