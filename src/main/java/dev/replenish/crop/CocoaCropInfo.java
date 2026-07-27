package dev.replenish.crop;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.List;

/**
 * CropInfo for cocoa pods, which grow on the side of jungle logs
 * in four horizontal facing directions.
 */
public record CocoaCropInfo(
        int maximumAge,
        List<BlockFace> faces,
        BlockData[][] ageFacingStates) implements CropInfo {

    public BlockData stateFor(int age, int faceOrdinal) {
        return ageFacingStates[clamp(age, maximumAge)][faceOrdinal & 3];
    }

    private static int clamp(int value, int max) {
        if (value < 0) return 0;
        return Math.min(value, max);
    }
}