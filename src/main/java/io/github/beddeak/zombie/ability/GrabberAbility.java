package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Blitzcrank style grab: the zombie stops and raises its arms, then throws a hook in a straight line at where the
 * player was when the wind-up ended. Blocks stop the hook; the first player it touches is yanked to the zombie.
 */
final class GrabberAbility implements Ability {
    private static final int MAX_PULL_TICKS = 12;
    private final Apocalypse core;
    private final Set<UUID> busy = new HashSet<>();

    GrabberAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void tick(Zombie zombie) {
        if (busy.contains(zombie.getUniqueId()) || !Tags.ready(zombie, Keys.COOLDOWN)) { return; }
        TypeSettings.Grabber options = core.settings().types().grabber();
        Player target = Support.target(zombie);
        if (target == null) { return; }
        double distance = target.getLocation().distance(zombie.getLocation());
        if (distance < 4 || distance > options.range() || !zombie.hasLineOfSight(target)) { return; }
        Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
        busy.add(zombie.getUniqueId());
        core.effects().add(new Hook(zombie, target, options));
    }

    private enum Phase { WINDUP, FLIGHT, PULL }

    private final class Hook implements Effects.Effect {
        private final Zombie zombie;
        private final Player target;
        private final TypeSettings.Grabber options;
        private Phase phase = Phase.WINDUP;
        private int ticks;
        private Location head;
        private Vector direction;
        private double travelled;
        private ItemDisplay display;
        private Player caught;

        Hook(Zombie zombie, Player target, TypeSettings.Grabber options) {
            this.zombie = zombie;
            this.target = target;
            this.options = options;
            zombie.setAware(false);
            zombie.setAggressive(true);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_CHAIN_HIT, 1.2f, 0.6f);
        }

        @Override public World world() { return zombie.getWorld(); }

        @Override public boolean tick() {
            if (!zombie.isValid() || zombie.isDead() || !core.active(zombie.getWorld())) { return false; }
            ticks++;
            return switch (phase) {
                case WINDUP -> windup();
                case FLIGHT -> flight();
                case PULL -> pull();
            };
        }

        private boolean windup() {
            zombie.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hand(), 3, 0.15, 0.15, 0.15, 0.02);
            if (ticks < options.windupTicks()) { return true; }
            if (!Support.fighting(target) || !target.getWorld().equals(zombie.getWorld())) { return false; }
            head = hand();
            direction = target.getEyeLocation().subtract(0, 0.3, 0).toVector().subtract(head.toVector());
            if (direction.lengthSquared() < 1.0E-4) { return false; }
            direction.normalize();
            display = head.getWorld().spawn(head, ItemDisplay.class, entity -> {
                entity.setItemStack(ItemStack.of(Material.TRIPWIRE_HOOK));
                entity.setPersistent(false);
                entity.setTeleportDuration(1);
                Tags.helper(entity, Tags.HOOK);
            });
            zombie.getWorld().playSound(head, Sound.BLOCK_CHAIN_FALL, 1.5f, 0.8f);
            phase = Phase.FLIGHT;
            return true;
        }

        private boolean flight() {
            double step = options.hookSpeed();
            RayTraceResult blocked = head.getWorld().rayTraceBlocks(head, direction, step, FluidCollisionMode.NEVER, true);
            if (blocked != null) {
                head.getWorld().playSound(head, Sound.BLOCK_CHAIN_BREAK, 1f, 1.2f);
                return false;
            }
            head.add(direction.clone().multiply(step));
            travelled += step;
            display.teleport(head);
            chain(head);
            for (Player player : head.getWorld().getNearbyEntitiesByType(Player.class, head, 1.5)) {
                if (Support.fighting(player) && player.getBoundingBox().expand(0.4).contains(head.toVector())) {
                    caught = player;
                    phase = Phase.PULL;
                    ticks = 0;
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.6f);
                    return true;
                }
            }
            return travelled < options.range();
        }

        private boolean pull() {
            if (!Support.fighting(caught) || !caught.getWorld().equals(zombie.getWorld())) { return false; }
            Location goal = zombie.getLocation().add(Support.flatDirection(zombie.getLocation(), caught.getLocation()));
            Vector toGoal = goal.toVector().subtract(caught.getLocation().toVector());
            double distance = toGoal.length();
            caught.setFallDistance(0);
            display.teleport(caught.getEyeLocation());
            chain(caught.getEyeLocation());
            if (distance < 1.5 || ticks > MAX_PULL_TICKS) {
                caught.setVelocity(new Vector());
                caught.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 2));
                if (options.damage() > 0) { caught.damage(options.damage(), zombie); }
                return false;
            }
            Vector velocity = toGoal.normalize().multiply(Math.min(1.6, distance * 0.5 + 0.6));
            velocity.setY(Math.max(-0.5, Math.min(0.6, velocity.getY() + 0.15)));
            caught.setVelocity(velocity);
            return true;
        }

        private Location hand() {
            Location eye = zombie.getEyeLocation();
            return eye.add(eye.getDirection().multiply(0.4)).subtract(0, 0.3, 0);
        }

        private void chain(Location to) {
            Location from = hand();
            Vector line = to.toVector().subtract(from.toVector());
            double length = line.length();
            if (length < 0.1) { return; }
            line.normalize();
            for (double offset = 0; offset < length; offset += 0.6) {
                from.getWorld().spawnParticle(Particle.CRIT, from.clone().add(line.clone().multiply(offset)), 1, 0, 0, 0, 0);
            }
        }

        @Override public void end() {
            busy.remove(zombie.getUniqueId());
            if (display != null && display.isValid()) { display.remove(); }
            if (zombie.isValid()) { zombie.setAware(true); }
        }
    }
}
