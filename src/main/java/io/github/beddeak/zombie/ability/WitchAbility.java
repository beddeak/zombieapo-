package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.TypeSettings;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

final class WitchAbility implements Ability {
    private static final Component NAME = Component.text("위치 (가까이 가지 마세요)", NamedTextColor.LIGHT_PURPLE);
    private static final Component RAGE_NAME = Component.text("분노한 위치", NamedTextColor.DARK_RED);

    private final Apocalypse core;

    WitchAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void spawned(Zombie zombie, SpawnContext context) {
        zombie.getEquipment().clear();
        zombie.setCanPickupItems(false);
        zombie.customName(NAME);
        zombie.setCustomNameVisible(true);
        zombie.setAware(false);
        Attributes.multiply(zombie, Attribute.MAX_HEALTH, Keys.MOD_TYPE_HEALTH, core.settings().types().witch().healthMultiplier());
    }

    @Override public void tick(Zombie zombie) {
        if (Tags.uuid(zombie, Keys.VICTIM) != null) {
            Player victim = victim(zombie);
            if (victim == null || zombie.getWorld().getGameTime() >= Tags.number(zombie, Keys.MARK, 0)) {
                calm(zombie);
            } else if (zombie.getTarget() != victim) {
                zombie.setTarget(victim);
            }
            return;
        }
        if (zombie.isAware()) { zombie.setAware(false); }
        cry(zombie);
        double distance = core.settings().types().witch().startleDistance();
        for (Player player : zombie.getWorld().getPlayers()) {
            double reach = player.isSneaking() ? distance / 2 : distance;
            if (Support.fighting(player) && player.getLocation().distanceSquared(zombie.getLocation()) <= reach * reach) {
                rage(zombie, player);
                return;
            }
        }
    }

    @Override public void damaged(Zombie zombie, EntityDamageEvent event) {
        if (Tags.uuid(zombie, Keys.VICTIM) != null || !(event instanceof EntityDamageByEntityEvent byEntity)) { return; }
        Player attacker = byEntity.getDamager() instanceof Player player ? player
                : byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker != null && Support.fighting(attacker)) { rage(zombie, attacker); }
    }

    private void rage(Zombie zombie, Player victim) {
        TypeSettings.Witch options = core.settings().types().witch();
        Tags.uuid(zombie, Keys.VICTIM, victim.getUniqueId());
        zombie.getPersistentDataContainer().set(Keys.MARK, PersistentDataType.LONG,
                zombie.getWorld().getGameTime() + options.rageSeconds() * 20L);
        Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_TYPE_SPEED, options.speedMultiplier());
        Attributes.multiply(zombie, Attribute.ATTACK_DAMAGE, Keys.MOD_TYPE_DAMAGE, options.damageMultiplier());
        zombie.setAware(true);
        zombie.setTarget(victim);
        zombie.customName(RAGE_NAME);
        zombie.setGlowing(true);
        Location head = zombie.getEyeLocation();
        zombie.getWorld().playSound(head, Sound.ENTITY_GHAST_SCREAM, 3f, 0.6f);
        zombie.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, head, 6, 0.4, 0.3, 0.4, 0);
        victim.sendActionBar(Msg.alert("위치를 깨웠습니다! 님은 좆됏어요"));
    }

    private static void calm(Zombie zombie) {
        Tags.uuid(zombie, Keys.VICTIM, null);
        zombie.getPersistentDataContainer().remove(Keys.MARK);
        Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_TYPE_SPEED, 1);
        Attributes.multiply(zombie, Attribute.ATTACK_DAMAGE, Keys.MOD_TYPE_DAMAGE, 1);
        zombie.setTarget(null);
        zombie.setAware(false);
        zombie.customName(NAME);
        zombie.setGlowing(false);
    }

    private static Player victim(Zombie zombie) {
        UUID id = Tags.uuid(zombie, Keys.VICTIM);
        Player player = id == null ? null : Bukkit.getPlayer(id);
        return player != null && Support.fighting(player) && player.getWorld().equals(zombie.getWorld()) ? player : null;
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
