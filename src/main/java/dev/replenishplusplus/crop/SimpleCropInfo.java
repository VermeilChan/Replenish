package dev.replenishplusplus.crop;

import org.bukkit.block.data.BlockData;

public record SimpleCropInfo(
        int maximumAge,
        boolean requiresFarmland,
        boolean requiresSoulSand,
        BlockData[] ageStates) implements CropInfo {

    public BlockData stateFor(int age) {
        return ageStates[age];
    }
}