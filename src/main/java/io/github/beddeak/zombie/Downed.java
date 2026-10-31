package io.github.beddeak.zombie;

import io.github.beddeak.zombie.config.Settings;
import io.github.beddeak.zombie.zombie.Attributes;
import io.github.beddeak.zombie.zombie.Keys;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.PlayerInventory;

public final class Downed {
    static final int TICK_INTERVAL = 5;
    private static final int BAR_CELLS = 10;
    private final Apocalypse core;
    private final Map<UUID, State> downed = new HashMap<>();

    private static final class State {
        UUID reviver;
        int progress;
    }

    Downed(Apocalypse core) {
        this.core = core;
    }

    public boolean is(Player player) {
        return downed.containsKey(player.getUniqueId());
    }

    public void intercept(Player player, EntityDamageEvent event) {
        Settings.Downed options = core.settings().downed();
        if (!options.enabled() || is(player) || !core.active(player.getWorld()) || !fighting(player)) { return; }
        if (player.getHealth() - event.getFinalDamage() > 0) { return; }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE, WORLD_BORDER -> { return; }
            default -> { }
        }
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || inventory.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) { return; }
        event.setCancelled(true);
        down(player, options);
    }

    private void down(Player player, Settings.Downed options) {
        downed.put(player.getUniqueId(), new State());
        player.setHealth(Math.max(1, Attributes.maxHealth(player)));
        player.setFireTicks(0);
        Attributes.multiply(player, Attribute.MOVEMENT_SPEED, Keys.MOD_DOWNED_SPEED, options.crawlSpeed());
        Attributes.multiply(player, Attribute.JUMP_STRENGTH, Keys.MOD_DOWNED_JUMP, 0);
        player.setPose(Pose.SWIMMING, true);
        player.setGlowing(true);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.5f, 0.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.8f);
        Msg.title(List.of(player), "쓰러졌습니다!", NamedTextColor.RED, "동료가 옆에서 웅크리면 살아납니다");
        core.announce(world, Msg.alert(player.getName() + "이(가) 쓰러졌습니다! 옆에서 " + options.reviveSeconds()
                + "초 동안 웅크려 살려주세요. " + options.bleedSeconds() + "초 안에 못 살리면 죽습니다."));
    }

    void tick() {
        Settings.Downed options = core.settings().downed();
        for (Iterator<Map.Entry<UUID, State>> iterator = downed.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, State> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.isDead() || !core.active(player.getWorld()) || !fighting(player)) {
                iterator.remove();
                restore(player);
                continue;
            }
            double max = Math.max(1, Attributes.maxHealth(player));
            double drain = max / (Math.max(1, options.bleedSeconds()) * 20.0 / TICK_INTERVAL);
            double health = player.getHealth() - drain;
            if (health <= 0) {
                iterator.remove();
                restore(player);
                core.announce(player.getWorld(), Msg.alert(player.getName() + "이(가) 끝내 일어나지 못했습니다..."));
                player.setHealth(0);
                continue;
            }
            player.setHealth(health);
            State state = entry.getValue();
            Player reviver = reviver(player, options);
            if (reviver == null) {
                state.reviver = null;
                state.progress = 0;
                int seconds = (int) Math.ceil(health / drain * TICK_INTERVAL / 20.0);
                player.sendActionBar(Component.text("쓰러짐 — " + seconds + "초 안에 동료가 살려야 합니다", NamedTextColor.RED));
                continue;
            }
            if (!reviver.getUniqueId().equals(state.reviver)) {
                state.reviver = reviver.getUniqueId();
                state.progress = 0;
            }
            state.progress += TICK_INTERVAL;
            int needed = Math.max(1, options.reviveSeconds() * 20);
            if (state.progress >= needed) {
                iterator.remove();
                revive(player, reviver, options);
                continue;
            }
            Component progress = Component.text(bar((double) state.progress / needed), NamedTextColor.GREEN);
            player.sendActionBar(Component.text(reviver.getName() + "이(가) 살리는 중 ", NamedTextColor.YELLOW).append(progress));
            reviver.sendActionBar(Component.text(player.getName() + " 살리는 중 ", NamedTextColor.YELLOW).append(progress)
                    .append(Component.text(" — 계속 웅크리세요", NamedTextColor.GRAY)));
        }
    }

    public void clear(Player player) {
        if (downed.remove(player.getUniqueId()) != null) { restore(player); }
    }

    void clearAll() {
        for (UUID id : List.copyOf(downed.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) { restore(player); }
        }
        downed.clear();
    }

    private Player reviver(Player player, Settings.Downed options) {
        double reach = options.reviveDistance() * options.reviveDistance();
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || is(other) || !other.isSneaking() || !fighting(other) || other.isDead()) { continue; }
            if (other.getLocation().distanceSquared(player.getLocation()) <= reach) { return other; }
        }
        return null;
    }

    private void revive(Player player, Player reviver, Settings.Downed options) {
        restore(player);
        player.setHealth(Math.max(1, Math.min(options.reviveHealth(), Attributes.maxHealth(player))));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        Msg.title(List.of(player), "다시 일어났습니다!", NamedTextColor.GREEN, reviver.getName() + "이(가) 살려줬습니다");
        core.announce(player.getWorld(), Msg.good(reviver.getName() + "이(가) " + player.getName() + "을(를) 살렸습니다!"));
    }

    private static void restore(Player player) {
        Attributes.multiply(player, Attribute.MOVEMENT_SPEED, Keys.MOD_DOWNED_SPEED, 1);
        Attributes.multiply(player, Attribute.JUMP_STRENGTH, Keys.MOD_DOWNED_JUMP, 1);
        player.setPose(Pose.STANDING, false);
        player.setGlowing(false);
    }

    private static boolean fighting(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private static String bar(double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * BAR_CELLS);
        return "█".repeat(filled) + "░".repeat(BAR_CELLS - filled);
    }
}
