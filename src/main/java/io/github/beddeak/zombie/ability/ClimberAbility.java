package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;

final class ClimberAbility implements Ability {
    private static final int MAX_CLIMB_TICKS = 400;
    private final Apocalypse core;
    private final Set<UUID> climbing = new HashSet<>();

    ClimberAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void spawned(Zombie zombie, SpawnContext context) {
        Attributes.multiply(zombie, Attribute.FALL_DAMAGE_MULTIPLIER, Keys.MOD_NO_FALL, 0);
    }

    @Override public void tick(Zombie zombie) {
        if (climbing.contains(zombie.getUniqueId())) { return; }
        Player target = Support.target(zombie);
        if (target == null) { return; }
        Location from = zombie.getLocation();
        Location to = target.getLocation();
        if (to.getY() - from.getY() < 1.5 || !wall(from, Support.flatDirection(from, to))) { return; }
        climbing.add(zombie.getUniqueId());
        core.effects().add(new Climb(zombie));
    }

    private static boolean wall(Location from, Vector direction) {
        Location ahead = from.clone().add(direction.clone().multiply(0.75));
        return ahead.getBlock().isSolid() || ahead.add(0, 1, 0).getBlock().isSolid();
    }

    private final class Climb implements Effects.Effect {
        private final Zombie zombie;
        private int ticks;

        Climb(Zombie zombie) {
            this.zombie = zombie;
        }

        @Override public World world() {
            return zombie.getWorld();
        }

        @Override public boolean tick() {
            Player target = zombie.isValid() ? Support.target(zombie) : null;
            if (target == null || ++ticks > MAX_CLIMB_TICKS) { return false; }
            Location from = zombie.getLocation();
            Location to = target.getLocation();
            Vector direction = Support.flatDirection(from, to);
            double rise = to.getY() - from.getY();
            if (rise > -0.5 && wall(from, direction)) {
                zombie.setVelocity(direction.multiply(0.05).setY(core.settings().types().climber().climbSpeed()));
                if (ticks % 5 == 0) { zombie.getWorld().playSound(from, Sound.ENTITY_SPIDER_STEP, 0.8f, 0.7f); }
                return true;
            }
            if (rise > -1.5) { zombie.setVelocity(direction.multiply(0.3).setY(0.25)); }
            return false;
        }

        @Override public void end() {
            climbing.remove(zombie.getUniqueId());
        }
    }
}
