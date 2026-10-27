package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.logic.AbilityRules;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * A baby zombie that jumps onto a player's head, blinds them and keeps biting. It lets go when another player hits
 * it, when the rider sneaks three times quickly, or after a few seconds.
 */
public final class JockeyAbility implements Ability {
    private final Apocalypse core;

    JockeyAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void spawned(Zombie zombie, SpawnContext context) {
        zombie.setBaby();
    }

    @Override public void tick(Zombie zombie) {
        if (zombie.getVehicle() != null || !Tags.ready(zombie, Keys.COOLDOWN)) { return; }
        TypeSettings.Jockey options = core.settings().types().jockey();
        Player target = Support.target(zombie);
        if (target == null || !target.getPassengers().isEmpty()) { return; }
        if (target.getLocation().distanceSquared(zombie.getLocation()) > options.leapDistance() * options.leapDistance()) { return; }
        if (core.effects().of(Ride.class).anyMatch(ride -> ride.player.equals(target))) { return; }
        Tags.cooldown(zombie, Keys.COOLDOWN, options.rideTicks() + options.cooldownTicks());
        if (!target.addPassenger(zombie)) { return; }
        PotionEffectType effect = options.darkness() ? PotionEffectType.DARKNESS : PotionEffectType.BLINDNESS;
        core.effects().add(new Ride(zombie, target, effect, target.getPotionEffect(effect), options));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.2f, 1.3f);
        target.sendActionBar(Msg.alert("자키가 머리에 올라탔습니다! 웅크리기를 빠르게 3번 눌러 떨쳐내세요."));
    }

    /** A sneak press by a player; three in a row throw off their rider. */
    public void sneaked(Player player) {
        core.effects().of(Ride.class).filter(ride -> ride.player.equals(player)).findFirst().ifPresent(ride -> {
            if (ride.shake.press(player.getWorld().getGameTime())) { core.effects().end(ride); }
        });
    }

    /** Another player hit this jockey: it lets go. */
    public void struck(Zombie zombie, Player attacker) {
        core.effects().of(Ride.class).filter(ride -> ride.jockey.equals(zombie) && !ride.player.equals(attacker))
                .findFirst().ifPresent(ride -> core.effects().end(ride));
    }

    private final class Ride implements Effects.Effect {
        private final Zombie jockey;
        private final Player player;
        private final PotionEffectType effect;
        private final PotionEffect previous;
        private final TypeSettings.Jockey options;
        private final AbilityRules.Shake shake = new AbilityRules.Shake(3, 30);
        private int ticks;

        Ride(Zombie jockey, Player player, PotionEffectType effect, PotionEffect previous, TypeSettings.Jockey options) {
            this.jockey = jockey;
            this.player = player;
            this.effect = effect;
            this.previous = previous;
            this.options = options;
            jockey.setAware(false);
        }

        @Override public World world() { return player.getWorld(); }

        @Override public boolean tick() {
            if (!jockey.isValid() || jockey.isDead() || !Support.fighting(player) || !player.getPassengers().contains(jockey)) {
                return false;
            }
            if (ticks % 20 == 0) {
                player.addPotionEffect(new PotionEffect(effect, 50, 0, false, false, true));
                if (ticks > 0 && options.damage() > 0) { player.damage(options.damage(), jockey); }
            }
            return ++ticks < options.rideTicks();
        }

        @Override public void end() {
            if (player.isOnline()) {
                player.removePassenger(jockey);
                player.removePotionEffect(effect);
                if (previous != null) { player.addPotionEffect(previous); }
            }
            if (jockey.isValid() && !jockey.isDead()) {
                jockey.setAware(true);
                Vector push = player.isOnline() ? Support.flatDirection(player.getLocation(), jockey.getLocation()) : new Vector(1, 0, 0);
                jockey.setVelocity(push.multiply(0.6).setY(0.4));
                jockey.getWorld().spawnParticle(Particle.POOF, jockey.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }
}
