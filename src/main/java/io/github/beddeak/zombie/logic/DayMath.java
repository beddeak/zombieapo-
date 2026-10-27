package io.github.beddeak.zombie.logic;

import java.util.Random;

/** Day numbering and time-of-day rules. A new apocalypse day starts at Minecraft time 0 (sunrise). */
public final class DayMath {
    public static final long DAY = 24_000;
    public static final long NIGHT_START = 13_000;
    public static final long NIGHT_END = 23_000;

    private DayMath() {}

    public static long dayIndex(long fullTime) {
        return Math.floorDiv(fullTime, DAY);
    }

    public static long timeOfDay(long fullTime) {
        return Math.floorMod(fullTime, DAY);
    }

    /** Day number, starting at 1 on the day the apocalypse started. Never below 1 even if time is rewound. */
    public static int day(long fullTime, long startDay) {
        long day = dayIndex(fullTime) - startDay + 1;
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, day));
    }

    /** The start-day value that makes {@code fullTime} fall on {@code day}. */
    public static long startDayFor(long fullTime, int day) {
        return dayIndex(fullTime) - (day - 1L);
    }

    public static boolean night(long timeOfDay) {
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
    }

    public static boolean bloodMoonNight(int day, int everyDays, long timeOfDay) {
        return everyDays > 0 && day % everyDays == 0 && night(timeOfDay);
    }

    /** Full time of the next morning (time of day 23000) strictly after {@code fullTime}'s current night started. */
    public static long nextMorning(long fullTime) {
        long tod = timeOfDay(fullTime);
        long base = fullTime - tod;
        return tod < NIGHT_END ? base + NIGHT_END : base + DAY + NIGHT_END;
    }

    public static boolean everyNth(int day, int every) {
        return every > 0 && day % every == 0;
    }

    /** Picks a random tick in the rest of the current day, at least 30 seconds from now. */
    public static long pickLaterToday(long timeOfDayNow, Random random) {
        long from = Math.min(DAY - 1, timeOfDayNow + 600);
        return from + (long) (random.nextDouble() * (DAY - from));
    }

    /** 0 on day 1, growing by one per day until {@code maxDay}. */
    public static int dayFactor(int day, int maxDay) {
        return Math.max(0, Math.min(day, maxDay) - 1);
    }
}
