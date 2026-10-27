package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Spits an acid glob from range; where it lands an acid puddle poisons and slows players. */
final class SpitterAbility implements Ability {
    private final Apocalypse core;

    SpitterAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void tick(Zombie zombie) {
        if (!Tags.ready(zombie, Keys.COOLDOWN)) { return; }
        TypeSettings.Spitter options = core.settings().types().spitter();
        Player target = Support.target(zombie);
        if (target == null) { return; }
        double distance = target.getLocation().distance(zombie.getLocation());
        if (distance < options.minDistance() || distance > options.maxDistance() || !zombie.hasLineOfSight(target)) { return; }
        Tags.cooldown(zombie, Keys.COOLDOWN, options.cooldownTicks());
        Location from = zombie.getEyeLocation();
        zombie.launchProjectile(Snowball.class, Support.lob(from, target.getLocation().add(0, 0.6, 0), 1.2), ball -> {
            ball.setItem(ItemStack.of(Material.SLIME_BALL));
            Tags.helper(ball, Tags.SPIT);
        });
        zombie.getWorld().playSound(from, Sound.ENTITY_LLAMA_SPIT, 1.5f, 0.7f);
    }

    /** Handles a landed spit glob. */
    void landed(Projectile projectile, ProjectileHitEvent event) {
        projectile.remove();
        if (!core.active(projectile.getWorld())) { return; }
        TypeSettings.Spitter options = core.settings().types().spitter();
        Zombie shooter = projectile.getShooter() instanceof Zombie zombie ? zombie : null;
        Location location;
        if (event.getHitEntity() != null) {
            location = event.getHitEntity().getLocation();
            if (event.getHitEntity() instanceof Player player && Support.fighting(player) && options.hitDamage() > 0) {
                if (shooter != null && shooter.isValid()) {
                    player.damage(options.hitDamage(), shooter);
                } else {
                    player.damage(options.hitDamage());
                }
            }
        } else if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            location = event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
        } else {
            location = projectile.getLocation();
        }
        location.getWorld().spawn(location, AreaEffectCloud.class, cloud -> {
            cloud.setRadius((float) options.puddleRadius());
            cloud.setDuration(options.puddleSeconds() * 20);
            cloud.setWaitTime(0);
            cloud.setReapplicationDelay(20);
            cloud.setRadiusPerTick(0);
            cloud.setColor(Color.LIME);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 1), true);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0), true);
            if (shooter != null) { cloud.setSource(shooter); }
            Tags.helper(cloud, Tags.ACID);
        });
        location.getWorld().playSound(location, Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.6f);
    }
}
