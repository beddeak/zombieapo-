package io.github.beddeak.zombie;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public final class HandLight {
    private static final int RESEND_CALLS = 10;
    private final Apocalypse core;
    private final Map<UUID, Location> lit = new HashMap<>();
    private int calls;

    HandLight(Apocalypse core) {
        this.core = core;
    }

    void tick() {
        calls++;
        Set<UUID> seen = new HashSet<>();
        if (core.settings().handLight()) {
            for (World world : core.activeWorlds()) {
                for (Player player : world.getPlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR) { continue; }
                    int level = level(player.getInventory());
                    if (level == 0) { continue; }
                    Block block = player.getEyeLocation().getBlock();
                    Light light = light(block, level);
                    if (light == null) { continue; }
                    seen.add(player.getUniqueId());
                    Location here = block.getLocation();
                    Location previous = lit.put(player.getUniqueId(), here);
                    if (previous != null && previous.equals(here) && calls % RESEND_CALLS != 0) { continue; }
                    if (previous != null && !previous.equals(here)) { restore(player, previous); }
                    player.sendBlockChange(here, light);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, Location>> iterator = lit.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, Location> entry = iterator.next();
            if (seen.contains(entry.getKey())) { continue; }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) { restore(player, entry.getValue()); }
            iterator.remove();
        }
    }

    void clear() {
        for (Map.Entry<UUID, Location> entry : lit.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) { restore(player, entry.getValue()); }
        }
        lit.clear();
    }

    private static int level(PlayerInventory inventory) {
        return Math.max(level(inventory.getItemInMainHand().getType()), level(inventory.getItemInOffHand().getType()));
    }

    private static int level(Material material) {
        return switch (material) {
            case TORCH -> 14;
            case LANTERN -> 15;
            case SOUL_TORCH, SOUL_LANTERN -> 10;
            default -> 0;
        };
    }

    private static Light light(Block block, int level) {
        boolean water = block.getType() == Material.WATER && block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
        if (!block.getType().isAir() && !water) { return null; }
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(level);
        light.setWaterlogged(water);
        return light;
    }

    private static void restore(Player player, Location location) {
        if (player.getWorld().equals(location.getWorld())) { player.sendBlockChange(location, location.getBlock().getBlockData()); }
    }
}
