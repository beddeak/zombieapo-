package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Husk;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class SimpleAbilities {
    private SimpleAbilities() {}

    static final class Sprinter implements Ability {
        private final Apocalypse core;
        Sprinter(Apocalypse core) { this.core = core; }

        @Override public void spawned(Zombie zombie, SpawnContext context) {
            Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_TYPE_SPEED, core.settings().types().sprinter().speedMultiplier());
        }
    }

    static final class Boomer implements Ability {
        private final Apocalypse core;
        Boomer(Apocalypse core) { this.core = core; }

        @Override public void died(Zombie zombie, EntityDeathEvent event) {
            if (!core.active(zombie.getWorld())) { return; }
            TypeSettings.Boomer options = core.settings().types().boomer();
            Location location = zombie.getLocation();
            location.getWorld().playSound(location, Sound.ENTITY_CREEPER_PRIMED, 1.2f, 0.7f);
            core.effects().add(new DelayedBlast(core, location, options.fuseTicks(), options.power(), options.breakBlocks()));
        }
    }

    static final class DelayedBlast implements Effects.Effect {
        private final Apocalypse core;
        private final Location location;
        private final int fuse;
        private final double power;
        private final boolean breakBlocks;
        private int ticks;

        DelayedBlast(Apocalypse core, Location location, int fuse, double power, boolean breakBlocks) {
            this.core = core;
            this.location = location.clone().add(0, 0.5, 0);
            this.fuse = fuse;
            this.power = power;
            this.breakBlocks = breakBlocks;
        }

        @Override public World world() { return location.getWorld(); }

        @Override public boolean tick() {
            if (ticks++ < fuse) {
                location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 4, 0.3, 0.4, 0.3, 0.01);
                return true;
            }
            Support.explode(core, ZombieType.BOOMER, location, power, breakBlocks);
            return false;
        }

        @Override public void end() {}
    }

    static final class Thrower implements Ability {
        private final Apocalypse core;
        Thrower(Apocalypse core) { this.core = core; }

        @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
            TypeSettings.Thrower options = core.settings().types().thrower();
            Vector velocity = Support.flatDirection(zombie.getLocation(), victim.getLocation())
                    .multiply(options.horizontal()).setY(options.vertical());
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
            Support.launchLater(core, victim, velocity);
        }
    }

    static final class Toxic implements Ability {
        private final Apocalypse core;
        Toxic(Apocalypse core) { this.core = core; }

        @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
            TypeSettings.Toxic options = core.settings().types().toxic();
            if (options.poisonSeconds() > 0) { victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, options.poisonSeconds() * 20, 0)); }
            if (options.hungerSeconds() > 0) { victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, options.hungerSeconds() * 20, 1)); }
            victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0);
        }
    }

    static final class Frost implements Ability {
        private final Apocalypse core;
        Frost(Apocalypse core) { this.core = core; }

        @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
            TypeSettings.Frost options = core.settings().types().frost();
            victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), options.freezeTicks()));
            if (options.slownessSeconds() > 0) { victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, options.slownessSeconds() * 20, 1)); }
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 1f, 0.8f);
        }
    }

    static final class Vampire implements Ability {
        private final Apocalypse core;
        Vampire(Apocalypse core) { this.core = core; }

        @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
            Attributes.heal(zombie, event.getFinalDamage() * core.settings().types().vampire().healRatio());
            zombie.getWorld().spawnParticle(Particle.DUST, zombie.getLocation().add(0, 1.2, 0), 14, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(Color.RED, 1.3f));
        }
    }

    static final class Jumper implements Ability {
        private final Apocalypse core;
        Jumper(Apocalypse core) { this.core = core; }

        @Override public void spawned(Zombie zombie, SpawnContext context) {
            Attributes.multiply(zombie, Attribute.FALL_DAMAGE_MULTIPLIER, Keys.MOD_NO_FALL, 0);
        }

        @Override public void tick(Zombie zombie) {
            Player target = Support.target(zombie);
            if (target == null || !zombie.isOnGround() || !Tags.ready(zombie, Keys.COOLDOWN)) { return; }
            TypeSettings.Jumper options = core.settings().types().jumper();
            Location from = zombie.getLocation();
            Location to = target.getLocation();
            double distance = Support.horizontalDistance(from, to);
            double rise = to.getY() - from.getY();
            boolean high = rise >= 2 && distance <= options.maxDistance();
            boolean far = distance >= options.minDistance() && distance <= options.maxDistance();
            if (!high && !far) { return; }
            double speed = Math.min(1.6, distance * 0.16 + 0.2);
            double lift = Math.min(1.5, 0.55 + Math.max(0, rise) * 0.12);
            zombie.setVelocity(Support.flatDirection(from, to).multiply(speed).setY(lift));
            zombie.getWorld().playSound(from, Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.8f);
            zombie.getWorld().spawnParticle(Particle.CLOUD, from, 8, 0.3, 0.1, 0.3, 0.02);
            Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
        }
    }

    static final class Ninja implements Ability {
        private final Apocalypse core;
        Ninja(Apocalypse core) { this.core = core; }

        @Override public void spawned(Zombie zombie, SpawnContext context) {
            zombie.getEquipment().clear();
            zombie.setCanPickupItems(false);
            zombie.setInvisible(true);
        }

        @Override public void tick(Zombie zombie) {
            TypeSettings.Ninja options = core.settings().types().ninja();
            long now = zombie.getWorld().getGameTime();
            if (Support.nearest(zombie.getLocation(), options.revealDistance()) != null) {
                reveal(zombie, now);
            } else if (!zombie.isInvisible() && now - Tags.number(zombie, Keys.MARK, 0) >= options.hideAfterTicks()) {
                zombie.setInvisible(true);
                zombie.getWorld().spawnParticle(Particle.LARGE_SMOKE, zombie.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.01);
            }
        }

        @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
            reveal(zombie, zombie.getWorld().getGameTime());
        }

        @Override public void damaged(Zombie zombie, EntityDamageEvent event) {
            reveal(zombie, zombie.getWorld().getGameTime());
        }

        private void reveal(Zombie zombie, long now) {
            zombie.getPersistentDataContainer().set(Keys.MARK, org.bukkit.persistence.PersistentDataType.LONG, now);
            if (!zombie.isInvisible()) { return; }
            zombie.setInvisible(false);
            zombie.getWorld().spawnParticle(Particle.LARGE_SMOKE, zombie.getLocation().add(0, 1, 0), 16, 0.3, 0.6, 0.3, 0.02);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1.4f);
        }
    }

    static final class Screamer implements Ability {
        private final Apocalypse core;
        Screamer(Apocalypse core) { this.core = core; }

        @Override public void tick(Zombie zombie) {
            TypeSettings.Screamer options = core.settings().types().screamer();
            if (!Tags.ready(zombie, Keys.COOLDOWN)) { return; }
            Player target = Support.targetOrNearest(zombie, options.detectDistance());
            if (target == null || target.getLocation().distanceSquared(zombie.getLocation()) > options.detectDistance() * options.detectDistance()) {
                return;
            }
            Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
            scream(zombie, options.radius());
            double squared = options.radius() * options.radius();
            for (Zombie other : core.registry().inWorld(zombie.getWorld())) {
                if (other.equals(zombie) || other.getLocation().distanceSquared(zombie.getLocation()) > squared) { continue; }
                if (!(other.getTarget() instanceof Player)) { other.setTarget(target); }
            }
            core.hordes().screamed(zombie.getWorld());
        }

        private void scream(Zombie zombie, double radius) {
            World world = zombie.getWorld();
            Location head = zombie.getEyeLocation();
            world.playSound(head, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 3f, 0.9f);
            world.playSound(head, Sound.ENTITY_WARDEN_ROAR, 3f, 0.8f);
            for (int index = 0; index < 3; index++) {
                world.spawnParticle(Particle.SHRIEK, head, 1, 0, 0, 0, 0, index * 5);
            }
            world.spawnParticle(Particle.SONIC_BOOM, head, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, head, 16, 0.5, 0.4, 0.5, 0.05);
            double squared = radius * radius;
            for (Player player : world.getPlayers()) {
                if (!Support.fighting(player) || player.getLocation().distanceSquared(zombie.getLocation()) > squared) { continue; }
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 1f);
                core.runLater(20, () -> {
                    if (player.isOnline()) { player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.9f); }
                });
            }
        }
    }

    static final class Healer implements Ability {
        private final Apocalypse core;
        Healer(Apocalypse core) { this.core = core; }

        @Override public void tick(Zombie zombie) {
            if (!Tags.ready(zombie, Keys.COOLDOWN)) { return; }
            TypeSettings.Healer options = core.settings().types().healer();
            double squared = options.radius() * options.radius();
            boolean healed = false;
            for (Zombie other : core.registry().inWorld(zombie.getWorld())) {
                if (other.equals(zombie) || other.getLocation().distanceSquared(zombie.getLocation()) > squared) { continue; }
                if (other.getHealth() >= Attributes.maxHealth(other) - 0.01) { continue; }
                Attributes.heal(other, options.amount());
                other.getWorld().spawnParticle(Particle.HEART, other.getLocation().add(0, other.getHeight() + 0.3, 0), 2, 0.3, 0.2, 0.3, 0);
                healed = true;
            }
            if (healed) {
                zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.8f);
                zombie.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, zombie.getLocation().add(0, 1, 0), 10, 1.5, 0.5, 1.5, 0);
                Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
            }
        }
    }

    static final class Multiplier implements Ability {
        private final Apocalypse core;
        Multiplier(Apocalypse core) { this.core = core; }

        @Override public void spawned(Zombie zombie, SpawnContext context) {
            TypeSettings.Multiplier options = core.settings().types().multiplier();
            Attributes.multiply(zombie, Attribute.SCALE, Keys.MOD_TYPE_SCALE,
                    io.github.beddeak.zombie.logic.AbilityRules.generationFactor(options.scalePerGeneration(), context.generation()));
            Attributes.multiply(zombie, Attribute.MAX_HEALTH, Keys.MOD_TYPE_HEALTH,
                    io.github.beddeak.zombie.logic.AbilityRules.generationFactor(options.healthPerGeneration(), context.generation()));
        }

        @Override public void died(Zombie zombie, EntityDeathEvent event) {
            World world = zombie.getWorld();
            if (!core.active(world)) { return; }
            TypeSettings.Multiplier options = core.settings().types().multiplier();
            int generation = Tags.integer(zombie, Keys.GENERATION, 1);
            Location location = zombie.getLocation();
            int nearby = core.registry().countNear(location, options.nearbyRadius(), ZombieType.MULTIPLIER);
            boolean globalRoom = core.registry().size() < core.settings().spawning().globalCap();
            if (!io.github.beddeak.zombie.logic.AbilityRules.canSplit(generation, options.maxGeneration(), nearby, options.nearbyCap(), globalRoom)) {
                return;
            }
            int count = java.util.concurrent.ThreadLocalRandom.current().nextInt(options.splitMin(), options.splitMax() + 1);
            SpawnContext context = core.context(world).withGeneration(generation + 1)
                    .withHorde(Tags.integer(zombie, Keys.HORDE, 0));
            for (int index = 0; index < count; index++) {
                Zombie child = core.factory().spawn(location, variant(zombie), ZombieType.MULTIPLIER, context);
                double angle = Math.PI * 2 * index / count;
                child.setVelocity(new Vector(Math.cos(angle) * 0.35, 0.35, Math.sin(angle) * 0.35));
                if (zombie.getTarget() != null) { child.setTarget(zombie.getTarget()); }
            }
            world.playSound(location, Sound.ENTITY_ZOMBIE_INFECT, 1f, 1.4f);
            world.spawnParticle(Particle.POOF, location.add(0, 0.8, 0), 12, 0.4, 0.4, 0.4, 0.02);
        }

    }

    static Class<? extends Zombie> variant(Zombie zombie) {
        if (zombie instanceof Drowned) { return Drowned.class; }
        if (zombie instanceof Husk) { return Husk.class; }
        if (zombie instanceof PigZombie) { return PigZombie.class; }
        if (zombie instanceof ZombieVillager) { return ZombieVillager.class; }
        return Zombie.class;
    }
}
