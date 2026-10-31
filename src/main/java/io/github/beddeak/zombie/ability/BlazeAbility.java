package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.util.TriState;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

final class BlazeAbility implements Ability {
    private static final int WINDUP_TICKS = 12;
    private static final int SHOT_GAP_TICKS = 6;
    private static final double SPREAD = 0.06;
    private final Apocalypse core;
    private final Set<UUID> busy = new HashSet<>();

    BlazeAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void spawned(Zombie zombie, SpawnContext context) {
        zombie.setVisualFire(TriState.TRUE);
    }

    @Override public void tick(Zombie zombie) {
        if (zombie.getVisualFire() != TriState.TRUE) { zombie.setVisualFire(TriState.TRUE); }
        if (busy.contains(zombie.getUniqueId()) || !Tags.ready(zombie, Keys.COOLDOWN)) { return; }
        TypeSettings.Blaze options = core.settings().types().blaze();
        Player target = Support.target(zombie);
        if (target == null) { return; }
        double distance = target.getLocation().distance(zombie.getLocation());
        if (distance < options.minDistance() || distance > options.maxDistance() || !zombie.hasLineOfSight(target)) { return; }
        Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
        busy.add(zombie.getUniqueId());
        core.effects().add(new Volley(zombie, options.shots()));
    }

    @Override public void damaged(Zombie zombie, EntityDamageEvent event) {
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> event.setCancelled(true);
            default -> { }
        }
    }

    private final class Volley implements Effects.Effect {
        private final Zombie zombie;
        private final int shots;
        private int ticks;
        private int fired;

        Volley(Zombie zombie, int shots) {
            this.zombie = zombie;
            this.shots = shots;
        }

        @Override public World world() {
            return zombie.getWorld();
        }

        @Override public boolean tick() {
            Player target = zombie.isValid() ? Support.target(zombie) : null;
            if (target == null) { return false; }
            World world = zombie.getWorld();
            Location eye = zombie.getEyeLocation();
            if (ticks < WINDUP_TICKS) {
                if (ticks == 0) { world.playSound(eye, Sound.ENTITY_BLAZE_BURN, 1.5f, 0.8f); }
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.FLAME, eye, 8, 0.4, 0.5, 0.4, 0.02);
                    world.spawnParticle(Particle.LARGE_SMOKE, eye, 2, 0.3, 0.3, 0.3, 0.01);
                }
            } else if ((ticks - WINDUP_TICKS) % SHOT_GAP_TICKS == 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Vector direction = target.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(eye.toVector()).normalize()
                        .add(new Vector(random.nextGaussian() * SPREAD, random.nextGaussian() * SPREAD, random.nextGaussian() * SPREAD));
                zombie.launchProjectile(SmallFireball.class, null, ball -> {
                    ball.setDirection(direction);
                    ball.setIsIncendiary(false);
                });
                world.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1f);
                if (++fired >= shots) { return false; }
            }
            ticks++;
            return true;
        }

        @Override public void end() {
            busy.remove(zombie.getUniqueId());
        }
    }
}
