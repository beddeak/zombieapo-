package io.github.beddeak.zombie.zombie;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Spawn eggs for trying out one zombie type. They spawn the custom zombie instead of a vanilla one. */
public final class TestEgg {
    private TestEgg() {}

    public static ItemStack create(ZombieType type, int amount) {
        ItemStack item = ItemStack.of(Material.ZOMBIE_SPAWN_EGG, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text(type.displayName() + " 좀비 스폰알", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("우클릭한 곳에 " + type.displayName() + " 좀비를 소환합니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("좀비 아포칼립스가 켜진 월드에서만 작동합니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(Keys.EGG, PersistentDataType.STRING, type.name());
        });
        return item;
    }

    /** The zombie type of a test egg, or null for any other item. */
    public static ZombieType type(ItemStack item) {
        if (item == null || item.isEmpty()) { return null; }
        String value = item.getPersistentDataContainer().get(Keys.EGG, PersistentDataType.STRING);
        return value == null ? null : ZombieType.parse(value).orElse(null);
    }
}
