package io.github.beddeak.zombie.logic;

/** Horde sizing. */
public final class HordeMath {
    private HordeMath() {}

    /** Zombies sent at one player in one wave. */
    public static int perPlayer(int baseSize, int sizePerDay, int day, int maxDay, double multiplier) {
        int size = baseSize + sizePerDay * DayMath.dayFactor(day, maxDay);
        return (int) Math.max(0, Math.round(size * multiplier));
    }

    /** Scales the per-player size down evenly so a whole wave stays within {@code maxTotal}. */
    public static int capped(int perPlayer, int players, int maxTotal) {
        if (players <= 0) { return 0; }
        if ((long) perPlayer * players <= maxTotal) { return perPlayer; }
        return Math.max(0, maxTotal / players);
    }
}
