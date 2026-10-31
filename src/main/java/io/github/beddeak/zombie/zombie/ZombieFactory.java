package io.github.beddeak.zombie.zombie;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.config.TypeSettings;
import io.github.beddeak.zombie.logic.DayMath;
import io.github.beddeak.zombie.logic.TypePicker;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Husk;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class ZombieFactory {
    private static final int PIGLIN_ANGER = 1_000_000;
    private final Apocalypse core;

    public ZombieFactory(Apocalypse core) {
        this.core = core;
    }

    public Class<? extends Zombie> variantFor(Location location) {
        Block block = location.getBlock();
        if (block.getType() == Material.WATER
                || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged())) {
            return Drowned.class;
        }
        return block.getBiome() == Biome.DESERT ? Husk.class : Zombie.class;
    }

    public static EntityType entityType(Class<? extends Zombie> variant) {
        if (variant == Drowned.class) { return EntityType.DROWNED; }
        if (variant == Husk.class) { return EntityType.HUSK; }
        return EntityType.ZOMBIE;
    }

    public ZombieType pick(Location location, EntityType variant, SpawnContext context, int sprinterBonus) {
        Settings settings = core.settings();
        int reduction = settings.difficulty().specialWeightPerDay() * DayMath.dayFactor(context.day(), settings.difficulty().maxDay())
                + (context.bloodMoon() ? settings.bloodMoon().specialWeightBonus() : 0);
        boolean sky = openSky(location, settings.types().pillar().heightMax() * 2 + 1);
        Map<ZombieType, Integer> weights = TypePicker.weights(settings.types(), context.day(), reduction, sprinterBonus,
                type -> type.fits(variant) && (type != ZombieType.PILLAR || sky)
                        && (type != ZombieType.WITCH || context.horde() == 0));
        return TypePicker.pick(weights, ThreadLocalRandom.current());
    }

    public Zombie spawnRandom(Location location, SpawnContext context, int sprinterBonus) {
        Class<? extends Zombie> variant = variantFor(location);
        ZombieType type = pick(location, entityType(variant), context, sprinterBonus);
        return spawn(location, variant, type, context);
    }

    public Zombie spawn(Location location, ZombieType type, SpawnContext context) {
        Class<? extends Zombie> variant = variantFor(location);
        if (!type.fits(entityType(variant))) { variant = Zombie.class; }
        return spawn(location, variant, type, context);
    }

    public Zombie spawn(Location location, Class<? extends Zombie> variant, ZombieType type, SpawnContext context) {
        World world = location.getWorld();
        return world.spawn(location, variant, CreatureSpawnEvent.SpawnReason.CUSTOM, zombie -> {
            if (type == ZombieType.PILLAR || type == ZombieType.TANK || type == ZombieType.PLAYER) { zombie.setAdult(); }
            apply(zombie, type, context);
        });
    }

    public void apply(Zombie zombie, ZombieType type, SpawnContext context) {
        Tags.type(zombie, type);
        if (context.horde() != 0) {
            zombie.getPersistentDataContainer().set(Keys.HORDE, org.bukkit.persistence.PersistentDataType.INTEGER, context.horde());
        }
        if (context.generation() > 1) {
            zombie.getPersistentDataContainer().set(Keys.GENERATION, org.bukkit.persistence.PersistentDataType.INTEGER,
                    context.generation());
        }
        if (context.owner() != null) { Tags.uuid(zombie, Keys.OWNER, context.owner()); }
        zombie.setShouldBurnInDay(false);
        zombie.setCanPickupItems(false);

        Settings settings = core.settings();
        double baseFollow = Attributes.base(zombie, Attribute.FOLLOW_RANGE, 35);
        Attributes.add(zombie, Attribute.FOLLOW_RANGE, Keys.MOD_FOLLOW_RANGE, Math.max(0, settings.zombie().followRange() - baseFollow));
        int factor = DayMath.dayFactor(context.day(), settings.difficulty().maxDay());
        Attributes.multiply(zombie, Attribute.MAX_HEALTH, Keys.MOD_DAY_HEALTH, 1 + settings.difficulty().healthPerDay() * factor);
        Attributes.multiply(zombie, Attribute.ATTACK_DAMAGE, Keys.MOD_DAY_DAMAGE, 1 + settings.difficulty().damagePerDay() * factor);
        Attributes.multiply(zombie, Attribute.MAX_HEALTH, Keys.MOD_BLOOD_MOON_HEALTH,
                context.bloodMoon() ? settings.bloodMoon().healthMultiplier() : 1);
        Attributes.multiply(zombie, Attribute.MOVEMENT_SPEED, Keys.MOD_BLOOD_MOON_SPEED,
                context.bloodMoon() ? settings.bloodMoon().speedMultiplier() : 1);
        if (zombie instanceof PigZombie piglin) {
            piglin.setAngry(true);
            piglin.setAnger(PIGLIN_ANGER);
        }
        if (settings.zombie().showTypeName() && type.random()) {
            zombie.customName(Component.text("[" + type.displayName() + "] 좀비", NamedTextColor.RED));
            zombie.setCustomNameVisible(true);
        }
        core.abilities().get(type).spawned(zombie, context);
        Attributes.fillHealth(zombie);
        if (type == ZombieType.PILLAR) { core.runLater(1, () -> buildTower(zombie, context)); }
    }

    public boolean openSky(Location location, int height) {
        World world = location.getWorld();
        int y = location.getBlockY();
        return y + height < world.getMaxHeight()
                && world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), HeightMap.MOTION_BLOCKING) < y;
    }

    private void buildTower(Zombie bottom, SpawnContext context) {
        if (!bottom.isValid() || bottom.isDead()) { return; }
        TypeSettings.Pillar pillar = core.settings().types().pillar();
        int height = ThreadLocalRandom.current().nextInt(pillar.heightMin(), pillar.heightMax() + 1);
        if (!openSky(bottom.getLocation(), height * 2 + 1)) { return; }
        Zombie below = bottom;
        for (int level = 1; level < height; level++) {
            Zombie rider = spawn(bottom.getLocation(), Zombie.class, ZombieType.DEFAULT, context);
            rider.setAdult();
            if (!below.addPassenger(rider)) {
                rider.remove();
                return;
            }
            below = rider;
        }
    }
}
