package io.github.beddeak.zombie.data;

import io.github.beddeak.zombie.zombie.ZombieType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** data.yml: everything that must survive a restart. World names are keys, so '|' is used as the path separator. */
public final class DataStore {
    public record PendingMode(GameMode mode, String world, BlockPos pos) {}

    private final File file;
    private final Logger logger;
    private final Map<String, WorldData> worlds = new LinkedHashMap<>();
    private final Map<String, BlockPos> radios = new HashMap<>();
    private final Map<String, BlockPos> antennas = new HashMap<>();
    private final Map<UUID, UUID> playerZombies = new HashMap<>();
    private final Map<UUID, List<String>> pendingItems = new HashMap<>();
    private final Map<UUID, PendingMode> pendingModes = new HashMap<>();

    public DataStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Active worlds by name. */
    public Map<String, WorldData> worlds() { return worlds; }
    /** Radio block per world name. Kept across stop/start so a placed radio keeps working. */
    public Map<String, BlockPos> radios() { return radios; }
    /** Antenna block per world name, kept like the radio. */
    public Map<String, BlockPos> antennas() { return antennas; }
    /** Owner UUID to the UUID of their current player zombie. */
    public Map<UUID, UUID> playerZombies() { return playerZombies; }
    /** Items stolen by thieves that could not be returned yet, serialized with {@code ItemStack#serializeAsBytes}. */
    public Map<UUID, List<String>> pendingItems() { return pendingItems; }
    /** Escaped spectators whose game mode must be restored on their next join. */
    public Map<UUID, PendingMode> pendingModes() { return pendingModes; }

    public void load() {
        worlds.clear();
        radios.clear();
        antennas.clear();
        playerZombies.clear();
        pendingItems.clear();
        pendingModes.clear();
        if (!file.exists()) { return; }
        YamlConfiguration yaml = newYaml();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            File broken = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
            logger.log(Level.SEVERE, "data.yml could not be read; starting with empty state. Moved it to " + broken.getName(), exception);
            if (!file.renameTo(broken)) { logger.warning("Could not move the broken data.yml aside"); }
            return;
        }
        ConfigurationSection worldSection = yaml.getConfigurationSection("worlds");
        if (worldSection != null) {
            for (String name : worldSection.getKeys(false)) {
                ConfigurationSection section = worldSection.getConfigurationSection(name);
                if (section != null) { worlds.put(name, readWorld(section)); }
            }
        }
        ConfigurationSection radioSection = yaml.getConfigurationSection("radios");
        if (radioSection != null) {
            for (String name : radioSection.getKeys(false)) {
                BlockPos pos = readPos(radioSection.getIntegerList(name));
                if (pos != null) { radios.put(name, pos); }
            }
        }
        ConfigurationSection antennaSection = yaml.getConfigurationSection("antennas");
        if (antennaSection != null) {
            for (String name : antennaSection.getKeys(false)) {
                BlockPos pos = readPos(antennaSection.getIntegerList(name));
                if (pos != null) { antennas.put(name, pos); }
            }
        }
        ConfigurationSection zombies = yaml.getConfigurationSection("player-zombies");
        if (zombies != null) {
            for (String owner : zombies.getKeys(false)) {
                UUID ownerId = uuid(owner);
                UUID zombieId = uuid(zombies.getString(owner));
                if (ownerId != null && zombieId != null) { playerZombies.put(ownerId, zombieId); }
            }
        }
        ConfigurationSection items = yaml.getConfigurationSection("pending-items");
        if (items != null) {
            for (String owner : items.getKeys(false)) {
                UUID ownerId = uuid(owner);
                List<String> encoded = items.getStringList(owner);
                if (ownerId != null && !encoded.isEmpty()) { pendingItems.put(ownerId, new ArrayList<>(encoded)); }
            }
        }
        ConfigurationSection modes = yaml.getConfigurationSection("pending-modes");
        if (modes != null) {
            for (String player : modes.getKeys(false)) {
                ConfigurationSection section = modes.getConfigurationSection(player);
                UUID playerId = uuid(player);
                GameMode mode = section == null ? null : gameMode(section.getString("mode"));
                if (playerId != null && mode != null) {
                    pendingModes.put(playerId, new PendingMode(mode, section.getString("world", ""),
                            readPos(section.getIntegerList("pos"))));
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = newYaml();
        ConfigurationSection worldSection = yaml.createSection("worlds");
        worlds.forEach((name, data) -> writeWorld(worldSection.createSection(name), data));
        ConfigurationSection radioSection = yaml.createSection("radios");
        radios.forEach((name, pos) -> radioSection.set(name, writePos(pos)));
        ConfigurationSection antennaSection = yaml.createSection("antennas");
        antennas.forEach((name, pos) -> antennaSection.set(name, writePos(pos)));
        ConfigurationSection zombies = yaml.createSection("player-zombies");
        playerZombies.forEach((owner, zombie) -> zombies.set(owner.toString(), zombie.toString()));
        ConfigurationSection items = yaml.createSection("pending-items");
        pendingItems.forEach((owner, encoded) -> items.set(owner.toString(), encoded));
        ConfigurationSection modes = yaml.createSection("pending-modes");
        pendingModes.forEach((player, pending) -> {
            ConfigurationSection section = modes.createSection(player.toString());
            section.set("mode", pending.mode().name());
            section.set("world", pending.world());
            if (pending.pos() != null) { section.set("pos", writePos(pending.pos())); }
        });
        try {
            Path target = file.toPath();
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            replace(temporary, target);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Could not save data.yml", exception);
        }
    }

    /**
     * Moves the temporary file over data.yml. On Windows the target can be briefly held by another process (virus
     * scanners right after a write), so a few retries are made before copying over the file in place.
     */
    private static void replace(Path temporary, Path target) throws IOException {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (FileSystemException exception) {
                try {
                    Thread.sleep(20L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Files.copy(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(temporary);
    }

    private static WorldData readWorld(ConfigurationSection section) {
        WorldData data = new WorldData(section.getLong("start-day"));
        data.lastDayShown = section.getInt("last-day-shown");
        ConfigurationSection horde = section.getConfigurationSection("horde");
        if (horde != null) {
            data.hordeDay = horde.getInt("day");
            data.hordeTime = horde.getLong("time");
            data.hordeDone = horde.getBoolean("done");
        }
        data.tankDay = section.getInt("tank-day");
        data.bloodMoonTankDay = section.getInt("blood-moon-tank-day");
        data.preparingTicks = Math.max(0, section.getInt("preparing-ticks"));
        data.preparingTotal = Math.max(data.preparingTicks, section.getInt("preparing-total"));
        data.quietSurfaceTicks = Math.max(0, section.getInt("quiet-surface-ticks"));
        ConfigurationSection assembly = section.getConfigurationSection("assembly");
        if (assembly != null && assembly.getString("item") != null) {
            data.assemblyItem = assembly.getString("item");
            data.assemblyPos = readPos(assembly.getIntegerList("pos"));
            data.assemblyRemaining = Math.max(0, assembly.getInt("remaining-ticks"));
            data.assemblyTotal = Math.max(data.assemblyRemaining, assembly.getInt("total-ticks"));
            data.assemblyCrafter = uuid(assembly.getString("crafter"));
            if (data.assemblyPos == null) { data.assemblyItem = null; }
        }
        ConfigurationSection ending = section.getConfigurationSection("ending");
        if (ending != null) {
            EndingData target = data.ending;
            try {
                target.phase = EndingData.Phase.valueOf(ending.getString("phase", "NONE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                target.phase = EndingData.Phase.NONE;
            }
            target.totalTicks = ending.getInt("total-ticks");
            target.remainingTicks = ending.getInt("remaining-ticks");
            target.tanksSpawned.addAll(ending.getIntegerList("tanks-spawned"));
            target.escapePoint = readPos(ending.getIntegerList("escape-point"));
            for (String id : ending.getStringList("escaped")) {
                UUID uuid = uuid(id);
                if (uuid != null) { target.escaped.add(uuid); }
            }
            ConfigurationSection modes = ending.getConfigurationSection("original-modes");
            if (modes != null) {
                for (String id : modes.getKeys(false)) {
                    UUID uuid = uuid(id);
                    GameMode mode = gameMode(modes.getString(id));
                    if (uuid != null && mode != null) { target.originalModes.put(uuid, mode); }
                }
            }
        }
        ConfigurationSection stats = section.getConfigurationSection("stats");
        if (stats != null) {
            for (String id : stats.getKeys(false)) {
                UUID uuid = uuid(id);
                ConfigurationSection entry = stats.getConfigurationSection(id);
                if (uuid == null || entry == null) { continue; }
                PlayerStats target = data.stats(uuid);
                target.deaths = Math.max(0, entry.getInt("deaths"));
                target.stolen = Math.max(0, entry.getInt("stolen"));
                ConfigurationSection kills = entry.getConfigurationSection("kills");
                if (kills == null) { continue; }
                for (String key : kills.getKeys(false)) {
                    int count = kills.getInt(key);
                    if (count > 0) { ZombieType.parse(key).ifPresent(type -> target.kills.put(type, count)); }
                }
            }
        }
        return data;
    }

    private static void writeWorld(ConfigurationSection section, WorldData data) {
        section.set("start-day", data.startDay);
        section.set("last-day-shown", data.lastDayShown);
        ConfigurationSection horde = section.createSection("horde");
        horde.set("day", data.hordeDay);
        horde.set("time", data.hordeTime);
        horde.set("done", data.hordeDone);
        section.set("tank-day", data.tankDay);
        section.set("blood-moon-tank-day", data.bloodMoonTankDay);
        section.set("preparing-ticks", data.preparingTicks);
        section.set("preparing-total", data.preparingTotal);
        section.set("quiet-surface-ticks", data.quietSurfaceTicks);
        if (data.assemblyItem != null && data.assemblyPos != null) {
            ConfigurationSection assembly = section.createSection("assembly");
            assembly.set("item", data.assemblyItem);
            assembly.set("pos", writePos(data.assemblyPos));
            assembly.set("remaining-ticks", data.assemblyRemaining);
            assembly.set("total-ticks", data.assemblyTotal);
            if (data.assemblyCrafter != null) { assembly.set("crafter", data.assemblyCrafter.toString()); }
        }
        EndingData ending = data.ending;
        ConfigurationSection target = section.createSection("ending");
        target.set("phase", ending.phase.name());
        target.set("total-ticks", ending.totalTicks);
        target.set("remaining-ticks", ending.remainingTicks);
        target.set("tanks-spawned", new ArrayList<>(ending.tanksSpawned));
        if (ending.escapePoint != null) { target.set("escape-point", writePos(ending.escapePoint)); }
        target.set("escaped", ending.escaped.stream().map(UUID::toString).toList());
        ConfigurationSection modes = target.createSection("original-modes");
        ending.originalModes.forEach((id, mode) -> modes.set(id.toString(), mode.name()));
        ConfigurationSection stats = section.createSection("stats");
        data.stats.forEach((id, player) -> {
            ConfigurationSection entry = stats.createSection(id.toString());
            entry.set("deaths", player.deaths);
            entry.set("stolen", player.stolen);
            ConfigurationSection kills = entry.createSection("kills");
            player.kills.forEach((type, count) -> kills.set(type.configKey(), count));
        });
    }

    private static YamlConfiguration newYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('|');
        return yaml;
    }

    private static List<Integer> writePos(BlockPos pos) {
        return List.of(pos.x(), pos.y(), pos.z());
    }

    private static BlockPos readPos(List<Integer> values) {
        return values.size() == 3 ? new BlockPos(values.get(0), values.get(1), values.get(2)) : null;
    }

    private static UUID uuid(String text) {
        if (text == null) { return null; }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static GameMode gameMode(String text) {
        if (text == null) { return null; }
        try {
            return GameMode.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
