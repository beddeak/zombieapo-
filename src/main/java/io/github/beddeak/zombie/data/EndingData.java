package io.github.beddeak.zombie.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;

public final class EndingData {
    public enum Phase { NONE, FINALE, ESCAPE, DONE }

    public Phase phase = Phase.NONE;
    public int totalTicks;
    public int remainingTicks;
    public final List<Integer> tanksSpawned = new ArrayList<>();
    public BlockPos escapePoint;
    public final Set<UUID> escaped = new LinkedHashSet<>();
    public final Map<UUID, GameMode> originalModes = new HashMap<>();

    public int elapsedSeconds() {
        return Math.max(0, totalTicks - remainingTicks) / 20;
    }

    public void reset() {
        phase = Phase.NONE;
        totalTicks = 0;
        remainingTicks = 0;
        tanksSpawned.clear();
        escapePoint = null;
        escaped.clear();
        originalModes.clear();
    }
}
