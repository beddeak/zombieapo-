package io.github.beddeak.zombie.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/** Validated, immutable view of config.yml. */
public record Settings(
        Replace replace,
        Spawning spawning,
        ZombieOptions zombie,
        TypeSettings types,
        Tank tank,
        Difficulty difficulty,
        BloodMoon bloodMoon,
        PlayerZombie playerZombie,
        Horde horde,
        Ending ending,
        Start start) {

    /**
     * Preparation time between {@code /apocalypse start} and the first zombies, then how long nothing spawns under
     * the open sky so players can settle in.
     */
    public record Start(int graceSeconds, int quietSurfaceSeconds) {}

    public record Replace(Set<SpawnReason> reasons, Set<EntityType> exclude, int spawnerNearbyCap) {}

    public record Spawning(int monsterSpawnLimit, int ticksPerMonsterSpawn, boolean extraEnabled, int intervalTicks,
                           int perPlayerCap, int globalCap, double countRadius, int perAttempt,
                           double minDistance, double maxDistance) {}

    public record ZombieOptions(double followRange, boolean showTypeName) {}

    public record Loot(Material material, int amount) {}

    public record Tank(boolean enabled, double health, double damage, double scale, double speedMultiplier, int limit,
                       boolean bloodMoon, double hordeFinalChance, int hordeMinDay, int everyDays,
                       double fireDamageMultiplier, int rockCooldownTicks, double rockDamage, int slamCooldownTicks,
                       double slamDamage, List<Loot> loot, int exp) {}

    public record Difficulty(double healthPerDay, double damagePerDay, int specialWeightPerDay, int capPerDay,
                             int maxDay, boolean dayTitle) {}

    public record BloodMoon(boolean enabled, int everyDays, double spawnIntervalMultiplier, double capMultiplier,
                            int specialWeightBonus, double healthMultiplier, double speedMultiplier) {}

    public record PlayerZombie(boolean enabled, double healthMultiplier, double damageMultiplier) {}

    public record Horde(boolean enabled, int regularEveryDays, double screamerChance, int screamerCooldownSeconds,
                        int warningSeconds, int wavesMin, int wavesMax, int waveGapSeconds, int baseSize,
                        int sizePerDay, int maxWaveTotal, int distanceMin, int distanceMax, int timeoutSeconds,
                        int sprinterBonusWeight, List<Loot> reward) {}

    public record Finale(int seconds, double radius, double height, double hordeSizeMultiplier, int gapMin, int gapMax,
                         List<Integer> tankAtSeconds, int tankLimit) {}

    public record Escape(int distance, int seconds, double radius, double hordeSizeMultiplier, int gapMin, int gapMax,
                         int pointZombies) {}

    /** Crafting the radio or antenna is an assembly at the crafting table that takes time while waves attack. */
    public record Assembly(int radioSeconds, int antennaSeconds, double radius, double waveSizeMultiplier, int gapMin, int gapMax) {}

    /** A shaped crafting recipe: up to three rows of up to three symbols (space = empty) and what each symbol is. */
    public record Shape(List<String> rows, Map<Character, Material> keys) {}

    static final Shape RADIO_SHAPE = new Shape(List.of("DRD", "RHR", "TBT"), Map.of('D', Material.DIAMOND_BLOCK,
            'R', Material.REDSTONE_BLOCK, 'H', Material.HEART_OF_THE_SEA, 'T', Material.REDSTONE_TORCH, 'B', Material.NOTE_BLOCK));
    static final Shape ANTENNA_SHAPE = new Shape(List.of(" C ", "QRQ", " C "), Map.of('C', Material.IRON_CHAIN,
            'Q', Material.QUARTZ, 'R', Material.REDSTONE));

    public record Ending(Material radioBlock, Shape radioRecipe, Material antennaBlock, Shape antennaRecipe,
                         Assembly assembly, int minDay, int confirmSeconds, Finale finale, Escape escape, boolean autoStop,
                         int autoStopDelaySeconds, String message) {}

    public record Loaded(Settings settings, List<String> warnings) {}

    public static Settings defaults() {
        return load(new YamlConfiguration()).settings();
    }

    public static Loaded load(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();
        ConfigReader reader = ConfigReader.root(root, warnings);

        ConfigReader r = reader.child("replace");
        Replace replace = new Replace(
                enums(r, "reasons", SpawnReason.class, EnumSet.of(SpawnReason.NATURAL, SpawnReason.SPAWNER,
                        SpawnReason.TRIAL_SPAWNER, SpawnReason.PATROL)),
                enums(r, "exclude", EntityType.class, EnumSet.of(EntityType.ENDER_DRAGON, EntityType.WITHER,
                        EntityType.WARDEN, EntityType.ELDER_GUARDIAN)),
                r.integer("spawner-nearby-cap", 12, 0, 1000));

        ConfigReader s = reader.child("spawning");
        ConfigReader extra = s.child("extra");
        double minDistance = extra.number("min-distance", 20, 1, 128);
        double maxDistance = extra.number("max-distance", 40, 1, 128);
        if (minDistance > maxDistance) {
            warnings.add("spawning.extra: min-distance가 max-distance보다 큽니다. 기본값 20~40을 사용합니다.");
            minDistance = 20;
            maxDistance = 40;
        }
        Spawning spawning = new Spawning(s.integer("monster-spawn-limit", 150, 0, 10_000),
                s.integer("ticks-per-monster-spawn", 1, 1, 10_000), extra.bool("enabled", true),
                extra.integer("interval-ticks", 100, 1, 72_000), extra.integer("per-player-cap", 40, 0, 1000),
                extra.integer("global-cap", 500, 0, 10_000), extra.number("count-radius", 48, 1, 256),
                extra.integer("per-attempt", 5, 1, 100), minDistance, maxDistance);

        ConfigReader z = reader.child("zombie");
        ZombieOptions zombie = new ZombieOptions(z.number("follow-range", 40, 1, 2048), z.bool("show-type-name", false));

        TypeSettings types = TypeSettings.read(reader.child("types"), warnings);

        ConfigReader t = reader.child("tank");
        Tank tank = new Tank(t.bool("enabled", true), t.number("health", 400, 1, 100_000), t.number("damage", 12, 0, 1000),
                t.number("scale", 2.2, 0.5, 8), t.number("speed-multiplier", 1.1, 0.1, 5), t.integer("limit", 1, 1, 10),
                t.bool("blood-moon", true), t.number("horde-final-chance", 0.15, 0, 1), t.integer("horde-min-day", 5, 0, 100_000),
                t.integer("every-days", 10, 0, 100_000), t.number("fire-damage-multiplier", 2, 0, 100),
                t.integer("rock-cooldown-ticks", 120, 1, 72_000), t.number("rock-damage", 8, 0, 1000),
                t.integer("slam-cooldown-ticks", 160, 1, 72_000), t.number("slam-damage", 6, 0, 1000),
                loot(t, "loot", List.of(new Loot(Material.GOLDEN_APPLE, 3))),
                t.integer("exp", 100, 0, 100_000));

        ConfigReader d = reader.child("difficulty");
        Difficulty difficulty = new Difficulty(d.number("health-per-day", 0.05, 0, 10), d.number("damage-per-day", 0.03, 0, 10),
                d.integer("special-weight-per-day", 2, 0, 100_000), d.integer("cap-per-day", 1, 0, 1000),
                d.integer("max-day", 30, 1, 100_000), d.bool("day-title", true));

        ConfigReader b = reader.child("blood-moon");
        BloodMoon bloodMoon = new BloodMoon(b.bool("enabled", true), b.integer("every-days", 7, 1, 100_000),
                b.number("spawn-interval-multiplier", 0.4, 0.05, 10), b.number("cap-multiplier", 2, 0.1, 20),
                b.integer("special-weight-bonus", 20, 0, 100_000), b.number("health-multiplier", 1.3, 0.1, 20),
                b.number("speed-multiplier", 1.15, 0.1, 5));

        ConfigReader p = reader.child("player-zombie");
        PlayerZombie playerZombie = new PlayerZombie(p.bool("enabled", true), p.number("health-multiplier", 1.5, 0.1, 50),
                p.number("damage-multiplier", 1.2, 0, 50));

        ConfigReader h = reader.child("horde");
        int[] waves = h.range("waves", 2, 3, 1, 20);
        int[] distance = h.range("distance", 30, 45, 8, 128);
        Horde horde = new Horde(h.bool("enabled", true), h.integer("regular-every-days", 3, 0, 100_000),
                h.number("screamer-chance", 0.25, 0, 1), h.integer("screamer-cooldown-seconds", 300, 0, 86_400),
                h.integer("warning-seconds", 10, 0, 300), waves[0], waves[1], h.integer("wave-gap-seconds", 20, 1, 3600),
                h.integer("base-size", 8, 0, 500), h.integer("size-per-day", 1, 0, 500), h.integer("max-wave-total", 80, 1, 2000),
                distance[0], distance[1], h.integer("timeout-seconds", 180, 10, 86_400),
                h.integer("sprinter-bonus-weight", 5, 0, 100_000), loot(h, "reward", List.of()));

        ConfigReader e = reader.child("ending");
        Material radioBlock = material(e, "radio-block", Material.JUKEBOX);
        Shape recipe = shape(e, "radio-recipe", RADIO_SHAPE);
        Material antennaBlock = material(e, "antenna-block", Material.END_ROD);
        Shape antennaRecipe = shape(e, "antenna-recipe", ANTENNA_SHAPE);
        ConfigReader a = e.child("assembly");
        int[] assemblyGap = a.range("wave-gap-seconds", 20, 25, 1, 3600);
        Assembly assembly = new Assembly(a.integer("radio-seconds", 90, 0, 86_400), a.integer("antenna-seconds", 45, 0, 86_400),
                a.number("radius", 8, 1, 64), a.number("wave-size-multiplier", 1.0, 0, 20), assemblyGap[0], assemblyGap[1]);
        ConfigReader f = e.child("finale");
        int[] finaleGap = f.range("wave-gap-seconds", 15, 20, 1, 3600);
        Finale finale = new Finale(f.integer("seconds", 300, 10, 86_400), f.number("radius", 32, 1, 256), f.number("height", 8, 1, 256),
                f.number("horde-size-multiplier", 1.5, 0, 20), finaleGap[0], finaleGap[1],
                f.integers("tank-at-seconds", List.of(60, 210), 0, 86_400), f.integer("tank-limit", 2, 1, 10));
        ConfigReader x = e.child("escape");
        int[] escapeGap = x.range("wave-gap-seconds", 20, 25, 1, 3600);
        Escape escape = new Escape(x.integer("distance", 100, 10, 2000), x.integer("seconds", 180, 10, 86_400),
                x.number("radius", 5, 1, 64), x.number("horde-size-multiplier", 1.0, 0, 20), escapeGap[0], escapeGap[1],
                x.integer("point-zombies", 4, 0, 100));
        Ending ending = new Ending(radioBlock, recipe, antennaBlock, antennaRecipe, assembly, e.integer("min-day", 0, 0, 100_000),
                e.integer("confirm-seconds", 5, 1, 60), finale, escape, e.bool("auto-stop", true), e.integer("auto-stop-delay-seconds", 10, 0, 600),
                e.text("message", "구조 헬기가 도착했습니다. 생존자들은 무사히 탈출했습니다."));

        ConfigReader st = reader.child("start");
        Start start = new Start(st.integer("grace-seconds", 6, 0, 86_400), st.integer("quiet-surface-seconds", 180, 0, 86_400));

        reader.finish();
        return new Loaded(new Settings(replace, spawning, zombie, types, tank, difficulty, bloodMoon, playerZombie, horde, ending,
                start), List.copyOf(warnings));
    }

    private static <E extends Enum<E>> Set<E> enums(ConfigReader reader, String key, Class<E> type, Set<E> fallback) {
        List<String> names = reader.strings(key, null);
        if (names == null) { return Set.copyOf(fallback); }
        EnumSet<E> result = EnumSet.noneOf(type);
        for (String name : names) {
            try {
                result.add(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                reader.warn(key, "알 수 없는 이름 '" + name + "'은(는) 건너뜁니다.", "나머지 항목");
            }
        }
        return Set.copyOf(result);
    }

    private static Shape shape(ConfigReader parent, String key, Shape fallback) {
        ConfigReader reader = parent.child(key);
        List<String> rows = reader.lines("shape");
        Map<String, String> names = reader.entries("keys");
        if (rows == null && names == null) { return fallback; }
        if (rows == null || names == null) {
            reader.warn("shape", "shape와 keys를 함께 적어야 합니다.", "기본 조합법");
            return fallback;
        }
        Map<Character, Material> keys = new HashMap<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            Material material = Material.matchMaterial(entry.getValue());
            if (entry.getKey().length() != 1 || material == null) {
                reader.warn("keys", "'" + entry.getKey() + ": " + entry.getValue() + "'은(는) 한 글자: 아이템 형식이 아닙니다.", "기본 조합법");
                return fallback;
            }
            keys.put(entry.getKey().charAt(0), material);
        }
        String problem = shapeProblem(rows, keys.keySet());
        if (problem != null) {
            reader.warn("shape", problem, "기본 조합법");
            return fallback;
        }
        Map<Character, Material> used = new HashMap<>();
        for (String row : rows) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ') { used.put(symbol, keys.get(symbol)); }
            }
        }
        return new Shape(List.copyOf(rows), Map.copyOf(used));
    }

    /** Why a crafting shape is invalid, or null: 1-3 rows of the same 1-3 width, every symbol defined, not empty. */
    static String shapeProblem(List<String> rows, java.util.Set<Character> keys) {
        if (rows.isEmpty() || rows.size() > 3) { return "줄은 1~3개여야 합니다."; }
        int width = rows.get(0).length();
        boolean any = false;
        for (String row : rows) {
            if (row.length() != width || width < 1 || width > 3) { return "모든 줄이 같은 길이(1~3칸)여야 합니다."; }
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') { continue; }
                if (!keys.contains(symbol)) { return "'" + symbol + "'에 해당하는 재료가 keys에 없습니다."; }
                any = true;
            }
        }
        return any ? null : "재료가 하나도 없습니다.";
    }

    private static Material material(ConfigReader reader, String key, Material fallback) {
        String name = reader.text(key, null);
        if (name == null) { return fallback; }
        Material material = Material.matchMaterial(name);
        if (material == null) {
            reader.warn(key, "알 수 없는 아이템 '" + name + "'입니다.", fallback);
            return fallback;
        }
        return material;
    }

    /** Parses entries like {@code DIAMOND:5}. */
    private static List<Loot> loot(ConfigReader reader, String key, List<Loot> fallback) {
        List<String> entries = reader.strings(key, null);
        if (entries == null) { return fallback; }
        List<Loot> result = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split(":");
            Material material = parts.length == 0 ? null : Material.matchMaterial(parts[0]);
            int amount = 1;
            try {
                if (parts.length == 2) { amount = Integer.parseInt(parts[1].trim()); }
            } catch (NumberFormatException exception) {
                amount = -1;
            }
            if (material == null || parts.length > 2 || amount < 1 || amount > 6400) {
                reader.warn(key, "'" + entry + "'은(는) 아이템:개수 형식이 아니라 건너뜁니다.", "나머지 항목");
                continue;
            }
            result.add(new Loot(material, amount));
        }
        return List.copyOf(result);
    }
}
