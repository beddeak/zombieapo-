package io.github.beddeak.zombie.listener;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import io.github.beddeak.zombie.zombie.TestEgg;
import io.github.beddeak.zombie.zombie.ZombieType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Test spawn eggs: right-clicking spawns the custom zombie; the vanilla egg behaviour is always blocked. */
public final class EggListener implements Listener {
    private final Apocalypse core;

    public EggListener(Apocalypse core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) { return; }
        ZombieType type = TestEgg.type(event.getItem());
        if (type == null) { return; }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location target = target(event, player);
        if (target == null) {
            player.sendActionBar(Msg.warn("소환할 블록을 바라보고 사용하세요."));
            return;
        }
        World world = target.getWorld();
        if (!core.active(world)) {
            player.sendMessage(Msg.warn("좀비 아포칼립스가 켜진 월드에서만 쓸 수 있습니다. 먼저 /apoc start 를 입력하세요."));
            return;
        }
        if (type == ZombieType.TANK) {
            core.tanks().spawnAt(target);
        } else {
            core.factory().spawn(target, type, core.context(world));
        }
        EquipmentSlot hand = event.getHand();
        if (hand != null && player.getGameMode() != GameMode.CREATIVE) {
            ItemStack held = player.getInventory().getItem(hand);
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held.getAmount() > 0 ? held : null);
        }
        player.sendActionBar(Msg.info(type.displayName() + " 좀비를 소환했습니다."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void useOnEntity(PlayerInteractEntityEvent event) {
        if (TestEgg.type(event.getPlayer().getInventory().getItem(event.getHand())) != null) { event.setCancelled(true); }
    }

    @EventHandler(ignoreCancelled = true)
    public void dispense(BlockDispenseEvent event) {
        if (TestEgg.type(event.getItem()) != null) { event.setCancelled(true); }
    }

    private static Location target(PlayerInteractEvent event, Player player) {
        Block clicked = event.getClickedBlock();
        if (clicked != null && event.getBlockFace() != null) {
            return clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        }
        Block looked = player.getTargetBlockExact(32);
        return looked == null ? null : looked.getLocation().add(0.5, 1, 0.5);
    }
}
