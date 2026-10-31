package io.github.beddeak.zombie.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.World;

public final class Effects {
    public interface Effect {
        World world();

        boolean tick();

        void end();
    }

    private final List<Effect> effects = new ArrayList<>();
    private final Logger logger;

    public Effects(Logger logger) {
        this.logger = logger;
    }

    public void add(Effect effect) {
        effects.add(effect);
    }

    public void tick() {
        for (Effect effect : List.copyOf(effects)) {
            if (!effects.contains(effect)) { continue; }
            boolean keep;
            try {
                keep = effect.tick();
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "ZombieApocalypse effect failed: " + effect.getClass().getSimpleName(), exception);
                keep = false;
            }
            if (!keep) { finish(effect); }
        }
    }

    public void endWorld(World world) {
        for (Effect effect : List.copyOf(effects)) {
            if (effect.world().equals(world)) { finish(effect); }
        }
    }

    public void endAll() {
        for (Effect effect : List.copyOf(effects)) { finish(effect); }
    }

    public <T extends Effect> Stream<T> of(Class<T> type) {
        return List.copyOf(effects).stream().filter(type::isInstance).map(type::cast);
    }

    public void end(Effect effect) {
        finish(effect);
    }

    private void finish(Effect effect) {
        if (!effects.remove(effect)) { return; }
        try {
            effect.end();
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "ZombieApocalypse effect cleanup failed: " + effect.getClass().getSimpleName(), exception);
        }
    }
}
