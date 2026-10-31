package io.github.beddeak.zombie.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EndingRules {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public enum Ground { OK, COVERED, FLOATING, TOO_HIGH }

    public static final int SOLID_BELOW = 3;
    public static final int HEIGHT_TOLERANCE = 5;
    public static final int MIN_SAMPLES = 8;

    private EndingRules() {}

    public static Ground ground(int radioY, boolean openAbove, int solidBelow, List<Integer> surroundings) {
        if (!openAbove) { return Ground.COVERED; }
        if (solidBelow < SOLID_BELOW) { return Ground.FLOATING; }
        if (surroundings.size() >= MIN_SAMPLES) {
            List<Integer> sorted = new ArrayList<>(surroundings);
            Collections.sort(sorted);
            int median = sorted.get(sorted.size() / 2);
            if (radioY - 1 - median > HEIGHT_TOLERANCE) { return Ground.TOO_HIGH; }
        }
        return Ground.OK;
    }

    public static String arrow(float playerYaw, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((targetYaw - playerYaw) % 360 + 540) % 360 - 180;
        int index = (int) Math.round(relative / 45);
        return ARROWS[Math.floorMod(index, 8)];
    }

    public static boolean attached(int dx, int dy, int dz) {
        if (dy == 1) { return dx == 0 && dz == 0; }
        return dy == 0 && Math.abs(dx) + Math.abs(dz) == 1;
    }

    public static boolean escapeOver(int escaped, int stillRunning, int remainingTicks) {
        return remainingTicks <= 0 || (escaped > 0 && stillRunning == 0);
    }

    public static int[] column(double centerX, double centerZ, double angle, double distance) {
        return new int[]{(int) Math.floor(centerX + Math.cos(angle) * distance), (int) Math.floor(centerZ + Math.sin(angle) * distance)};
    }
}
