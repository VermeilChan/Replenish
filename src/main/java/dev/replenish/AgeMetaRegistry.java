package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

public final class AgeMetaRegistry {

    public static final class CropInfo {
        public final int maximumAge;
        public final BlockData baseData;
        public final boolean requiresFarmland;
        public final boolean requiresSoulSand;
        public final boolean isCocoa;
        public final BlockData[] ageStates;
        public final BlockData[][] ageFacingStates;

        private CropInfo(
                int maximumAge,
                BlockData baseData,
                boolean requiresFarmland,
                boolean requiresSoulSand,
                boolean isCocoa,
                BlockData[] ageStates,
                BlockData[][] ageFacingStates) {
            this.maximumAge = maximumAge;
            this.baseData = baseData;
            this.requiresFarmland = requiresFarmland;
            this.requiresSoulSand = requiresSoulSand;
            this.isCocoa = isCocoa;
            this.ageStates = ageStates;
            this.ageFacingStates = ageFacingStates;
        }

        public BlockData getBlockData(int age) {
            if (isCocoa) throw new UnsupportedOperationException("Use getCocoaBlockData for cocoa");
            if (age < 0) age = 0;
            if (age > maximumAge) age = maximumAge;
            return ageStates[age];
        }

        public BlockData getCocoaBlockData(int age, int faceOrdinal) {
            if (!isCocoa) throw new UnsupportedOperationException("Not cocoa");
            if (age < 0) age = 0;
            if (age > maximumAge) age = maximumAge;
            return ageFacingStates[age][faceOrdinal & 3];
        }
    }

    public static final class CocoaFaces {
        public static final BlockFace[] FACES = {
                BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
        };
    }

    private final Map<Material, CropInfo> metadataMap;

    public AgeMetaRegistry(Plugin plugin) {
        this.metadataMap = new EnumMap<>(Material.class);
        Material[] supportedCrops = {
                Material.WHEAT,
                Material.CARROTS,
                Material.POTATOES,
                Material.NETHER_WART,
                Material.COCOA,
                Material.BEETROOTS
        };

        for (Material material : supportedCrops) {
            try {
                BlockData base = Bukkit.createBlockData(material);
                if (!(base instanceof Ageable ageable)) continue;

                int maxAge = ageable.getMaximumAge();
                boolean isCocoa = material == Material.COCOA;
                boolean requiresFarmland = !isCocoa && material != Material.NETHER_WART;
                boolean requiresSoulSand = material == Material.NETHER_WART;

                BlockData[] ageStates = null;
                BlockData[][] ageFacingStates = null;

                if (!isCocoa) {
                    ageStates = new BlockData[maxAge + 1];
                    for (int age = 0; age <= maxAge; age++) {
                        BlockData data = base.clone();
                        ((Ageable) data).setAge(age);
                        ageStates[age] = data;
                    }
                } else {
                    if (!(base instanceof Directional)) continue;
                    ageFacingStates = new BlockData[maxAge + 1][4];
                    for (int age = 0; age <= maxAge; age++) {
                        for (int f = 0; f < 4; f++) {
                            BlockData data = base.clone();
                            ((Ageable) data).setAge(age);
                            ((Directional) data).setFacing(CocoaFaces.FACES[f]);
                            ageFacingStates[age][f] = data;
                        }
                    }
                }

                CropInfo info = new CropInfo(
                        maxAge,
                        ageStates != null ? ageStates[0] : ageFacingStates[0][0],
                        requiresFarmland,
                        requiresSoulSand,
                        isCocoa,
                        ageStates,
                        ageFacingStates);
                metadataMap.put(material, info);

            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING, "Age meta scan skipped for " + material, error);
            }
        }
    }

    public CropInfo get(Material material) {
        if (material == null) return null;
        return metadataMap.get(material);
    }
}
