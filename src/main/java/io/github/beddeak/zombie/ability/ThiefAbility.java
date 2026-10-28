package io.github.beddeak.zombie.ability;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.ending.EndingItem;
import io.github.beddeak.zombie.logic.AbilityRules;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.Tags;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * Steals one stack instead of dealing damage and runs off with it: ordinary items first, weapons and armor last. The item is never lost: it drops when the
 * thief dies, is dropped when the plugin removes the thief, and goes straight back to its owner when the thief dies
 * somewhere a dropped item would burn or fall out of the world.
 */
public final class ThiefAbility implements Ability {
    private final Apocalypse core;

    ThiefAbility(Apocalypse core) {
        this.core = core;
    }

    @Override public void attacked(Zombie zombie, Player victim, EntityDamageByEntityEvent event) {
        if (Tags.uuid(zombie, Keys.STOLEN_FROM) != null) { return; }
        PlayerInventory inventory = victim.getInventory();
        int slot = AbilityRules.theftSlot(36, inventory.getHeldItemSlot(), index -> {
            ItemStack item = inventory.getItem(index);
            return item != null && !item.isEmpty() && EndingItem.of(item) == null;
        }, index -> AbilityRules.weaponOrArmor(inventory.getItem(index).getType()), ThreadLocalRandom.current());
        if (slot < 0) { return; }
        ItemStack stolen = inventory.getItem(slot);
        if (stolen == null) { return; }
        event.setCancelled(true);
        inventory.setItem(slot, null);
        EntityEquipment equipment = zombie.getEquipment();
        ItemStack previous = equipment.getItemInMainHand();
        if (!previous.isEmpty()) { zombie.getWorld().dropItemNaturally(zombie.getLocation(), previous); }
        equipment.setItemInMainHand(stolen);
        equipment.setItemInMainHandDropChance(1.0f);
        Tags.uuid(zombie, Keys.STOLEN_FROM, victim.getUniqueId());
        int fleeTicks = core.settings().types().thief().fleeSeconds() * 20;
        zombie.getPersistentDataContainer().set(Keys.MARK, PersistentDataType.LONG, zombie.getWorld().getGameTime() + fleeTicks);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, fleeTicks, 1));
        zombie.setTarget(null);
        victim.sendMessage(Msg.alert("도둑 좀비가 ").append(stolen.displayName())
                .append(Component.text("을(를) 훔쳐 달아났습니다! 잡으면 돌려받을 수 있습니다.", NamedTextColor.RED)));
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

    @Override public void died(Zombie zombie, EntityDeathEvent event) {
        UUID owner = Tags.uuid(zombie, Keys.STOLEN_FROM);
        if (owner == null) { return; }
        ItemStack stolen = zombie.getEquipment().getItemInMainHand();
        if (stolen.isEmpty()) { return; }
        if (!unsafe(zombie)) { return; }
        for (Iterator<ItemStack> drops = event.getDrops().iterator(); drops.hasNext(); ) {
            ItemStack drop = drops.next();
            if (drop.isSimilar(stolen) && drop.getAmount() == stolen.getAmount()) {
                drops.remove();
                break;
            }
        }
        giveBack(owner, stolen.clone());
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
