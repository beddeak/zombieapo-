package io.github.beddeak.zombie.config;

import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Weights and per-type options under {@code types:}. */
public record TypeSettings(
        Map<ZombieType, Spawn> spawns,
        Sprinter sprinter,
        Boomer boomer,
        Bomber bomber,
        Thrower thrower,
        Toxic toxic,
        Frost frost,
        Jumper jumper,
        Ninja ninja,
        Grabber grabber,
        Multiplier multiplier,
        Screamer screamer,
        Healer healer,
        Spitter spitter,
        Pillar pillar,
        Vampire vampire,
        Thief thief,
        Necromancer necromancer,
        Jockey jockey,
        Witch witch) {

    public record Spawn(int weight, int minDay) {}
    public record Sprinter(double speedMultiplier) {}
    public record Boomer(double power, boolean breakBlocks, int fuseTicks) {}
    public record Bomber(double triggerDistance, int fuseTicks, double power, boolean breakBlocks) {}
    public record Thrower(double horizontal, double vertical) {}
    public record Toxic(int poisonSeconds, int hungerSeconds) {}
    public record Frost(int freezeTicks, int slownessSeconds) {}
    public record Jumper(int cooldownTicks, double minDistance, double maxDistance) {}
    public record Ninja(double revealDistance, int hideAfterTicks) {}
    public record Grabber(double range, double hookSpeed, int windupTicks, int cooldownTicks, double damage) {}
    public record Multiplier(int splitMin, int splitMax, int maxGeneration, double scalePerGeneration,
                             double healthPerGeneration, int nearbyCap, double nearbyRadius) {}
    public record Screamer(double detectDistance, double radius, int cooldownTicks) {}
    public record Healer(double radius, double amount, int cooldownTicks) {}
    public record Spitter(double minDistance, double maxDistance, int cooldownTicks, int puddleSeconds,
                          double puddleRadius, double hitDamage) {}
    public record Pillar(int heightMin, int heightMax) {}
    public record Vampire(double healRatio) {}
    public record Thief(int fleeSeconds) {}
    public record Necromancer(double range, int cooldownTicks, int raiseMin, int raiseMax, int maxAlive) {}
    public record Jockey(double leapDistance, int cooldownTicks, int rideTicks, double damage, boolean darkness) {}
    public record Witch(double startleDistance, double speedMultiplier, double damageMultiplier, double healthMultiplier,
                        int rageSeconds) {}

    private static final int MAX_TICKS = 72_000;

    public Spawn spawn(ZombieType type) {
        return spawns.getOrDefault(type, new Spawn(0, 0));
    }

    static TypeSettings read(ConfigReader types, java.util.List<String> warnings) {
        Map<ZombieType, Spawn> spawns = new EnumMap<>(ZombieType.class);
        spawn(types, ZombieType.DEFAULT, 40, 0, spawns);
        ConfigReader c = spawn(types, ZombieType.SPRINTER, 8, 0, spawns);
        Sprinter sprinter = new Sprinter(c.number("speed-multiplier", 1.6, 0.1, 5));
        c = spawn(types, ZombieType.BOOMER, 8, 0, spawns);
        Boomer boomer = new Boomer(c.number("power", 3.0, 0, 10), c.bool("break-blocks", true),
                c.integer("fuse-ticks", 20, 0, 200));
        c = spawn(types, ZombieType.BOMBER, 8, 0, spawns);
        Bomber bomber = new Bomber(c.number("trigger-distance", 2.5, 0.5, 16), c.integer("fuse-ticks", 30, 1, 200),
                c.number("power", 3.0, 0, 10), c.bool("break-blocks", true));
        c = spawn(types, ZombieType.THROWER, 7, 0, spawns);
        Thrower thrower = new Thrower(c.number("horizontal", 1.6, 0, 5), c.number("vertical", 1.0, 0, 5));
        c = spawn(types, ZombieType.TOXIC, 8, 0, spawns);
        Toxic toxic = new Toxic(c.integer("poison-seconds", 5, 0, 600), c.integer("hunger-seconds", 8, 0, 600));
        c = spawn(types, ZombieType.FROST, 7, 0, spawns);
        Frost frost = new Frost(c.integer("freeze-ticks", 160, 0, 1000), c.integer("slowness-seconds", 4, 0, 600));
        c = spawn(types, ZombieType.JUMPER, 7, 0, spawns);
        Jumper jumper = new Jumper(c.integer("cooldown-ticks", 80, 1, MAX_TICKS), c.number("min-distance", 4, 0, 64),
                c.number("max-distance", 10, 1, 64));
        if (jumper.minDistance() > jumper.maxDistance()) {
            warnings.add("types.jumper: min-distance가 max-distance보다 큽니다. 기본값 4~10을 사용합니다.");
            jumper = new Jumper(jumper.cooldownTicks(), 4, 10);
        }
        c = spawn(types, ZombieType.NINJA, 6, 0, spawns);
        Ninja ninja = new Ninja(c.number("reveal-distance", 4, 0, 32), c.integer("hide-after-ticks", 100, 1, MAX_TICKS));
        c = spawn(types, ZombieType.GRABBER, 6, 0, spawns);
        Grabber grabber = new Grabber(c.number("range", 16, 4, 48), c.number("hook-speed", 1.2, 0.2, 5),
                c.integer("windup-ticks", 10, 0, 100), c.integer("cooldown-ticks", 160, 1, MAX_TICKS),
                c.number("damage", 2, 0, 100));
        c = spawn(types, ZombieType.MULTIPLIER, 6, 0, spawns);
        Multiplier multiplier = new Multiplier(c.integer("split-min", 2, 1, 10), c.integer("split-max", 3, 1, 10),
                c.integer("max-generation", 4, 0, 100), c.number("scale-per-generation", 0.8, 0.1, 1),
                c.number("health-per-generation", 0.7, 0.05, 1), c.integer("nearby-cap", 60, 1, 1000),
                c.number("nearby-radius", 32, 1, 128));
        if (multiplier.splitMin() > multiplier.splitMax()) {
            warnings.add("types.multiplier: split-min이 split-max보다 큽니다. 기본값 2~3을 사용합니다.");
            multiplier = new Multiplier(2, 3, multiplier.maxGeneration(), multiplier.scalePerGeneration(),
                    multiplier.healthPerGeneration(), multiplier.nearbyCap(), multiplier.nearbyRadius());
        }
        c = spawn(types, ZombieType.SCREAMER, 5, 0, spawns);
        Screamer screamer = new Screamer(c.number("detect-distance", 16, 1, 64), c.number("radius", 24, 1, 128),
                c.integer("cooldown-ticks", 200, 1, MAX_TICKS));
        c = spawn(types, ZombieType.HEALER, 5, 0, spawns);
        Healer healer = new Healer(c.number("radius", 8, 1, 64), c.number("amount", 4, 0, 1000),
                c.integer("cooldown-ticks", 60, 1, MAX_TICKS));
        c = spawn(types, ZombieType.SPITTER, 5, 0, spawns);
        Spitter spitter = new Spitter(c.number("min-distance", 5, 0, 64), c.number("max-distance", 16, 1, 64),
                c.integer("cooldown-ticks", 100, 1, MAX_TICKS), c.integer("puddle-seconds", 5, 1, 120),
                c.number("puddle-radius", 2.5, 0.5, 10), c.number("hit-damage", 3, 0, 100));
        if (spitter.minDistance() > spitter.maxDistance()) {
            warnings.add("types.spitter: min-distance가 max-distance보다 큽니다. 기본값 5~16을 사용합니다.");
            spitter = new Spitter(5, 16, spitter.cooldownTicks(), spitter.puddleSeconds(), spitter.puddleRadius(),
                    spitter.hitDamage());
        }
        c = spawn(types, ZombieType.PILLAR, 4, 0, spawns);
        Pillar pillar = new Pillar(c.integer("height-min", 3, 2, 8), c.integer("height-max", 4, 2, 8));
        if (pillar.heightMin() > pillar.heightMax()) {
            warnings.add("types.pillar: height-min이 height-max보다 큽니다. 기본값 3~4를 사용합니다.");
            pillar = new Pillar(3, 4);
        }
        c = spawn(types, ZombieType.VAMPIRE, 5, 0, spawns);
        Vampire vampire = new Vampire(c.number("heal-ratio", 1.0, 0, 10));
        c = spawn(types, ZombieType.THIEF, 4, 0, spawns);
        Thief thief = new Thief(c.integer("flee-seconds", 20, 1, 3600));
        c = spawn(types, ZombieType.NECROMANCER, 3, 0, spawns);
        Necromancer necromancer = new Necromancer(c.number("range", 20, 1, 64), c.integer("cooldown-ticks", 240, 1, MAX_TICKS),
                c.integer("raise-min", 2, 1, 10), c.integer("raise-max", 3, 1, 10), c.integer("max-alive", 6, 1, 50));
        if (necromancer.raiseMin() > necromancer.raiseMax()) {
            warnings.add("types.necromancer: raise-min이 raise-max보다 큽니다. 기본값 2~3을 사용합니다.");
            necromancer = new Necromancer(necromancer.range(), necromancer.cooldownTicks(), 2, 3, necromancer.maxAlive());
        }
        c = spawn(types, ZombieType.JOCKEY, 5, 0, spawns);
        Jockey jockey = new Jockey(c.number("leap-distance", 3, 1, 16), c.integer("cooldown-ticks", 100, 1, MAX_TICKS),
                c.integer("ride-ticks", 120, 1, MAX_TICKS), c.number("damage", 1, 0, 100), c.bool("darkness", false));
        c = spawn(types, ZombieType.WITCH, 3, 0, spawns);
        Witch witch = new Witch(c.number("startle-distance", 5, 1, 32), c.number("speed-multiplier", 2.0, 0.1, 5),
                c.number("damage-multiplier", 2.5, 0, 20), c.number("health-multiplier", 2.0, 0.1, 20),
                c.integer("rage-seconds", 30, 1, 3600));

        int total = spawns.values().stream().mapToInt(Spawn::weight).sum();
        if (total <= 0) {
            warnings.add("types: 모든 가중치가 0입니다. 기본 가중치를 사용합니다.");
            spawns = defaultSpawns();
        }
        return new TypeSettings(Collections.unmodifiableMap(spawns), sprinter, boomer, bomber, thrower, toxic, frost,
                jumper, ninja, grabber, multiplier, screamer, healer, spitter, pillar, vampire, thief, necromancer, jockey, witch);
    }

    private static ConfigReader spawn(ConfigReader types, ZombieType type, int weight, int minDay,
                                      Map<ZombieType, Spawn> spawns) {
        ConfigReader section = types.child(type.configKey());
        spawns.put(type, new Spawn(section.integer("weight", weight, 0, 100_000), section.integer("min-day", minDay, 0, 100_000)));
        return section;
    }

    private static Map<ZombieType, Spawn> defaultSpawns() {
        Map<ZombieType, Spawn> defaults = new EnumMap<>(ZombieType.class);
        read(ConfigReader.root(null, new java.util.ArrayList<>()).child("types"), new java.util.ArrayList<>())
                .spawns().forEach(defaults::put);
        return defaults;
    }
}
