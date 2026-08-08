package dev.replenishplusplus.crop;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.List;

public record CocoaCropInfo(
        int maximumAge,
        List<BlockFace> faces,
        BlockData[][] ageFacingStates) implements CropInfo {

    public BlockData stateFor(int age, int faceOrdinal) {
        return ageFacingStates[age][faceOrdinal & 3];
    }
}