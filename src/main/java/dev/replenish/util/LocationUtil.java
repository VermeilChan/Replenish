package dev.replenish.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Location and block-related helper methods.
 */
public final class LocationUtil {

    private LocationUtil() {}

    /** Returns the centre of the block containing the given location. */
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

    /** Returns true if the chunk at the given location is currently loaded. */
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

    /** Human-readable block identifier for log messages. */
    public static String describe(Block block) {
        if (block == null) return "null_block";
        World world = block.getWorld();
        String worldName = world.getName();
        return worldName + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}