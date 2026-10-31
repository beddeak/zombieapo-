package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

final class BomberAbility implements Ability {
    private final Apocalypse core;
    private final Set<UUID> fusing = new HashSet<>();

    BomberAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void tick(Zombie zombie) {
        if (fusing.contains(zombie.getUniqueId())) { return; }
        TypeSettings.Bomber options = core.settings().types().bomber();
        Player target = Support.target(zombie);
        if (target == null) { return; }
        double trigger = options.triggerDistance();
        if (target.getLocation().distanceSquared(zombie.getLocation()) > trigger * trigger) { return; }
        fusing.add(zombie.getUniqueId());
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.5f, 0.9f);
        core.effects().add(new Fuse(zombie, options.fuseTicks(), options.power(), options.breakBlocks()));
    }

    private final class Fuse implements Effects.Effect {
        private final Zombie zombie;
        private final int fuse;
        private final double power;
        private final boolean breakBlocks;
        private int ticks;

        Fuse(Zombie zombie, int fuse, double power, boolean breakBlocks) {
            this.zombie = zombie;
            this.fuse = fuse;
            this.power = power;
            this.breakBlocks = breakBlocks;
        }

        @Override public World world() { return zombie.getWorld(); }

        @Override public boolean tick() {
            if (!zombie.isValid() || zombie.isDead()) { return false; }
            ticks++;
            zombie.setGlowing((ticks / 4) % 2 == 0);
            zombie.getWorld().spawnParticle(Particle.SMOKE, zombie.getLocation().add(0, 1.8, 0), 3, 0.2, 0.2, 0.2, 0.01);
            if (ticks < fuse) { return true; }
            Location location = zombie.getLocation().add(0, 0.5, 0);
            core.registry().forget(zombie);
            zombie.remove();
            Support.explode(core, ZombieType.BOMBER, location, power, breakBlocks);
            return false;
        }

        @Override public void end() {
            fusing.remove(zombie.getUniqueId());
            if (zombie.isValid()) { zombie.setGlowing(false); }
        }
    }
}
