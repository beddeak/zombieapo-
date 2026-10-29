package io.github.beddeak.zombie.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure helpers for the radio ending. */
public final class EndingRules {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Where a radio may stand: on the natural surface, so zombies can always walk up to it. */
    public enum Ground { OK, COVERED, FLOATING, TOO_HIGH }

    /** Solid blocks needed directly under the radio. */
    public static final int SOLID_BELOW = 3;
    /** How far the ground under the radio may sit above the surrounding surface. */
    public static final int HEIGHT_TOLERANCE = 5;
    /** Surface samples needed before the height check applies. */
    public static final int MIN_SAMPLES = 8;

    private EndingRules() {}

    /**
     * @param radioY       y of the radio block
     * @param openAbove    nothing that blocks movement above the radio (leaves are fine)
     * @param solidBelow   solid blocks directly under the radio, counted up to {@link #SOLID_BELOW}
     * @param surroundings surface heights of columns around the radio
     */
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

    /**
     * Arrow pointing from a player's view direction towards a target offset. Minecraft yaw: 0 faces +Z (south)
     * and grows when turning right.
     */
    public static String arrow(float playerYaw, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((targetYaw - playerYaw) % 360 + 540) % 360 - 180;
        int index = (int) Math.round(relative / 45);
        return ARROWS[Math.floorMod(index, 8)];
    }

    /** The antenna touches the radio: directly on top of it or against one of its four sides. Offsets are antenna minus radio. */
    public static boolean attached(int dx, int dy, int dz) {
        if (dy == 1) { return dx == 0 && dz == 0; }
        return dy == 0 && Math.abs(dx) + Math.abs(dz) == 1;
    }

    /**
     * The escape ends when time runs out, or once someone made it and nobody is left to run. {@code stillRunning}
     * counts players waiting to respawn, so one death does not end the escape for them.
     */
    public static boolean escapeOver(int escaped, int stillRunning, int remainingTicks) {
        return remainingTicks <= 0 || (escaped > 0 && stillRunning == 0);
    }

    /** Block column at {@code distance} from a center in the direction {@code angle} (radians). */
    public static int[] column(double centerX, double centerZ, double angle, double distance) {
        return new int[]{(int) Math.floor(centerX + Math.cos(angle) * distance), (int) Math.floor(centerZ + Math.sin(angle) * distance)};
    }
}
