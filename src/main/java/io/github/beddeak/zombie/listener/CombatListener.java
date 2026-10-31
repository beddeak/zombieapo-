package io.github.beddeak.zombie.listener;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

public final class CombatListener implements Listener {
    private static final long BLAME_TICKS = 200;

    private record Hit(String attacker, long tick) {}

    private final Apocalypse core;
    private final Map<UUID, Hit> lastHits = new HashMap<>();

    public CombatListener(Apocalypse core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void firework(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework && Tags.helper(event.getDamager()) != null) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void noFriendlyFire(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie) || !core.active(event.getEntity().getWorld())) { return; }
        if (core.zombieBlast() || (event instanceof EntityDamageByEntityEvent byEntity && fromZombie(byEntity.getDamager()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void noInfighting(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Zombie && event.getTarget() instanceof Zombie && core.active(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    private static boolean fromZombie(Entity damager) {
        return zombieSource(damager) != null;
    }

    private static Zombie zombieSource(Entity damager) {
        if (damager instanceof Zombie zombie) { return zombie; }
        if (damager instanceof Projectile projectile) { return projectile.getShooter() instanceof Zombie zombie ? zombie : null; }
        return damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Zombie zombie ? zombie : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !core.active(player.getWorld())) { return; }
        String attacker = null;
        if (core.zombieBlast()) {
            attacker = attackerName(core.blastType(), null);
        } else if (event instanceof EntityDamageByEntityEvent byEntity) {
            Zombie zombie = zombieSource(byEntity.getDamager());
            if (zombie != null && Tags.custom(zombie)) { attacker = attackerName(core.registry().type(zombie), zombie); }
        }
        if (attacker != null) { lastHits.put(player.getUniqueId(), new Hit(attacker, core.ticks())); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerDied(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Hit hit = lastHits.remove(player.getUniqueId());
        WorldData data = core.data(player.getWorld());
        if (data == null || !core.active(player.getWorld())) { return; }
        data.stats(player.getUniqueId()).deaths++;
        core.statsChanged();
        if (hit != null && core.ticks() - hit.tick() <= BLAME_TICKS) {
            event.deathMessage(Component.text(player.getName() + "이(가) " + hit.attacker() + "에게 당했습니다."));
        }
    }

    private static String attackerName(ZombieType type, Zombie zombie) {
        if (type == null || type == ZombieType.DEFAULT) { return "좀비"; }
        return switch (type) {
            case TANK -> "탱크";
            case PLAYER -> zombie != null && zombie.customName() != null
                    ? PlainTextComponentSerializer.plainText().serialize(zombie.customName()) + " 좀비" : "플레이어 좀비";
            default -> type.displayName() + " 좀비";
        };
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
        Player killer = zombie.getKiller();
        WorldData data = core.data(zombie.getWorld());
        if (killer != null && data != null && core.active(zombie.getWorld())) {
            data.stats(killer.getUniqueId()).kill(type);
            core.statsChanged();
        }
        if (type == ZombieType.PLAYER) {
            UUID owner = Tags.uuid(zombie, Keys.OWNER);
            if (owner != null && core.store().playerZombies().remove(owner, zombie.getUniqueId())) { core.save(); }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void resurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Zombie zombie && Tags.custom(zombie) && Tags.uuid(zombie, Keys.STOLEN_FROM) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void landed(ProjectileHitEvent event) {
        core.abilities().projectileLanded(event.getEntity(), event);
    }
}
