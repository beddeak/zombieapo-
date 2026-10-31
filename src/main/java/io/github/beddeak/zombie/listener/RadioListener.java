package io.github.beddeak.zombie.listener;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.ending.EndingItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class RadioListener implements Listener {
    private final Apocalypse core;

    public RadioListener(Apocalypse core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        core.ending().placed(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (core.assembly().protects(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(io.github.beddeak.zombie.Msg.warn("조립 중인 작업대는 부술 수 없습니다."));
            return;
        }
        core.ending().broken(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        core.assembly().crafted(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void crafter(CrafterCraftEvent event) {
        if (EndingItem.of(event.getRecipe()) != null) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) { core.ending().clicked(event); }
    }

    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) {
        core.ending().protect(event.getLocation().getWorld(), event.blockList());
        event.blockList().removeIf(core.assembly()::protects);
    }

    @EventHandler(ignoreCancelled = true)
    public void blockExplode(BlockExplodeEvent event) {
        core.ending().protect(event.getBlock().getWorld(), event.blockList());
        event.blockList().removeIf(core.assembly()::protects);
    }
}
