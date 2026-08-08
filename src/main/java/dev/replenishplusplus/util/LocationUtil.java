package dev.replenishplusplus.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class LocationUtil {

    private LocationUtil() {}

    public static Location centerOf(Location location) {
        if (location == null) return null;
        World world = location.getWorld();
        if (world == null) return null;
        return new Location(
                world,
                location.getBlockX() + 0.5,
                location.getBlockY() + 0.5,
                location.getBlockZ() + 0.5,
                0f, 0f);
    }

    public static boolean isChunkLoadedAround(Location location) {
        if (location == null) return false;
        World world = location.getWorld();
        if (world == null) return false;
        try {
            return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        } catch (Exception e) {
            return false;
        }
    }

    public static String describe(Block block) {
        if (block == null) return "null_block";
        World world = block.getWorld();
        return world.getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    public static String describe(World world, int x, int y, int z) {
        if (world == null) return "null_world:" + x + "," + y + "," + z;
        return world.getName() + ":" + x + "," + y + "," + z;
    }
}