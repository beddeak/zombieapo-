package io.github.beddeak.zombie.spawn;

import io.github.beddeak.zombie.logic.SpawnLocator;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class BukkitSampler implements SpawnLocator.Sampler {
    private final World world;

    public BukkitSampler(World world) {
        this.world = world;
    }

    @Override public boolean loaded(int x, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4);
    }

    @Override public SpawnLocator.Kind kind(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (block.isLiquid()) { return SpawnLocator.Kind.LIQUID; }
        if (type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.COBWEB || type == Material.POWDER_SNOW
                || type == Material.SWEET_BERRY_BUSH || type == Material.WITHER_ROSE || type == Material.MAGMA_BLOCK
                || type == Material.CACTUS || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) {
            return SpawnLocator.Kind.BLOCKED;
        }
        if (block.isPassable()) { return SpawnLocator.Kind.OPEN; }
        return block.isSolid() ? SpawnLocator.Kind.SOLID : SpawnLocator.Kind.BLOCKED;
    }

    @Override public int surface(int x, int z) {
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    @Override public int minY() { return world.getMinHeight(); }

    @Override public int maxY() { return world.getMaxHeight(); }
}
