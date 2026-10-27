package io.github.beddeak.zombie;

import io.github.beddeak.zombie.ability.Abilities;
import io.github.beddeak.zombie.ability.Effects;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.data.DataStore;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.ending.AssemblyManager;
import io.github.beddeak.zombie.ending.EndingManager;
import io.github.beddeak.zombie.event.BloodMoonManager;
import io.github.beddeak.zombie.event.HordeManager;
import io.github.beddeak.zombie.event.TankManager;
import io.github.beddeak.zombie.event.WorldClock;
import io.github.beddeak.zombie.logic.DayMath;
import io.github.beddeak.zombie.spawn.ExtraSpawner;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieFactory;
import io.github.beddeak.zombie.zombie.ZombieRegistry;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Central state: active worlds, settings, and the main tick loop that drives every feature. */
public class Apocalypse {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final DataStore store;
    private final ZombieRegistry registry = new ZombieRegistry();
    private final Effects effects;
    private final ZombieFactory factory;
    private final Abilities abilities;
    private final WorldClock clock;
    private final BloodMoonManager bloodMoon;
    private final HordeManager hordes;
    private final TankManager tanks;
    private final EndingManager ending;
    private final AssemblyManager assembly;
    private final ExtraSpawner spawner;
    /** Spawn settings each active world had before we changed them: {monster limit, ticks per spawn}. */
    private final Map<String, long[]> originalSpawn = new HashMap<>();
    /** Countdown bars of worlds that are still preparing. */
    private final Map<String, WorldBar> preparationBars = new HashMap<>();
    /** The last seconds of the preparation shown as big numbers: 3, 2, 1. */
    private static final int COUNTDOWN = 3;
    /** Preparations longer than this (seconds) also get a boss bar and a chat notice; shorter ones are just the intro. */
    private static final int LONG_PREPARATION = 10;
    private Settings settings;
    private BukkitTask task;
    private long ticks;
    private boolean dirty;
    private boolean zombieBlast;

    public Apocalypse(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.settings = settings;
        this.store = new DataStore(new File(plugin.getDataFolder(), "data.yml"), logger);
        this.effects = new Effects(logger);
        this.factory = new ZombieFactory(this);
        this.abilities = new Abilities(this);
        this.clock = new WorldClock(this);
        this.bloodMoon = new BloodMoonManager(this);
        this.hordes = new HordeManager(this);
        this.tanks = new TankManager(this);
        this.ending = new EndingManager(this);
        this.assembly = new AssemblyManager(this);
        this.spawner = new ExtraSpawner(this);
    }

    public void enable() {
        store.load();
        registry.rescan(plugin.getServer());
        ending.registerRecipe();
        for (String name : List.copyOf(store.worlds().keySet())) {
            World world = plugin.getServer().getWorld(name);
            if (world == null) {
                logger.warning("Active world '" + name + "' is not loaded; it stays active and resumes when loaded.");
                continue;
            }
            if (active(world)) { applySpawnSettings(world); }
        }
        ending.restoreAfterRestart();
        assembly.restoreAfterRestart();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void shutdown() {
        if (task != null) { task.cancel(); }
        effects.endAll();
        hordes.clearAll();
        bloodMoon.clearAll();
        tanks.clearAll();
        ending.shutdown();
        assembly.shutdown();
        for (WorldBar bar : preparationBars.values()) { bar.hide(); }
        preparationBars.clear();
        for (String name : List.copyOf(originalSpawn.keySet())) {
            World world = plugin.getServer().getWorld(name);
            if (world != null) { restoreSpawnSettings(world); }
        }
        ending.unregisterRecipe();
        store.save();
        registry.clear();
    }

    public void settings(Settings next) {
        settings = next;
        for (World world : activeWorlds()) { applySpawnSettings(world); }
        ending.registerRecipe();
    }

    /**
     * Registers the world. With a preparation time the apocalypse only begins when the countdown ends; until then the
     * world behaves like vanilla. False when the world is already running or preparing.
     */
    public boolean start(World world, int graceSeconds) {
        if (registered(world)) { return false; }
        WorldData data = new WorldData(DayMath.dayIndex(world.getFullTime()));
        store.worlds().put(world.getName(), data);
        if (graceSeconds <= 0) {
            begin(world, data);
            return true;
        }
        data.preparingTicks = graceSeconds * 20;
        data.preparingTotal = data.preparingTicks;
        save();
        Msg.title(world.getPlayers(), "좀비 아포칼립스", NamedTextColor.DARK_RED, "");
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.5f);
        }
        if (graceSeconds > LONG_PREPARATION) {
            announce(world, Msg.alert(Msg.clock(graceSeconds) + " 뒤 좀비 아포칼립스가 시작됩니다. 지금 준비하세요!"));
        }
        return true;
    }

    /** The countdown is over (or there was none): the apocalypse runs from now, starting on day 1. */
    private void begin(World world, WorldData data) {
        hidePreparation(world);
        data.preparingTicks = 0;
        data.preparingTotal = 0;
        data.startDay = DayMath.dayIndex(world.getFullTime());
        data.lastDayShown = 0;
        applySpawnSettings(world);
        int cleared = clearMonsters(world);
        if (cleared > 0) { logger.info("[" + world.getName() + "] Removed " + cleared + " vanilla monsters at the start"); }
        save();
        ending.discoverRecipe(world.getPlayers());
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.6f);
        }
        announce(world, Msg.alert("좀비 아포칼립스가 시작되었습니다. 라디오와 안테나 조합법이 레시피북에 추가되었습니다."));
    }

    /** One second of a preparing world's countdown. */
    private void prepare(World world, WorldData data) {
        data.preparingTicks = Math.max(0, data.preparingTicks - 20);
        if (data.preparingTicks == 0) {
            begin(world, data);
            return;
        }
        int seconds = (data.preparingTicks + 19) / 20;
        if (data.preparingTotal > LONG_PREPARATION * 20) {
            WorldBar bar = preparationBars.computeIfAbsent(world.getName(),
                    name -> new WorldBar(Component.text("좀비 아포칼립스 준비 중"), BossBar.Color.GREEN));
            bar.update(Component.text("좀비 아포칼립스 시작까지 " + Msg.clock(seconds), NamedTextColor.GREEN),
                    (double) data.preparingTicks / Math.max(1, data.preparingTotal));
            bar.sync(world.getPlayers());
        }
        if (seconds <= COUNTDOWN) {
            Msg.countdown(world.getPlayers(), String.valueOf(seconds));
            for (Player player : world.getPlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
            }
        }
        if (seconds % 10 == 0) { save(); }
    }

    private void hidePreparation(World world) {
        WorldBar bar = preparationBars.remove(world.getName());
        if (bar != null) { bar.hide(); }
    }

    public boolean stop(World world) {
        WorldData data = store.worlds().remove(world.getName());
        if (data == null) { return false; }
        hidePreparation(world);
        if (data.preparingTicks > 0) {
            save();
            announce(world, Msg.good("좀비 아포칼립스 시작을 취소했습니다."));
            return true;
        }
        assembly.clear(world, data);
        ending.clear(world, data);
        hordes.stop(world);
        bloodMoon.clear(world);
        tanks.clear(world);
        effects.endWorld(world);
        int removed = removeCustomEntities(world);
        restoreSpawnSettings(world);
        spawner.forget(world);
        save();
        announce(world, Msg.good("좀비 아포칼립스가 끝났습니다. 커스텀 좀비 " + removed + "마리를 정리했습니다."));
        return true;
    }

    /**
     * The apocalypse replaces the monsters, so the ones already around go away when it begins. Animals, villagers,
     * golems and other non-hostile mobs stay, as do bosses from {@code replace.exclude} and mobs with a name.
     */
    private int clearMonsters(World world) {
        int removed = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (!(entity instanceof Enemy) || entity instanceof Player || Tags.custom(entity) || entity.customName() != null
                    || settings.replace().exclude().contains(entity.getType())) {
                continue;
            }
            entity.remove();
            removed++;
        }
        return removed;
    }

    /** Removes every loaded zombie and helper entity this plugin created in the world. Thieves drop what they stole. */
    public int removeCustomEntities(World world) {
        int removed = 0;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (Tags.custom(entity)) {
                abilities.thief().release((Zombie) entity);
                registry.forget(entity);
                entity.remove();
                removed++;
            } else if (Tags.helper(entity) != null) {
                entity.remove();
            }
        }
        return removed;
    }

    /** Running: started and past the preparation countdown. Everything apocalypse-related checks this. */
    public boolean active(World world) {
        if (world == null) { return false; }
        WorldData data = store.worlds().get(world.getName());
        return data != null && data.preparingTicks <= 0;
    }

    /** Started, including worlds still in the preparation countdown. */
    public boolean registered(World world) {
        return world != null && store.worlds().containsKey(world.getName());
    }

    /** Seconds left of the preparation countdown, 0 when running or not started. */
    public int preparingSeconds(World world) {
        WorldData data = world == null ? null : store.worlds().get(world.getName());
        return data == null ? 0 : (data.preparingTicks + 19) / 20;
    }

    public int defaultGraceSeconds() {
        return settings.start().graceSeconds();
    }

    public WorldData data(World world) {
        return store.worlds().get(world.getName());
    }

    /** Loaded worlds where the apocalypse is running (not preparing). */
    public List<World> activeWorlds() {
        List<World> worlds = new ArrayList<>();
        for (World world : registeredWorlds()) {
            if (active(world)) { worlds.add(world); }
        }
        return worlds;
    }

    /** Loaded worlds that are running or preparing. */
    public List<World> registeredWorlds() {
        List<World> worlds = new ArrayList<>();
        for (String name : store.worlds().keySet()) {
            World world = plugin.getServer().getWorld(name);
            if (world != null) { worlds.add(world); }
        }
        return worlds;
    }

    public int day(World world) {
        WorldData data = data(world);
        return data == null ? 1 : DayMath.day(world.getFullTime(), data.startDay);
    }

    public void setDay(World world, int day) {
        WorldData data = data(world);
        if (data == null) { return; }
        data.startDay = DayMath.startDayFor(world.getFullTime(), day);
        data.lastDayShown = day - 1;
        data.hordeDay = 0;
        data.hordeDone = false;
        data.tankDay = 0;
        data.bloodMoonTankDay = 0;
        save();
    }

    public boolean bloodMoon(World world) {
        return bloodMoon.active(world);
    }

    /**
     * Most custom zombies that natural spawning and extra spawning may gather within {@code spawning.extra.count-radius}
     * of one spot. Grows with the day and is multiplied on a blood moon. Hordes and abilities are not limited by it.
     */
    public int crowdCap(World world) {
        int cap = settings.spawning().perPlayerCap()
                + settings.difficulty().capPerDay() * DayMath.dayFactor(day(world), settings.difficulty().maxDay());
        return bloodMoon(world) ? (int) Math.round(cap * settings.bloodMoon().capMultiplier()) : cap;
    }

    /** Runs an explosion caused by a zombie ability; damage it deals while running is "from a zombie". */
    public void zombieBlast(Runnable explosion) {
        zombieBlast = true;
        try {
            explosion.run();
        } finally {
            zombieBlast = false;
        }
    }

    public boolean zombieBlast() {
        return zombieBlast;
    }

    public boolean crowded(Location location) {
        return registry.countNear(location, settings.spawning().countRadius()) >= crowdCap(location.getWorld());
    }

    public SpawnContext context(World world) {
        return SpawnContext.of(day(world), bloodMoon(world));
    }

    /** Players who can fight: in the world, alive, in survival or adventure. */
    public List<Player> survivors(World world) {
        List<Player> result = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            GameMode mode = player.getGameMode();
            if (!player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)) { result.add(player); }
        }
        return result;
    }

    /** Sends to every player in the world and logs to the console. */
    public void announce(World world, Component message) {
        for (Player player : world.getPlayers()) { player.sendMessage(message); }
        logger.info("[" + world.getName() + "] " + PlainTextComponentSerializer.plainText().serialize(message));
    }

    public void runLater(long delay, Runnable runnable) {
        if (!plugin.isEnabled()) { return; }
        plugin.getServer().getScheduler().runTaskLater(plugin, runnable, Math.max(1, delay));
    }

    private void tick() {
        ticks++;
        safely("effects", effects::tick);
        if (ticks % 10 == 0) {
            safely("abilities", abilities::tick);
            safely("tank bars", tanks::updateBars);
        }
        if (ticks % 20 == 0) {
            for (World world : registeredWorlds()) {
                WorldData data = data(world);
                if (data.preparingTicks > 0) { safely("preparation", () -> prepare(world, data)); }
            }
            for (World world : activeWorlds()) {
                WorldData data = data(world);
                safely("clock", () -> clock.second(world, data));
                safely("blood moon", () -> bloodMoon.second(world, data));
                safely("horde", () -> hordes.second(world));
                safely("ending", () -> ending.second(world, data));
                safely("assembly", () -> assembly.second(world, data));
            }
            if (dirty) {
                dirty = false;
                store.save();
            }
        }
        safely("extra spawning", () -> spawner.tick(ticks));
    }

    private void safely(String what, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "ZombieApocalypse " + what + " failed", exception);
        }
    }

    private void applySpawnSettings(World world) {
        originalSpawn.computeIfAbsent(world.getName(), name ->
                new long[]{world.getSpawnLimit(SpawnCategory.MONSTER), world.getTicksPerSpawns(SpawnCategory.MONSTER)});
        world.setSpawnLimit(SpawnCategory.MONSTER, settings.spawning().monsterSpawnLimit());
        world.setTicksPerSpawns(SpawnCategory.MONSTER, settings.spawning().ticksPerMonsterSpawn());
    }

    private void restoreSpawnSettings(World world) {
        long[] original = originalSpawn.remove(world.getName());
        if (original == null) { return; }
        world.setSpawnLimit(SpawnCategory.MONSTER, (int) original[0]);
        world.setTicksPerSpawns(SpawnCategory.MONSTER, (int) original[1]);
    }

    /** Marks data.yml for writing; the main loop writes it at most once a second and shutdown writes it at once. */
    public void save() { dirty = true; }

    public JavaPlugin plugin() { return plugin; }
    public Logger logger() { return logger; }
    public Settings settings() { return settings; }
    public DataStore store() { return store; }
    public ZombieRegistry registry() { return registry; }
    public Effects effects() { return effects; }
    public ZombieFactory factory() { return factory; }
    public Abilities abilities() { return abilities; }
    public BloodMoonManager bloodMoonManager() { return bloodMoon; }
    public HordeManager hordes() { return hordes; }
    public TankManager tanks() { return tanks; }
    public EndingManager ending() { return ending; }
    public AssemblyManager assembly() { return assembly; }
    public long ticks() { return ticks; }

    /** Type name for messages, e.g. "부머". */
    public static String typeName(ZombieType type) { return type.displayName(); }
}
