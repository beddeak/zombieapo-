package io.github.beddeak.zombie;

import io.github.beddeak.zombie.command.ApocalypseCommand;
import io.github.beddeak.zombie.config.LiveConfig;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.listener.CombatListener;
import io.github.beddeak.zombie.listener.EggListener;
import io.github.beddeak.zombie.listener.PlayerListener;
import io.github.beddeak.zombie.listener.RadioListener;
import io.github.beddeak.zombie.listener.ZombieListener;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZombieApocalypsePlugin extends JavaPlugin {
    private Apocalypse apocalypse;

    @Override public void onEnable() {
        Settings settings;
        try {
            settings = applyWarnings(loadSettings());
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "config.yml could not be read; running with default settings", exception);
            settings = Settings.defaults();
        }
        apocalypse = new Apocalypse(this, settings);
        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new ZombieListener(apocalypse), this);
        plugins.registerEvents(new CombatListener(apocalypse), this);
        plugins.registerEvents(new PlayerListener(apocalypse), this);
        plugins.registerEvents(new RadioListener(apocalypse), this);
        plugins.registerEvents(new EggListener(apocalypse), this);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(getResource("config.yml"), "Missing bundled config.yml"), StandardCharsets.UTF_8));
        LiveConfig live = new LiveConfig(new File(getDataFolder(), "config.yml"), defaults);
        ApocalypseCommand executor = new ApocalypseCommand(apocalypse, this::reloadSettings, live, getLogger());
        var command = Objects.requireNonNull(getCommand("apocalypse"), "Missing apocalypse command descriptor");
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        apocalypse.enable();
        getLogger().info("좀비 아포칼립스 / mod by beddeak(침댁) enabled");
    }

    @Override public void onDisable() {
        try {
            if (apocalypse != null) { apocalypse.shutdown(); }
        } finally {
            apocalypse = null;
            getServer().getScheduler().cancelTasks(this);
            HandlerList.unregisterAll(this);
        }
    }

    private Settings.Loaded loadSettings() throws Exception {
        saveDefaultConfig();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), "config.yml"));
        return Settings.load(yaml);
    }

    private Settings applyWarnings(Settings.Loaded loaded) {
        for (String warning : loaded.warnings()) { getLogger().warning("config.yml " + warning); }
        return loaded.settings();
    }

    /** Parses first, so a syntax error keeps the previous settings. */
    private List<String> reloadSettings() throws Exception {
        Settings.Loaded loaded = loadSettings();
        apocalypse.settings(applyWarnings(loaded));
        reloadConfig();
        return loaded.warnings();
    }
}
