package io.github.beddeak.zombie.config;

import static org.junit.jupiter.api.Assertions.*;

import io.github.beddeak.zombie.zombie.ZombieType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

class SettingsTest {
    static YamlConfiguration bundled() throws Exception {
        try (var stream = Objects.requireNonNull(SettingsTest.class.getResourceAsStream("/config.yml"))) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static Settings.Loaded load(String yaml) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return Settings.load(configuration);
    }

    @Test void bundledConfigHasNoWarningsAndMatchesCodeDefaults() throws Exception {
        Settings.Loaded loaded = Settings.load(bundled());
        assertEquals(List.of(), loaded.warnings());
        assertEquals(Settings.defaults(), loaded.settings());
    }

    @Test void bundledConfigListsEveryRandomType() throws Exception {
        YamlConfiguration yaml = bundled();
        for (ZombieType type : ZombieType.values()) {
            assertEquals(type.random(), yaml.isConfigurationSection("types." + type.configKey()), type.name());
        }
    }

    @Test void defaultsFollowThePlan() {
        Settings settings = Settings.defaults();
        assertTrue(settings.replace().reasons().containsAll(List.of(SpawnReason.NATURAL, SpawnReason.SPAWNER,
                SpawnReason.TRIAL_SPAWNER, SpawnReason.PATROL)));
        assertFalse(settings.replace().reasons().contains(SpawnReason.SPAWNER_EGG));
        assertTrue(settings.replace().exclude().contains(EntityType.ENDER_DRAGON));
        assertEquals(300, settings.ending().finale().seconds());
        assertEquals(100, settings.ending().escape().distance());
        assertEquals(180, settings.ending().escape().seconds());
        assertEquals(List.of("DRD", "RHR", "TBT"), settings.ending().radioRecipe().rows());
        assertEquals(java.util.Map.of('D', Material.DIAMOND_BLOCK, 'R', Material.REDSTONE_BLOCK, 'H', Material.HEART_OF_THE_SEA,
                'T', Material.REDSTONE_TORCH, 'B', Material.NOTE_BLOCK), settings.ending().radioRecipe().keys());
        assertTrue(settings.tank().loot().stream().noneMatch(loot -> loot.material() == Material.HEART_OF_THE_SEA || loot.material() == Material.DIAMOND));
        assertEquals(3, settings.horde().regularEveryDays());
        assertEquals(6, settings.start().graceSeconds());
        assertEquals(180, settings.start().quietSurfaceSeconds());
        assertEquals(Material.END_ROD, settings.ending().antennaBlock());
        assertEquals(List.of(" C ", "BRB", " C "), settings.ending().antennaRecipe().rows());
        assertEquals(java.util.Map.of('C', Material.IRON_CHAIN, 'B', Material.BLAZE_ROD, 'R', Material.REDSTONE),
                settings.ending().antennaRecipe().keys());
        assertEquals(java.util.Set.of(EntityType.BLAZE), settings.replace().keepFromSpawner());
        assertEquals(90, settings.ending().assembly().radioSeconds());
        assertEquals(45, settings.ending().assembly().antennaSeconds());
        assertEquals(8, settings.ending().finale().height());
    }

    @Test void invalidValuesFallBackWithWarnings() throws Exception {
        Settings.Loaded loaded = load("""
                spawning:
                  monster-spawn-limit: -5
                  extra:
                    min-distance: 50
                    max-distance: 10
                types:
                  boomer:
                    power: "big"
                    weight: 2.5
                horde:
                  waves: [5, 1]
                tank:
                  loot: [DIAMOND:abc, NOT_A_THING:2, EMERALD:4]
                replace:
                  exclude: [CREEPER, UNICORN]
                """);
        Settings settings = loaded.settings();
        assertEquals(150, settings.spawning().monsterSpawnLimit());
        assertEquals(20, settings.spawning().minDistance());
        assertEquals(40, settings.spawning().maxDistance());
        assertEquals(3.0, settings.types().boomer().power());
        assertEquals(8, settings.types().spawn(ZombieType.BOOMER).weight());
        assertEquals(2, settings.horde().wavesMin());
        assertEquals(3, settings.horde().wavesMax());
        assertEquals(List.of(new Settings.Loot(Material.EMERALD, 4)), settings.tank().loot());
        assertEquals(java.util.Set.of(EntityType.CREEPER), settings.replace().exclude());
        assertEquals(8, loaded.warnings().size(), String.join("\n", loaded.warnings()));
    }

    @Test void unknownKeysAndWrongSectionsAreReported() throws Exception {
        Settings.Loaded loaded = load("""
                spawnin: {}
                zombie: 5
                types:
                  boomer:
                    powr: 9
                """);
        assertTrue(loaded.warnings().stream().anyMatch(warning -> warning.startsWith("spawnin:")));
        assertTrue(loaded.warnings().stream().anyMatch(warning -> warning.startsWith("zombie:")));
        assertTrue(loaded.warnings().stream().anyMatch(warning -> warning.startsWith("types.boomer.powr:")));
        assertEquals(40, loaded.settings().zombie().followRange());
    }

    @Test void allZeroWeightsFallBackToDefaults() throws Exception {
        StringBuilder yaml = new StringBuilder("types:\n");
        for (ZombieType type : ZombieType.values()) {
            if (type.random()) { yaml.append("  ").append(type.configKey()).append(":\n    weight: 0\n"); }
        }
        Settings.Loaded loaded = load(yaml.toString());
        assertEquals(40, loaded.settings().types().spawn(ZombieType.DEFAULT).weight());
        assertTrue(loaded.warnings().stream().anyMatch(warning -> warning.contains("가중치")));
    }

    @Test void brokenRecipeShapesFallBackToTheDefault() throws Exception {
        for (String broken : java.util.List.of(
                "radio-recipe: {shape: [\"DD\", \"DDD\"], keys: {D: DIAMOND_BLOCK}}",
                "radio-recipe: {shape: [\"DX\"], keys: {D: DIAMOND_BLOCK}}",
                "radio-recipe: {shape: [\"D\"], keys: {D: NOT_A_THING}}",
                "radio-recipe: {shape: [\"D\", \"D\", \"D\", \"D\"], keys: {D: DIRT}}",
                "radio-recipe: {shape: [\"   \"], keys: {D: DIRT}}",
                "radio-recipe: {shape: [\"D\"]}")) {
            Settings.Loaded loaded = load("ending:\n  " + broken + "\n");
            assertEquals(Settings.defaults().ending().radioRecipe(), loaded.settings().ending().radioRecipe(), broken);
            assertTrue(loaded.warnings().stream().anyMatch(warning -> warning.startsWith("ending.radio-recipe")), broken);
        }
    }

    @Test void customShapesKeepSpacesAndOnlyTheSymbolsTheyUse() throws Exception {
        Settings.Loaded loaded = load("""
                ending:
                  antenna-recipe:
                    shape: [" I ", " I "]
                    keys: {I: IRON_INGOT, G: GOLD_INGOT}
                """);
        assertEquals(List.of(), loaded.warnings());
        assertEquals(List.of(" I ", " I "), loaded.settings().ending().antennaRecipe().rows());
        assertEquals(java.util.Map.of('I', Material.IRON_INGOT), loaded.settings().ending().antennaRecipe().keys());
        assertNull(Settings.shapeProblem(List.of("AB", "BA"), java.util.Set.of('A', 'B')));
    }
}
