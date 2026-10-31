package io.github.beddeak.zombie.data;

import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.EnumMap;
import java.util.Map;

public final class PlayerStats {
    public final Map<ZombieType, Integer> kills = new EnumMap<>(ZombieType.class);
    public int deaths;
    public int stolen;

    public void kill(ZombieType type) {
        if (type != null) { kills.merge(type, 1, Integer::sum); }
    }

    public int kills(ZombieType type) {
        return kills.getOrDefault(type, 0);
    }

    public int totalKills() {
        return kills.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean empty() {
        return totalKills() == 0 && deaths == 0 && stolen == 0;
    }
}
