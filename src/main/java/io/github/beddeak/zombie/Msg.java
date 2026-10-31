package io.github.beddeak.zombie;

import java.time.Duration;
import java.util.Collection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class Msg {
    private Msg() {}

    public static Component info(String text) {
        return prefix().append(Component.text(text, NamedTextColor.WHITE));
    }

    public static Component warn(String text) {
        return prefix().append(Component.text(text, NamedTextColor.YELLOW));
    }

    public static Component alert(String text) {
        return prefix().append(Component.text(text, NamedTextColor.RED));
    }

    public static Component good(String text) {
        return prefix().append(Component.text(text, NamedTextColor.GREEN));
    }

    public static Component prefix() {
        return Component.text("[좀비 아포칼립스] ", NamedTextColor.DARK_RED);
    }

    public static void title(Collection<? extends Player> players, String title, NamedTextColor color, String subtitle) {
        Title shown = Title.title(Component.text(title, color), Component.text(subtitle, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700)));
        for (Player player : players) { player.showTitle(shown); }
    }

    public static void opening(Collection<? extends Player> players) {
        Title shown = Title.title(Component.text("plugin made by beddeak(침댁)", NamedTextColor.GRAY),
                Component.text("좀비 아포칼립스", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(700)));
        for (Player player : players) { player.showTitle(shown); }
    }

    public static void credit(Collection<? extends Player> players, String title, String subtitle) {
        Title shown = Title.title(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD), Component.text(subtitle, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(500)));
        for (Player player : players) { player.showTitle(shown); }
    }

    public static void countdown(Collection<? extends Player> players, String text) {
        Title shown = Title.title(Component.text(text, NamedTextColor.RED), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200)));
        for (Player player : players) { player.showTitle(shown); }
    }

    public static String clock(int seconds) {
        int clamped = Math.max(0, seconds);
        return clamped / 60 + ":" + String.format("%02d", clamped % 60);
    }
}
