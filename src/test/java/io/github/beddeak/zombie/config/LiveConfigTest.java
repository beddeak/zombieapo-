package io.github.beddeak.zombie.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveConfigTest {
    @TempDir Path folder;

    private static String bundledText() throws Exception {
        try (var stream = Objects.requireNonNull(LiveConfigTest.class.getResourceAsStream("/config.yml"))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private LiveConfig live(String text) throws Exception {
        File file = folder.resolve("config.yml").toFile();
        if (text != null) { Files.writeString(file.toPath(), text, StandardCharsets.UTF_8); }
        return new LiveConfig(file, SettingsTest.bundled());
    }

    private String saved(String name) throws Exception {
        return Files.readString(folder.resolve(name), StandardCharsets.UTF_8);
    }

    @Test void keysComeFromTheBundledConfigAndCompleteOneLevelAtATime() throws Exception {
        LiveConfig live = live(null);
        assertTrue(live.known("spawning.extra.per-player-cap"));
        assertTrue(live.known("types.witch.rage-seconds"));
        assertFalse(live.known("spawning"));
        assertFalse(live.known("spawning.extra.nope"));
        assertEquals(List.of("spawning."), live.complete("sp"));
        assertTrue(live.complete("spawning.").containsAll(List.of("spawning.monster-spawn-limit", "spawning.extra.", "spawning.daylight.")));
        assertEquals(List.of("spawning.daylight.cap-multiplier"), live.complete("spawning.daylight.cap"));
        assertEquals(40, live.value("spawning.extra.per-player-cap"));
    }

    @Test void validChangeAppliesIsSavedWithCommentsAndKeepsTheFirstOriginal() throws Exception {
        String original = bundledText();
        LiveConfig live = live(original);
        LiveConfig.Change change = live.set("spawning.extra.per-player-cap", "60");
        assertNull(change.problem());
        assertEquals(60, change.settings().spawning().perPlayerCap());
        assertEquals(40, change.previous());
        assertEquals(60, live.value("spawning.extra.per-player-cap"));
        assertTrue(saved("config.yml").contains("바닐라 몬스터 스폰 상한과 스폰 주기"), "comments are kept");
        assertEquals(original, saved("config.yml.bak"));
        assertNull(live.set("spawning.extra.per-player-cap", "70").problem());
        assertEquals(original, saved("config.yml.bak"), "the backup keeps the file from before the first change");
        assertNull(live.set("ending.message", "다들 수고하셨습니다").problem());
        assertEquals("다들 수고하셨습니다", live.value("ending.message"));
    }

    @Test void invalidValuesAreRefusedAndNothingIsWritten() throws Exception {
        String original = bundledText();
        LiveConfig live = live(original);
        for (String value : List.of("많이", "-5", "1.5", "[1, 2]")) {
            LiveConfig.Change change = live.set("spawning.extra.per-player-cap", value);
            assertNotNull(change.problem(), value);
            assertNull(change.settings(), value);
        }
        assertNotNull(live.set("spawning.daylight.enabled", "maybe").problem());
        assertEquals(original, saved("config.yml"));
        assertFalse(Files.exists(folder.resolve("config.yml.bak")));
    }

    @Test void keysMissingFromAnOlderConfigUseTheDefaultAndCanBeAdded() throws Exception {
        LiveConfig live = live("spawning:\n  extra:\n    per-player-cap: 30\n");
        assertEquals(0.25, live.value("spawning.daylight.cap-multiplier"));
        assertEquals(30, live.value("spawning.extra.per-player-cap"));
        LiveConfig.Change change = live.set("spawning.daylight.per-attempt", "0");
        assertNull(change.problem());
        assertEquals(0, change.settings().spawning().daylight().perAttempt());
        assertEquals(30, change.settings().spawning().perPlayerCap());
        assertEquals(0, live.value("spawning.daylight.per-attempt"));
    }

    @Test void typedValuesAreReadLikeYaml() {
        assertEquals(true, LiveConfig.parse("true"));
        assertEquals(1.5, LiveConfig.parse("1.5"));
        assertEquals(12, LiveConfig.parse("12"));
        assertEquals(List.of("ZOMBIE", "HUSK"), LiveConfig.parse("[ZOMBIE, HUSK]"));
        assertEquals("구조 헬기가 왔습니다", LiveConfig.parse("구조 헬기가 왔습니다"));
    }
}
