package io.github.beddeak.zombie.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Changes one config.yml value while the server runs ({@code /apocalypse set}). The change is checked like a reload:
 * a value that would fall back to the default is refused and nothing is written. Only keys of the bundled config can
 * be set, so a server config without newer keys can still get them. The first change keeps a copy of the original
 * file as {@code config.yml.bak}.
 */
public final class LiveConfig {
    /** Result of {@link #set}: the settings to apply, or why nothing changed. */
    public record Change(Settings settings, Object previous, Object value, String problem) {}

    private final File file;
    private final YamlConfiguration defaults;
    private final List<String> keys;

    public LiveConfig(File file, YamlConfiguration defaults) {
        this.file = file;
        this.defaults = defaults;
        List<String> leaves = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key)) { leaves.add(key); }
        }
        this.keys = List.copyOf(leaves);
    }

    /** Every key that can be set, in config.yml order. */
    public List<String> keys() {
        return keys;
    }

    public boolean known(String key) {
        return keys.contains(key);
    }

    /** The value in effect: config.yml's, or the default when the file does not have the key. */
    public Object value(String key) throws IOException, InvalidConfigurationException {
        YamlConfiguration current = current();
        return current.contains(key) ? current.get(key) : defaults.get(key);
    }

    /** Sets {@code key} to {@code text} read as YAML, checks it and saves config.yml when it is valid. */
    public Change set(String key, String text) throws IOException, InvalidConfigurationException {
        YamlConfiguration current = current();
        Object previous = current.contains(key) ? current.get(key) : defaults.get(key);
        Object value = parse(text);
        List<String> before = Settings.load(current).warnings();
        current.set(key, value);
        Settings.Loaded loaded = Settings.load(current);
        List<String> added = new ArrayList<>(loaded.warnings());
        added.removeAll(before);
        if (!added.isEmpty()) { return new Change(null, previous, value, added.get(0)); }
        File backup = new File(file.getParentFile(), file.getName() + ".bak");
        if (file.exists() && !backup.exists()) { Files.copy(file.toPath(), backup.toPath()); }
        current.save(file);
        return new Change(loaded.settings(), previous, value, null);
    }

    /** Tab completion one level at a time: {@code sp} gives {@code spawning.}, which then gives its children. */
    public List<String> complete(String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        Set<String> options = new LinkedHashSet<>();
        for (String key : keys) {
            if (!key.toLowerCase(Locale.ROOT).startsWith(prefix)) { continue; }
            int dot = key.indexOf('.', prefix.length());
            options.add(dot < 0 ? key : key.substring(0, dot + 1));
        }
        return List.copyOf(options);
    }

    /** Reads a typed value the way config.yml would: numbers, true/false and [lists]; anything else stays text. */
    static Object parse(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("value: " + text);
        } catch (InvalidConfigurationException exception) {
            return text;
        }
        Object value = yaml.get("value");
        return value == null || value instanceof ConfigurationSection ? text : value;
    }

    private YamlConfiguration current() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) { yaml.load(file); }
        return yaml;
    }
}
