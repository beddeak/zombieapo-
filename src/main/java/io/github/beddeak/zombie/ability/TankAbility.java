package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.logic.DayMath;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class TankAbility implements Ability {
    private static final double SLAM_RADIUS = 4.5;
    private static final int MAX_SMASH = 64;
    private final Apocalypse core;
    private final Set<UUID> throwing = new HashSet<>();
    private boolean special;

    TankAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void spawned(Zombie zombie, SpawnContext context) {
        Settings.Tank options = core.settings().tank();
        zombie.setAdult();
        zombie.getEquipment().clear();
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setPersistent(true);
        Attributes.add(zombie, Attribute.MAX_HEALTH, Keys.MOD_TYPE_HEALTH,
                options.health() - Attributes.base(zombie, Attribute.MAX_HEALTH, 20));
        Attributes.add(zombie, Attribute.ATTACK_DAMAGE, Keys.MOD_TYPE_DAMAGE,
                options.damage() - Attributes.base(zombie, Attribute.ATTACK_DAMAGE, 3));
        Attributes.multiply(zombie, Attribute.SCALE, Keys.MOD_TYPE_SCALE, options.scale());
        Attributes.add(zombie, Attribute.KNOCKBACK_RESISTANCE, Keys.MOD_TYPE_KNOCKBACK, 1.0);
        Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_TYPE_SPEED, options.speedMultiplier());
        zombie.customName(Component.text("탱크", NamedTextColor.DARK_RED));
        zombie.setCustomNameVisible(true);
    }

    @Override public void tick(Zombie zombie) {
        Settings.Tank options = core.settings().tank();
        Player target = Support.targetOrNearest(zombie, 48);
        if (target != null && zombie.getTarget() != target) { zombie.setTarget(target); }
        if (target != null && options.breakBlocks()) { smash(zombie, target, options); }
        boolean phaseTwo = zombie.getPersistentDataContainer().has(Keys.PHASE_TWO, PersistentDataType.BYTE);
        if (!phaseTwo && zombie.getHealth() <= Attributes.maxHealth(zombie) / 2) {
            zombie.getPersistentDataContainer().set(Keys.PHASE_TWO, PersistentDataType.BYTE, (byte) 1);
            Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_PHASE_TWO, 1.25);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3f, 0.5f);
            Msg.title(zombie.getWorld().getPlayers(), "탱크가 분노했습니다!", NamedTextColor.RED, "더 빨라지고 바위를 자주 던집니다");
            phaseTwo = true;
        }
        if (target == null || throwing.contains(zombie.getUniqueId())) { return; }
        List<Player> close = zombie.getWorld().getNearbyEntitiesByType(Player.class, zombie.getLocation(), SLAM_RADIUS)
                .stream().filter(Support::fighting).toList();
        if (close.size() >= 2 && Tags.ready(zombie, Keys.COOLDOWN_2)) {
            Tags.cooldown(zombie, Keys.COOLDOWN_2, options.slamCooldownTicks());
            slam(zombie, close, options);
            return;
        }
        double distance = target.getLocation().distance(zombie.getLocation());
        if (distance >= 8 && distance <= 30 && Tags.ready(zombie, Keys.COOLDOWN) && zombie.hasLineOfSight(target)) {
            Tags.cooldown(zombie, Keys.COOLDOWN, phaseTwo ? options.rockCooldownTicks() * 6L / 10 : options.rockCooldownTicks());
            throwing.add(zombie.getUniqueId());
            core.effects().add(new Rock(zombie, target));
        }
    }

    private void smash(Zombie zombie, Player target, Settings.Tank options) {
        World world = zombie.getWorld();
        Location at = zombie.getLocation();
        Vector direction = Support.flatDirection(at, target.getLocation());
        BoundingBox box = zombie.getBoundingBox().expand(0.3, 0, 0.3).expandDirectional(direction.multiply(1.0));
        int bottom = (int) Math.floor(box.getMinY() + 0.05) + (target.getLocation().getY() > at.getY() + 0.8 ? 1 : 0);
        List<Block> blocks = new ArrayList<>();
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - 1.0E-6) && blocks.size() < MAX_SMASH; x++) {
            for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - 1.0E-6) && blocks.size() < MAX_SMASH; z++) {
                for (int y = bottom; y <= (int) Math.floor(box.getMaxY() - 1.0E-6) && blocks.size() < MAX_SMASH; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    float hardness = block.getType().getHardness();
                    if (block.getType().isAir() || block.isLiquid() || hardness < 0 || hardness > options.breakMaxHardness()) { continue; }
                    blocks.add(block);
                }
            }
        }
        core.ending().protect(world, blocks);
        blocks.removeIf(core.assembly()::protects);
        if (blocks.isEmpty()) { return; }
        for (Block block : blocks) {
            if (block.getState(false) instanceof Container) {
                block.breakNaturally();
                continue;
            }
            world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, block.getBlockData());
            block.setType(Material.AIR);
        }
        world.playSound(at, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2f, 0.5f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
    }

    @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
        if (special) { return; }
        Vector velocity = Support.flatDirection(zombie.getLocation(), victim.getLocation()).multiply(2.2).setY(0.95);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
        Support.launchLater(core, victim, velocity);
    }

    @Override public void damaged(Zombie zombie, EntityDamageEvent event) {
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA ->
                    event.setDamage(event.getDamage() * core.settings().tank().fireDamageMultiplier());
            default -> { }
        }
    }

    @Override public void died(Zombie zombie, EntityDeathEvent event) {
        Settings.Tank options = core.settings().tank();
        for (Settings.Loot loot : options.loot()) { event.getDrops().add(ItemStack.of(loot.material(), loot.amount())); }
        event.setDroppedExp(options.exp());
        World world = zombie.getWorld();
        Player killer = zombie.getKiller();
        world.playSound(zombie.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        Msg.title(world.getPlayers(), "탱크를 쓰러뜨렸습니다!", NamedTextColor.GOLD, killer == null ? "" : killer.getName() + "의 마무리");
        if (core.active(world)) { core.announce(world, Msg.good("탱크를 쓰러뜨렸습니다! 보상이 떨어졌습니다.")); }
    }

    void landed(Projectile projectile, ProjectileHitEvent event) {
        projectile.remove();
        Location location = event.getHitEntity() != null ? event.getHitEntity().getLocation() : projectile.getLocation();
        World world = location.getWorld();
        world.spawnParticle(Particle.EXPLOSION, location, 2, 0.4, 0.4, 0.4, 0);
        world.spawnParticle(Particle.BLOCK, location, 30, 0.8, 0.5, 0.8, 0, Material.COBBLESTONE.createBlockData());
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.4f);
        if (!core.active(world)) { return; }
        Zombie tank = projectile.getShooter() instanceof Zombie zombie && zombie.isValid() ? zombie : null;
        double damage = core.settings().tank().rockDamage() * dayDamage(world);
        for (Player player : world.getNearbyEntitiesByType(Player.class, location, 2.5)) {
            if (!Support.fighting(player)) { continue; }
            hurt(player, damage, tank);
            player.setVelocity(Support.flatDirection(location, player.getLocation()).multiply(0.9).setY(0.6));
        }
    }

    private void slam(Zombie zombie, List<Player> players, Settings.Tank options) {
        Location center = zombie.getLocation();
        World world = zombie.getWorld();
        zombie.swingMainHand();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION, center, 5, 1.5, 0.2, 1.5, 0);
        for (int index = 0; index < 24; index++) {
            double angle = Math.PI * 2 * index / 24;
            world.spawnParticle(Particle.CLOUD, center.clone().add(Math.cos(angle) * 3, 0.2, Math.sin(angle) * 3), 1, 0, 0, 0, 0.05);
        }
        double damage = options.slamDamage() * dayDamage(world);
        for (Player player : players) {
            hurt(player, damage, zombie);
            player.setVelocity(Support.flatDirection(center, player.getLocation()).multiply(0.9).setY(1.0));
        }
    }

    private void hurt(Player player, double damage, Zombie tank) {
        if (damage <= 0) { return; }
        special = true;
        try {
            if (tank != null) {
                player.damage(damage, tank);
            } else {
                player.damage(damage);
            }
        } finally {
            special = false;
        }
    }

    private double dayDamage(World world) {
        Settings.Difficulty difficulty = core.settings().difficulty();
        return 1 + difficulty.damagePerDay() * DayMath.dayFactor(core.day(world), difficulty.maxDay());
    }

    private final class Rock implements Effects.Effect {
        private static final int WINDUP = 20;
        private final Zombie tank;
        private final Player target;
        private final ItemDisplay boulder;
        private int ticks;

        Rock(Zombie tank, Player target) {
            this.tank = tank;
            this.target = target;
            tank.setAware(false);
            boulder = tank.getWorld().spawn(above(), ItemDisplay.class, display -> {
                display.setItemStack(ItemStack.of(Material.COBBLESTONE));
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f, 1.6f, 1.6f),
                        new AxisAngle4f()));
                display.setTeleportDuration(1);
                display.setPersistent(false);
                Tags.helper(display, Tags.ROCK_DISPLAY);
            });
            tank.getWorld().playSound(tank.getLocation(), Sound.BLOCK_STONE_BREAK, 2f, 0.5f);
        }

        @Override public World world() { return tank.getWorld(); }

        @Override public boolean tick() {
            if (!tank.isValid() || tank.isDead()) { return false; }
            boulder.teleport(above());
            if (++ticks < WINDUP) { return true; }
            if (!Support.fighting(target) || !target.getWorld().equals(tank.getWorld())) { return false; }
            Location from = above();
            tank.swingMainHand();
            Vector velocity = Support.lob(from, target.getLocation().add(0, 0.5, 0), 1.5);
            tank.getWorld().spawn(from, Snowball.class, ball -> {
                ball.setShooter(tank);
                ball.setVelocity(velocity);
                ball.setItem(ItemStack.of(Material.COBBLESTONE));
                Tags.helper(ball, Tags.ROCK);
            });
            tank.getWorld().playSound(from, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 1.2f);
            return false;
        }

        private Location above() {
            return tank.getLocation().add(0, tank.getHeight() + 0.6, 0);
        }

        @Override public void end() {
            throwing.remove(tank.getUniqueId());
            if (boulder.isValid()) { boulder.remove(); }
            if (tank.isValid()) { tank.setAware(true); }
        }
    }
}
