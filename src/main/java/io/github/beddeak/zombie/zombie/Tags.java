package io.github.beddeak.zombie.zombie;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class Tags {
    public static final String HOOK = "hook";
    public static final String ROCK = "rock";
    public static final String ROCK_DISPLAY = "rock_display";
    public static final String SPIT = "spit";
    public static final String ACID = "acid";
    public static final String RADIO_DISPLAY = "radio_display";
    public static final String ESCAPE_MARKER = "escape_marker";

    private Tags() {}

    public static ZombieType type(Entity entity) {
        String value = entity.getPersistentDataContainer().get(Keys.TYPE, PersistentDataType.STRING);
        return value == null ? null : ZombieType.parse(value).orElse(null);
    }

    public static boolean custom(Entity entity) {
        return entity instanceof Zombie && entity.getPersistentDataContainer().has(Keys.TYPE, PersistentDataType.STRING);
    }

    public static void type(Entity entity, ZombieType type) {
        entity.getPersistentDataContainer().set(Keys.TYPE, PersistentDataType.STRING, type.name());
    }

    public static int integer(Entity entity, org.bukkit.NamespacedKey key, int fallback) {
        Integer value = entity.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }

    public static long number(Entity entity, org.bukkit.NamespacedKey key, long fallback) {
        Long value = entity.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return value == null ? fallback : value;
    }

    public static UUID uuid(Entity entity, org.bukkit.NamespacedKey key) {
        String value = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) { return null; }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static void uuid(Entity entity, org.bukkit.NamespacedKey key, UUID value) {
        if (value == null) {
            entity.getPersistentDataContainer().remove(key);
        } else {
            entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, value.toString());
        }
    }

    public static String helper(Entity entity) {
        return entity.getPersistentDataContainer().get(Keys.HELPER, PersistentDataType.STRING);
    }

    public static void helper(Entity entity, String kind) {
        entity.getPersistentDataContainer().set(Keys.HELPER, PersistentDataType.STRING, kind);
    }

    public static void strip(Entity entity) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : container.getKeys()) {
            if (Keys.NAMESPACE.equals(key.getNamespace())) { container.remove(key); }
        }
    }

    public static boolean ready(Entity entity, org.bukkit.NamespacedKey key) {
        return entity.getWorld().getGameTime() >= number(entity, key, Long.MIN_VALUE);
    }

    public static void cooldown(Entity entity, org.bukkit.NamespacedKey key, long ticks) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.LONG, entity.getWorld().getGameTime() + ticks);
    }
}
