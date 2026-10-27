package io.github.beddeak.zombie.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class DayMathTest {
    @Test void daysStartAtSunriseAndCountFromTheStartDay() {
        long start = DayMath.dayIndex(5 * DayMath.DAY + 18_000);
        assertEquals(1, DayMath.day(5 * DayMath.DAY + 18_000, start));
        assertEquals(1, DayMath.day(5 * DayMath.DAY + 23_999, start));
        assertEquals(2, DayMath.day(6 * DayMath.DAY, start));
        assertEquals(4, DayMath.day(8 * DayMath.DAY + 100, start));
    }

    @Test void rewindingTimeNeverGoesBelowDayOne() {
        assertEquals(1, DayMath.day(0, 10));
    }

    @Test void setDayKeepsTheTimeOfDay() {
        long now = 42 * DayMath.DAY + 15_000;
        long start = DayMath.startDayFor(now, 7);
        assertEquals(7, DayMath.day(now, start));
        assertEquals(8, DayMath.day(now + DayMath.DAY, start));
    }

    @Test void bloodMoonIsTheNightOfEveryNthDay() {
        assertTrue(DayMath.bloodMoonNight(7, 7, 13_000));
        assertTrue(DayMath.bloodMoonNight(14, 7, 22_999));
        assertFalse(DayMath.bloodMoonNight(7, 7, 12_999));
        assertFalse(DayMath.bloodMoonNight(7, 7, 23_000));
        assertFalse(DayMath.bloodMoonNight(6, 7, 18_000));
        assertFalse(DayMath.bloodMoonNight(7, 0, 18_000));
    }

    @Test void nextMorningIsTheComingTime23000() {
        assertEquals(10 * DayMath.DAY + 23_000, DayMath.nextMorning(10 * DayMath.DAY + 14_000));
        assertEquals(10 * DayMath.DAY + 23_000, DayMath.nextMorning(10 * DayMath.DAY + 1_000));
        assertEquals(11 * DayMath.DAY + 23_000, DayMath.nextMorning(10 * DayMath.DAY + 23_500));
    }

    @Test void laterTodayStaysInsideTheRestOfTheDay() {
        Random random = new Random(1);
        for (int index = 0; index < 1000; index++) {
            long now = random.nextInt((int) DayMath.DAY);
            long picked = DayMath.pickLaterToday(now, random);
            assertTrue(picked >= Math.min(DayMath.DAY - 1, now + 600) && picked < DayMath.DAY, now + " -> " + picked);
        }
    }

    @Test void dayFactorGrowsUntilTheCap() {
        assertEquals(0, DayMath.dayFactor(1, 30));
        assertEquals(4, DayMath.dayFactor(5, 30));
        assertEquals(29, DayMath.dayFactor(100, 30));
        assertTrue(DayMath.everyNth(9, 3));
        assertFalse(DayMath.everyNth(9, 0));
    }
}
