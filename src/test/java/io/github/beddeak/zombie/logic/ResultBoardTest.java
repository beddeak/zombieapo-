package io.github.beddeak.zombie.logic;

import static org.junit.jupiter.api.Assertions.*;

import io.github.beddeak.zombie.data.PlayerStats;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultBoardTest {
    private static PlayerStats stats(int sprinters, int tanks, int deaths, int stolen) {
        PlayerStats stats = new PlayerStats();
        for (int index = 0; index < sprinters; index++) { stats.kill(ZombieType.SPRINTER); }
        for (int index = 0; index < tanks; index++) { stats.kill(ZombieType.TANK); }
        stats.kill(ZombieType.BOOMER);
        stats.deaths = deaths;
        stats.stolen = stolen;
        return stats;
    }

    @Test void playersAreRankedByKillsAndAwardsGoToTheLeaders() {
        ResultBoard.Board board = ResultBoard.build(12, List.of(
                new ResultBoard.Entry("Alex", "낙오", stats(3, 0, 5, 2)),
                new ResultBoard.Entry("Steve", "탈출", stats(9, 1, 1, 0))));
        assertEquals("생존 기록 · 12일차 · 처치 15마리 · 사망 6번", board.header());
        assertEquals(List.of(
                "1. Steve [탈출] — 처치 11 · 사망 1 · 주로 스프린터 9",
                "2. Alex [낙오] — 처치 4 · 사망 5 · 주로 스프린터 3"), board.rows());
        assertEquals(List.of(
                "최다 처치: Steve (11마리)",
                "최다 사망: Alex (5번)",
                "탱크 처치: Steve (1마리)",
                "도둑에게 가장 많이 털림: Alex (2번)"), board.awards());
    }

    @Test void tiesShareAnAwardAndEmptyAwardsAreLeftOut() {
        ResultBoard.Board board = ResultBoard.build(1, List.of(
                new ResultBoard.Entry("B", null, stats(0, 0, 0, 0)),
                new ResultBoard.Entry("A", null, stats(0, 0, 0, 0))));
        assertEquals(List.of("1. A — 처치 1 · 사망 0 · 주로 부머 1", "2. B — 처치 1 · 사망 0 · 주로 부머 1"), board.rows());
        assertEquals(List.of("최다 처치: A, B (1마리)"), board.awards());
        assertEquals(List.of("아직 기록이 없습니다."), ResultBoard.build(1, List.of()).rows());
    }
}
