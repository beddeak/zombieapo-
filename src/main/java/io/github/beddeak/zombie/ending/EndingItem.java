package io.github.beddeak.zombie.ending;

import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.zombie.Keys;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;

public enum EndingItem {
    RADIO(Keys.RADIO_ITEM, Keys.RADIO_RECIPE, "라디오",
            List.of("구조대와 연락할 수 있는 무전기", "안테나를 붙여 설치한 뒤 두 번 우클릭하면 구조를 요청합니다")),
    ANTENNA(Keys.ANTENNA_ITEM, Keys.ANTENNA_RECIPE, "안테나",
            List.of("라디오 신호를 멀리 보내는 안테나", "라디오 바로 위나 옆에 붙여 설치하세요"));

    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private final String displayName;
    private final List<String> lore;

    EndingItem(NamespacedKey itemKey, NamespacedKey recipeKey, String displayName, List<String> lore) {
        this.itemKey = itemKey;
        this.recipeKey = recipeKey;
        this.displayName = displayName;
        this.lore = lore;
    }

    public String displayName() { return displayName; }

    public NamespacedKey recipeKey() { return recipeKey; }

    public ItemStack create(Material block) {
        ItemStack item = ItemStack.of(block);
        item.editMeta(meta -> {
            meta.displayName(Component.text(displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean is(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public ShapedRecipe recipe(Material block, Settings.Shape shape) {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create(block));
        recipe.shape(shape.rows().toArray(String[]::new));
        shape.keys().forEach(recipe::setIngredient);
        return recipe;
    }

    public static EndingItem of(ItemStack item) {
        for (EndingItem kind : values()) {
            if (kind.is(item)) { return kind; }
        }
        return null;
    }

    public static EndingItem of(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) { return null; }
        for (EndingItem kind : values()) {
            if (kind.recipeKey.equals(keyed.getKey())) { return kind; }
        }
        return null;
    }
}
