package dev.replenish;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

public final class AgeMetaRegistry {

    public static final class AgeMeta {
        public final int maximumAge;
        public final BlockData baseData;

        private AgeMeta(int maximumAge, BlockData baseData) {
            this.maximumAge = maximumAge;
            this.baseData = baseData;
        }
    }

    private final Map<Material, AgeMeta> metadataMap;

    public AgeMetaRegistry(Plugin plugin) {
        this.metadataMap = new EnumMap<>(Material.class);
        Material[] supportedCrops = {
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.NETHER_WART,
            Material.COCOA, Material.BEETROOTS
        };

        for (Material material : supportedCrops) {
            try {
                BlockData data = Bukkit.createBlockData(material);
                if (data instanceof Ageable ageable) {
                    metadataMap.put(material, new AgeMeta(ageable.getMaximumAge(), data));
                }
            } catch (Throwable error) {
                plugin.getLogger()
                        .log(Level.WARNING, "Age meta scan skipped for " + material, error);
            }
        }
    }

    public AgeMeta get(Material material) {
        if (material == null) return null;
        return metadataMap.get(material);
    }
}
