package io.github.beddeak.zombie.zombie;

import org.bukkit.NamespacedKey;

/** Persistent data and attribute modifier keys. Everything this plugin owns uses the {@code zombieapoc} namespace. */
public final class Keys {
    public static final String NAMESPACE = "zombieapoc";

    public static final NamespacedKey TYPE = key("type");
    public static final NamespacedKey GENERATION = key("generation");
    public static final NamespacedKey HORDE = key("horde");
    /** Necromancer UUID on raised zombies, player UUID on player zombies. */
    public static final NamespacedKey OWNER = key("owner");
    public static final NamespacedKey COOLDOWN = key("cooldown");
    public static final NamespacedKey COOLDOWN_2 = key("cooldown2");
    /** Game time of a type-specific event (ninja last seen, thief flee end). */
    public static final NamespacedKey MARK = key("mark");
    /** UUID of the player a thief robbed. */
    public static final NamespacedKey STOLEN_FROM = key("stolen_from");
    public static final NamespacedKey PHASE_TWO = key("phase_two");
    /** Kind of a helper entity (hook, rock, spit, acid, radio display). */
    public static final NamespacedKey HELPER = key("helper");
    public static final NamespacedKey RADIO_ITEM = key("radio");
    public static final NamespacedKey RADIO_RECIPE = key("radio");
    public static final NamespacedKey ANTENNA_ITEM = key("antenna");
    public static final NamespacedKey ANTENNA_RECIPE = key("antenna");
    /** Zombie type of a test spawn egg. */
    public static final NamespacedKey EGG = key("egg");

    public static final NamespacedKey MOD_FOLLOW_RANGE = key("follow_range");
    public static final NamespacedKey MOD_DAY_HEALTH = key("day_health");
    public static final NamespacedKey MOD_DAY_DAMAGE = key("day_damage");
    public static final NamespacedKey MOD_BLOOD_MOON_HEALTH = key("blood_moon_health");
    public static final NamespacedKey MOD_BLOOD_MOON_SPEED = key("blood_moon_speed");
    public static final NamespacedKey MOD_TYPE_HEALTH = key("type_health");
    public static final NamespacedKey MOD_TYPE_DAMAGE = key("type_damage");
    public static final NamespacedKey MOD_TYPE_SPEED = key("type_speed");
    public static final NamespacedKey MOD_TYPE_SCALE = key("type_scale");
    public static final NamespacedKey MOD_TYPE_KNOCKBACK = key("type_knockback");
    public static final NamespacedKey MOD_NO_FALL = key("no_fall");
    public static final NamespacedKey MOD_PHASE_TWO = key("phase_two_speed");

    private Keys() {}

    private static NamespacedKey key(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }
}
