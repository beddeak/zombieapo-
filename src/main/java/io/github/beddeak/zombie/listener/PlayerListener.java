package io.github.beddeak.zombie.listener;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.Keys;
import io.github.beddeak.zombie.zombie.ZombieType;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class PlayerListener implements Listener {
    private final Apocalypse core;

    public PlayerListener(Apocalypse core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        Settings.PlayerZombie options = core.settings().playerZombie();
        Location location = player.getLocation();
        if (!core.active(world) || !options.enabled() || location.getY() < world.getMinHeight()) { return; }
        UUID previous = core.store().playerZombies().get(player.getUniqueId());
        if (previous != null) {
            Entity old = Bukkit.getEntity(previous);
            if (old != null) {
                core.registry().forget(old);
                old.remove();
            }
        }
        Zombie zombie = core.factory().spawn(location, Zombie.class, ZombieType.PLAYER, core.context(world).withOwner(player.getUniqueId()));
        zombie.customName(Component.text(player.getName(), NamedTextColor.RED));
        zombie.setCustomNameVisible(true);
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        zombie.getEquipment().setHelmet(head);
        zombie.getEquipment().setHelmetDropChance(0f);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setPersistent(true);
        Attributes.multiply(zombie, Attribute.MAX_HEALTH, Keys.MOD_TYPE_HEALTH, options.healthMultiplier());
        Attributes.multiply(zombie, Attribute.ATTACK_DAMAGE, Keys.MOD_TYPE_DAMAGE, options.damageMultiplier());
        Attributes.fillHealth(zombie);
        core.store().playerZombies().put(player.getUniqueId(), zombie.getUniqueId());
        core.save();
        core.announce(world, Msg.alert(player.getName() + "이(가) 쓰러져 좀비가 되었습니다..."));
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.ending().joined(player);
        core.abilities().thief().deliverPending(player);
    }

    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (core.active(player.getWorld())) { core.ending().discoverRecipe(List.of(player)); }
    }

    @EventHandler(ignoreCancelled = true)
    public void sneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) { core.abilities().jockey().sneaked(event.getPlayer()); }
    }
}
