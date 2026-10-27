package io.github.beddeak.zombie.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record BlockPos(int x, int y, int z) {
    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location center(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public boolean matches(Block block) {
        return block.getX() == x && block.getY() == y && block.getZ() == z;
    }

    @Override public String toString() {
        return x + ", " + y + ", " + z;
    }
}
