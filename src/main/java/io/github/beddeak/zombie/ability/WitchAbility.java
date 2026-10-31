package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.logic.AbilityRules;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

final class WitchAbility implements Ability {
    private static final Component NAME = Component.text("위치 (쳐다보지 마세요)", NamedTextColor.LIGHT_PURPLE);
    private static final Component SCREAM_NAME = Component.text("비명 지르는 위치", NamedTextColor.RED);
    private static final Component RAGE_NAME = Component.text("분노한 위치", NamedTextColor.DARK_RED);
    private static final double LOOK_RADIUS = 0.9;

    private final Apocalypse core;

    WitchAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void spawned(Zombie zombie, SpawnContext context) {
        zombie.getEquipment().clear();
        zombie.setCanPickupItems(false);
        zombie.customName(NAME);
        zombie.setCustomNameVisible(true);
        crouch(zombie, true);
        zombie.setAware(false);
        Attributes.multiply(zombie, Attribute.MAX_HEALTH, Keys.MOD_TYPE_HEALTH, core.settings().types().witch().healthMultiplier());
    }

    @Override public void tick(Zombie zombie) {
        if (Tags.uuid(zombie, Keys.VICTIM) != null) {
            Player victim = victim(zombie);
            long now = zombie.getWorld().getGameTime();
            if (victim == null || now >= Tags.number(zombie, Keys.MARK, 0)) {
                calm(zombie);
                return;
            }
            long windUp = Tags.number(zombie, Keys.WIND_UP, 0);
            if (windUp > 0 && now < windUp) {
                face(zombie, victim);
                scream(zombie);
            } else if (windUp > 0) {
                zombie.getPersistentDataContainer().remove(Keys.WIND_UP);
                chase(zombie, victim);
            } else if (zombie.getTarget() != victim) {
                zombie.setTarget(victim);
            }
            return;
        }
        if (zombie.isAware()) { zombie.setAware(false); }
        if (!zombie.isSneaking()) { crouch(zombie, true); }
        cry(zombie);
        TypeSettings.Witch options = core.settings().types().witch();
        Location at = zombie.getLocation();
        for (Player player : zombie.getWorld().getPlayers()) {
            if (!Support.fighting(player)) { continue; }
            Location location = player.getLocation();
            if (!AbilityRules.inFront(at.getYaw(), location.getX() - at.getX(), location.getZ() - at.getZ(), options.viewAngle())) { continue; }
            double reach = player.isSneaking() ? options.startleDistance() / 2 : options.startleDistance();
            if (location.distanceSquared(at) <= reach * reach || staredAt(zombie, player, options.lookDistance())) {
                provoke(zombie, player);
                return;
            }
        }
    }

    @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
        if (Tags.uuid(zombie, Keys.VICTIM) != null && !zombie.getPersistentDataContainer().has(Keys.WIND_UP)) {
            event.setDamage(core.settings().types().witch().hitDamage());
        }
    }

    @Override public void damaged(Zombie zombie, EntityDamageEvent event) {
        if (Tags.uuid(zombie, Keys.VICTIM) != null || !(event instanceof EntityDamageByEntityEvent byEntity)) { return; }
        Player attacker = byEntity.getDamager() instanceof Player player ? player
                : byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker != null && Support.fighting(attacker)) { provoke(zombie, attacker); }
    }

    private static boolean staredAt(Zombie zombie, Player player, double lookDistance) {
        if (lookDistance <= 0) { return false; }
        Location eye = player.getEyeLocation();
        Vector toHead = zombie.getEyeLocation().toVector().subtract(eye.toVector());
        double distanceSquared = toHead.lengthSquared();
        if (distanceSquared > lookDistance * lookDistance) { return false; }
        double along = toHead.dot(eye.getDirection());
        if (along <= 0 || distanceSquared - along * along > LOOK_RADIUS * LOOK_RADIUS) { return false; }
        return player.hasLineOfSight(zombie);
    }

    private void provoke(Zombie zombie, Player victim) {
        TypeSettings.Witch options = core.settings().types().witch();
        long now = zombie.getWorld().getGameTime();
        Tags.uuid(zombie, Keys.VICTIM, victim.getUniqueId());
        zombie.getPersistentDataContainer().set(Keys.WIND_UP, PersistentDataType.LONG, now + options.screamSeconds() * 20L);
        zombie.getPersistentDataContainer().set(Keys.MARK, PersistentDataType.LONG,
                now + (options.screamSeconds() + options.rageSeconds()) * 20L);
        crouch(zombie, false);
        zombie.setGlowing(true);
        zombie.customName(SCREAM_NAME);
        face(zombie, victim);
        scream(zombie);
        victim.sendActionBar(Msg.alert("위치를 깨웠습니다! 님은 좆됏어요"));
        victim.playSound(victim.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 1.2f);
    }

    private void chase(Zombie zombie, Player victim) {
        Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_TYPE_SPEED, core.settings().types().witch().speedMultiplier());
        zombie.setAware(true);
        zombie.setTarget(victim);
        zombie.customName(RAGE_NAME);
        Location head = zombie.getEyeLocation();
        zombie.getWorld().playSound(head, Sound.ENTITY_GHAST_SCREAM, 3f, 0.5f);
        zombie.getWorld().playSound(head, Sound.ENTITY_ENDERMAN_SCREAM, 3f, 0.6f);
        zombie.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head, 6, 0.4, 0.3, 0.4, 0);
    }

    private static void calm(Zombie zombie) {
        Tags.uuid(zombie, Keys.VICTIM, null);
        zombie.getPersistentDataContainer().remove(Keys.MARK);
        zombie.getPersistentDataContainer().remove(Keys.WIND_UP);
        Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_TYPE_SPEED, 1);
        Attributes.multiply(zombie, Attribute.ATTACK_DAMAGE, Keys.MOD_TYPE_DAMAGE, 1);
        zombie.setTarget(null);
        zombie.setAware(false);
        zombie.customName(NAME);
        zombie.setGlowing(false);
        crouch(zombie, true);
    }

    private static void crouch(Zombie zombie, boolean crouch) {
        zombie.setSneaking(crouch);
        zombie.setPose(crouch ? Pose.SNEAKING : Pose.STANDING, crouch);
    }

    private static void face(Zombie zombie, Player victim) {
        Location look = zombie.getLocation().setDirection(victim.getEyeLocation().toVector().subtract(zombie.getEyeLocation().toVector()));
        zombie.setRotation(look.getYaw(), look.getPitch());
    }

    private static Player victim(Zombie zombie) {
        UUID id = Tags.uuid(zombie, Keys.VICTIM);
        Player player = id == null ? null : Bukkit.getPlayer(id);
        return player != null && Support.fighting(player) && player.getWorld().equals(zombie.getWorld()) ? player : null;
    }

    private static void scream(Zombie zombie) {
        World world = zombie.getWorld();
        Location head = zombie.getEyeLocation();
        float pitch = 0.5f + ThreadLocalRandom.current().nextFloat() * 0.25f;
        world.playSound(head, Sound.ENTITY_GHAST_SCREAM, 3f, pitch);
        world.playSound(head, Sound.ENTITY_GHAST_HURT, 2f, pitch + 0.1f);
        world.spawnParticle(Particle.FALLING_WATER, head, 6, 0.2, 0.1, 0.2, 0);
        world.spawnParticle(Particle.ANGRY_VILLAGER, head.clone().add(0, 0.5, 0), 2, 0.3, 0.2, 0.3, 0);
    }

    private static void cry(Zombie zombie) {
        Location head = zombie.getEyeLocation();
        zombie.getWorld().spawnParticle(Particle.FALLING_WATER, head, 2, 0.15, 0.05, 0.15, 0);
        zombie.getWorld().spawnParticle(Particle.WITCH, head.clone().add(0, 0.4, 0), 4, 0.3, 0.2, 0.3, 0);
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            zombie.getWorld().playSound(head, Sound.ENTITY_GHAST_AMBIENT, 1f, 1.6f);
        }
    }
}
