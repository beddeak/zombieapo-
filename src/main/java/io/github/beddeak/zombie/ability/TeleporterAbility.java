package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

final class TeleporterAbility implements Ability {
    private static final int ATTEMPTS = 16;
    private final Apocalypse core;

    TeleporterAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void tick(Zombie zombie) {
        zombie.getWorld().spawnParticle(Particle.PORTAL, zombie.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.4);
        if (!Tags.ready(zombie, Keys.COOLDOWN)) { return; }
        TypeSettings.Teleporter options = core.settings().types().teleporter();
        Player target = Support.target(zombie);
        if (target == null) { return; }
        double distance = target.getLocation().distance(zombie.getLocation());
        if (distance < options.minDistance() || distance > options.maxDistance()) { return; }
        if (blink(zombie, target.getLocation(), backOf(target), 2, 3.5, true)) {
            Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
        }
    }

    @Override public void damaged(Zombie zombie, EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) { return; }
        TypeSettings.Teleporter options = core.settings().types().teleporter();
        if (ThreadLocalRandom.current().nextDouble() >= options.dodgeChance()) { return; }
        if (byEntity.getDamager() instanceof Projectile) {
            event.setCancelled(true);
            core.runLater(1, () -> {
                if (zombie.isValid()) { blink(zombie, zombie.getLocation(), null, 5, 9, false); }
            });
        } else if (byEntity.getDamager() instanceof Player attacker && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            core.runLater(1, () -> {
                if (zombie.isValid() && attacker.isOnline() && attacker.getWorld().equals(zombie.getWorld())) {
                    blink(zombie, attacker.getLocation(), backOf(attacker), 1.5, 3, true);
                }
            });
        }
    }

    private static Vector backOf(Player player) {
        Vector facing = player.getLocation().getDirection().setY(0);
        return facing.lengthSquared() < 1.0E-4 ? null : facing.normalize().multiply(-1);
    }

    private static boolean blink(Zombie zombie, Location center, Vector preferred, double min, double max, boolean face) {
        Location to = spot(center, preferred, min, max);
        if (to == null) { return false; }
        if (face) { to.setDirection(center.toVector().subtract(to.toVector())); }
        World world = zombie.getWorld();
        Location from = zombie.getLocation();
        world.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.6);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.8f);
        if (!zombie.teleport(to)) { return false; }
        world.spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.6);
        world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.8f);
        return true;
    }

    private static Location spot(Location center, Vector preferred, double min, double max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = center.getWorld();
        double baseAngle = preferred == null ? random.nextDouble(Math.PI * 2) : Math.atan2(preferred.getZ(), preferred.getX());
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double spread = preferred == null ? Math.PI : Math.PI / 4 + attempt * Math.PI / 16;
            double angle = baseAngle + (random.nextDouble() * 2 - 1) * spread;
            double distance = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
            for (int dy = 3; dy >= -4; dy--) {
                Block feet = world.getBlockAt(x, center.getBlockY() + dy, z);
                Block head = feet.getRelative(0, 1, 0);
                if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                        && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }
}
