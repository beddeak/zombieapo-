package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.logic.SpawnLocator;
import io.github.beddeak.zombie.spawn.BukkitSampler;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDeathEvent;

final class NecromancerAbility implements Ability {
    private static final int RISE_TICKS = 30;
    private static final double BURIED_DEPTH = 1.9;
    private final Apocalypse core;

    NecromancerAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void tick(Zombie zombie) {
        if (!Tags.ready(zombie, Keys.COOLDOWN)) { return; }
        TypeSettings.Necromancer options = core.settings().types().necromancer();
        Player target = Support.targetOrNearest(zombie, options.range());
        if (target == null || target.getLocation().distanceSquared(zombie.getLocation()) > options.range() * options.range()) { return; }
        World world = zombie.getWorld();
        UUID self = zombie.getUniqueId();
        long alive = core.registry().inWorld(world).stream().filter(other -> self.equals(Tags.uuid(other, Keys.OWNER))).count();
        int globalRoom = core.settings().spawning().globalCap() - core.registry().size();
        int room = (int) Math.min(options.maxAlive() - alive, globalRoom);
        if (room <= 0) { return; }
        Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
        int count = Math.min(room, ThreadLocalRandom.current().nextInt(options.raiseMin(), options.raiseMax() + 1));
        SpawnContext context = core.context(world).withOwner(self);
        BukkitSampler sampler = new BukkitSampler(world);
        Location center = zombie.getLocation();
        int raised = 0;
        for (int index = 0; index < count; index++) {
            Optional<SpawnLocator.Spot> spot = SpawnLocator.find(sampler, center.getX(), center.getY(), center.getZ(), 2, 5,
                    0, Math.PI, true, ThreadLocalRandom.current(), 6);
            if (spot.isEmpty()) { continue; }
            Location surface = new Location(world, spot.get().x() + 0.5, spot.get().y(), spot.get().z() + 0.5);
            BlockData ground = surface.clone().subtract(0, 1, 0).getBlock().getBlockData();
            ZombieType type = ThreadLocalRandom.current().nextInt(10) < 7 ? ZombieType.DEFAULT : ZombieType.SPRINTER;
            Zombie corpse = core.factory().spawn(surface.clone().subtract(0, BURIED_DEPTH, 0), SimpleAbilities.variant(zombie), type, context);
            corpse.setAI(false);
            corpse.setGravity(false);
            corpse.setInvulnerable(true);
            core.effects().add(new Rise(corpse, surface, ground, target));
            raised++;
        }
        if (raised > 0) {
            world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.6f);
            world.spawnParticle(Particle.SOUL, center.clone().add(0, 1, 0), 20, 0.6, 0.8, 0.6, 0.02);
        }
    }

    @Override public void died(Zombie zombie, EntityDeathEvent event) {
        UUID self = zombie.getUniqueId();
        for (Zombie raised : core.registry().inWorld(zombie.getWorld())) {
            if (!self.equals(Tags.uuid(raised, Keys.OWNER)) || raised.equals(zombie)) { continue; }
            raised.setInvulnerable(false);
            raised.getWorld().spawnParticle(Particle.SOUL, raised.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
            raised.setHealth(0);
        }
    }

    private final class Rise implements Effects.Effect {
        private final Zombie corpse;
        private final Location surface;
        private final BlockData ground;
        private final Player target;
        private int ticks;

        Rise(Zombie corpse, Location surface, BlockData ground, Player target) {
            this.corpse = corpse;
            this.surface = surface;
            this.ground = ground;
            this.target = target;
        }

        @Override public World world() { return surface.getWorld(); }

        @Override public boolean tick() {
            if (!corpse.isValid() || corpse.isDead()) { return false; }
            ticks++;
            double depth = BURIED_DEPTH * (1 - (double) ticks / RISE_TICKS);
            Location location = surface.clone().subtract(0, Math.max(0, depth), 0);
            location.setYaw(corpse.getLocation().getYaw());
            corpse.teleport(location);
            surface.getWorld().spawnParticle(Particle.BLOCK, surface, 6, 0.3, 0.05, 0.3, 0, ground);
            return ticks < RISE_TICKS;
        }

        @Override public void end() {
            if (!corpse.isValid() || corpse.isDead()) { return; }
            corpse.teleport(surface);
            corpse.setAI(true);
            corpse.setGravity(true);
            corpse.setInvulnerable(false);
            if (Support.fighting(target) && target.getWorld().equals(corpse.getWorld())) { corpse.setTarget(target); }
        }
    }
}
