package io.github.beddeak.zombie.logic;

public final class HordeMath {
    private HordeMath() {}

    public static int perPlayer(int baseSize, int sizePerDay, int day, int maxDay, double multiplier) {
        int size = baseSize + sizePerDay * DayMath.dayFactor(day, maxDay);
        return (int) Math.max(0, Math.round(size * multiplier));
    }

    public static int capped(int perPlayer, int players, int maxTotal) {
        if (players <= 0) { return 0; }
        if ((long) perPlayer * players <= maxTotal) { return perPlayer; }
        return Math.max(0, maxTotal / players);
    }
}
