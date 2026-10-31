package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.zombie.ZombieType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;

final class Support {
    private Support() {}

    static boolean fighting(Player player) {
        GameMode mode = player.getGameMode();
        return player.isOnline() && !player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    static Player target(Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        if (target instanceof Player player && fighting(player) && player.getWorld().equals(zombie.getWorld())) {
            return player;
        }
        return null;
    }

    static Player nearest(Location center, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player player : center.getWorld().getPlayers()) {
            if (!fighting(player)) { continue; }
            double distance = player.getLocation().distanceSquared(center);
            if (distance <= bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    static Player targetOrNearest(Zombie zombie, double radius) {
        Player target = target(zombie);
        return target != null ? target : nearest(zombie.getLocation(), radius);
    }

    static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static Vector flatDirection(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() < 1.0E-4) { direction = from.getDirection().setY(0); }
        if (direction.lengthSquared() < 1.0E-4) { direction = new Vector(1, 0, 0); }
        return direction.normalize();
    }

    static void launchLater(Apocalypse core, Player player, Vector velocity) {
        core.runLater(1, () -> {
            if (player.isOnline() && !player.isDead()) { player.setVelocity(velocity); }
        });
    }

    static Vector lob(Location from, Location to, double speed) {
        Vector difference = to.toVector().subtract(from.toVector());
        double flightTicks = Math.max(5, difference.length() / speed);
        return difference.multiply(1 / flightTicks).add(new Vector(0, 0.5 * 0.03 * flightTicks, 0));
    }

    static void explode(Apocalypse core, ZombieType type, Location location, double power, boolean breakBlocks) {
        World world = location.getWorld();
        if (power <= 0 || !core.active(world)) { return; }
        core.zombieBlast(type, () -> world.createExplosion(location, (float) power, false, breakBlocks, null));
    }
}
