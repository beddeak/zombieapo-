package io.github.beddeak.zombie.event;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.WorldBar;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.logic.HordeMath;
import io.github.beddeak.zombie.logic.SpawnLocator;
import io.github.beddeak.zombie.spawn.BukkitSampler;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.SpawnContext;
import io.github.beddeak.zombie.zombie.Tags;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

/**
 * Left 4 Dead style hordes, one per world at a time. After a warning, waves come from one direction per player.
 * A continuous horde (radio finale and escape) keeps sending waves until it is stopped.
 */
public final class HordeManager {
    public enum Kind { REGULAR, SCREAMER, MANUAL, CONTINUOUS }

    private static final double SECTOR = 0.6;

    private final class Horde {
        final int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        final Kind kind;
        final long startedAt = core.ticks();
        final Map<UUID, Double> directions = new HashMap<>();
        int wavesTotal;
        int wavesSpawned;
        long nextWaveAt;
        long endsAt = Long.MAX_VALUE;
        double sizeMultiplier = 1;
        int gapMin;
        int gapMax;
        int postponed;
        WorldBar bar;
        Location extraPoint;
        int extraCount;

        Horde(Kind kind) {
            this.kind = kind;
        }
    }

    private final Apocalypse core;
    private final Map<String, Horde> hordes = new HashMap<>();
    private final Map<String, Long> screamerReadyAt = new HashMap<>();

    public HordeManager(Apocalypse core) {
        this.core = core;
    }

    public boolean running(World world) {
        return hordes.containsKey(world.getName());
    }

    public boolean continuous(World world) {
        Horde horde = hordes.get(world.getName());
        return horde != null && horde.kind == Kind.CONTINUOUS;
    }

    /** Starts a normal horde. False when one is already running, hordes are off, or nobody can fight. */
    public boolean start(World world, Kind kind) {
        Settings.Horde settings = core.settings().horde();
        if ((!settings.enabled() && kind != Kind.MANUAL) || running(world) || core.survivors(world).isEmpty()) { return false; }
        Horde horde = new Horde(kind);
        horde.wavesTotal = ThreadLocalRandom.current().nextInt(settings.wavesMin(), settings.wavesMax() + 1);
        horde.nextWaveAt = core.ticks() + settings.warningSeconds() * 20L;
        horde.endsAt = horde.nextWaveAt + settings.timeoutSeconds() * 20L;
        horde.gapMin = settings.waveGapSeconds();
        horde.gapMax = settings.waveGapSeconds();
        horde.bar = new WorldBar(Component.text("좀비 웨이브 접근 중", NamedTextColor.RED), BossBar.Color.RED);
        hordes.put(world.getName(), horde);
        warn(world, horde, settings.warningSeconds() + "초 후 도착");
        return true;
    }

    /** Replaces any running horde with endless waves every {@code gapMin..gapMax} seconds. */
    public void startContinuous(World world, double sizeMultiplier, int gapMin, int gapMax) {
        stop(world);
        Horde horde = new Horde(Kind.CONTINUOUS);
        horde.wavesTotal = Integer.MAX_VALUE;
        horde.nextWaveAt = core.ticks() + Math.max(5, core.settings().horde().warningSeconds()) * 20L;
        horde.sizeMultiplier = sizeMultiplier;
        horde.gapMin = gapMin;
        horde.gapMax = gapMax;
        hordes.put(world.getName(), horde);
        warn(world, horde, "끝없이 몰려옵니다. 버티세요!");
    }

    /** Extra zombies spawned around a point with every wave (the escape point). */
    public void extraPoint(World world, Location point, int count) {
        Horde horde = hordes.get(world.getName());
        if (horde == null) { return; }
        horde.extraPoint = point;
        horde.extraCount = count;
    }

    public void stop(World world) {
        Horde horde = hordes.remove(world.getName());
        if (horde != null && horde.bar != null) { horde.bar.hide(); }
    }

    public void clearAll() {
        for (Horde horde : hordes.values()) {
            if (horde.bar != null) { horde.bar.hide(); }
        }
        hordes.clear();
    }

    /** Loaded zombies that belong to the running horde. */
    public int remaining(World world) {
        Horde horde = hordes.get(world.getName());
        if (horde == null) { return 0; }
        int count = 0;
        for (Zombie zombie : core.registry().inWorld(world)) {
            if (Tags.integer(zombie, Keys.HORDE, 0) == horde.id) { count++; }
        }
        return count;
    }

    /** A screamer screamed: with some chance, and not too often, it calls a horde. */
    public void screamed(World world) {
        Settings.Horde settings = core.settings().horde();
        if (!settings.enabled() || running(world)) { return; }
        long now = core.ticks();
        if (now < screamerReadyAt.getOrDefault(world.getName(), 0L)) { return; }
        if (ThreadLocalRandom.current().nextDouble() >= settings.screamerChance()) { return; }
        if (start(world, Kind.SCREAMER)) {
            screamerReadyAt.put(world.getName(), now + settings.screamerCooldownSeconds() * 20L);
            core.announce(world, Msg.alert("스크리머의 비명에 좀비 웨이브가 깨어났습니다!"));
        }
    }

    public void second(World world) {
        Horde horde = hordes.get(world.getName());
        if (horde == null) { return; }
        long now = core.ticks();
        if (horde.wavesSpawned < horde.wavesTotal && now >= horde.nextWaveAt) { wave(world, horde); }
        if (horde.kind == Kind.CONTINUOUS || hordes.get(world.getName()) != horde) { return; }
        int remaining = remaining(world);
        if (horde.wavesSpawned == 0) {
            int seconds = (int) Math.max(0, (horde.nextWaveAt - now) / 20);
            horde.bar.update(Component.text("좀비 웨이브 접근 중 — " + seconds + "초", NamedTextColor.RED), 1);
        } else {
            double progress = (double) Math.max(0, horde.endsAt - now) / Math.max(1, horde.endsAt - horde.startedAt);
            horde.bar.update(Component.text("좀비 웨이브 — 남은 좀비 " + remaining + " (" + horde.wavesSpawned + "/" + horde.wavesTotal + ")",
                    NamedTextColor.RED), progress);
        }
        horde.bar.sync(world.getPlayers());
        if (horde.wavesSpawned >= horde.wavesTotal && remaining == 0) {
            stop(world);
            Msg.title(world.getPlayers(), "좀비 웨이브를 막아냈습니다!", NamedTextColor.GREEN, "");
            reward(world);
            core.announce(world, Msg.good("좀비 웨이브를 막아냈습니다!"));
        } else if (now >= horde.endsAt) {
            stop(world);
            core.announce(world, Msg.warn("좀비 웨이브가 지나갔습니다. 남은 좀비가 아직 주변을 맴돕니다."));
        }
    }

    private void wave(World world, Horde horde) {
        Settings settings = core.settings();
        Settings.Horde options = settings.horde();
        List<Player> survivors = core.survivors(world);
        int day = core.day(world);
        int perPlayer = HordeMath.capped(HordeMath.perPlayer(options.baseSize(), options.sizePerDay(), day,
                settings.difficulty().maxDay(), horde.sizeMultiplier), survivors.size(), options.maxWaveTotal());
        SpawnContext context = core.context(world).withHorde(horde.id);
        BukkitSampler sampler = new BukkitSampler(world);
        int spawned = 0;
        for (Player player : survivors) {
            double angle = horde.directions.computeIfAbsent(player.getUniqueId(),
                    id -> ThreadLocalRandom.current().nextDouble(Math.PI * 2));
            spawned += group(world, sampler, player.getLocation(), perPlayer, angle, SECTOR, options.distanceMin(),
                    options.distanceMax(), context, player, options.sprinterBonusWeight());
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 0.5f);
        }
        if (horde.extraPoint != null && horde.extraCount > 0 && !survivors.isEmpty()
                && world.isChunkLoaded(horde.extraPoint.getBlockX() >> 4, horde.extraPoint.getBlockZ() >> 4)) {
            spawned += group(world, sampler, horde.extraPoint, horde.extraCount, 0, Math.PI, 6, 16, context,
                    survivors.get(0), options.sprinterBonusWeight());
        }
        long now = core.ticks();
        if (spawned == 0 && horde.postponed < 3 && !survivors.isEmpty()) {
            horde.postponed++;
            horde.nextWaveAt = now + 100;
            return;
        }
        horde.postponed = 0;
        horde.wavesSpawned++;
        horde.nextWaveAt = now + 20L * ThreadLocalRandom.current().nextInt(horde.gapMin, horde.gapMax + 1);
        Settings.Tank tank = settings.tank();
        if (horde.kind != Kind.CONTINUOUS && horde.wavesSpawned == horde.wavesTotal && tank.enabled() && day >= tank.hordeMinDay()
                && ThreadLocalRandom.current().nextDouble() < tank.hordeFinalChance()) {
            core.tanks().spawnNearSurvivor(world);
        }
    }

    private int group(World world, BukkitSampler sampler, Location center, int count, double angle, double halfWidth,
                      double min, double max, SpawnContext context, Player target, int sprinterBonus) {
        boolean cave = world.getEnvironment() == World.Environment.NETHER;
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            Optional<SpawnLocator.Spot> spot = SpawnLocator.find(sampler, center.getX(), center.getY(), center.getZ(), min, max,
                    angle, halfWidth, cave, ThreadLocalRandom.current(), 6);
            if (spot.isEmpty()) { continue; }
            Zombie zombie = core.factory().spawnRandom(new Location(world, spot.get().x() + 0.5, spot.get().y(), spot.get().z() + 0.5),
                    context, sprinterBonus);
            if (core.registry().type(zombie) != ZombieType.THIEF) { zombie.setTarget(target); }
            spawned++;
        }
        return spawned;
    }

    private void warn(World world, Horde horde, String subtitle) {
        Msg.title(world.getPlayers(), "좀비 웨이브가 몰려옵니다!", NamedTextColor.DARK_RED, subtitle);
        for (Player player : world.getPlayers()) {
            double angle = horde.directions.computeIfAbsent(player.getUniqueId(), id -> ThreadLocalRandom.current().nextDouble(Math.PI * 2));
            Location source = player.getLocation().add(Math.cos(angle) * 12, 2, Math.sin(angle) * 12);
            player.playSound(source, Sound.EVENT_RAID_HORN, 4f, 1f);
        }
        core.announce(world, Msg.alert("좀비 웨이브가 몰려옵니다! 뿔피리 소리가 나는 쪽을 경계하세요."));
    }

    private void reward(World world) {
        List<Settings.Loot> reward = core.settings().horde().reward();
        if (reward.isEmpty()) { return; }
        for (Player player : core.survivors(world)) {
            for (Settings.Loot loot : reward) {
                for (ItemStack left : player.getInventory().addItem(ItemStack.of(loot.material(), loot.amount())).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }
    }
}
