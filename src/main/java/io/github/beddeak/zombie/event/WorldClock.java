package io.github.beddeak.zombie.event;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.data.EndingData;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.logic.DayMath;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WorldClock {
    private final Apocalypse core;

    public WorldClock(Apocalypse core) {
        this.core = core;
    }

    public void second(World world, WorldData data) {
        Settings settings = core.settings();
        int day = core.day(world);
        long timeOfDay = DayMath.timeOfDay(core.fullTime(world));
        boolean over = data.ending.phase == EndingData.Phase.DONE;
        boolean changed = false;

        if (day > data.lastDayShown) {
            data.lastDayShown = day;
            changed = true;
            if (settings.difficulty().dayTitle()) {
                Msg.title(world.getPlayers(), day + "일차", NamedTextColor.RED, "");
                for (Player player : world.getPlayers()) { player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 0.6f); }
            }
        }

        Settings.Horde horde = settings.horde();
        if (horde.enabled() && horde.regularEveryDays() > 0) {
            boolean due = data.hordeDay > 0 && !data.hordeDone && day >= data.hordeDay
                    && (day > data.hordeDay || timeOfDay >= data.hordeTime);
            if (due) {
                if (over || core.hordes().continuous(world)) {
                    data.hordeDone = true;
                    changed = true;
                } else if (core.hordes().start(world, HordeManager.Kind.REGULAR)) {
                    data.hordeDone = true;
                    changed = true;
                }
            } else if (DayMath.everyNth(day, horde.regularEveryDays()) && data.hordeDay != day) {
                data.hordeDay = day;
                data.hordeTime = DayMath.pickLaterToday(timeOfDay, ThreadLocalRandom.current());
                data.hordeDone = false;
                changed = true;
            }
        }

        Settings.Tank tank = settings.tank();
        if (!over && tank.enabled() && DayMath.everyNth(day, tank.everyDays()) && data.tankDay != day && DayMath.night(timeOfDay)
                && core.tanks().spawnNearSurvivor(world)) {
            data.tankDay = day;
            changed = true;
        }
        if (changed) { core.save(); }
    }
}
