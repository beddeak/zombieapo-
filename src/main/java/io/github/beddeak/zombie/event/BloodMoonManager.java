package io.github.beddeak.zombie.event;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.WorldBar;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.logic.DayMath;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BloodMoonManager {
    private static final class State {
        boolean active;
        long forcedUntil = Long.MIN_VALUE;
        long suppressedUntil = Long.MIN_VALUE;
        long startedAt;
        long endsAt;
        long tankAt;
        WorldBar bar;
    }

    private final Apocalypse core;
    private final Map<String, State> states = new HashMap<>();

    public BloodMoonManager(Apocalypse core) {
        this.core = core;
    }

    public boolean active(World world) {
        State state = states.get(world.getName());
        return state != null && state.active;
    }

    public void second(World world, WorldData data) {
        Settings settings = core.settings();
        State state = states.computeIfAbsent(world.getName(), name -> new State());
        long now = core.fullTime(world);
        int day = core.day(world);
        boolean natural = settings.bloodMoon().enabled()
                && DayMath.bloodMoonNight(day, settings.bloodMoon().everyDays(), DayMath.timeOfDay(now))
                && now >= state.suppressedUntil;
        boolean forced = now < state.forcedUntil;
        if ((natural || forced) && !state.active) {
            begin(world, state, now, forced ? state.forcedUntil : DayMath.nextMorning(now));
        } else if (!natural && !forced && state.active) {
            finish(world, state);
        }
        if (!state.active) { return; }
        long total = Math.max(1, state.endsAt - state.startedAt);
        long left = Math.max(0, state.endsAt - now);
        state.bar.update(Component.text("블러드문 — 아침까지 " + Msg.clock((int) (left / 20)), NamedTextColor.RED), (double) left / total);
        state.bar.sync(world.getPlayers());
        Settings.Tank tank = settings.tank();
        if (tank.enabled() && tank.bloodMoon() && data.bloodMoonTankDay != day && now >= state.tankAt
                && core.tanks().spawnNearSurvivor(world)) {
            data.bloodMoonTankDay = day;
            core.save();
        }
    }

    public void forceStart(World world, WorldData data) {
        State state = states.computeIfAbsent(world.getName(), name -> new State());
        state.forcedUntil = DayMath.nextMorning(core.fullTime(world));
        state.suppressedUntil = Long.MIN_VALUE;
        second(world, data);
    }

    public void forceStop(World world, WorldData data) {
        State state = states.computeIfAbsent(world.getName(), name -> new State());
        state.forcedUntil = Long.MIN_VALUE;
        state.suppressedUntil = DayMath.nextMorning(core.fullTime(world));
        second(world, data);
    }

    public void clear(World world) {
        State state = states.remove(world.getName());
        if (state != null && state.bar != null) { state.bar.hide(); }
    }

    public void clearAll() {
        for (State state : states.values()) {
            if (state.bar != null) { state.bar.hide(); }
        }
        states.clear();
    }

    private void begin(World world, State state, long now, long endsAt) {
        state.active = true;
        state.startedAt = now;
        state.endsAt = endsAt;
        state.tankAt = now + 20L * ThreadLocalRandom.current().nextInt(60, 181);
        state.bar = new WorldBar(Component.text("블러드문", NamedTextColor.RED), BossBar.Color.RED);
        state.bar.sync(world.getPlayers());
        Msg.title(world.getPlayers(), "블러드문", NamedTextColor.DARK_RED, "좀비들이 미쳐 날뜁니다. 아침까지 버티세요");
        for (Player player : world.getPlayers()) { player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f); }
        core.announce(world, Msg.alert("블러드문이 떴습니다! 좀비가 더 많이, 더 강하게 몰려옵니다."));
    }

    private void finish(World world, State state) {
        state.active = false;
        if (state.bar != null) {
            state.bar.hide();
            state.bar = null;
        }
        Msg.title(world.getPlayers(), "블러드문이 끝났습니다", NamedTextColor.GOLD, "");
        core.announce(world, Msg.good("블러드문이 끝났습니다."));
    }
}
