package io.github.beddeak.zombie;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** A boss bar shown to an explicit set of players; {@link #sync} adds and removes viewers. */
public final class WorldBar {
    private final BossBar bar;
    private final Set<UUID> viewers = new HashSet<>();

    public WorldBar(Component name, BossBar.Color color) {
        bar = BossBar.bossBar(name, 1f, color, BossBar.Overlay.PROGRESS);
    }

    public void update(Component name, double progress) {
        bar.name(name);
        bar.progress((float) Math.max(0, Math.min(1, progress)));
    }

    public void color(BossBar.Color color) {
        bar.color(color);
    }

    public void sync(Collection<? extends Player> players) {
        Set<UUID> wanted = new HashSet<>();
        for (Player player : players) {
            wanted.add(player.getUniqueId());
            if (viewers.add(player.getUniqueId())) { player.showBossBar(bar); }
        }
        for (UUID viewer : Set.copyOf(viewers)) {
            if (wanted.contains(viewer)) { continue; }
            viewers.remove(viewer);
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) { player.hideBossBar(bar); }
        }
    }

    public void hide() {
        for (UUID viewer : viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) { player.hideBossBar(bar); }
        }
        viewers.clear();
    }
}
