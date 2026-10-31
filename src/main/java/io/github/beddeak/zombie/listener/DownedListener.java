package io.github.beddeak.zombie.listener;

import io.github.beddeak.zombie.Apocalypse;
import io.github.beddeak.zombie.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class DownedListener implements Listener {
    private final Apocalypse core;

    public DownedListener(Apocalypse core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) { core.downed().intercept(player, event); }
    }

    @EventHandler(ignoreCancelled = true)
    public void regain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player && core.downed().is(player)) { event.setCancelled(true); }
    }

    @EventHandler(ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (!core.downed().is(event.getPlayer())) { return; }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Msg.warn("쓰러진 동안에는 먹거나 마실 수 없습니다."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) {
        core.downed().clear(event.getEntity());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        core.downed().clear(event.getPlayer());
    }
}
