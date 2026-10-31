package io.github.beddeak.zombie.zombie;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

public final class Attributes {
    private Attributes() {}

    public static void multiply(LivingEntity entity, Attribute attribute, NamespacedKey key, double multiplier) {
        set(entity, attribute, key, multiplier - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    public static void add(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount) {
        set(entity, attribute, key, amount, AttributeModifier.Operation.ADD_NUMBER);
    }

    public static void set(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount,
                           AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) { return; }
        instance.removeModifier(key);
        if (amount != 0) { instance.addModifier(new AttributeModifier(key, amount, operation)); }
    }

    public static double value(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    public static double base(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getBaseValue();
    }

    public static double maxHealth(LivingEntity entity) {
        return value(entity, Attribute.MAX_HEALTH, 20);
    }

    public static void fillHealth(LivingEntity entity) {
        entity.setHealth(Math.max(1, maxHealth(entity)));
    }

    public static void heal(LivingEntity entity, double amount) {
        if (amount <= 0 || entity.isDead()) { return; }
        entity.setHealth(Math.min(maxHealth(entity), entity.getHealth() + amount));
    }
}
