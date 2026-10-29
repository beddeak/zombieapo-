package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.data.WorldData;
import io.github.beddeak.zombie.ending.EndingItem;
import io.github.beddeak.zombie.logic.AbilityRules;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Knocks one stack out of the player's inventory instead of dealing damage: ordinary items first, weapons and armor
 * last. Thieves saved by older versions may still carry a stolen item; the flee, death and removal code below makes
 * sure that item still comes back.
 */
public final class ThiefAbility implements Ability {
    /** Ticks before the knocked-out item can be picked up, so it visibly flies off first. */
    private static final int KNOCKED_PICKUP_DELAY = 20;

    private final Apocalypse core;

    ThiefAbility(Apocalypse core) {
        this.core = core;
    }

    /** Every hit knocks one stack out of the inventory onto the ground instead of dealing damage; the thief keeps fighting. */
    @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
        if (Tags.uuid(zombie, Keys.STOLEN_FROM) != null) { return; }
        PlayerInventory inventory = victim.getInventory();
        int slot = AbilityRules.theftSlot(36, inventory.getHeldItemSlot(), index -> {
            ItemStack item = inventory.getItem(index);
            return item != null && !item.isEmpty() && EndingItem.of(item) == null;
        }, index -> AbilityRules.weaponOrArmor(inventory.getItem(index).getType()), ThreadLocalRandom.current());
        if (slot < 0) { return; }
        ItemStack knocked = inventory.getItem(slot);
        if (knocked == null) { return; }
        event.setCancelled(true);
        inventory.setItem(slot, null);
        Item drop = victim.getWorld().dropItem(victim.getLocation().add(0, 1, 0), knocked);
        drop.setVelocity(Support.flatDirection(zombie.getLocation(), victim.getLocation()).multiply(0.3).setY(0.3));
        drop.setPickupDelay(KNOCKED_PICKUP_DELAY);
        WorldData data = core.data(victim.getWorld());
        if (data != null) {
            data.stats(victim.getUniqueId()).stolen++;
            core.statsChanged();
        }
        victim.sendMessage(Msg.alert("도둑 좀비가 ").append(knocked.displayName())
                .append(Component.text("을(를) 쳐서 떨어뜨렸습니다! 얼른 주우세요.", NamedTextColor.RED)));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        victim.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 1.2f);
    }

    @Override public void tick(Zombie zombie) {
        if (Tags.uuid(zombie, Keys.STOLEN_FROM) == null) { return; }
        if (zombie.getWorld().getGameTime() >= Tags.number(zombie, Keys.MARK, 0)) { return; }
        Player chaser = Support.nearest(zombie.getLocation(), 24);
        if (chaser == null) { return; }
        zombie.setTarget(null);
        Vector away = Support.flatDirection(chaser.getLocation(), zombie.getLocation()).multiply(10);
        zombie.getPathfinder().moveTo(zombie.getLocation().add(away), 1.3);
    }

    /**
     * Whatever killed the thief, the stolen item comes back unchanged: dropped where it fell, or straight to its owner
     * when it died in lava or the void.
     */
    @Override public void died(Zombie zombie, EntityDeathEvent event) {
        UUID owner = Tags.uuid(zombie, Keys.STOLEN_FROM);
        if (owner == null) { return; }
        EntityEquipment equipment = zombie.getEquipment();
        ItemStack stolen = equipment.getItemInMainHand().clone();
        if (stolen.isEmpty()) { return; }
        equipment.setItemInMainHand(null);
        if (unsafe(zombie)) {
            giveBack(owner, stolen);
        } else {
            event.getDrops().add(stolen);
        }
    }

    /** Called before the plugin removes a thief: whatever it holds drops where it stands. */
    public void release(Zombie zombie) {
        if (Tags.uuid(zombie, Keys.STOLEN_FROM) == null) { return; }
        ItemStack stolen = zombie.getEquipment().getItemInMainHand();
        if (stolen.isEmpty()) { return; }
        zombie.getEquipment().setItemInMainHand(null);
        Location location = zombie.getLocation();
        if (location.getY() < location.getWorld().getMinHeight() || location.getBlock().getType() == Material.LAVA) {
            giveBack(Tags.uuid(zombie, Keys.STOLEN_FROM), stolen);
        } else {
            location.getWorld().dropItemNaturally(location, stolen);
        }
    }

    /** Hands items that were returned while their owner was offline. */
    public void deliverPending(Player player) {
        List<String> encoded = core.store().pendingItems().remove(player.getUniqueId());
        if (encoded == null) { return; }
        for (String value : encoded) {
            try {
                give(player, ItemStack.deserializeBytes(Base64.getDecoder().decode(value)));
            } catch (RuntimeException exception) {
                core.logger().warning("Could not restore a stolen item for " + player.getName() + ": " + exception.getMessage());
            }
        }
        core.save();
        player.sendMessage(Msg.good("도둑 좀비에게 빼앗겼던 아이템을 돌려받았습니다."));
    }

    private void giveBack(UUID owner, ItemStack item) {
        Player player = owner == null ? null : Bukkit.getPlayer(owner);
        if (player != null) {
            give(player, item);
            player.sendMessage(Msg.good("도둑 좀비가 사라지면서 훔쳐 간 아이템이 돌아왔습니다."));
            return;
        }
        if (owner == null) { return; }
        core.store().pendingItems().computeIfAbsent(owner, id -> new ArrayList<>())
                .add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        core.save();
    }

    private static void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** True where a dropped item would be destroyed: lava, fire or the void. */
    private static boolean unsafe(Zombie zombie) {
        Location location = zombie.getLocation();
        if (location.getY() < location.getWorld().getMinHeight()) { return true; }
        Material feet = location.getBlock().getType();
        if (feet == Material.LAVA || feet == Material.FIRE || feet == Material.SOUL_FIRE) { return true; }
        EntityDamageEvent last = zombie.getLastDamageCause();
        if (last == null) { return false; }
        EntityDamageEvent.DamageCause cause = last.getCause();
        return cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.VOID;
    }
}
