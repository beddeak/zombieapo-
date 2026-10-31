package io.github.beddeak.zombie.logic;

import io.github.beddeak.zombie.data.PlayerStats;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class ResultBoard {
    public record Entry(String name, String tag, PlayerStats stats) {}

    public record Board(String header, List<String> rows, List<String> awards) {}

    private ResultBoard() {}

    public static Board build(int day, List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt((Entry entry) -> -entry.stats().totalKills())
                .thenComparingInt(entry -> entry.stats().deaths).thenComparing(Entry::name));
        int kills = sorted.stream().mapToInt(entry -> entry.stats().totalKills()).sum();
        int deaths = sorted.stream().mapToInt(entry -> entry.stats().deaths).sum();
        String header = "생존 기록 · " + day + "일차 · 처치 " + kills + "마리 · 사망 " + deaths + "번";

        List<String> rows = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            Entry entry = sorted.get(index);
            PlayerStats stats = entry.stats();
            StringBuilder row = new StringBuilder().append(index + 1).append(". ").append(entry.name());
            if (entry.tag() != null) { row.append(" [").append(entry.tag()).append("]"); }
            row.append(" — 처치 ").append(stats.totalKills()).append(" · 사망 ").append(stats.deaths);
            Map.Entry<ZombieType, Integer> favorite = favorite(stats);
            if (favorite != null) {
                row.append(" · 주로 ").append(favorite.getKey().displayName()).append(" ").append(favorite.getValue());
            }
            rows.add(row.toString());
        }
        if (rows.isEmpty()) { rows.add("아직 기록이 없습니다."); }

        List<String> awards = new ArrayList<>();
        award(awards, sorted, "최다 처치", "마리", PlayerStats::totalKills);
        award(awards, sorted, "최다 사망", "번", stats -> stats.deaths);
        award(awards, sorted, "탱크 처치", "마리", stats -> stats.kills(ZombieType.TANK));
        award(awards, sorted, "도둑에게 가장 많이 털림", "번", stats -> stats.stolen);
        return new Board(header, rows, awards);
    }

    private static Map.Entry<ZombieType, Integer> favorite(PlayerStats stats) {
        return stats.kills.entrySet().stream().filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.<ZombieType, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .orElse(null);
    }

    private static void award(List<String> awards, List<Entry> entries, String title, String unit, ToIntFunction<PlayerStats> score) {
        int best = entries.stream().mapToInt(entry -> score.applyAsInt(entry.stats())).max().orElse(0);
        if (best <= 0) { return; }
        List<String> names = entries.stream().filter(entry -> score.applyAsInt(entry.stats()) == best).map(Entry::name).toList();
        awards.add(title + ": " + String.join(", ", names) + " (" + best + unit + ")");
    }
}
