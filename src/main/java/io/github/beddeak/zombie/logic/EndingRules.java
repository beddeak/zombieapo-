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

    public static boolean helicopterLeaves(int standing, int standingAboard, int remainingTicks) {
        return remainingTicks <= 0 || (standing > 0 && standingAboard >= standing);
    }

    public record Credit(String title, String subtitle) {}

    public static List<Credit> credits(int day, List<String> saved, List<String> left, List<String> awards) {
        List<Credit> credits = new ArrayList<>();
        credits.add(new Credit("좀비 아포칼립스", day + "일 동안 살아남았습니다"));
        credits.add(new Credit("탈출한 생존자", saved.isEmpty() ? "아무도 탈출하지 못했습니다" : String.join(", ", saved)));
        if (!left.isEmpty()) { credits.add(new Credit("남겨진 사람들", String.join(", ", left))); }
        for (String award : awards) {
            int colon = award.indexOf(": ");
            credits.add(colon < 0 ? new Credit(award, "") : new Credit(award.substring(0, colon), award.substring(colon + 2)));
        }
        credits.add(new Credit("plugin made by beddeak(침댁)", "플레이해 주셔서 감사합니다"));
        return credits;
    }

    public static int[] column(double centerX, double centerZ, double angle, double distance) {
        return new int[]{(int) Math.floor(centerX + Math.cos(angle) * distance), (int) Math.floor(centerZ + Math.sin(angle) * distance)};
    }
}
