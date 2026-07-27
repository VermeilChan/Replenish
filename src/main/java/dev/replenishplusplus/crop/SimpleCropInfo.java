package dev.replenishplusplus.crop;

import org.bukkit.block.data.BlockData;

/**
 * CropInfo for crops that grow straight up on farmland or soul sand
 * (wheat, carrots, potatoes, nether wart, beetroots).
 */
public record SimpleCropInfo(
        int maximumAge,
        boolean requiresFarmland,
        boolean requiresSoulSand,
        BlockData[] ageStates) implements CropInfo {

    public BlockData stateFor(int age) {
        return ageStates[clamp(age, maximumAge)];
    }

    private static int clamp(int value, int max) {
        if (value < 0) return 0;
        return Math.min(value, max);
    }
}